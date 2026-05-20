# PurchaseKit‑Android — Build Prompt

> **Hand this entire file to a fresh Claude Code (or other capable) agent.** It is self‑contained and includes everything needed to produce a production‑grade, open‑source Android library that wraps the Google Play Billing Library behind a clean, app‑agnostic API — the spiritual sibling of `PurchaseKit` for iOS.

---

## 0. Activate Skills (do this FIRST)

Before writing a single line of code, **load and apply these skills for the entire engagement.** Re‑invoke them whenever the relevant phase begins:

| Skill | When to apply |
|---|---|
| `/anthropic-skills:modern-app-architecture` | Whole project. Enforce SSOT, Clean Architecture, UDF, modularity, security‑by‑design, offline‑first awareness, observability hooks. |
| `/anthropic-skills:android-kotlin-compose` | All `*.kt` files. Compose state APIs, Coroutines/Flow with `WhileSubscribed(5_000)`, Lifecycle‑aware collection, Material 3, `build.gradle.kts`, Gradle Version Catalog. |
| `/engineering:architecture` | Module boundaries, dependency direction, write at least one ADR in `documentation/adr/` for: (1) "no DI framework" decision, (2) "JSON persistence without third‑party libs" decision, (3) "module split: core vs compose". |
| `/engineering:system-design` | Designing the public API surface, the contract types, and the service composition. |
| `/engineering:documentation` | Every public symbol gets full English KDoc. Top‑level README mirrors the structure of the iOS PurchaseKit README. |
| `/engineering:testing-strategy` | Test pyramid: pure unit tests for policies/mappers, fakes for `BillingClient`, instrumentation tests are optional. |
| `/engineering:code-review` | After implementation, run a self‑review pass: leaks, threading, error handling, public API ergonomics. |
| `/design:accessibility-review` | For any Compose UI building blocks shipped in `:purchasekit-compose`. |

If you do not have one of these skills, explicitly note it and continue with first‑principles equivalents.

---

## 1. Mission

Build **PurchaseKit‑Android** — an Apache‑2.0‑licensed, open‑source Android library that gives any host app a clean, type‑safe, reactive, leak‑free, Compose‑optional facade over the Google Play Billing Library (v8+).

**Why this exists**

- Wrapping Play Billing is a recurring, error‑prone chore: connection lifecycle, acknowledgement, pending purchases, restoration, replacement modes, network awareness, EU personalised‑pricing flag, obfuscated account IDs, manage‑subscription deep links — every team re‑implements these and gets at least one of them wrong.
- `PurchaseKit` (iOS) already proves the abstraction works for StoreKit 2. This is its Android sibling. The two libraries should feel like the same product on both platforms.
- A working but app‑coupled implementation already exists in **Link2** (see §4). Use it as a battle‑tested template — then **architecturally clean it up, decouple it, document it, and harden it.**

**Target user**

> An Android developer who runs `implementation("com.markusmock:purchasekit:1.0.0")`, defines an `enum` of their products, drops one `PurchaseKitManager` into `Application.onCreate`, and ships a paywall the same afternoon — without having to learn the `BillingClient` API, replacement modes, or acknowledgement semantics.

---

## 2. Hard Constraints (non‑negotiable)

1. **License: Apache 2.0.** Keep the existing `LICENSE` file. Add SPDX headers to every source file.
2. **Minimal dependencies.** Allowed list — no others without an ADR:
   - `com.android.billingclient:billing-ktx` (the thing we are wrapping; mandatory)
   - `org.jetbrains.kotlinx:kotlinx-coroutines-core`
   - `org.jetbrains.kotlinx:kotlinx-coroutines-android`
   - `androidx.lifecycle:lifecycle-common` (for `LifecycleOwner` / `DefaultLifecycleObserver`)
   - `androidx.lifecycle:lifecycle-runtime-ktx`
   - `androidx.annotation:annotation`
   - **For `:purchasekit-compose` only:** `androidx.compose.*` (BoM), `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-runtime-compose`. The core module must NOT depend on Compose.
   - **For persistence:** use `org.json.JSONObject` (Android built‑in) or hand‑rolled string encoding. **No Gson, no Moshi, no kotlinx.serialization.** If you genuinely need structured persistence and a strong case for `kotlinx.serialization`, write an ADR and ask.
   - **For DI:** none. Use constructor injection by hand. **No Hilt, no Koin, no Dagger.**
   - **For logging:** wrap `android.util.Log` behind a tiny internal `Logger` interface so consumers can swap it.
