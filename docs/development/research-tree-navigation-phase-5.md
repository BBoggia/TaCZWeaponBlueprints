# Research Tree Navigation Phase 5

Date: 2026-08-25

Phase 5 completes the visual and interaction layer for the grouped Research
Tree publication introduced in Phases 1 through 4. The implementation remains
client-only: it does not change prerequisite authority, research costs,
inventory consumption, learned data, or the network protocol.

## Bottom-to-top progression

Both the fallback DAG layout and the authored grouped layout now place lower
ranks toward the bottom and stronger, later ranks toward the top. A connector
leaves the top of its prerequisite and terminates with an upward arrowhead at
the bottom of the weapon it unlocks.

Keyboard navigation follows the same model. Up prefers an immediate unlock;
Down prefers an immediate requirement. Left and Right continue to choose
spatial neighbors on the same rank.

## Branch and atlas layouts

`ResearchTreeGroupedLayoutEngine` uses the server-published group order, rank,
and sibling order directly. Branches receive one bounded region. All Weapons
places every group in its own non-overlapping horizontal region on one logical
canvas, with ranks aligned bottom-to-top across regions.

Disconnected siblings use a near-square grid instead of a single stack. Group
and canvas dimensions remain bounded by `ResearchTreeLayout.MAX_DIMENSION`.
The maximum 4,096-node disconnected branch is covered by a timed regression
test and uses both axes.

Group headers remain pinned to the visible top of the canvas while their
regions pan beneath them. Selecting a group in All Weapons fits the complete
published region and retains every graph node and edge.

## Fullscreen navigation

Fullscreen now reserves disjoint rectangles for the toolbar, a 96-pixel
Weapon Trees sidebar, and the canvas. The sidebar begins with All Weapons and
then lists groups in published order. It creates only enough buttons for
visible rows, so thousands of groups do not create thousands of live widgets.

The mouse wheel scrolls the sidebar without zooming the canvas. Sidebar
buttons participate in normal tab navigation, use full tooltips for clipped
labels, and keep the selected branch in view after search, portal navigation,
or a publication change. Compact mode retains the bounded branch selector.

## Cross-group portals

Every published edge crossing the active Branches projection becomes a typed
portal marker. Gold downward markers lead to requirements in another branch;
blue upward markers lead to later unlocks. Hover text names only the
disclosure-safe destination group, and clicking changes local projection and
focus without submitting a research selection to the server.

Portal banks reserve sufficient branch width and use separate hit targets, so
multiple links on one node do not collapse into one inaccessible marker. No
portal is synthesized from hidden nodes, private IDs, or client-side guesses.

## Search, cameras, and details

Search remains global to the synchronized public graph. A Branches result
opens its public group, while an All Weapons result centers in the complete
atlas. Up/Down cycles deterministic matches and Enter performs the normal
validated selection behavior.

Camera snapshots are isolated by compact/fullscreen mode and by projection:
All Weapons has its own camera, and every Branches group has its own camera.
State-only publications retain those snapshots; topology changes discard them
safely. Switching views or returning to a branch restores its prior pan and
zoom unless the player explicitly asked to center a search result, group, or
portal destination.

Fullscreen hover continues to show concise contextual details. Clicking a
node pins the same information without restoring a permanent bottom panel;
clicking empty canvas space closes the floating detail while preserving the
focused path. Anonymous nodes may be pinned locally but still cannot submit a
server selection or research action.

## Verification

Automated coverage verifies:

- deterministic non-overlapping group regions;
- authored ranks and fallback tiers render bottom-to-top;
- upward connector routing, fan-in/out ports, and viewport culling;
- bounded near-square layout for 4,096 disconnected nodes;
- Branches and All Weapons reuse the grouped layout engine;
- clickable disclosure-safe portal hit targets and non-overlapping banks;
- All Weapons region focus does not filter its graph;
- independent atlas and per-branch camera snapshots;
- disjoint minimum-size fullscreen toolbar, sidebar, and canvas geometry;
- first-visit guidance still fits the minimum canvas; and
- the complete pre-existing automated suite remains compatible.

In-game GUI-scale, localization, world-background, and interaction checks
remain in `docs/research-tree-manual-qa.md` for Phase 8 release validation.

