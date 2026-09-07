# Tech Tree branch allocation

Audience: developers working on the scene-based Research Bench replacement.

`ResearchTreeBranchSceneEngine` reserves space for a containment forest before
placing nodes. It is available through the development comparison harness. The
active Research Bench continues to use its existing layout and renderer.

## Ownership and public input

Containment and dependencies are separate structures. Each permitted node has
one direct owning region. Regions may contain child regions. Dependencies can
cross between any regions, including A → B → A, as long as the blueprint graph
itself remains acyclic. A dependency does not create a containment relationship.

`ResearchTreeBranchStructure.resolve` chooses ownership from these sources:

1. Explicit internal seeds, when supplied through `ResearchTreeSceneInput.withBranchSeeds`.
   Seeds specify an internal ID, optional parent ID, sibling order, and direct
   members. They must cover the current public projection exactly. Empty containers
   are permitted; entirely empty subtrees are rejected.
2. Coherent published automatic Weapons hints. Foundation and transition members
   remain owned by `automatic/shared`. A node enters its family region only when
   its published `rankIndex` exceeds `transitionEndIndex`. At least two populated
   post-transition families are required to establish distinct family regions.
3. Neutral regions for weakly connected components when those sources do not
   establish specialization. This includes unhinted authored trees, inconsistent
   automatic thresholds, and trees whose visible nodes have not reached distinct
   post-transition families.

Keeping transition ownership shared is a conservative first allocation rule.
Transition dependencies can still connect different families; the engine does
not interpret those connections as fully separated specialization. The live
replay fixture therefore retains 234 shared foundation/transition members and
separates 53 later members into seven family regions. These are fixture counts,
not assumptions about every pack or player's disclosure.

Region IDs are internal, topology-scoped identities, not inferred player-facing
titles. A numeric automatic family index does not become a specialization name.
Classification lanes are ordering hints, not authored branches. No new node,
hidden alternative identity, research relationship, or representative icon is
created by branch inference.

The seed API is currently an engine/fixture seam. There is no new datapack field,
network record, player setting, or public branch-authoring format. Future authored
branches need their own validation, disclosure, serialization, and compatibility
work before pack authors can supply them.

## Allocation order

The engine performs four ordered operations:

1. **Validate the forest.** Reject duplicate IDs/owners, missing members or parents,
   cycles, empty subtrees, and exceeded count/depth budgets. Canonicalize seed and
   member ordering independently of map insertion order.
2. **Measure widths from children to parents.** Reserve each direct member's paint
   width, prerequisite-group allowance, external marker footprint, and node gap.
   A region's width must fit its widest direct row, child widths and branch gaps,
   header width, side padding, and routing gutters.
3. **Build one occupied-rank table.** Sort actual semantic ranks, skipping numeric
   holes. Allocate maximum nesting/header, node, group, and external marker
   clearances for each occupied rank, with an additional gap at band changes.
   All nodes of one rank share the same body Y coordinate. Higher ranks remain
   above lower ranks. Physical Research Bench tier is not used for placement.
4. **Place parents before children.** Assign sibling regions in stable order within
   measured parent bounds. Place direct members in lane/sibling/identifier order
   within their owning row. Build final envelopes from owned paint and marker
   bounds, rank escape allowances, and descendant envelopes. No branch compactor or later global centering
   pass is applied to the result.

When a parent owns a node at a rank covered by a child envelope, its direct row
uses a reserved strip beside the children. A shared foundation or later merge
outside child rank ranges can instead use the parent width. This avoids putting
parent-owned nodes into the space reserved for a child branch.

Sibling envelopes reserve a horizontal gap. Their vertical extents end at their
own occupied content, including nested headers, padding, and external markers.
Short branches are not extended to the height of longer neighbors. Horizontal
slots remain fixed even when sibling rank ranges do not overlap; this engine does
not yet reuse the horizontal space of an ended branch.

The default region profile reserves a 40-unit sibling gap, 12-unit padding,
12-unit header height, 80-unit header width, 16-unit routing gutter, and 10 units
per prerequisite-group allowance. These are geometry defaults for development,
not final node-art or typography decisions. The profile is part of the geometry
key and can be replaced with measured values.

## Final validation and budgets

The [shared scene validator](research-tree-scenes.md) still checks node/edge/group/
boundary membership, coordinates, route endpoints, and bottom-to-top progression.
Branch scenes additionally require:

- Exact forest identity, parent relationships, direct ownership, and occupied ranges.
- Parent containment of node paint, external markers, and child envelopes, including
  header/padding/gutter allowances.
- Preserved sibling order and reserved gaps. Combined with parent containment, this
  establishes separation for all non-ancestor regions.
- Separation of parent-owned content from child envelopes.
- Preserved node order and spacing within each owned row.
- Exact occupied-rank membership, shared row coordinates, and sufficient row clearance.
- Tight vertical envelopes around actual occupied content, preventing empty
  full-height branch columns from passing as valid allocation.

There may be at most 8,192 regions, 64 containment levels, and 4,096 direct node
memberships in total. Layout retains the existing graph limits and million-unit
coordinate envelope. Width and rank accumulation check bounds before placement;
oversized allocations reject without publishing partial geometry. Group routing
adds its own work/count limits and requires a fallback with validated grouped
connections; otherwise the experimental request is unavailable.

Node metrics, measured region allowances, and explicit seeds enter the scene key.
State-only changes still reuse geometry. Window width remains a conservative
cache-key input in the shared API, but it cannot change branch node, region, rank,
or route coordinates. The engine never calls responsive row-capacity logic and
does not wrap a semantic rank into new visual rows. The server's semantic width
and generation behavior remain unchanged; see the
[width contract](research-tree-dynamic-width.md).

## Routing and visual inspection

The branch engine measures prerequisite junctions and routing demand before
placement, then routes group-owned connections through local and shared gutters.
It validates node, marker, junction, and header clearance and records crossings
explicitly. Two bounded spacing retries can widen gutters while preserving
ownership, ranks, and ordering. See the [routing contract](research-tree-routing.md)
for ports, AND-of-OR meaning, crossing treatment, visibility, and failure limits.

Run the comparison with Java 17:

```sh
./gradlew researchTreeSceneComparison -PresearchTreeSceneEngine=branches
./gradlew researchTreeSceneComparison \
  -PresearchTreeSceneEngine=branches \
  -PresearchTreeSceneSvgDirectory=build/reports/research-tree/geometry
./gradlew researchTreeSceneComparison \
  -PresearchTreeBaselineInput=src/test/resources/research-tree-baseline/live-weapons-287.json \
  -PresearchTreeSceneSvgDirectory=build/reports/research-tree/geometry
```

Default `compare` runs compatibility, simple, and branch engines on identical
inputs. The ordinary structural fixtures add explicit development branch seeds
to all three inputs. Shared foundation/merge ownership is declared in the fixture
adapter, never inferred from those fixture names in production. A replay supplied
with `researchTreeBaselineInput` uses only its public projection and normal branch
inference.

Report format 3 includes region, node, occupied-rank, junction, and routing
diagnostics for branch scenes. Optional SVG exports draw every node and region,
grouped connections, markers, and crossing gaps. Their camera fits the full routed
extent. These are geometry diagnostics, not screenshots or previews
of the finished Minecraft UI. SVG/fixture tooling is excluded from release JARs.

`ResearchTreeBranchSceneTest` covers structural separation, nested and parent-owned
members, returning region connections, sparse ranks, insertion-order stability,
viewport invariance, neutral inference, automatic hints, cache reuse, final-scene
rejection, large/tiny metrics, marker containment, and bounded stress inputs.
