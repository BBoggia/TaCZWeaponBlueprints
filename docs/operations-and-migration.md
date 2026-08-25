# Operations and Migration

## Supported runtime

Release 1.2.0 is built for Minecraft 1.20.1, Forge 47.x, TaCZ 1.1.8-hotfix, and Fzzy Config 0.5.9. Declared dependency ranges intentionally stop before TaCZ 1.2 and Fzzy Config 0.6 because those API lines have not been validated.

TaCZ 1.1.8 can inject a Model 943 revolver and ammunition into a world's optional bonus chest. Obtaining those items does not unlock their gun-smithing recipes; blueprint progression and server-side crafting enforcement continue to operate normally.

Install the blueprint mod and required dependencies on both client and server. TaCZ gun content packs remain normal TaCZ resources and do not need to be declared as Forge mods.

## Upgrade procedure

1. Stop the server cleanly.
2. Back up the world, player data, configuration, and custom datapacks.
3. Replace the mod JAR and confirm required dependency versions.
4. Start the server and wait for the blueprint catalog and loot snapshot messages.
5. Run `/gg loot status` and record the revision, catalog count, and distribution mode.
6. Run `/gg research status`, inspect representative roots and leaves, and optionally export the live catalog.
7. Inspect and preview representative loot tables before reopening the server to players.

The Research Bench tree does not change the persisted player-data schema.
Learned and discovered blueprint IDs, Research Points, and legacy recipe aliases
remain readable without a world conversion. The custom network protocol is
`15`, so clients and servers must update together. Protocol 15 transfers each
disclosure-safe research graph and its matching group presentation as one
bounded, atomic publication, hardens progression chunk generations, and uses
the inventory-only Research Bench preview shape. This is a network-only
compatibility change and does not alter learned blueprints, discoveries,
Research Points, or datapack formats.

Existing valid learned recipe IDs remain readable from the `Recipes` capability
list. When an ID matches any current recipe alias, it is migrated to a durable
blueprint output ID in the new `Blueprints` list and synchronized as the
catalog's deterministic canonical recipe. Both lists are retained so rollback to
an older release remains possible. IDs from an entirely removed content pack
remain persisted; reinstalling a pack that supplies the alias makes them
migratable again.

## Dynamic and legacy distribution

This release packages one dynamic modifier and 485 generated legacy modifiers.

- An enabled dynamic rule owns only its exact or selector-matched tables.
- A targeted disabled rule suppresses dynamic and legacy distribution for its targets.
- A disabled rule with no targets is the explicit global legacy opt-out.
- Tables not owned by any dynamic rule may continue using their legacy modifier.
- Pools without rules can be staged without changing existing loot.

This table-selective bridge supports incremental datapack migration. The legacy set is intentionally retained for the 1.0.x release line; removing it is a future breaking migration and should occur only after deployed packs have moved to dynamic rules.

## Safe datapack rollout

1. Add tags and pools first; verify they load with `/reload` and `/gg loot pool`.
2. Add a rule targeting one test table.
3. Use `/gg loot inspect` to confirm ownership and predicates.
4. Use `/gg loot preview` to confirm blacklists, effective defaults or overrides, and weights.
5. Expand exact targets or selectors after the test rule behaves as intended.
6. Use a targeted disabled override when intentionally suppressing one built-in policy.

Definitions with the same namespace and path replace lower-priority definitions. Different IDs are additive. A failed reload does not publish a partial snapshot; verify that `/gg loot status` retains the previous revision.

Research progression and presentation use three independent resource
directories:

```text
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
```

Roll out group resources after their referenced profile and rules. Group ranks
organize the UI only; they never replace prerequisite rules. After `/reload`,
use `research status`, inspect representative members, and run `research
export`. Export format 2 records authored placements, automatic-fallback
members, and authored IDs absent from the live TaCZ catalog. A malformed group,
missing profile, duplicate active-profile membership, or rank that contradicts
an effective prerequisite rejects the complete research reload and preserves
the last published research snapshot.

## Rollback

To roll back custom loot policy while keeping the current mod:

1. Remove or disable the higher-priority custom datapack.
2. Run `/reload`.
3. Verify built-in definitions and the new revision with `/gg loot status`.

To roll back the mod version, stop the server, restore the previous JAR and matching configuration/datapacks, then restore the backup if the older version cannot read newer state. Do not replace JARs while the server is running.

## Diagnostics

- `status` distinguishes dynamic, globally datapack-disabled, targeted-disabled, and legacy-fallback modes.
- `inspect` includes disabled ownership and global opt-out rules.
- `pool` reports flattened entries/selectors and current pre-blacklist candidates.
- `preview` applies the current catalog, predicates, defaults, overrides, and immutable blacklist snapshot without consuming RNG.
- `research status` audits rule assignment plus authored group coverage and missing members for the active profile.
- `research inspect` reports the selected rule, visibility, cost, prerequisites, and authored group placement for one live blueprint.
- `research export` writes a sorted format-2 authoring catalog with group and fallback metadata under the current world directory.

Third-party TaCZ content packs can contain malformed recipes, language files, models, sounds, or missing definitions. The blueprint catalog isolates invalid recipes and reports aggregate samples while preserving valid entries. Fix those resources in the originating content pack; they are not blueprint datapack failures.

## Release verification

Run with JDK 17:

```text
./gradlew cleanTest test build
./gradlew verifyReleaseArtifact
./gradlew verifyPublicationReadiness
./gradlew certifyReleaseCandidate
```

The artifact verifier rejects missing runtime classes, malformed packaged JSON,
mismatched modifier indexes, incorrect Minecraft/Forge/FML/TaCZ/Fzzy dependency
ranges, non-reproducible manifest timestamps, and unwanted cache or `.DS_Store`
entries. Publication readiness additionally verifies that the selected root
license is not the stock Forge MDK placeholder.
The final certification task records the verified JAR hash and exact dependency,
protocol, and test metadata in `build/reports/release-candidate.json`.
