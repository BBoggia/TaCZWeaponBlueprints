# [TaCZ] Weapon Research & Blueprints

Build your arsenal through exploration, blueprints, and research. This add-on
turns **Timeless and Classics Zero (TaCZ)** into a configurable progression
system: find weapon blueprints, collect Scrap, analyze recovered guns, and work
through a Tech Tree toward the weapons you want.

Learning a blueprint permanently unlocks its matching recipe for your player.
Your knowledge stays with you through death, dimension changes, logout, and
server restarts. Crafting can also require an appropriate Workbench level or
other progression requirements set by the server.

## Explore, Research, and Craft

- **Find blueprints while exploring.** Configurable world loot rewards you with
  blueprints, Scrap, and Research Data. Use a blueprint to learn its recipe, or
  recycle supported duplicates at a Blueprint Analyzer.
- **Spend physical Scrap.** Scrap is the default research currency. Carry it to
  pay research costs, trade it with other players, or save it in storage. It
  follows normal inventory death drops. Servers can use banked Research Points
  (RP) instead.
- **Plan your next weapon.** Browse a searchable Tech Tree with distinct
  branches, weapon artwork, pan and zoom, keyboard navigation, and an interactive
  minimap. Track a goal and its required resources, or use Affordable Now to find
  research within reach.
- **See what you need.** Click a blueprint to open its details. A Resource / Have /
  Required table compares your currency and materials with the full purchase
  cost. Follow prerequisite links to inspect earlier blueprints, then use Back
  to return to your target.
- **Research a whole path.** Select a locked weapon and use Path to review the
  shortest valid route included in its purchase, with the required blueprints,
  currency, and materials. Buy the route together when you meet its requirements;
  a failed purchase does not consume a partial payment.
- **Build better stations.** Three Research Bench tiers support research
  progression, while three crafting Workbench levels open TaCZ's native crafting
  screen. Research and crafting requirements are configured separately. Every
  station tier has a direct crafting recipe and its own model.
- **Keep a Blueprint Journal.** Review discoveries, learned recipes, recent
  unlocks, and completion. Getting Started guidance explains the progression
  loop and the server's available ways to earn research currency.

Servers can also choose a single Research Bench or enable **Portable Research**
to open the Tech Tree through the Journal / Research key. Costs and other
progression requirements still apply. Crafting Workbench tiers stay independent.

Toggle branch labels, click a visible
branch title to frame it, or use View in the details panel to focus your blueprint
or branch. Weapon artwork expands when in-node names are hidden, and earlier
prerequisites have a lighter highlight than immediate requirements. The details
panel adapts to smaller screens while keeping purchase controls available.

## Analyze Weapons and Research Data

Bring supported weapons to a **Blueprint Analyzer** to recover research rewards.
The Basic, Advanced, and Experimental Analyzers use both the weapon's tier and
the station's tier to determine the result: a full blueprint, matching Blueprint
Fragments, or a configured chance of no reward.

Prefer one Analyzer? **Single Analyzer** mode lets the basic station handle all
weapon tiers using each weapon's same-tier reward rules. A separate setting
chooses Research Data reward strength without changing its target pools.

With the default tiered rules, weapons at or below the Analyzer's tier always
produce a blueprint or fragments. Attempting a higher-tier weapon can produce
nothing. Better Analyzers improve the odds for lower-tier weapons. The screen
shows the possible outcomes, costs, and saved fragment progress before you act.
Loaded weapons return their ammo to your inventory after a successful analysis
or recycling action, with overflow dropped at your feet.

Research Data offers another way to build your collection. By default:

- **Research Notes** target Tier 1 weapons.
- **Research Reports** target Tier 1 or Tier 2 weapons.
- **Research Dossiers** target Tier 1, Tier 2, or Tier 3 weapons.

Reports and Dossiers can also produce a full blueprint, and higher-tier
Analyzers improve fragment quantities and blueprint chances. Archive matching
analysis fragments to complete a set and reconstruct a blueprint. Already
learned weapons can still be selected by default, so duplicate rewards remain
possible.

Ordinary fragments found through blueprint loot have separate settings and
progress. Depending on the server's rules, completed sets can discount matching
research, reconstruct a blueprint, or return currency for a learned weapon.
Servers can also configure weapon recovery and recycling, including whether
crafted guns can be processed.

## Create Your Own Tech Trees

**Research Designer** lets you build and share custom Tech Trees in game. Open it
from the title or pause menu, with `/researchdesigner`, or through an assigned
Controls key.

Copy the server's tree or start an empty project from its weapon catalog. Add
weapons, connect paths, organize branches, and edit costs and prerequisites.
Undo/Redo and recovery saves support editing as you go.

Test with simulated player progress, export an installable datapack, or share an
editable project bundle. Capturing server data requires operator permission
level 2. Local editing and imports remain available without it; operators
control which exported trees become active.

## Getting Started

1. Install the mod and its required dependencies on the client and server.
2. Explore configured loot locations for blueprints, Scrap, and Research Data.
3. Use a blueprint to learn a recipe, then check your Blueprint Journal.
4. Craft a Research Bench to browse and purchase research. Use the appropriate
   crafting Workbench level to make your learned weapons.
5. Build a Blueprint Analyzer to process Research Data, analyze supported guns,
   archive fragments, and recycle supported duplicates.

Allow two adjacent blocks of space for a Blueprint Analyzer. Either half opens
it. Optional JEI and EMI integrations explain items and stations without
revealing hidden research targets.

## Servers and Modpacks

Configure loot, costs, starting blueprints, discovery visibility, progression
exemptions, and separate research and crafting tiers. Progression Gates can
require advancements, Research Bench tiers, or custom server milestones.
Compatible TaCZ content-pack workstations can receive their own crafting levels.

Datapacks can change prerequisites, ingredients, branches, Analyzer rewards,
fragment requirements, and Research Data target pools. Loot and gameplay currency
awards have independent controls. See the
[operations guide](https://github.com/BBoggia/TaCZWeaponBlueprints/blob/main/docs/operations-and-migration.md)
for pack-author settings.

The automatic Tech Tree adapts to compatible TaCZ weapon catalogs. Pack authors
can also create trees containing selected weapons or enable optional ammo and
attachment research.

## Requirements

- **Minecraft 1.20.1** with **Forge 47.x**; built with Forge 47.3.0.
- **Timeless and Classics Zero (TaCZ) 1.1.8 or later in the 1.1 series**;
  development testing uses 1.1.8-hotfix.
- **Fzzy Config 0.7.6 or later in the 0.7 series**, with its required dependencies,
  including Kotlin for Forge.
- **Java 17.** JEI 15.x and EMI 1.1.x are optional client-side integrations.

Install matching versions of [TaCZ] Weapon Research & Blueprints on every client
and the server, with only one version in each `mods` folder. Unlock and crafting
checks are server-authoritative.

Back up existing worlds before updating. Supported player data migrates while
preserving learned blueprints, discoveries, fragment archives, and saved RP.
Changing currency modes never automatically turns saved RP into items. See the
[currency migration guide](https://github.com/BBoggia/TaCZWeaponBlueprints/blob/main/docs/research-currency-migration.md)
for keeping the banked economy or withdrawing saved RP into physical currency.

For help, [report an issue](https://github.com/BBoggia/TaCZWeaponBlueprints/issues)
with your mod and dependency versions, installed TaCZ content packs, and the
relevant `latest.log`.
