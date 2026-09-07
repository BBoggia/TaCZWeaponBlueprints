# Research Tree baseline tooling

The development baseline tools replay the exact typed Tech Tree input used by
the client. They support comparisons of layout changes without regenerating a
different prerequisite graph for each run.

The tools live in `src/development/java`, are available to tests, and are added
to the Forge client run only when explicitly enabled. Release artifact
verification rejects their classes in the mod JAR. They do not change research
rules, packets, saved progression, or the normal Research Bench.

## Replay a recorded projection

Use JDK 17 from the repository root. The offline option assumes dependencies
have already been resolved in the local Gradle cache.

```sh
./gradlew --offline researchTreeBaseline \
  -PresearchTreeBaselineInput=src/test/resources/research-tree-baseline/live-weapons-287.json \
  -PresearchTreeBaselineReport=build/reports/research-tree/live-baseline.json
```

Without `researchTreeBaselineInput`, the task runs the hand-designed structural
fixtures. Their prerequisite groups and ranks are specified directly; they do
not call the automatic topology generator.

```sh
./gradlew --offline researchTreeBaseline
./gradlew --offline test --tests '*ResearchTreeBaselineTest'
```

Each report identifies the input by a canonical SHA-256, records the effective
layout policy and viewport width, and includes counts, final node positions,
edge paths, and requirement junctions. Replayed graph and projection data pass
through production validation before measurement. Unsupported format versions
and inconsistent edges/groups are rejected.

The report measures these operations separately:

- Complete current Tech Tree node placement and layout metadata.
- Edge routing and edge-index construction together; the current implementation
  exposes them through one operation.
- Node-index construction.
- A fixed 120-step sequence of camera transforms, visibility queries, junction
  queries, and pointer hit tests.

There is one separately recorded first iteration, five warmup iterations, and
20 measured iterations per domain. Median is the ordinary midpoint median;
p95 uses the nearest-rank definition. Repeated iterations must produce equal
layout and edge geometry. No test asserts a machine-specific speed threshold.

The first iteration is the first for that fixture in the process, not a claim
of a cold Minecraft startup. Camera/query timings exclude painting, texture
loading, world rendering, frame pacing, and GPU execution. The tool does not
measure retained memory or allocation rate. Use a profiler when those become
relevant; a heap-size setting is not a memory-usage measurement.

## Capture native client input and timing

```sh
./gradlew --offline runClient \
  -PresearchTreeBaseline=build/reports/research-tree/native \
  -PresearchTreeBaselineMotion=true
```

Open a test world and the Research Bench, then leave it open without input for
at least 30 seconds. The observer records a nonempty canvas only when it
matches a domain in `ClientResearchState`'s current public publication.

The optional motion flag runs a repeatable camera-only workload: 60 warmup
frames, 120 stationary frames, 120 pan frames, and 120 zoom frames. It restores
the camera on completion, early closure, or error only while the captured view
and publication still match. It does not select a node or send research actions.
Reopening the Bench starts a new
timestamped capture. Close the test client after collecting evidence when a
quiet CPU replay comparison is needed.

Omit the motion flag for observation without scripted camera movement. In that
mode, later frames are labelled `manual_interaction`; the label does not prove
that a user actually panned or zoomed.

Each timestamped directory contains:

- `projection.json`: the permitted typed domain inputs, including nodes,
  groups, bands, boundary links, source ordinals, and automatic branch hints.
- `runtime.json`: publication/input binding, active domain, screen and
  framebuffer dimensions, GUI scale, frame limit, VSync, camera, layout policy,
  dependency versions, opening interval, sample counts, and completion status.
- `screenshots/`: native framebuffer captures of the opening view and completed
  stationary, pan, and zoom intervals.

Opening time is measured from Forge's screen-opening event to the first
nonempty matching rendered Bench frame. The recurring native interval surrounds
the whole Research Bench screen render through Forge screen events. It includes
other screen-render event subscribers inside those boundaries, excludes world
rendering and frame wait, and does not wait for GPU completion. It is not an
isolated canvas or whole-game frame-time measurement.

