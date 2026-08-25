# TaCZ Weapon Blueprints

Turn TaCZ's gun collection into a persistent progression system built around
exploration, blueprints, and research.

Blueprints can appear in configured world loot. Using one permanently teaches
that player the matching TaCZ gun-smithing recipe, and the unlock remains across
death, dimension changes, logout, and server restarts. Crafting is enforced by
the server rather than trusting the client recipe screen.

## Research Bench

The Research Bench provides a visual, Rust-inspired weapon tree for the built-in
TaCZ 1.1.8 arsenal. Its default progression contains curated pistol, SMG,
shotgun, rifle, sniper, machine-gun, and launcher branches.

- Browse and search the complete visual tree.
- Pan, zoom, center, or fit the tree to the screen.
- See unlock status, prerequisites, Research Point cost, and materials.
- Research eligible weapons directly from the tree; materials are pulled
  automatically from your inventory.
- Use the Research button or double-click a ready weapon for the same atomic,
  server-authoritative transaction.
- Recycle learned duplicate blueprints for Research Points.

Mouse and keyboard navigation are both supported. Locked states use text and
icons as well as color.

## Built for modpacks and servers

Research profiles, costs, material tags, visibility, recycling values, and
prerequisites are datapack-driven. Pack authors can rebalance the default tree
or add exact branches for TaCZ content packs without modifying Java code.
Unknown add-on weapons remain accessible through independent research instead
of being assigned arbitrary prerequisites.

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
