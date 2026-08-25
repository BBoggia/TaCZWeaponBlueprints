# Changelog

## Unreleased

## 1.2.0 - 2026-08-25

### Added

- Synchronized each disclosure-safe research graph together with its matching
  branch titles, kinds, icons, ranks, sibling order, and complete membership.
- Added lazy client-side Branches and All Weapons projections, disclosure-safe
  cross-group link metadata, and deterministic branch navigation.
- Added a bounded fullscreen Weapon Trees sidebar, clickable cross-branch
  requirement/unlock portals, pinned contextual details, and independent
  camera restoration for All Weapons and every branch.
- Added adversarial coverage for 4,096-node mixed-disclosure publications,
  maximum fallback/grouped atlases, content-pack fallback churn, profile group
  reloads, and disconnect cleanup.

### Changed

- Advanced the network protocol to `15`; research graph and presentation chunks
  now validate and publish atomically under one generation, and Research Bench
  previews contain only the active inventory-backed workflow.
- Made Branches the default Research Bench view and added global search-aware
  view/group controls; selecting a group filters Branches but only focuses its
  region in All Weapons.
- Reoriented progression bottom-to-top and replaced category lanes with
  authored, horizontally clustered group regions in All Weapons.
- Rebalanced the complete 54-weapon TaCZ 1.1.8 default progression into seven
  independent role-aware branches, with weaker entries at the bottom and
  stronger weapons at the top.
- Hardened release certification to require JDK 17 and verify the packaged
  grouped-tree runtime classes, 32 default rules, and seven presentation groups.
- Updated the packaged mod description to explain the Research Bench,
  inventory-backed research, recycling, and datapack customization.

### Fixed

- Stopped hidden prerequisites from contributing published counts, anonymous
  anchors, layout tiers, or tooltips; public counts now exactly match public
  prerequisite edges.
- Normalized authored, fallback, and Undisclosed ranks against the complete
  public graph and reject publications whose ranks contradict an edge.
- Bounded research-definition JSON before parsing, bounded group rank/member
  decoding while streaming the list, and reject synchronization totals as soon
  as accumulated chunks exceed their declaration.
- Prevented stale completed sync halves from regressing the client publication,
  and made point-only updates reuse a newer completed pending tree without
  discarding future generations.
- Rejected stale, conflicting, completed-duplicate, and cumulatively oversized
  player-progression chunks, and cleared partial progression state on logout.
- Cleared cached per-player tree publications when a server stops, preventing
  integrated-server sessions from retaining obsolete server-owned state.
- Removed the retired Prepare/Fill actions, menu mode, hidden ingredient/result
  slots, preview fields, and fill planner; research now has one inventory-backed
  transaction path from UI through server authority.

### Compatibility

- Existing learned and discovered blueprint IDs, Research Points, datapack
  formats, and loot configuration remain compatible without a world migration.
- Protocol `15` requires matching `1.2.0` clients and servers for the new
  disclosure-safe grouped-tree publication.
- Development and release verification remain pinned to TaCZ `1.1.8-hotfix`.

## 1.1.0 - 2026-08-25

### Added

- Blueprint Journal discovery, completion, filtering, and disclosure-aware policy presentation.
- Per-player Research Points with synchronized caps and durable persistence.
- Datapack-driven research profiles and deterministic per-blueprint rules.
- A non-ticking Research Bench for atomic physical-blueprint research and manual duplicate recycling.
- Overlap-safe point, explicit-item, and item-tag ingredient transactions.
- Operator progression inspection and explicit learned, discovered, points, or complete resets.

### Changed

- Replaced the original list-style Research Bench browser with a pannable,
  zoomable, searchable, keyboard-navigable research tree with responsive
  translucent fullscreen overlay, contextual node details, category focus,
  first-visit guidance, automatic inventory-backed research, and recycling.
- Advanced the network protocol to `13` for bounded Research Bench actions,
  atomic tree publication, per-player node state, and exact open-menu previews.
- Made all five undiscovered-visibility ceilings distinct in the Journal and
  Research Bench: hidden, anonymous silhouette, name-only, limited preview,
  and complete policy detail.
- Changed the packaged exact-tree rules to request full disclosure so the
  server visibility ceiling can select any of the five presentation tiers.
- Added a repeatable runtime-log gate and machine-readable release-candidate
  report containing dependency versions, test totals, artifact size, and SHA-256.

### Fixed

- Removed the manual Prepare/Fill step, made research consume exact materials
  directly from the player's inventory, and made Fit show the complete tree.
- Forced a complete tree publication when recycling also migrates legacy unlocks.
- Limited root, leaf, component, and independent-node diagnostics to the visible
  graph and delayed audit logging until the TaCZ catalog is initialized.
