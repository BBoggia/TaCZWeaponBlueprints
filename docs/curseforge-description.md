# [TaCZ] Weapon Research & Blueprints

This addon adds blueprint progression to **Timeless and Classics Zero (TaCZ)**. Find a blueprint in loot, research a weapon at a **Research Bench**, or analyze a gun you’ve recovered for a chance at its blueprint. Once you learn a recipe, you can craft that weapon at the required Workbench level.

Learned recipes are saved per player and carry over through death, dimension changes, logout, and server restarts.

**A note on the defaults:** I set up the default progression for my own modpack. You can use it as it is or change the loot, research costs, bench requirements, and weapon availability to fit yours.

## Explore, Research, and Craft

- **Find blueprints while exploring.** Loot chests can contain blueprints, Scrap, Research Data, and Blueprint Fragments. Right-click a blueprint to learn its recipe. You can recycle eligible duplicates at a **Blueprint Analyzer**.
- **Collect and spend Scrap.** Scrap is a physical item you can find, earn from player kills, trade, or spend on research. It follows normal item-drop rules when you die. Servers can also use banked **Research Points (RP)**.
- **Plan your next unlock.** The Research Bench organizes the **Tech Tree** by branch or weapon type. Press **Ctrl+F** to search, **F** to see the whole tree, or **N** to jump to a blueprint you can afford or inspect a suggestion. Amber tiles mark blueprints you can research now.
- **Check the cost before spending.** Select a blueprint to see its prerequisites, required bench tier, currency cost, and materials. In direct-learning mode, you can unlock a weapon and its missing prerequisites in one purchase. If the price or route changes while you hold **Research**, the bench asks you to confirm again.
- **Research and craft at separate stations.** Three Research Bench tiers unlock blueprints. Three Crafting Workbench levels let you make learned items in TaCZ’s crafting screen. Servers can set research and crafting tier requirements independently, use a single Research Bench, or enable **Portable Research** through the Journal / Research key.
- **Keep track of what you know.** The **Blueprint Journal** lists discoveries, learned recipes, recent unlocks, and collection progress.

## Analyze Weapons and Research Data

Bring a recovered weapon to a **Basic**, **Advanced**, or **Experimental Blueprint Analyzer** for a chance at its blueprint or matching analysis fragments. Higher-tier Analyzers improve your odds. An attempt above the Analyzer’s tier can consume the weapon without producing a reward. Servers can enable **Single Analyzer** mode to handle every tier at the basic station.

You can also process **Research Data**. By default, **Research Notes** target Tier 1 weapons, **Research Reports** cover Tiers 1–2, and **Research Dossiers** cover Tiers 1–3. Archive enough matching analysis fragments to reconstruct a blueprint.

Blueprint Fragments found in loot have their own collection progress. Depending on server settings, a completed set can reduce a weapon’s research cost, produce its blueprint, or return currency if you already know the recipe.

## Establish Village Traders

Place a job site near an unemployed adult villager to establish either profession in an existing village.

- **Gunsmith Station.** Gunsmiths sell guns, matching ammo, and attachments. On Forge, they can also offer matching empty basic magazines when **TaCZ Magazines** is installed.
- **Ballistics Desk.** Ballistics Researchers sell Research Data and analysis fragments for specific weapons. Take these to the Blueprint Analyzer to work toward the blueprint you want.

Both professions have five trade levels. Each villager keeps its selected products when it restocks, and players share that villager’s stock. By default, traders accept your server’s physical research currency or Emeralds in banked RP mode. Buying a gun gives you the item. Learning its blueprint is a separate step.

## Design and Configure Progression

**Research Designer** lets pack authors build Tech Trees in game. Start an empty project or copy the server’s tree, add weapons, connect prerequisites, set costs, and test a route with simulated progress. Export the result as a datapack or share the editable project. Open the Designer from the title menu, pause menu, or `/researchdesigner`. Copying a server’s tree requires operator permission level 2.

Server settings control loot, starting blueprints, research costs, bench tiers, and progression exemptions. Datapacks can also change branches, Analyzer rewards, Research Data pools, and villager trades. On **NeoForge**, **Quick Setup → Pace** offers Relaxed, Normal, and Hard presets for loot and research prices. **Advanced Settings** lets you adjust those values individually.

**Ammo and attachment research** are included. The default profile enables only the weapon tree. To enable either of the other trees on your server:

- **Forge 1.20.1** — Open **Server Settings → Research Progression** in Fzzy Config. Keep **Enable Research** on, turn on **Ammo Research** or **Attachment Research**, and save.
- **NeoForge 1.21.1** — Open **World & Server Settings → Quick Setup**. Keep **Research** on, enable **Ammo research** or **Attachment research**, and choose **Apply**.

**TaCZ Magazines on Forge** adds empty-magazine crafting at all three Crafting Workbench levels and TaCZ’s ammo Workbench. Servers can require a learned compatible weapon or a minimum Workbench level. Eject an inserted magazine before analyzing the gun. Magazine recipe discovery after a datapack reload has been verified on a dedicated Forge server with **TaCZ 1.2.0-hotfix** and **TaCZ Magazines 0.2.0-Beta**.

## Getting Started

1. Install the matching addon and dependencies on the server and every client.
2. Find a blueprint in loot and right-click it to learn the recipe. Open the **Blueprint Journal** to check your unlocks.
3. For a specific weapon, open a **Research Bench**, select it in the Tech Tree, and gather the listed Scrap and materials.
4. Take your crafting ingredients to the required **Crafting Workbench** to make the item.
5. Build a **Blueprint Analyzer** when you have recovered equipment, Research Data, or fragments to process. Leave room for two adjacent blocks when placing it.

## Requirements

| | Forge | NeoForge |
| --- | --- | --- |
| **Minecraft** | 1.20.1 | 1.21.1 |
| **Loader** | Forge 47.x | NeoForge 21.1.233 or later in the 21.1 series |
| **Java** | 17 | 21 |
| **TaCZ** | Official Forge build, 1.2.0 or newer | Official NeoForge build, 1.2.0 or newer |
| **Fzzy Config** | Forge 0.7.6 or later in the 0.7 series | NeoForge 0.7.7 or later in the 0.7 series |
| **Kotlin dependency** | Kotlin for Forge | Kotlin for Forge, NeoForge variant |
| **Optional recipe viewers** | JEI 15.x or EMI 1.1.x | JEI 19.x or EMI 1.1.x |

Use the official TaCZ build for your loader. If you previously used the unofficial NeoForge port, replace it with the official NeoForge file. Back up your world before updating and start a separate world when moving from Forge 1.20.1 to NeoForge 1.21.1.

If you’re switching an existing server from banked RP to physical Scrap, follow the [currency migration guide](https://github.com/BBoggia/TaCZWeaponBlueprints/blob/main/docs/research-currency-migration.md). For support, include your Minecraft version, loader, addon and TaCZ versions, installed content packs, and relevant `latest.log` when you [report an issue](https://github.com/BBoggia/TaCZWeaponBlueprints/issues).
