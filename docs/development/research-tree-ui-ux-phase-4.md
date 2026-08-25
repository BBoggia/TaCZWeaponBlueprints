# Research Tree UI/UX Phase 4

Date: 2026-08-25

> Historical implementation note: this phase documents downward connector
> routing in the current renderer. `research-tree-navigation-phase-0.md`
> supersedes that direction; grouped layouts will route from a prerequisite's
> top edge to an upward arrowhead at its dependent.

Phase 4 makes dependency direction and branch structure readable without
requiring the player to infer meaning from color. It replaces the original
three-segment center-to-center connectors with deterministic routed connectors
and downward arrowheads.

## Directed connectors

Every visible dependency still runs from the prerequisite's bottom edge to the
dependent's top edge. The dependent end now terminates in a filled downward
arrowhead immediately above the node, making progression direction visible at
normal and fullscreen sizes.

Branches and merges receive deterministic ports distributed across the usable
width of their source and target nodes. Port order follows the public peer's
layout position and opaque public key, so repeated publications with unchanged
topology produce identical connector geometry.

## Obstacle-aware routing

Each route uses five orthogonal segments:

1. a short exit below the prerequisite;
2. a horizontal branch through the empty gap below its row;
3. a vertical track in the source category lane's reserved gutter;
4. a horizontal approach through the empty gap above the dependent; and
5. a short entry ending at the arrowhead.

Lane gutters are outside every node's content area. They remain safe even when
a large tier wraps into centered rows with different horizontal offsets. This
prevents long connectors from passing through cards in later rows, which the
old midpoint route could do.

Layouts created without category metadata retain a deterministic fallback
route for compatibility with tests and internal callers.

## Visual priority and culling

Connectors are rendered in three layers:

- unrelated and neutral branches first;
- earlier requirement and later unlock paths second; and
- direct requirements and direct unlocks last.

Focused relationships therefore remain visible at crossings instead of being
painted underneath unrelated branches. Existing gold/cyan relationship colors
and textual tooltips remain unchanged.

Arrowhead width and every routed track are included in the edge's spatial
bounds. The balanced interval index therefore retains viewport culling without
cutting off an arrow at the edge of the screen.

## Disclosure and authority

Routing consumes only synchronized public graph edges and immutable client
layout positions. Silhouette and Name nodes use their opaque publication keys
and the `undisclosed` lane exactly like any other public topology. No real
blueprint identity, hidden edge, category, cost, or policy state is inferred.

Phase 4 adds no packet, server action, persisted progression, datapack format,
or visibility-rule change.

## Automated coverage

Tests cover:

- downward arrow placement at the dependent node;
- deterministic distinct branch and merge ports;
- node-body avoidance through wrapped rows;
- arrow-aware viewport culling;
- anonymous Silhouette/Name topology;
- deterministic route rebuilding; and
- maximum-node fan-out construction and querying within a bounded performance
  gate.

The complete client, graph, disclosure, packet, progression, artifact, and
resource suites remain part of the build gate.

## Deferred presentation work

Phase 5 adds status symbols and richer node-card presentation. Connector
bundling beyond shared lane tracks is intentionally deferred unless large
third-party research packs demonstrate that it is needed in practice.
