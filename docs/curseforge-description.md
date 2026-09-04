# [TaCZ] Weapon Research & Blueprints

Turn the Timeless and Classics Zero arsenal into a lasting progression system
built around exploration, blueprints, and research.

Blueprints can appear in configurable world loot. Using one permanently
unlocks the matching TaCZ gun-smithing recipe for that player, and the unlock
remains through death, dimension changes, logout, and server restarts. The
system is server-authoritative, so locked recipes cannot be bypassed through a
client recipe screen.

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
  its complete research route, and track the RP and materials you still need.
  The Affordable Now filter can narrow the tree to research you can purchase
  with your current resources.<br><br>
- **Automatic Weapon Placement** — The built-in tree organizes TaCZ weapons by
  strength, handling, range, and play style. Its width, layers, and branches
  adapt as compatible add-on guns enter or leave the server's weapon catalog.<br><br>
- **Flexible Shortest-Path Research** — Select a higher locked weapon to
  preview and unlock the shortest valid route to it. Servers can require
  Research Points (RP), inventory materials, or both. The purchase is atomic,
  so a failed or changed route consumes no partial cost.<br><br>
- **Blueprint Analyzer** — Reverse engineer supported TaCZ equipment, recycle
  learned duplicate blueprints, and redeem configured Research Data for RP.
  Servers can protect crafted guns while allowing guns found in loot to become
  a blueprint, convert directly into RP, or offer the player both choices.<br><br>
- **Blueprint Journal** — Track discoveries, learned recipes, recent unlocks,
  completion, and the active server rules. A reusable Getting Started page
  explains the main progression loop and available ways to earn RP.<br><br>
- **JEI and EMI Information** — Optional integrations explain Research Benches,
  crafting Workbenches, the Blueprint Analyzer, blueprints, and Research Data
  without revealing hidden research targets or bypassing unlocks.

Weapons are the only Tech Tree category enabled by default. Built-in research
data for TaCZ ammo and attachments remains available for pack authors
who want to enable and customize those categories.

## Getting Started

1. Install the mod and its required dependencies on both the client and server.
2. Explore configured loot locations to find your first blueprint or Research
   Data.
3. Use a blueprint to learn its recipe, then open the Blueprint Journal to
   review your progress and ways to earn RP.
4. Craft a Research Bench to browse the Tech Tree, then build the appropriate
   Workbench level to craft learned weapons. Build higher tiers as your arsenal
   advances.<br><br>
5. Use the Blueprint Analyzer to archive Blueprint Fragments, reverse engineer
   equipment, or recycle supported items.<br><br>

## Servers and Modpacks

Server configuration controls blueprint loot, pacing, blacklists, Research
Point limits, research and crafting tiers, Blueprint Fragment behavior,
found-weapon recovery, recycling, discovery visibility, starting blueprints,
and progression exemptions. Settings are
organized into clear sections, while advanced pack controls remain available
without crowding the common options. Players can separately choose a Compact,
Balanced, Spacious, or Custom Tech Tree layout and adjust confirmation,
notifications, the background grid, and reduced motion.

Datapacks can customize loot pools, research costs, ingredients, entry points,
visibility, prerequisites, grouped routes, Research Point awards, and Tech Tree
presentation. They can also assign workstation tiers, Blueprint Fragment
rules, and Progression Gates based on advancements, Bench tiers, or custom
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
- Fzzy Config 0.7.6
- Kotlin for Forge 4.11.x, required by Fzzy Config

JEI 15.x and EMI 1.1.x are optional client-side integrations.

## Installation and Compatibility

Install [TaCZ] Weapon Research & Blueprints and every required dependency on
both the client and server. Clients and servers must use matching [TaCZ] Weapon
Research & Blueprints versions.

Existing learned blueprints, discoveries, Research Points, worlds, loot
configuration, and supported datapacks remain compatible with the current
release. No world migration is required.

## Support

When reporting a problem, include the Minecraft, Forge, TaCZ, Fzzy Config,
Kotlin for Forge, and [TaCZ] Weapon Research & Blueprints versions, along with the relevant
`latest.log` and the names of installed TaCZ content packs.
