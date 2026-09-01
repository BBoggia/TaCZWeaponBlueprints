# Release checklist

Use this checklist for every public build. Release commands require JDK 17.

## Prepare

- Confirm `mod_version` in `gradle.properties` and use the same version in the
  changelog and Git tag.
- Confirm `mod_license` agrees with the root `LICENSE.txt`.
- Update the changelog heading with the release version and date.
- Merge or rebase the latest `origin/main` before the final validation build.
- Confirm `git status --short` contains only intentional release changes.
- Review generated-resource additions and deletions, especially the global
  loot-modifier index.

## Validate

Run:

```text
./gradlew cleanTest build --warning-mode all
./gradlew certifyReleaseCandidate
```

The first command runs the tests, reobfuscates the mod JAR, and validates the
packaged metadata, JSON, loot-modifier index, and archive hygiene. The second
also rejects unresolved publication choices such as the stock Forge MDK
license. The selected root license is packaged into the verified JAR.
Certification rejects a non-JDK-17 runtime and writes
`build/reports/release-candidate.json`; retain that report with the exact JAR it
describes.

The candidate report also records the research-workstation ownership and
presentation contract: the permanent fullscreen research-only Bench, the
single-input Recycler and its three explicit actions, final eight-element
256x256 presentation, recipe discovery path, artifact gate, and still-required
manual-QA matrix.

Confirm `unifiedTechTree.viewOrder` contains only `tech_tree` and that
`dormantCompatibilityViews` lists `branches` and `all_weapons`. Neither dormant
view may be reachable from compact controls, the fullscreen rail, search,
recommendations, portals, publication fallback, or restored client state.

Confirm the candidate report records exact/category/subgroup progression
exemptions as live policy, exact idempotent starting grants as durable
knowledge, and the no-award grant policy. Exercise representative exemption
removal and starter-list removal before release; only the former should revoke
access, while neither may delete learned progress.

Confirm the candidate report records `journal_getting_started`, optional JEI
and EMI versions, generic-information-only viewer content, and `none` for both
recipe transfer and hidden catalog enumeration. Run the onboarding and viewer
presence/absence cases in the manual matrix; compile-only API success does not
prove optional runtime classloading.

Confirm the candidate report records the setup assistant as
`discovery_pacing_only`, with all four presets, preview plus explicit
confirmation, preserved custom values, unchanged player progression, and export
format 1. On a disposable world, assess an empty catalog and a loaded add-on
catalog, preview every preset, apply one with `confirm`, switch back to
`custom`, and verify the original custom values become effective again.

The build also runs `verifySharedLayoutKernelMigration`. It rejects any live
production reference to the two compatibility-only Research Tree engines,
missing shared-kernel source, or non-client registration of the visual layout
policy. Packaged-artifact verification separately requires every kernel and
configuration class plus all twelve localized setting surfaces.

The build also runs `verifyTaperedAutomaticTopologyContract`. It pins automatic
topology `tacz-gun-placement-v13`, Research Tree protocol 40, export format 18,
and the canonical-coordinate/decision/finalized-rank publication contract;
requires clean server planning, finalization, diagnostics, client layout,
network, and packaged-data suites; and writes
`build/reports/tapered-automatic-topology.json`. Confirm the JAR manifest and
release-candidate report contain the same tuple.

The build also runs `verifyAutomaticPublicationRecoveryContract`. It requires
clean health/recovery tests and localized operator diagnostics, pins publication
health contract `staged-failure-recovery-v1`, and writes
`build/reports/automatic-publication-recovery.json`. Confirm the report lists
all four publication states and all six rebuild stages, and that the JAR
manifest contains the same health contract.

The build also runs `verifyGroupedPrerequisiteAcceptanceContract`. It requires
all 20 named Phase 7 semantic and integration invariants to resolve to exact,
clean JUnit cases and writes
`build/reports/grouped-prerequisite-acceptance.json`. Confirm the report pins
`truth-tables-integration-v1`, protocol 40, export format 18, and
`grouped_routes_v1`, and that the JAR manifest and release-candidate report
contain the same acceptance contract.

The build also runs `verifyGroupedRouteRolloutContract`. It requires the Phase
7 acceptance report plus 20 exact Phase 8 rollout invariants and writes
`build/reports/grouped-route-rollout.json`. Confirm it pins
`grouped-routes-v1-rollout-v1`, records the grouped merge interval as ignored,
labels the group-aware route-balance guard plus its separate legacy-AND
compatibility guard, and keeps Weapons
enabled while Attachment and Ammunition research remain disabled. Confirm the
JAR manifest and release-candidate report contain the same rollout contract.

