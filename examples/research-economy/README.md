# Research economy examples

Use the server Blueprint configuration for these independent mode choices:

```toml
# Alternate physical currency (supply and payments both use this item):
researchCurrencyKind = "PHYSICAL_ITEM"
researchCurrencyItem = "minecraft:iron_ingot"
researchDataBehavior = "ANALYSIS"
```

To preserve the old economy, run `/gg progression currency legacy apply`, or set
`researchCurrencyKind = "BANKED_RP"` and
`researchDataBehavior = "LEGACY_CURRENCY"` explicitly. Selecting another item
does not exchange existing Scrap. Iron is an example, not a recommended balance:
vanilla farms and overlapping material costs change its value.

The three datapacks below are independent alternatives. Install one at a time
and run `/reload`. Build distributable ZIPs with `packageResearchEconomyDatapacks`.

- `unlearned-only` excludes known targets for every Data item. Processing stops
  without consuming input when no targets remain.
- `weighted-pool` gives Reports a 3:1 Tier 1/Tier 2 split and restricts its pool
  to selected TaCZ weapons, with an extra AK-47 weight. Missing targets are excluded.
- `recyclable-data` lets future Data outputs be recycled under the normal rules.
  It also enables shared reconstruction recycling; existing protected items keep
  their restrictions. Review fragment return and recipe costs before using it.

The shipped defaults use protected Data outputs, equal tier weights, and allow
known targets. [Supply and balance guidance](../../docs/research-economy-balance.md)
explains the model assumptions and the loot override format.
