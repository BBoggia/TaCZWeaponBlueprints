# Phase 4 Implementation

Date: 2026-08-24

Phase 4 replaces the generated-per-loot-table runtime model with a versioned, reloadable datapack model. Blueprint pools and loot-table rules are now independent resources, one global modifier evaluates them at runtime, and the existing 485 generated modifiers remain as a reversible compatibility fallback during the migration.

## Architecture

The dynamic path is split into four responsibilities:

1. Versioned Mojang codecs decode weighted blueprint pools and loot-table rules.
2. `BlueprintLootDataManager` prepares and validates a complete immutable snapshot during datapack reload.
3. `BlueprintLootSnapshot` indexes enabled rules by exact loot-table ID for constant-time lookup.
4. `DynamicBlueprintLootModifier` evaluates the matching rules against the live server config and authoritative TaCZ blueprint catalog.

The reload manager publishes the new snapshot through one volatile reference only after every pool, rule, and reference has decoded and validated. A malformed reload therefore cannot expose a partially updated distribution to loot generation; the prior complete snapshot remains authoritative if reload preparation fails.

## Datapack resource locations

Pools load from:

```text
data/<namespace>/taczweaponblueprints/loot_pools/<path>.json
```

Rules load from:

```text
data/<namespace>/taczweaponblueprints/loot_rules/<path>.json
```

The definition ID is `<namespace>:<path>`. For example:

```text
data/example/taczweaponblueprints/loot_pools/rare/nether.json
```

defines the pool `example:rare/nether`.

The built-in definitions use the `taczweaponblueprints` namespace. Their files therefore live beneath:

```text
data/taczweaponblueprints/taczweaponblueprints/loot_pools/
data/taczweaponblueprints/taczweaponblueprints/loot_rules/
```

## Pool schema, format 1

```json
{
  "format": 1,
  "entries": [
    {
      "blueprint": "tacz:ak47",
      "weight": 10.0
    },
    {
      "blueprint": "classicr:ak_alpha",
      "weight": 4.5
    }
  ]
}
```

Pool requirements:

- `format` is required and must currently be `1`.
- `entries` is required and must contain at least one entry.
- `blueprint` must be a valid resource location.
- `weight` must be finite and greater than zero.
- A blueprint that is not in the current authoritative catalog is ignored at loot time rather than invalidating reload. This allows TaCZ content packs to be added or removed independently.
- Gun, ammo, and attachment blacklists continue to filter otherwise valid entries at loot time.

Weights are relative within the eligible part of a pool. They do not need to add to 100.

## Rule schema, format 1

The smallest enabled rule is:

```json
{
  "format": 1,
  "pool": "example:rare/nether",
  "loot_tables": [
    "minecraft:chests/bastion_treasure",
    "another_mod:chests/armory"
  ]
}
```

A rule can override the global chance and roll range:

```json
{
  "format": 1,
  "enabled": true,
  "pool": "example:rare/nether",
  "loot_tables": [
    "minecraft:chests/bastion_treasure"
  ],
  "chance": 0.35,
  "rolls": {
    "min": 1,
    "max": 3
  }
}
```

Rule behavior and validation:

- `format` is required and must currently be `1`.
- `enabled` is optional and defaults to `true`.
- `pool` is required. An enabled rule referencing a missing pool fails the reload.
- `loot_tables` is required. An enabled rule must contain at least one table.
- Duplicate table IDs inside one rule are collapsed while preserving authored order.
- `chance` is optional and must be finite and between `0.0` and `1.0` when present.
- `rolls` is optional. Both bounds must be between 0 and 64, and `max` cannot be lower than `min`. The runtime also enforces one 64-blueprint budget across every matching dynamic rule and legacy modifier in the same loot event.
- If `chance` is omitted, the live global `blueprintSpawnChance` setting is used.
- If `rolls` is omitted, the live global `minBlueprints` and `maxBlueprints` settings are used.
- `enableBlueprints` and all live blacklists remain authoritative even when a rule supplies its own chance or rolls.

The six built-in rules deliberately omit `chance` and `rolls`. Existing server configuration therefore retains control without regenerating datapack resources.

## Overrides, additions, and disabling rules

Normal datapack priority controls same-ID overrides. A higher-priority datapack can replace a built-in pool or rule by defining the exact same namespace and path.

For example, this higher-priority file replaces the built-in easy rule:

```text
data/taczweaponblueprints/taczweaponblueprints/loot_rules/easy.json
```

Different definition IDs are additive. A pack can define its own pool and rule under its own namespace without copying or editing the built-in files. If multiple enabled rules target the same loot table, each rule is evaluated independently in deterministic rule-ID order. This preserves the four intentional cross-tier overlaps in the original data.

To disable a same-ID built-in rule, override it with:

```json
{
  "format": 1,
  "enabled": false,
  "pool": "taczweaponblueprints:easy",
  "loot_tables": []
}
```

Disabled rules intentionally do not validate their pool reference. A disabled rule with explicit `loot_tables` suppresses legacy distribution only for those tables. A disabled rule with an empty target list is the documented global opt-out: it claims all tables and prevents the legacy modifier set from reactivating.

## Runtime and reload behavior

The packaged global modifier index now contains one `dynamic_blueprints` modifier in addition to the 485 legacy `add_items` definitions.

