# Research Tree UI/UX Phase 5

Date: 2026-08-25

Phase 5 gives every node a readable state that does not depend on border color.
It adds procedural pixel status glyphs, layered node cards, and a distinct
marker for the server-authoritative research selection.

## Node-card anatomy

Each fixed-size node card now renders, back to front:

1. learned or default card fill;
2. a narrow state-colored header accent;
3. the published blueprint icon, anonymous question mark, or Name initial;
4. unrelated-path dimming when applicable;
5. relationship-aware border;
6. an optional authoritative-selection corner marker;
7. a lower-right status badge; and
8. local focus and search-result outlines.

The card remains the established `24 x 24` logical size, so Phase 5 does not
invalidate deterministic layout, connector routes, hit targets, or saved
compact/fullscreen viewports.

Card detail adapts to zoom so extremely large third-party trees do not issue
thousands of unreadable item and badge draws:

- at `0.30` scale and above, the complete icon and status badge render;
- from `0.10` through `0.299…`, the unique status glyph replaces the item icon;
- below `0.10`, a minimal state mark replaces sub-pixel detail while the card,
  relationship border, focus, search, and tooltip remain available.

The packaged tree's normal fitted scale remains in the detailed tier. Pixel
glyph rendering also batches contiguous pixels into horizontal runs.

## Color-independent status language

Every semantic state defined in the Phase 0 presentation contract has a unique
7×7 pixel glyph:

| State | Glyph concept |
| --- | --- |
| Redacted | Question mark |
| Preview | Eye |
| Learned | Checkmark |
| Available | Research spark |
| Insufficient RP | RP mark with warning dot |
| Discovery required | Compass diamond |
| Prerequisites required | Chain links |
| Research disabled | Slashed enclosure |
| Cost above cap | Bounded exclamation |
| Content unavailable | Cross |

The renderer uses filled logical pixels instead of Unicode symbols or a new
texture atlas. The shapes therefore remain deterministic and do not depend on
font coverage, resource-pack glyph substitutions, filtering, or external UI
libraries.

The focused details card repeats the same glyph beside its plain-language
status. Players can learn the visual vocabulary through normal use, while node
tooltips continue to spell out the exact state.

## Focus versus research selection

Local focus and the server-authoritative research selection are intentionally
different states:

- local focus keeps the existing gold outer outline;
- hover keeps the bright relationship border;
- search keeps its larger result outline; and
- the authoritative Preview/Full selection receives a white lower-left corner
  marker.

This matters when a player focuses an anonymous Silhouette or Name node. The
anonymous node can receive local focus without clearing or impersonating the
previous server-approved selection. When those two locations differ, hovering
the authoritative node explains that it is `Chosen for research`.

## Disclosure and state ownership

Status selection consumes the already synchronized availability and the local
affordability result used by the existing status text. Only Full-visible
Available nodes can produce the insufficient-RP distinction. Preview remains
Preview, and Silhouette/Name remain Redacted regardless of client inventory or
journal state.

The authoritative marker accepts only a public node whose synchronized
visibility permits server selection. Missing, stale, Silhouette, and Name IDs
clear the marker safely.

Phase 5 adds no packet, server action, persisted progression, datapack format,
or visibility-rule change.

## Automated coverage

Tests verify:

- every semantic state has one non-empty, unique 7×7 glyph;
- glyph data is bounded, immutable, and safe for out-of-range pixel queries;
- every availability and insufficient-RP condition retains the intended
  semantic mapping;
- Full and Preview nodes may display the authoritative marker;
- stale and anonymous IDs cannot display that marker;
- authoritative selection remains independent from local focus; and
- all prior canvas, routing, disclosure, graph, packet, progression, resource,
  and artifact tests remain green.

## Deferred presentation work

Phase 6 owns the richer focused-node details and next-action experience. Phase
5 deliberately keeps card geometry stable so that work can improve information
hierarchy without another tree-layout migration.
