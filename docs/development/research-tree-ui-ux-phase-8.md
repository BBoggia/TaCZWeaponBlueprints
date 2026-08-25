# Research Tree UI/UX Phase 8

Date: 2026-08-25

Phase 8 completes the Research Tree redesign with lightweight first-visit
guidance, disclosure-safe category focus, and the final accessibility and input
polish required by the Phase 0 contract.

## Three-step first-visit guidance

The first Browse visit displays exactly three short instructions:

1. pick a weapon to see what it needs;
2. gold identifies requirements and blue identifies unlocks; and
3. drag to move and scroll to zoom.

The guide is an opaque, bounded panel inside the active tree canvas. Its
dismiss button stays inside the panel at compact, wide, medium, expanded-drawer,
and minimum supported fullscreen sizes. The panel intercepts underlying clicks,
scrolling, hover paths, and node tooltips without blocking the toolbar or
focused details.

`Got it` dismisses the guide immediately and writes one client-only preference
to `config/taczweaponblueprints-client.properties`. A failed write still
dismisses it for the current session. Malformed or absent preference data fails
open to showing help rather than trapping the player in an unexplained UI.

The compact `?` control can reopen help without resetting the saved dismissal.
The help control, dismissal, and complete three-step summary participate in
normal screen narration.

## Category focus

The final toolbar control now implements the Phase 0 category-filter contract.
It cycles through the deterministic category lanes already published in the
Research Tree layout and then returns to `All`.

Selecting a category:

- fits that lane in the active viewport;
- gives its sticky header a gold outline and label;
- dims nodes in other lanes while keeping them visible; and
- preserves dependency connectors, focused nodes, and search results.

Keeping the complete topology visible avoids making cross-category
requirements appear to vanish. `Fit` clears category focus and restores the
complete graph.

Category matching uses only `ResearchTreePresentationContract.categoryLane`.
Silhouette and Name nodes therefore remain in the public `undisclosed` lane;
the filter cannot recover their true type or identity. Custom published keys
use the same localized-label fallback as sticky lane headers.

## Persistence and failure behavior

The guidance preference owns only one mod-specific client file. It updates
memory before attempting disk persistence, writes through a same-directory
temporary file, requests an atomic replacement when supported, and falls back
to a normal replacement when the filesystem cannot provide atomic moves.

No preference is synchronized to a server or associated with player
progression. Removing the client preference file intentionally restores the
first-visit guide.

## Accessibility and input polish

- category, fullscreen, drawer, and help controls have localized narration;
- icon-sized controls have localized tooltips;
- the guide receives a concise complete narration;
- selected category emphasis uses text, viewport framing, and an outline in
  addition to color;
- relationship roles retain textual tooltips and status glyphs; and
- half-open guide and relationship bounds prevent adjacent controls from
  sharing edge clicks.

Phase 8 adds no UI library, packet, protocol revision, server action, persisted
progression, datapack format, or visibility-rule change.

## Automated coverage

The final gates cover:

- all-to-category-to-all cycling and stale-category recovery;
- Full and anonymous category matching without disclosure recovery;
- category viewport fitting and invalid-category rejection;
- guidance containment at every responsive layout;
- half-open guidance hit boundaries;
- dismissal persistence across preference instances;
- malformed preference recovery and failed-write session dismissal; and
- every Phase 0 through Phase 7 graph, interaction, synchronization,
  progression, resource, and artifact test.

## Completion

All eight implementation phases of the Research Tree UI/UX plan are now
represented in production code. Remaining work is release-candidate visual QA
inside Minecraft at the supported GUI scales and window sizes.
