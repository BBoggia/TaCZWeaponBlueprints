# Phase 5 Implementation

Date: 2026-08-24

Phase 5 turns the Phase 4 datapack model into a composable policy system. Datapacks can build pools from reusable tags, inherit and reweight other pools, select current TaCZ catalog entries by metadata, target families of loot tables, and constrain rules by dimension or luck. Existing format-1 pools and rules remain valid and retain identical behavior.

## Version compatibility

- Pool and rule `format: 1` resources decode exactly as before.
- Phase 5 fields require `format: 2`.
- Format 1 rejects Phase 5 fields instead of silently ignoring them.
- Blueprint tags have their own `format: 1` schema because they are a new resource type rather than an evolution of pools or rules.
- Unknown formats, unknown fields, and malformed optional fields fail reload.

## Blueprint tags

Tags load from:

```text
data/<namespace>/taczweaponblueprints/blueprint_tags/<path>.json
```

For example, `data/example/taczweaponblueprints/blueprint_tags/sidearms.json` defines `example:sidearms`:

```json
{
  "format": 1,
  "values": [
    "tacz:glock_17",
    "classicr:python_357"
  ]
}
```

Values are blueprint output IDs, not recipe IDs. Duplicate values are collapsed in authored order. Unknown current-catalog IDs are retained in the immutable datapack snapshot and simply remain ineligible until a matching TaCZ content pack is present.

## Format-2 pools

A format-2 pool may combine four source types:

```json
{
  "format": 2,
  "entries": [
    {"blueprint": "tacz:ak47", "weight": 8.0}
  ],
  "includes": [
    {"pool": "example:common_weapons", "weight": 0.5}
  ],
  "tags": [
    {"tag": "example:sidearms", "weight": 3.0}
  ],
  "selectors": [
    {
      "namespaces": ["tacz", "classicr"],
      "item_types": ["rifle", "smg"],
      "path_prefixes": ["gun/"],
      "exclude": ["tacz:gun/experimental"],
      "weight": 2.0
    }
  ]
}
```

At least one source is required. Every source is additive:

- `entries` adds exact blueprint IDs with authored weights.
- `includes` inherits the fully resolved referenced pool and multiplies every inherited entry and selector weight by the reference weight.
- `tags` adds every value in the named tag with the reference weight.
- `selectors` adds every current catalog entry that matches with the selector weight.

When one blueprint is added by multiple entries, tags, inherited pools, or selectors, its weights are summed. This makes composition predictable and lets datapacks intentionally boost entries that belong to several groups.

Selector lists use OR within a field and AND between fields. The example matches a blueprint when its namespace is either `tacz` or `classicr`, its normalized item type is either `rifle` or `smg`, and its path begins with `gun/`; excluded IDs are then removed. Omitting all positive fields means “the complete catalog except exclusions.”

## Pool resolution and caching

Pool and tag references are resolved during datapack preparation before the new snapshot is published. Resolution is deterministic and rejects:

- missing pool or tag references;
- inheritance cycles;
- inheritance deeper than 64 pools;
- resolved pools larger than 4,096 explicit IDs;
- more than 4,096 composed sources or selectors;
- non-finite, non-positive, or overflowing weights.

Inherited exact entries and selectors are flattened once. Catalog selectors cannot be finalized until the authoritative TaCZ catalog is available, so their results are resolved lazily per used pool and cached. Cache identity includes both the immutable loot snapshot and immutable server catalog map. A successful datapack reload or catalog rebuild therefore invalidates results automatically without manual bookkeeping.

Blacklists remain live and are applied after cached pool resolution. Changing a blacklist does not require rebuilding the selector cache.

## Format-2 rule targeting

Exact `loot_tables` remain available. A format-2 rule may additionally select table families:

```json
{
  "format": 2,
  "pool": "example:nether_rifles",
  "loot_tables": [
    "another_mod:vault/special"
  ],
  "loot_table_selector": {
    "namespaces": ["minecraft", "betterfortresses"],
    "path_prefixes": ["chests/"]
  }
}
```

