# Research Tree UI/UX Phase 7

Date: 2026-08-25

Phase 7 activates the responsive fullscreen Research Tree defined by the Phase
0 contract. Fullscreen remains a presentation state of the already-open
Research Bench menu; it does not open a second container, move inventory slots,
or add a server action.

## Entering and leaving fullscreen

The compact Browse toolbar now includes an icon-sized fullscreen control with
a localized tooltip and narration label. Entering fullscreen preserves the
published graph, focused node, search query, search focus, and authoritative
selection. It switches the canvas to its independent fullscreen viewport,
which is fitted only on first use and retained on later visits.

Fullscreen exits when the player:

- presses Escape;
- activates the return-to-bench control;
- opens the inventory-backed Prepare view;
- switches to Recycle; or
- resizes below the supported `260×180` interaction surface.

Escape is consumed when it leaves fullscreen. A later Escape from the compact
Bench retains normal Minecraft menu-closing behavior.

## Responsive layouts

The live screen now uses all three tested Phase 0 breakpoints:

- `700×360` and larger: a large canvas with a tall right details panel;
- `480×300` and larger: a wide canvas with a bottom details panel; and
- smaller supported surfaces: a canvas with an expandable bottom drawer.

The small drawer can collapse to 24 pixels, returning the reclaimed height to
the canvas while retaining the focused blueprint's name and its expand
control. Resize rebuilds widget geometry against the current breakpoint while
keeping focus, search, and the active fullscreen viewport valid.

## Responsive focused details

The Phase 6 information hierarchy is reused rather than replaced. Right,
bottom, and expanded-drawer panels show the published icon/name, status glyph,
primary next action, RP cost or visibility summary, relationship counts, and
the disclosure-safe requirement/unlock cards.

Fullscreen panels can display up to eight direct requirements and eight
immediate unlocks. Counts remain authoritative when authored content exceeds
the available row. Visible cards remain clickable and center their public
nodes; hidden prerequisites remain absent from the public presentation.

`ResearchTreeDetailLayout` now owns responsive card rows, the Prepare action,
the drawer toggle, and hit testing. Tests assert that all controls remain in
the details panel and that relationship cards never overlap Prepare.

## Toolbar and accessibility

Search, zoom, Fit, Center, and fullscreen controls are rebuilt from the active
responsive toolbar contract. Icon-sized fullscreen and drawer controls include
localized tooltips and narration. Focused-node narration from Phase 6 remains
active in both compact and fullscreen presentations.

No progression or interaction depends on color alone: responsive relationship
cards retain gold/cyan outlines, status glyphs, and textual tooltips.

## Authority and disclosure

Fullscreen consumes the same atomic `ClientResearchState.Publication` as the
compact screen. It cannot request selection of Silhouette or Name nodes and
cannot reveal IDs, icons, categories, costs, readiness, or hidden topology that
the server did not publish.

Phase 7 adds no packet, protocol revision, server action, persisted data,
datapack format, dependency, or visibility-rule change.

## Automated coverage

The Phase 7 gates cover:

- right, bottom, expanded-drawer, and collapsed-drawer geometry;
- minimum-size and inclusive-breakpoint behavior;
- independent compact/fullscreen viewport state;
- responsive relationship-slot containment and hit testing;
- Prepare and drawer-toggle containment;
- relationship-card and Prepare non-overlap;
- drawer collapse reclaiming canvas height; and
- Escape leaving fullscreen before the Bench closes.

## Deferred presentation work

Phase 8 owns the final first-use guidance and polish pass. Fullscreen itself is
complete and requires no external UI mod or rendering dependency.
