# Tech Tree prerequisite routing

Audience: developers integrating the scene-based Research Bench.

`ResearchTreeBranchSceneEngine` publishes a validated `ResearchTreeRouting` network
alongside the [branch allocation](research-tree-branch-layout.md). The active
Minecraft Research Bench renders this network using compact tiles, route gaps,
and prerequisite junctions. This geometry contract does not by itself certify
native readability or frame times.

## Research meaning and ownership

Every public prerequisite group gets one junction, even a singleton or a group
containing only anonymous alternatives. A junction's incoming alternatives are
OR choices; the dependent requires all of its groups. Therefore `(A OR B) AND C`
has two junctions and two group trunks, while `A OR B OR C` has one.

The network contains three connection kinds:

- `ALTERNATIVE`: one permitted blueprint to one input port of one particular
  group. A blueprint repeated in several groups has a separate connection to
  each group. Its blueprint node is not duplicated.
- `GROUP_TRUNK`: one group junction to its dependent. Alternatives share this
  single, explicitly owned trunk through the visible junction. Render the trunk
  once, regardless of how many alternatives it represents.
- `BOUNDARY`: a local blueprint to a marker containing its disclosed cross-domain
  links. A marker aggregates only the same local blueprint, domain, and direction.
  The public boundary data does not identify a prerequisite group, so a boundary
  connection never asserts one. This applies to both requirement and unlock links.

Junctions retain the exact public `Group`, including dependent/ordinal, visible
alternatives, hidden counts, and external counts. Only individually disclosed
local alternatives receive input ports. Anonymous alternatives remain summaries,
without invented identifiers or connections. An inspector can resolve each group
against the current presentation without reconstructing meaning from drawn lines.
Satisfaction, costs, names, icons, and selection remain outside geometry.

`ResearchTreeScene.routes()` remains the compatibility edge view: one complete
polyline per unique blueprint pair, using its first canonical group occurrence.
Its path passes through that group's marker and trunk. It cannot express repeated
group membership alone. **A grouped scene renderer must draw `routing.connections()`;
it must not also draw the compatibility paths.** Validation checks both views agree.

Region metrics include `routingTrackSpacing`, bounded to 2–6 GUI units. The
six-argument constructor retains the three-unit default. This measurement is
part of the scene policy/cache key and affects reserved route space and ports;
it never changes group membership or clearance validation.

## Measurement and routing order

Before placement, `ResearchTreeRoutingPlan` measures each junction from its visible
port count and anonymous-summary allowance. Junctions are eight logical units tall,
with a minimum width of eight, or eighteen when anonymous summaries are present.
Visible ports reserve three units each. Independent group markers reserve a gap
and sit below their dependent; their trunks terminate at distinct body ports.

Node slots reserve the complete marker row. Each occupied semantic rank also
reserves separate source escape rows and destination approach rows, using the
actual number of alternative connections. All nodes of the same semantic rank
retain a common body Y coordinate. Region headers sit above the rank's entire
escape channel. Header footprints participate in obstacle checks.

The initial side gutter is the greater of the configured minimum and a demand
estimate: three units times one plus the ceiling of the busiest rank's alternative
count divided by twice the region count. This estimate reserves capacity before
node placement; it is not a proof that every route will fit.

Routing then performs these operations in stable public-input order:

1. Connect group outputs and domain markers to their blueprint ports.
2. Assign each alternative a distinct source body port, escape row, junction input
   port, and approach row. Reserve every fixed port stem before routing so an early
   direct path cannot block a later connection's only exit.
3. First try a clear direct connection for alternatives within the same owner.
   Otherwise try vertical tracks beside the source and destination regions, then their
   ancestors, then other shared region gutters ordered by distance and identifier.
   Check the complete candidate polyline against obstacles and occupied tracks.
4. Accept the first clear candidate and reserve its segments. Tracks can be reused
   over disjoint vertical intervals. Unrelated collinear connections cannot overlap
   or touch; different connections never acquire an implicit shared trunk.