- Rejected stale or conflicting Journal chunks with the same fail-closed rules
  used by research-tree synchronization.
- Spatially indexed prerequisite edges so large authored graphs do not scan every
  edge on every rendered frame.
- Replaced real IDs on silhouette and name-only tree nodes with validated opaque
  publication keys and blocked server selection below preview visibility.
- Prevented release certification from accepting an older changelog version or
  nonempty Unreleased section.

### Compatibility

- Existing learned recipes migrate to durable learned/discovered blueprint output IDs.
- Research and recycling remain opt-in policy surfaces and never delete progression when disabled.

## 1.0.4 - 2026-08-24

### Changed

- Updated the development and runtime compatibility target to TaCZ 1.1.8-hotfix (`[1.1.8,1.2)`).
- Switched the pinned TaCZ development artifact from CurseMaven to the official Modrinth Maven endpoint.
- Let TaCZ own gunsmith result-button registration and ingredient-count null handling instead of replacing those paths with redundant client mixin injections.
- Resolved lazily created gun displays once per blueprint render while retaining the synchronized catalog texture fallback.

### Compatibility

- TaCZ 1.1.8-hotfix compiles successfully and passes the complete automated test suite.
- Dedicated-server and client startup probes load the blueprint menu and screen mixins without an injection error.
- Existing learned blueprint data, datapack formats, loot policies, and network formats are unchanged.
- Promotes the complete `1.0.3-beta7` feature and hardening set below to the stable release channel.

## 1.0.3-beta7 - 2026-08-24

### Added

- Authoritative TaCZ 1.1.5 blueprint catalog discovery for guns, ammunition, and attachments.
- Persistent, validated, server-authoritative learned-recipe progression.
- Server-side TaCZ crafting enforcement and bounded deterministic synchronization.
- Durable blueprint-output unlock identities with automatic duplicate-recipe alias migration.
- Byte-budgeted atomic synchronization chunks below Minecraft's custom-payload ceiling.
- Live configuration-aware loot selection and all three blacklist categories.
- Versioned, reloadable blueprint tags, loot pools, and loot rules.
- Pool inheritance, catalog selectors, loot-table selectors, and dimension/luck predicates.
- Atomic last-known-good catalog and loot snapshot publication.
- Operator status, inspection, pool, and analytical preview commands.
- Exact effective weights, per-roll probabilities, and expected-addition reporting.
- Automated tests and packaged-release verification.

### Changed

- Replaced TaCZ recipe-ID path guessing with result-item API resolution.
- Replaced global client/server catalog state with isolated authoritative and presentation catalogs.
- Replaced reflection-driven loot resource discovery with strict deterministic loading.
- Made generated legacy loot modifiers a table-selective compatibility fallback behind dynamic rules.
- Limited blueprint additions to one shared 64-item budget per loot event.
- Tightened declared compatibility to TaCZ `[1.1.5,1.2)` and Fzzy Config `[0.5.9,0.6)`.
- Tightened Minecraft to `[1.20.1,1.20.2)` and Forge/FML to `[47,48)`.
- Made archive ordering and timestamps reproducible.

### Fixed

- Content-pack namespaces being incorrectly treated as Forge mod IDs.
- Blueprint consumption on duplicate or invalid unlocks.
- Lost unlocks during player cloning, including return from the End.
- Client catalog synchronization overwriting integrated-server authority.
- Stale learned IDs exhausting or polluting active synchronization.
- Malformed optional datapack fields silently applying defaults.
- Partial reload publication, inheritance cycles, unsafe weights, and unbounded definitions.
- Multiple overlapping modifiers independently exceeding the per-event blueprint limit.
- Optional structure dependencies breaking the normal dedicated-server development runtime.
- Canonical duplicate recipes invalidating previously learned aliases.
- Maximum-count synchronization payloads exceeding Minecraft's byte limit.
- Selector inheritance underflow escaping reload validation.
- Removed content-pack blueprints rendering invisibly and spamming client logs.
- Open gun-smithing screens retaining stale unlock/configuration state.
- Translation overrides being interpreted as unsafe Java format strings.
- Targeted disabled loot rules being reported as a global datapack opt-out.
- Removed 69 orphaned legacy modifier files that were not referenced by the
  global loot-modifier index and therefore could never execute.

### Compatibility

- Existing valid player `Recipes` NBT remains readable and is migrated into durable `Blueprints` state.
- Format-1 loot pools and rules remain supported.
- The 485 generated legacy modifiers remain packaged for incremental migration and rollback.
- Existing six-tier weights and authored table overlaps are unchanged.
