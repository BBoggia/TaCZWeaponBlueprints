# Phase 6 Implementation

Date: 2026-08-24

Phase 6 adds operator-facing diagnostics and production hardening around the dynamic loot policy system introduced in Phases 4 and 5. It does not introduce another datapack format, change the six built-in loot tiers, or mutate loot while inspecting it. Its purpose is to make highly dynamic configurations understandable on a running server.

## Operator commands

All Phase 6 commands are beneath the existing permission-level-2 `/gg` root.

### Distribution status

```text
/gg loot status
```

The status response reports:

- prepared tag, pool, and rule counts;
- enabled-rule, exact-binding, and selector-rule counts;
- the authoritative TaCZ catalog size;
- the successful loot-reload revision;
- the live `enableBlueprints`, default chance, roll range, and total blacklist size;
- whether distribution is using dynamic rules, a datapack global opt-out, or legacy fallback.

The revision and snapshot are published together as one immutable value. A failed datapack reload cannot advance the revision or pair a new revision with an old snapshot.

### Loot-table inspection

```text
/gg loot inspect minecraft:chests/simple_dungeon
```

Inspection uses the command source's current dimension and, for a player source, current luck. It reports whether dynamic data owns the table and explains every matching rule in deterministic rule-ID order:

- enabled or disabled state;
- referenced pool;
- exact, selector, exact-and-selector, or global-disable targeting;
- whether the rule predicate matches the current dimension and luck;
- current catalog candidate count before live blacklists;
- whether the rule is context-eligible before chance and blacklist evaluation.

An exact target that also matches its selector appears once and is labeled `exact+selector`. A disabled empty-target rule appears as `global-disable`, making an intentional global legacy opt-out visible.

Output is capped at 20 rule-detail lines. The header still reports the complete match count and the command reports how many additional lines were omitted. This prevents a broad or adversarial selector set from flooding operator chat.

Inspection is read-only. It does not consume random values, roll chance, create item stacks, or change selector-cache identity.

### Pool inspection

```text
/gg loot pool taczweaponblueprints:easy
```

Pool inspection reports whether the prepared pool exists, its flattened explicit-entry count, its inherited catalog-selector count, and the number of current authoritative catalog candidates before blacklists. Because Phase 5 flattens tags and inherited pools during reload, these counts describe the exact prepared policy used at runtime rather than only the source file's local terms.

## Diagnostics model

`BlueprintLootDiagnostics` is a read-only model shared by commands and tests. It accepts an immutable loot snapshot and catalog map and produces small immutable reports. Command formatting is kept outside the model so matching, ordering, ownership, predicates, candidate counts, and null-safe fallback behavior can be tested without a running command dispatcher.

The diagnostics deliberately distinguish three stages:

1. **Target match:** exact list, loot-table selector, or global opt-out ownership.
2. **Context eligibility:** rule enabled, predicate matched, and at least one catalog candidate.
3. **Runtime roll:** live global enable setting, blacklists, chance, roll bounds, and the shared event budget.

The command reports stages one and two and displays the live defaults used by stage three. It never claims that a context-eligible rule is guaranteed to add loot.

## Production cleanup

Phase 6 also removes development-only startup and player-join `HELLO` logging, the unused Log4j duplicate logger, an empty common-setup listener, commented command-registration remnants, and the empty `ReloadCommand` placeholder. Command registration is now explicitly scoped to this mod on the Forge event bus and uses an accurately named handler.

## Automated coverage

Phase 6 adds tests for:

- empty-snapshot and negative catalog-size normalization;
- summary counts for mixed enabled and disabled rules;
- deterministic rule-report ordering;
- exact-and-selector target classification;
- predicate mismatch and inclusive match behavior;
- catalog-selector candidate counts;
- disabled targeted ownership and global opt-out reporting;
- context-eligible rule counts;
- missing, selected, and empty pool inspection;
- null and untargeted-table safety;
- complete `/gg loot` command-tree registration.

The full Java 17 suite now contains 38 tests.

## Validation status

- Java 17 `cleanTest test build` succeeds, including compilation, tests, mixin processing, reobfuscation, and packaging.
- All 38 automated tests pass with no failures, errors, or skips.
- Every checked source and generated JSON resource parses successfully.
- Scoped diff whitespace validation passes.
- The release JAR contains the Phase 6 command and diagnostics classes, all 9 new localization keys, 486 loot modifiers, and all 12 built-in pool/rule resources. It contains no `.cache` or `.DS_Store` entries.
- An isolated dedicated server applied loot snapshot revision 1 with 6 pools, 6 rules, and 748 exact bindings; rebuilt the 452-entry authoritative catalog; and reached `Done` without a command-registration or side-only classloading failure.
- The smoke test still reports the previously documented malformed recipes, language file, and missing definitions from third-party TaCZ packs. No new exception originated from the Phase 6 paths.

## Hands-on operator checks

The remaining connected-client acceptance pass should include:

1. Run all three `/gg loot` commands as an operator and confirm localized output is readable in chat.
2. Inspect a built-in exact target, a format-2 selector target, and an untargeted legacy table.
3. Move between dimensions or change luck and confirm predicate reporting changes without a reload.
4. Change a blacklist and confirm pool/table candidate counts remain explicitly described as pre-blacklist while actual loot eligibility changes live.
5. Run `/reload` with valid data and confirm the revision advances once.
6. Attempt an invalid reload and confirm the revision and last-known-good diagnostics remain unchanged.

Exact effective weights, probabilities, blacklist filtering, and expected additions are implemented by the analytical preview documented in `phase-7-implementation.md`.
