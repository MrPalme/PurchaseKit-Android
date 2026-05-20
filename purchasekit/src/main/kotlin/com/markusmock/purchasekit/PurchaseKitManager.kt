// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.markusmock.purchasekit.api.AnyPurchasableOption
import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import com.markusmock.purchasekit.model.BillingConnectionState
import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.model.PurchaseError
import com.markusmock.purchasekit.model.PurchaseFlowState
import com.markusmock.purchasekit.policy.VerificationFailureMode
import com.markusmock.purchasekit.service.BillingBridge
import com.markusmock.purchasekit.service.PersistenceService
import com.markusmock.purchasekit.service.ProductService
import com.markusmock.purchasekit.service.RealBillingBridge
import com.markusmock.purchasekit.service.TransactionService
import com.markusmock.purchasekit.service.TransactionServiceDelegate
import com.markusmock.purchasekit.support.PurchaseKitLogger
import com.markusmock.purchasekit.support.d
import com.markusmock.purchasekit.support.e
import com.markusmock.purchasekit.support.i
import com.markusmock.purchasekit.support.w
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * App-agnostic facade over Google Play Billing v8+.
 *
 * Owns one [BillingBridge] for the lifetime of the manager and publishes a
 * small set of `StateFlow`s consumed identically from Compose and from
 * View/XML hosts:
 *
 * - [entitlements]                — per-option [EntitlementState] (truth from Play, cached for cold start).
 * - [availableProducts]           — per-option [ProductDetails] loaded via [loadProducts].
 * - [flowState] / [perOptionFlowState] — transient UI state for purchase attempts.
 * - [hasAnyActiveSubscription], [primaryActiveSubscription] — paywall convenience.
 * - [connectionState]             — typed mirror of `BillingClient.ConnectionState`.
 * - [canAttemptNetworkOperations] — optional gate when a `NetworkService` is configured.
 *
 * The manager is **not** a singleton. Hosts own the lifetime — typically one
 * instance attached to the application object. Tear it down with [shutdown];
 * the implementation cancels its `SupervisorJob`, ends the Play connection,
 * and releases listeners.
 *
 * Java callers consume the same API via the `PurchaseKitDelegate` callback
 * path; the `suspend` entry points are annotated `@JvmSynthetic` and hidden
 * from the Java view.
 *
 * Threading: every public state flow emits on the main thread. Suspend
 * functions hop to `Dispatchers.IO` internally; the caller's dispatcher is
 * unconstrained. Delegate callbacks fire on the main thread.
 *
 * @since 0.1.0
 */
