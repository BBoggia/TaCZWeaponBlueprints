# Research Tree Navigation Phase 2

Date: 2026-08-25

Phase 2 implements the server-side publication boundary between private
research authoring data and the grouped metadata a client may receive. It does
not change the network protocol or render groups yet.

## Implemented outcome

- Added an immutable, bounded `ResearchTreePresentation` DTO containing public
  groups, public members, ranks, sibling order, and optional safe icons.
- Added `ResearchTreePublication` so a graph and its matching presentation are
  constructed and cross-validated as one value.
- Added player-specific sanitization to `ResearchTreeBuilder` while preserving
  the original graph-only API for existing callers.
- Added deterministic fallback groups for group-less datapacks and unconfigured
  add-on blueprints.
- Added topology comparison for safely reusing future projection layouts across
  player-state-only updates.

## Disclosure behavior

The effective node visibility controls publication before any group metadata
can reach a packet:

| Visibility | Published result |
| --- | --- |
| Hidden | No node, membership, group contribution, rank gap, icon, or count |
| Silhouette | Opaque node in the shared Undisclosed group |
| Name | Opaque named node in the shared Undisclosed group |
| Preview | Identified node in an authored or item-type fallback group |
| Full | Identified node in an authored or item-type fallback group |

An authored group is omitted unless it has at least one identity-disclosed
member. Anonymous members never retain their authored group. They are assigned
only to the synthetic Undisclosed group, whose title, translation key, missing
icon, and kind are validated as fixed generic metadata.

An authored icon is published only when that exact member is present in the
public authored group and reveals its icon. The publisher does not substitute
an anonymous member or expose an icon from a hidden node.

## Side-channel protection

Published authored rank values are compacted globally after filtering, then all
authored, fallback, and Undisclosed memberships are normalized against the
complete public DAG. Every prerequisite therefore has a strictly lower public
rank than its dependent, including edges that cross group or disclosure
boundaries. Sibling order is rebuilt within every retained group rank. Public
group orders are also compacted.
Consequently, hidden groups, ranks, and siblings cannot be inferred from gaps
such as authored orders 10 and 70 or ranks zero and four.

Every public graph node belongs to exactly one public group. Presentation
construction rejects unknown members, duplicate memberships, duplicate groups,
non-contiguous public orders, invalid icons, anonymous members in identifying
groups, identified members in the Undisclosed group, and ranks that contradict
a public prerequisite edge.

Cross-group edges remain the already sanitized graph edges. When an anonymous
node connects to an identified node, the edge uses only the opaque per-
publication node ID and the anonymous node remains in Undisclosed; its real
blueprint or authored group is not present in the publication.

## Fallback behavior

Identity-disclosed nodes without an authored placement are grouped by their
already-published item type. Fallback groups are ordered deterministically after
all non-empty authored groups. Their ranks use longest depth in the published
graph, so hidden prerequisites cannot affect fallback spacing.

Silhouette and Name nodes share one final Undisclosed group. Synthetic IDs are
allocated deterministically and avoid collisions with published authored group
IDs. A group-less datapack therefore receives a complete bounded presentation
without changing its progression definitions.

## Compatibility boundary

`ResearchTreeBuilder.build(...)` still returns the same disclosure-filtered
graph and now delegates through the combined publication builder. The new
`buildPublication(...)` and data-manager accessor expose matching presentation
metadata for Phase 3. No player save data, progression authority, client state,
or wire format changes in Phase 2.

## Verification

Automated tests cover all five visibility levels, hidden-only and mixed groups,
rank/order compaction, safe-icon omission, mixed-visibility cross-group edges,
group-less fallback ranks, deterministic output, synthetic-ID collisions,
complete one-group-per-node assignment, invalid classification rejection, and
topology reuse across learned-state changes.
