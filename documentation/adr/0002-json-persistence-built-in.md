<!--
SPDX-License-Identifier: Apache-2.0
-->

# ADR 0002 — Built-in JSON Persistence

Status: Accepted · Date: 2026-05-20

## Context

PurchaseKit persists a small, derived cache of `EntitlementState` per
`PurchasableOption` so the UI can render the user's last-known
entitlement during cold-start *before* `BillingClient.queryPurchasesAsync`
returns. The cache is **not** authoritative — Play is the source of truth.

The Link2 template uses **Gson** for this. That choice forces Gson on every
consumer of PurchaseKit, conflicts with apps that use Moshi or
`kotlinx.serialization`, and adds a method count cost to a 1-file feature.

The data we persist is trivial: 5 sealed-class variants with at most an
epoch-millis Long and a transaction-id String each.

## Decision

The library serialises its persistence layer with **`org.json.JSONObject`**
— the Android-built-in JSON type — through a small hand-written codec
inside `PersistenceService`.

- Storage: `SharedPreferences` named `purchasekit_prefs` (overridable via
  `PurchaseKitConfig.persistencePrefsName`).
- Codec: one `toJson(EntitlementState)` / `fromJson(JSONObject)` pair,
  unit-tested round-trip for every variant.
- The codec persists **only derived state**: the variant discriminator,
  expiration millis, transaction id. **No purchase tokens, no PII.**
- Schema is versioned via a `v` integer field. Unknown versions fall back
  to `Inactive` rather than throwing — old caches are disposable.

## Consequences

**Positive.**

- Zero third-party JSON dependency. Constraint §2.2 satisfied.
- Codec is ~80 lines and exhaustively unit-tested — easier to audit than a
  reflection-based serializer.
- Forward compatibility (version field) is explicit, not implicit.

**Negative.**

- If `EntitlementState` grows in shape, the codec must be amended manually.
  Acceptable: this type changes once a year, if that.
- Manual codec is verbose compared to one-line `@Serializable`. We trade
  ~50 lines of code for one fewer dependency.

## Alternatives considered

- **`kotlinx.serialization`.** Rejected as the brief disallows it without
  an ADR; no genuine need given the trivial schema.
- **Moshi / Gson.** Same objection plus the explicit "no Moshi, no Gson"
  rule.
- **Raw `SharedPreferences` keys per option.** Rejected: requires a
  separate write per option and complicates atomic snapshot semantics.