Capture/serialization and screenshot work happens outside measured screen
render intervals. It can still perturb the following frame; inspect outliers
and repeat when investigating small timing differences. Warmup timings remain
separate from steady measurements. The observer checks canvas, graph, and layout
identity, canvas bounds, screen/framebuffer size, effective GUI scale, frame
limit, and VSync before and after each measured render. A changed view ends the
sample without restoring an old camera into the replacement view. Completion,
closure, and failure release retained screen/canvas references.
A `screen_closed_early`, `view_changed`, `publication_changed`, or `failed`
status is incomplete evidence, even if the
projection file is usable.

Camera snapshots use the existing viewport's target pan/zoom API. The opening
frame can still be easing toward that target. Use the settled stationary
screenshot for a stable comparison; do not infer exact opening-frame positions
from a target snapshot.

Capture the same catalog, disclosure state, selected target, viewport, GUI
scale, settings, and view when comparing native screenshots. Record the commit
and any uncommitted implementation alongside the artifact. A named capture
alone does not identify source changes or prove visual quality.

## Fixture coverage

| Fixture | Nodes / edges | Purpose |
| --- | --- | --- |
| empty | 0 / 0 | No available typed domain |
| single | 1 / 0 | Smallest nonempty domain |
| independent_roots | 4 / 1 | Disconnected components and different starting ranks |
| unequal_branches | 13 / 12 | Three branches of unequal length after a shared base |
| nested_split_merge | 10 / 11 | Two nested regions, parent-owned merge, later shared milestone |
| region_return | 3 / 2 | A1 → B2 → A3; region connections cannot be assumed acyclic |
| sparse_ranks | 3 / 2 | Ranks 0, 400,000, and 1,000,000 |
| and_of_or | 4 / 3 | D requires (A or B) and C |
| external_unknown | 2 / 1 | A local alternative, an external alternative, and an unknown alternative |
| fan_in_out | 18 / 32 | Shared root, sixteen arms, and a grouped merge |
| long_crossings | 20 / 20 | Ordinary branch edges and long cross-branch connections |
| connected_512 | 512 / 992 | Connected sparse graph with repeated joins |
| dense_256 | 256 / 3,840 | Valid edge-heavy graph with sixteen-way alternative groups |
| maximum_nodes_edgeless | 4,096 / 0 | Placement input at the node limit |
| maximum_edges | 1,088 / 65,536 | Valid edge-limit input with 1,024 groups of 64 alternatives |
| live-weapons-287.json | 287 / 454 | Recorded client input with seven automatic families |

The two maximum fixtures have explicit constructors and validation tests but
are excluded from the default repeated benchmark. Constructing the maximum
edge graph does not establish that its routing/rendering is fast. Use an
explicit stress experiment before making that claim.

`BranchIntent` records express expected structural cases only. They do not
change production inputs or implement a new authoring format. The current
engine receives the recorded projection without those intentions; a future
engine can use the same fixture's expectations to assess branch separation.

## Maintaining comparison evidence

Keep raw captures, profiling output, plan progress, and machine-specific
timings in ignored local development notes. Promote only sanitized replay
inputs needed by tests into ordinary test resources, with provenance and a
stable fingerprint. Do not make tests depend on a developer's local capture
directory.

The recorded input preserves public availability and progress at capture time.
It is not a default player state. Identical topology with different player
state is a separate cache/interaction comparison.

Review related contracts in `ResearchTreePresentationContract`, layout/index/
viewport tests, `build.gradle`, the [manual QA matrix](research-tree-manual-qa.md),
and the [width contract](research-tree-dynamic-width.md). Changing a rendering
contract requires updating its tests and documentation together; this baseline
tooling itself changes none of those contracts.

For comparisons through the immutable scene API, see the
[scene boundary and engine comparison commands](research-tree-scenes.md).
