# TaCZ Weapon Blueprints

TaCZ Weapon Blueprints adds persistent blueprint progression to Timeless and Classics Zero. Players find blueprints in configured loot, use them to learn TaCZ gun-smithing recipes, and retain those unlocks across death, dimension changes, logout, and content-pack reloads.

The mod is server-authoritative: the client UI hides locked recipes for convenience, while the server independently rejects attempts to craft recipes the player has not learned.

## Requirements

- Minecraft 1.20.1
- Forge 47.x (validated with 47.3.0)
- Timeless and Classics Zero 1.1.8-hotfix (`[1.1.8,1.2)`)
- Fzzy Config 0.5.9 (`[0.5.9,0.6)`)
- Kotlin for Forge 4.11.x, required by Fzzy Config

Install the same mod and dependency versions on both client and server. TaCZ content packs may be added or removed independently; the blueprint catalog is rebuilt from the recipes currently available on the server.

## Core behavior

- Blueprint items persist unlocks by TaCZ output ID and expose one deterministic canonical recipe per output.
- Legacy exact-recipe unlocks migrate through current duplicate aliases without discarding rollback-compatible data.
- Player and catalog state are synchronized with byte-budgeted, atomic chunks.
- Crafting enforcement occurs in the server-side TaCZ gun-smithing menu.
- Blueprint loot uses live chance, roll-range, and blacklist configuration.
- Datapacks can replace or extend loot pools and rules without rebuilding the mod.
- Format-2 datapacks support tags, pool inheritance, catalog selectors, loot-table selectors, dimensions, and luck predicates.
- Catalog and loot reloads publish complete immutable snapshots; failed rebuilds preserve the previous working state.
- The Blueprint Journal presents disclosure-filtered discovery, completion, research, and recycling policy.
- The Research Bench performs atomic, server-authoritative research and voluntary duplicate recycling.
- Research profiles and rules can configure point costs, item/tag ingredients, prerequisites, visibility, and recycling values.
- Research Bench tree topology is derived directly from those prerequisites, with hidden policies remaining undisclosed.
- The built-in TaCZ 1.1.8 progression covers all 54 default weapons once across seven independently researchable, bottom-to-top branches.
- Branches and All Weapons views support mouse pan/zoom, a fullscreen tree sidebar, cross-branch portals, arrow-key graph traversal, and keyboard-selectable search results.

## Configuration

The synchronized Fzzy Config screen exposes:

- global blueprint enable/disable;
- default blueprint loot chance;
- default minimum and maximum rolls, bounded to 64;
- gun, ammo, and attachment blacklists.
- Journal, discovery tracking, research, and manual recycling enablement;
- undiscovered visibility, Research Point cap, Creative cost bypass, and active research profile.

Datapack rules may override chance and rolls for individual loot policies. Rules that omit those fields continue using the live global defaults.

## Operator commands

All `/gg` commands require permission level 2.

| Command | Purpose |
| --- | --- |
| `/gg clearRecipes` | Clear the invoking player's learned blueprint recipes. |
| `/gg reloadRecipes` | Rebuild the authoritative catalog and synchronize players. |
| `/gg loot status` | Show snapshot, catalog, configuration, and distribution-mode status. |
| `/gg loot inspect <loot_table>` | Explain dynamic ownership, targeting, predicates, and candidates. |
| `/gg loot pool <pool_id>` | Inspect a prepared pool and its current catalog candidates. |
| `/gg loot preview <loot_table>` | Show effective chance, rolls, weights, probabilities, and expected additions. |
| `/gg progression inspect <player>` | Inspect a player's durable blueprint progression counts and Research Points. |
| `/gg progression reset <targets> <learned\|discovered\|points\|all>` | Explicitly reset one progression state while preserving invariants. |
| `/gg research status` | Audit the active research profile and presentation groups against the live TaCZ catalog. |
| `/gg research inspect <blueprint_id>` | Inspect the selected rule, visibility, cost, prerequisites, and authored placement. |
| `/gg research export` | Export a sorted format-2 authoring catalog inside the current world folder. |

Use vanilla `/reload` after changing blueprint loot datapacks. A successful reload advances the revision reported by `/gg loot status`; an invalid reload leaves the last-known-good revision active.

The current client/server network protocol is `15`; matching mod versions are
required on both sides. Protocol 15 publishes each disclosure-safe research
graph and its matching branch metadata atomically, transfers inventory-only
Research Bench previews, and rejects stale or conflicting progression chunks.
It does not change persisted player progression or require a world migration.

## Datapack resources

Definitions use these locations:

```text
data/<namespace>/taczweaponblueprints/blueprint_tags/<path>.json
data/<namespace>/taczweaponblueprints/loot_pools/<path>.json
data/<namespace>/taczweaponblueprints/loot_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
```

Format 1 provides exact weighted pools and exact loot-table rules. Format 2 adds reusable composition, current-catalog selection, table-family selection, and runtime predicates. Research profiles provide defaults while deterministic exact, tag, namespace, category, and catalog-selector rules provide per-blueprint overrides. Separate research-tree group resources author presentation without changing progression. See the [research-tree authoring guide](docs/research-tree-authoring.md), [grouped-navigation contract](docs/development/research-tree-navigation-phase-0.md), [Phase 1 group-data implementation](docs/development/research-tree-navigation-phase-1.md), [Phase 2 publication boundary](docs/development/research-tree-navigation-phase-2.md), [Phase 3 synchronization](docs/development/research-tree-navigation-phase-3.md), [Phase 5 navigation and layout](docs/development/research-tree-navigation-phase-5.md), [Phase 6 default progression](docs/development/research-tree-navigation-phase-6.md), [Phase 7 adversarial hardening](docs/development/research-tree-navigation-phase-7.md), [Phase 8 release preparation](docs/development/research-tree-navigation-phase-8.md), [Journal/research Phase 8](docs/development/journal-research-phase-8.md), and [operations and migration](docs/operations-and-migration.md).

## Building

Use JDK 17:

```text
./gradlew cleanTest build
./gradlew certifyReleaseCandidate
```

`certifyReleaseCandidate` runs the publication and packaged-artifact gates and
writes `build/reports/release-candidate.json` with the exact dependency
versions, build JVM, network protocol, test totals, artifact size, and SHA-256.

Normal builds do not resolve optional structure mods. Structure-aware legacy data regeneration is opt-in:

```text
./gradlew runData -PincludeStructureDataMods=true
```

Run structure-aware generation only in a clean or isolated copy and review the generated-resource diff before accepting it.

Packet Fixer is excluded from the normal client and server development runtime so
minimum-dependency smoke tests are representative. It can be enabled only for an
explicit compatibility run with `-PincludePacketFixer=true`.

Captured client and server startup logs can be checked independently:

```text
./gradlew verifyRuntimeSmokeLog -PsmokeKind=client -PsmokeLog=run/logs/client.log.gz
./gradlew verifyRuntimeSmokeLog -PsmokeKind=server -PsmokeLog=run/logs/server.log
```

These checks require complete logs from startup through the main menu or
dedicated-server `Done` marker. They reject missing lifecycle markers and known
mod-local classloading, mixin, and initialization failures without treating a
third-party content-pack warning as a failure of this mod.

The repository's complete release procedure is recorded in the
[release checklist](docs/release-checklist.md), with the hands-on tree matrix in
[research-tree manual QA](docs/research-tree-manual-qa.md).

The release artifact is written to `build/libs/taczweaponblueprints-<version>.jar`.

## Development history

The staged recovery and redesign are recorded under [docs/development](docs/development), from the preserved Phase 0 baseline through the final Phase 8 release certification.
