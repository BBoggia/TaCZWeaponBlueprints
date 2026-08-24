# TaCZ Weapon Blueprints

TaCZ Weapon Blueprints adds persistent blueprint progression to Timeless and Classics Zero. Players find blueprints in configured loot, use them to learn TaCZ gun-smithing recipes, and retain those unlocks across death, dimension changes, logout, and content-pack reloads.

The mod is server-authoritative: the client UI hides locked recipes for convenience, while the server independently rejects attempts to craft recipes the player has not learned.

## Requirements

- Minecraft 1.20.1
- Forge 47.x (validated with 47.3.0)
- Timeless and Classics Zero 1.1.5 (`[1.1.5,1.2)`)
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

## Configuration

The synchronized Fzzy Config screen exposes:

- global blueprint enable/disable;
- default blueprint loot chance;
- default minimum and maximum rolls, bounded to 64;
- gun, ammo, and attachment blacklists.

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

Use vanilla `/reload` after changing blueprint loot datapacks. A successful reload advances the revision reported by `/gg loot status`; an invalid reload leaves the last-known-good revision active.

## Datapack resources

Definitions use these locations:

```text
data/<namespace>/taczweaponblueprints/blueprint_tags/<path>.json
data/<namespace>/taczweaponblueprints/loot_pools/<path>.json
data/<namespace>/taczweaponblueprints/loot_rules/<path>.json
```

Format 1 provides exact weighted pools and exact loot-table rules. Format 2 adds reusable composition, current-catalog selection, table-family selection, and runtime predicates. See [Phase 5 implementation](docs/development/phase-5-implementation.md) for the complete schemas and [operations and migration](docs/operations-and-migration.md) for rollout and rollback guidance.

## Building

Use JDK 17:

```text
./gradlew cleanTest build
./gradlew verifyReleaseArtifact
```

Normal builds do not resolve optional structure mods. Structure-aware legacy data regeneration is opt-in:

```text
./gradlew runData -PincludeStructureDataMods=true
```

Run structure-aware generation only in a clean or isolated copy and review the generated-resource diff before accepting it.

Packet Fixer is excluded from the normal client and server development runtime so
minimum-dependency smoke tests are representative. It can be enabled only for an
explicit compatibility run with `-PincludePacketFixer=true`.

Before public distribution, run:

```text
./gradlew verifyPublicationReadiness
```

This adds the unresolved project-license decision to the normal artifact checks.

The repository's complete release procedure is recorded in the
[release checklist](docs/release-checklist.md).

The release artifact is written to `build/libs/taczweaponblueprints-<version>.jar`.

## Development history

The staged recovery and redesign are recorded under [docs/development](docs/development), from the preserved Phase 0 baseline through the final Phase 8 release certification.
