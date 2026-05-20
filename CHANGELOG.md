<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

All notable changes to PurchaseKit-Android are recorded here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-05-20

### Added

- Initial release.
- `:purchasekit` — app-agnostic facade over Google Play Billing v8+
  (`PurchaseKitManager`, `PurchaseKitDelegate`, `PurchaseKitConfig`).
- Models: `PurchasableOption`, `AnyPurchasableOption`, `PurchaseType`,
  `EntitlementState`, `PurchaseFlowState`, `PurchaseError`,
  `BillingConnectionState`, `TierBadge`, `Feature`, `PurchaseOffering`.
- Policies: `SubscriptionReplacementPolicy`, `SubscriptionExclusivityPolicy`,
  `PurchaseVerifier` (+ `VerificationFailureMode`).
- Services: `ProductService`, `TransactionService`, `PersistenceService`,
  `NetworkService` (with `ConnectivityManagerNetworkService` default).
- `:purchasekit-compose` — optional Material 3 building blocks:
  `PurchaseButton`, `RestorePurchasesButton`, `ManageSubscriptionsButton`,
  `LegalLinksRow`, `PaywallScaffold`.
- `:sample-compose` — end-to-end Compose paywall sample.
- `:sample-views` — end-to-end XML/Fragment paywall sample with
  `JavaInteropDemo.java` proving the public API is callable from Java.
- ADRs `0001-no-di-framework`, `0002-json-persistence-built-in`,
  `0003-module-split-core-compose`.