2a. **View/XML/Fragment apps are FIRST‑CLASS consumers — equal standing with Compose apps.** The library must be **fully usable from an app that does not pull in any `androidx.compose.*` artifact**. This means:
   - `:purchasekit` core has zero Compose dependencies (verify with `./gradlew :purchasekit:dependencies | grep -i compose` — must return nothing).
   - `StateFlow` is consumed in Views via `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { flow.collect { ... } } }` — document this pattern explicitly in README and KDoc.
   - `PurchaseKitDelegate` is the supported callback path for Fragments/Activities that prefer not to touch coroutines at all (also the Java‑interop path).
   - Java callers must be able to use the public API. Annotate suspend functions with `@JvmSynthetic` where appropriate; provide `@JvmStatic` factory methods; verify by adding **one Java file** in `:sample-views` that exercises the public API. Use `@JvmOverloads` on Kotlin functions with default arguments that Java consumers need.
   - **No Compose APIs leak into the core public surface** — no `@Composable`, no `MutableState`, no `androidx.compose.runtime.*` imports in `:purchasekit/src/main`.
3. **No singletons in the library.** The host app owns the manager's lifetime. (`InAppPurchaseManager.create(context)` factory is allowed but does not store a global instance.)
4. **No host coupling.** The library must compile and run without any reference to `Link2Application`, `R.string.*`, `localeHelper`, or any host symbol. All host‑provided strings/resources are passed in or resolved via host‑defined `PurchasableOption.title` etc.
5. **No memory leaks.**
   - Never hold an `Activity` or `Context` longer than a method call. Use `applicationContext` when persistence is needed.
   - Listeners stored as `WeakReference`, with optional `LifecycleOwner` auto‑removal.
   - `CoroutineScope` is `SupervisorJob + Dispatchers.Main.immediate`, cancelled in a `shutdown()`/`close()` method.
   - `BillingClient.endConnection()` is called on shutdown.
6. **Threading contract is documented and enforced.** Public state is exposed as `StateFlow` on the main thread; suspend functions run their I/O on `Dispatchers.IO`; delegate callbacks fire on the main thread (annotate with `@MainThread`).
7. **Public API stability.** Mark everything that is not part of the surface as `internal`. Mark unstable APIs with `@ExperimentalPurchaseKitApi` (own opt‑in annotation).
8. **English KDoc on every public symbol** — class, property, function, parameter, return, throws, threading note, since‑version. Match the depth shown in the iOS code (see §5.2).
9. **README in English**, mirroring the section structure of `/Users/markusehmer/Apps/iOS/PurchaseKit/README.md` so the two libraries look like one product.
10. **A `:sample` module must exist** and demonstrate the recommended integration in <60 lines of Kotlin (`Application` setup + a Compose paywall).
11. **Tests must pass.** Pure JVM unit tests for all logic that does not touch `BillingClient`. A fake `BillingClient` for the service tests.
12. **`./gradlew build` must succeed** with `--warning-mode all` and zero warnings from PurchaseKit modules (third‑party warnings are out of scope).

---

## 3. Where Things Live (read these before coding)

| Path | What it is | How to use it |
|---|---|---|
| `/Users/markusehmer/Apps/iOS/PurchaseKit/` | **Inspiration & design reference.** Mirror its public API shape, naming, KDoc depth, README structure. | Read the entire `Sources/PurchaseKit/` tree and the README before designing the public API. Treat it as the "what good looks like" benchmark. |
| `/Users/markusehmer/Apps/Android/Link2/app/src/main/java/com/sigmasport/link2/backend/inAppPurchase/` | **Working template.** Production code that has shipped. | Use as the implementation reference for `BillingClient` wiring, acknowledgement, replacement modes, pending purchases, etc. **Do not copy verbatim** — clean it up per §5.1. |
| `/Users/markusehmer/Apps/Android/Link2/app/src/main/java/com/sigmasport/link2/ui/upgradeRide/` | **Real paywall consumer.** Shows how the manager is wired into a Fragment + Compose ViewModel. | Study to understand the integration ergonomics you need to deliver. The `UpgradeRideViewModel` is a good "from‑the‑outside" view. |
| `/Users/markusehmer/Apps/Android/Link2/app/src/main/java/com/sigmasport/link2/ui/inAppPurchase/` | **Feature‑gating coordinator pattern.** | Inspire — but feature gating is an *application‑level* concern, not a library concern. Expose primitives (`isEntitled`, entitlement `StateFlow`), let consumers build their own coordinator. Do NOT ship a `FeatureAccessCoordinator` analogue in the library. |
| `/Users/markusehmer/Apps/Android/Link2/app/src/main/java/com/sigmasport/link2/Link2Application.kt` (lines 188–270) | Real call site of `InAppPurchaseManager.create()` and `onAppEnteredForeground()`. | Verify your factory ergonomics match this real usage. |
| `/Users/markusehmer/Apps/Android/PurchaseKit/` | **Target directory.** Currently contains only `LICENSE` and a stub `README.md`. | Build everything here. |

