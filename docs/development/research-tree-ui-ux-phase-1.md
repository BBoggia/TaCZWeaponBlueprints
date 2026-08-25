# Research Tree UI/UX Phase 1

Date: 2026-08-25

Phase 1 extracts the Research Tree into a reusable, dynamically bounded canvas
without intentionally changing the compact Research Bench presentation.

## Canvas boundary

`ResearchTreeCanvas` now owns:

- the published graph, deterministic layout, and spatial edge index;
- absolute screen bounds and scissoring;
- grid, connector, node, focus, and search rendering;
- disclosure-validated node icons;
- node hit testing in transformed canvas coordinates;
- mouse selection, empty-space dragging, and wheel zoom;
- fit, centered zoom, and focus-centering operations; and
- access to the active graph, layout, icons, focus, and search results for the
  surrounding details panel and navigation controls.

The Research Bench still owns player-facing text, status colors, details,
Prepare and Recycle modes, and every server action. The canvas reports a public
node selection through a callback; it never submits a packet itself.

## Shared view state

`ResearchTreeViewState` owns the local focused public node, ordered search
matches, and one `ResearchTreeViewport` for each presentation mode.

Compact and fullscreen viewports are deliberately independent. Switching to a
large view will not destroy a player's compact pan and zoom, and returning to
compact restores the prior scale. Focus and search remain shared so the same
node stays meaningful across presentations.

On atomic graph publication, stale focus and search IDs are removed. Focus
falls back deterministically to the current authoritative selection or first
public node. A topology change rebuilds the edge index and follows the existing
fit/focus behavior; player-state-only updates retain layout and viewport state.

## Dynamic geometry

The canvas consumes an absolute `ResearchTreeScreenLayout.Rect`. Rendering,
scissoring, grid offsets, hit testing, pointer-relative zoom, and viewport
configuration all derive from that rectangle rather than the old fixed
Research Bench constants.

The compact screen currently supplies the same `294 x 116` rectangle at its
existing position, preserving the live presentation. Phase 7 can supply the
responsive fullscreen canvas rectangle from the Phase 0 layout contract
without duplicating rendering or input code.

## Defensive validation

The reusable boundary rejects:

- null graph, layout, icon map, state, style, or render dependencies;
- graph/layout node-count or ordinal-ID mismatches;
- icons for unknown nodes;
- icons for a visibility tier that does not reveal an icon;
- null icon stacks;
- focus requests for nodes absent from the current public graph; and
- search-match collections containing null IDs.

This validation preserves the server's disclosure boundary when future
presentations reuse the canvas.

## Automated coverage

Phase 1 tests cover:

- absolute hit testing after moving and resizing the canvas;
- callback selection through the reusable input surface;
- independent compact/fullscreen viewport scale restoration;
- focus retention and deterministic fallback after publication;
- removal of stale search matches;
- empty-publication focus clearing;
- graph/layout mismatch rejection; and
- unknown or disclosure-violating icon rejection.

The existing viewport, navigator, edge-index, screen geometry, graph,
disclosure, packet, and progression suites continue to protect behavior around
the extracted surface.

## Deferred presentation work

Phase 1 does not yet add fullscreen controls, path emphasis, category lanes,
arrowheads, adaptive node cards, or the redesigned details panel. The compact
view should remain visually equivalent while later phases build on this single
canvas implementation.
