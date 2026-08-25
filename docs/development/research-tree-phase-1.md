# Research Tree Phase 1: Graph Foundation

Date: 2026-08-24

## Outcome

Phase 1 adds the server-authored graph that the visual Research Bench will use.
It deliberately reuses the existing research-profile and exact-rule
`prerequisites` fields. Existing datapacks therefore remain valid and do not
need a second copy of their progression structure.

`ResearchTreeBuilder` resolves the active profile and runtime configuration for
one player, then creates a deterministic `ResearchTreeGraph`. Nodes are sorted
by blueprint ID and receive contiguous ordinals. Edges point from a prerequisite
to the blueprint it unlocks and are sorted by dependent ID, then prerequisite
ID. Learned, available, and locked node state is derived from the same policy
used by research transactions.

## Disclosure boundary

Every non-hidden effective Journal visibility can become a tree node. Lower
tiers use opaque per-publication keys: silhouette nodes contain no identity or
policy metadata and name-only nodes add only a translation key. A prerequisite
becomes an edge only when both endpoints are visible. Hidden prerequisites do
not contribute an ID, edge, count, anchor, rank, or other presentation metadata
to the public graph.

This makes the graph suitable for later synchronization without weakening the
existing server-controlled Journal disclosure policy.

## Defensive limits

The graph enforces the existing progression limits:

- no more than the 4,096-entry synchronized blueprint-catalog limit of nodes;
- no more than 65,536 visible prerequisite edges;
- no duplicate nodes or edges;
- no missing edge endpoints or self references;
- contiguous node ordinals and prerequisite counts that exactly match public edges;
- no prerequisite cycles or paths deeper than 64 nodes;
- bounded IDs, translation keys, item types, point values, and summary counts.

`BlueprintResearchDataManager.treeFor` is the production entry point for
building a graph from the current catalog, datapack publication, synchronized
configuration, blacklist, and player progression. Phase 1 does not synchronize
or draw the graph yet; those concerns remain separate so that networking and
layout can each be tested against this stable contract.

## Verification

Automated tests cover deterministic branching and merging, learned/available/
locked state, hidden-prerequisite omission, disabled input, unknown endpoints,
duplicate edges, and cycles. The complete project test and release build remains
the final Phase 1 gate.
