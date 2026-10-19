# Changelog

This changelog records player-, operator-, pack-author-, and API-visible
changes. Detailed implementation and validation evidence belongs in technical
documentation.

## Unreleased

## 1.3.8 - 2026-10-19

### Added

- Ammo Research and Attachment Research settings can enable the bundled Tech
  Trees without an additional datapack. Choose Use Profile, Enabled, or Disabled
  under Server Settings → Research Progression. Use Profile preserves existing
  worlds and custom datapack behavior.
- Research Bench category tooltips explain when server settings or the active
  profile disable research and identify the relevant category setting.

### Improved

- Research Designer captures, previews, and category warnings account for the
  server's Ammo Research and Attachment Research choices. Older captures
  continue to use their profile's category rules.

### Compatibility

- Category settings refresh connected players and open workstations when saved.
  Turning a category off preserves learned blueprints. Clients and servers must
  update together to receive the new category-status messages (protocol 70).

## 1.3.7 - 2026-10-07

### Compatibility

- TaCZ now requires version 1.1.8 or newer without an upper version limit.
  Future versions are no longer blocked solely by the dependency range;
  compatibility still depends on their APIs and supported Minecraft version.

## 1.3.6 - 2026-09-18

### Added

- Added the Gunsmith and Ballistics Researcher professions, each with five
  trading levels and craftable job sites that work in existing worlds.
- Gunsmith stock includes entry weapons, standard Expert rifles, and costlier
  Master rifles, with prices based on their TaCZ recipes.
- Added distinct Gunsmith Station and Ballistics Desk models, recipe-book
  unlocks, and job-site tooltips. Gunsmiths wear charcoal workwear with copper
  accents and ear defenders; Ballistics Researchers wear blue coats and an
  inspection lens. Both outfits include zombie variants.
- Added village-trading guidance and examples for custom gun pools, physical currencies, and a strict economy without material purchases.
- Added availability and research-restriction hints to native villager trades,
  including empty-magazine family and capacity information.
- Villager trades now wait for settled reload data, preserve unchanged menus
  when another player joins, and retain unavailable addon items across saves.
  Disabled professions cannot be newly assigned; existing professions remain.
- Purchased villager weapons now retain their origin through transfers and use protected, prerequisite-bound analysis rewards. They cannot recover currency as found loot.
- Added opt-in protected blueprint trades and conservative checks for legacy Research Data exchanges. Currency changes update material payouts without replenishing stock.
- Added versioned villager trade definitions, server-side economy settings, and
  `/gg villager_trades` inspection/export commands for pack authors. Bundled
  Gunsmith and Ballistics Researcher tables support native trading. See
  [villager trading](docs/villager-trading.md).
- Added persistent Gunsmith gun/ammo choices and Ballistics Researcher study
  targets across leveling, restocking, world reloads, and curing. Gunsmiths add
  compatible basic magazines when supported TaCZ Magazines content is available.
- Saved villager offers now suspend when their content or required supplies
  become unavailable. Price changes preserve spent stock and selected items.

### Fixed

- Getting Started guidance includes villager trading and scrolls with the mouse wheel or Page Up/Page Down on small screens.
- Trade hints wrap on smaller screens and display readable magazine and ammo names. Empty custom-villager stock explains that no trades are available under the current server rules.
- Villager trades recover after an aborted datapack reload without consuming held payment or replacing saved stock, including failures from late synchronization handlers.
- Disabled trade fallbacks no longer expose their descendants through that branch.
- Ballistics Researcher stock now selects compatible shared study targets from available fallback trades, including when a preferred attachment cannot be stocked.
- Custom fallback guns now receive automatic matching magazine support.
- Villager currency settings accept the same namespaced ID lengths as research currency settings, including saved trade stock.
- Separated the portable Tech Tree's Journal button from the branch-label toggle.
- Kept rapid mouse-wheel and zoom-button input from being lost during camera animation.
- Made Page Up and Page Down select visible search results. Enter no longer opens a result hidden by scrolling.
- Added Previous/Next material pages to research details so long paths expose every requirement and its allocated inventory count.
- Fixed research-path review rejecting a matching preview when viewing later
  material pages, including paths with more than 32 material types.
