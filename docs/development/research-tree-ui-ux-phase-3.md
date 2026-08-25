# Research Tree UI/UX Phase 3

Date: 2026-08-25

> Historical implementation note: this phase documents the current shared-lane
> top-to-bottom renderer. `research-tree-navigation-phase-0.md` supersedes its
> direction and global lane-budget decisions for the forthcoming grouped
> Branches and All Weapons layouts.

Phase 3 replaces the original flat tier packing with a disclosure-safe layered
layout. The result preserves the established top-to-bottom progression while
making tiers and content categories visually scannable.

## Category lanes

Every published item type receives a stable vertical lane. Lane keys come only
from `ResearchTreeGraph.Node.itemType`, so they cannot recover server-redacted
metadata. Silhouette and Name nodes therefore share the single `undisclosed`
lane supplied by the server publication.

Lanes are deterministic and ordered by published key, with `undisclosed` last.
The layout shares a twelve-column budget across visible lanes. Every lane receives at least
one column, while busy lanes receive additional columns based on their population. A single
category can use the full budget; larger same-tier populations wrap
vertically. This keeps normal lanes readable while retaining bounded behavior
for unusually large content packs. The layout includes immutable lane geometry
for rendering and validation.

The canvas renders:

- alternating low-contrast lane bands;
- sticky localized category headers with a readable fallback and full-label tooltip
  for custom item types;
- compact `T1`, `T2`, and later tier markers; and
- horizontal tier separators behind the research graph.

Category headers remain visible while vertically panning, and tier markers remain
visible while horizontally panning. Their screen-space overlays do not pass clicks
through to nodes underneath them.

Only lanes intersecting the viewport are rendered, avoiding per-frame header
work for large packs with many custom item types.

## Crossing reduction

Nodes remain assigned to the tier determined by their longest visible
prerequisite path. Within each category lane, ordering now uses four bounded
top-down and bottom-up barycentric sweeps:

1. a top-down sweep orders dependents around the average position of their
   visible prerequisites;
2. a bottom-up sweep orders prerequisites around their visible dependents; and
3. component and public blueprint key provide deterministic tie-breakers.

This removes common avoidable crossings in branches and merges without an
unbounded optimization pass. Cross-category dependencies may still cross lane
boundaries; Phase 4 owns explicit routing and arrowheads.

## Bounds and compatibility

The layout remains immutable, validates non-overlapping nodes and category
lanes, and preserves the existing constructor overloads used by tests and
other internal callers. The shared budget permits up to twelve nodes per
physical row when one category owns the available width. A graph containing
one lane per maximum public node remains well below the one-million-pixel
logical canvas bound.

The client still computes layout solely from the atomic public graph
publication. No packet, server action, persisted progression, datapack format,
or visibility rule changes in this phase.

## Automated coverage

Tests cover:

- deterministic branch, merge, and component placement;
- longest-visible-path tier assignment;
- disclosure-safe Full and redacted category lanes;
- lane containment and overlap rejection;
- elimination of an avoidable same-lane crossing;
- non-overlapping maximum-size node placement; and
- existing canvas hit testing, viewport independence, relationship focus, and
  keyboard navigation after the layout change.

## Deferred presentation work

Phase 4 adds directional arrowheads and improved connector routing. Phase 5
adds status/card iconography and richer node presentation.
