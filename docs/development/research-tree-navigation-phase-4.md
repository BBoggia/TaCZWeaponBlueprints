# Research Tree Navigation Phase 4

Date: 2026-08-25

Phase 4 turns the synchronized publication from Phase 3 into two client-only
browse projections. It does not change progression rules, persist a player
choice, or send a view/group change to the server.

## Implemented views

`Branches` is the default view. It displays one published group at a time and
contains only that group's public members and the public prerequisite edges
whose two endpoints are in the group. Node ordinals and visible prerequisite
counts are rebuilt for the smaller graph so it continues to satisfy the same
strict graph contract as the complete publication.

`All Weapons` retains the authoritative public graph and all of its public
edges. Selecting a group in this view fits the camera around that group's
published members without filtering or rebuilding the graph. `Fit` restores
the complete atlas.

The compact and current fullscreen toolbar provide:

- a `B`/`A` view toggle with full `Branches`/`All Weapons` tooltip and
  narration;
- a bounded group selector that advances in published group order;
- global search that changes branch projection only when a Branches result is
  chosen, and otherwise centers a result in All Weapons; and
- the existing zoom, Fit, and fullscreen controls in disjoint toolbar bounds.

When All Weapons has local focus in a different group, returning to Branches
opens that focused node's group. Selecting a group in All Weapons updates only
local focus and camera state; it does not replace the menu's server-authorized
research selection.

## Projection and cache contract

`ResearchTreeProjectionCache` lazily builds a branch the first time the player
opens it. Projection objects are rebuilt when node state changes, while their
layouts are retained when graph and presentation topology are identical. A
topology change invalidates every cached layout. The All Weapons projection
uses the layout paired with the synchronized publication.

Every public edge crossing a branch boundary is retained as a typed
`CrossGroupLink`. Each link records the local public endpoint, remote public
endpoint, disclosure-safe destination group, and whether the link is a
requirement or unlock. Phase 5 will turn this metadata into visible portals;
Phase 4 never tries to reconstruct an unpublished endpoint or private group.

Empty publications produce a valid empty Branches projection. Unknown groups,
graph/layout mismatches, invalid cross-boundary links, and null navigation
state are rejected before reaching the canvas.

## Phase boundary

Phase 4 deliberately continues to use the existing deterministic layout
engine. Phase 5 owns the bottom-to-top grouped atlas, fullscreen sidebar,
visible cross-group portals, projection-specific camera restoration, and
remaining search/input polish. This keeps projection correctness separate
from the larger rendering change.

## Verification

Automated coverage now verifies:

- Branches contains only group members and internal edges;
- cross-group requirements and unlocks survive as typed links;
- All Weapons retains every public node and edge;
- state-only publications rebuild node state while reusing layouts;
- topology changes invalidate projection layouts;
- Branches is the default and stale group state falls back safely;
- Branches group selection swaps projections while All Weapons group
  selection reports a camera-focus action;
- fitting a group region leaves the All Weapons graph and server selection
  intact; and
- the minimum fullscreen toolbar keeps search, view, group, Fit, zoom, and
  fullscreen controls usable and non-overlapping.

