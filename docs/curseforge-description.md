# [TaCZ] Weapon Research & Blueprints

Turn the Timeless and Classics Zero (TaCZ) arsenal into a lasting progression system
built around exploration, blueprints, and research.

Blueprints can appear in configurable world loot. Using one permanently
unlocks the matching TaCZ gun-smithing recipe for that player, and the unlock
remains through death, dimension changes, logout, and server restarts. The
system is server-authoritative, so locked recipes cannot be bypassed through a
client recipe screen. Collect Scrap from exploration and player kills to buy
research, and bring Research Data to a Blueprint Analyzer for weapon fragments
or a blueprint.

## Features

- **Lootable Blueprints** — Find blueprints while exploring and permanently
  learn the matching TaCZ recipes.<br><br>
- **Research Bench Tech Tree** — Browse the available weapon collection in one
  edge-to-edge Tech Tree with search, pan, zoom, keyboard navigation, and
  an interactive minimap for navigating large weapon collections.<br><br>
- **Dedicated Research and Crafting Stations** — Progress through three
  Research Bench tiers for the Tech Tree and three Workbench levels for TaCZ
  weapon crafting. Every tier has its own direct crafting recipe.<br><br>
- **Blueprint Fragments** — Find weapon-specific fragments through the normal
  blueprint loot system and archive them in the Blueprint Analyzer. A completed
  set can reduce that weapon's research cost or reconstruct a protected
  blueprint, depending on the server preset.<br><br>
- **Research Planning Tools** — Pin a weapon as your current goal, highlight
  its complete research route, and track the currency and materials you still need.
  The Affordable Now filter can narrow the tree to research you can purchase
  with your current resources.<br><br>
- **Automatic Weapon Placement** — The built-in tree organizes TaCZ weapons by
  strength, handling, range, and play style. Its width, layers, and branches
  adapt as compatible add-on guns enter or leave the server's weapon catalog.<br><br>
- **Flexible Shortest-Path Research** — Select a higher locked weapon to
  preview and unlock the shortest valid route to it. Servers can require
  physical Scrap, banked Research Points (RP), inventory materials, or combined
  costs. The purchase is atomic, so a failed or changed route consumes no
  partial cost.<br><br>
- **Blueprint Analyzer** — Reverse engineer supported TaCZ equipment and recycle
  learned duplicate blueprints. Analyze Research Notes for Tier 1 weapon
  fragments, Reports for Tier 1–2 rewards, and Dossiers for Tier 1–3 rewards.
  Reports and Dossiers can produce a full blueprint. Advanced and Experimental
  Analyzers improve fragment quantities and blueprint chances.
  Servers can protect crafted guns while allowing guns found in loot to become
  a blueprint, convert directly into research currency, or offer the player both choices.<br><br>
- **Blueprint Journal** — Track discoveries, learned recipes, recent unlocks,
  completion, and the active server rules. A reusable Getting Started page
  explains the main progression loop and available ways to earn research currency.<br><br>
- **JEI and EMI Information** — Optional integrations explain Research Benches,
  crafting Workbenches, the Blueprint Analyzer, blueprints, and Research Data
  without revealing hidden research targets or bypassing unlocks.

Weapons are the only Tech Tree category enabled by default. Built-in research
data for TaCZ ammo and attachments remains available for pack authors
who want to enable and customize those categories.

## Getting Started

1. Install the mod and its required dependencies on both the client and server.
2. Explore configured loot locations to find blueprints, Scrap, and Research
   Data. Scrap can be shared or traded and follows normal inventory death drops.
3. Use a blueprint to learn its recipe, then open the Blueprint Journal to
   review your progress and ways to earn research currency.
4. Craft a Research Bench to browse the Tech Tree, then build the appropriate
   Workbench level to craft learned weapons. Build higher tiers as your arsenal
   advances.<br><br>
5. Process Research Data at the Blueprint Analyzer, then archive matching
   analysis fragments to reconstruct a blueprint. You can also reverse engineer
   equipment or recycle supported duplicates. Closing the Analyzer returns its
   contents to your inventory or drops them if no space remains.<br><br>

## Servers and Modpacks

Server configuration controls blueprint loot, pacing, blacklists, physical
Scrap or banked RP, research and crafting tiers, Blueprint Fragment behavior,
found-weapon recovery, recycling, discovery visibility, starting blueprints,
and progression exemptions. Research Data can use random analysis, fixed
currency redemption, or be disabled. Learned weapons remain possible Data
rewards by default; servers can exclude them and stop processing when no
weapons remain in the pool. Scrap loot, Data loot, and gameplay currency awards
have independent switches. Settings are organized into clear sections, while advanced pack controls remain available
without crowding the common options. Players can separately choose a Compact,
Balanced, Spacious, or Custom Tech Tree layout and adjust confirmation,
notifications, the background grid, and reduced motion.

Datapacks can customize loot pools, research costs, ingredients, entry points,
visibility, prerequisites, grouped routes, research-currency awards, and Tech
Tree presentation. Data pools, weapon weights, fragment quantities, blueprint
chances, and reconstruction thresholds are configurable. Weapon analysis
varies outcomes by weapon and Analyzer level by default, including configurable
no-reward chances for above-tier weapons. Datapacks can
assign workstation tiers, Blueprint Fragment rules, and Progression Gates based on advancements, Bench tiers, or custom
server milestones. Pack authors can use the automatic weapon tree or provide
an authored-only tree containing only their selected weapons. If a reload
contains invalid data, the server keeps the last working settings.

The mod supports TaCZ content packs without requiring a separate blueprint
definition for every added gun. The available blueprint catalog is rebuilt
from the recipes currently available on the server.

## Requirements

- Minecraft 1.20.1
- Forge 47.x, validated with 47.3.0
- Timeless and Classics Zero 1.1.8-hotfix (`1.1.8` up to, but not including,
  `1.2`)
- Fzzy Config 0.7.6 for Forge (`0.7.6` up to, but not including, `0.8`)
- Kotlin for Forge 4.11.x, required by Fzzy Config

JEI 15.x and EMI 1.1.x are optional client-side integrations.

## Installation and Compatibility

Install [TaCZ] Weapon Research & Blueprints and every required dependency on
both the client and server. Clients and servers must use matching [TaCZ] Weapon
Research & Blueprints versions.

Upgrades preserve learned blueprints, discoveries, fragment archives, and saved
RP. Settings files missing the new economy choices use Scrap and Data analysis.
Server operators can explicitly keep banked RP and fixed Data redemption or
preview a one-way withdrawal of saved RP into the selected physical item.
Changing modes never converts saved RP automatically. See the
[currency migration guide](https://github.com/BBoggia/TaCZWeaponBlueprints/blob/main/docs/research-currency-migration.md) before updating an
established server.

## Support

When reporting a problem, include the Minecraft, Forge, TaCZ, Fzzy Config,
Kotlin for Forge, and [TaCZ] Weapon Research & Blueprints versions, along with the relevant
`latest.log` and the names of installed TaCZ content packs.
