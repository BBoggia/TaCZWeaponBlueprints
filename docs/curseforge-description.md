# TaCZ Weapon Blueprints

Turn Timeless and Classics Zero into a persistent, server-authoritative weapon
progression system built around exploration, research, and blueprints.

Blueprints can appear in configurable world loot. Learning one permanently
unlocks that player's matching TaCZ gun-smithing recipe across death, dimension
changes, logout, and server restarts. The server enforces every unlock rather
than trusting the client recipe screen.

## Research Bench and automatic Tech Tree

The Research Bench opens into an edge-to-edge weapon Tech Tree inspired by the
interconnected progression of Rust and the gradual specialization of War
Thunder.

- The complete 53-weapon TaCZ 1.1.8 arsenal is arranged from a shared foundation
  into distinct, gradually narrowing weapon families.
- Automatic placement uses weapon capabilities such as damage, sustained fire,
  precision, range, area control, handling, and versatility.
- Compatible add-on guns are scored and incorporated automatically in an
  automatic tree. Pack authors may instead select an authored-only tree, where
  only explicitly placed weapons appear and unspecified weapons are omitted.
- Grouped routes support meaningful "one of these paths" requirements without
  turning every merge into a mandatory grind.
- Dynamic row width, branch-aware spacing, responsive wrapping, search, pan,
  Fit, keyboard navigation, and extended zoom-out keep large modpack trees
  readable.
- Select a higher locked weapon to preview and purchase the deterministic
  shortest valid path. Every newly unlocked prerequisite is charged once and
  committed in one atomic transaction.
- Track a revealed weapon as a session goal and keep its remaining route, RP,
  and material requirements highlighted.

Weapons are the only Tech Tree domain enabled by default. The built-in data for
TaCZ ammunition and attachments remains available for pack authors who want to
enable and customize those domains.

## Research Points, costs, and the Blueprint Analyzer

Research can require Research Points, inventory materials, or both. Servers can
choose between `POINTS_AND_ITEMS`, `POINTS_ONLY`, and `ITEMS_ONLY` without
rewriting datapack costs.

The Blueprint Analyzer provides a deliberate workstation for:

- reverse engineering physical TaCZ weapons;
- recycling eligible duplicate blueprints into Research Points;
- redeeming configured Research Data;
- distinguishing verified loot weapons from crafted or unverified legacy guns;
- optionally turning a found weapon into a recyclable blueprint, converting it
  directly into RP, or letting the player choose.

Crafted weapons remain protected from recovery exploits. Every preview and
transaction is resolved by the server, checks current inventory and RP state,
and either commits completely or consumes nothing.

## Built for modpacks and servers

Loot pools, research profiles, costs, ingredient tags, visibility, recycling,
entry points, prerequisites, grouped routes, presentation, and tree authority
are datapack-driven. Pack authors can use one fully generated automatic weapon
tree or replace it with an authored-only selection, add custom content-pack
branches, or remove tiers without modifying the mod.

Synchronized configuration includes:

- blueprint loot chance, roll limits, pacing presets, and blacklists;
- Research Point cap and research cost mode;
- found-weapon recovery and duplicate-recycling policy;
- discovery visibility and Creative cost bypass;
- starting blueprints and progression exemptions;
- separate client-side tree spacing, wrapping, crossing, compaction, grid, and
  reduced-motion controls.

The Blueprint Journal provides discovery and completion tracking, policy-aware
details, and a reusable Getting Started guide. Optional JEI and EMI integrations
add generic information pages without exposing hidden research targets or
allowing recipe-transfer bypasses.

## Requirements

- Minecraft 1.20.1
- Forge 47.x, validated with 47.3.0
- Timeless and Classics Zero 1.1.8-hotfix (`1.1.8` up to, but not including,
  `1.2`)
- Fzzy Config 0.5.9
- Kotlin for Forge 4.11.x, required by Fzzy Config

JEI 15.x and EMI 1.1.x are optional client-side integrations.

Install TaCZ Weapon Blueprints and its required dependencies on both the client
and server. Version 1.3.0 uses network protocol 40, so clients and servers must
run matching mod versions. Existing learned blueprints, discoveries, Research
Points, loot configuration, and supported datapacks remain compatible; no world
migration is required.
