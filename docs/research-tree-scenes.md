# Tech Tree scene boundary

Audience: developers integrating or replacing the Research Bench layout.

The scene API separates current public research information from reusable geometry.
The Research Bench's Tech Tree uses the validated branch scene through
`ResearchTreeCanvas.setSceneContent`. `ResearchTreeSceneRenderer` paints compact
tiles, prerequisite junctions, routes, and branch regions. The screen retains
selection, preview, research, and gesture orchestration. An opt-in development
task also compares three scene engines without opening Minecraft.

## Research Bench consumer

`ResearchTreeWorkbenchLayout` owns the persistent header, canvas, inspector,
action row, and shared search/navigation overlay geometry. The inspector appears only while a valid research node is selected; clearing
the selection returns its space to the canvas. Its hidden area does not block
canvas clicks or scrolling. Resizing the canvas preserves the camera anchor.
The inspector docks
right at GUI widths of 420 or more and below the canvas at smaller supported
sizes. Its requirement list scrolls independently; actions remain fixed. The
minimap respects its existing setting and avoids the central target area. It
yields when no corner fits, and contains only disclosed compact marks and region
outlines.

The production policy measures a 96-unit tile width and two native-font name
lines before layout. Window dimensions do not enter this policy: resizing updates
screen rectangles and camera bounds without packing the graph again. Overview
uses compact state marks only at very small tile sizes; an intermediate
artwork view keeps weapon identities visible while labels are hidden.
Zoom buttons and wheel input share the scene zoom limits; button availability
uses the pending camera destination during animation.
TaCZ slot art is cropped and fitted within the tile; missing or unsupported art
uses the blueprint item. Physical blueprint item rendering is unchanged.

`ResearchTreeSceneView` normalizes the scene extent, including negative origins,
and supplies actual body/paint bounds. Its compatibility layout contains only
navigation centers, ordered by the public graph's IDs; those positions do not
define tile dimensions or route geometry. Painting, picking, proportional
pointer zoom, and minimap navigation share the canvas viewport transform. Body
hits take priority over neighboring hit padding, with stable distance/ID ties.

Scene cameras use the existing `ResearchTechTreeViewState` domain and surface
state, keyed by scene geometry. Progress refreshes reuse geometry and camera;
changed metrics preserve a surviving focused node's screen position, subject to
viewport bounds. Opening, Overview, Branch, and target navigation are explicit
camera actions. Preview refreshes do not reframe the target.

The inspector requires a matching authoritative preview for exact costs,
material allocation, and research eligibility. The existing action controller
retains pending-action, click/drag/hold, and cancellation behavior. Tier metadata
is descriptive: a displayed blueprint tier does not itself block research.
The server's access summary supplies the blocker. Crafting access and Blueprint
Fragment progress appear separately. Every public prerequisite group and visible
alternative remains available in the scrolling content; undisclosed alternatives
remain counts.

Search, Next, tracked goals, affordability, keyboard navigation, and domain
portals use the current public projection. Focus uses gold outlines; inspected
selection has a white corner, hover a white border, active search a cyan outer
outline, and tracked goals a cyan cross. Routes preserve individual OR alternatives;
only the chosen tracked edges are emphasized as the tracked route. Affordability
can dim unrelated choices, but never moves them or hides inspected context.

Search and scrollable help draw above the inspector and consume input in that
same order. Existing staged Escape, Tab, domain navigation, and narrator content
remain connected. Help explains the scene's controls and marks. Reduce Motion,
background grid, minimap mode, hold duration, and sidebar/help preferences remain
client settings. Unused legacy layout keys are retained as deprecated data, not
repurposed. Client settings stay at version 3.

The screen reconciles publications before rendering or handling pointer/keyboard
input, as well as on ticks. Rejection clears projection/scene/art/text/search/pin
and route state instead of showing an old tree. The same rejected publication is
not rebuilt every frame; a new valid publication or reopening the Bench retries.
A primary scene failure may use a validated fallback with the required grouped
routing, otherwise it displays unavailable. The current simple fallback cannot
satisfy the grouped-routing contract, so such failures deliberately use unavailable.

Resource/language changes invalidate text and art caches once. Unchanged metrics
reuse geometry and camera; changed metrics preserve a surviving focus anchor.
Scene cameras do not depend on legacy row layouts. Domain loss removes its view
state; returning domains get current public content, while the preferred-domain
choice is remembered. A screen owns its caches for its open menu/session.

