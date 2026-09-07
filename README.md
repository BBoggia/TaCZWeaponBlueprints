# [TaCZ] Weapon Research & Blueprints

[TaCZ] Weapon Research & Blueprints turns the Timeless and Classics Zero (TaCZ) arsenal into a
persistent progression system built around exploration, blueprints, and
research.

Players can find blueprints in world loot, permanently learn matching TaCZ
gun-smithing recipes, collect physical Scrap, and spend it on unlocks in a
visual Tech Tree. Learned recipes and archived progress remain through death,
dimension changes, logout, and server restarts. Scrap is an inventory item that
can be traded and follows normal death drops; banked Research Points (RP) remain
an optional server setting.

## Features

- Configurable blueprint loot with live server settings and datapack support.
- Target-specific Blueprint Fragments that share existing blueprint loot rules,
  favor unlearned weapons when possible, and can be archived for research
  discounts or protected blueprint reconstruction.
- Permanent per-player recipe knowledge enforced by the server.
- A Blueprint Journal for discoveries, learned recipes, recent unlocks,
  completion, active rules, and a reusable Getting Started guide.
- An edge-to-edge Research Bench Tech Tree with search, pan, zoom, keyboard
  navigation, an interactive minimap for large trees, and selected-weapon
  details.
- Three Research Bench tiers for the Tech Tree and three dedicated crafting
  Workbench levels using TaCZ's native crafting screen.
- Guided research tools for pinning a target, highlighting its complete route,
  tracking currency and material progress, and filtering to affordable unlocks.
- Automatic weapon placement based on strength, handling, range, and play
  style, including compatible weapons added by TaCZ content packs.
- Authored-only Tech Trees for pack authors who want complete control over
  which weapons appear and how they are arranged.
- Shortest-path research that can preview and unlock a complete prerequisite
  route in one atomic transaction.
- Physical Scrap from exploration and player kills, with configurable banked RP
  and material-only research alternatives.
- A Blueprint Analyzer for archiving Blueprint Fragments, reverse engineering
  equipment, recycling learned duplicate blueprints, and analyzing Research Data
  for blueprints or fragments. Higher Analyzer tiers improve Data outcomes
  and weapon-analysis odds. Above-tier weapon attempts can produce no reward;
  same-tier and lower-tier attempts produce blueprints or fragments.
- Optional JEI and EMI information pages that explain the progression
  workstations without exposing hidden research targets or enabling recipe
  transfer bypasses.

Weapons are the only Tech Tree category enabled by default. Pack authors can
add ammo and attachments to research using the included datapack data.

Research Designer lets operators copy the installed server tree or start an empty
tree with its weapons, then build branches, edit requirements, test simulated
players and export a custom datapack. The catalog scrolls beside the canvas;
Add next connects new weapons in one action. Open it from the title or pause menu,
`/researchdesigner`, or an assigned Controls key. See the
[workspace guide](docs/research-designer-workspace.md) for the workflow.

## Getting Started

1. Install the mod and its required dependencies on both the client and server.
2. Explore configured loot locations to find blueprints, Scrap, and Research Data.
3. Use a blueprint to learn its recipe permanently.
4. Open the Blueprint Journal to review progress and ways to earn research currency.
5. Craft a Research Bench to browse and purchase Tech Tree unlocks, then use the
   appropriate Workbench level to manufacture learned weapons.
6. Analyze a Research Note, Report, or Dossier at the Blueprint Analyzer for
   weapon fragments or a blueprint. Archive matching analysis fragments there
   to reconstruct a blueprint; ordinary loot fragments follow the server preset.
7. Use the Analyzer to reverse engineer equipment or recycle supported
   duplicates for research currency. Collect Input and Output when finished;
   closing the menu returns them to your inventory or drops them if it is full.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.x; validated with 47.3.0 |
| Timeless and Classics Zero | 1.1.8-hotfix (`[1.1.8,1.2)`) |
| Fzzy Config | 0.7.6 for Forge (`[0.7.6,0.8)`) |
| Kotlin for Forge | 4.11.x, required by Fzzy Config |

JEI 15.x and EMI 1.1.x are optional client-side integrations.

