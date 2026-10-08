# [TaCZ] Weapon Research & Blueprints

Build your arsenal through exploration, blueprints, and research. This add-on
turns **Timeless and Classics Zero (TaCZ)** into a configurable progression
system: find weapon blueprints, collect Scrap, analyze recovered guns, and work
through a Tech Tree toward the weapons you want.

Learning a blueprint permanently unlocks its matching recipe for your player.
Knowledge stays with you through death, dimension changes, logout, and server
restarts. Crafting can also require a Workbench level or other server-defined
progression requirements.

## Explore, Research, and Craft

- **Find blueprints while exploring.** Configurable loot rewards you with
  blueprints, Scrap, and Research Data. Learn recipes from blueprints or recycle
  supported duplicates at a Blueprint Analyzer.
- **Collect physical Scrap.** Earn it through exploration and player kills,
  carry it to pay research costs, or trade it with other players. Scrap follows
  normal inventory death drops. Servers can choose banked Research Points (RP).
- **Plan your next weapon.** Browse a searchable Tech Tree with weapon artwork,
  branches, pan, zoom, keyboard navigation, and an interactive minimap. Track a
  target and its requirements, or filter to research you can afford.
- **See the full cost.** Select a blueprint to compare the resources you have
  with those needed for its prerequisite path. Review and buy a whole path
  together; a failed purchase does not consume a partial payment.
- **Build better stations.** Three Research Bench tiers support research, and
  three crafting Workbench levels open TaCZ's native crafting screen. Each
  station tier has its own recipe and model. Servers configure research and
  crafting requirements separately.
- **Keep a Blueprint Journal.** Review discoveries, learned recipes, recent
  unlocks, and completion. Getting Started guidance explains progression and
  the server's available ways to earn research currency.

Servers can use a single Research Bench or enable **Portable Research** through
the Journal / Research key. Costs and other progression requirements still apply.
Toggle branch labels or use the details panel to focus the tree on a blueprint
or branch. The panel adapts to smaller screens while keeping purchase controls
available.

## Analyze Weapons and Research Data

Bring supported equipment to a **Blueprint Analyzer** to recover research
rewards. The Basic, Advanced, and Experimental Analyzers compare weapon and
station tiers to determine blueprint or fragment rewards. Higher-tier weapon
attempts can produce nothing; same-tier and lower-tier attempts produce a
blueprint or fragments. The screen shows outcomes, costs, and saved progress.
Single Analyzer mode lets the basic station handle all weapon tiers under each
weapon's same-tier rules.

Research Data supplies another route to blueprints. By default, **Research
Notes** target Tier 1 weapons, **Research Reports** target Tiers 1–2, and
**Research Dossiers** target Tiers 1–3. Higher Analyzer tiers improve Data
outcomes. Archive matching analysis fragments to reconstruct a blueprint;
already learned targets can still produce duplicate rewards.

Ordinary fragments found through loot have separate progress and settings.
Completed sets can discount research, reconstruct a blueprint, or return currency
for a learned weapon. Servers also control weapon recovery and recycling.
Successful processing returns loaded ammo, with overflow dropped at your feet.

## Establish Village Traders

Place a **Gunsmith Station** or **Ballistics Desk** near an unemployed adult
villager. The **Gunsmith** sells selected guns, matching ammo, and attachments.
With supported TaCZ Magazines content, a gun that needs a magazine also has a
matching empty basic-magazine offer at the same level.

The **Ballistics Researcher** sells Research Data and targeted analysis fragments.
Later fragment bundles continue the villager's study targets. Bring those
supplies to the Blueprint Analyzer. Buying a gun or research item does not
learn a recipe automatically; protected reconstructed blueprints respect
prerequisites and cannot be recycled for currency under the default rules.

Both professions use the familiar trading screen and five villager levels.
Products stay selected through restocking; stock is shared between players.
They accept physical research currency, or Emeralds in banked RP mode. Pack
authors can change stock and currency or disable material purchases. More
villagers multiply renewable supply. Craftable job sites work in existing worlds.

## Design and Configure Progression

**Research Designer** lets you build custom Tech Trees in game. Copy the server's
tree or start an empty project, add weapons, connect paths, and edit costs and
prerequisites. Test with simulated progress, export an installable datapack, or
share an editable project. Open it from the title or pause menu,
`/researchdesigner`, or an assigned Controls key. Capturing server data requires
operator permission level 2; local editing does not.

Configure loot, starting blueprints, discovery visibility, progression exemptions,
and separate research and crafting tiers. Datapacks can change tree branches,
requirements, Analyzer rewards, fragments, Data pools, and villager trades.
Progression Gates support advancements, Bench tiers, and custom milestones.
The automatic tree adapts to compatible TaCZ catalogs; pack authors can instead
select exact weapons and optionally enable ammo or attachment research.
See the [operations guide](https://github.com/BBoggia/TaCZWeaponBlueprints/blob/main/docs/operations-and-migration.md).

With **TaCZ Magazines 0.2.0-Beta**, craft empty magazines at the three Workbench
levels or TaCZ's ammo Workbench. Servers can require learned compatible weapons
and minimum Workbench levels. Eject inserted magazines before analyzing a gun.

## Getting Started

1. Install matching mod versions on the client and server, with required dependencies.
2. Explore for blueprints, Scrap, and Data; use blueprints to learn recipes.
3. Open the Journal, then craft a Research Bench and the appropriate crafting Workbench.
4. Build an Analyzer to process equipment and Data or archive fragments. Leave
   two adjacent blocks of space; either half opens it.
5. Establish village traders for equipment and research supplies.

## Requirements

Requires **Minecraft 1.20.1**, **Forge 47.x**, **TaCZ 1.1.8 or newer**,
**Fzzy Config 0.7.6 or later in the 0.7 series** with Kotlin for Forge,
and **Java 17**. Built with Forge 47.3.0; development uses TaCZ 1.1.8-hotfix.
TaCZ has no upper version limit in the dependency requirement, but future API
changes can still require a compatibility update.
JEI 15.x and EMI 1.1.x are optional; follow their own compatibility requirements
when installing both.

Back up worlds before updating. Supported migration preserves learned recipes,
discoveries, fragment archives, and saved RP. Changing currency modes does not
turn RP into items automatically; see the [currency migration guide](https://github.com/BBoggia/TaCZWeaponBlueprints/blob/main/docs/research-currency-migration.md).
[Report issues](https://github.com/BBoggia/TaCZWeaponBlueprints/issues) with mod and
dependency versions, installed content packs, and the relevant `latest.log`.
