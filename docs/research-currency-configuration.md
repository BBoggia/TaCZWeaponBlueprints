# Research currency configuration

The default economy uses physical Scrap for research and Research Data analysis
for weapon fragments or blueprints. Currency storage and Data processing are
independent choices. This guide covers server settings; use the
[migration guide](research-currency-migration.md) for established worlds and
[Data policy guide](research-data-policies.md) for datapacks.

## Choose the economy

Edit the server's Blueprint configuration through Server Settings with operator
permission level 2. Its Research and RP section contains the economy controls.
For offline editing, stop the server and edit
`config/taczweaponblueprints/blueprint.toml` in its game directory. Fields remain
at the TOML root; UI groups do not add table nesting. Start the server afterward.
Use `/reload` for datapack edits, not as a substitute for reloading an externally
edited settings file.

| Currency kind | Data behavior | Research spending | Research Data action |
| --- | --- | --- | --- |
| `PHYSICAL_ITEM` | `ANALYSIS` | Carried Scrap or the selected item | One item produces weapon fragments or a blueprint; default combination |
| `BANKED_RP` | `ANALYSIS` | Saved Research Points (RP) | Same analysis policies and station tiers |
| `PHYSICAL_ITEM` | `LEGACY_CURRENCY` | Carried currency items | Fixed currency redemption, including Redeem Stack |
| `BANKED_RP` | `LEGACY_CURRENCY` | Saved RP | Fixed RP redemption, including Redeem Stack |
| Either | `DISABLED` | Unchanged | Data processing unavailable; existing items remain |

In global `ANALYSIS` mode, a datapack can assign an individual input to analysis,
legacy redemption, or disabled processing. Global `LEGACY_CURRENCY` and
`DISABLED` take precedence. Data analysis is separate from the optional tiered
**weapon** Analyzer policy: the bundled weapon profile still guarantees its
configured blueprint result.

## Settings reference

| Setting | Default | Effect |
| --- | --- | --- |
| `researchCurrencyKind` | `PHYSICAL_ITEM` | Physical inventory currency or `BANKED_RP` |
| `researchCurrencyItem` | `taczweaponblueprints:scrap` | One registered item ID; one item is one unit |
| `researchCostMode` | `POINTS_AND_ITEMS` | Bench currency plus materials; `POINTS_ONLY` or `ITEMS_ONLY` select one channel |
| `researchPointCap` | `10000` | Banked balance limit only, configurable from 0 to 100000 |
| `researchDataBehavior` | `ANALYSIS` | Analysis, `LEGACY_CURRENCY`, or `DISABLED` |
| `researchDataAllowLearned` | `true` | Already learned weapons can be selected; per-item policies can override |
| `researchDataAllowUndiscovered` | `true` | Undiscovered weapons can be selected; per-item policies can override |
| `enableScrapLoot` | `true` | Bundled physical currency loot injection |
| `scrapLootBudget` | `32` | Limit per affected loot roll, 0–64, including currency already in that roll |
| `enableResearchDataLoot` | `true` | Bundled Research Data loot, independent of processing mode |
| `enableResearchPointAwards` | `true` | Gameplay currency awards in either currency mode |
| `enableCombatResearchPointAwards` | `false` | Additional permission for pack-authored combat awards |

The bundled player-kill Scrap loot uses `enableScrapLoot`; it does not require
combat award enablement. Turning off Data processing does not remove Data loot.
Turn off its loot switch as well if the pack should stop supplying those items.
Global progression, workstation, blacklist, and target policy checks still apply.

For an alternate item, these are the only currency fields needed:

```toml
researchCurrencyKind = "PHYSICAL_ITEM"
researchCurrencyItem = "minecraft:iron_ingot"
```

This example changes future payments, manual returns, gameplay awards, and
bundled physical supply. It does not exchange existing Scrap. Use a registered,
non-air item with a stack limit from 1 to 64. A missing or unsupported item makes
physical payment and delivery unavailable. A tag or a collection of interchangeable
items cannot serve as the currency. Item names and NBT do not multiply its value.

The item ID must be a complete lowercase `namespace:path` of at most 256
characters, excluding `minecraft:air`. Invalid values produce a configuration
validation error and retain the previous valid setting, or Scrap on first load.
They are never shortened into another item ID. These restrictions also apply
while banked mode is selected. A valid ID for a missing item remains configured;
it makes physical payments unavailable instead of substituting another item.

## Costs and available inventory

The existing integer `points` fields in datapacks mean active currency units:
8 points cost 8 Scrap in default mode or 8 RP in banked mode. Ordinary fragment
discounts reduce that channel using the existing integer rounding. Decimal
balances and exchange denominations are unsupported.

The Research Bench cost switch does not mask Analyzer costs. A Data policy can
charge its own currency and materials, although bundled Data analysis is free.
The optional tiered weapon preset has a separate paid attempt cost.

Only the 36 main-inventory and hotbar slots pay physical costs. Armor, offhand,
cursor, Analyzer slots, backpacks, and nearby containers are excluded. Costs can
span several stacks. If an item also matches an authored material requirement,
the combined allocation reserves separate units for each demand. A payment of
8 iron currency plus 2 iron ingredients therefore needs 10 ingots.

All resources for a whole-path purchase must fit together; repeated smaller
purchases can spend successive inventories. The shortest valid research route
does not change when the player moves currency between slots. After-purchase
awards cannot fund that purchase's initial cost.

## Delivery and duplicates

Manual currency rewards fill compatible Analyzer Output first, then main
inventory. An incompatible output blocks the action, and the entire result must
fit without dropped overflow. Legacy Redeem Stack completes one item at a time
and stops before the next item's reward cannot fit. Random analysis always
processes one Data item and requires an empty Output.

Gameplay awards fill main inventory and can drop overflow at the player's
location, within the documented [delivery limits](research-currency-integrations.md).
They do not create a saved physical-currency balance. Scrap can be traded and
follows normal inventory death drops. Knowledge and archived fragment progress
remain saved separately.

Data permits learned targets by default. Setting `researchDataAllowLearned = false`
filters them before selection; processing stops without consuming input only
when no valid target remains. This choice does not prevent reconstruction of
already owned analysis fragments. Completed blueprints can be traded, and default
Data outputs remain protected from recycling.

## Verify a pack change

1. Run `/gg progression currency inspect PlayerName` to check the active item,
   carried amount, dormant RP, and Data mode.
2. Run `/gg research status` and `/gg research export` after a datapack reload.
   Inspect the Analyzer's actual input preview for station odds and pool rules;
   the balance export is an operator model, not a quote for that player.
3. Compare all three Data items at all three stations. A missing tier is removed
   from selection; an empty pool preserves the input. Check one learned target
   and reconstruction from previously owned fragments.
4. Test an occupied output and a nearly full inventory. Manual failures should
   leave inputs intact. Check that a currency item also used as a material is
   counted once per paid unit.

Use the [supply and balance guide](research-economy-balance.md) to tune sources,
prices, and reward odds together. The [example packs](../examples/research-economy/README.md)
include unlearned-only pools, custom weights, and recyclable Data rewards.
