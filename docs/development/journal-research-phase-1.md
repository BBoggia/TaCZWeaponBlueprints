# Journal and Research Phase 1: Progression Foundation

Date: 2026-08-24

## Scope

Phase 1 implements the server-authoritative player progression foundation for
the Blueprint Journal, duplicate recycling, and research systems. It adds
versioned persistence and synchronized state without adding the Journal screen,
inventory discovery hooks, recycling gameplay, research definitions, or the
Research Bench.

## Versioned player data

The existing `taczweaponblueprints:player_recipe_data` capability and its
legacy NBT keys remain in place. The capability now writes schema version 1:

```text
DataVersion: 1
Recipes: string list
Blueprints: string list
DiscoveredBlueprints: string list
ResearchPoints: integer
```

`Recipes` remains the downgrade-compatible canonical TaCZ recipe list.
`Blueprints` remains the durable set of learned output IDs. The two new fields
store permanent discovery history and the server-authoritative Research Point
balance.

Loading a version 0 save:

1. normalizes and retains valid legacy recipe and learned blueprint IDs;
2. copies learned blueprint IDs into discovery history;
3. initializes Research Points to zero;
4. preserves unknown but syntactically valid content-pack IDs;
5. migrates active legacy recipe IDs through the catalog when it becomes
   available.

Every load also repairs the invariant that every learned blueprint is
discovered. Learned IDs take priority when corrupted or manually edited data
would otherwise exceed discovery capacity.

All saved ID lists are deterministic and sorted. Invalid values are ignored,
oversized numeric balances are clamped during recovery, and NBT scanning is
bounded so malformed saves cannot bypass the Phase 0 limits.

## Progression operations

The player capability now exposes:

- read-only learned and discovered ID sets;
- discovery and membership operations;
- validated Research Point replacement, earning, and spending operations;
- atomic replacement of a complete synchronized progression snapshot.

Learning a blueprint discovers it in the same operation. Learning fails
without mutation when either collection has reached its safety limit.

Research Point earning rejects negative values, invalid caps, cap overflow,
and integer overflow. Spending rejects negative values and insufficient funds.
All rejected operations leave the prior balance unchanged.

The existing `clearRecipes` behavior still clears learned recipes and learned
blueprints, but now deliberately retains discovery history and Research Points.
Player cloning copies the complete versioned capability snapshot.

## Shared limits

`PlayerProgressionLimits` is the single source for player persistence and wire
limits:

| Boundary | Limit |
| --- | ---: |
| Capability schema | 1 |
| IDs per player collection | 4,096 |
| Resource ID length | 256 characters |
| Research Points | 1,000,000,000 |

Network resource-ID validation references the same constant. All blueprint
snapshot accumulators are also limited to 16 chunks, while each encoded chunk
retains the existing 900,000-byte ceiling.

## Progression synchronization

`SyncPlayerProgressionPacket` synchronizes durable learned blueprint IDs,
discovered blueprint IDs, and Research Points independently of the active TaCZ
recipe-filter snapshot.

The packet family:

- normalizes and sorts outbound state;
- requires learned IDs to be a subset of discovered IDs;
- splits worst-case snapshots below the byte ceiling;
- validates IDs, counts, point balances, and chunk metadata while decoding;
- accumulates chunks by synchronization sequence;
- publishes to the client capability only after a complete valid snapshot is
  available.

Login, respawn, dimension-change, blueprint-use, catalog-reload, and recipe
reset synchronization paths now send the progression snapshot alongside the
existing active-recipe snapshot.

The network protocol is intentionally bumped from `3` to `4`. Clients and
servers from before Phase 1 are rejected instead of accepting an incompatible
wire format.

## Catalog and item integration

Catalog migration now discovers every learned output it resolves from legacy
recipe state. It continues retaining canonical recipes for downgrade
compatibility.

Blueprint use distinguishes an already-known blueprint from a progression
capacity failure. A capacity failure displays a dedicated message and does not
consume the item.

## Verification

The clean Phase 1 validation completed successfully:

- 59 automated tests;
- 0 failures, errors, or skipped tests;
- `clean build`;
- `verifyReleaseArtifact`;
- `verifyPublicationReadiness`;
- `git diff --check`;
- dedicated server reached `Done (2.461s)` with TaCZ 1.1.8-hotfix;
- client initialized OpenGL 4.1, OpenAL, texture atlases, and the gun-smithing
  screen mixin.

The test suite covers version 0 migration, deterministic version 1 round trips,
discovery invariants, reset behavior, clone persistence, corrupt balances,
point atomicity, collection ceilings, catalog migration, packet validation,
and worst-case chunking.

The installed CCRP and Suffuse packs continue to report their pre-existing
malformed language, sound-path, and unresolved-content errors. No Phase 1
error, packet-registration failure, or mixin failure appeared.

## Deferred to later phases

Phase 1 intentionally does not implement:

- discovering blueprint items when they enter inventory;
- Journal presentation or visibility policy;
- recycling values or transactions;
- research profiles, rules, costs, or prerequisites;
- synchronized coarse progression configuration;
- the Research Bench block, menu, or result transaction.

Those features can now consume one bounded, migrated, server-authoritative
progression model.
