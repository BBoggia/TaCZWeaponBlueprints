# Research Tree UI/UX Phase 0 Contract

Date: 2026-08-25

> Historical implementation note: this contract originally selected
> top-to-bottom progression. The grouped-navigation Phase 0 contract supersedes
> that direction with bottom-to-top presentation while preserving logical tier
> numbers and prerequisite semantics. See
> `research-tree-navigation-phase-0.md`.

Phase 0 defines the interaction, disclosure, responsive-layout, and
accessibility contracts for the Research Tree redesign. It intentionally keeps
the current live presentation while creating testable foundations for later
rendering phases.

## Player questions

The redesigned Browse view must answer these questions in order:

1. Which blueprint am I looking at?
2. What does it require?
3. Which requirements have I completed?
4. Why can I not research it yet?
5. What will it unlock?
6. What action should I take next?

Implementation details such as server validation, policy eligibility, packet
state, or disclosure ceilings do not belong in normal player-facing copy.

## Direction and relationship language

The implementation described by this historical phase runs from top to bottom.
The active grouped-navigation contract requires a future layout phase to place
prerequisites at the bottom and dependents above them, with an upward arrowhead
at the dependent end.

One focused node defines the following priority-ordered roles:

| Role | Meaning | Planned treatment |
| --- | --- | --- |
| Selected | Current local focus | Strong double outline |
| Direct requirement | Immediately required by the focus | Solid gold |
| Requirement path | Earlier visible ancestor | Muted gold |
| Direct unlock | Immediately unlocked by the focus | Solid cyan |
| Unlock path | Later visible descendant | Muted cyan |
| Unrelated | Outside the focused progression path | Dimmed |
| Neutral | No active focus | Normal authored state |

Symbols and line shape must carry the same meaning as color. Hover emphasizes
direct relationships. Selection emphasizes the complete visible path.

## Node status language

Every synchronized availability has a non-color status symbol:

| Synchronized state | Planned symbol concept |
| --- | --- |
| Redacted | Question mark |
| Preview | Blueprint or eye |
| Learned | Checkmark |
| Available | Research spark |
| Insufficient RP | RP badge with warning |
| Discovery required | Compass |
| Prerequisite required | Chain link |
| Research disabled | Disabled research symbol |
| Cost above cap | Capped RP warning |
| Content unavailable | Broken or missing blueprint |

The exact pixel assets are a Phase 5 decision. Phase 0 locks down the semantic
roles through `ResearchTreePresentationContract` so later textures cannot
silently change behavior.

## Disclosure requirements

The presentation consumes only the server-published graph. UI work must not
infer or recover restricted metadata.

- Hidden publishes no node.
- Silhouette may show anonymous topology and a question-mark node.
- Name may add only the published name.
- Preview may use published identity, icon, category, and aggregate costs.
- Full may add exact learned, readiness, affordability, and recycling state.
- Category lanes use the published item type only when identity is revealed.
  All lower-visibility nodes belong to one `undisclosed` lane.
- Local focus may use opaque public node keys. Server selection remains blocked
  below Preview.
- Relationship highlighting may reveal only topology already present in the
  synchronized graph.

## Compact layout baseline

The current 310 by 240 Research Bench remains the compact baseline:

- toolbar: `(8, 43)` sized `294 x 18`;
- tree canvas: `(8, 64)` sized `294 x 116`;
- focused details: `(8, 183)` sized `294 x 44`.

`ResearchBenchScreen` now reads its compact canvas and detail geometry from the
pure `ResearchTreeScreenLayout` contract. Phase 1 can therefore extract the
canvas without changing the current placement.

The target compact toolbar contains Search, Zoom Out, Zoom In, Show All,
Category Filter, and Expand. Buttons may use icons plus localized tooltips when
the compact width cannot safely fit text.

## Fullscreen contract

Fullscreen is a presentation state inside the existing open Research Bench
screen. It is not a separate server menu and does not widen disclosure or add a
network action.

- Expand is available only from Browse.
- Entering fullscreen keeps the current graph, focus, search, and publication.
- Compact and fullscreen retain independent pan/zoom viewports.
- Escape exits fullscreen before the normal close-menu behavior.
- Prepare Research returns to the compact inventory-backed Prepare view.
- Switching to Recycle also leaves fullscreen.
- A live server publication replaces Journal and tree state atomically in both
  presentations.
- Resizing recomputes geometry while keeping a valid focused node.

Responsive layouts are selected from logical GUI dimensions:

- At least `700 x 360`: canvas with a right-side detail panel.
- At least `480 x 300`: canvas with a bottom detail panel.
- Smaller supported dimensions: canvas with a collapsible detail drawer.
- Minimum contract surface: `260 x 180`.
- Primary release target at the smallest normal Minecraft GUI size: `320 x
  240`.

## Interaction contract

- Single click focuses a node. Preview and Full nodes may also become the
  authoritative server selection under the existing rules.
- Clicking a requirement or unlock in the details panel focuses and centers it.
- Dragging empty canvas space pans.
- Mouse wheel and `+`/`-` zoom around the cursor or canvas center.
- Show All fits the complete visible graph with padding.
- Arrow keys prefer graph relationships before spatial neighbors.
- Up prefers prerequisites; Down prefers dependents.
- Search exposes only fields permitted by the node's visibility.
- Enter focuses the active search result.
- Escape leaves fullscreen first, then closes the Bench on a later press.
- Prepare is never enabled for a merely local anonymous focus.

## Accessibility and copy

- No progression or status distinction may depend only on color.
- Focus, hover, and server selection remain visually distinguishable.
- Every icon-only control receives a localized tooltip and narration label.
- Narration states the selected name, status, direct requirement count, direct
  unlock count, and primary next action when disclosure permits.
- Tab order follows Search, zoom, Show All, filter, fullscreen, details actions.
- Long translations may wrap in the details panel but never overlap the canvas
  or action buttons.
- First-visit guidance is limited to three short instructions and can be
  dismissed persistently.

## Phase 0 automated gates

- Compact canvas and detail geometry remain unchanged.
- Toolbar controls are contained, non-overlapping, and retain a usable search
  field at every supported responsive size.
- Fullscreen primary regions remain on-screen and non-overlapping.
- Right, bottom, and drawer detail placements select at their defined
  breakpoints.
- Collapsing the small-screen drawer increases usable canvas height.
- Category grouping cannot recover a redacted item type.
- Every availability maps to a color-independent symbol role.
- Escape behavior distinguishes fullscreen from normal Bench closing.

## Deferred work

Phase 0 does not add the fullscreen button, relationship highlighting, category
layout, arrowheads, new node cards, guidance overlay, or visual assets. Those
changes remain isolated in Phases 1 through 8 so each step can be reviewed and
tested independently.
