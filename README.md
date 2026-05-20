<!-- SPDX-License-Identifier: Apache-2.0 -->

# PurchaseKit-Android

**PurchaseKit-Android** is an Apache-2.0 licensed, app-agnostic facade over
the Google Play Billing Library v8+. It is the Android sibling of
[PurchaseKit (iOS)](https://github.com/markusmock/PurchaseKit) — same
product, same vocabulary, same paywall ergonomics on both platforms.

Drop one library in, define an `enum` of your products, hand a
`PurchaseKitManager` to `Application.onCreate`, and ship your paywall the
same afternoon — whether your app uses Jetpack Compose or pure View/XML.

Licensed under **Apache-2.0**.

---

## Features

- **Play Billing v8+ product loading**
  - Loads `ProductDetails` for your app-defined `PurchasableOption`s.
  - Publishes `availableProducts` for paywalls / settings UI.
- **Purchasing**
  - Play Billing purchase flow with explicit replacement modes.
  - Acknowledgement + consumption + pending-purchase handling.
  - Normalised `PurchaseFlowState` (`Idle`, `Purchasing`, `Pending`, `Failed`).
- **Restore / Refresh / Foreground sync**
  - `BillingClient.queryPurchasesAsync` as the source of truth.
  - Cold cache via `SharedPreferences` + `org.json`, never as truth.
- **Entitlements**
  - Normalised `EntitlementState` (`Inactive`, `NonConsumable`,
    `SubscriptionActive`, `SubscriptionExpired`, `Revoked`).
  - Convenience checks for gating (`isEntitled`, `isActive`).
- **Offerings & Features (optional)**
  - Group options via `offeringId` (e.g. monthly + yearly under `"pro"`).
  - Host-defined `Feature` / `PurchaseOffering` describe "what gets unlocked".
- **Optional network awareness**
  - Inject a `NetworkService` to expose `canAttemptNetworkOperations`.
- **Optional Compose building blocks** (`:purchasekit-compose`)
  - `PurchaseButton`, `RestorePurchasesButton`, `ManageSubscriptionsButton`,
    `LegalLinksRow`, `PaywallScaffold`.
- **First-class View/XML/Fragment support**
  - Core module has **zero** Compose dependencies (direct or transitive).
  - `PurchaseKitDelegate` callback path for hosts that prefer no coroutines.
- **Java interop**
  - Public API callable from Java; see `JavaInteropDemo.java` in `:sample-views`.

> Android-only by design: this library does not ship promo-code redemption
> UI (Google Play handles that externally). iOS-only: StoreKit 2 Offer Code
> Sheet, transaction listeners.

---

## Requirements

- **minSdk 24**, **compileSdk 35**
- Kotlin **2.0+**
- Google Play Billing **8.x**
- Java 17 toolchain

---

## Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// your app or feature module
dependencies {
    implementation("com.markusmock:purchasekit:0.1.0")          // core
    implementation("com.markusmock:purchasekit-compose:0.1.0")  // optional Compose UI
}
```

Maven coordinates are placeholders — publish from a tag when ready.

---

## Quick Setup (Recommended)

### 1) Define your purchasable options

```kotlin
import com.markusmock.purchasekit.api.PurchasableOption
import com.markusmock.purchasekit.api.PurchaseType
import com.markusmock.purchasekit.api.TierBadge

enum class AppOption(
    override val productId: String,
    override val purchaseType: PurchaseType,
    override val title: String,
    override val sortOrder: Int,
    override val badge: TierBadge? = null,
) : PurchasableOption {
    Monthly("app.pro.monthly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Pro Monthly", 0),
    Yearly("app.pro.yearly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Pro Yearly", 1, badge = TierBadge.BestValue),
    Lifetime("app.lifetime", PurchaseType.NON_CONSUMABLE, "Lifetime", 2);

    override val subscriptionGroup: String? get() = if (purchaseType.isSubscription) "pro" else null
}
```

### 2) Install PurchaseKit in `Application.onCreate`

```kotlin
import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.markusmock.purchasekit.PurchaseKitManager

class MyApp : Application() {
    lateinit var purchaseKit: PurchaseKitManager
        private set

    override fun onCreate() {
        super.onCreate()
        purchaseKit = PurchaseKitManager.create(this, AppOption.entries)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { purchaseKit.onAppEnteredForeground() }
        })
    }
}
```

### 3) Use it anywhere

Either collect the `StateFlow`s directly (preferred) or register a
`PurchaseKitDelegate`. Both paths are first-class — see the dedicated
sections below.

---

## Using with Jetpack Compose

`:purchasekit-compose` is optional but ships with five primitives that wrap
the state flows. Use them or read the flows yourself.

```kotlin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markusmock.purchasekit.PurchaseKitManager
import com.markusmock.purchasekit.api.AnyPurchasableOption
import com.markusmock.purchasekit.compose.LegalLinksRow
import com.markusmock.purchasekit.compose.ManageSubscriptionsButton
import com.markusmock.purchasekit.compose.PaywallScaffold
import com.markusmock.purchasekit.compose.PurchaseButton
import com.markusmock.purchasekit.compose.RestorePurchasesButton

class PaywallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = (application as MyApp).purchaseKit
        setContent { PaywallRoot(manager) }
    }
}

@Composable
fun PaywallRoot(manager: PurchaseKitManager) {
    val entitlements by manager.entitlements.collectAsStateWithLifecycle()
    val flowState by manager.flowState.collectAsStateWithLifecycle()
    val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity

    PaywallScaffold(
        title = "Unlock Pro",
        footer = {
            RestorePurchasesButton(onRestore = manager::restorePurchases)
            ManageSubscriptionsButton()
            LegalLinksRow(termsUrl = "https://example.com/terms", privacyUrl = "https://example.com/privacy")
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppOption.entries.forEach { option ->
                val state = entitlements[AnyPurchasableOption.of(option)]
                    ?: com.markusmock.purchasekit.model.EntitlementState.Inactive
                PurchaseButton(
                    entitlement = state,
                    flowState = flowState,
                    onPurchase = { manager.purchase(option, activity) },
                    labelIdle = option.title,
                )
            }
        }
    }
}
```

See `:sample-compose` for the full app (~60 lines of business logic).

---

## Using with Views / Fragments (no Compose)

This section is a peer of the Compose one, not a footnote.
`:purchasekit-compose` is **not** required —
`implementation("com.markusmock:purchasekit:VERSION")` alone is enough.
Verified by `./gradlew :sample-views:dependencies | grep -i compose` →
empty.

### `StateFlow` collection from a Fragment / Activity

```kotlin
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class PaywallActivity : AppCompatActivity() {

    private val manager get() = (application as MyApp).purchaseKit

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paywall)

        findViewById<View>(R.id.buyMonthly).setOnClickListener {
            manager.purchase(AppOption.Monthly, this)
        }
        findViewById<View>(R.id.restore).setOnClickListener {
            manager.restorePurchases()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { manager.flowState.collect { state ->
                    findViewById<TextView>(R.id.flowStateLabel).text = state.toString()
                } }
                launch { manager.entitlements.collect { /* enable/disable CTAs */ } }
            }
        }
    }
}
```

### Pure-callback `PurchaseKitDelegate` (no coroutines)

For Fragments / Activities / non-Android coordinator classes that prefer
plain callbacks:

```kotlin
class FeatureController(
    private val manager: PurchaseKitManager,
    private val owner: LifecycleOwner,
) : PurchaseKitDelegate {

    fun attach() { manager.addListener(this, owner) }

    override fun onEntitlementUpdated(option: AnyPurchasableOption, state: EntitlementState) {
        // toggle features
    }

    override fun onPurchaseFlowStateChanged(state: PurchaseFlowState, option: AnyPurchasableOption?) {
        // update CTA spinner
    }
}
```

`addListener(listener, owner)` automatically removes the listener on the
owner's `ON_DESTROY`. Passing `null` (the default) requires manual
`removeListener` cleanup.

### Java interop

The full public API is callable from Java. See
[`sample-views/.../JavaInteropDemo.java`](sample-views/src/main/java/com/markusmock/purchasekit/sample/views/JavaInteropDemo.java)
— it exercises `PurchaseKitManager.create(...)`, `addListener(...)`,
`purchase(...)`, `restorePurchases()`, `openSubscriptionManagement(...)`,
and a Java implementation of `PurchasableOption`. The factory and helper
methods are `@JvmStatic`; defaulted-arg functions use `@JvmOverloads`.

---

## Optional: Install with NetworkService

PurchaseKit does **not** require connectivity awareness — Play returns
sensible errors when offline. Inject a `NetworkService` if you want a
fast-fail UX (disable CTAs before calling Play):

```kotlin
import com.markusmock.purchasekit.PurchaseKitConfig
import com.markusmock.purchasekit.service.ConnectivityManagerNetworkService

val manager = PurchaseKitManager.create(
    context = this,
    options = AppOption.entries,
    config = PurchaseKitConfig(networkService = ConnectivityManagerNetworkService(this)),
)

// In your paywall:
if (manager.canAttemptNetworkOperations.value) manager.purchase(option, activity)
```

---

## Offerings (Grouping Paywall Options)

`PurchasableOption.offeringId` lets you group options into paywall sections.
Typical use cases:

- group monthly + yearly under one plan (e.g. `"pro"`),
- separate consumer vs team offerings,
- build a paywall with multiple sections.

```kotlin
fun Iterable<AnyPurchasableOption>.groupedByOffering(): Map<String, List<AnyPurchasableOption>> =
    groupBy { it.offeringId ?: "default" }.mapValues { it.value.sortedBy(AnyPurchasableOption::sortOrder) }