- Restored native magazine crafting when blueprint progression is disabled, including catalogs beyond the restricted recipe-list limit.

### Compatibility

- Updated the network protocol to 69 for material-page previews, purchased-weapon Analyzer origins, and villager trade status. Clients and servers must update together.

### Included from 1.3.5

Players updating from 1.3.4 or earlier also receive these fixes, originally
released in 1.3.5:

- Fixed the Research Bench server crash when a player has the required materials
  but insufficient Scrap, including Scrap-only research and paths with already
  learned prerequisites.
- Fixed research previews counting the same physical items toward both currency
  and material costs. Unexpected preview failures disable the selection safely
  instead of crashing the server.
- Fixed physical-blueprint research showing saved Research Points as the
  available balance when the server requires Scrap or another physical currency.

## 1.3.5 - 2026-09-16

### Fixed

- Fixed a Research Bench server crash when selecting research with enough
  materials but insufficient Scrap, including Scrap-only costs and paths with
  learned prerequisites.
- Research previews now reserve physical currency before counting materials
  when the same item is used for both costs. Unexpected preview failures disable
  the selection safely instead of crashing the server.
- Research that creates a physical blueprint now previews the configured
  currency balance instead of treating saved Research Points as available Scrap.

## 1.3.4 - 2026-09-13

- The Blueprint Analyzer now asks players to eject inserted TaCZ Magazines
  magazines, including empty ones, before analyzing a gun or recovering its
  value. Clients and servers must update together.
- Added optional TaCZ Magazines 0.2.0-Beta crafting support at all three
  Workbench tiers and TaCZ's ammo Workbench. Empty magazines use the add-on's
  recipe costs without requiring a learned magazine blueprint; recipe changes
  refresh open Workbenches after reload.
- Added optional weapon-linked magazine access and separate minimum Workbench
  tiers for basic and extended magazines. Material-only crafting remains the
  default. Servers can keep basic magazines available for found or traded guns
  while requiring learned compatible weapons for extended variants.
- Fixed magazine recipe synchronization for players already online when discovery
  recovers during another player's login. Empty magazine lists now also point
  players toward weapon learning and Workbench requirements when progression is enabled.
- Fixed learned recipes disappearing from all three crafting Workbench tiers
  on dedicated servers. Workbench recipe filters now retain their behavior
  during multiplayer synchronization; blueprint and progression requirements
  still apply.
- Added TaCZ's ammo categories to all three crafting Workbench tiers so learned
  ammo recipes can appear alongside guns and attachments.

## 1.3.3 - 2026-09-11

These notes include the 1.3.2 changes for players updating directly from 1.3.1.
The historical 1.3.2 entry remains below.

### Tech Tree and research details

- Clicking a blueprint opens its details directly. The inspector gives more room
  to scrolling content and keeps the purchase controls visible at small GUI sizes.
- Research costs appear in one Resource / Have / Required table using the current
  server quote, including the active currency and allocated materials. Shortages
  describe the entire purchase, including required unlearned predecessors.
- Prerequisite groups explain which blueprints satisfy each requirement. Inspect
  a disclosed prerequisite with mouse or keyboard, then use Back to restore the
  previous blueprint, view, and scroll position without changing the tracked goal.
- The Path button reviews the blueprints and chosen prerequisites in the current
  purchase, even when resources are missing. Inspecting a link does not choose a
  different purchase route or enable research.
- The View menu can focus the selected blueprint or its branch. Clicking a
  visible branch title also frames that branch.
- Branch labels avoid nodes, connectors, and other labels. They keep a stable
  position while panning and zooming and hide when there is no room. The On/Off
  toggle remembers its setting across screen openings and game restarts.
- Weapon artwork fills the padded node interior when its name is hidden. Names
  use only the space their rendered lines need, retain pack colors when wrapped,
  and keep their layout when a node is selected or focused. Corner badges stay
  clear of names, with one marker per tracked goal.
