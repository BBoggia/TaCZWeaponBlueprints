# Research Tree Phase 2: Deterministic Layout

> Historical implementation note: this phase documents the original
> top-to-bottom layout. `research-tree-navigation-phase-0.md` supersedes the
> visual direction and packing strategy for the grouped redesign; tier numbers
> and prerequisite meanings remain unchanged.

Date: 2026-08-24

## Outcome

Phase 2 adds a dependency-free layout engine for the Phase 1 research graph.
It produces logical canvas coordinates rather than screen coordinates, allowing
the later Research Bench renderer to pan, clip, and scale the tree without
rebuilding its topology.

Prerequisites flow from top to bottom. A node's tier is the longest visible path
from a root to that node, so every visible prerequisite appears above its
dependent. Hidden prerequisites do not affect the public tier because their
existence is not part of the published presentation.

## Branch and component ordering

Weakly connected components remain a deterministic ordering key, followed by
the average position of visible parents, item type, and blueprint ID. Each tier
wraps after 12 columns rather than allowing disconnected or very large packs to
create a canvas hundreds of thousands of pixels wide. Wrapped rows stay inside
their logical tier, and the following tier begins below the entire preceding
tier so every prerequisite edge still flows downward.

The resulting layout uses:

- 24 by 24 logical node bounds;
- 24 logical pixels between neighboring nodes;
- 32 logical pixels between tiers;
- 16 logical pixels between wrapped rows;
- at most 12 node columns;
- 16 logical pixels of outer canvas padding.

These are implementation constants rather than datapack coordinates. The tree
therefore adapts automatically when packs add, remove, or reconnect nodes.

## Layout invariants

`ResearchTreeLayout` rejects duplicate nodes, inconsistent ordinals, duplicate
tier ordering, nodes outside the canvas, overlapping nodes,
out-of-order tiers, oversized IDs, and invalid dimensions. It pre-indexes nodes,
and tiers so the renderer does not perform linear searches per frame. Empty
graphs have a canonical empty layout.

Focused tests cover branching, merging, disconnected components,
a maximum-size disconnected graph, longest-path tiering,
determinism, overlap rejection, out-of-bounds rejection, and empty input.
Phase 2 does not synchronize or render the graph;
the next stages can consume the graph and layout as independently tested data.
