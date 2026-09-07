# Blueprint Analyzer stations

The Blueprint Analyzer, Advanced Blueprint Analyzer, and Experimental Blueprint
Analyzer provide Analyzer tiers 1, 2, and 3. Each station has one input slot and
one output slot. Closing the screen returns both slots' contents to the player,
using Minecraft's normal container-close behavior, with drops if inventory space
is unavailable. Items do not stay inside the block.

Profiles without an Analyzer extension keep the existing deterministic weapon
operations. Tiered profiles select full blueprints, matching analysis fragments,
or an explicitly configured no-result outcome using the station and weapon
tiers. Marked fragments reconstruct independently of the ordinary fragment
preset. All three tiers also support Research Data analysis by default, with
higher tiers improving its quantities and blueprint chances. Analyzer tiers do
not change Research Bench or crafting requirements.

## Blocks and recipes

| Station | Analyzer tier | Block and item ID | Recipe materials | Harvest tool |
| --- | --- | --- | --- | --- |
| Blueprint Analyzer | 1 | `taczweaponblueprints:blueprint_recycler` | 6 iron ingots, redstone, grindstone, hopper | Any pickaxe |
| Advanced Blueprint Analyzer | 2 | `taczweaponblueprints:advanced_blueprint_analyzer` | 4 iron ingots, comparator, 2 redstone blocks, grindstone, hopper | Iron pickaxe or better |
| Experimental Blueprint Analyzer | 3 | `taczweaponblueprints:experimental_blueprint_analyzer` | 2 diamonds, comparator, 2 obsidian, grindstone, 2 iron ingots, hopper | Iron pickaxe or better |

Each tier has its own direct crafting-table recipe. Higher tiers do not consume
an existing station. Their recipe-book entries unlock when the player obtains
a comparator. Iron and diamond ingredients use Forge tags, so packs can provide
equivalent materials. Packs can replace the recipes and harvesting tags through
ordinary datapack resources.

Each station occupies two blocks side by side and faces the player when placed.
The basic station has a manual magnifier and open shelf; the Advanced station
adds microscope equipment, and the Experimental station adds electronic
inspection equipment. Both halves open the same menu. Inventory tooltips and
the screen show the numerical tier, and the screen title identifies the station.
All tiers appear in the mod's creative tab and its JEI/EMI information pages.
Breaking either half removes the whole station and yields at most one station
item, subject to the normal tool and explosion rules. Pistons cannot move it.

## Existing worlds

Block and item IDs and recipes remain unchanged. Existing inventory stacks use
the new models immediately. An existing single-block station gains its second
half when used, provided the adjacent space is empty and within the world border.
If that space is occupied, clear it and use the station again, or break and
place the station in a wider location. Neighboring blocks are never replaced
by this conversion.

## Menu and integration contract

All three blocks use `BlueprintRecyclerBlock`, the existing `blueprint_recycler`
menu type, and the same controller. They have no block entity, persistent machine
inventory, or processing timer. The opening packet remains a block position;
station identity does not add another player-data format. Analyzer previews and results use protocol 63.

The server captures the actual Analyzer block at the opening position. Validity
requires the same player, level, loaded chunk, block identity, and the usual
8-block interaction distance, and a complete matching pair of halves. Removing or replacing either half permanently
invalidates that open menu, including a same-tier replacement between menu ticks.
Changing one half to a mismatched facing invalidates the structure. Removing another station has
no effect. Requests against an invalid session cannot consume either slot.

`BlueprintRecyclerMenu.analyzerTier()` returns an optional `AnalyzerTier` while
the station is valid. On the server, it is derived from the bound physical block,
never from a packet's claimed tier. Server callers must still validate the active
menu before executing an action. On the client, tier lookup uses the current
block state, allowing a delayed block update to arrive after the opening packet;
that client value must not authorize rewards.

The `BlueprintRecyclerBlock(Properties)` compatibility constructor continues to
create tier 1. The tier-aware constructor accepts `AnalyzerTier` directly.
`BlueprintAnalyzerItem` reads its tooltip tier from its block, avoiding a second
independent tier assignment.

Policy definitions and outcome behavior are documented in the
[Analyzer behavior contract](analyzer-behavior-contract.md) and
[policy authoring guide](analyzer-policy-authoring.md).
