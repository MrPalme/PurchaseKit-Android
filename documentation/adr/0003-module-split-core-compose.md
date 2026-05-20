<!--
SPDX-License-Identifier: Apache-2.0
-->

# ADR 0003 — Module Split: `:purchasekit` vs `:purchasekit-compose`

Status: Accepted · Date: 2026-05-20

## Context

The iOS sibling ships SwiftUI UI building blocks (`PurchaseKitPurchaseButton`,
`PurchaseKitInfoToolbar`) inside the same Swift package. On Android, Jetpack
Compose is an opt-in choice — many production apps still use XML +
ViewBinding + Fragments, and would refuse a Compose transitive dependency.

The build brief makes Views/XML/Fragment apps "first-class consumers — equal
standing with Compose apps" (§2a). That cannot be true if the only
distribution path drags `androidx.compose.runtime`, `androidx.compose.foundation`,
`androidx.compose.material3` and the Compose compiler plugin onto every
consumer.

## Decision

Ship **two Gradle modules** from one repository:

1. `:purchasekit` — the core library. **Zero Compose dependencies, direct
   or transitive.** Public API is pure Kotlin types, coroutines/Flow,
   `androidx.lifecycle:lifecycle-common` (the non-runtime, non-compose
   artifact) and the Play Billing client.
2. `:purchasekit-compose` — optional Material 3 building blocks
   (`PurchaseButton`, `RestorePurchasesButton`, `ManageSubscriptionsButton`,
   `LegalLinksRow`, `PaywallScaffold`). Depends on `:purchasekit` and on
   the Compose BOM.

Two sample apps prove both consumption paths:

- `:sample-compose` → `:purchasekit-compose` → `:purchasekit`
- `:sample-views` → `:purchasekit` only. Verified empty by
  `./gradlew :sample-views:dependencies | grep -i compose`.

## Consequences

**Positive.**

- View/XML apps consume the library at zero Compose cost.
- Compose users get drop-in components via one extra Gradle line.
- Dependency direction is enforced by Gradle module boundaries — Compose
  cannot accidentally leak into core (compilation breaks).
- Acceptance test §10 ("`./gradlew :purchasekit:dependencies | grep -i compose`
  → empty") is structurally satisfied.

**Negative.**

- Two Gradle modules instead of one — slightly more boilerplate.
- Consumers who want both modules pay an extra `implementation(...)` line.
- Maven coordinates double from one to two.

## Alternatives considered

- **Compose-only.** Rejected by §2a.
- **Single module with Compose declared `compileOnly`.** Rejected: still
  surfaces Compose types in the public API; consumers would need the
  compiler plugin to use the library at all.
- **Three modules (core / views-helpers / compose).** Premature. View apps
  can build their own UI on top of `StateFlow` + `PurchaseKitDelegate` —
  the README documents the pattern; no helpers library required.
