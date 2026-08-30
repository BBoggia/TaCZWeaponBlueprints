# TaCZ Weapon Blueprints

Turn TaCZ's gun collection into a persistent progression system built around
exploration, blueprints, and research.

Blueprints can appear in configured world loot. Using one permanently teaches
that player the matching TaCZ gun-smithing recipe, and the unlock remains across
death, dimension changes, logout, and server restarts. Crafting is enforced by
the server rather than trusting the client recipe screen.

## Research Bench

The Research Bench provides a visual, Rust-inspired weapon tree for the built-in
TaCZ 1.1.8 arsenal. It opens directly into an edge-to-edge tree, with search,
navigation, and selected-weapon details overlaid instead of reserving space for
an inventory or a smaller secondary view. Its default progression contains
curated pistol, SMG, shotgun, rifle, sniper, machine-gun, and launcher branches.

- Browse and search the complete visual tree.
- Pan, zoom, center, or fit the tree to the screen.
- See unlock status, prerequisites, Research Point cost, and materials.
- Research eligible weapons directly from the tree; materials are pulled
  automatically from your inventory and the recipe is learned immediately.
- Select a ready weapon and use the Research button for one deliberate,
  server-authoritative transaction with visible success or failure feedback.
- Use the dedicated Blueprint Recycler to turn learned duplicate blueprints or
  configured Research Data into Research Points.

Mouse and keyboard navigation are both supported. Locked states use text and
icons as well as color.

## Built for modpacks and servers

Research profiles, costs, material tags, visibility, recycling values, and
prerequisites are datapack-driven. Pack authors can rebalance the default tree
or add exact branches for TaCZ content packs without modifying Java code.
Unknown add-on weapons are placed into deterministic, mechanically informed
tier/level rows. Warning-bearing estimates stay auditable, and weapons without
usable runtime stats receive a conservative type-based fallback instead of
being lost or piled into one disconnected row.

Blueprint loot is also configurable through reloadable pools and rules, with
selectors for content namespaces, item categories, loot-table families,
dimensions, and luck conditions. Operator diagnostics can inspect effective
loot and research policy before players encounter it.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Timeless and Classics Zero 1.1.8-hotfix
- Fzzy Config 0.5.9 and its Kotlin for Forge dependency

Install the mod and its required dependencies on both the client and server.
TaCZ gun content packs remain normal TaCZ resources and can be managed
independently.

Existing learned blueprint data remains compatible with the visual research
tree update. Clients and servers must run matching mod versions because the
custom network protocol is versioned.