- Direct requirements, earlier possible prerequisites, and reviewed purchase
  paths have distinct emphasis. Earlier paths gain a modest highlight at distant
  zoom, with capped thickness that stays lighter than immediate requirements.
- AUTO minimap visibility follows zoom and visible tree coverage, with a buffer
  against flicker. Its panel follows the tree's proportions and stays fixed
  during a drag; the tracked target remains distinct from the camera.
- First-seen weapon artwork prepares its crop in the background, reducing
  opening stalls while keeping blueprint fallbacks available during loading.
- Fresh views prefer a tracked target or an actionable starting point. Compatible
  saved views retain their camera position.

### Navigation and fixes

- Zoom keeps the point under the cursor steady during animation. Resizing keeps
  camera motion stable, and inspector size changes preserve the selected
  blueprint's screen position where bounds allow.
- Details and search results support fractional scrolling. Drag the details
  scrollbar or use Page Up/Down in focused content. Losing window focus cancels
  pending holds and drags.
- Help keeps keyboard focus on its own controls, preventing research or tree
  navigation underneath it. Help text matches the route colors and small-screen
  controls.
- Moving or scrolling after Back takes priority over a delayed preview response.
- Fixed an exception when an edited search has no matching blueprints. Editing
  a search keeps its active result visible for keyboard selection.
- Loading, empty searches, and unavailable data have contextual messages and
  recovery actions, including when no blueprint is selected.

### Included from 1.3.2

- Fixed packaged-server startup and native TaCZ crafting-screen/menu hooks that
  incorrectly used development-only Minecraft method names.
- Added Stations & Research Access settings for tiered or single Blueprint
  Analyzers, tiered or single Research Benches, and Portable Research through the
  Journal / Research key. Portable Research keeps a Journal button in the tree.
- Single Analyzers use each weapon's same-tier reward rules. A separate setting
  chooses Research Data reward strength without changing its target pools.
  Existing higher-tier stations remain usable.
- Station mode changes close open research stations and refresh bundled recipes.
  Crafting Workbench tiers remain independent. Existing configurations retain
  their progression-preset behavior until an operator selects a new mode.
- Portable sessions validate the current mode on the server and become invalid
  when access is withdrawn. Research costs and other progression requirements
  still apply in Single Bench and Portable Research modes.
- Research Designer captures and offline Analyzer comparisons include station
  choices. Older projects retain their original station rules.

### Updating

- Update every client and the server together. The current network protocol is
  65; older mod versions cannot connect to 1.3.3.
- Saved player progression is unchanged. Existing settings migrate through the
  station-access settings introduced in 1.3.2, retaining prior preset behavior.
- See the [combined 1.3.3 release notes](docs/releases/1.3.3.md) for installation,
  compatibility, and direct-upgrade guidance.

## 1.3.2 - 2026-09-08

- Fixed production startup and crafting-screen hooks targeting development-only
  Minecraft method names in TaCZ workstation mixins.

- Added Stations & Research Access settings for tiered or single Blueprint
  Analyzers, tiered or single Research Benches, and portable research through the
  Journal / Research key. Portable research keeps the Journal available from the
  tree. Research costs and non-bench progression requirements still apply.
- Single Analyzers use each weapon's same-tier reward rules. A separate setting
  chooses Research Data reward tables without changing the Data item's target pool.
  Existing higher-tier stations remain usable in single-station modes.
- Station mode changes close open research stations and refresh bundled crafting
  recipes. Crafting Workbench tiers remain independent. Existing configurations
  retain their progression-preset behavior until an operator selects a new mode.
- Research Designer captures and offline Analyzer comparisons include station
  choices. Older projects retain their original station rules when opened.
- Updated the network protocol to 64 and server settings to version 5. Clients
  and servers must update together; saved player progression is unchanged.

## 1.3.1 - 2026-09-07

This is the first public release of the 1.3 update; 1.3.0 was not published.
The [combined release notes](docs/releases/1.3.1.md) cover all changes from
1.3.0 and 1.3.1, including the beta, for users upgrading from 1.2.0. The entries
below retain their development-version grouping.

