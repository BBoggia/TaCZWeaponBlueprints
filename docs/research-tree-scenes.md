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
canvas clicks or scrolling. Resizing preserves the selected node’s absolute screen position when it fits the new canvas; otherwise the view moves only enough to reveal it, subject to content bounds.
Selection opens a docked inspector when both canvas width and detail height fit.
Smaller screens use a compact selection strip with a Details button; expanding
it opens an overlay. The compact strip has no purchase action. Expanded details
reserve at least four normal text rows at the minimum supported 320×240 GUI size.
Identity, purchase scope, status, cost, and controls remain fixed while the body
scrolls. Long names and summaries wrap within a measured budget, then use an
ellipsis; tooltips expose the full text.

Requirement rows distinguish explanations, materials, groups, prerequisites,
and external links. Every group is required; alternatives inside a group use
choose-one wording. Only disclosed, selectable requirement targets become links.
Arrow keys visit links, Enter follows them, and Page Up/Down scroll the body.
Back restores the inspected target, domain, camera, scroll, and expansion state
from a bounded 32-entry history. Only following a requirement adds an entry;
ordinary selection clears the chain. Progress updates preserve valid entries,
while incompatible publications and closing the screen clear them. Inspection
never changes the tracked research goal. Escape collapses the small-screen
overlay when search or help is not handling the key. The
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
camera actions. Compatible saved views take priority over a fresh opening view.
Preview refreshes do not reframe the target. Animated pointer zoom stores a world
point and its screen coordinate, interpolates scale, and derives pan from that
anchor each frame. Bounds may clamp it. Repeated input retargets from the rendered
camera; dragging cancels the pending transition. Reduced motion applies camera
destinations immediately.

Wheel ownership uses the same frontmost-layer policy as pointer input. Search
results and help cover the inspector; inspector and minimap wheels never zoom
the underlying graph. Canvas, minimap, and scrollbar drags retain their owner
through release outside their starting region. Window focus loss cancels drag
and hold state. Hold eligibility also requires an active window and no focused
widget or help overlay, in addition to the existing selection and quote checks.

Inspector and search scrolling accumulate fractional wheel distances. Search
scrolling moves the visible window without committing a different research
selection. The inspector scrollbar supports dragging; its focused content uses
Page Up/Down. Those keys scroll focused search results as well, while domain
switching remains available outside these contexts.

The inspector requires a matching authoritative preview for exact costs,
material allocation, and research eligibility. The existing action controller
retains pending-action, click/drag/hold, and cancellation behavior. Tier metadata
is descriptive: a displayed blueprint tier does not itself block research.
The server's access summary supplies the blocker. Crafting access and Blueprint
Fragment progress appear separately. Every public prerequisite group and visible
alternative remains available in the scrolling content; undisclosed alternatives
remain counts.

`ResearchTreeSelectedNodePresenter` carries the selected identity, matching quote,
access summary, and progression details. A matching quote can report currency
and material shortages together; planning and access failures take priority.
The inspector, action tooltip, and narration describe the number of blueprints
in the purchase, including the target and required unlearned predecessors.
Currency and materials are purchase totals. Published summaries alone cannot
expose inventory allocation or enable research. Currency wording uses the current
server-issued denomination, including research failure feedback.

`ResearchTreeCanvasStatusPresenter` resolves loading, rejected publication,
unavailable scene, empty publication/domain, and zero search matches independently
of inspector visibility. Loading means no complete publication or scene has
arrived; an accepted empty publication is distinct. Rejected replacements clear
the previous presentation. Failure states take priority over empty search results.
Clear search uses the current domain's existing search controller. Closing the
Bench allows the player to reopen it; no automatic retry or purchase is submitted.
The status panel owns its pointer area and supplies narrator text.

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

## Purchase-bound route review

The expanded inspector offers Review research path separately from Track and
Research. It uses screen-owned state, so reviewing B while tracking A does not
replace A's goal. The review lists newly purchased blueprints, learned support,
and the selected alternative for each requirement group. Disclosed selectable
steps use the inspector's navigation and Back history. Current authoritative
preview data supplies purchase totals, balances, and material allocation.

Protocol 65 adds a dedicated request/result pair. Each request identifies the
menu, unique request identity, publication generation, target, and opaque
route fingerprint from the selection preview. The server checks the open menu,
selected quote, disclosed target, current publication, and freshly evaluated
fingerprint. It constructs the guidance payload from that same evaluation.
Review shares the menu's guidance limiter and global planning admission; the
existing bounded guidance codec and disclosure checks apply. Oversized or
unavailable routes receive an explicit explanation.

