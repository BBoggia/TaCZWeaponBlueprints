# Research Tree Navigation Phase 7

Date: 2026-08-25

Phase 7 hardens the grouped research-tree implementation at its publication,
layout, disclosure, and lifecycle boundaries. It does not change datapack
formats, saved player progression, or network protocol 14.

## Client publication lifecycle

The Journal and research tree continue to become visible as one atomic client
publication. Generation handling now also tolerates completed results arriving
across the two bounded packet accumulators in an unexpected order:

- completed inputs older than the currently published generation cannot
  regress client state;
- the first publication accepts the complete signed generation range used by
  the server's randomly seeded sequence;
- a point-only Journal update reuses the newest completed tree at or before its
  generation, including a tree that is complete but still awaiting its matching
  Journal;
- publishing an older point-only generation does not discard a future pending
  tree;
- a newer full-tree generation invalidates older unmatched halves; and
- disconnect clearing removes both the visible publication and every partial
  publication.

This closes an edge case where a completed replacement tree could previously be
discarded while a later point-only update reused the older visible topology.
The normal Forge packet order already makes that sequence unlikely, but the
client boundary no longer depends on it for correctness.

## Maximum-scale coverage

Automated tests now exercise the declared 4,096-node boundary rather than only
representative small graphs:

- one publication contains all five visibility tiers across 4,096 catalog
  entries: 820 Hidden entries, 1,638 anonymous Silhouette/Name entries, and
  1,638 identified Preview/Full entries;
- Hidden entries contribute no node or membership, anonymous entries use only
  opaque IDs and the Undisclosed group, and only Preview/Full entries retain
  authored membership and a safe authored icon;
- 4,096 disclosed fallback item types produce the maximum 4,096 deterministic
  fallback groups within the publication time bound;
- an All Weapons atlas containing 4,096 one-node groups lays out twice
  identically, keeps every group region separated, and remains below
  `ResearchTreeLayout.MAX_DIMENSION`; and
- the existing 4,096-node disconnected branch test continues to prove bounded
  two-axis placement rather than an unusable single stack.

The timeout assertions are regression ceilings for runaway behavior, not
player-facing performance targets. The implementations remain deterministic
and bounded by the public node, edge, group, member, and canvas-dimension
limits.

## Reload and fallback coverage

Reload-oriented tests now verify that:

- adding an unconfigured content-pack weapon creates its deterministic fallback
  group;
- removing that weapon removes its node and all related fallback metadata;
- restoring the same content in a different map insertion order recreates the
  exact same publication;
- group addition, removal, and reassignment from one research profile to
  another produce independent immutable snapshots; and
- invalid profile or group preparation still leaves the last published
  snapshot untouched.

Together with the existing navigation-state tests, a replacement publication
also discards stale group selections, focused nodes, and search matches while
retaining state that is still valid.

## Acceptance status

The automated Phase 7 contract now covers disclosure, deterministic fallback,
maximum branch and atlas scale, publication ordering, reload preparation, and
disconnect cleanup. The direct-inventory research transaction, packet bounds,
projection cache reuse, hidden cross-group relationships, and stale navigation
fallbacks remain covered by their earlier focused suites.

Phase 8 owns hands-on in-game verification across GUI scales, window sizes,
long translations, real content-pack add/remove reloads, integrated and
dedicated servers, and final release preparation. Those visual and runtime
checks cannot be replaced by headless unit tests.
