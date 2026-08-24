# Journal and Research Phase 3: Research Datapack Model

Date: 2026-08-24

## Scope

Phase 3 adds the immutable, server-authoritative policy model that later
Journal, recycling, and Research Bench phases will consume. Server datapacks
can now define reusable research profiles and targeted rule overrides without
changing Java code.

This phase does not add the Journal screen, recycling transactions, the
Research Bench, item-cost consumption, or operator configuration for selecting
the active profile.

## Datapack resources

The research listener reads these server-data directories:

```text
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
```

It also reads the existing shared blueprint-tag directory. Rules can therefore
target exact blueprint output IDs, blueprint tags, or the same catalog selector
language used by dynamic loot rules. A selector may filter namespace, category,
or loaded catalog entries; its loot-specific weight now safely defaults to
`1.0` when omitted.

Every definition is strictly decoded. Unknown fields, malformed resource IDs,
negative values, empty ingredient alternatives, invalid target combinations,
and values outside the documented bounds reject the complete research reload.

## Profiles and rules

A profile provides a complete fallback policy for:

- whether Journal presentation is enabled;
- the undiscovered visibility level;
- whether research and recycling are enabled;
- whether unlearned blueprints may be recycled;
- the recycling Research Point value;
- the Research Point and item cost;
- whether discovery is required before research;
- whether creative players may bypass the research cost.

A rule names one profile and selectively overrides those fields for a target.
Rules may additionally declare exact blueprint prerequisites. Prerequisite
rules intentionally require exact targets, which makes the complete dependency
graph knowable and validatable even when optional content packs are absent.

The hard limits are:

- 4,096 profiles, rules, and blueprint tags per snapshot;
- 256 target terms per rule;
- 64 prerequisite blueprint IDs per rule;
- 6 ingredient types per research cost;
- 64 exact item alternatives per ingredient;
- 64 items per ingredient stack;
- 1,000,000,000 Research Points per value;
- dependency depth of 64.

The per-definition ceilings are reinforced by aggregate limits so many valid
definitions cannot combine into an unsafe workload:

- 65,536 total blueprint-tag values;
- 65,536 total rule target terms;
- 262,144 expanded rule-to-tag-value bindings;
- 65,536 total ingredient terms;
- 65,536 total prerequisite references.

Self-dependencies, cycles, excessive dependency depth, missing profile or tag
references, and invalid programmatically constructed definitions are rejected.
Targets for content that is not currently installed remain valid but dormant.

## Deterministic resolution

`BlueprintResearchPolicyResolver` selects at most one rule overlay for a
blueprint. The order is:

1. exact blueprint target;
2. blueprint tag target;
3. catalog selector target;
4. the selected profile fallback.

Within one target rank, higher priority wins. Equal priorities are resolved by
ascending definition ID. Diagnostics retain every equal-rank, equal-priority
candidate so ambiguous datapack ties can be reported without making runtime
behavior nondeterministic.

Using one selected overlay instead of field-by-field rule merging keeps policy
ownership understandable: a datapack author can identify the exact profile and
rule responsible for every resolved value.

Exact and expanded tag targets are compiled into immutable lookup indices once
per snapshot/profile cache state. Selector rules remain in deterministic order
and are evaluated only for the requested blueprint. Policy definitions are
resolved lazily instead of eagerly calculating the Cartesian product of every
catalog entry and rule. Up to eight snapshot/catalog/profile identities can be
cached concurrently, preventing profile inspection from continuously replacing
the active profile cache.

## Safety and publication

Research preparation builds and validates an entire immutable
`BlueprintResearchSnapshot` before publication. A failed reload never exposes
a partial snapshot and leaves the previous publication active. Successful
publication increments a revision and atomically replaces the snapshot.

Resolved base policies are cached against all three inputs that define them:
the research snapshot identity, the authoritative catalog snapshot identity,
and the active profile ID. Reloading either research data or the TaCZ catalog
therefore invalidates stale resolution without mutable cache entries leaking
between snapshots.

