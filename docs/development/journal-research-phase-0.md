# Journal and Research Phase 0: Baseline and Contracts

## Scope

Phase 0 establishes the immutable starting point and design contracts for the
Blueprint Journal, duplicate recycling, and Research Bench feature line. It
does not change gameplay, persistence, networking, configuration, registries,
datapack formats, or packaged resources.

Development begins on `feature/blueprint-research` from the dereferenced
`v1.0.4` tag at commit `7792a13e22745d3f2948358754cac87046beeabe`.
The intended public feature line is `1.1.0`, using beta and release-candidate
builds before a stable release.

## Phase 0 baseline

### Supported runtime

- Minecraft 1.20.1;
- Forge 47.x, validated with 47.3.0;
- TaCZ 1.1.8-hotfix, resolved as
  `1.1.8-hotfix_mapped_official_1.20.1` from Modrinth;
- Fzzy Config 0.5.9;
- Kotlin for Forge 4.11.x;
- network protocol `3`.

### Automated and artifact baseline

The exact `v1.0.4` tree passed:

- `clean cleanTest build`;
- 50 automated tests, with 0 failures, 0 errors, and 0 skipped;
- `verifyReleaseArtifact`;
- `verifyPublicationReadiness`;
- packaged version `1.0.4`;
- packaged TaCZ range `[1.1.8,1.2)`.

The rebuilt release artifact is
`build/libs/taczweaponblueprints-1.0.4.jar` with SHA-256:

```text
cedd53067d9d28cd6929b8f8b164117fd008b7ef9b9e5e5a7cea619008a3105c
```

This matches the published `v1.0.4` artifact.

Gradle reports deprecations in the current ForgeGradle/MixinGradle toolchain
that will matter for a future Gradle 9 migration. They do not fail or alter the
current Gradle 8.8 build.

### Dedicated-server baseline

The development dedicated server:

- loaded TaCZ 1.1.8-hotfix and add-on version 1.0.4;
- applied `GunSmithTableMenuMixin` without an injection error;
- published loot snapshot revision 1 with 6 pools, 6 rules, and 748 exact
  bindings;
- reached `Done (1.977s)`;
- registered 481 blueprints from 724 loaded TaCZ recipes: 191 guns, 46
  ammunition entries, and 244 attachments;
- ignored 235 duplicate recipe aliases;
- isolated and skipped 8 invalid third-party recipes.

### Client baseline

With TaCZ lazy client asset loading enabled, the development client:

- opened an OpenGL 4.1 context;
- applied `GunSmithTableScreenMixin` without an injection error;
- initialized OpenAL and the sound engine;
- created the normal texture atlases;
- reached the normal render loop.

The installed Suffuse pack contains two invalid sound paths, and the installed
CCRP pack contains malformed English language JSON. Those external data errors
occur without this feature work and are not Phase 0 regressions.

## Existing contracts that must remain valid

### Blueprint identity and catalog

- A blueprint is identified by the TaCZ output `ResourceLocation`, not by a
  gun-smithing recipe ID.
- A catalog output has one deterministic canonical recipe.
- Additional recipes producing the same output are aliases and must not create
  additional progression entries.
- Legacy recipe IDs migrate through the active recipe-to-blueprint alias map.
- Missing content-pack IDs remain persisted so reinstalling the pack can make
  them active again.
- Server and client catalogs remain separate so integrated-client sync cannot
  overwrite server authority.
- Catalog rebuilds publish a complete immutable snapshot or preserve the prior
  complete snapshot on failure.

### Player persistence

Version 1.0.4 stores two deterministic string lists in the existing player
capability:

- `Recipes`: legacy/canonical recipe IDs retained for downgrade compatibility;
- `Blueprints`: durable learned output IDs used by current progression.

The capability is copied for Forge player clones such as death/respawn and
return from the End. Ordinary dimension changes retain the same player
capability and trigger synchronization. The same capability ID and existing NBT
keys must remain readable throughout the 1.1.0 line.

### Networking

- Protocol 3 sends active learned recipe IDs and the presentation catalog in
  separate client-bound packet families.
- Entries are normalized, sorted, bounded, and accumulated atomically.
- Synchronization chunks remain below 900,000 encoded bytes, leaving headroom
  under Minecraft 1.20.1's custom-payload ceiling.
- A random per-server sequence prevents chunks from an earlier connection from
  completing a later snapshot.
- Client state is replaced only after every chunk in one snapshot arrives.

The progression sync introduced after Phase 0 must preserve these properties
and deliberately bump the protocol instead of accepting mismatched clients.

### Reload publication

- Loot definitions are decoded strictly; unknown fields and invalid values fail
  preparation.
- A complete immutable loot snapshot is published only after preparation
  succeeds.