Namespace and path-prefix fields use the same OR-within, AND-between behavior as catalog selectors. The exact list and selector are additive. If both match the same table, the rule is evaluated only once.

Table-selective legacy ownership extends naturally to selector rules:

- enabled and disabled selectors claim only matching tables;
- exact targets continue to claim only their listed tables;
- a disabled rule with neither exact targets nor a selector remains the explicit global opt-out.

## Runtime predicates

Format-2 rules may constrain evaluation by dimension and loot luck:

```json
{
  "format": 2,
  "pool": "example:nether_rifles",
  "loot_tables": [],
  "loot_table_selector": {
    "namespaces": ["minecraft"],
    "path_prefixes": ["chests/bastion"]
  },
  "predicate": {
    "dimensions": ["minecraft:the_nether"],
    "min_luck": 1.0,
    "max_luck": 3.0
  }
}
```

Dimensions are ORed. Dimension, minimum luck, and maximum luck are ANDed when more than one is present. Bounds are inclusive, finite, and ordered. Predicates are evaluated before chance and weighted selection, so a rule that does not match consumes no random values and adds no loot.

A predicate controls its dynamic rule only. It does not reactivate a legacy modifier when false, because the dynamic rule still owns the selected loot table.

## Safety limits

Phase 4 limits remain, with these Phase 5 additions:

- at most 4,096 blueprint-tag values;
- at most 256 terms per catalog or loot-table selector;
- at most 256 predicate dimensions;
- at most 64 inherited pool levels;
- at most 4,096 authoritative catalog entries, matching the catalog packet limit.

The complete prepared snapshot remains atomic. Any format, reference, cycle, predicate, selector, bound, or size failure prevents publication and leaves the previous complete snapshot active.

## Automated coverage

Phase 5 adds tests for:

- strict format-2 pool and rule decoding;
- continued format-1 parity and rejection of Phase 5 fields in format 1;
- malformed optional lists, weights, namespaces, and selector objects;
- additive tag and inherited-pool weights;
- inherited selector multipliers;
- missing tags and inheritance cycles;
- deterministic catalog selection and exclusions;
- exact/selector rule deduplication;
- selector-based ownership for enabled and disabled rules;
- dimension and inclusive luck predicates.

The full Java 17 suite now contains 32 tests.

## Build and runtime validation

Phase 5 was validated against a clean temporary copy rather than the existing development world:

- `cleanTest test build` succeeds on Java 17 with all 32 tests passing;
- a dedicated-server smoke test reaches `Done` with a temporary datapack containing one blueprint tag, an inherited format-2 pool, a catalog selector, a loot-table selector, and a dimension/luck predicate;
- the reload publishes the expected additional tag, pool, rule, and selector-rule counts while building the authoritative 452-entry TaCZ catalog;
- structure-aware `runData` succeeds and produces 486 modifiers: all 485 legacy files are byte-identical, while the dynamic modifier and global index are JSON-equivalent after normalization;
- all source and generated JSON parses successfully;
- the release JAR contains the new Phase 5 runtime classes, 486 modifiers, and all 12 built-in pool/rule resources, with no `.cache` or `.DS_Store` entries.

## Remaining hands-on acceptance checks

The loader and schema portions of the acceptance test are complete. The remaining checks require interactive loot generation or live pack mutation:

1. Confirm inherited, tagged, and selector-matched blueprints appear at their expected relative weights over a statistically useful sample.
2. Add a matching TaCZ content pack, reload, and confirm its selector-matched entries become eligible without editing the pool.
3. Confirm the same rule does not run outside its configured dimension or luck range.
4. Introduce a missing tag and an inheritance cycle, confirm reload fails with a useful diagnostic, and confirm the prior distribution remains active.

Operator inspection for these dynamic policies is implemented in `phase-6-implementation.md`.
