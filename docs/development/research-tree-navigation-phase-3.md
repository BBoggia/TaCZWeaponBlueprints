# Research Tree Navigation Phase 3

Date: 2026-08-25

Phase 3 synchronizes the Phase 2 disclosure-safe group presentation and its
matching public graph as one client publication. It does not render the new
Branches or All Weapons projections; that remains Phase 4.

## Wire contract

`SyncResearchTreePacket` now carries four bounded tables under one sync ID:

- public graph nodes;
- prerequisite edges encoded with public node ordinals;
- public groups containing only disclosure-safe title, translation, kind, and
  optional icon metadata;
- group members encoded as node ordinal, public rank, and sibling order.

The packet header declares total nodes, edges, groups, and memberships. Every
non-empty graph must have at least one group, and total memberships must equal
the public node count. Group members and icons use the graph's public ordinal
table so no blueprint ID is repeated or recovered from private authoring data.

Nodes, edges, and groups share the existing 900,000-byte chunk budget and
16-chunk snapshot limit. Groups remain whole within a chunk. Counts are checked
before allocation and cumulative table totals are checked after every accepted
chunk; string, enum, ordinal, rank, order, icon, and membership bounds are
checked while decoding.

## Atomic reconstruction

The client accumulator accepts chunks out of order but publishes nothing until
all declared tables are complete. It rejects stale generations, conflicting
duplicates, inconsistent headers, missing entries, duplicate node ordinals,
invalid edge or member ordinals, non-contiguous orders, and malformed group
metadata.

After the tables pass their wire checks, the accumulator rebuilds and validates
`ResearchTreeGraph`, `ResearchTreePresentation`, and
`ResearchTreePublication`. This final boundary proves that every public node is
assigned exactly once, every icon belongs to its group, anonymous nodes belong
only to Undisclosed, identifying groups contain only identity-disclosed nodes,
no group references a missing graph node, and every public prerequisite has a
strictly lower rank than its dependent.

Graph and presentation therefore cannot become visible independently. They are
passed to `ClientResearchState` together and are paired with the Journal only
when both use the same generation.

## Lifecycle and reuse

The server caches the last research publication per connected player. A point-
only or otherwise unchanged update may send a new Journal with an explicit
reuse marker instead of retransferring the tree. Because the cached value now
contains both graph and presentation, reuse cannot combine a current graph with
stale groups.

A changed publication sends the Journal and all tree chunks under one new
generation. Login, respawn, dimension changes, reloads, progression changes,
and disconnect cleanup retain the previous synchronization lifecycle. Logout
removes the server cache, while client logout clears the Journal, combined tree
publication, and both packet accumulators.

The existing whole-graph layout may still be reused when only public node state
changes and graph topology is identical. Phase 4 projection caches will use the
combined graph-and-presentation topology comparison already exposed by
`ResearchTreePublication`.

## Disclosure guarantees

Phase 3 never synchronizes private group definitions and never asks the client
to hide them. Hidden nodes have no node or membership. Silhouette and Name nodes
use opaque public IDs and fixed Undisclosed metadata without an icon. Preview
and Full nodes may carry only the already-sanitized authored or fallback group
metadata produced in Phase 2.

Malformed Undisclosed groups and any reconstructed publication that assigns an
anonymous node to an identifying group fail closed before client publication.

## Compatibility

The custom network protocol advances from `13` to `14`. Client and server mod
versions must match. No player NBT, learned-blueprint identity, Research Point,
research profile, research rule, or research-tree-group resource format changes
in this phase, so no world or datapack migration is required.

## Verification

Automated coverage verifies:

- multi-chunk, out-of-order graph-and-presentation reconstruction;
- ordinal edge and group-member round trips;
- authored, fallback, and Undisclosed group metadata;
- titles, translation keys, icons, ranks, sibling orders, and kinds;
- maximum public node and dense-edge tables within the byte budget;
- stale generations, duplicate chunks, conflicting chunks, and incorrect totals;
- invalid visibility, enum, edge ordinal, member count, and disclosure pairing;
- Journal/tree publication only after matching generations; and
- layout and presentation reuse across point-only and state-only updates.

Phase 4 can now consume one immutable client presentation without reading
server resources or inferring groups from graph geometry.