Economic validation requires an enabled research policy's point cost to exceed
its recycling value. This prevents a direct research-and-recycle point-profit
loop, including for rule overrides that are currently shadowed by another rule.

Exact ingredient item IDs are checked against the item registry during reload.
Missing or empty item tags are reported after tag application and leave the
affected cost unavailable, allowing a tag supplied by the same reload to become
valid without a false preparation failure. Research selectors use their own
strict codec: loot-only `weight` is rejected, and every selector term and
resource ID is length-bounded.

## Built-in profile

The packaged `taczweaponblueprints:duplicate_recovery` profile is the current
default. It provides:

- Journal presentation enabled with `SILHOUETTE` undiscovered entries;
- research enabled without a discovery or prerequisite requirement;
- creative cost bypass disabled;
- duplicate-only recycling worth 1 Research Point;
- research costing 8 Research Points, 4 paper, and 2 iron ingots.

The defaults are deliberately conservative. Later phases can expose profile
selection and tuning while datapacks can already replace the packaged resource
or add targeted rules.

## Player-aware policy API

`BlueprintResearchDataManager.policyFor(blueprintId, player)` combines the
immutable base definition with the current authoritative catalog, synchronized
blacklist, and Phase 1 player progression.

The returned immutable policy reports availability, administrative blocking,
player-data availability, learned/discovered state, prerequisite satisfaction, current point balance,
visibility, costs, recycling value, selected rule, and target specificity.
Learned entries are always `FULL`. Discovered entries are at least `PREVIEW`
unless the selected rule explicitly restricts disclosure.

The policy object exposes eligibility helpers, but later transaction phases
must still re-resolve and validate the policy on the server at commit time.
Research, point-affordability, and recycling helpers all fail closed when the
player capability is unavailable.

Phase 2 discovery synchronization is also hardened here: multiple unique
discoveries in one player tick mark one dirty state and emit at most one full
progression snapshot at tick end. Explicit progression synchronization consumes
the same dirty state, and logout/server-stop cleanup prevents stale entries.

## Verification

Phase 3 validation completed successfully with:

- 86 automated tests;
- 0 failures, errors, or skipped tests;
- strict codec and nested unknown-field tests;
- exact, tag, selector, priority, ID, and tie-resolution tests;
- dormant-content and missing-reference tests;
- self-reference, cycle, and maximum-depth tests;
- economic-loop and shadowed-rule validation tests;
- snapshot and catalog cache-identity tests;
- multi-profile lazy-cache and aggregate-limit tests;
- fail-closed capability and strict progression-replacement tests;
- selector-length and research-only-schema tests;
- exact-item and unresolved-tag validation tests;
- coalesced discovery scheduler tests;
- out-of-order, duplicate, and replaced progression-chunk tests;
- failed reload preparation preserving the active publication;
- built-in profile decoding and balance tests;
- `clean build`, `verifyReleaseArtifact`, and
  `verifyPublicationReadiness`;
- a dedicated server that published one profile and reached `Done (2.165s)`
  with TaCZ 1.1.8-hotfix.

The installed CCRP, ATea, ARIP, Zugzwang, ClassicR, and Suffuse content packs
continue to report their pre-existing malformed or unresolved data. No Phase 3
codec, reload, publication, catalog, or server-start error appeared.

The network protocol remains `4`: research definitions and policy evaluation
are server-only in this phase, so no packet wire format changed.

## Deferred to later phases

Phase 3 intentionally defers:

- Journal entry view models, synchronization, and screen UI;
- duplicate-recycling transactions and point awards;
- Research Bench registration, menus, recipes, and transactions;
- inventory item-cost matching and consumption;
- server configuration for active-profile selection and balance controls;
- operator diagnostics, reset commands, and in-game reload feedback.