Preview receipt records the current publication identity before the next frame.
A publication replacement invalidates that preview until a fresh response arrives.
Existing menu/target matching and request IDs still apply; this client guard does not add a wire-generation field. The server rechecks
policy, costs, inventory, and the route fingerprint for every research action.
The normal ordered publication/preview delivery remains a protocol assumption:
`NetworkHandler` sends the complete Journal/tree publication before refreshing
the open menu. Point-only updates also request a bounded Research Bench preview
refresh, without rebuilding crafting access. Withdrawal and rejection clear the
server selection as well as the local preview; reselecting the same blueprint
therefore produces a fresh quote instead of a duplicate-selection acknowledgment.

## Input and geometry

`ResearchTreeSceneInput.from` accepts a validated `ResearchTechTreeProjection`.
It copies permitted node identities and placement hints, prerequisite edges,
AND-of-OR requirement groups, boundary links, ordered band identifiers, and the
domain's row limit. It does not read server definitions or extend disclosure.
Anonymous nodes stay excluded. Hidden alternatives stay counts; external counts
and individually disclosed boundary links remain separate information.

Nodes are ordered by identifier. Existing graph and projection constructors
canonicalize edges, groups, alternatives, and boundary links. Band order remains
meaningful. Graph table ordinals do not affect the scene key. Lane ordering and
published automatic family hints remain available to future layout engines;
they do not establish authored branch ownership by themselves.

`ResearchTreeScenePolicy` contains the existing spacing policy, viewport width,
node metrics, region allowances, and a metrics revision. Current profiles use a uniform body width,
height, and paint padding. The revision lets a resource/font measurement change
invalidate geometry even when its numeric dimensions happen to match. Future
rendering code must supply measurements before layout; engines do not access
fonts, textures, Minecraft widgets, or inventory state.

Explicit branch seeds are validated against permitted membership and enter the
geometry key. Production uses disclosed authored containment where provided, then
published automatic hints or neutral component grouping. See the
[authored branch guide](research-tree-authored-branches.md). See the
[branch allocation contract](research-tree-branch-layout.md).

The geometry key consists of the structural input, geometry policy, and engine
revision. Names, icons, rating details, costs, learned/discovered state, group
satisfaction, and availability do not enter it. Hover, selection, search, tracking,
and camera offsets are view state and are not scene inputs.

`ResearchTreeScene` contains:

- One node per permitted blueprint, with body and paint bounds. Hit bounds are
  derived from the body using view-supplied padding.
- Every requirement group, with its original dependent/ordinal identity,
  alternatives, and hidden/external counts. A compact junction position is optional.
- One orthogonal polyline per visible dependency edge. An edge can participate
  in several groups; rendering must consult the groups instead of interpreting
  independent edges as an AND expression.
- Boundary markers that aggregate only the same local node, remote domain, and
  direction, while retaining every individual disclosed link.
- Band content envelopes referencing current public labels by identifier.
  These envelopes are not measured label rectangles or branch regions.
- For the branch engine, a containment forest with owned node identities, measured
  region bounds, occupied ranges, and one shared table of occupied semantic ranks.
- Optional grouped routing, with measured junctions, ordered ports, group-owned
  connections, domain-marker connections, crossing records, and routing diagnostics.
  Render this network instead of the compatibility edge polylines when present.
- An extent containing node paint, route endpoints, compact junction markers,
  boundary markers, and band content. An absent domain has a zero-size extent
  and no synthetic node.

## Validation and limits

An engine produces a candidate. `ResearchTreeSceneValidator` must accept that
candidate before a consumer publishes or caches it. All engines use the same
validator. It checks the complete input key; exact node, edge, group, boundary,
and band membership; unique ownership of node identities; metrics; containment;
body overlaps; orthogonal routes; attached endpoints; and bottom-to-top dependency
progression. Compact junction footprints reserve an 8-by-8 envelope.

Derived containment, branch gaps, and rank allowances allow arithmetic rounding
within 0.000001 logical units. This prevents fractional measured cards from being
rejected at mathematically touching boundaries. Node metrics, identities, group
ports, and topology comparisons remain exact; measurable overlaps remain invalid.

Coordinates, dimensions, and derived bounds must be finite and remain within
1,000,000 logical units. Node counts, edge counts, and group counts inherit the
existing graph limits: 4,096 nodes and 65,536 edges/groups. Individual groups have
at most 64 alternatives. Scene routes have at most 64 points each and 1,048,576
points in total. Aggregate boundary links cannot exceed the graph edge limit.
Copies and validation loops are bounded by these limits. Body-overlap validation
uses at most the bounded node-pair count and exits early for separated rows.

The base checks establish structural validity for compatibility and simple scenes.
Branch scenes additionally pass containment, ownership, gap, ordering, rank,
painted-obstacle clearance, grouped connection membership, collinear-conflict,
and crossing checks. See the [routing contract](research-tree-routing.md) for its
stronger validation and budgets. These checks do not establish native readability
or visual acceptance; those require native Research Bench validation.

