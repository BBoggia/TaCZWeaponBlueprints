# Research Path Purchases

The packaged `DIRECT_LEARN` Research Tree can purchase a selected higher node
together with the unlearned prerequisite closure required to reach it. The
client only presents the server's bounded preview; the server independently
rebuilds and validates the route when Research is pressed.

## Route semantics

- Requirement groups remain ANDed.
- An already learned or progression-exempt alternative satisfies its group
  without adding cost. A progression-exempt selected target remains ineligible.
- Every nondominated alternative closure is retained while mandatory groups
  are combined. Shared prerequisites are deduplicated before the globally
  shortest closure size is selected.
- Among globally shortest closures, a route affordable with the player's live
  RP and inventory is preferred. Remaining ties use payable RP, total material
  count, material-type count, and canonical resource IDs.
- The winning closure is ordered prerequisite-first and the selected target is
  appended last.
- Missing content, blocked or disabled research, progression exemptions,
  undisclosed server-selection policy, discovery requirements, stale policy,
  cycles, and collection limits fail closed. Another valid any-of alternative
  may still be selected.

This produces one deterministic prerequisite-first closure for the current
player state. Affordability never causes a longer structural route to replace
a globally shortest one.

Exact planning is deliberately bounded. One purchase may unlock at most 1,024
blueprints, one node may retain at most 4,096 nondominated closures, and one
request may explore at most 262,144 route states. Oversized paths and route
frontiers fail closed with distinct action results instead of silently falling
back to a locally greedy route.

## Economic and transaction authority

The payable RP and material predicates of every distinct planned node are
combined. Equivalent item-list or tag predicates are aggregated, then a sparse
maximum-flow allocation resolves overlapping inventory choices exactly. A
Creative bypass is evaluated per node; partially bypassed routes charge only
the remaining nodes.

Before mutation, the server verifies the full RP balance, exact material
allocation, canonical learning targets, and learned/discovered/legacy-recipe
collection capacity. It snapshots inventory, RP, learned blueprints,
discoveries, and recipe aliases. Payment and learning then occur on the server
thread in prerequisite-first order. If consumption or any learning commit
fails, every snapshot is restored and the transaction reports failure rather
than exposing a partial route.

Research Point awards are published as one ordered batch only after the full
transaction commits. The batch reconstructs intermediate learned/discovered
counts for milestone thresholds and synchronizes presentation help once. The
synchronized result balance therefore describes the balance immediately after
paying the path and before any award callbacks.

## Preview and compatibility

Protocol 39 carries the planned unlock count, complete material-type count,
explicit oversized/complex planning states, and append-only matching action
results in addition to the Protocol 38 preview fields. RP is always the
aggregate payable cost. At most
six material rows are transferred and rendered; when more exist, compact and
fullscreen presentation report how many additional types belong to the full
cost while readiness still reflects the exact complete allocation.

If a safety bound prevents planning, the preview carries the explicit failure
state without presenting the selected node's individual cost as though it were
the full path economy.

The action becomes `Unlock N` when more than one blueprint will be
learned. Selecting and previewing never mutates progression.

`CREATE_BLUEPRINT` remains a single-node compatibility mode. It cannot promise
a multi-node permanent unlock while producing only one physical blueprint, so
unmet prerequisites retain their original rejection in that mode.
