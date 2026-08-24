# Phase 4.1 Stabilization

Date: 2026-08-24

This pass addresses the correctness and integration issues found during the full Phase 0-4 audit. It does not change the intended blueprint progression or the six built-in loot tiers. Its purpose is to make the dynamic foundation safe across player lifecycle events, integrated servers, datapack reloads, hostile or malformed data, partial migrations, and packaged releases.

## Player capability lifecycle

Learned recipes are now copied on every Forge player clone, including both death respawns and the non-death clone used when returning from the End. The original player's invalidated capabilities are revived only for the duration of the copy and invalidated again in a `finally` block.

The old temporary UUID map and death/respawn bridge were removed. Capability providers now register their invalidation listener when attached, and the clone receives an independent serialized snapshot rather than sharing mutable state.

Recipe-data synchronization still occurs after login, respawn, dimension changes, blueprint use, administrative clearing, catalog reload, and successful datapack reload.

## Authoritative and presentation catalogs

The former process-wide catalog singleton is split into explicit `SERVER` and `CLIENT` instances.

- Server gameplay, loot eligibility, commands, and outgoing synchronization always use `SERVER`.
- Client item rendering, names, tooltips, and creative tabs use `CLIENT`.
- A catalog packet can no longer overwrite the authoritative catalog in an integrated-server process.
- Catalog rebuilds publish a replacement only after the complete TaCZ recipe scan succeeds.
- If the TaCZ recipe manager is unavailable or a top-level rebuild error occurs, the prior complete server snapshot remains authoritative and no empty catalog is synchronized.

Individual malformed TaCZ recipes remain isolated: they are diagnosed and skipped without preventing valid recipes from rebuilding.

## Network boundaries and reload synchronization

Catalog packets now use deterministic ordering and immutable snapshots. Their constructor and decoder enforce a 4,096-entry limit, bounded translation keys and item types, non-null metadata, duplicate-ID rejection, and matching encoder/decoder string limits.

Learned-recipe packets retain deterministic ordering and enforce a 4,096-entry, 256-character wire contract. Before sending, learned IDs are intersected with recipes in the current authoritative catalog. Removed content-pack recipes remain in persistent player data, so reinstalling that pack restores the unlock, but stale IDs cannot exhaust the client packet budget or appear in its active crafting view.

After a successful server-wide datapack reload, the server rebuilds the TaCZ-derived catalog once and synchronizes both the catalog and active learned recipes to every connected player. A failed rebuild preserves and does not broadcast over the last-known-good catalog.

Receiving a new client catalog invalidates Minecraft's creative-tab cache. The next normal refresh therefore rebuilds blueprint tabs from the synchronized catalog whether the creative screen was already open or is opened later.

## Strict and bounded datapack data

Format-1 pool and rule codecs now reject unknown fields at every object level:

- pool: `format`, `entries`;
- entry: `blueprint`, `weight`;
- rule: `format`, `enabled`, `pool`, `loot_tables`, `chance`, `rolls`;
- rolls: `min`, `max`.

Malformed optional `enabled`, `chance`, and `rolls` values fail decoding rather than falling back to defaults. Existing format, weight, chance, roll-order, missing-pool, and enabled-target validation remains in place.

Resource limits protect reload and indexing work:

- at most 4,096 pools and 4,096 rules;
- at most 4,096 entries per pool;
- at most 4,096 target tables per rule;
- at most 65,536 enabled rule-to-table bindings.

Preparation still builds a complete immutable snapshot before publication. A decoding, validation, or reference failure leaves the previous complete loot snapshot active.

## Incremental ownership and loot budget

Dynamic ownership is now table-selective instead of all-or-nothing.

- Pools alone do not affect legacy behavior.
- An enabled rule suppresses legacy modifiers only for the tables it targets.
- A disabled rule with explicit targets suppresses legacy modifiers only for those targets.
- A disabled rule with an empty target list is the explicit global opt-out.
- Untargeted legacy tables continue operating, which supports gradual datapack migration and additive third-party integrations.

The 64-blueprint limit is now a true event-wide budget. Both dynamic and legacy modifiers count blueprints already present in generated loot, clamp their requested rolls to the remaining budget, and stop when it is exhausted. Multiple overlapping dynamic rules or multiple legacy modifiers can no longer each add up to 64 in one event.

## Client and inherited-code cleanup

The redundant GunSmith screen reinitalization after every ingredient-count update was removed. Reinitializing the entire screen there could reset interaction state and duplicate work during ordinary UI updates.

Invalid blueprint names and tooltips now use dedicated localization keys. Creative-tab empty-catalog warnings occur only after a client player exists and blueprints are enabled, avoiding misleading startup noise before the initial catalog packet arrives.

## Packaging hygiene

The data-generator cache and `.DS_Store` files are excluded from processed generated resources. The generated cache directory is ignored by Git and removed from the working generated-resource tree. Optional structure-mod dependencies remain opt-in for structure-aware data generation rather than being resolved for every normal build.

## Automated coverage

The suite now contains 26 tests covering the earlier Phase 2-4 behavior plus:

- independent capability cloning;
- strict rejection of malformed optional and unknown datapack fields;
- deterministic catalog packet round trips and outbound bounds;
- client/server catalog isolation;
- filtering stale learned recipes from active synchronization;
- pools-only fallback, partial table ownership, targeted disabling, and global opt-out;
- the shared 64-blueprint budget helpers.

Current result: 26 tests, 0 failures, 0 errors, 0 skipped.

## Build and runtime validation

- Clean Java 17 `test build`: successful, including compilation, 26 tests, resource processing, mixin processing, reobfuscation, and packaging.
- Dedicated Java 17 server: reached `Done`, loaded 6 pools, 6 rules, and 748 bindings, and rebuilt the 452-entry authoritative catalog without a side-only classloading or loot-codec failure.
- Java 17 client: reached the normal render loop, initialized OpenAL, built texture atlases, and applied the `ICreativeModeTabsAccessor` and GunSmith screen mixins without an injection failure.
- Isolated Java 17 structure-aware `runData`: successful with one dynamic and 485 legacy modifiers. All 485 legacy payloads reproduced byte-for-byte; the dynamic file differed only by its final newline, and the 486-entry global index reproduced the exact same entry set.
- Every checked project JSON resource parses successfully.
- The release JAR contains 12 dynamic schema files and 486 modifier files, with no data-generator cache or `.DS_Store` entries.

The smoke tests still report malformed recipe, language, sound-path, and missing gun-definition warnings from the installed third-party TaCZ content packs. They are the same external pack issues recorded in earlier phases; no new exception originated from the blueprint mod's stabilization paths.

## Remaining hands-on acceptance checks

The connected-client checks from Phase 4 still apply. The most important additional scenarios are:

1. Return from the End and confirm learned recipes remain available.
2. Keep the creative inventory open while reloading a TaCZ content pack and confirm the blueprint tabs refresh.
3. Load a datapack with one custom targeted rule and confirm untargeted legacy tables still distribute blueprints.
4. Disable one targeted rule and confirm only those tables produce no blueprint loot.
5. Use an empty-target disabled rule and confirm it globally opts out of legacy distribution.
6. Trigger a deliberately invalid reload and confirm both loot and catalog behavior continue from their last complete snapshots.