- Research definitions will follow the same last-known-good publication model.
- Catalog-dependent policy caches must be keyed by both the immutable policy
  snapshot and immutable catalog snapshot, so either reload invalidates them.

## Feature contracts

### Discovery

1. Discovery uses the durable blueprint output ID.
2. A blueprint is discovered when a valid blueprint item first enters a
   player's inventory or is successfully learned. Generating it in unopened
   loot does not discover it.
3. Learning a blueprint always discovers it in the same server transaction.
   The invariant is `learned blueprints` is a subset of `discovered blueprints`.
4. Discovery is permanent until an explicit administrator reset. Dropping,
   trading, recycling, or consuming the item does not remove discovery.
5. Discovered IDs from removed content packs remain persisted and appear as
   unavailable history rather than being discarded.
6. Blacklisting or disabling a blueprint does not erase discovery or learning.
   It changes only current eligibility and completion calculations.
7. Disabling Journal or research features does not erase progression data.

Inventory detection may use explicit pickup/use/research hooks plus a
server-side `BlueprintItem` inventory-tick fallback. The fallback must perform
no synchronization or allocation after the ID is already discovered.

### Duplicate identity and ownership

1. A blueprint item is a duplicate for a player only when that player has
   already learned its durable output ID.
2. Merely discovering an entry does not make later copies duplicates.
3. Recipe aliases for one output share duplicate state.
4. The default policy keeps duplicate items unchanged and permits voluntary
   recycling at a Research Bench.
5. Phase 0 does not authorize automatic pickup conversion, personal loot
   rerolling, or duplicate suppression.
6. A duplicate remains tradeable before recycling; duplicate state is evaluated
   for the player performing the transaction.

### Research Points

1. Research Points are a per-player, server-authoritative integer balance.
2. They are not stored in blueprint item NBT and cannot be supplied by a client
   packet.
3. Addition rejects overflow, spending rejects underflow, and failed operations
   leave the balance unchanged.
4. No gameplay transaction may partially award or spend points.
5. The absolute implementation limit is 1,000,000,000 points. The synchronized
   server configuration may impose a lower cap; the planned default cap is
   1,000,000.
6. An individual recycling value or research cost must be between 0 and the
   active point cap.
7. Creative-mode recycling never produces points from an unconsumed/infinite
   item. Creative research cost bypass, if provided, is a separate synchronized
   setting.
8. Recycling or research is rejected when crediting/spending cannot complete in
   full. Partial credit is outside the initial scope.

### Recycling

1. Manual recycling is the initial and default behavior.
2. Only already-learned duplicates are recyclable by default.
3. Value is resolved from the current server research snapshot at commit time.
4. The item carries only its blueprint ID; it never carries an authoritative
   point value.
5. A recycling transaction validates the catalog/persisted identity, player
   state, current policy, point cap, and input stack before consuming exactly
   one item and crediting points.
6. Any validation failure preserves both the item and point balance.
7. Automatic recycling, unlearned recycling, rerolling, and alternate currencies
   require explicit future policies and are not implicit extensions of this
   contract.

### Research eligibility

A blueprint is researchable only when all applicable conditions are true:

- research is globally enabled;
- the output is present in the current authoritative catalog;
- the output is not currently blacklisted or administratively blocked;
- the player has not already learned it;
- a resolved profile/rule enables research;
- any discovery requirement is satisfied;
- all supported prerequisites are satisfied;
- the player has the complete point and item cost;
- the result can be produced without item loss.

Eligibility is recomputed on the server at result-take time. A client preview is
never authorization. The first implementation outputs a normal physical
blueprint and does not directly mutate learned state.

### Journal visibility

The visibility modes, ordered from least to most disclosure, are:

1. `HIDDEN`: no entry or completion disclosure;
2. `SILHOUETTE`: anonymous entry and aggregate progress position;
3. `NAME`: translated name without icon or detailed policy;
4. `PREVIEW`: name, icon, category, pack, and permitted research preview;
5. `FULL`: all client-presentable metadata.

`SILHOUETTE` is the planned default for undiscovered entries. Learned entries
are always fully presentable. Discovered entries use at least `PREVIEW` unless a
higher-priority server rule intentionally restricts them. Visibility affects
presentation only; it never grants learning, recycling, crafting, or research
permission.

Unavailable historical IDs may be listed by normalized ID, but a missing
content pack cannot be trusted to supply a name, icon, category, or cost.

### Journal completion

- The denominator is the current eligible authoritative catalog after global
  enablement and blacklists.
- The numerator is the player's learned durable IDs within that denominator.
- Aliases never count separately.
- Removed/unavailable historical entries appear in a separate history count and
  do not make current completion impossible.
- Discovered and researchable counts are distinct from learned completion.

