# Phase 3 Implementation

Date: 2026-08-24

Phase 3 makes blueprint loot distribution respect the live server configuration and the authoritative blueprint catalog. It also hardens the authored loot data and its generator without requiring a rewrite of the existing 485 generated global loot modifiers.

## Runtime loot selection

`AddItemsModifier` now treats the generated JSON as a description of the eligible weighted pool and loot-table condition. The server's synchronized configuration supplies the behavior that an operator expects to be live:

- `enableBlueprints` immediately disables blueprint loot as well as blueprint-gated crafting.
- `blueprintSpawnChance` is read for every eligible loot roll instead of being permanently baked into generated JSON.
- `minBlueprints` and `maxBlueprints` are read for every successful roll.
- Roll bounds are normalized safely and capped at 64 blueprints per loot event.
- Selected stacks are copied before insertion into generated loot.

The legacy `poolProbability` and `rolls` JSON fields remain in the codec. This preserves compatibility with every existing generated modifier while allowing live config to override their historical snapshots at runtime.

## Content-pack namespace fix

The old runtime filter called `ModList.isLoaded()` with each blueprint ID's namespace. That is not valid for TaCZ content packs: namespaces such as `classicr`, `lradd`, and `suffuse` are data-pack namespaces, not Forge mod IDs. Valid blueprint entries from those packs were therefore removed before selection.

Runtime eligibility is now based on the data that actually defines a usable blueprint:

1. The blueprint ID must be a valid `ResourceLocation`.
2. The ID must resolve in the current authoritative `BlueprintDataManager` catalog.
3. The ID must not appear in the gun, ammo, or attachment blacklist.
4. Its weight must be finite and greater than zero.

Forge mod checks remain only in data generation, where they correctly decide which optional structure-mod loot tables should be emitted for the current generation classpath.

## Weighted selection safety

The selection algorithm was extracted into a small pure helper so its behavior can be tested without bootstrapping Minecraft registries.

- Invalid IDs and non-positive or non-finite weights are discarded.
- Empty or invalid pools add no loot.
- Probability is clamped to `[0, 1]`, with non-finite values treated as zero.
- Random selection uses stable half-open cumulative-weight boundaries.
- A clamped fallback protects the final weighted entry from floating-point edge cases.
- Reversed roll bounds resolve to the configured minimum rather than failing or producing a negative random bound.

The unused, unregistered singular `AddItemModifier` implementation was removed. The registered `add_items` codec and its resource name are unchanged.

## Configuration behavior

All three blacklist types now affect both new data generation and live runtime selection. Previously the generator consulted only the gun blacklist, and runtime loot ignored all blacklist changes embedded after generation.

The configuration validators now enforce the same 64-item safety limit used by runtime selection. English config labels explain that chance applies to configured loot tables, describe the roll cap, and state that blacklists exclude entries from loot distribution. Missing `enableBlueprints` labels were added.

## Data loading and generation

The reflection-based loot-resource discovery was replaced with an explicit ordered list of the six required tiers: easy, medium, hard, village, nether, and water.

- Resources are decoded as UTF-8 with deterministic closure of streams.
- Missing, malformed, or incorrectly shaped required resources fail generation with a useful exception.
- Every spawn-rate entry validates its name, ID, and positive finite score.
- Every loot-table ID must be valid and grouped beneath its own namespace.
- Tier traversal and modifier construction are deterministic.
- Generator logging reports one useful total instead of logging every modifier.
- Historical float operation order is preserved to avoid one-thousandth weight churn in existing generated files.

The obsolete server-start scan that enumerated and logged every chest loot table was also removed. It did not control distribution or validate the generated modifier set.

## Automated tests

Five Phase 3 tests were added to the seven Phase 2 tests. The 12-test Java 17 suite now additionally covers:

- content-pack namespaces surviving catalog-style eligibility filtering without Forge mod-ID assumptions;
- invalid IDs and invalid weights;
- exact weighted-selection boundaries;
- probability and roll-range sanitization;
- loading and validating all six required tier resources;
- valid namespace grouping and no duplicate loot-table IDs within one tier;
- malformed spawn-rate entries.

Result: 12 tests, 0 failures, 0 errors, 0 skipped.

## Generation and runtime validation

- Java 17 `build`: successful, including compilation, tests, packaging, mixin processing, and reobfuscation.
- Isolated Java 17 structure-aware `runData`: successful and generated 485 modifiers.
- All 485 generated modifier payloads reproduce byte-for-byte from the current source.
- The global modifier index contains the exact same 485-entry set; only entry order changes because tier traversal is now deterministic.
- No generated resources in the working repository were rewritten during Phase 3 validation.
- Dedicated server: reached `Done`, decoded the existing modifier schema without a global-loot-modifier error, and initialized the authoritative catalog with 452 blueprints.
- Client: reached the normal render loop, initialized OpenAL, built texture atlases, and applied the existing blueprint mixins without an injection error.
- Remaining client/server asset warnings are from malformed third-party TaCZ packs already recorded in earlier phases.

## Remaining hands-on acceptance checks

The ForgeGradle development server in this repository does not forward terminal commands to the running Minecraft process, so a final physical-loot pass still requires a connected game client:

1. Set blueprint chance to 100% and the roll range to a known value such as 2-2.
2. Open or generate an eligible vanilla chest and confirm exactly two blueprints are added.
3. Confirm at least one valid non-`tacz` content-pack blueprint can appear.
4. Add that blueprint ID to the matching blacklist and confirm it no longer appears without regenerating data.
5. Change chance or roll bounds through the config UI and confirm new loot immediately uses the new values.
6. Disable blueprints and confirm eligible loot tables add no blueprints.

Tier balance and the few intentional cross-tier loot-table overlaps were not changed in this phase. Those are design choices rather than correctness defects and should be tuned separately after playtesting.
