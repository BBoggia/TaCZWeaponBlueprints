# Research Tree fullscreen redesign

Status: canonical implementation and unified-overview target contract
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

Two client views are derived from the same complete, disclosure-safe server
publication:

1. **All Weapons** is a curated unified overview. Authored groups participate by
   default; generated item-type fallback and Undisclosed groups remain outside
   it unless server-authored presentation metadata explicitly opts them in.
   Included nodes use one prerequisite-driven bottom-to-top layout rather than
   category lanes or side-by-side group rectangles.
2. **Group view** uses one isolated authored or fallback group. Edges crossing
   either the group boundary or the curated-overview boundary are represented
   by disclosure-safe portals that navigate to the remote group and node.

The full published graph remains available to Branches, search, relationship
validation, and server-authoritative selection. Removing a node from the
overview never hides or disables it. Every ordinary connector represents an
actual public prerequisite edge; the client never draws decorative unlock
relationships merely to make disconnected content look connected.

Datapack-authored ranks and sibling order remain authoritative. The client does
not infer weapon quality from TaCZ statistics. Synthetic groups use deterministic
dependency depth and fallback ordering.

## Visual layers

The fullscreen render order is:

1. translucent world tint and graph background;
2. graph decoration, edges, nodes, item models, and graph status on an explicit
   negative graph Z layer;
3. sidebar, search, contextual card, and close-control backgrounds on the
   ordinary overlay layer;
4. overlay item models and interactive widgets;
5. first-visit coachmark;
6. tooltips and narration feedback.

Render call order alone is not a layering guarantee: Minecraft item rendering
raises models inside the current pose. The complete graph layer must therefore
remain below the overlay base even when graph and card item models overlap in
screen space.

Fullscreen category headers and the tier gutter are not sticky overlays. Level
of detail determines which graph labels appear as the camera zooms.

## Overlay states

### Sidebar

- Starts as a visible compact icon rail.
- Contains Search, a stateful Branches/All Weapons action, published groups, and
  optional help/pin actions. In Branches the first entry opens All Weapons; in
  All Weapons the same entry returns to Branches.
- Reveals a label pill for the hovered or selected entry.
- May collapse to an edge handle only after the player has used it.
- Cannot auto-collapse while hovered, keyboard-focused, searched, guided, or pinned.
- Uses virtualized row widgets for bounded memory and tab order.
- Uses only published group icons; a missing icon receives a generic fallback.
- Retains the last valid branch and its camera when the primary entry toggles
  between views. If overview focus belongs to a different group, returning to
  Branches selects that focused node's published group.

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

Phase 6 implements this as a single adaptive surface rather than retaining any
fullscreen right, bottom, or drawer details panel. Card geometry evaluates the
four sides of the selected node, minimizes overlap with active controls, and
clamps to the screen. Exact costs, inventory counts, readiness, and the action
are withheld until pinned node, authoritative menu selection, and server preview
IDs all agree. The action then uses the existing menu transaction; the server
still replans and consumes the player's inventory. A selected node that leaves
the visible, unobstructed canvas exposes a return-to-weapon chip.

### Guidance

The first-visit guide is a small non-blocking coachmark: `Drag to move • Scroll
to zoom • Click to inspect`. The existing persisted dismissal preference is
reused, and a localized help action in the fullscreen rail can reopen the guide.

### Phase 7 polish contract

- The collapsed rail keeps its four-pixel visual edge while exposing a
  twelve-pixel pointer target, so discoverability does not require visible
  chrome across the graph.
- Node and portal pointer targets retain minimum screen-space sizes of sixteen
  and fourteen pixels respectively as semantic zoom reduces their artwork.
  Overlapping expanded targets resolve to the nearest center with deterministic
  node-order tie breaking.
- Fullscreen Help and rail pin actions remain inside the virtualized icon rail.
  Pin state is a client-only preference, survives reopening the bench, preserves
  unknown future properties, and never changes server or datapack state.
