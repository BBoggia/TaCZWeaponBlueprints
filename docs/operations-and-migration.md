# Operations and Migration

## Supported runtime

Release 1.2.0 is built for Minecraft 1.20.1, Forge 47.x, TaCZ 1.1.8-hotfix, and Fzzy Config 0.5.9. Declared dependency ranges intentionally stop before TaCZ 1.2 and Fzzy Config 0.6 because those API lines have not been validated.

TaCZ 1.1.8 can inject a Model 943 revolver and ammunition into a world's optional bonus chest, but does not provide a gun-smithing recipe for that weapon. It is therefore not part of the recipe-backed blueprint tree. Glock 17 is the preferred shared research entry; if its recipe is removed, the server selects the first available configured pistol fallback.

Install the blueprint mod and required dependencies on both client and server. TaCZ gun content packs remain normal TaCZ resources and do not need to be declared as Forge mods.

Research Tree purchases use synchronized `treeResearchResultMode`. The packaged
`DIRECT_LEARN` value learns the recipe immediately without creating a physical
blueprint. `CREATE_BLUEPRINT` retains the former two-step result for a server
that needs a temporary compatibility window. Changing the setting requires no
world conversion and does not rewrite existing knowledge or physical items.

Synchronized progression exemptions are live access policy, not saved
knowledge. Exact IDs, coarse `gun`/`ammo`/`attachment` kinds, and TaCZ item
subgroups are additive and re-evaluated against the current catalog after
reload. An exempt target is craftable and satisfies prerequisites but is
omitted from research, blueprint loot, and Analyzer acquisition. Removing the
exemption restores normal progression unless the player independently learned
the target.

`startingBlueprints` is an exact-ID, additive knowledge grant. It is applied
idempotently on login, successful resource reload, and synchronized config
update. Removing a configured starter never revokes it, and repeated callbacks
do not issue Research Point awards. Audit missing IDs and unmatched subgroup
selectors with `/gg research status` after changing packs or configuration.

## Balance presets and setup assistant

`balancePreset` defaults to `CUSTOM`, preserving existing configuration and
upgrade behavior. `ACCESSIBLE`, `BALANCED`, and `SCARCE` are reversible
overlays for maximum undiscovered visibility plus the global default blueprint
loot chance and roll range. Per-rule datapack overrides continue to win. A
preset does not alter the active research profile, costs, prerequisites, RP
economy, blacklists, exemptions, starting grants, or any player's learned and
discovered IDs.

Operators can use this preview-first flow after installing or changing TaCZ
content packs:

1. Run `/gg research setup assess` after the catalog is available.
2. Resolve any reported research-structure warnings; a pacing preset cannot
   repair missing prerequisites or competing rules.
3. Run `/gg research setup preview <custom|accessible|balanced|scarce>`.
4. Apply only with `/gg research setup apply <preset> confirm`.
5. Optionally run `/gg research setup export`; the deterministic format-1 file
   is written to `<world>/taczweaponblueprints/setup-assessment.json` and
   contains no player knowledge.

The assistant subtracts effective progression exemptions and valid starting
blueprints before evaluating discovery workload. It recommends `ACCESSIBLE`
when at least 240 effective entries or 96 effective non-TaCZ entries remain,
otherwise `BALANCED`. Disabled blueprints block readiness; disabled research or
unavailable loot distribution produce explicit review warnings. It never
automatically applies a choice and never recommends `SCARCE`. Switching to
`CUSTOM` restores the saved custom values because named presets do not rewrite
them.

## Upgrade procedure

1. Stop the server cleanly.
2. Back up the world, player data, configuration, and custom datapacks.
3. Replace the mod JAR and confirm required dependency versions.
4. Start the server and wait for the blueprint catalog and loot snapshot messages.
5. Run `/gg loot status` and record the revision, catalog count, and distribution mode.
6. Run `/gg research status`, inspect representative roots and leaves, and optionally export the live catalog.
7. Run `/gg research setup assess` and preview the recommendation; do not apply it until any structural warnings are understood.
8. Inspect and preview representative loot tables before reopening the server to players.

