<!-- SPDX-License-Identifier: Apache-2.0 -->

# PurchaseKit-Android — Architecture

This document captures the architectural shape of the library. See the ADRs
under `documentation/adr/` for the decisions behind each major choice.

## Modules

```
PurchaseKit/
├── purchasekit/                   # core, NO Compose
├── purchasekit-compose/           # optional Material 3 building blocks
├── sample-compose/                # demo: :purchasekit + :purchasekit-compose
├── sample-views/                  # demo: :purchasekit only (proves no-Compose path)
└── documentation/
    ├── adr/
    └── architecture.md (this file)
```

**Dependency direction (one-way):**

```
:sample-compose ─► :purchasekit-compose ─► :purchasekit
:sample-views   ─►                          :purchasekit
```

## Runtime composition

`PurchaseKitManager.create(context, options, config)` builds the following
graph:

```
PurchaseKitManager
├── BillingBridge                — wraps Play Billing client (RealBillingBridge in prod, FakeBillingClient in tests)
├── ProductService               — queryProductDetailsAsync per product type
├── TransactionService           — launchBillingFlow / queryPurchases / ack / consume + delegate fan-out
├── PersistenceService           — derived-state JSON cache in SharedPreferences
├── NetworkService? (optional)   — fast-fail UX seam
├── PurchaseVerifier             — server-side verification seam
└── CoroutineScope(SupervisorJob + Dispatchers.Main.immediate)
```

`PurchaseKitConfig` is a single `data class` carrying every replaceable seam
with sensible defaults. No DI framework is involved (ADR-0001).

## State model

Public state is exposed as `StateFlow`s only. There are no mutable public
properties.

| StateFlow                              | Source                                       | Threading              |
|----------------------------------------|----------------------------------------------|------------------------|
| `entitlements`                         | `BillingClient.queryPurchasesAsync` (truth)  | emits on main          |
| `availableProducts`                    | `BillingClient.queryProductDetailsAsync`     | emits on main          |
| `flowState`, `perOptionFlowState`      | Manager (transient, UI-only)                 | emits on main          |
| `hasAnyActiveSubscription`             | derived from `entitlements`                  | emits on main          |
| `primaryActiveSubscription`            | derived; `SubscriptionExclusivityPolicy`     | emits on main          |
| `connectionState`                      | `BillingClient` connection callbacks         | emits on main          |
| `canAttemptNetworkOperations`          | `NetworkService` or constant `true`          | emits on caller thread |

Persistence is a cold cache — Play wins on every refresh / restore /
foreground cycle. The codec deliberately discards stale-version payloads
into `Inactive` (ADR-0002).

## Lifecycle

```
create(context, options, config)
  ├─ warmFromCache()            // paint UI from SharedPreferences cache
  ├─ bridge.connect()           // start Play Billing
  ├─ loadProducts()             // populate availableProducts
  └─ transactionService.refresh()  // pull truth from Play

onAppEnteredForeground()
  ├─ networkService?.onForeground()
  ├─ connectIfNeeded()
  └─ refreshPurchases()         // re-pull truth

shutdown()
  ├─ transactionService.stop()
  ├─ bridge.endConnection()
  ├─ networkService?.shutdown()
  ├─ release weak listeners
  └─ scope.cancel()
```

Listeners are tracked as `CopyOnWriteArrayList<WeakReference<…>>`. When a
caller passes a `LifecycleOwner`, the manager attaches a
`DefaultLifecycleObserver` that removes the listener on `ON_DESTROY`.

## Testing

| Layer              | What                                                               | How                                |
|--------------------|--------------------------------------------------------------------|------------------------------------|
| Models / policies  | Pure logic                                                         | JUnit 5, no mocks                  |
| `PurchaseError`    | Mapping from `BillingResult` / `Throwable`                         | JUnit 5                            |
| Persistence codec  | Round-trip of every `EntitlementState` variant                     | JUnit 5 with `org.json:json` on JVM|
| `PersistenceService` | `SharedPreferences` round-trip                                   | JUnit 4 + Robolectric              |
| Manager            | Composition, listener semantics, flow transitions, network gating  | JUnit 4 + Robolectric + FakeBillingClient |
| Samples            | Smoke build                                                        | `./gradlew :sample-*:assembleDebug`|

`FakeBillingClient` is a hand-written `BillingBridge` implementation — no
Mockito, no reflection.

## Threading contract

- Public `StateFlow`s emit on `Dispatchers.Main.immediate`.
- `suspend` functions internally hop to `Dispatchers.IO` for blocking work.
- `PurchaseKitDelegate` callbacks fire on the **main thread**
  (`@MainThread`).
- Listener registration and `purchase()` must be invoked on the main thread.
- `shutdown()` is idempotent and main-thread-only.
