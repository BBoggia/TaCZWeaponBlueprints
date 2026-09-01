# TaCZ Weapon Blueprints 1.3.0

Version 1.3.0 is the largest progression update yet. It replaces the fixed
default weapon layout with a capability-scored automatic Tech Tree, adds
grouped research routes and shortest-path purchasing, expands Research Point
and reverse-engineering options, and substantially hardens large-tree layout
and server authority.

## Highlights

- Migrated 49 of the 53 default TaCZ weapons to the `capability_v3` automatic
  placement system. Glock 17 remains the preferred shared entry point, while
  M320, RPG-7, and Minigun retain reviewed high-tier exceptions.
- Added dynamic tree width and layers, a dense interconnected foundation,
  gradually separating weapon-family branches, bounded terminal cohorts,
  branch-aware spacing, responsive wrapping, and zoom-out to 15%.
- Simplified the Research Bench to one authoritative Tech Tree view. The older
  Branches and All Weapons projections are hidden to avoid redundant view
  switching and a confusingly inconsistent picture of progression.
- Added inclusive grouped routes. A node may require one valid alternative from
  a route group while still supporting separate mandatory gateways.
- Added atomic shortest-path research. Selecting a higher node previews and
  unlocks the fewest-new-node valid route, charging shared prerequisites only
  once and rolling back everything if any part of the transaction fails.
- Added configurable research cost modes: Research Points and items, RP only,
  or items only.
- Expanded the Blueprint Analyzer with physical-weapon reverse engineering,
  duplicate recycling, Research Data redemption, and configurable recovery for
  verified found weapons.
- Added crafted-versus-looted weapon provenance. Crafted and unknown legacy
  weapons remain protected by default, while verified loot weapons may be
  converted to a recyclable blueprint, direct RP, or a player-selected result.
- Added clearer RP earning guidance, disclosure-safe Journal onboarding,
  progression goals, operator diagnostics, economy audits, and deterministic
  authoring exports.
- Kept ammunition and attachment Tech Tree research disabled by default while
  preserving their opt-in datapack definitions.

## Reliability and fixes

- Fixed emergency exact fallback entries incorrectly masking authored tag or
  selector placements from datapacks.
- Fixed a missing preferred weapon root producing an extra free foundation
  route; the selected fallback now becomes the single effective entry point.
- Fixed excessive gaps, left-side drift, top-heavy packing, branch collapse,
  junction overlap, and unstable compaction across small, large, and maximum
  supported trees.
- Made automatic placement publication revision-coupled and failure-atomic.
  Failed evidence, planning, finalization, or publication shows an explicit
  unavailable Tech Tree and server error instead of exposing a partial tree or
  silently redirecting players to a legacy view.
- Hardened grouped-route disclosure, path planning, packet limits, inventory
  transactions, recipe enforcement, recycling, and recovery against stale or
  conflicting state.

## Compatibility

- Minecraft 1.20.1 and Forge 47.x
- Timeless and Classics Zero 1.1.8-hotfix (`[1.1.8,1.2)`)
- Fzzy Config 0.5.9 and Kotlin for Forge 4.11.x
- Optional JEI 15.x and EMI 1.1.x integrations
- Network protocol 40; matching 1.3.0 clients and servers are required
- Existing learned blueprints, discoveries, Research Points, worlds, loot
  configuration, and supported datapacks require no migration

The release artifact passed the complete automated test, topology, recovery,
grouped-route, layout, and packaged-resource verification suites.