### Research Bench transaction

1. The initial bench is an instant menu transaction and has no ticking block
   entity, persistent queue, power, or chunk-loading behavior.
2. The client sends at most a container ID, action, and selected blueprint ID.
3. The server confirms that the matching menu is open and still usable.
4. The result slot is virtual. On take, the server resolves the current catalog,
   player state, configuration, research snapshot, points, and ingredients.
5. The server consumes the complete cost and creates one normal blueprint as one
   logical commit.
6. If commit cannot complete, no points or items are consumed.
7. Closing the menu returns unused inputs to inventory and drops them only when
   safe insertion is impossible.
8. Datapack/config/catalog reload while the menu is open invalidates the preview
   and forces current-state revalidation.

### Configuration ownership

Synchronized Fzzy Config owns coarse server policy:

- Journal enabled;
- default visibility;
- discovery tracking enabled;
- research enabled;
- duplicate policy;
- unlearned recycling permission;
- point cap;
- creative research-cost behavior;
- active research profile ID.

Datapacks own detailed balance and selection:

- research/recycling values;
- item ingredients;
- exact, tag, namespace, category, and catalog-selector targeting;
- discovery requirements;
- future prerequisites.

Disabling a feature suppresses behavior but never deletes its stored data.

### Datapack precedence and replacement

Planned resources use:

```text
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
```

- Standard resource-pack priority replaces a lower-priority definition with the
  same namespace and path.
- Definitions with different IDs are additive.
- One resolved rule supplies a blueprint's policy overlay; policy is not merged
  field-by-field across an unbounded set of matching rules.
- Matching ranks by target specificity: exact ID, tag, catalog selector, then
  profile fallback.
- Within one specificity, higher numeric priority wins.
- A remaining tie is resolved by ascending definition ID for deterministic
  results and reported by diagnostics.
- Missing optional content-pack targets remain dormant.
- Unknown fields, invalid values, cycles, and hard-limit violations reject the
  new snapshot and preserve the last-known-good research publication.

## Hard limits reserved for implementation

| Boundary | Absolute limit |
| --- | ---: |
| Active blueprint catalog | 4,096 entries |
| Learned blueprint IDs per player | 4,096 |
| Discovered blueprint IDs per player | 4,096 |
| Legacy active recipe IDs per player | 4,096 |
| Resource ID length | 256 characters |
| Network synchronization chunk | 900,000 encoded bytes |
| Research Points | 1,000,000,000 |
| Research profiles | 4,096 definitions |
| Research rules | 4,096 definitions |
| Selector terms per selector | 256 |
| Ingredient types per research cost | 6 |
| Items per ingredient type | 64 |
| Direct prerequisite IDs per rule | 64 |
| Prerequisite graph depth | 64 |

These are safety ceilings, not recommended balance values. Codecs, NBT loading,
packet decoding, commands, and runtime transactions must enforce the same
constants rather than defining independent limits.

## Persistence migration contract

The first persistence implementation will add:

```text
DataVersion: 1
DiscoveredBlueprints: string list
ResearchPoints: integer
```

Loading NBT without `DataVersion` is migration from version 0:

1. Read and normalize `Recipes` and `Blueprints` exactly as version 1.0.4 does.
2. Preserve both lists for forward and downgrade compatibility.
3. Copy valid learned `Blueprints` into `DiscoveredBlueprints`.
4. Migrate active legacy `Recipes` through the catalog and discover their output
   IDs when the catalog is available.
5. Initialize Research Points to zero.
6. Preserve unknown normalized IDs.
7. Write version 1 only after a complete in-memory migration.

Resetting learned recipes will not implicitly reset discovery or points. A new
explicit progression-reset command must identify which state is being removed.

## Deferred scope

Phase 0 and the initial 1.1.0 implementation do not require:

- timed or queued research;
- a Research Bench block entity;
- blueprint fragments;
- reverse engineering guns or attachments;
- team-shared unlocks or points;
- physical or third-party currencies;
- automatic personal loot rerolling;
- automation support;
- a complex prerequisite tree;
- public research integration APIs.

The contracts leave room for those features without making them implicit in the
first persistence or wire format.

## Phase 0 acceptance

Phase 0 is complete when:

- the feature branch is based exactly on `v1.0.4`;
- the baseline build, 50 tests, artifact verifier, and publication verifier pass;
- the rebuilt artifact matches the published checksum;
- dedicated-server and client startup probes reach their normal ready states;
- current persistence, networking, catalog, reload, and configuration contracts
  are recorded;
- discovery, duplicate, points, research, visibility, transaction, precedence,
  migration, and hard-limit contracts are explicit;
- `git diff --check` passes;
- no runtime source or resource behavior changes in Phase 0.