Install matching [TaCZ] Weapon Research & Blueprints versions on both the client and server.
TaCZ content packs can be added or removed independently; the available
blueprint catalog is rebuilt from the recipes currently loaded by the server.

## Configuration

The synchronized Server Settings screen organizes its controls into General
Progression, Discovery and Loot, Research and RP, Blueprint Analyzer, Starting
Access, and a collapsed Advanced section. Related controls become visibly
inactive when their parent feature or preset is disabled, with a tooltip that
explains what must be enabled.

Server controls include:

- blueprint progression and discovery tracking;
- default loot chance and roll limits;
- discovery pacing presets;
- gun, ammo, and attachment blacklists;
- the Blueprint Journal, Research Bench, and Blueprint Analyzer;
- active currency item or banked RP, research cost channels, and the banked cap;
- Research Data processing, learned/undiscovered targets, and independent Scrap
  and Research Data loot;
- research and crafting tier enforcement, external workstation mappings, and
  Creative-mode tier bypass;
- found-weapon recovery and duplicate recycling;
- undiscovered blueprint visibility;
- Creative-mode cost bypass;
- starting blueprints and progression exemptions; and
- the active research profile.

Each player also has Personal Settings for research confirmation, currency
notifications, reduced motion, the optional background grid, and Compact,
Balanced, Spacious, or Custom Tech Tree layouts. The fullscreen minimap can
appear automatically for large trees, remain always visible, or stay hidden.
Advanced canvas, weapon, level, and connection spacing plus crossing-reduction
and compaction controls remain available under the collapsed Custom section.
These settings affect presentation only and do not change server progression.

## Servers and Modpacks

Datapacks can customize:

- blueprint loot pools and loot-table rules;
- research profiles and per-blueprint costs;
- item or tag ingredients;
- prerequisites and alternate route groups;
- discovery visibility and tree inclusion;
- Tech Tree groups, entry points, and presentation;
- automatic weapon placement;
- gameplay research-currency awards;
- Research Data target pools, weights, odds, prices, and shared reconstruction; and
- workstation tiers, Blueprint Fragment rules, and Progression Gates.

Automatic trees include every weapon available for research and generate their
placement and prerequisites. Authored-only trees include only entries selected
by the pack author. Choose one mode for each weapon tree; automatic and
authored-only placement don't mix.

Invalid datapack reloads keep the last working settings active. Upgrades retain
learned blueprints, discoveries, archives, and saved RP. Configurations missing
the new economy selectors use physical Scrap and Data analysis, so operators
who want the old economy should explicitly select banked RP and legacy Data
redemption. Saved RP is preserved separately; conversion is an operator choice.
Start with the [currency configuration guide](docs/research-currency-configuration.md)
and [upgrade and withdrawal instructions](docs/research-currency-migration.md).

See the [research-tree authoring guide](docs/research-tree-authoring.md) for
resource locations, formats, selectors, prerequisites, and tree authority. The
[operations and migration guide](docs/operations-and-migration.md) covers
upgrades, reload behavior, save compatibility, and rollback considerations.
The [Blueprint Fragment supply guide](docs/blueprint-fragment-supply.md)
explains fragment identity, loot replacement, weighting, and diagnostics.
The [Research Bench and crafting Workbench guide](docs/research-bench-tiers-and-crafting.md)
defines tier assignment, direct station recipes, native crafting,
compatibility tables, and reload behavior.
The [Blueprint Analyzer presets and balance guide](docs/analyzer-presets-and-balancing.md)
documents the default tier matrix, optional legacy mode, weapon-specific overrides, migration steps,
and an offline report of expected weapon, currency, and material costs.

## Operator Commands

All `/gg` commands require permission level 2.