- Released the 1.3.1 progression update, including physical Scrap, tiered stations,
  Research Data analysis, Blueprint Fragments, and the redesigned Tech Tree from
  1.3.1-beta.1. Added the in-game Research Designer described below.

- Gave Research Designer more room for the tree and weapon catalog with one
  compact toolbar, a scrolling weapon list, single-line entries, and controls
  inside the canvas. Project actions and help are grouped in the Project menu.

- Simplified Research Designer with a persistent Build canvas, searchable weapon
  catalog, Add next and Connect actions, direct drag placement, named branch
  regions, and nearby research controls. Incomplete nodes stay visible for repair.
  Added quick player-test presets and clearer Build, Test and Export entry points.

- Fixed Research Designer save retries after a partial disk-write failure and
  preserved unchanged quantities when applying ingredient or simulation slots.
  Import requires another review if any part of the selected bundle changes.

- Improved Research Designer responsiveness by cancelling replaced previews and
  adding Cancel/Retry controls under Issues. Fixed the fallback research cost in blank projects.

- Added reviewed Research Designer datapack export and editable project sharing.
  Export includes dependencies, required settings, and activation instructions;
  importing creates a separate local project.

- Added Research Designer property forms with inherited/effective values and
  synthetic player scenarios. Preview research routes, total payments, Research
  Bench requirements, hidden content, and direct or physical-blueprint results
  without changing a real player or server.

- Added reviewed Tech Tree editing in Research Designer: catalog insertion,
  multiple-node selection, nested branches, prerequisite groups, drag moves,
  reversible arrangement, and empty branch templates. Structural edits preserve
  research costs and require explicit repair of deleted prerequisites.

- Added the Research Designer workspace with a local project chooser, searchable
  node and branch library, compact Tech Tree preview, responsive inspector,
  validation drawer, and save/recovery controls. Open it from the title or pause
  menu, `/researchdesigner`, or an assigned Controls key.

- Added detached Research Designer compilation and explicit scenario comparisons,
  with dependency diagnostics and offline research, crafting, Analyzer and
  Research Data evaluation. Draft validation does not activate server changes.

- Added a permission-checked Research Designer capture API and explicit conversion
  into local custom-tree projects, with retained dependency context for offline
  work.
- Updated the network protocol to 63 for bounded operator capture requests.
  Clients and servers must use matching builds.

## 1.3.1-beta.1 - 2026-09-06

### Added

- Reorganized settings into clearer currency, Research Data, tier, crafting, and
  fragment sections. Percentage and hold-time inputs now use readable units;
  large counts use number boxes. Updated descriptions explain Scrap rewards,
  Analyzer odds, and the separate fragment systems. Saved config values remain
  compatible. Restore Tree Appearance now also resets motion and the grid.

- Weapon analysis now uses Analyzer and weapon tiers by default. Above-tier
  attempts can yield nothing; same-tier and lower-tier attempts always yield a
  blueprint or fragments. Pack authors can override every tier pair, cost, and
  fragment quantity, or select the legacy guaranteed-analysis preset.

- The Blueprint Analyzer automatically returns ammunition when analysis or weapon
  recycling succeeds. Ammo fills inventory space and drops any overflow at your
  feet; the preview shows what will be returned.

- Added physical Scrap as the default research currency, with configurable chest
  and player-kill supply. Research Bench purchases, Analyzer costs and returns,
  and gameplay rewards use the selected currency; banked RP remains optional.
  See [currency configuration](docs/research-currency-configuration.md).
- Added one-item Research Data analysis at the Blueprint Analyzer. Notes target
  Tier 1 weapons, Reports Tiers 1–2, and Dossiers Tiers 1–3. Higher Analyzers
  improve fragment quantities and blueprint chances. Pack authors can change
  pools, weights, costs, odds, and learned-target eligibility. Fixed currency
  redemption remains available. See [Data policies](docs/research-data-policies.md).
