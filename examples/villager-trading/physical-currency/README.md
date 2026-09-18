# Use another physical currency

Merge the two fields in `blueprint.toml` into the existing server-owned Blueprint
configuration (`config/taczweaponblueprints/blueprint.toml`), or set them through
Fzzy Config. Keep the rest of your existing configuration. Restart after manual
file editing; the settings UI applies its normal synchronized update.

The example makes villager prices and material-purchase payouts use Emeralds,
independently of research payments. Replace the item ID with a registered,
stackable physical item to use your pack's currency. Prices must fit its stack
limit. Check `/gg villager_trades status` after applying it. Banked RP is not a
merchant payment item. Use `FOLLOW_RESEARCH_ITEM` to follow physical research
currency again. Existing stock keeps its uses and selected merchandise.
