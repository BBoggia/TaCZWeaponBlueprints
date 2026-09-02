# Changelog

This changelog records player-, operator-, pack-author-, and API-visible
changes. Detailed implementation and validation evidence belongs in technical
documentation.

## Unreleased

## 1.2.0 - 2026-09-02

### Added

- Added research target pinning with route highlighting, material progress,
  and a direct return to the tracked goal.
- Added an Affordable Now filter that checks the complete research cost without
  blocking normal Research Bench actions.
- Added a recent unlock history and a fullscreen Tech Tree minimap with click
  and drag navigation.

### Fixed

- Prevented rejected or expired server route guidance from leaving an
  estimated path highlighted as though it were authoritative.
- Kept Affordable Now checks responsive under shared-server load by
  acknowledging queued work and renewing its client request while it waits.
- Prevented the minimap from covering persistent controls on compact screens.

### Compatibility

- Updated the network protocol to 47. Clients and servers must update together.

## 1.1.1 - 2026-09-01

### Added

- Added Compact, Balanced, Spacious, and Custom client layout presets for the
  Tech Tree, plus a one-click appearance reset.

### Changed

- Reorganized Server Settings into approachable sections for general
  progression, discovery and loot, research and RP, the Blueprint Analyzer,
  starting access, and advanced pack controls.
- Reorganized Personal Settings into interaction, appearance, and collapsed
  advanced-layout sections.
- Made Compact, Balanced, and Spacious produce distinct Tech Tree row spacing,
  and bounded every preset or Custom layout to the supported canvas instead of
  allowing an oversized visual setting to hide the tree.
- Added a Balanced screen-local fallback when a requested visual layout cannot
  be prepared.
- Reduced the default RP balance cap to 10,000 and the settings slider maximum
  to 100,000. Existing stored RP is not deleted when the configured cap falls.
- Retired six legacy layout controls that no longer affect the visible Tech
  Tree while continuing to read older client configuration files.
- Made dependent settings visibly inactive when their parent feature or preset
  is unavailable, with an explanation of how to enable them.
- Made Starting Blueprints, Blueprint-Free Item IDs, and gun, ammo, and
  attachment loot blacklists searchable by loaded item names or resource IDs.
  Stored IDs from temporarily unavailable content packs remain valid.
- Made Balanced the default discovery and loot preset for new installations.
  Existing servers retain their prior custom visibility and loot values.
- Updated the required Fzzy Config version to 0.7.6.

### Compatibility

- Existing server discovery values and version-zero client layout values
  migrate to Custom so upgrades preserve their current behavior. Retired
  legacy layout keys remain readable during migration and are omitted from new
  saves. Existing configured RP caps above 100,000 are bounded to 100,000;
  stored player balances remain intact.

## 1.1 - 2026-09-01

### Added

- Added the Blueprint Journal for tracking discovered and learned blueprints,
  completion, active server rules, and ways to earn Research Points (RP).
- Added the Research Bench and its searchable, pannable, zoomable Tech Tree.
  Weapons are organized automatically by strength, handling, range, and play
  style, including compatible guns from TaCZ content packs.
- Added shortest-path research. Selecting a higher weapon can unlock the
  complete valid route to it in one atomic transaction, with shared
  prerequisites charged only once.
- Added grouped requirements so a research node can accept one valid route
  from a set of alternatives while retaining separate mandatory requirements.
- Added configurable research costs that can require RP, inventory items, or
  both.
- Added the Blueprint Analyzer for reverse engineering TaCZ equipment,
  recycling learned duplicate blueprints, and redeeming Research Data for RP.
- Added crafted and loot-origin tracking for TaCZ guns. Servers can let
  verified found weapons produce a protected blueprint, a recyclable
  blueprint, direct RP, or a player-selected result.
- Added configurable RP awards for exploration, combat, blueprint learning,
  recycling, Research Data, and other progression events.
- Added JEI and EMI information pages for the Blueprint Journal, Research
  Bench, Blueprint Analyzer, blueprints, and Research Data.
- Added operator tools for inspecting progression, resetting selected player
  state, managing RP, auditing research rules, previewing server presets, and
  exporting pack-author data.

### Changed

- Made automatic and authored-only weapon trees explicit choices. Automatic
  trees place every available weapon; authored-only trees show only weapons
  selected by the pack author.
- Enabled weapon research by default while leaving ammo and attachment
  research disabled for pack authors to opt into.
- Improved automatic weapon scoring for launchers, machine guns, shotguns,
  burst weapons, heat-based guns, falloff, armor bypass, and other TaCZ weapon
  behavior.
- Made Tech Tree width, layer count, branch tapering, and branch spacing adapt
  to the loaded weapon catalog. Large trees can spread across wider rows and
  zoom out to 15%.
- Required research above an out-of-order unlock to retain a complete route
  back to a valid Tech Tree root. Already learned support nodes remain usable
  without being charged or learned again.
### Fixed

- Fixed high-damage launchers and machine guns appearing near the beginning of
  automatically generated trees while weaker sidearms appeared much later.
- Fixed generated prerequisites skipping many ranks, leaving disconnected
  weapons, or creating routes that did not lead back to a valid root.
- Fixed large tree layouts becoming too narrow, too tall, top-heavy, offset to
  one side, or visually collapsing separate weapon branches into one mass.
- Fixed stale or changed research routes consuming resources. The Research
  Bench now commits only the exact current server preview the player approved.
- Fixed repeated or rapidly changing Research Bench requests being able to
  slow other server activity. Players now receive a clear retry message when
  requests arrive too quickly.
- Fixed progression resets leaving an invalid Research Bench selection active
  and causing broken menus or stalled world saving.
- Fixed out-of-order blueprint learning letting later research bypass required
  earlier unlocks.
- Fixed duplicate blueprints created from verified found weapons being blocked
  from configured recycling.
- Fixed invalid automatic-tree reloads showing partial or outdated trees.
  Research now stays unavailable until the complete current tree is ready.

### Removed

- Removed the legacy `/gg clearRecipes` command.
- Hid the redundant Branches and All Weapons Research Bench views.

### Compatibility

- Requires Minecraft 1.20.1, Forge 47.x, TaCZ 1.1.8-hotfix, Fzzy Config 0.5.9,
  and Kotlin for Forge 4.11.x.
- Matching [TaCZ] Weapon Research & Blueprints 1.1 clients and servers are
  required.
- Existing learned blueprints, discoveries, RP, worlds, loot settings, and
  supported datapacks require no migration.

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