## Engines

`ResearchTreeBranchSceneEngine` measures a containment forest bottom-up and places
it top-down. It reserves distinct branch envelopes, supports nested internal seeds,
and uses a shared occupied-rank table without responsive row repacking. It measures
and routes grouped prerequisites through reserved region gutters. Full details
and limits are in the [branch allocation contract](research-tree-branch-layout.md)
and [routing contract](research-tree-routing.md).

`ResearchTreeCompatibilitySceneEngine` wraps the current
`ResearchTechTreeLayoutEngine` and `ResearchTreeEdgeIndex`. It canonicalizes the
public graph's table order within the adapter, then converts node positions,
routes, junctions, and boundary portals. The adapter's private projection must
match the geometry input. Geometry is recomputed from current public context
only when required, and the result contains no presentation text or player state.

The compatibility profile is a 24-by-24 body with four logical units of paint
padding for existing focus/search/tracking outlines. Other metrics are rejected;
the adapter does not claim that a larger card fits those coordinates. Old route
polylines end at the arrow base, so conversion includes the actual blueprint
endpoint. Band content envelopes and the resulting tight extent need not equal
the old canvas's padding or band drawing rectangles. Canonical graph ordering can
also change an old ordinal-dependent tie. The active renderer is unaffected.

`ResearchTreeSimpleSceneEngine` is an independent fallback. It uses compacted
rank order, one node per row, and separate outside tracks for edges. It supports
larger measured bodies without calling the current engine or shared layered
kernel. It retains all group semantics even though it does not place OR junctions.
It is intended for recoverable presentation, not as the branch layout design.
Large metrics combined with large catalogs can exceed the coordinate budget;
such inputs are rejected rather than clipped, truncated, or partially returned.
The simple engine does not provide grouped routing or its clearance guarantees;
it cannot satisfy a fallback for an engine requiring that stronger contract.

## Cache and publication lifecycle

`ResearchTreeSceneCache` is synchronous and confined to the client thread.
The Research Bench prepares only typed projections and unlock indexes, then binds
its scene cache to that prepared catalog. The legacy publication entry points keep
their compatibility builders for callers that explicitly request row layouts. Binding immediately drops geometry whose membership or topology
has changed. No scene generation runs on the ordinary rendering path.

Call `techTreeScene` with the exact current `ResearchTreePublication` object, a
domain, viewport width, metrics, and metrics revision. An older publication object
is rejected. A result contains current presentation separately from optional
geometry and reports one of three states:

| State | Meaning |
| --- | --- |
| `VALID` | The requested engine produced validated geometry, including an empty scene for an absent domain. |
| `FALLBACK` | The requested engine failed or rejected its metrics; the independent fallback passed the same validator. Diagnostics describe the failure. |
| `UNAVAILABLE` | Both engines failed, or scene input/engine preparation failed before a fallback contract could be established. Geometry is absent. |

Each domain retains at most one geometry entry. Matching state-only updates reuse
the immutable geometry instance and return fresh presentation. Metrics, spacing,
viewport, topology, engine revision, and grouped-routing requirement changes invalidate reuse. A matching
fallback stays cached until the key changes; the cache does not repeatedly retry
an already rejected primary engine during ordinary use. Unavailable results can
be retried by making another request.

Input and engine-factory failures are contained at the same boundary as layout
failures. They evict the affected domain's old geometry and return diagnostics;
they cannot silently switch an unknown engine contract to the simple engine.

Consumers retaining a result must use `isCurrentScene` before drawing or acting on
it. The check includes a per-domain request scope, publication generation/object
identity, and the currently retained geometry. Each new uncached request invalidates
that domain's earlier result, including an earlier `UNAVAILABLE` result. An actual
cache hit preserves its scope; requests for another domain leave it current.
Clearing the cache invalidates results across
disconnects and other scope resets, even if a later publication has equal content.
All presentation lookups must use the result's current projection. Scene keys are
not authorization to display names, icons, costs, or satisfaction from older data.

The Research Bench uses these publication guards before rendering and input.
There are no asynchronous layout jobs. A known simple fallback is skipped when
grouped routing is required, because it cannot provide that contract. Other
fallback engines are still validated against the primary contract.

## Development comparison

Use Java 17. The development source set is separate from the release artifact.
The build explicitly rejects the comparison tool if it appears in the mod JAR.

