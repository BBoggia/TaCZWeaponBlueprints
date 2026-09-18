# Add a gun pack to the market

This installable template replaces the starter gun and ammo pools with the
placeholder IDs `exampleguns:field_carbine` and `exampleguns:light_round`.
Replace both with actual recipe output IDs from your installed pack before use.
The gun must resolve to effective Tier 1. Its example price is 32 currency items
plus 6 Iron Ingots; review its real recipe and progression before keeping that price.

Copy this folder into `<world>/datapacks/` and run `/reload`. Inspect
`taczweaponblueprints:gunsmith/starter_gun` with `/gg villager_trades inspect`
and export the resolved table with `/gg villager_trades export`. Missing IDs
remain unavailable and use the authored fallback where possible. An installed
magazine addon also requires a usable base family and recipe for the gun.
Do not add a third core slot at the same level; replace an existing definition.
Existing villagers retain their established choices. Test the new pool with a
new villager; removal suspends old merchandise rather than rerolling it.
