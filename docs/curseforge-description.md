# [TaCZ] Weapon Research & Blueprints

Find a blueprint in a chest, learn the recipe, and you can craft that weapon again even after you lose it. **[TaCZ] Weapon Research & Blueprints** adds exploration, research, and tiered crafting to **Timeless and Classics Zero (TaCZ)**.

If you have a particular weapon in mind, open the **Research Bench** and search for it. You can see which blueprints lead to it, what the route will cost, and which bench you need. If you find the gun itself first, take it to a **Blueprint Analyzer** for a chance at its blueprint or matching fragments.

## Find your way through the Tech Tree

- **Choose what to work toward.** Group weapons by branch to follow a route, or by weapon type to compare your options. Rows show the required Research Bench tier when tiers are enabled. A dashed link marks a choice between prerequisites.
- **Find a next step.** Amber tiles mark blueprints you can research now. Select a weapon to see its requirements and your available Scrap or Research Points (RP). Scroll through the materials for longer routes. In direct-learning mode you can buy the target and its required unlearned predecessors together.
- **Navigate a large pack.** Use **Ctrl+F** to search, **F** for the whole tree, and **N** to jump to the next ready blueprint or inspect a suggestion. **?** explains the colors and links. Choose **Classic view** for the original canvas, minimap, and target tracking. The bench remembers your view.
- **Learn once, craft again.** Learned recipes stay with your player through death and server restarts. Research Benches unlock blueprints. Crafting Workbenches make the items in TaCZ’s crafting screen. Servers can set the tiers for those two jobs independently.

## Blueprints beyond the bench

**Explore and recycle.** Look for blueprints, Scrap, and Research Data while looting. Spend physical Scrap on research, trade it, or recycle eligible duplicate blueprints. Servers can use banked RP as their research currency. Physical Scrap follows normal death drops. The **Blueprint Journal** keeps track of discoveries, learned recipes, and recent unlocks.

**Analyze what you bring home.** Basic, Advanced, and Experimental Blueprint Analyzers can process recovered equipment and Research Data. A Research Note targets Tier 1 weapons by default. Reports include Tiers 1–2, and Dossiers reach Tier 3. Archive matching analysis fragments to reconstruct a blueprint. Fragments found as loot have their own collection progress and can discount research or complete a blueprint, depending on server settings.

**Set up village trades.** A **Gunsmith Station** gives an unemployed adult villager access to the Gunsmith profession. Gunsmiths sell selected guns, matching ammo, and attachments. A **Ballistics Desk** attracts a Ballistics Researcher who sells Research Data and targeted analysis fragments for your Analyzer. Both professions have five trading levels and can be established in existing villages.

## Getting Started

1. Install the matching addon and dependencies on the client and server.
2. Use a found blueprint to learn its recipe. Open the Blueprint Journal to see what you know.
3. Craft a Research Bench, select a target, and gather its listed currency and materials. Hold **Research** to confirm when hold-to-confirm is enabled.
4. Take your crafting ingredients to the appropriate Crafting Workbench to make the unlocked item.

## Servers and modpacks

**Forge 1.20.1** uses Fzzy Config. To open the bundled ammo or attachment Tech Tree, go to **Server Settings → Research Progression**, keep **Enable Research** on, turn **Ammo Research** or **Attachment Research** to **On**, and save.

**NeoForge 1.21.1** offers the same category choices under **World & Server Settings → Quick Setup**. Keep **Research** on, turn on the categories you want, and choose **Apply**. **Pace** lets you choose Relaxed, Normal, or Hard to adjust blueprint loot, currency drops, and research prices together. **Advanced Settings** lets you tune those values individually, including Tier 2 and Tier 3 currency prices. Both settings views share a draft you can review before applying.

In NeoForge’s automatic tree, a weapon’s depth and strength help determine its research and crafting tiers. With the bundled weapons, the M249 sits in Tier 2 while the M4A1 and HK416D need Tier 3. Custom datapacks can assign different tiers.

The bundled profile starts with weapon research. These switches enable the other two trees without installing a datapack. Servers can also choose a single Research Bench, open Portable Research from the journal key, or use one Analyzer for all tiers. Pack authors can use **Research Designer** to edit branches, prerequisites, and costs in game, try a route with simulated progress, and export a datapack.

## Requirements

| | Forge | NeoForge |
| --- | --- | --- |
| Minecraft | 1.20.1 | 1.21.1 |
| Loader | Forge 47.x | NeoForge 21.1.233 or newer in the 21.1 series |
| Java | 17 | 21 |
| TaCZ | Official Forge build, 1.2.0 or newer | Official NeoForge build, 1.2.0 or newer |
| Fzzy Config | Forge 0.7.6 or later in the 0.7 series | NeoForge 0.7.7 or later in the 0.7 series |
| Kotlin dependency | Kotlin for Forge | Kotlin for Forge, NeoForge variant |
| Optional recipe viewers | JEI 15.x or EMI 1.1.x | JEI 19.x or EMI 1.1.x |

Use matching addon builds on the server and every client. When moving from the unofficial NeoForge TaCZ port, replace it with the official NeoForge file. Start a separate world when switching between Minecraft 1.20.1 Forge and 1.21.1 NeoForge.

Back up your world and configuration before updating. If you switch from banked RP to physical currency, see the [currency migration guide](https://github.com/BBoggia/TaCZWeaponBlueprints/blob/main/docs/research-currency-migration.md).

**TaCZ Magazines** integration is available on Forge. It adds empty-magazine crafting and optional Gunsmith magazine offers. Compatibility of TaCZ Magazines itself with the TaCZ 1.2.0 hotfix still needs in-game verification.

For help with a modpack, include your Minecraft version, loader, addon and TaCZ versions, installed content packs, and the relevant log. [Report an issue](https://github.com/BBoggia/TaCZWeaponBlueprints/issues)
