# Research Tree fullscreen redesign

Status: canonical implementation contract  
Baseline checkpoint: `63196e8`  
Scope: fullscreen Browse mode only unless a phase explicitly says otherwise

## Product objective

The Research Tree should behave like a full-screen map. The graph occupies the
complete screen and all navigation, search, selection, and help surfaces float
above it. The interface should remain understandable to a first-time player
without permanently covering the graph with instructions or technical detail.

Compact Browse and Recycle mode remain stable. Research continues to consume
materials directly from the player's inventory through the existing
server-authoritative transaction.

## Projection contract

Two views are published from the same disclosure-safe graph:

1. **All Weapons** uses the global layered DAG produced by
   `ResearchTreeLayoutEngine`. Every public prerequisite edge remains present,
   and ranks run from weaker/root weapons at the bottom to stronger/dependent
   weapons at the top.
2. **Group view** uses one isolated authored or fallback group. Edges crossing
   the projection boundary are represented by disclosure-safe portals that
   navigate to the remote group and node.

Datapack-authored ranks and sibling order remain authoritative. The client does
not infer weapon quality from TaCZ statistics. Synthetic groups use deterministic
dependency depth and fallback ordering.

## Visual layers

The fullscreen render order is:

1. translucent world tint and graph background;
2. graph decoration and guides;
3. edges;
4. nodes and graph status;
5. sidebar, search, contextual card, and close-control backgrounds;
6. interactive widgets;
7. first-visit coachmark;
8. tooltips and narration feedback.

Fullscreen category headers and the tier gutter are not sticky overlays. Level
of detail determines which graph labels appear as the camera zooms.

## Overlay states

### Sidebar

- Starts as a visible compact icon rail.
- Contains Search, All Weapons, published groups, and optional help/pin actions.
- Reveals a label pill for the hovered or selected entry.
- May collapse to an edge handle only after the player has used it.
- Cannot auto-collapse while hovered, keyboard-focused, searched, guided, or pinned.
- Uses virtualized row widgets for bounded memory and tab order.
- Uses only published group icons; a missing icon receives a generic fallback.

### Search

- Closed, open, and keyboard-focused are distinct states.
- `Ctrl+F` and `/` open and focus it.
- Up/Down cycle matches and Enter activates the current match.
- A collapsed active query retains a visible result-count marker.
- Search text and focus survive a screen resize when still valid.

### Contextual card

- Hover provides a lightweight name/status tooltip.
- Click pins the contextual card beside the node.
- The card flips and clamps around screen edges and other controls.
- An offscreen selected node produces a small return-to-selection chip.
- Up to six material requirements use a responsive two-column grid.
- Exact preview content and the Research button require a matching authoritative
  preview ID.

### Guidance

The first-visit guide is a small non-blocking coachmark: `Drag to move • Scroll
to zoom • Click to inspect`. The existing persisted dismissal preference is
reused, and a help action can reopen the guide.

## Input ownership

Pointer layers are resolved front-to-back in this order:

1. guidance;
2. contextual card;
3. search;
4. sidebar;
5. close control;
6. graph node or portal;
7. graph background.

The resolved owner controls clicking, hovering, scrolling, dragging, and
tooltips. An overlay blocks graph input even when that overlay has nothing to
scroll.

Node activation is release-based. A left press records a candidate; movement
of at least three logical pixels converts the gesture to a pan and permanently
cancels that candidate. Middle-drag pans without changing selection.

Keyboard behavior:

- `F`: fit the active projection;
- `+` / `-`: zoom;
- arrows: related/spatial node navigation;
- Enter: select the focused node;
- `Ctrl+F` or `/`: search;
- Escape: close search, dismiss guidance, close the card, then exit fullscreen.

## Camera contract

- The canvas bounds are exactly the complete screen.
- The viewport maintains current and target pan/zoom values.
- Dragging is immediate; focus, fit, and wheel zoom use short bounded easing.
- No long release momentum is added.
- Zoom remains cursor-centered.
- Bounded overscroll and safe focus margins allow every edge node to move clear
  of the sidebar, search, close control, and contextual card.
- Cameras are isolated by screen mode, projection, and group.
- A resize or topology replacement reclamps current and target state and cancels
  an active pointer gesture.

## Disclosure and authority

- Hidden nodes remain absent.
- Silhouette and name-only nodes retain anonymous public IDs.
- Search, group icons, cards, and tooltips reveal only fields allowed by the
  published visibility tier.
- The client never decides whether research succeeds or removes inventory.
- The server revalidates menu identity, selection, policy, prerequisites,
  points, inventory allocation, and creative bypass.
- The redesign requires no packet or protocol change.

## Performance limits

The design remains bounded for 4,096 nodes, 65,536 edges, and a large published
group list. Edge culling reuses `ResearchTreeEdgeIndex`; later phases add a node
spatial index, semantic zoom, visible-region culling, virtualized rail entries,
and allocation-conscious render paths.

## Phase gates

0. **Contracts:** checkpoint baseline; add this document and pure overlay,
   geometry, gesture, and input-routing contracts with tests. No visible change.
1. **Foundation:** wire the edge-to-edge canvas and overlay render/input layers.
2. **Navigation:** implement the compact rail and expandable search.
3. **Interaction:** implement release-based gestures, camera targets, safe margins,
   and shortcuts.
4. **Projection:** use the global DAG for All Weapons and isolated branch layouts
   with portals for groups.
5. **Readability:** add relationship emphasis, semantic zoom, culling, and node
   indexing.
6. **Action card:** add the adaptive contextual card and inventory-backed
   Research action.
7. **Polish:** finish narration, localization, coachmark, hit targets, and
   meaningful client preferences.
8. **Release:** run automated, large-fixture, multiplayer, GUI-scale, and manual
   in-game verification before building the release artifact.

Every phase must leave the build and test suite green. Any intentional change to
the contracts above is documented here before it is wired into the screen.

## Non-goals

- Replacing Minecraft's GUI framework or adding a UI-mod dependency.
- Making the client authoritative for research.
- A freeform 2D atlas of independently stacked group rectangles.
- Runtime weapon-strength inference from TaCZ gun statistics.
- Long camera inertia or physics-based movement.
- Press-and-hold research during the initial redesign.
- A broad Compact or Recycle redesign.