The Research Point award foundation advances persisted player progression from
data version 1 to 2 by adding a bounded, server-only `ResearchPointAwards`
ledger. Version-1 saves migrate automatically with an empty ledger; learned and
discovered blueprint IDs, Research Points, and legacy recipe aliases remain
unchanged and require no world conversion. The ledger is copied across every
player clone and is not sent to clients. The custom network protocol is
`36`, so clients and servers must update together. Protocol 36 transfers each
disclosure-safe research graph, matching group presentation, and optional
identity-safe Tech Tree metadata as one bounded atomic publication. It retains
the server-resolved curated-overview flag and the coarse gun/ammo/attachment
kind used by research selectors, plus bounded Tech Tree rank, long sibling
order, optional visual-band references, placement-origin metadata, an ordered
bounded table of custom band labels, and the tree-owned resolved 8–28 node
layer capacity.
It retains hardened progression chunk generations and uses a research-only,
live-inventory Research Bench preview; Blueprint Analyzer previews independently carry
duplicate, Research Data, and physical-item reverse-engineering decisions plus an
opaque workstation-state token. It correlates Research Bench
selection/research results and sends
bounded disclosure-filtered RP help plus aggregated award feedback. This
compatibility change does not alter learned
blueprints, discoveries, Research Points, or the required format number of
existing research-tree group datapacks. Tech Tree resources remain optional;
missing or unusable presentation data hides the optional view without changing
the established Branches or All Weapons graph.

