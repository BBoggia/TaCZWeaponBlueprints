# Research Tree Phase 3: Atomic Synchronization

Date: 2026-08-24

## Outcome

Phase 3 synchronizes the Phase 1 disclosure-filtered graph from the server and
atomically publishes it with the matching Journal and Phase 2 layout on the
client. One generation ID now covers both models. Tree updates are sent on
login and whenever topology or tree state changes; point-only Journal updates
reuse the already-published graph and layout.

The network protocol is now version 10. Clients with an older action, state, or
tree wire format are rejected during connection negotiation.

## Bounded wire format

`SyncResearchTreePacket` uses the existing 900,000-byte chunk budget and the
16-chunk snapshot ceiling. Each chunk repeats only bounded synchronization
metadata and carries a portion of the nodes and edges.

Nodes synchronize their already-disclosed presentation, explicit availability
reason, and player-specific state. Affordability remains in the Journal because
it is derived from the current point balance and should not force a full tree
transfer. Edges use pairs of bounded node ordinals rather than repeating two
full resource IDs. This retains the validated ID-based graph after assembly
while keeping dense trees comfortably inside the payload budget.

The decoder rejects invalid chunk indices, totals, counts, visibility levels,
flag bits, resource IDs, text lengths, state values, and edge ordinals before
the data can reach the client model. Accumulated node, edge, group, and member
counts are also checked after every accepted chunk, so an incomplete generation
cannot retain more data than its declared totals.

## Atomic assembly and stale data

The client accumulators accept chunks out of order and publish nothing until
the Journal and its declared tree generation are both complete. Duplicate
chunks do not advance completion, conflicting duplicates are rejected, and an
older synchronization cannot replace a newer in-progress or completed graph.
Logout clears both accumulators and the combined publication.

After assembly, `ResearchTreeGraph` revalidates node ordinals, edge endpoints,
counts, duplicates, limits, and acyclicity. `ClientResearchState` publishes the
matching Journal, graph, and layout through one volatile reference. State-only
tree changes reuse the existing layout when IDs, item types, and edges are
unchanged.

## Verification

Automated coverage includes:

- maximum 4,096-node multi-chunk round trips;
- reverse-order chunk assembly;
- compact ordinal-edge reconstruction;
- duplicate and completed-snapshot handling;
- replacement of an incomplete graph by a newer graph;
- rejection of stale remainders;
- malformed counts, hidden visibility, and invalid edge ordinals.
