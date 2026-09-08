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
  minimap. Pin a goal to track its route and required resources, or use
  Affordable Now to find research within reach.
- **Research a whole path.** Select a locked weapon to preview the shortest valid
  route to it, including prerequisite research, currency, and materials. Buy the
  route together when you meet its requirements; a failed purchase does not
  consume a partial payment.
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

Operators can copy the server's tree or start an empty project using its
installed weapon catalog. Search the scrolling weapon list, add starting
weapons, extend paths with Add next, connect existing nodes, and drag weapons
into named branches. Nearby controls let you change costs, materials, Research
Bench requirements, and prerequisites. Undo/Redo and recovery saves support
editing as you go.

Test your design with simulated player progress before exporting an installable
datapack. Share a separate editable project bundle with other pack authors.
Capturing server data requires operator permission level 2; local editing and
project imports remain available without it. The server operator installs the
exported datapack and chooses when to activate its research profile.

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

Datapacks can change prerequisites, ingredients, branches, Analyzer odds for each
weapon/station tier pair, fragment quantities, reconstruction requirements, and
Research Data target pools. Servers can exclude learned Data targets; processing
then stops when no valid target remains. Scrap loot, Data loot, and gameplay
currency awards have independent controls. Legacy guaranteed analysis and fixed
Data currency redemption remain available as options.

The automatic Tech Tree adapts to the server's compatible TaCZ weapon catalog,
so each added gun does not need a separate blueprint definition. Pack authors
can instead create a tree containing only selected weapons. Weapons are the
only Tech Tree category enabled by default; research support for ammo and
attachments is available for packs that choose to enable it.

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