val grouped = manager.entitlements.value.keys.groupedByOffering()
```

---

## Optional: Feature Gating

PurchaseKit ships primitives (`isEntitled`, `entitlements` StateFlow) and
nothing else. Feature gating is an application-level concern — build your
own coordinator on top:

```kotlin
class FeatureGate(private val manager: PurchaseKitManager) {
    val hasPro: Boolean get() = manager.hasAnyActiveSubscription.value || manager.isEntitled(AppOption.Lifetime)
}
```

The library deliberately does **not** ship a feature-gating coordinator
analogue (Link2's `FeatureAccessCoordinator` was app-specific).

---

## UI Building Blocks (Compose only)

`:purchasekit-compose` ships five minimal primitives. They are intentionally
small — paywall layouts are product decisions and live in your app.

- `PurchaseButton(entitlement, flowState, onPurchase, labelIdle)` — the CTA.
- `RestorePurchasesButton(onRestore)` — text button.
- `ManageSubscriptionsButton(productId?)` — deep-links to Play Store.
- `LegalLinksRow(termsUrl?, privacyUrl?, infoUrl?)` — horizontal text-buttons row.
- `PaywallScaffold(title, subtitle?, footer, content)` — column + scroll wrapper.

**Hosts using Views/XML build their own UI on top of the state flows /
delegate.** This is intentional, not a gap.

---

## Best Value / Savings Badges

`PurchaseKit` keeps badge presentation in the host app. The library only
classifies them via `TierBadge`:

```kotlin
enum class AppOption(
    override val productId: String,
    override val purchaseType: PurchaseType,
    override val title: String,
    override val sortOrder: Int,
    override val badge: TierBadge? = null,
) : PurchasableOption {
    Monthly("…", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Monthly", 0),
    Yearly("…", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Yearly", 1, badge = TierBadge.BestValue),
    Premium("…", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Premium", 2, badge = TierBadge.SavePercent(33));
}
```

`TierBadge.defaultText` returns an English fallback. Production apps map
the sealed subtype to their own localised resources.

---

## Delegate (optional)

For callback-oriented hosts — UIKit-style ports, Java callers, Fragments
that don't want coroutines — implement `PurchaseKitDelegate`. See the
[Views / Fragments](#using-with-views--fragments-no-compose) section above
for a Kotlin example and [`JavaInteropDemo.java`](sample-views/src/main/java/com/markusmock/purchasekit/sample/views/JavaInteropDemo.java)
for the Java version.

Every callback fires on the **main thread**.

---

## Promo Codes

Google Play handles promo-code redemption externally — there is no in-app
sheet. Point users at
[`PurchaseKitManager.openSubscriptionManagement(context)`](purchasekit/src/main/kotlin/com/markusmock/purchasekit/PurchaseKitManager.kt)
or
`https://play.google.com/redeem?code=…`. The library's role ends once the
purchase appears in `queryPurchasesAsync` — at which point entitlements
update normally.

---

## Network / Reachability (Optional)

PurchaseKit does **not** require reachability. Play returns errors when the
device is offline.

For better UX, inject a `NetworkService` into `PurchaseKitConfig` (see
[Optional: Install with NetworkService](#optional-install-with-networkservice)
above). The provided `ConnectivityManagerNetworkService` wraps the system
`ConnectivityManager` default-network callback.

---

## Server-side Receipt Verification

`PurchaseKit` exposes a `PurchaseVerifier` seam. The default
(`PurchaseVerifier.NoOp`) trusts Play's signature. Production apps with a
backend should implement against their own validation endpoint:

```kotlin
class MyVerifier(private val api: BackendApi) : PurchaseVerifier {
    override suspend fun verify(purchase: com.android.billingclient.api.Purchase): Boolean =
        api.validate(purchase.purchaseToken, purchase.products.firstOrNull()).isOk
}

val config = PurchaseKitConfig(
    verifier = MyVerifier(api),
    verificationFailureMode = VerificationFailureMode.Block,  // or .Warn while wiring up
)
```

`Block` rejects unverified entitlements (`PurchaseError.VerificationFailed`).
`Warn` logs the failure and grants the entitlement anyway — useful when
launching server-side validation in stages.

---

## Notes

- **Not a singleton.** Hosts own the lifetime — typically one instance on
  the `Application`. Call `shutdown()` if you want to release everything
  early.
- **Persistence is cache, Play is truth.** The on-disk JSON cache exists to
  paint UI during cold start. Every `queryPurchasesAsync` overwrites it.
  No purchase tokens or PII are persisted.
- **Localisation is the host's job.** `PurchaseError.code` is a stable
  string for analytics / resource lookup; `TierBadge.defaultText` is an
  English fallback only.
- **Compose is optional.** `:purchasekit` works in pure View/XML apps; the
  build verifies zero-Compose-dependency on every release.
- **`PurchaseKit.openSubscriptionManagement(context, productId?)`** is the
  Android equivalent of iOS's `AppStore.showManageSubscriptions(in:)`.

---

## Support

If PurchaseKit-Android saves you an afternoon, a ⭐ on GitHub is appreciated.
