<!--
SPDX-License-Identifier: Apache-2.0
-->

# Discovery: Link2 InAppPurchase Call Sites

Inputs: `grep` for `InAppPurchaseManager`, `InAppPurchaseListener`,
`addListener|removeListener` in
`/Users/markusehmer/Apps/Android/Link2/app/src/main/java/com/sigmasport/link2`.

## Call sites that define the minimum public API

| File:line                                       | Call                                                       | What the caller needs                                                            |
|-------------------------------------------------|------------------------------------------------------------|----------------------------------------------------------------------------------|
| `Link2Application.kt:253`                       | `InAppPurchaseManager.create(this)`                        | Factory taking `Context` only. No host-typed services required.                  |
| `Link2Application.kt:196`                       | `inAppPurchaseManager?.onAppEnteredForeground()`           | Library refreshes state when host re-enters foreground.                          |
| `Link2Application.kt:255–256`                   | `VirtualBCManager.initPurchaseManagerListener(iap)`        | A listener registration that survives across process foregrounding.              |
| `HomeViewModel.kt:186/190`                      | `addListener(this)` / `removeListener(this)` in `init`/`onCleared` | ViewModel-scoped listener with manual removal.                            |
| `UpgradeRideViewModel.kt:113/117`               | `addListener(this)` / `removeListener(this)`               | Same shape as HomeViewModel.                                                     |
| `UpgradeRideViewModel.kt:143`                   | `iap?.getOffer(product)`                                   | Resolve a subscription offer for a product (price/period/tag).                   |
| `UpgradeRideFragment.kt:292,311`                | `InAppPurchaseManager.openSubscriptionManagement(context)` | Static deep link to Play Store manage-subscriptions sheet.                       |
| `PaywallCapabilityDelegate.kt:148,155`          | `addListener` / `removeListener`                           | Imperative listener lifecycle inside a non-Android coordinator class.            |
| `TripsViewModel.kt:144,148`                     | `addListener` / `removeListener`                           | ViewModel-scoped listener.                                                       |
| `VirtualBCManager.kt:264–272`                   | `addListener` / `removeListener`                           | Long-lived backend manager observing entitlements to unlock features remotely.   |
| `UpgradeRideViewModel`                          | `purchase(product, activity, basePlanId, offerTag, obfuscatedAccountId, isPricePersonalised)` | Six-arg purchase that exposes Play Billing knobs explicitly.   |
| `UpgradeRideViewModel`                          | `restorePurchases()`, `refreshPurchases()`                 | Imperative restore/refresh from paywall UI.                                      |
| Various                                         | `purchaseStates`, `availableProducts`, `hasAnyActiveSubscription`, `connectionState` | StateFlow collection from Compose & non-Compose call sites.        |

## Listener callbacks the library must support

`onPurchaseStateUpdated(option, newState)`, `onProductsLoaded(options)`,
`onProductsLoadFailed(error)`, `onRestoreCompleted(results)`. All callers
expect main-thread delivery and idempotent registration.

## Implications for the public API

1. Factory shape `PurchaseKitManager.create(context, options, config)` is
   non-negotiable — every consumer expects a single creation point.
2. `addListener(listener, owner: LifecycleOwner? = null)` must work both
   with and without a `LifecycleOwner` (VirtualBCManager has no lifecycle).
3. `getOffer(product)` semantics expand to plural
   `subscriptionOffers(option): List<ProductDetails.SubscriptionOfferDetails>`
   so consumers pick by `basePlanId` / `offerTag`.
4. `openSubscriptionManagement(context, productId? = null)` is a companion
   helper, not bound to an instance.
5. All listener call sites either pass a `LifecycleOwner` implicitly via a
   ViewModel/Fragment, or manage their own lifecycle. Both paths must work.

(298 words.)