| Command | Purpose |
| --- | --- |
| `/gg reloadRecipes` | Rebuild and synchronize the blueprint catalog. |
| `/gg loot status` | Show the active loot snapshot and configuration. |
| `/gg loot inspect <loot_table>` | Explain how a loot table is handled. |
| `/gg loot pool <pool_id>` | Inspect a prepared blueprint pool. |
| `/gg loot preview <loot_table>` | Preview effective chances, rolls, and expected additions. |
| `/gg progression inspect <player> [blueprint_id]` | Inspect player progress and the independent research/crafting result for a blueprint. |
| `/gg progression reset <targets> <state>` | Reset learned, discovered, point, award, fragment, criterion, or complete progression state. |
| `/gg progression currency inspect <player>` | Show carried currency, saved RP, and Data mode. |
| `/gg progression currency give <targets> <amount>` | Grant the active currency. |
| `/gg progression points give <targets> <amount>` | Grant saved banked RP, including dormant RP in physical mode. |
| `/gg progression criteria inspect <player> <criterion>` | Inspect one custom Progression Gate counter. |
| `/gg progression criteria grant <targets> <criterion> [value]` | Raise a custom criterion to a minimum value. |
| `/gg progression criteria increment <targets> <criterion> <amount>` | Add progress without exceeding the saved limit. |
| `/gg progression criteria reset <targets> <criterion>` | Clear one custom criterion without changing other progression. |
| `/gg research status` | Audit the active research profile and live catalog. |
| `/gg research inspect <blueprint_id>` | Explain one blueprint's research and crafting assignments. |
| `/gg research setup assess` | Recommend a discovery preset for the current server. |
| `/gg research setup preview <preset>` | Preview a preset without changing server state. |
| `/gg research setup apply <preset> confirm` | Apply and synchronize a confirmed preset. |
| `/gg research export` | Export the complete research/crafting catalog and economy review. |

Use vanilla `/reload` after changing datapacks. A rejected reload leaves the
last working data active.

## Documentation

- [Currency configuration and mode choices](docs/research-currency-configuration.md)
- [Research currency migration](docs/research-currency-migration.md)
- [Research Data authoring and behavior](docs/research-data-policies.md)
- [Research economy supply and balance](docs/research-economy-balance.md)
- [Research currency integration API](docs/research-currency-integrations.md)
- [Economy release handoff and playtest checklist](docs/research-economy-release-handoff.md)
- [Current release notes](docs/releases/1.3.1.md)
- [CurseForge description](docs/curseforge-description.md)
- [Operations and migration](docs/operations-and-migration.md)
- [Research Designer workspace](docs/research-designer-workspace.md)
- [Share and install custom Tech Trees](docs/research-designer-export.md)
- [Research Designer help](docs/research-designer-troubleshooting.md)
- [Research-tree authoring](docs/research-tree-authoring.md)
- [Research Bench and crafting Workbench tiers](docs/research-bench-tiers-and-crafting.md)
- [Progression Gate criterion API](docs/progression-gate-api.md)
- [Research costs and found-weapon recovery](docs/research-cost-and-found-weapon-recovery.md)
- [Shortest-path purchases](docs/research-path-purchases.md)
- [Capability scoring](docs/research-capability-scoring-v3.md)
- [Dynamic tree width](docs/research-tree-dynamic-width.md)
- [Guided progression](docs/research-tree-guided-progression.md)
- [Research-tree manual QA](docs/research-tree-manual-qa.md)
- [Release checklist](docs/release-checklist.md)
- [Release validation reference](docs/release-validation.md)
- [Example Research Tree datapack](examples/research-tree-datapack/README.md)

## Building

Use JDK 17 from the repository root:

```text
./gradlew cleanTest build
./gradlew certifyReleaseCandidate
```

The release artifact is written to
`build/libs/taczweaponblueprints-<version>.jar`.

Optional structure-aware data generation must be requested explicitly:

```text
./gradlew runData -PincludeStructureDataMods=true
```

Run data generation in a clean or isolated working tree and review generated
resource changes before accepting them.

Captured client or server startup logs can be checked with:

```text
./gradlew verifyRuntimeSmokeLog -PsmokeKind=client -PsmokeLog=run/logs/client.log.gz
./gradlew verifyRuntimeSmokeLog -PsmokeKind=server -PsmokeLog=run/logs/server.log
```

The complete validation and publishing procedure is in the
[release checklist](docs/release-checklist.md).

## License

All Rights Reserved. See [LICENSE.txt](LICENSE.txt).

See the [settings guide](docs/settings-guide.md) for control groups, input units,
and the difference between ordinary and Analyzer fragments.
