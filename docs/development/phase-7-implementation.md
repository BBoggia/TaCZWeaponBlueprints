# Phase 7 Implementation

Date: 2026-08-24

Phase 7 makes effective blueprint-loot outcomes analytically previewable and ensures the preview and gameplay paths resolve policy through the same implementation. It closes the largest remaining balancing and acceptance gap without adding random simulation, changing datapack formats, or altering the six built-in tiers.

## Shared effective-policy resolution

`BlueprintLootPolicyResolver` converts one prepared rule binding into the complete non-random policy used for a loot event:

- global blueprint enable state;
- current dimension and luck predicate result;
- rule chance override or sanitized live default chance;
- rule roll override or sanitized live default roll range;
- authoritative catalog entries selected by the flattened pool;
- all three live blacklist types;
- eligible blueprint weights and total weight;
- normalized per-roll selection probabilities;
- expected additions before the shared event budget.

The resolver is split into settings and candidate stages. The dynamic modifier can therefore retain its existing execution order:

1. reject a false predicate without consuming randomness;
2. evaluate the chance roll;
3. resolve the catalog and blacklist-filtered candidates only after chance succeeds;
4. choose a bounded roll count;
5. perform weighted selection with replacement while respecting the remaining event budget.

The command path resolves both stages immediately because its purpose is to explain the complete policy. Candidate resolution verifies that its settings belong to the same rule binding, preventing accidental cross-rule reuse.

## Coherent runtime configuration

`BlueprintLootRuntimeConfig.capture()` takes one event-local snapshot of:

- `enableBlueprints`;
- default chance;
- default minimum and maximum rolls;
- gun, ammo, and attachment blacklist values.

The blacklist values are copied into an immutable set. Real loot and a command preview cannot observe half of one configuration update and half of another during a single evaluation. Datapack snapshots and catalog maps were already immutable publications from earlier phases.

## Analytical preview command

```text
/gg loot preview minecraft:chests/simple_dungeon
```

The permission-level-2 command uses the command source's current dimension and player luck. For every enabled matching dynamic rule it reports:

- whether the rule can currently produce loot;
- effective chance;
- effective roll range;
- post-blacklist eligible candidates versus catalog candidates;
- total eligible weight;
- expected additions for that rule;
- the highest-probability candidates with exact weight and per-roll probability.

Per-roll probability is calculated exactly:

```text
candidate weight / total eligible weight
```

For a context-active rule with a uniform inclusive roll range, expected additions are:

```text
chance × ((minimum rolls + maximum rolls) / 2)
```

Expected values from overlapping rules are summed in the header. The command clearly labels this as the expectation before the shared 64-blueprint event budget and before accounting for blueprints already present in generated loot.

No random values are generated. Repeating a preview against unchanged state produces the same output and cannot perturb later chest results.

## Bounded output

Preview output is limited to:

- five rule summaries;
- three highest-probability candidates per displayed rule;
- one truncation message when additional rules exist.

Complete rule counts and total expected additions still include every matching enabled rule. Candidate ordering is probability descending with resource ID as a deterministic tie-breaker.

Phase 6 `status`, `inspect`, and `pool` remain available. `inspect` continues to include disabled ownership and global opt-out rules, while `preview` intentionally covers only enabled rules that the runtime would evaluate.

## Automated coverage

Phase 7 adds tests for:

- exact weight normalization and per-roll probabilities;
- stable half-open weighted-selection boundaries through the effective policy;
- rule chance and roll overrides taking precedence over live defaults;
- inclusive dimension/luck predicate behavior;
- expected-addition calculations;
- global blueprint disable behavior;
- live blacklist exclusions and empty eligible pools;
- unsafe default chance and reversed roll sanitization;
- preservation of chance-evaluation ordering;
- rejection of settings reused with the wrong binding;
- registration of the preview command branch.

The full Java 17 suite now contains 43 tests.

## Validation status

- Java 17 `cleanTest test build` succeeds, including compilation, all tests, mixin processing, reobfuscation, and packaging.
- All 43 automated tests pass with no failures, errors, or skips.
- Every checked source and generated JSON resource parses successfully, and scoped diff whitespace validation passes.
- The release JAR contains the policy resolver, immutable runtime-config capture, preview command, and all five preview localization keys. It retains all 486 loot modifiers and contains no `.cache` or `.DS_Store` entries.
- An isolated dedicated server applied snapshot revision 1 with the expected 6 pools, 6 rules, and 748 exact bindings; rebuilt the 452-entry authoritative catalog; and reached `Done` without a resolver, command-registration, or side-only classloading failure.
- Remaining malformed recipe, language, and missing-definition messages are the same third-party TaCZ pack issues documented in prior phases.

## Remaining hands-on checks

1. Run preview for each built-in tier and compare the highest-probability candidates with intended progression.
2. Preview a format-2 selector rule before and after adding a matching TaCZ content pack.
3. Change each blacklist and confirm its entries disappear from the preview without a datapack reload.
4. Change global defaults and confirm only rules without overrides change.
5. Move across a configured predicate boundary and confirm the rule becomes active or inactive.
6. Generate a large physical-loot sample and compare observed frequencies with the exact per-roll probabilities.
