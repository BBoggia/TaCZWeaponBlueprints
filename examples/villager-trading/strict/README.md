# Strict villager economy

Install this folder in `<world>/datapacks/` and run `/reload` to disable the
four bundled material-purchase definitions. This does not override purchases
added by other packs. To disable all owned material purchases, apply these fields to the server's existing Blueprint configuration; retain its
other settings. This is a configuration example, not a replacement config file.

```toml
villagerMaterialPurchases = false
villagerAmmoSales = true
villagerDataSales = true
villagerFragmentSales = true
villagerBlueprintSales = false
villagerMaximumGunTier = 2
villagerMinimumPricePercent = 75
```

Keep the default Research Data analysis behavior and a usable protected Data
pool. Ammunition purchases provide Gunsmith Novice XP, and Research Note
purchases provide Ballistics Researcher Novice XP. Disabling both of a
profession's Novice routes suppresses its later stock as unreachable.

This removes direct renewable material-to-currency income. Stock limits remain
per villager per restock, so additional villagers still multiply supply.
Use `/gg villager_trades status` after changes to verify both XP routes and
inspect exclusions. Custom packs must check the actual resolved pools.