### Mandatory discovery pass before coding

Run (and report what you find, before writing code):

```bash
grep -rn "InAppPurchaseManager" /Users/markusehmer/Apps/Android/Link2/app/src/main/java
grep -rn "InAppPurchaseListener" /Users/markusehmer/Apps/Android/Link2/app/src/main/java
grep -rn "addListener\|removeListener" /Users/markusehmer/Apps/Android/Link2/app/src/main/java/com/sigmasport/link2/backend/inAppPurchase
```

For every call site you find, write down: *what does the caller need from the library?* That list defines your minimum public API. Anything not on that list is a candidate for `internal`.

---

## 4. Architecture

### 4.1 Module layout

```
PurchaseKit/
├── settings.gradle.kts
├── build.gradle.kts                  # root, no plugins applied
├── gradle/libs.versions.toml         # version catalog (single source of truth for deps)
├── purchasekit/                      # the core library (Kotlin only, NO Compose)
│   ├── build.gradle.kts              # android-library, kotlin-android
│   └── src/main/kotlin/com/markusmock/purchasekit/...
├── purchasekit-compose/              # OPTIONAL Compose building blocks
│   ├── build.gradle.kts              # android-library, compose enabled
│   └── src/main/kotlin/com/markusmock/purchasekit/compose/...
├── sample-compose/                   # demo app using ONLY :purchasekit + :purchasekit-compose
│   ├── build.gradle.kts              # com.android.application, compose enabled
│   └── src/main/kotlin/com/markusmock/purchasekit/sample/compose/...
├── sample-views/                     # demo app using ONLY :purchasekit (no Compose at all)
│   ├── build.gradle.kts              # com.android.application, ViewBinding enabled, NO compose plugin
│   ├── src/main/kotlin/com/markusmock/purchasekit/sample/views/...   # Fragments + ViewModels + ViewBinding
│   ├── src/main/kotlin/com/markusmock/purchasekit/sample/views/JavaInteropDemo.java   # one .java file to prove Java-interop
│   └── src/main/res/layout/...       # XML paywall layout
├── documentation/
│   ├── adr/0001-no-di-framework.md
│   ├── adr/0002-json-persistence-built-in.md
│   ├── adr/0003-module-split-core-compose.md
│   └── architecture.md
├── README.md                         # mirrors iOS README structure
├── CHANGELOG.md
└── LICENSE                           # already present, keep
```

**Dependency direction is one‑way:**
- `:sample-compose` → `:purchasekit-compose` → `:purchasekit`
- `:sample-views` → `:purchasekit` (does NOT depend on `:purchasekit-compose`; proves Compose is truly optional)

Core knows nothing of Compose. Compose knows nothing of the samples. The two sample apps prove both consumption paths work and are equally well documented.

### 4.2 Core package layout (inside `purchasekit/`)