- Added shared reconstruction for fragments from Data and weapon analysis,
  including when weapon analysis uses legacy mode. Saved analysis progress can
  reconstruct matching protected blueprints after the target is learned.
- Added Analyzer Data pool summaries, outcome odds, station comparisons, and
  eligibility guidance. Research screens, item tooltips, and onboarding show the
  active currency and carried balance. Data loot has its own enablement setting.
- Added operator currency inspection and grants, an explicit banked/legacy
  preset, and previewed one-way withdrawals of saved RP. See
  [migration](docs/research-currency-migration.md).
- Added an active-currency award API and events that identify delivered items
  and units. Banked compatibility APIs explicitly reject physical mode. See
  [currency integrations](docs/research-currency-integrations.md).
- Added pack examples and economy reports for supply, research prices, Data
  outcomes, and multiplayer fragment pooling. See
  [economy balance](docs/research-economy-balance.md).

- Added optional Blueprint Analyzer datapacks for tiered analysis, legacy
  behavior, and exact-target/tag overrides. Pack authors can report expected
  attempts and resource costs, including fragment reconstruction, from a
  resolved catalog export. See the [preset and balance guide](docs/analyzer-presets-and-balancing.md).

- Added Advanced and Experimental Blueprint Analyzers with direct crafting
  recipes, distinct models, and tier labels. Tiered policies can award full
  blueprints, matching fragments, or an explicitly configured no-result outcome.
  Profiles without the Analyzer extension retain deterministic behavior. See the
  [station guide](docs/blueprint-analyzer-stations.md).

- Added an API for protected analysis fragments, saved progress, and blueprint
  reconstruction. Analysis progress stays separate from ordinary fragment
  discounts and RP returns. The Analyzer can reconstruct matching blueprints
  even after the target is learned. See the
  [storage and reconstruction guide](docs/analysis-fragment-storage.md).

- Pack authors can validate proposed tiered Blueprint Analyzer policies and inspect
  their resolved odds, costs, and reconstruction thresholds in research exports.
  See the
  [policy authoring guide](docs/analyzer-policy-authoring.md).

- Pack authors can name and nest Tech Tree branches, assign blueprints to separate
  specialties, and control when branch labels become visible. Existing packs
  retain inferred layouts. See the [authored branch guide](docs/research-tree-authored-branches.md).

### Compatibility

- Update clients and servers together: protocol 62 synchronizes active currency,
  Data settings and previews, action costs, committed outcomes, and analysis
  progress. Player-data version 5 preserves old saves with an empty analysis
  archive when upgrading from older formats. Server config remains version 4.
- Configurations missing the new economy selectors use Scrap and Data analysis.
  Explicit banked/legacy choices take precedence; saved RP is preserved and
  never automatically converted. Review the [migration guide](docs/research-currency-migration.md)
  before updating an established server.
- Existing datapack formats remain readable. Authored branch metadata uses
  tree/placement format 3, Analyzer extensions use profile/rule format 5, and
  Data/shared reconstruction use format 1. Catalog exports remain format 22
  with an optional versioned Research Data economy section.

### Changed

- Keep weapon artwork visible farther out in the Tech Tree, with an icon-focused
  middle zoom level and enlarged weapon previews on hover.

- Expand the Tech Tree into the inspect panel’s space when no research node is selected.

- Redesigned the Blueprint Analyzer with a horizontal Input/results/Output
  layout, dark metal panels, clearer outcome icons, and one main action button.
  Costs follow the selected operation; longer rules live in Details. Repeated
  ready messages are removed, and fragment deposits distinguish saved progress
  from items retained in Input.

- Replaced all three Blueprint Analyzer models with custom manual, microscope,
  and electronic inspection benches. Analyzers now require two blocks of space
  side by side; either half opens the menu and breaking either half removes the station.

- The Blueprint Analyzer now shows station and weapon tiers, full/partial/no-result
  odds, saved fragment progress, and separate costs for analysis and currency recovery.
  Scroll its details for materials and blueprint requirements. Reconstruction
  shows how many fragments stay in the input slot, and completed actions report
  their actual result. See the [screen guide](docs/analyzer-screen.md).