The build also runs `verifyGroupedRouteSelectionContract`. It consumes the
Phase 8 rollout evidence, requires 12 clean Phase 9 selection/economy/client
invariants, and writes `build/reports/grouped-route-selection.json`. Confirm it
pins `group-aware-route-balance-v1`, the grouped
`group_aware_route_balance_v1` guard, the separate legacy union guard, the
8.0x proven-imbalance threshold (with 4.0x retained as a p95 warning), protocol
37, and export format 18. Confirm the
JAR manifest and release-candidate report contain the same selection contract.

The build also runs `verifyGroupedRouteStabilizationContract`. It consumes the
Phase 7–9 reports, requires the exact Phase 10 migration, disclosure, packet,
cache, save-compatibility, scale, and viewport cases, and writes
`build/reports/grouped-route-stabilization.json`. Confirm it pins
`default-rollout-migration-v1`, records `grouped_routes_v1` as the packaged
default, retains explicit or omitted `legacy_and` as the compatibility
fallback, and never permits semantic fallback after grouped-generation
failure. Confirm the JAR manifest and release-candidate report contain the same
stabilization contract. Before public release, also complete the report's
manual acceptance items from `docs/research-tree-manual-qa.md` at normal and
maximum zoom-out.

The build also runs `verifyHybridRouteGenerationContract`. It requires the six
exact Phase 11 profile, scheduling, planner, canonical-cost, diagnostics/export,
and quality-audit cases and writes
`build/reports/hybrid-route-generation.json`. Confirm it pins
`deliberate-hybrid-generation-v1`, records `hybrid_routes_v1` as an explicit
opt-in while preserving `grouped_routes_v1` as the packaged default, limits
scheduled mandatory gateways to at most one per eligible transition rank, and
records export format 18. Confirm the JAR manifest and release-candidate report
contain the same hybrid-generation contract.

The build also runs `verifyGroupedVisualRefinementContract`. It requires the
exact Phase 12 clean-boundary, severe-underfill guard, capacity-forced split,
one-column bounded-state, dense shared-row, single-family/authored cohesion,
end-to-end responsive-family wrapping, ordinary and maximum valid
multi-junction clearance, branch-gutter, terminal, hybrid-authority, and
287/4,096-scale cases and writes
`build/reports/grouped-visual-refinement.json`. Confirm it pins
`branch-aware-visual-refinement-v1`, declares client-only visual geometry,
preserves semantic ranks and canonical AND-of-OR authority, retains the
tree-owned dynamic-width and 15% zoom contracts, and leaves
`grouped_routes_v1` as the packaged default. Confirm the JAR manifest and
release-candidate report contain the same visual-refinement contract. The
automated report does not replace the before/after Minecraft screenshots in
`docs/research-tree-manual-qa.md`.

For the unified Tech Tree, packaged-artifact verification reconstructs the
exact release data and requires a format-2 dynamic-band tree, a contiguous
explicit-rank 53-weapon bundle bounded to nine nodes per rank, preserved
format-1 opt-in placement data for 95 attachments and 24 ammunition types,
Glock 17/RK-6/9mm authored roots, same-domain monotonic prerequisites, complete
authored root reachability, correctly routed kind fallbacks, and all live or
data-referenced localization. It also requires the format-2 packaged profile
to publish/research Weapons only while retaining disabled Attachment and Ammo
domain policies. The automatic merge interval is validated as bounded policy,
not frozen to one release value; it is explicitly ignored by
`grouped_routes_v1` and remains active only where the legacy strategy/layering
classification says so. Format-2 layering has no required levels-per-tier
value. Do not certify from source-only JSON inspection; this
gate intentionally reads the reobfuscated JAR.

For the unified Research Tree release, artifact verification also requires the
exact 53-weapon recipe-backed connected default progression, preferred Glock 17 root, complete
seven-group membership, curated-overview defaults, runtime classes, and localized
UI surface. Runtime-log verification rejects Research Tree invariant exceptions
and crash reports that identify this mod even if the normal startup markers were
reached first.