```
com.markusmock.purchasekit/
├── PurchaseKitManager.kt              # public — facade
├── PurchaseKitDelegate.kt             # public — callback interface
├── PurchaseKitManagerProtocol.kt      # public — interface for DI / fakes (rename to PurchaseKitFacade if "Protocol" feels Swifty in Kotlin)
├── api/
│   ├── PurchasableOption.kt           # public — host implements
│   ├── PurchaseType.kt                # public — enum: NON_CONSUMABLE, CONSUMABLE, AUTO_RENEWING_SUBSCRIPTION, NON_RENEWING_SUBSCRIPTION
│   ├── PurchaseOffering.kt            # public — optional grouping contract
│   ├── Feature.kt                     # public — optional feature contract
│   ├── TierBadge.kt                   # public — sealed class: BestValue, MostPopular, SavePercent(Int), Custom(String)
│   └── AnyPurchasableOption.kt        # public — type-erased wrapper for collections/maps
├── model/
│   ├── EntitlementState.kt            # public — sealed: Inactive, NonConsumable(txId), SubscriptionActive(expiry,txId), SubscriptionExpired(expiry), Revoked(date)
│   ├── PurchaseFlowState.kt           # public — sealed: Idle, Purchasing, Pending, Failed(PurchaseError)
│   └── PurchaseError.kt               # public — sealed (port the Link2 version, drop @StringRes — leave message-mapping to the host)
├── service/
│   ├── ProductService.kt              # internal — wraps queryProductDetailsAsync
│   ├── TransactionService.kt          # internal — wraps connect/launch/acknowledge/query/restore
│   ├── TransactionServiceDelegate.kt  # internal
│   ├── PersistenceService.kt          # internal — SharedPreferences + org.json
│   └── NetworkService.kt              # public — optional, host can inject custom NetworkService
├── policy/
│   ├── SubscriptionExclusivityPolicy.kt   # internal — port iOS analogue
│   ├── SubscriptionReplacementPolicy.kt   # public — host can override; default = tier-based (port from Link2)
│   └── PurchaseVerifier.kt                # public interface — default = NoOpPurchaseVerifier (host can plug in server verification)
├── support/
│   ├── PurchaseKitLogger.kt           # internal interface, simple Logcat default
│   ├── ProductDetailsExtensions.kt    # internal — pickOfferToken, formatted price helpers
│   └── PlayStoreAvailability.kt       # internal — isGooglePlayStoreInstalled()
└── annotation/
    └── ExperimentalPurchaseKitApi.kt  # public — opt-in annotation
```

### 4.3 Compose package layout (inside `purchasekit-compose/`)

```
com.markusmock.purchasekit.compose/
├── PurchaseButton.kt                  # Material 3 CTA; takes EntitlementState + PurchaseFlowState + onClick
├── RestorePurchasesButton.kt
├── ManageSubscriptionsButton.kt
├── LegalLinksRow.kt                   # Terms / Privacy slots
├── PaywallScaffold.kt                 # optional opinionated layout that ties the above together
└── theme/PurchaseKitDefaults.kt       # neutral defaults, no app branding
```

These are deliberately minimal. The Link2 paywall (tier carousel, free‑trial row, etc.) is **product‑specific** and should NOT be in the library. Ship primitives, let consumers compose.

### 4.4 Public API surface (final list — keep it small)

```kotlin
// Configuration & lifecycle
PurchaseKitManager.create(context, options, config = PurchaseKitConfig()): PurchaseKitManager
fun PurchaseKitManager.shutdown()
fun PurchaseKitManager.onAppEnteredForeground()
fun PurchaseKitManager.addListener(listener, owner: LifecycleOwner? = null)
fun PurchaseKitManager.removeListener(listener)

// Operations
suspend fun PurchaseKitManager.loadProducts()
fun PurchaseKitManager.purchase(option, activity, basePlanId?, offerTag?, obfuscatedAccountId?, isPricePersonalized = false)
fun PurchaseKitManager.restorePurchases()
fun PurchaseKitManager.refreshPurchases()
fun PurchaseKitManager.openSubscriptionManagement(context, productId: String? = null) // companion

// State (StateFlow)
val entitlements: StateFlow<Map<AnyPurchasableOption, EntitlementState>>
val availableProducts: StateFlow<Map<AnyPurchasableOption, ProductDetails>>
val flowState: StateFlow<PurchaseFlowState>
val perOptionFlowState: StateFlow<Map<AnyPurchasableOption, PurchaseFlowState>>
val hasAnyActiveSubscription: StateFlow<Boolean>
val primaryActiveSubscription: StateFlow<AnyPurchasableOption?>
val canAttemptNetworkOperations: StateFlow<Boolean>
val connectionState: StateFlow<BillingConnectionState>   // CONNECTING/CONNECTED/DISCONNECTED — our own enum, not int

// Convenience
fun PurchaseKitManager.isEntitled(option): Boolean
fun PurchaseKitManager.entitlementState(option): EntitlementState
fun PurchaseKitManager.productDetails(option): ProductDetails?
fun PurchaseKitManager.subscriptionOffers(option): List<ProductDetails.SubscriptionOfferDetails>
```