- Dynamic rules claim only the loot tables they target. Legacy modifiers continue to handle untargeted tables, which lets a datapack migrate or extend distribution incrementally.
- Pool definitions alone do not suppress legacy modifiers. They can be staged safely before their rules are enabled.
- A disabled rule claims its explicitly listed tables. A disabled rule with no targets is an intentional global opt-out.
- If no rule definitions exist, the legacy modifiers retain their Phase 3 behavior. This provides a recovery path for migration builds and old resource layouts.
- The dynamic modifier reads the queried loot-table ID and performs one indexed lookup instead of relying on hundreds of table-specific modifier conditions.
- Pool eligibility is evaluated against the current catalog and config for every matching rule, so config and content-pack changes remain live.
- `/reload` constructs and atomically publishes a new loot snapshot.
- After a server-wide datapack reload has applied, the TaCZ-derived blueprint catalog is rebuilt and synchronized to connected players.
- Per-player datapack sync does not rebuild the server catalog; the existing login sync sends the already authoritative snapshot.

Malformed optional fields are rejected rather than silently treated as absent. Unknown fields are also rejected at the pool, entry, rule, and nested-roll levels so typographical errors cannot silently change balance. This required strict record and optional-field codecs because the DataFixerUpper version used by Minecraft 1.20.1 otherwise accepts unknown fields and converts some malformed optional fields into empty optionals.

## Built-in migration and parity

The six historical tiers were converted without balance changes:

| Tier | Blueprint entries | Loot-table bindings |
| --- | ---: | ---: |
| Easy | 33 | 297 |
| Medium | 67 | 95 |
| Hard | 28 | 18 |
| Village | 31 | 230 |
| Nether | 6 | 72 |
| Water | 12 | 36 |
| Total | 177 | 748 |

The 748 bindings cover 744 unique loot-table IDs. Three tables participate in four extra cross-tier bindings, matching the authored legacy tier lists. The original weights, including their historical float rounding, are preserved exactly.

Unlike the 485 generated legacy modifiers, the dynamic rules can refer to every optional structure table in the authored lists. A compatible structure mod added to a runtime pack can therefore become eligible without rerunning this mod's data generator.

Data generation now emits:

- one condition-free `dynamic_blueprints` modifier;
- the same 485 legacy modifiers for migration compatibility;
- a 486-entry global modifier index.

An isolated structure-aware generation run reproduced every legacy modifier payload byte-for-byte. Its only differences from the checked working resources were the expected new dynamic definition, deterministic global-index ordering, the generator cache, and an inconsequential final-newline difference in the new JSON file.

## Automated tests

Phase 4 adds codec, snapshot, migration-parity, and ownership tests to the Phase 2 and Phase 3 suites. The 20-test Java 17 suite now covers:

- valid format-1 pool and rule decoding;
- strict rejection of unknown formats, invalid weights, invalid chances, and unsafe roll ranges;
- deterministic rule binding and intentional cross-rule overlap;
- missing-pool rejection for enabled rules;
- stable definition IDs derived from datapack resource paths;
- disabled-rule ownership without legacy fallback reactivation;
- exact pool ID and weight parity for all six historical tiers;
- exact rule/table parity for all six authored loot-table lists;
- 748 total bindings and 744 unique table IDs;
- the dynamic modifier and exact 486-entry packaged global index;
- all previously covered catalog, packet, capability, selection, and legacy-modifier behavior.

Result: 20 tests, 0 failures, 0 errors, 0 skipped.

## Build and runtime validation

- Java 17 `build`: successful, including compilation, all tests, packaging, mixin processing, and reobfuscation.
- Isolated Java 17 structure-aware `runData`: successful with one dynamic and 485 legacy modifiers.
- Dedicated server: reached `Done`, loaded 6 pools, 6 rules, and 748 bindings, initialized the 452-entry authoritative catalog, and explicitly reported dynamic distribution as active.
- Client: reached the normal render loop, initialized OpenAL, built texture atlases, and applied the blueprint screen mixin without an injection or modifier-codec error.
- No Phase 4 global-loot-modifier parse errors were observed.
- Existing malformed recipe, language, sound-path, and missing gun-definition warnings still come from the third-party TaCZ packs recorded in prior phases.

## Remaining hands-on acceptance checks

ForgeGradle's development-server console still does not forward typed Minecraft commands, so a connected client is required for the final behavior pass:

1. Run `/reload` and confirm the server again reports 6 pools, 6 rules, and 748 bindings.
2. Set chance to 100% and rolls to 2-2, then confirm an eligible chest receives exactly two blueprints.
3. Confirm a valid blueprint from a non-`tacz` content-pack namespace can appear.
4. Override one built-in rule from a test datapack and confirm only its new target/chance/roll policy applies after `/reload`.
5. Disable one same-ID built-in rule with `enabled: false` and confirm its tables do not fall back to legacy distribution.
6. Introduce an invalid chance or missing pool, confirm reload reports the resource and source pack, then confirm the prior complete distribution remains in use.
7. Restore valid data, reload, and confirm the authoritative catalog is synchronized to a connected client.

Selector expressions, tag-driven pool composition, pool inheritance, and more advanced rule predicates are implemented by the backward-compatible format-2 model documented in `phase-5-implementation.md`.

The post-Phase-4 audit and stabilization changes are recorded in `phase-4-1-stabilization.md`.