- Search, rail entries, the active projection, and pinned context cards narrate
  their current state and action. Exact cost and inventory narration obeys the
  same authoritative-preview identity check as the visible card.
- Compact view labels, rail actions, search state, and all new narration use
  localization keys; only universal symbolic button artwork remains literal.

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
- Phases 0 through 2 require no packet or protocol change. The planned optional
  overview-inclusion field is server-authored presentation metadata and must be
  synchronized in Phase 3 with the normal network protocol compatibility bump.

## Performance limits

The design remains bounded for 4,096 nodes, 65,536 edges, and a large published
group list. Edge culling uses `ResearchTreeEdgeIndex`; node rendering and hit
testing use `ResearchTreeNodeIndex`; semantic zoom reduces connector, label,
icon, and badge density; other graph decoration is clipped to the visible
region; and the rail keeps its virtualized entry widgets.

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

Phase 8 strengthens packaged-class and localization verification, runs the
343-test JDK 17 certification suite and bounded maximum fixtures, and validates
fresh client and dedicated-server startup logs. The app-less Java/GLFW client
cannot be controlled by the available desktop bridge, so two-player, GUI-scale,
pointer, narration, model, and screenshot checks remain explicit hands-on items
in `docs/research-tree-manual-qa.md`; startup evidence does not mark them passed.
The full evidence record is
`docs/development/research-tree-fullscreen-phase-8.md`.

Every phase must leave the build and test suite green. Any intentional change to
the contracts above is documented here before it is wired into the screen.

## Player-experience polish follow-up

The post-layout polish series starts with
`development/research-tree-player-experience-phase-0.md`. Where earlier
historical phases differ, this follow-up governs future player-facing work:

- the graph reduces detailed synchronized availability to Learned, Available,
  Locked, and Hidden/unavailable families;
- Available means worth inspecting, not exact transaction readiness;
- only a matching authoritative preview may say Ready or enable Research;
- typing search text highlights and counts results but does not move the camera
  or switch projections until the player commits a result;
- single click selects, the visible Research button is the safe default action,
  and immediate double-click spending is not the default interaction;
- graph, hover, selected-card, and help/settings information have separate
  minimum surfaces so secondary detail does not refill the canvas with clutter.

Phase 0 records these as pure contracts and fixtures. Phase 1 introduces the
behavior-preserving screen/controller and dirty-refresh boundaries. Phase 2
adopts non-disruptive global search in the live screen: typing and Up/Down only
change matches and the result cursor, while Enter or a result click explicitly
commits projection navigation, camera focus, and selection.

## Unified overview follow-up phase gates

The hands-on failures found after the original Phase 8 validation are addressed
as a separate, ordered follow-up without retroactively treating the prior
automated evidence as visual proof:

0. **Contracts and fixtures:** record the 481-node runtime shape, reserve graph
   and overlay layers, require a two-way fullscreen view action, define authored
   overview defaults, and add dense and connected regression publications. No
   visible behavior changes.
1. **Layering:** isolate the graph below every fullscreen overlay surface.
2. **Navigation parity:** make the first fullscreen rail entry a stateful,
   camera-preserving Branches/All Weapons toggle.
3. **Curated projection:** synchronize optional inclusion metadata, retain the
   complete authoritative publication, and represent overview-boundary edges as
   portals.
4. **Unified layout:** replace overview category lanes with deterministic,
   prerequisite-driven crossing reduction and subtree compaction.
5. **Tree polish:** route shared trunks, focus configured groups, and make Fit
   target a readable curated tree.
6. **Default progression:** audit the TaCZ 1.1.8 default weapons and connect the
   weakest-to-strongest rules into one truthful progression.
7. **Dynamic hardening:** cover add-on groups, reloads, scale limits, privacy,
   caches, and existing player state.
8. **Release validation:** repeat automated, multiplayer, GUI-scale, visual,
   documentation, and artifact gates on the resulting release candidate.