A review is labeled Current purchase only while its complete binding matches:
menu, publication, inspected target, authoritative preview, and captured inventory
identity. Changed bindings discard pending and accepted reviews before rendering.
The client checks support disclosure, learned/purchased membership, chosen public
alternatives, required group coverage, and connectivity. Delayed responses must
match the current request exactly. Resource allocation is not encoded in the
route fingerprint; visible allocations are checked against the authoritative
preview, and inventory changes invalidate the review even with the same fingerprint.

Requests have a 500 ms minimum spacing and a 3 second timeout, with at most two
attempts per unchanged binding. A throttled request waits before retrying. Closing
the review or screen discards its state. A rejected review can be retried by
reopening it or after a relevant binding change. Review failure never changes
purchase eligibility; Research continues through the existing quote fingerprint
check and atomic transaction.

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
is independent of the network protocol version and does not change research authority.

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

Artwork, ordinary names, status marks, and junction text have independent
screen-space thresholds with transition buffers. Artwork appears at 18×8 GUI
pixels and remains down to 16×7. Ordinary names appear at 76×36 and remain down
to 72×34; they render at the normal GUI font size inside the available card
space. Status marks appear at 32×14 and remain down to 28×12. Junction text
appears at a node height of 28 and remains down to 26. Both dimensions must fit
where a width and height are given. Framebuffer density does not change these
thresholds.

Artwork always uses the same 16-unit-high allocation near the top of a node,
with aspect ratio preserved by the existing crop cache. It does not expand into
the name area when names disappear. Very small nodes retain colored overview
markers. These choices do not change geometry, hit targets, or selection.

The inspected node, keyboard focus, and active search result receive readable
screen-space labels in that priority order. Labels are clipped to the canvas and
placed around higher-priority annotations. Ordinary names yield to these labels.
Branch titles and Shared foundations also use screen-space labels with clipping,
collision handling, and full-name hover tooltips. Generated branches retain
neutral numbered titles; authored titles and translation keys are preserved.
The shared landmark follows its direct foundation nodes, excluding child branch
crowns from its framing bounds. Clicking a landmark frames its region. Clicking
a node at overview still inspects that node. A landmark drag only pans.

Connector precedence is verified review route, direct inspected requirements,
tracked route, earlier inspected requirements, then quiet context. Review lines
are thicker gold, direct requirements are gold, ancestry is muted gold, and
tracking is cyan. The tracked goal retains a fixed-size cyan cross even while
another target is reviewed. Review emphasis is withdrawn whenever the
quote-bound review becomes non-current. Crossing gaps account for the width of
both lines; group junctions and AND-of-OR identities are retained. Renderer state
and labels never enter the scene geometry key. The text cache is bounded to 4,096
entries.

A fresh view with no compatible saved camera prefers a disclosed tracked target
in its domain, then a publicly actionable frontier, then the existing structural
fallback. It does not infer exact affordability. Ordinary publication updates,
hovering, and review responses do not apply this opening policy.

When ordinary names are hidden, hovering a disclosed node shows its readable
name, status, progression information, and enlarged artwork without selecting
it. The preview wraps text, stays within the screen, and uses the existing public
artwork cache. Restricted nodes retain the normal restricted tooltip.

## Minimap coverage and artwork preparation

Automatic minimap visibility follows the rendered camera, including zoom and
usable canvas insets. It appears below 80% visible layout area and stays visible
until coverage reaches 92%. Always and Hidden retain their explicit meanings.
The outer panel follows the layout aspect ratio, within a 168×112 GUI-pixel
maximum and a 48×36 minimum where space permits. Existing overlay avoidance
still applies. Very narrow content retains a usable panel hit area.

During minimap navigation, visibility, placement, and coordinate mapping remain
fixed until release. Publication replacement, canvas resize, explicit hiding,
and ordinary interaction cancellation invalidate the drag. The camera outline
is white; the tracked target uses a cyan mark drawn above it. Neither navigation
nor visibility changes modify research selection or progression.

Disclosed artwork is prepared on demand, not by scanning every pack texture.
Pack API bindings and resource lookup remain on the client thread. One daemon
worker reads PNG resources and calculates crop metadata, with at most 64 queued
jobs. A full queue leaves the blueprint fallback visible and retries on a later
frame. Texture drawing, uploads triggered by drawing, and item rendering remain
on the render thread. Completed metadata is reused across camera movement.

Resource reload cancels queued work and invalidates its generation; running work
checks cancellation before decoding and during cropping. Old results cannot
populate the new presentation. Streams and decoded native images are closed;
only small crop records remain cached. The cache retains textures referenced by
the current bounded public graph. Missing, malformed, oversized, or unavailable
artwork retains the blueprint fallback. PNG reads remain capped at 8 MiB and
4,194,304 pixels.