- Replaced all three crafting Workbench models and all three Research Bench
  models with custom pixel-textured designs. Higher Research Bench tiers now
  build on the basic drafting table with inspection tools and scanning equipment.
- Reduced Tech Tree opening work and overview rendering cost, with cached node art
  and fewer repeated draw operations while panning and zooming.

- Reworked the Tech Tree with named compact tiles, separate branch regions,
  proportional zoom, and a scrolling requirements inspector. Research actions
  remain accessible while you scroll through requirements.
- Added distinct Tech Tree marks for inspection, keyboard focus, search results,
  and tracked research routes, with updated scrollable help. Tree updates and
  resource reloads now refresh the displayed research details and clear
  withdrawn content.
- Replaced the three crafting Workbench models with surface-optimized voxel
  versions while retaining their existing placement footprint and item-view
  orientation.

### Fixed

- Multiplayer settings now receive the server’s values when joining, instead of
  displaying local defaults in the operator screen.

- Invalid currency item settings now retain the previous valid value, or Scrap
  on first load, without preventing configuration initialization.
- Fragment previews in the Blueprint Analyzer now show carried currency in
  physical mode and saved RP in banked mode, including unavailable actions.
- Reduced repeated work when projecting large Tech Tree branches and choosing
  prerequisites for automatically placed weapons, preserving their ordering.

- Fixed research progress being lost on player respawn after its capability handle was invalidated.
- Corrected Blueprint Analyzer result wording when analysis produces one fragment.

- Fixed incomplete input and reward-claim restoration when another integration throws during rollback.
- Fixed awards proceeding after a pre-award callback changes the currency policy, and reward feedback using a currency selected after the award.
- Fixed rejected Research Bench and Research Data actions reporting dormant RP instead of carried currency in physical mode.

- Turned Workbench, Research Bench, and Blueprint Analyzer inventory icons around
  to show their fronts. Placed models retain their existing orientation.

- When weapon analysis is blocked but currency recovery is available, the Blueprint
  Analyzer now shows the recovery cost and status while explaining the analysis
  restriction separately.

- Blueprint Analyzer currency recovery now shows unmet requirements before its award
  can be calculated, and direct-only mode hides inactive analysis details.
  Blueprint outputs with disabled learning now state that restriction. Delayed
  action feedback no longer attaches to replacement inputs with the same target
  and count.

- Analyzer requests now reject repeated request IDs and changes to inputs,
  inventory, research progress, or policy since the displayed preview. Failed
  tiered transactions restore consumed items, currency, output, and discovery together.

- Blueprint Analyzer actions now reject removed or replaced stations, including
  immediate same-tier replacements. The original Analyzer can now be harvested
  with a pickaxe.

- Kept the Tech Tree zoom-out button available at distant overview scales, matching
  mouse-wheel zoom. Zoom controls now follow the destination of an active camera transition.

- Research policy now uses the loaded configuration at server startup, including
  the selected research profile and Research Points cap in current-version files.

### Removed

- Removed Research Bench and crafting Workbench upgrade kits. Every station
  tier remains available through its own direct crafting recipe.

## 1.3.0 - 2026-09-03

### Added

- Added separate three-tier Research Benches and crafting Workbenches,
  configurable Blueprint Fragment behavior, and reusable Progression Gate
  requirements for datapacks and future event-based unlocks.
- Added format-3 research profile and rule authoring for workstation tiers,
  Blueprint Fragments, and Progression Gates, plus format-4 authoring for
  independently resolved crafting policy. Resolved policy details are included
  in research inspection, status, and format-20 catalog exports.
- Added bounded per-player storage for archived Blueprint Fragments and durable
  Progression Gate criteria, including operator inspection and independent
  `fragments` and `criteria` reset scopes.
- Added disclosure-filtered synchronization that never sends hidden or
  unrelated Progression Gate criterion IDs to clients. Full Blueprint Journal
  entries and selected Tech Tree details now show the applicable crafting
  Workbench requirement without exposing authoring diagnostics.