Phase 1 is implemented by translating fullscreen graph content to Z `-300`,
flushing its buffers before overlay drawing, restoring the pose in a `finally`
boundary, and making the context-card background opaque. Compact rendering stays
at Z `0`. The implementation and hands-on gate are recorded in
`docs/development/research-tree-unified-overview-phase-1.md`.

Phase 2 replaces the fullscreen rail's one-way All Weapons command with the same
camera-aware two-way transition used by compact Browse. Its pinned first slot
shows the destination (`A` / Show All Weapons or `B` / Back to Branches), while
only the group rows below it scroll. The transition retains independent cameras,
the last valid branch, and a valid focused node across both directions. Details
and validation are recorded in
`docs/development/research-tree-unified-overview-phase-2.md`.

Phase 3 adds optional server-authored `include_in_overview` group metadata,
resolves its stable kind default before publication, and synchronizes the
result under network protocol `16`. All Weapons now derives a curated subgraph;
excluded groups remain complete in Branches and global search, while real edges
crossing the overview boundary become disclosure-safe portals. The complete
implementation and compatibility record is
`docs/development/research-tree-unified-overview-phase-3.md`.

Phase 4 routes the curated All Weapons publication through a dedicated unified
layout engine. Published prerequisite ranks remain authoritative, every real
edge points bottom-to-top, barycentric ordering reduces crossings, alternating
alignment compacts forks and merges, and disconnected components are packed in
stable order without category lanes or group rectangles. Branch projections
and boundary portals remain unchanged. The implementation and validation are
recorded in
`docs/development/research-tree-unified-overview-phase-4.md`.

Phase 5 gives unified forks and merges shared centered trunks while preserving
distinct Branch ports, frames server-configured group members without restoring
group rectangles, and applies a `0.25` readability floor to overview Fit and
large group focus. The graph remains pannable when showing its entire width
would make nodes unusably small. The implementation and validation are recorded
in
`docs/development/research-tree-unified-overview-phase-5.md`.

Phase 6 audits the pinned TaCZ 1.1.8-hotfix definitions and replaces the seven
independent default components with one server-enforced progression. Taurus 943
is the sole 2 RP entry; the six other class starters branch from it at global
rank 1 while every existing within-class ordering and cost remains intact.
Leading empty authored ranks now provide a validated global-depth offset for
cross-group prerequisites. The implementation and evidence are recorded in
`docs/development/research-tree-unified-overview-phase-6.md`.

Phase 7 hardens dynamic unified publications without changing their data or
save contracts. Disconnected opted-in add-on components now use deterministic
aspect-aware grid packing instead of one ultra-wide strip; maximum fallback
populations remain outside the curated overview by default; anonymous boundary
portals remain opaque; overview-membership reloads invalidate stale cached
topology; and previously learned weapons are never revoked by new prerequisite
rules. The implementation and evidence are recorded in
`docs/development/research-tree-unified-overview-phase-7.md`.

Phase 8 closes the two post-Phase-7 hands-on crash regressions, strengthens the
packaged connected-tree, group-membership, localization, and runtime-crash gates,
and certifies the resulting JDK 17 release artifact. The complete 382-test suite,
reobfuscated artifact verification, publication readiness, and fresh client and
dedicated-server startup logs pass. Multiplayer, GUI-scale, visual interaction,
transactions, model behavior, and release screenshots remain explicit unchecked
hands-on publication gates. The implementation and evidence are recorded in
`docs/development/research-tree-unified-overview-phase-8.md`.

## Non-goals

- Replacing Minecraft's GUI framework or adding a UI-mod dependency.
- Making the client authoritative for research.
- A freeform 2D atlas of independently stacked group rectangles.
- Runtime weapon-strength inference from TaCZ gun statistics.
- Decorative connectors that imply prerequisites not enforced by the server.
- Long camera inertia or physics-based movement.
- Press-and-hold research during the initial redesign.
- A broad Compact or Recycle redesign.