For automatic add-on placement, artifact verification requires exactly one
built-in profile targeting the default tree in bounded `connected` mode with
format-2 dynamic layering, a two-weapon foundation, a population-resolved
tree-owned 9–20-node layer capacity, no configured bands, and
`place_connected` review handling, the pinned TaCZ 1.1.8 mechanical-v2 and
capability-v3 references and fingerprints, all runtime authority classes, and
nonblank operator diagnostics. Confirm the packaged format-4 profile selects
`capability_v3`, while omission remains the migration-safe `mechanical_v2`
default. Confirm the release report records formula `tacz-gun-capability-v3`,
reference `tacz-1.1.8-capability-v3`,
capability metric fingerprint
`2ab8c81e48fff1ba1a419c85423c5981e13fe9eeac8dba9f6f8e4170e2e42d89`,
placement `tacz-gun-placement-v13`, dynamic layering, the 4:3 population formula,
a configured 9–20 width range plus the baseline effective width, authored-slot
reservation, and a 15% manual zoom floor,
a branch-aware shared trunk, deterministic 100%–20% second-parent taper through
the lower three quarters and specialization, branch-local upper requirements,
third-parent-only legacy merge intervals, group-aware alternative-route balance
evidence plus legacy RP-union-closure rejection evidence,
two-occupied-rank automatic edges at normal catalog scale, a depth-safe
scale-aware bound at the 4,096-item ceiling, and progressive branch-envelope spacing,
adaptive one-to-three-member terminal clusters with full-metric safeguards,
bounded four-point score tolerance, and explicit truncation diagnostics,
family-preserving responsive row boundaries and bounded ten-pixel lanes for
additional any-of junctions,
tree-owned optional/dynamic/configured presentation bands, the 4,096-candidate
ceiling, protocol 40, export format 18, and packaged automatic prerequisite
strategy `grouped_routes_v1`. Confirm canonical branch coordinates
round-trip for every automatic member, two-family layouts receive a visible
gutter, planned and published ranks are reported separately, and complete
automatic semantic rows remain together when authored occupancy forces a lift.
Confirm generated any-of pairs converge through one diamond and one outgoing
arrow, singleton requirements retain direct arrows, selected-node details say
`Requires one of`, anonymous alternatives remain counts, and satisfaction-only
updates change gate state without invalidating cached layout geometry.
Confirm status and export also report the `distributions-warnings-v1` grouped-
route quality contract: effective alternatives, ancestry/cost distributions,
phase fan-out and family density, branch entries, single-route chains, and
terminal affordability. Observed warning counts are evidence, not release
thresholds.
Confirm the same surfaces expose the `evidence-gate-v1` motif decision: the
representative grouped fixture retains current routes, named motif prototypes
appear only for decisive semantic signals, incomplete authority cannot
authorize a prototype, and pre-junction crossings remain manual visual evidence.
For a current connected publication, status must report equal candidate,
canonical-coordinate, decision, and finalized-rank counts with `complete=true`.
For generation-redesign Phase 9, also confirm the export contains the
topology audit, per-weapon authoring evidence, and economy review; the packaged
weapon-only baseline is 418 RP against 218 finite RP, and costs remain under
`research_policy` authority with the automatic curve disabled.

For compatibility-sensitive changes, repeat the dedicated-server and client
smoke tests documented in `docs/operations-and-migration.md`.
Validate their complete logs with:

```text
./gradlew verifyRuntimeSmokeLog -PsmokeKind=client -PsmokeLog=<client-log>
./gradlew verifyRuntimeSmokeLog -PsmokeKind=server -PsmokeLog=<server-log>
```

For Journal/research releases, complete one integrated-server Research Bench
interaction pass:

- obtain a blueprint, Research Bench, and Research Data in separate fresh-player
  checks and confirm each path unlocks the same Blueprint Recycler recipe once;
- craft the Recycler and confirm the Research Bench is not consumed or required;
- place the Recycler facing north, east, south, and west; confirm the paper
  intake faces the player, the top/side/control textures remain correctly
  oriented, the inventory icon is centered, adjacent faces do not disappear,
  and its selection/collision outline matches the shaped model;
- place the bench with two horizontal spaces available, verify both parts face
  correctly, and confirm interacting with either part opens the same menu;
- break each half in separate tests and confirm the complete bench is removed
  with exactly one item drop; confirm pistons cannot split the bench;
- browse without exposing inventory slots, select an unlearned blueprint, and
  verify the exact RP and player-inventory material preview in its tooltip;
- confirm single and double clicks only select, the Research button sends one
  request while pending, and confirm the Bench exposes no tab or turn-in action;
