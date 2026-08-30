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
topology `tacz-gun-placement-v12`, Research Tree protocol 36, export format 12,
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

For the unified Tech Tree, packaged-artifact verification reconstructs the
exact release data and requires a format-2 dynamic-band tree, a contiguous
explicit-rank 53-weapon bundle bounded to nine nodes per rank, preserved
format-1 opt-in placement data for 95 attachments and 24 ammunition types,
Glock 17/RK-6/9mm authored roots, same-domain monotonic prerequisites, complete
authored root reachability, correctly routed kind fallbacks, and all live or
data-referenced localization. It also requires the format-2 packaged profile
to publish/research Weapons only while retaining disabled Attachment and Ammo
domain policies. The automatic merge interval is validated as bounded policy,
not frozen to one release value, and format-2 layering has no required
levels-per-tier value. Do not certify from source-only JSON inspection; this
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
`place_connected` review handling, the
pinned TaCZ 1.1.8 mechanical reference and fingerprints, all runtime authority
classes, and nonblank operator diagnostics. Confirm the release report records
formula `tacz-gun-mechanical-v2`, reference `tacz-1.1.8-mechanical-v2`,
placement `tacz-gun-placement-v12`, dynamic layering, the 4:3 population formula,
a configured 9–20 width range plus the baseline effective width, authored-slot
reservation, and a 15% manual zoom floor,
a branch-aware shared trunk, deterministic 100%–20% second-parent taper through
the lower three quarters and specialization, branch-local upper requirements,
third-parent-only merge intervals, RP-closure inflation rejection evidence,
bounded same-family depth shortcuts, and progressive branch-envelope spacing,
adaptive one-to-three-member terminal clusters with full-metric safeguards,
bounded four-point score tolerance, and explicit truncation diagnostics,
tree-owned optional/dynamic/configured presentation bands, the 4,096-candidate
ceiling, protocol 36, and export format 12. Confirm canonical branch coordinates
round-trip for every automatic member, two-family layouts receive a visible
gutter, planned and published ranks are reported separately, and complete
automatic semantic rows remain together when authored occupancy forces a lift.
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
