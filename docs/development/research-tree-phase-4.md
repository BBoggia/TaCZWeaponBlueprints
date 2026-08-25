# Research Tree Phase 4: Interactive Bench Canvas

> Historical implementation note: this phase documents the original
> top-to-bottom renderer. `research-tree-navigation-phase-0.md` supersedes its
> visual direction for Branches and All Weapons.

Phase 4 turns the synchronized graph and deterministic layout from Phases 2 and
3 into the primary Research Bench browsing experience. The old filtered,
paginated button list is no longer used on the Research tab.

## Player experience

The Research tab now presents a clipped top-to-bottom tech tree:

- weapon icons are nodes;
- prerequisite relationships are orthogonal connecting lines;
- green nodes are learned, gold nodes are available, orange nodes need earlier
  unlocks, gray nodes still need discovery, and red nodes cannot currently be
  researched or need more RP;
- the selected node has a gold outer outline and search matches have a white
  outer outline.

Clicking a node requests selection from the server. The detail strip changes
only after the server returns its authoritative preview, so the canvas does not
turn client presentation state into research authority.

The detail strip intentionally contains only the selected weapon name, a short
action-oriented status, RP cost, current RP balance, and the **Prepare** action.
Material allocation and exact per-slot counts remain on the Prepare screen,
where they are useful.

## Navigation

- Drag empty canvas space with either mouse button to pan.
- Use the mouse wheel over the canvas to zoom between 50% and 150%.
- **Center** focuses the selected node, the first search match, or the first
  currently available node.
- **Fit** recenters the complete logical layout at the closest supported zoom.
- Search keeps the full topology visible, highlights every match, and focuses
  the first match. Pressing Enter selects that first match.

Pan and zoom are handled by the pure `ResearchTreeViewport` transform. It keeps
small layouts centered, clamps large layouts to their bounds, preserves the
canvas point under the cursor while zooming, and supplies the visible rectangle
used to skip off-screen node and edge drawing.

## Disclosure and synchronization

The screen consumes one `ClientResearchState.Publication`, which contains the
matching Journal snapshot, graph, and layout. A new publication swaps all three
views together. Search, icons, labels, costs, state colors, and public
prerequisite counts are derived only from that filtered publication.

The canvas never reconstructs hidden IDs, reads the local TaCZ catalog to fill
redacted data, predicts eligibility, or performs a research transaction. Node
selection and all subsequent actions continue through the existing
server-authoritative Research Bench packet flow.

## Validation

`ResearchTreeViewportTest` covers:

- centering a canvas smaller than its viewport;
- clamping pan movement at every canvas boundary;
- cursor-anchored zoom;
- focus and fit behavior; and
- visible-rectangle intersection checks.

The normal full test and build tasks additionally compile the Minecraft screen,
validate language JSON, and exercise the synchronized graph/menu transaction
paths added in the earlier phases.