- confirm insufficient points, missing ingredients, and unmet prerequisites
  consume nothing;
- in the packaged `DIRECT_LEARN` mode, complete one research transaction and
  confirm the recipe is learned immediately, no physical blueprint is created,
  and the exact RP/material cost is consumed;
- select an unlearned node several ranks above the learned frontier and confirm
  the button reports `Unlock N`, the preview reports aggregate RP and
  every displayed material count, and one confirmation learns the target plus
  the deterministic shortest prerequisite closure while charging shared nodes
  once; repeat without enough RP and without one material and confirm no node,
  point, recipe alias, or inventory slot changes;
- test mandatory any-of joins with shared prerequisites, one already learned
  route, one progression-exempt alternative, two equal-length routes with
  different materials, and one blocked alternative; confirm the globally
  shortest combined closure wins, an affordable equal-length route is
  preferred, learned/exempt routes cost nothing, remaining ties use stable
  economic then resource-ID order, and blocked alternatives are never silently
  traversed;
- preview a path with more than six material predicates in compact and
  fullscreen modes; confirm both report the additional material-type count and
  readiness still reflects the complete server allocation;
- temporarily select `CREATE_BLUEPRINT`, complete one compatibility transaction,
  and confirm exactly one normal physical blueprint is produced without
  learning until that item is used; restore `DIRECT_LEARN` afterward;
- open the dedicated Blueprint Recycler, recycle one learned duplicate, and
  confirm exactly one item is consumed for the complete configured award;
- place an eligible unloaded, attachment-free TaCZ item in the Blueprint
  Analyzer and confirm its logical target, physical count, RP cost, material
  counts, customization warning, and output readiness match server policy;
- reverse engineer once and confirm the exact physical count, RP, and materials
  are consumed, exactly one protected physical blueprint appears in the
  extract-only output, and discovery occurs without learning the recipe;
- repeat with an occupied output, stale inventory, insufficient ammo batch,
  loaded gun, and attached gun and confirm every state consumes nothing;
- close the Analyzer with unused input and unclaimed output and confirm both
  return exactly once;
- keep a bench open across `/reload` and confirm its preview refreshes while the
  next action uses the new policy; confirm the research tree publication also
  refreshes without briefly showing a partial graph.

Complete and retain the environment details and results from
`docs/research-tree-manual-qa.md`. This includes GUI scales 1 through Auto,
minimum window sizes, long translations, mouse/keyboard/narration, two players,
content-pack removal and restoration, protocol mismatch, model orientation, and
the required release screenshots.

For Tech Tree sign-off, record a 53-node Weapons publication and verify that
Attachment and Ammo selectors/nodes are absent under the packaged profile.
Learn one attachment and one ammunition blueprint physically to confirm their
non-tree route still works. Then use a disposable format-2 profile with those
domains enabled, record all three authored domain counts and entry nodes, and
exercise fullscreen switching and one research transaction in each re-enabled
domain. Automated certification does not mark these runtime boxes complete.

For automatic-placement sign-off, use one representative add-on weapon pack and
complete the `independent`, `distributed`, `connected`, reload, rollback, and
status/inspect/export agreement cases in the manual matrix. Maximum-fixture and
packaged-resource tests do not prove a real pack's TaCZ recipes or in-game
interaction behavior.

For Research Point economy sign-off, confirm the candidate report records 15
definitions, 46 fixed progression RP, a 1-RP once-per-blueprint discovery
award, a 218-RP pinned-catalog finite maximum, 1,246 RP of pinned research cost,
1/3/6 Research Data values, and combat disabled by default. Exercise one fresh
discovery, one retroactive advancement, one milestone crossing, each Research
Data tier, near-cap rejection, award disable/reenable, `/reload`, relog, and
restart. Also confirm a near-cap finite advancement or milestone remains
unclaimed until enough RP is spent and then pays its complete value. Open
representative note/report/dossier loot tables and confirm their
12%/8%/5% modifiers coexist with blueprint loot without duplicate global-index
entries.

## Publish

- Record the SHA-256 hash of `build/libs/taczweaponblueprints-<version>.jar`.
- Create an annotated `v<version>` tag on the exact validated commit.
- Push the commit and tag without force-updating an existing release tag.
- Create the GitHub release from that tag and use the changelog section as its
  release notes.
- Attach the reobfuscated JAR from `build/libs`; do not attach a development or
  sources JAR in its place.
- Download the uploaded asset and verify its SHA-256 against the local artifact.