Cross-domain Tech Tree navigation adds no packet or migration field. Clients
derive one immutable reciprocal relationship index from the graph and placement
metadata after the current atomic publication completes. A portal is usable
only while its exact endpoints and direction remain in that current index.

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
data/<namespace>/taczweaponblueprints/research_tech_trees/<path>.json
data/<namespace>/taczweaponblueprints/research_tech_tree_entries/<path>.json
data/<namespace>/taczweaponblueprints/research_automatic_placement_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_point_awards/<path>.json
```

Roll out group resources after their referenced profile and rules. Group ranks
organize the UI only; they never replace prerequisite rules. After `/reload`,
use `research status`, inspect representative members, and run `research
export`. Export format 12 retains the format-2 research and presentation fields
and the revision-matched automatic-placement summary and per-weapon
decision: authored, automatic, excluded automatic, or legacy-compatible
unplaced. Automatic entries
record their score, confidence, semantic position, generated rank, optional
band, version identities, and planned prerequisite list or omission reason.
Format 8 added live topology/economy audits and per-weapon parent, role
similarity, fan-out, merge, and review evidence. Costs remain owned by research
policies; the report does not derive them from ranks or tiers.
Format 9 adds the topology weapon population, effective layer width, width
mode, and configured minimum/maximum width so an operator can reproduce the
server's dynamic-width decision.
Format 10 adds the exact branch-aware prerequisite decision captured by the
planner, parent-family relationships, transition boundaries, terminal/depth
flags, and an aggregate branch-prerequisite summary. It replaces heuristic
merge labels in the authoring report without changing prerequisite authority.
Format 11 adds the deterministic second-parent quota, eligibility result, and
exact RP-closure costs whenever an optional merge is rejected for inflation.
The legacy singular planned-
prerequisite field and `excluded_fallback_count` summary remain as compatible
aliases. Format 4 introduced `excluded_automatic`, format 5 added dynamic rank
and band coordinates, format 6 added tree-owned layer capacity, format 7 added
`tech_tree_presentation` with the tree-owned band mode and configuration, and
format 8 adds the diagnostic and economy review sections, format 9 adds
configured/effective width evidence, format 10 adds exact branch decision
provenance, format 11 adds gradual-merge and closure-guard evidence, and format
12 separates planned rank indexes from finalized publication ranks. Format 12
also adds an automatic `publication` summary with exact canonical-branch,
decision, and finalized-rank counts and a combined completeness result. A false
result is actionable for a current connected publication; legacy compatibility
plans report unavailable branch coordinates instead of fabricating them. Strict
format-3 through format-10 consumers must explicitly
migrate instead of receiving new fields or enum values under an older schema.
Tech Tree-only members still record
their domain/lane/tier/order, and groups still record authored IDs absent from
the live TaCZ catalog. A malformed group,
missing profile, duplicate active-profile membership, or rank that contradicts
an effective prerequisite rejects the complete research reload and preserves
the last published research snapshot.

Permission-level-2 operators may grant RP with
`/gg progression points give <targets> <amount>`. Grants respect the live
`researchPointCap`, synchronize successful balances immediately, and leave
learned/discovered blueprint state unchanged.

Research Point award resources reload independently from research progression
and loot. A malformed award file, conflicting shared-budget declaration, or
hard-limit violation rejects only the candidate award publication and keeps
its previous revision active. After adding or changing award data, run
`/reload`, then `/gg research awards status`. Use
`/gg research awards inspect <definition_id>` to confirm the selected trigger,
group, priority, points, overflow, repeat policy, profile scope, budget, and
target-term count. Normal resource-pack priority replaces the same definition
ID; different definition IDs are additive. Use
`/gg research awards sources` to list optional-mod integration event IDs and
`/gg research awards trigger <targets> <source>` as the permission-level-2,
repeat-safe command-function bridge for datapack-authored `integration`
definitions. Advancement completion, first
blueprint discovery, first learning, and filtered discovery/learning milestones
now dispatch this data. Definitions must explicitly opt into retroactive login
and reload catch-up. `enableResearchPointAwards` disables only these datapack
awards. `entity_killed` definitions additionally require
`enableCombatResearchPointAwards`, which remains off by default. Combat rules
fail closed for missing spawn provenance unless explicitly authored otherwise;
their safe defaults reject indirect, fake-player, pet, PvP, baby, tamed,
spawner, bred, and summoned sources. Neither setting
changes duplicate recycling, trusted operator grants, balances, or saved award
history.

### Built-in Research Point economy

The bundled publication contains 15 enabled definitions. A first discovery of
any current-catalog blueprint grants 1 RP once for that blueprint, including
retroactive reconciliation. Discovery thresholds at 10 and 25 and learned
thresholds at 5, 15, and 30 grant 18 finite RP in total. Acquire Hardware,
Diamonds!, A Terrible Fortress, Into Fire, The End?, and Free the End grant a
further 28 finite RP and reconcile for existing players. Every bundled finite
reward uses `require_full`: when its full value cannot fit below the RP cap, the
claim remains unconsumed and the reconciliation scheduler retries it after the
player spends RP.

For the pinned TaCZ 1.1.8 catalog, the maximum first-discovery plus fixed
milestone/advancement income is 218 RP against 1,246 RP of complete default-tree
research cost. It is intentionally supplemental; duplicate recycling,
exploration, and pack-authored sources remain relevant.

Research Notes, Reports, and Dossiers redeem for exactly 1, 3, and 6 RP with
`require_full` overflow, so a near-cap balance never consumes an item for a
partial award. Notes have a 12% chance in abandoned-mineshaft, simple-dungeon,
and pillager-outpost chests. Reports have an 8% chance in stronghold-library,
woodland-mansion, and bastion-treasure chests. Dossiers have a 5% chance in
ancient-city and End-city-treasure chests. These eight
`taczweaponblueprints:research_data/*` global loot modifiers can be replaced or
removed by a datapack independently of the award values. The global RP award
kill switch also prevents their built-in loot injection while disabled.

No built-in `entity_killed` or `integration` award is shipped. Combat remains
an explicit pack-author choice and still requires the separate combat kill
switch.

The progression reset command accepts `learned`, `discovered`, `points`,
`awards`, and `all`. The first three preserve RP award history. `awards` clears
only claim/cooldown/window/budget history, while `all` also clears that ledger
as part of a complete progression reset. Point spending, datapack removal, and
configuration changes never clear award history.

The built-in profile assigns exact research policies to the complete
recipe-backed TaCZ 1.1.8 catalog: 53 weapons, 95 attachments, and 24 ammunition
types. Profile format 2 now activates only the 53-weapon tree by default;
attachment and ammunition rules, placements, and RK-6/9mm entry candidates
remain authored but dormant. The legacy Branches and All Weapons projections
remain weapon-only. Servers can opt either dormant domain back in with a
format-2 profile whose matching `domain_policies` entry is enabled.
Adding these prerequisite edges does not change persisted learned IDs, so
servers do not need a progression migration and already learned content remains
learned. Disabled domains do not resolve or rebase entry candidates. Once a
domain is enabled, its ordered candidates again select the first usable
fallback. Existing format-1 profiles retain their previous all-domain behavior.

Existing Tech Tree resources remain format 1 compatible. The built-in active
weapon bundle now uses format 2 with 53 explicit placements on contiguous
ranks 0–12. Its prerequisite graph, costs, lanes, sibling order, and manual
authority are unchanged. The 95 attachment and 24 ammunition placements remain
authored in format 1 and can be enabled by an opt-in format-2 profile. Lane and
sibling metadata is still accepted and remains useful as a deterministic authoring
hint, but the client no longer promises one visible lane, column, or box per
classification. A custom pack that depended on the old lane-shaped layout
should validate its prerequisite topology and visual result before rollout;
costs and unlock authority continue to come only from research rules.

Research Tech Tree entry format 2 adds a required explicit `rank` to every
placement. Rank, not tier/level/order, is the dependency-order authority, and
every prerequisite must have a strictly smaller value. Format-1 resources must
omit `rank`; the server converts their tier/level positions with a wide stride
and topologically lifts legacy same-position dependents without changing their
rules, costs, or stored progression. Format-2 equal/backward edges reject the
reload rather than being repaired. Protocol 36 publishes rank, sibling order,
an optional visual-band reference, its bounded label table, and the tree-owned
layer capacity. The client
compresses sparse ranks into contiguous visual rows; band metadata cannot
change vertical dependency order. Client rows are capped by the tree's resolved
8–28-node policy, with additional presentation-only wrapping allowed for unusually
narrow embeddings. Empty bands and rows without cross-domain links reserve no
portal geometry. Disconnected graphs remain visible on the rank canvas and are
reported through stable layout component diagnostics instead of being hidden
by component-grid packing.

Automatic-placement profile format 1 remains backward compatible, including
its six score tiers and `levels_per_tier` behavior. Omitting `review_handling`
uses `exclude`, so warning-bearing estimates retain their legacy fallback
position. Format 2 creates contiguous stat-sorted ranks under the selected
format-2 tree's layout policy. A fixed policy supports 8–28 nodes. A dynamic
policy resolves `ceil(sqrt(4 × (authored + eligible automatic weapons) / 3))`
inside its configured minimum/maximum (the built-in range is 9–20), and may
define an ordered set of custom score bands or omit bands entirely. The legacy
automatic-profile width remains readable for format-1-tree compatibility. The
bundled tree uses format 2,
`connected`, `place_connected`, at most two generated prerequisites, a
two-weapon foundation, a configured bounded merge interval of four, a dynamic
9–20-node layer range, and dynamic three-rank presentation bands. It omits the legacy
`levels_per_tier` field because format-2 rank count is dynamic. Its lower ranks
form a shared multi-parent mesh; second-parent opportunities then taper
deterministically through the transition and remain possible at a bounded
branch-local floor toward the one-to-three-member terminal cohorts. If TaCZ cannot expose
usable runtime evidence for an add-on gun, the bundled policy assigns an
explicitly review-marked conservative band based on its weapon type and a
stable ID hash instead of placing every such gun at Basic level zero.

Custom profiles may choose `review_handling: "exclude"`,
`"place_independent"`, or `"place_connected"`. The middle option publishes the
reviewed tier/level but never creates a generated prerequisite for it and never
uses it as an anchor for another generated edge; the last allows reviewed
proposals to participate in connected-mode planning. The
profile `mode` still controls the overall capability: `independent` publishes
no automatic positions, `distributed` publishes positions without generated
edges, and `connected` may publish both. Roll authority changes out against a
world copy and verify the tree after `/reload`; stale or unsafe plans fail open
to the authored/legacy policy instead of stranding the add-on weapon.
Use `/gg research status` to confirm the automatic mode and revision pair, then
`/gg research inspect <blueprint>` to compare its effective policy with the
automatic proposal. For a current connected tree, the publication line should
report complete candidate/branch-coordinate/decision/finalized-rank coverage.
Planned prerequisites may be suppressed at runtime by a
blacklist or visibility ceiling; the effective policy prerequisite count is
authoritative.

Before enabling a changed Tech Tree on a public server, test it against a copy
of the world, run `/gg research status` and `/gg research export`, then keep a
bench open through `/reload`. Verify that unavailable domains disable in place,
the selected domain falls back safely if removed, and restoring the pack
restores the same domain slot. A rejected reload must leave the prior tree and
player progress active.

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
- `research export` writes a sorted format-12 authoring catalog with group,
  fallback, revision-matched automatic-placement, topology, configured/effective
  width, per-weapon decision, and economy evidence under the current world directory.
- `research awards status` reports the independent last-known-good RP award
  revision, definition/group/budget/index counts, trigger totals, and the last
  rejected reload without disclosing player ledger history.
- `research awards inspect` reports one strict award definition's normalized
  resolution and accounting policy without evaluating or granting it.
- `research awards sources` lists bounded stable integration event IDs
  registered by installed server mods.
- `research awards trigger` invokes the current matching integration
  definitions and reports per-status outcomes without accepting a point amount.

Third-party TaCZ content packs can contain malformed recipes, language files, models, sounds, or missing definitions. The blueprint catalog isolates invalid recipes and reports aggregate samples while preserving valid entries. Fix those resources in the originating content pack; they are not blueprint datapack failures.

JEI and EMI are optional client integrations. A server should not install them
just for this mod. When either viewer is present, TaCZ Weapon Blueprints adds
generic localized item information only; the current costs and available
research actions still come from the Research Bench or Blueprint Analyzer. If
an optional-viewer startup fails, capture a complete client log and verify the
installed viewer is within JEI 15.x or EMI 1.1.x before treating it as a
blueprint catalog or world-migration problem.

## Release verification

Run with JDK 17:

```text
./gradlew cleanTest test build
./gradlew verifyTaperedAutomaticTopologyContract
./gradlew verifyAutomaticPublicationRecoveryContract
./gradlew verifyReleaseArtifact
./gradlew verifyPublicationReadiness
./gradlew certifyReleaseCandidate
```

The artifact verifier rejects missing runtime classes, malformed packaged JSON,
mismatched modifier indexes, incorrect Minecraft/Forge/FML/TaCZ/Fzzy dependency
ranges, non-reproducible manifest timestamps, and unwanted cache or `.DS_Store`
entries. Publication readiness additionally verifies that the selected root
license is not the stock Forge MDK placeholder.
The tapered-topology gate writes
`build/reports/tapered-automatic-topology.json` and rejects drift among the
placement version, protocol, export format, required regression suites, and
packaged manifest. The publication-recovery gate writes
`build/reports/automatic-publication-recovery.json`, requires the localized
`/gg research status` health surface, and rejects drift in the four-state,
six-stage `staged-failure-recovery-v1` contract. The final certification task
records the verified JAR hash and exact dependency, protocol, test,
unified-tree, and automatic-placement contract metadata in
`build/reports/release-candidate.json`. The automatic section must record the
bundled `connected` mode with `place_connected` review handling, the three
version identities, dynamic layering with a configured 9–20-node range and its
baseline resolved width, optional
tree-owned optional/dynamic/configured presentation bands, the 4,096-candidate
limit, protocol 36, and export format 12 unless the
corresponding contract and compatibility documentation are deliberately
revised.
The report's research-workstation split must record the research-only permanent
fullscreen Bench, dedicated one-input Recycler, exact action ownership, final
model/texture contract, recipe discovery route, and manual-QA evidence path.