Anything else (the services, the policies' default impl details, the persistence file layout) is `internal`.

### 4.5 `PurchaseKitConfig` (single configuration object — no overload explosion)

```kotlin
data class PurchaseKitConfig(
    val logger: PurchaseKitLogger = PurchaseKitLogger.Default,
    val networkService: NetworkService? = null,            // null = library does not check network
    val verifier: PurchaseVerifier = NoOpPurchaseVerifier, // host can plug in server validation
    val replacementPolicy: SubscriptionReplacementPolicy = SubscriptionReplacementPolicy.Default,
    val persistencePrefsName: String = "purchasekit_prefs",
    val reconnectDelay: Duration = 2.seconds,
)
```

---

## 5. What to learn from the references

### 5.1 What to improve over Link2

Read the Link2 implementation as a working draft, then fix these things in PurchaseKit‑Android:

| Smell in Link2 | Fix in PurchaseKit |
|---|---|
| `IapProduct` is a hard‑coded `enum class` inside the library namespace. | Define a `PurchasableOption` **interface** (Kotlin `interface`, host implements as `enum`). Mirror iOS `PurchasableOption` protocol. Use `AnyPurchasableOption` for type‑erased collections. |
| `PersistenceService` depends on **Gson**. | Use `org.json.JSONObject` (built‑in) with a tiny hand‑written codec. Persist only non‑sensitive derived state (the per‑option `EntitlementState` discriminator + epoch millis + transaction id). Never store purchase tokens or PII. |
| `R.string.in_app_purchase_*` referenced from the manager. | Library never reaches into host resources. `PurchaseError` exposes a stable `code: String` and *optional* `messageKey` field the host can map; the library returns *errors*, not *strings*. |
| `Link2Application.localeHelper.getString(...)` calls leak host coupling into a "reusable" component. | Forbidden. Library is host‑agnostic. |
| `_errorMessage: StateFlow<String?>` returns a localized string. | Replace with `StateFlow<PurchaseError?>`. Host decides how to render. |
| `InAppPurchaseManager.create()` runs `initialize()` synchronously, including reading `SharedPreferences` on the calling thread. | Same factory shape is fine, but offload disk I/O to `Dispatchers.IO` inside `init`. Return immediately. |
| `connectionState: StateFlow<Int>` exposes the raw `BillingClient.ConnectionState` int. | Wrap in a sealed/`enum class BillingConnectionState { Connecting, Connected, Disconnected, Unavailable }`. |
| Reconnection: `delay(RECONNECT_DELAY_MS)` then re‑enter `connectToBillingService()` — recursive, unbounded retries. | Use the BillingClient v6+ built‑in `enableAutoServiceReconnection()` (Link2 already does this) AND remove the manual retry loop. Manual reconnect only on explicit user action. |
| `getOffer(product)` returns the first offer matching `basePlanId`. | Keep, but expose `subscriptionOffers(option)` (plural) so consumers can pick by tag. |
| Listener registration takes `LifecycleOwner` and observes `ON_DESTROY` — good. But the lifecycle observer is anonymous; if the listener is registered twice with different owners, the first observer leaks until ON_DESTROY. | Track owners by listener identity; tear down the observer if the listener is removed manually. |
| `notifyListeners` snapshots the list, but iteration can call back into `addListener`/`removeListener` and produce subtle reentrancy. | Document reentrancy explicitly. Use `CopyOnWriteArrayList<WeakReference<...>>` and an idempotent compaction pass. |
| `purchase(...)` accepts `Activity` directly — fine, but the manager has no defense if Activity is finishing. | Add a guard: `if (activity.isFinishing \|\| activity.isDestroyed) return Failure(...)`. |
| `processPurchases` reconciles missing entitlements by reading **persisted** state, which can resurrect stale `Expired` states across reinstalls. | Source of truth is `BillingClient.queryPurchasesAsync`. Persistence is **cache only** — used to render UI before Play responds, then overwritten by truth. Document this. |
| Subscription replacement is hard‑coded in `SubscriptionReplacementPolicy.replacementMode(from, to)` using `tier.ordinal`. | Make `SubscriptionReplacementPolicy` an interface with a default tier‑based impl, so hosts can override. |
| No server‑side verification hook actually exists despite the KDoc claim. | Implement `PurchaseVerifier` as a real seam: `suspend fun verify(purchase: Purchase): Boolean`. Default = always `true`. Host can implement against their backend. Block entitlement grant until `verify()` returns true (configurable: warn vs. block). |

### 5.2 Patterns to mirror from iOS

| iOS pattern | Android translation |
|---|---|
| `PurchasableOption` protocol with `id`, `productId`, `purchaseType`, `title`, `subtitle`, `sortOrder`, `offeringId`, `badge` | Kotlin `interface PurchasableOption` with the same fields. Make it `Hashable` via `equals/hashCode` based on `productId`. |
| `AnyPurchaseOption` type erasure | Kotlin: `data class AnyPurchasableOption(...)` that snapshots the values and implements `PurchasableOption`. |
| Manager exposes both `@Published` (Combine) AND `PurchaseKitDelegate` callbacks | Manager exposes both `StateFlow` AND `PurchaseKitDelegate` (mirror Link2's `InAppPurchaseListener`). |
| Subscription exclusivity policy enforces "one active sub per offering" | Same — port `SubscriptionExclusivityPolicy` to Kotlin. |
| Network awareness is opt‑in via `NetworkService` parameter | Same — `PurchaseKitConfig.networkService` is nullable. |
| `IAPLogger` wraps `os.Logger` | `PurchaseKitLogger` wraps `android.util.Log` behind an interface. |
| `presentPromoCodeRedemption(from:)` | Android has no in‑app sheet; expose `openSubscriptionManagement()` and document that promo codes are redeemed on the Play Store website / app. |
| README structure: Features → Requirements → Installation → Quick Setup → Optional NetworkService → Offerings → Feature Gating → UI Building Blocks → Best Value Badges → Delegate → Promo Codes → Network/Reachability → Notes | Mirror exactly. Same section names where applicable; Android‑specific equivalents where not. |
| Doc comments include "Threading model", "Important", "Note", code examples | Mirror in KDoc. Use ` ``` ` fenced code blocks for examples. |

---

## 6. Implementation order

1. **Discovery.** Read the iOS sources and the Link2 sources listed in §3. Produce a short (≤300 word) note in `documentation/discovery.md` listing every Link2 call site and what it needs from the library.
2. **ADRs.** Write the three ADRs listed in §4.1.
3. **Gradle skeleton.** `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, three module `build.gradle.kts` files. Verify `./gradlew help` works.
4. **Models & contracts.** Implement `PurchasableOption`, `AnyPurchasableOption`, `PurchaseType`, `EntitlementState`, `PurchaseFlowState`, `PurchaseError`, `TierBadge`, `PurchaseOffering`, `Feature`. Pure JVM. Write unit tests first.
5. **Policies.** `SubscriptionExclusivityPolicy`, `SubscriptionReplacementPolicy`, `PurchaseVerifier`. Pure JVM unit tests.
6. **Services.** `PersistenceService` (with org.json), `NetworkService` (interface + a default `ConnectivityManagerNetworkService` impl), `ProductService`, `TransactionService`. Fake `BillingClient` for tests.
7. **Manager.** `PurchaseKitManager` wires the services together. Mirror the iOS shape but using `StateFlow` + Kotlin coroutines.
8. **Compose layer.** Build the five Compose components in §4.3.
9. **Sample apps — BOTH variants must exist and work:**
   - `:sample-compose`: Compose paywall demonstrating `collectAsStateWithLifecycle` and the Compose building blocks. ~60 lines of business logic.
   - `:sample-views`: XML/Fragment paywall using `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle { ... } }` to observe `StateFlow`, plus a second Fragment that uses **only** `PurchaseKitDelegate` (no flows at all) to prove the callback path. Includes one `.java` file calling the public API to verify Java-interop. ~80 lines of business logic.
10. **README + CHANGELOG.** README structure mirrors iOS. README contains a dedicated *Using with Views / Fragments (no Compose)* section parallel to the Compose section, with full code samples for both `StateFlow` collection and `PurchaseKitDelegate`. CHANGELOG starts at `0.1.0`.
11. **Code‑review skill pass.** Self‑review for leaks, threading, public API ergonomics. Fix what comes up.
12. **Accessibility review** of the Compose components (TalkBack labels, minimum 48dp touch targets, color contrast).

---

## 7. Doc comment standard (English KDoc)

Every public symbol gets KDoc that covers:

- **What** it does (one sentence).
- **Why** it exists if not obvious.
- **Threading**: which thread is the caller required to be on, which thread does the callback fire on.
- **`@param`** for every parameter — including units, ranges, nullability semantics.
- **`@return`** if not `Unit`.
- **`@throws`** for every exception the symbol can throw (including those from coroutine cancellation, where relevant).
- **Code example** for top‑level types and any non‑obvious function — fenced ` ```kotlin ` block.
- **Cross‑references** with `[OtherSymbol]`.
- For state flows: document whether the value is "snapshot" or "stream of changes" and what the initial value is.

Example template:

```kotlin
/**
 * Initiates a purchase flow for [option] within the given [activity].
 *
 * Resolves the cached [com.android.billingclient.api.ProductDetails], picks the appropriate
 * subscription offer (via [basePlanId] / [offerTag], if provided), applies the configured
 * [SubscriptionReplacementPolicy] when an active subscription in the same group exists, and
 * launches Google Play's billing UI.
 *
 * Outcomes are delivered asynchronously via [PurchaseKitDelegate.onPurchaseStateUpdated] and
 * reflected in [perOptionFlowState] / [entitlements] state flows.
 *
 * Threading: must be called on the main thread. The actual billing flow launch is dispatched
 * to [Dispatchers.IO] internally.
 *
 * @param option The host-defined purchasable option to buy.
 * @param activity The hosting Activity. Must not be finishing or destroyed.
 * @param basePlanId Optional base plan ID for offer selection (subscriptions only).
 * @param offerTag Optional offer tag for offer selection (subscriptions only).
 * @param obfuscatedAccountId Optional opaque user identifier for fraud detection.
 *                            Must not contain PII. Max length 64.
 * @param isPricePersonalized Required `true` if the displayed price was personalized
 *                            (EU consumer law).
 *
 * @see restorePurchases
 * @see refreshPurchases
 * @since 0.1.0
 */
@MainThread
fun purchase(
    option: PurchasableOption,
    activity: Activity,
    basePlanId: String? = null,
    offerTag: String? = null,
    obfuscatedAccountId: String? = null,
    isPricePersonalized: Boolean = false,
)
```

---

## 8. Testing strategy

| Layer | What | How |
|---|---|---|
| Contracts (models, policies) | Pure logic | JUnit 5, no mocks. ~100% line coverage. |
| `PersistenceService` | Round‑trip every `EntitlementState` shape; verify no `Inactive → Purchased` resurrection. | Robolectric (lightweight, allowed as a **test‑only** dep). |
| `TransactionService` | connect, query, acknowledge, purchase, replacement | Hand‑rolled `FakeBillingClient` implementing the same interfaces. No reflection. |
| `PurchaseKitManager` | composition, listener semantics, flow state transitions, exclusivity, network gating | Constructor‑inject all services; use fakes. |
| `:sample` | Smoke‑build only — ensure the public API is actually usable. | `./gradlew :sample:assembleDebug`. |

`./gradlew test connectedCheck` are the canonical gates. `connectedCheck` is optional — call it out if you skip it.

---

## 9. README requirements

The top‑level `README.md` must mirror the structure of `/Users/markusehmer/Apps/iOS/PurchaseKit/README.md`:

1. **Title + one‑sentence pitch** (positioning, license).
2. **Features.** Bullet list. Match the iOS feature list one‑to‑one where the Android equivalent exists; clearly note Android‑only / iOS‑only.
3. **Requirements.** `minSdk 24`, Kotlin 2.0+, Google Play Billing 8.x.
4. **Installation.** Gradle dependency snippet (placeholder Maven coordinates; document that the artifact is published from a tag).
5. **Quick Setup (Recommended).**
   - Define your `PurchasableOption` enum (full example).
   - Install PurchaseKit in `Application.onCreate` (full example).
   - Use it anywhere via `StateFlow` collection or `PurchaseKitDelegate`.
6. **Using with Jetpack Compose** — `collectAsStateWithLifecycle()` example, plus the optional `:purchasekit-compose` building blocks. Note that this section is **optional reading** for Compose users.
7. **Using with Views / Fragments (no Compose)** — must be a peer of the Compose section, not a footnote.
   - Code sample with `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { manager.entitlements.collect { ... } } }` driving an `XML` paywall.
   - Code sample with `PurchaseKitDelegate` only (no coroutines), for hosts that prefer pure callbacks.
   - Note: `:purchasekit-compose` is **not** required — `implementation("com.markusmock:purchasekit:VERSION")` alone is enough.
   - Mention Java interop: link to the `JavaInteropDemo.java` file in `:sample-views` as the canonical reference.
8. **Optional: Install with NetworkService.**
9. **Offerings (Grouping Paywall Options).**
10. **Optional: Feature Gating** — primitives only, with an example of how the host builds its coordinator.
11. **UI Building Blocks (Compose only)** — `PurchaseButton`, `RestorePurchasesButton`, `LegalLinksRow`, `ManageSubscriptionsButton`. Explicitly note that View-based apps are expected to build their own UI on top of the state flows / delegate; the library is intentionally view-agnostic.
12. **Best Value / Savings Badges.**
13. **Delegate (optional)** — for callback‑oriented hosts (callback‑only Fragments, Java consumers, hosts that intentionally avoid coroutines).
14. **Promo Codes** — note that Play Store handles the redemption sheet externally.
15. **Network / Reachability (Optional).**
16. **Server‑side Receipt Verification** — explain `PurchaseVerifier`.
17. **Notes** (mirroring iOS — "not a singleton", "host owns scope", "persistence is cache, Play is truth", localization is the host's job, **"Compose is optional — `:purchasekit` works in pure View/XML apps"**).
18. **Support** — star the repo.

Code blocks must compile against the public API.

---

## 10. Acceptance checklist

The library is "done" when **every box** is honestly checkable:

- [ ] Apache‑2.0 LICENSE preserved, SPDX headers on every source file.
- [ ] `./gradlew build` clean, zero warnings from PurchaseKit modules, zero `@Suppress` annotations.
- [ ] No dependency outside the list in §2.2.
- [ ] No `import com.sigmasport.*`, no `import com.markusmock.purchasekit.sample.*` anywhere in `:purchasekit` or `:purchasekit-compose`.
- [ ] `grep -rn "Link2Application\|R.string\|getAppContext" purchasekit/src/main` returns nothing.
- [ ] `grep -rn "lateinit var" purchasekit/src/main` returns nothing in production code.
- [ ] **Compose isolation:** `./gradlew :purchasekit:dependencies` shows zero `androidx.compose.*` artifacts (direct or transitive). `grep -rn "androidx.compose" purchasekit/src/main` returns nothing.
- [ ] **`:sample-views` builds and runs** with `./gradlew :sample-views:assembleDebug` without pulling in any Compose artifact (verify with `./gradlew :sample-views:dependencies | grep -i compose` → empty).
- [ ] **`:sample-compose` builds and runs** with `./gradlew :sample-compose:assembleDebug`.
- [ ] Both sample apps demonstrate the same end-to-end flow (load → purchase → restore → refresh on foreground → entitlement display). Side-by-side they look like two paywall styles of the same product.
- [ ] At least one `.java` file in `:sample-views` exercises the public API end-to-end (proves Java-interop).
- [ ] README contains both *Using with Jetpack Compose* and *Using with Views / Fragments* sections of comparable length and detail.
- [ ] Every public symbol has KDoc with `@param`, `@return`, `@throws`, threading note, `@since`.
- [ ] No `Activity`/`Context` field stored beyond the scope of a single function call.
- [ ] `shutdown()` cancels all coroutines, ends the billing connection, releases listeners.
- [ ] Listener leak test: register a listener with a `LifecycleOwner`, drive it to `DESTROYED`, assert the listener list is empty.
- [ ] `EntitlementState` survives process death (validated by Robolectric test).
- [ ] `:sample-compose` paywall integrates the library in <60 lines of business logic. `:sample-views` paywall in <80 lines.
- [ ] README mirrors iOS structure and every code block compiles.
- [ ] Three ADRs exist in `documentation/adr/`.
- [ ] All test suites green.

---

## 11. Anti‑patterns (do not do)

- ❌ Singletons. ❌ `object PurchaseKit { ... }`. ❌ Hidden global state.
- ❌ `Context` stored as a `val context: Context` field (only `applicationContext` if absolutely necessary).
- ❌ `runBlocking` anywhere outside tests.
- ❌ `GlobalScope`.
- ❌ Returning localized strings from the library.
- ❌ Reaching into host resources (`R.string.*`).
- ❌ Storing purchase tokens or order IDs in `SharedPreferences`.
- ❌ Adding any feature flag, A/B switch, or "in case we change our mind" abstraction. YAGNI ruthlessly.
- ❌ Wrapping every Play Billing class. Expose `ProductDetails` and `Purchase` directly when consumers genuinely need them — these are stable, well‑documented types.
- ❌ "Helper" extension functions on `Any` or `Context` in public API.

---

## 12. Reporting back

When you finish, post a short status note that lists:

1. The exact public API surface (signatures only).
2. Which acceptance items (§10) passed, which did not, and why.
3. What you intentionally cut vs. the iOS surface, with reasoning.
4. Anything in this prompt you found wrong or unhelpful — be direct.

---

> **Start by activating the skills in §0, then do the §3 discovery pass, then the §6 implementation order. Don't skip the ADRs — they are the deliverable, not paperwork.**
