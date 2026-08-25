# Research Tree UI/UX Phase 6

Date: 2026-08-25

Phase 6 turns the compact focused-node card into a small decision panel. It
prioritizes the information a player needs to continue progressing and adds
direct navigation to the relationships that explain the current node.

## Player-facing hierarchy

The focused card now presents, in order:

1. the published blueprint icon and name;
2. a color-independent status glyph and one plain-language next action;
3. the RP cost or the node's disclosure level;
4. the total direct-requirement count and up to two visible requirement cards;
5. the immediate-unlock count and one visible unlock card; and
6. the existing Prepare action when the server-authoritative selection permits
   it.

The next-action text deliberately avoids implementation language. Examples
include `Earn more RP first`, `Unlock the earlier weapons first`, and `Ready —
prepare materials`. Server validation, packet state, policy flags, and point
cap mechanics remain outside the normal player-facing screen.

## Relationship cards

Requirement cards use the established gold relationship treatment. Immediate
unlock cards use the established cyan treatment. Each card reuses the public
node icon or disclosure-safe fallback and carries the same 7×7 status glyph as
the corresponding tree node. This makes completion and lock state readable
without depending on color.

The compact card has room for two requirements and one immediate unlock. Counts
communicate public overflow instead of silently implying that the displayed
subset is complete. Hidden prerequisites are omitted entirely.

Clicking a visible relationship card focuses and centers that public node.
Preview and Full nodes may also become the server-authoritative research
selection under the existing interaction policy. Silhouette and Name cards
change only local focus, preserving their disclosure boundary.

## Geometry and interaction ownership

`ResearchTreeDetailLayout` owns compact relationship-slot geometry, half-open
hit testing, and slot-to-public-target resolution. Rendering, tooltips, and
click handling use that same contract so their targets cannot drift apart.

All three 16×16 cards remain inside the established `(8, 183)` by `294×44`
detail region. They do not overlap the RP cost, summary labels, or the Prepare
button at `(232, 199)`.

## Accessibility and disclosure

Focused-node narration now includes the published name, status, requirement
count, immediate-unlock count, primary next action, and RP cost when that cost
is disclosed. Anonymous states receive the same useful relationship and action
summary without gaining hidden identity or policy data.

Tooltips identify each visible relationship by name and status and explicitly
say whether it is a direct requirement or immediate unlock.

Phase 6 adds no packet, server action, persisted progression, datapack format,
or visibility-rule change. It consumes only the atomic public graph already
published by the server.

## Automated coverage

Tests verify:

- every synchronized availability maps to one player-facing next action;
- insufficient RP changes the Available action from preparation to earning RP;
- compact relationship cards stay inside the details region and outside the
  Prepare button;
- cards do not overlap one another;
- hit testing uses half-open bounds; and
- the displayed requirement and unlock slot resolves to the same target used by
  click handling.

## Deferred presentation work

Phase 7 can reuse the same semantic model in responsive fullscreen layouts,
where the larger details panel can show more relationships and longer text
without compact clipping.