public class PurchaseKitManager internal constructor(
    private val options: List<AnyPurchasableOption>,
    private val config: PurchaseKitConfig,
    private val bridge: BillingBridge,
    private val productService: ProductService,
    private val persistence: PersistenceService,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val transactionService: TransactionService = TransactionService(
        bridge = bridge,
        replacementPolicy = config.replacementPolicy,
        logger = config.logger,
        scope = scope,
    )

    private val optionsByProductId: Map<String, AnyPurchasableOption> =
        options.associateBy(AnyPurchasableOption::productId)

    private val _entitlements: MutableStateFlow<Map<AnyPurchasableOption, EntitlementState>> =
        MutableStateFlow(options.associateWith { EntitlementState.Inactive })
    private val _availableProducts: MutableStateFlow<Map<AnyPurchasableOption, ProductDetails>> =
        MutableStateFlow(emptyMap())
    private val _flowState: MutableStateFlow<PurchaseFlowState> =
        MutableStateFlow(PurchaseFlowState.Idle)
    private val _perOptionFlowState: MutableStateFlow<Map<AnyPurchasableOption, PurchaseFlowState>> =
        MutableStateFlow(emptyMap())

    private val listeners = CopyOnWriteArrayList<ListenerHolder>()

    /** Per-option entitlement state. Map is keyed by the original catalogue order. */
    public val entitlements: StateFlow<Map<AnyPurchasableOption, EntitlementState>> =
        _entitlements.asStateFlow()

    /** Per-option [ProductDetails] returned by Play. Empty until [loadProducts] resolves. */
    public val availableProducts: StateFlow<Map<AnyPurchasableOption, ProductDetails>> =
        _availableProducts.asStateFlow()

    /** Global purchase flow state. See [PurchaseFlowState]. */
    public val flowState: StateFlow<PurchaseFlowState> = _flowState.asStateFlow()

    /** Per-option flow state — useful when several purchase attempts could be in flight. */
    public val perOptionFlowState: StateFlow<Map<AnyPurchasableOption, PurchaseFlowState>> =
        _perOptionFlowState.asStateFlow()

    /** Typed mirror of `BillingClient.ConnectionState`. */
    public val connectionState: StateFlow<BillingConnectionState> = bridge.connectionState

    /**
     * `true` when the library believes Play is reachable. If
     * `PurchaseKitConfig.networkService` is `null` this flow always reports `true`.
     */
    public val canAttemptNetworkOperations: StateFlow<Boolean> = run {
        val ns = config.networkService
        ns?.canAttemptNetworkOperations
            ?: MutableStateFlow(true).asStateFlow()
    }

    /** `true` when any [AUTO_RENEWING_SUBSCRIPTION][PurchaseType.AUTO_RENEWING_SUBSCRIPTION] is currently active. */
    public val hasAnyActiveSubscription: StateFlow<Boolean> =
        _entitlements
            .map { snapshot ->
                snapshot.any { (option, state) ->
                    option.purchaseType.isSubscription && state.isActive
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * The single subscription the library considers "primary" when multiple
     * are concurrently active. Resolved via
     * `PurchaseKitConfig.exclusivityPolicy`.
     */
    public val primaryActiveSubscription: StateFlow<AnyPurchasableOption?> =
        _entitlements
            .map { snapshot ->
                val active = snapshot.filter { (option, state) ->
                    option.purchaseType.isSubscription && state.isActive
                }.keys.toList()
                when {
                    active.isEmpty() -> null
                    active.size == 1 -> active.first()
                    else -> AnyPurchasableOption.of(config.exclusivityPolicy.selectPrimary(active))
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        transactionService.setDelegate(InternalDelegate())
        transactionService.setOptionResolver { productId -> optionsByProductId[productId] }
        transactionService.start()

        scope.launch {
            warmFromCache()
            connectAndLoad()
        }
    }

    // ---------------- Public API ----------------

    /** Tears the manager down. Idempotent. */
    @MainThread
    public fun shutdown() {
        transactionService.stop()
        bridge.endConnection()
        config.networkService?.shutdown()
        listeners.forEach { it.release() }
        listeners.clear()
        scope.cancel()
    }

    /**
     * Re-queries Play and the optional network service. Hosts call this from a
     * `ProcessLifecycleOwner.lifecycle` observer or equivalent.
     */
    @MainThread
    public fun onAppEnteredForeground() {
        config.networkService?.onForeground()
        scope.launch {
            connectIfNeeded()
            refreshPurchases()
        }
    }

    /**
     * Registers [listener]. When [owner] is provided, the listener is removed
     * automatically when the owner's lifecycle transitions to `ON_DESTROY`.
     * Re-registering an existing listener replaces the previous registration.
     */
    @JvmOverloads
    @MainThread
    public fun addListener(listener: PurchaseKitDelegate, owner: LifecycleOwner? = null) {
        removeListener(listener)
        val holder = ListenerHolder(listener, owner)
        listeners += holder
        owner?.lifecycle?.addObserver(holder.observer)
    }

    /** Removes [listener]. Idempotent. */
    @MainThread
    public fun removeListener(listener: PurchaseKitDelegate) {
        listeners.removeAll { it.matches(listener).also { matched -> if (matched) it.release() } }
    }

    /** Loads [ProductDetails] for the catalogue. Safe to call repeatedly. */
    @JvmSynthetic
    public suspend fun loadProducts() {
        val result = withContext(ioDispatcher) { productService.query(options) }
        when (result) {
            is ProductService.Result.Success -> {
                val mapped = result.details.entries.mapNotNull { (productId, detail) ->
                    optionsByProductId[productId]?.let { it to detail }
                }.toMap()
                _availableProducts.value = mapped
                notify { onProductsLoaded(mapped) }
            }
            is ProductService.Result.Failure -> {
                notify { onProductsLoadFailed(result.error) }
            }
        }
    }

    /**
     * Initiates a Play purchase flow for [option] inside [activity].
     *
     * @param option              The host catalogue entry to purchase.
     * @param activity            Hosting Activity. Must not be finishing/destroyed.
     * @param basePlanId          Optional subscription base plan filter.
     * @param offerTag            Optional subscription offer tag filter.
     * @param obfuscatedAccountId Optional fraud-detection token. Max 64 chars, no PII.
     * @param isPricePersonalized Required `true` if the displayed price was personalised (EU law).
     */
    @JvmOverloads
    @MainThread
    public fun purchase(
        option: PurchasableOption,
        activity: Activity,
        basePlanId: String? = null,
        offerTag: String? = null,
        obfuscatedAccountId: String? = null,
        isPricePersonalized: Boolean = false,
    ) {
        val any = AnyPurchasableOption.of(option)
        val details = _availableProducts.value[any]
        if (details == null) {
            postFlowState(any, PurchaseFlowState.Failed(PurchaseError.ProductUnavailable))
            return
        }
        postFlowState(any, PurchaseFlowState.Purchasing)
        scope.launch {
            val activePurchases = currentActivePurchases()
            val error = withContext(mainDispatcher) {
                transactionService.launchBillingFlow(
                    activity = activity,
                    option = any,
                    productDetails = details,
                    basePlanId = basePlanId,
                    offerTag = offerTag,
                    obfuscatedAccountId = obfuscatedAccountId,
                    isPricePersonalized = isPricePersonalized,
                    activePurchases = activePurchases,
                )
            }
            if (error != null) postFlowState(any, PurchaseFlowState.Failed(error))
        }
    }

    /** Triggers a restore. Resolves via [PurchaseKitDelegate.onRestoreCompleted]. */
    @MainThread
    public fun restorePurchases() {
        scope.launch {
            connectIfNeeded()
            withContext(ioDispatcher) { transactionService.restore() }
        }
    }

    /** Re-queries Play purchases and updates [entitlements]. */
    @MainThread
    public fun refreshPurchases() {
        scope.launch {
            connectIfNeeded()
            withContext(ioDispatcher) { transactionService.refresh() }
        }
    }

    /** Synchronous entitlement check. */
    public fun isEntitled(option: PurchasableOption): Boolean =
        _entitlements.value[AnyPurchasableOption.of(option)]?.isActive == true

    /** Synchronous entitlement state lookup. Defaults to [EntitlementState.Inactive]. */
    public fun entitlementState(option: PurchasableOption): EntitlementState =
        _entitlements.value[AnyPurchasableOption.of(option)] ?: EntitlementState.Inactive

    /** Looks up the cached [ProductDetails] for [option]. */
    public fun productDetails(option: PurchasableOption): ProductDetails? =
        _availableProducts.value[AnyPurchasableOption.of(option)]

    /** Returns every subscription offer Play exposes for [option]. */
    public fun subscriptionOffers(option: PurchasableOption): List<ProductDetails.SubscriptionOfferDetails> =
        productDetails(option)?.subscriptionOfferDetails.orEmpty()

    // ---------------- Internal mechanics ----------------

    private suspend fun warmFromCache() {
        val cached = persistence.snapshot()
        val merged = HashMap(_entitlements.value)
        cached.forEach { (productId, state) ->
            optionsByProductId[productId]?.let { merged[it] = state }
        }
        _entitlements.value = merged
    }

    private suspend fun connectAndLoad() {
        connectIfNeeded()
        if (connectionState.value == BillingConnectionState.Connected) {
            try {
                loadProducts()
            } catch (t: Throwable) {
                config.logger.w(TAG, "initial loadProducts failed", t)
            }
            withContext(ioDispatcher) { transactionService.refresh() }
        }
    }

    private suspend fun connectIfNeeded() {
        if (bridge.isReady()) return
        val result = withContext(ioDispatcher) { bridge.connect() }
        config.logger.d(TAG, "connect -> ${result.responseCode}")
    }

    private suspend fun currentActivePurchases(): List<Purchase> {
        val subs = try {
            bridge.queryPurchases(com.android.billingclient.api.BillingClient.ProductType.SUBS)
        } catch (t: Throwable) {
            config.logger.w(TAG, "currentActivePurchases threw", t)
            return emptyList()
        }
        return subs.purchasesList
    }

    private fun postFlowState(option: AnyPurchasableOption, next: PurchaseFlowState) {
        _flowState.value = next
        _perOptionFlowState.value = _perOptionFlowState.value + (option to next)
        notify { onPurchaseFlowStateChanged(next, option) }
    }

    private fun applyEntitlement(option: AnyPurchasableOption, next: EntitlementState) {
        val previous = _entitlements.value[option]
        if (previous == next) return
        _entitlements.value = _entitlements.value + (option to next)
        scope.launch {
            withContext(ioDispatcher) { persistence.put(option.productId, next) }
        }
        notify { onEntitlementUpdated(option, next) }
    }

    private fun notify(action: PurchaseKitDelegate.() -> Unit) {
        listeners.toList().forEach { holder ->
            val listener = holder.get() ?: return@forEach
            try {
                listener.action()
            } catch (t: Throwable) {
                config.logger.e(TAG, "listener threw", t)
            }
        }
        compactListeners()
    }

    private fun compactListeners() {
        val dead = listeners.filter { it.get() == null }
        if (dead.isNotEmpty()) listeners.removeAll(dead)
    }

    // ---------------- Internal delegate ----------------

    private inner class InternalDelegate : TransactionServiceDelegate {

        override fun onPurchaseSucceeded(option: PurchasableOption, purchase: Purchase) {
            scope.launch {
                val verified = try {
                    withContext(ioDispatcher) { config.verifier.verify(purchase) }
                } catch (t: Throwable) {
                    config.logger.w(TAG, "verifier threw", t)
                    config.verificationFailureMode == VerificationFailureMode.Warn
                }
                if (!verified && config.verificationFailureMode == VerificationFailureMode.Block) {
                    val any = AnyPurchasableOption.of(option)
                    postFlowState(any, PurchaseFlowState.Failed(PurchaseError.VerificationFailed))
                    return@launch
                }

                if (option.purchaseType == PurchaseType.CONSUMABLE) {
                    transactionService.consume(purchase)
                } else if (!purchase.isAcknowledged) {
                    transactionService.acknowledge(purchase)
                }

                val any = AnyPurchasableOption.of(option)
                val state = buildEntitlementState(any, purchase)
                applyEntitlement(any, state)
                postFlowState(any, PurchaseFlowState.Idle)
            }
        }

        override fun onPurchasePending(option: PurchasableOption, purchase: Purchase) {
            val any = AnyPurchasableOption.of(option)
            postFlowState(any, PurchaseFlowState.Pending)
        }

        override fun onPurchaseFailed(option: PurchasableOption?, error: PurchaseError) {
            val any = option?.let(AnyPurchasableOption.Companion::of)
            if (any != null) postFlowState(any, PurchaseFlowState.Failed(error))
            else {
                _flowState.value = PurchaseFlowState.Failed(error)
                notify { onPurchaseFlowStateChanged(_flowState.value, null) }
            }
        }

        override fun onPurchaseCancelled(option: PurchasableOption?) {
            val any = option?.let(AnyPurchasableOption.Companion::of)
            if (any != null) postFlowState(any, PurchaseFlowState.Idle)
            else _flowState.value = PurchaseFlowState.Idle
        }

        override fun onPurchasesQueried(purchases: List<Purchase>) {
            reconcileFromPlay(purchases)
        }

        override fun onRestoreCompleted(purchases: List<Purchase>) {
            reconcileFromPlay(purchases)
            notify { onRestoreCompleted(_entitlements.value) }
        }

        override fun onRestoreFailed(error: PurchaseError) {
            notify { onRestoreFailed(error) }
        }

        private fun reconcileFromPlay(purchases: List<Purchase>) {
            // Source of truth: queryPurchasesAsync. Persistence is cache only.
            val nextStates = HashMap<AnyPurchasableOption, EntitlementState>(_entitlements.value)
            // Mark everything that *was* active but is absent from Play as inactive.
            for (option in optionsByProductId.values) {
                val purchase = purchases.firstOrNull { it.products.contains(option.productId) }
                nextStates[option] = if (purchase != null) {
                    buildEntitlementState(option, purchase)
                } else {
                    EntitlementState.Inactive
                }
            }
            for ((option, state) in nextStates) {
                applyEntitlement(option, state)
            }
        }

        private fun buildEntitlementState(option: PurchasableOption, purchase: Purchase): EntitlementState {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                return EntitlementState.Inactive
            }
            val txId = purchase.orderId.orEmpty()
            return when (option.purchaseType) {
                PurchaseType.NON_CONSUMABLE -> EntitlementState.NonConsumable(txId)
                PurchaseType.CONSUMABLE -> EntitlementState.Inactive // consumed
                PurchaseType.AUTO_RENEWING_SUBSCRIPTION,
                PurchaseType.NON_RENEWING_SUBSCRIPTION ->
                    EntitlementState.SubscriptionActive(0L, txId)
            }
        }
    }

    // ---------------- Listener bookkeeping ----------------

    private inner class ListenerHolder(
        listener: PurchaseKitDelegate,
        private val owner: LifecycleOwner?,
    ) {
        private val ref: WeakReference<PurchaseKitDelegate> = WeakReference(listener)
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                this@ListenerHolder.release()
                listeners.remove(this@ListenerHolder)
            }
        }

        fun get(): PurchaseKitDelegate? = ref.get()

        fun matches(target: PurchaseKitDelegate): Boolean = ref.get() === target

        fun release() {
            ref.clear()
            owner?.lifecycle?.removeObserver(observer)
        }
    }

    // ---------------- Companion (factories + Play deep links) ----------------

    public companion object {
        private const val TAG = "PurchaseKit"

        /**
         * Builds a production manager. Hosts call this once (typically from
         * `Application.onCreate`) and keep a single reference.
         *
         * @param context  Any Context; `applicationContext` is used internally.
         * @param options  Catalogue of host options. Order is preserved for paywall rendering.
         * @param config   Optional configuration. See [PurchaseKitConfig].
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            context: Context,
            options: Iterable<PurchasableOption>,
            config: PurchaseKitConfig = PurchaseKitConfig(),
        ): PurchaseKitManager {
            val any = options.map(AnyPurchasableOption.Companion::of)
            val bridge = RealBillingBridge(context.applicationContext)
            return create(context.applicationContext, any, config, bridge)
        }

        internal fun create(
            context: Context,
            options: List<AnyPurchasableOption>,
            config: PurchaseKitConfig,
            bridge: BillingBridge,
            mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): PurchaseKitManager {
            val logger = config.logger
            val persistence = PersistenceService(context, config.persistencePrefsName, logger, ioDispatcher)
            val productService = ProductService(bridge, logger)
            return PurchaseKitManager(
                options = options,
                config = config,
                bridge = bridge,
                productService = productService,
                persistence = persistence,
                mainDispatcher = mainDispatcher,
                ioDispatcher = ioDispatcher,
            )
        }

        /**
         * Opens the Play Store subscription management sheet. When [productId]
         * is provided, deep-links into that subscription; otherwise opens the
         * generic subscriptions list.
         *
         * Threading: must be called on the main thread.
         *
         * @param context   Any Context (uses `applicationContext` for the intent).
         * @param productId Optional Play SKU to focus.
         */
        @JvmStatic
        @JvmOverloads
        @MainThread
        public fun openSubscriptionManagement(context: Context, productId: String? = null) {
            val uri = if (productId != null) {
                Uri.parse("https://play.google.com/store/account/subscriptions?sku=$productId&package=${context.packageName}")
            } else {
                Uri.parse("https://play.google.com/store/account/subscriptions")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.applicationContext.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                PurchaseKitLogger.Default.i(TAG, "No activity found to handle subscription management")
            }
        }
    }
}