```sh
./gradlew researchTreeSceneComparison
./gradlew researchTreeSceneComparison -PresearchTreeSceneEngine=simple
./gradlew researchTreeSceneComparison -PresearchTreeSceneEngine=compatibility
./gradlew researchTreeSceneComparison -PresearchTreeSceneEngine=branches
./gradlew researchTreeSceneComparison \
  -PresearchTreeBaselineInput=src/test/resources/research-tree-baseline/live-weapons-287.json
./gradlew researchTreeSceneComparison \
  -PresearchTreeSceneEngine=simple -PresearchTreeSceneStress
```

Default output: `build/reports/research-tree/scenes.json`. Set
`-PresearchTreeSceneReport=<path>` to choose another destination. The default
`compare` selection passes the exact same immutable geometry input to all three
engines. Ordinary structural comparisons supply explicit development branch seeds;
an external replay uses its published hints and neutral grouping. Empty domains
explicitly bypass the legacy engine. Stress mode adds
separate maximum-node and maximum-edge fixtures; use the simple engine for the
routine fallback limit check.

Reports include the original public replay fingerprint, a geometry-input
fingerprint, engine revision, status, geometry fingerprint, membership counts,
extent, and timings. Repeated scene equality checks guard against nondeterminism.
Timing covers generation plus final validation after three warmups, with ten
measured samples. Rejected candidates are recorded as rejected, never silently
replaced by fallback in the comparison report. Review every engine status; a
successfully written report does not by itself mean every candidate passed.

These measurements are not native rendering or frame-time benchmarks. See
[the baseline capture contract](research-tree-baseline.md) for native measurements
and replay provenance. Structural, disclosure, invalid-geometry, cache lifecycle,
canonical ordering, and live-catalog regression tests are in
`ResearchTreeSceneTest`.

## Rendering and performance contract

The compact-tile renderer caches region order, group edge identities, route
geometry, and spatial indexes for each scene. Per-frame work queries visible
geometry and refreshes colors from current view state. Overview primitives and
route annotations and minimap markers use managed batches. Browse item and text drawing preserves
native draw order. Art bindings are prepared once per presentation/resource
generation; texture inspection and fallback item creation are lazy and cached.
Resource reload drops decoded art before the next draw.

The Research Bench bypasses dormant legacy row compaction and responsive layout
preparation. Those APIs remain available to explicit compatibility consumers and
development comparisons. Window resize changes the viewport, not node placement.
No renderer selector or profiling tool is included in the release JAR. The
`Research-Tree-Scene-Layout` manifest attribute records `branches-v2`; artifact
verification checks it against the runtime engine revision. This layout contract
is independent of protocol 62 and does not change research authority.

Run `./gradlew researchTreePerformance` with Java 17 for the frozen live catalog
and structural limits. Override output with
`-PresearchTreePerformanceReport=<path>`. Reports separate publication preparation,
full scene/cache work, fixed-geometry routing, indexes, and camera queries. A
returned unavailable cache result is reported as unavailable, even though the
cache safely handled the rejection. Cold means the first invocation of that
operation in that process; later fixtures may already benefit from JVM warmup.
Thread allocation excludes other threads; forced-GC heap deltas are approximate
observations, not exact retained object sizes. Native canvas CPU, full-screen CPU,
frame pacing, and first useful frame are distinct measurements.

Development targets for the frozen live catalog are a first useful frame within
250 ms after data/resources are ready and incremental canvas CPU within 2 ms on
the reference machine. These are measured targets, not guarantees across hardware,
resource packs, or every supported graph. Dense legal inputs may exceed them.
Routing budgets may reject a valid graph; the screen then displays unavailable
without dropping nodes or requirements. No worker is used: the measured live
opening path fits the target, and layout already has explicit work limits.

## Zoom detail and hover artwork

Scene rendering uses three levels of detail based on the smallest displayed
node body in logical GUI pixels. Close views include artwork, names, and status
symbols. The middle level gives artwork the tile interior, uses a neutral
background, and keeps state in the border and status strip. Very small nodes
become colored overview markers. This changes presentation only; geometry,
selection, prerequisites, and hit targets keep the same meaning.

Labels disappear below 64×28 pixels and return at 68×30. Artwork disappears
below 16×7 and returns at 18×8. Both dimensions must fit; the small transition
buffers prevent flicker. For the usual 96×44 tile, artwork therefore remains
visible at 20% scale. Thresholds apply to GUI pixels rather than raw framebuffer
pixels, preserving behavior across Minecraft GUI scales.

When labels are hidden, hovering a disclosed node shows its readable name,
status, progression information, and enlarged artwork without selecting it or
opening the inspector. The preview wraps text, stays within the screen, and
uses the existing public artwork cache. Nodes whose visibility hides their
identity retain the normal restricted tooltip and never request weapon art.
