# Research Tree planning

The Research Bench can track one revealed blueprint as a client-side goal. The
goal persists while the player remains connected, including across closing the
bench and changing Research Tree views, and is cleared on logout. Tracking does
not select a blueprint on the server, reserve inventory, spend resources, or
change any research rule.

The planner computes one deterministic playable route from the canonical
AND-of-OR requirement groups in the same disclosure-filtered graph already
published by the server. It retains every mandatory group and selects one
published alternative per unsatisfied any-of group, preferring a fully
disclosed, lower-cost route. Already learned alternatives stop recursive
planning, and a group satisfied only by a hidden or out-of-view alternative
does not invent a local prerequisite. The active canvas draws only the part of
that route present in its current projection. While a goal is active, the
existing `Next` action recommends only a currently available node inside that
selected route; the server remains authoritative for selection and research.

## Cost truthfulness

The publication contains RP cost and ingredient-type count for `PREVIEW` and
`FULL` nodes, but exact ingredient item IDs and quantities are synchronized only
for the server-confirmed selected node. The plan therefore reports:

- the number of unfinished route steps;
- the sum of published RP costs;
- the sum of published material requirement entries; and
- the next published, exact-policy step that is currently available.

If any route step is anonymous or summary-only, or a mandatory group has no
published alternative from which a route can be constructed, the UI labels the
cost summary as partial. Unknown groups are counted once by dependent and group
ordinal even when visible branches reconverge on the same closure. The planner
does not infer a hidden item, quantity, availability, identity, or prerequisite.
Datapack authors can consequently limit cost previews through the existing
Journal visibility policy without a separate planner permission.

Exact whole-route item stacks, an outside-the-bench HUD, and JEI/EMI navigation
would require separate bounded synchronization and integration work. They are
intentionally outside this first implementation so the planner cannot widen the
server's disclosure boundary or add a hard optional-mod dependency.