- Added a server-only Progression Gate criterion API with idempotent grants,
  bounded increments, administrative clearing, cancellable pre-change events,
  immutable post-change events, and operator test commands.
- Added disclosure-safe gate evaluation for custom criteria, vanilla
  advancements, and the active Research Bench context. Advancement requirements
  use current player progress after login or reload without a separate ledger.
- Added three dedicated Workbench levels with native TaCZ crafting and direct
  recipes for every level.
- Added server-authoritative crafting-tier and Progression Gate enforcement for
  learned recipes, including configurable exact and fallback tiers for
  compatible third-party TaCZ workstations.
- Added approachable ammo and attachment crafting strategies, plus advanced
  no-level, disabled, and exact crafting overrides that remain independent of
  blueprint-free knowledge settings.
- Added separate research and crafting results to operator inspection, plus
  complete format-20 crafting-policy coverage, sources, rules, reasons, and
  warnings in research catalog exports.
- Added target-specific Blueprint Fragments to eligible blueprint loot. A
  configurable share replaces full-blueprint drops, and player-generated loot
  favors weapons the player has not learned without removing learned targets.
- Added Blueprint Fragment archiving to the Blueprint Analyzer. Completed sets
  can discount the matching Tech Tree research cost, reconstruct a protected
  blueprint, or return configured RP when the target is already learned.
- Included fragment discounts and set consumption in shortest-path research
  previews and atomic transactions, with stale-preview protection and complete
  rollback of inventory, RP, fragment, and knowledge changes.

### Fixed

- Kept core research and crafting rule selection independent in both
  directions, so a more specific crafting rule cannot hide broader research
  costs, prerequisites, visibility, recycling, discovery, or
  reverse-engineering policy, and a research-only rule cannot hide broader
  crafting policy.
- Kept the active profile, tiered progression, and Blueprint Fragment controls
  configurable when Research Bench learning is disabled, because Workbench
  crafting and blueprint reconstruction can remain active independently.
- Made crafting Workbench eligibility consume the complete crafting-policy
  projection without requiring the weapon to have a Tech Tree research
  assignment.
- Kept crafting Workbench tabs initialized while recipes remain unavailable
  until the initial server crafting-access response arrives, preventing TaCZ
  from initializing the tabs as permanently empty or exposing provisional
  crafting access.
- Republished tier and gate policy after live server-setting changes, and made
  crafting access repair a missed integrated-server config callback instead of
  leaving every crafting Workbench unavailable.
- Kept progression-exempt recipes subject to configured crafting tiers and
  Progression Gates instead of allowing them to bypass those policies.
- Prevented completed Blueprint Fragment sets from becoming stranded at their
  retention cap when converting an already learned target into RP.
- Made native crafting access synchronization request-scoped and chunked so
  large recipe catalogs cannot exceed the network payload budget or apply a
  stale response to a newly opened workstation. An incomplete response can be
  requested once more without allowing repeated requests to amplify server
  work.
- Limited native crafting access evaluation to learned and exempt candidates,
  and avoided rebuilding that access list for RP-only updates.
- Raised a resolved fragment retention cap to any exact threshold override so
  operator or datapack settings cannot make a completed set unreachable.
- Prevented unusually large combinations of research profiles and catalog
  entries from exhausting server memory during policy reloads.
- Kept upgraded version-2 servers on Classic progression with Blueprint
  Fragments disabled until an operator opts into the new systems.
- Corrected release reports and progression diagnostics so they identify the
  active compatibility versions and only attribute settings to rules that
  actually define them.
- Fixed a dedicated-server startup failure caused by client-only settings
  controls loading during common configuration initialization.
- Removed the ordinary crafting recipe for TaCZ's legacy Gun Smith Table while
  retaining the block, item, assets, Creative entry, and configurable crafting
  compatibility for existing tables.

### Compatibility

- Advanced player progression data to version 4 with automatic migration from
  older saves, and advanced the network protocol to 55. Clients and servers
  must update together.

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
