<!--
SPDX-License-Identifier: Apache-2.0
-->

# ADR 0001 — No DI Framework

Status: Accepted · Date: 2026-05-20

## Context

PurchaseKit is a small, self-contained library. Consumers want to drop one
dependency, not three. Most Android apps already use Hilt or Koin — forcing
a *second* DI framework into their classpath is a non-starter. Forcing
Hilt as a transitive dependency would also block apps that have
deliberately chosen Koin (or no DI at all).

The library has **five collaborators**: `ProductService`,
`TransactionService`, `PersistenceService`, optional `NetworkService`,
`PurchaseKitLogger`. All five are constructor-injectable already.

## Decision

The library does **not** depend on Hilt, Koin, Dagger, or any DI framework.

- All collaborators are constructor-injected by hand.
- A single public factory `PurchaseKitManager.create(context, options, config)`
  wires sensible defaults.
- `PurchaseKitConfig` is a `data class` with default values so consumers can
  override individual seams (logger, network service, verifier, replacement
  policy, prefs name) without learning a DI vocabulary.
- Tests construct the manager directly with fakes — no test-time DI graph,
  no `@Provides` indirection.

## Consequences

**Positive.**

- Zero classpath bloat for consumers. Library compiles against AGP 8.x and
  Kotlin 2.x with no third-party runtime.
- Substitutability is preserved: hosts using Hilt/Koin can still
  `@Provides`/`single` the manager from their existing graph.
- Test boundaries are explicit — every fake is a constructor argument.

**Negative.**

- The factory has to ship sane defaults for every seam (logger, prefs name,
  reconnect delay). Adding a new collaborator means amending `create()` and
  `PurchaseKitConfig` — but at this scope (≤ 5 collaborators) this is
  cheaper than a DI dependency.
- Hosts that want lazy initialization must wrap the manager themselves
  (e.g. `lazy {}` or their DI framework's scope).

## Alternatives considered

- **Ship a Hilt module.** Rejected: forces Hilt onto every consumer.
- **Ship a Koin module.** Same problem, smaller blast radius.
- **Service locator.** Rejected: hidden globals, hard to test, anti-pattern
  per §11 of the build brief.