5. Record every perpendicular crossing and perform final scene validation.

Region dependencies need not form a tree. A → B → A uses the same shared corridor
resources without modifying containment. Edges may cross branch envelopes, but
must clear node paint, group markers, domain markers, and measured header text.
Routes reserve one logical unit around their centerline when checking obstacles
and scene extents. Distinct body ports can be close on compact nodes with very
large fan-out; legibility there remains a native-prototype decision.

## Crossings and visibility

Each crossing names both connections and both segments. The rendering convention
interrupts horizontal ink while leaving vertical ink continuous. A crossing is
never a logical join; only a junction marker connects alternatives to a group
trunk. The diagnostic SVG implements this convention. Arbitrary DAGs are allowed
to have crossings; crossing count is a quality measurement, not a zero-crossing
acceptance rule.

`ResearchTreeRoutingIndex` indexes every segment's ink bounds in a balanced bounds
hierarchy. `query(viewport)` returns stable connection/segment order. A connection
is eligible even when both blueprint endpoints are outside the viewport.
`selected(viewport, connectionIds)` uses the same segment query, independently of
node culling. The renderer uses segment visibility for both ordinary
and selected routes. The index holds geometry only; it does not own selection.

## Failure and validation

The initial allocation can be retried at most twice, with two and four times its
measured gutter width. Each attempt starts from the same input. Region membership,
containment, rank membership, row Y values, and sibling/node ordering remain fixed;
widths and horizontal positions can expand. No post-routing compactor runs.

Deterministic limits apply to each request:

| Resource | Limit |
| --- | --- |
| Group-owned connections, including trunks and domain markers | 16,384 |
| Candidate gutter tracks per connection | 4,096 |
| Recorded crossings | 524,288 |
| Counted routing/validation work | 32,000,000 per pass; routing shares this budget across retries |
| Connection points | 64 per connection; 1,048,576 in the network |
| Spacing retries after initial allocation | 2 |

The scene coordinate and public graph limits still apply. The grouped-connection
budget counts one connection per aggregated local-node/domain/direction marker,
regardless of the number of disclosed links behind that marker. All those links
remain present and separately count against the public edge limit.
The grouped-connection
limit is deliberately lower than the public graph's 65,536-edge limit. A legal
input can exceed a presentation budget; it must then fail explicitly without
dropping alternatives. Budget exhaustion is not retried. Only corridor or port
capacity failures enter the spacing-retry loop.

Final validation checks exact connection and group membership, measured junction
footprints and ordered ports, attached endpoints and approach direction, painted
obstacles, collinear conflicts, self-crossings, crossing declarations, compatibility
edge agreement, finite coordinates, and complete ink extents. It also reruns branch
ownership, containment, gaps, ordering, and rank checks on the final allocation.

An engine declaring `requiresGroupedRouting()` cannot fall back to geometry lacking
this network. A fallback must pass validation for the same current input and
supply grouped routing; otherwise the cache returns `UNAVAILABLE` with diagnostics
and no geometry. The existing simple engine remains a valid structural fallback
for compatibility requests, but it does not satisfy this stronger contract.

## Development evidence

`researchTreeSceneComparison` report format 3 includes connection and crossing
counts, retries, bends, used corridor tracks, rejected track candidates, routed
width, counted work, and junction geometry. SVG exports include the entire routed
extent, all nodes and regions, group markers, domain markers, and crossing gaps.
These files are geometry diagnostics with internal identifiers, not Minecraft UI
screenshots. Export tooling remains outside release JARs.

`ResearchTreeRoutingTest` exercises Boolean group meaning, repeated alternatives,
anonymous summaries, multiple domain markers, large group counts, offscreen route
visibility, final-geometry corruption, spacing retries, budgets, and fallback
recovery. Existing branch tests cover the structural graphs and public live replay.
