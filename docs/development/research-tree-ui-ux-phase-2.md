# Research Tree UI/UX Phase 2

Date: 2026-08-25

Phase 2 adds indexed public relationships and focused-path behavior to the
reusable Research Tree canvas.

## Relationship index

`ResearchTreeRelations` builds deterministic direct adjacency tables whenever
published topology changes:

- blueprint to its direct prerequisites; and
- blueprint to the blueprints it directly unlocks.

It does not build an all-pairs transitive closure. Selecting one public node
performs bounded iterative traversals over the two adjacency tables and creates
one immutable `FocusPath` containing:

- direct requirements;
- earlier visible requirements;
- direct unlocks; and
- later visible unlocks.

This makes focus changes `O(nodes + edges)` in the worst case while avoiding a
potentially quadratic retained index for large content packs. Hover paths use
only direct adjacency and do not perform transitive traversal.

## Canvas behavior

The canvas rebuilds its relationship index only with layout topology. A
player-state-only publication reuses the index and recalculates the current
focus roles against the retained public focus.

Selected focus now applies these treatments:

- selected: strong gold focus outline;
- direct requirement: bright gold;
- earlier requirement: muted gold;
- direct unlock: bright cyan;
- later unlock: muted cyan; and
- unrelated branch: subdued connector and a translucent node overlay.

Search matches remain visible even when outside the selected path. Hovering a
node temporarily emphasizes that node and only its direct requirements and
unlocks without discarding the selected complete path.

Node tooltips include a localized text relationship such as `Direct
requirement`, `Earlier requirement`, `Unlocked next`, or `Outside the selected
path`. The relationship is therefore not communicated only through color while
arrowheads and node symbols remain scheduled for later phases.

## Disclosure and authority

The index consumes only the server-published `ResearchTreeGraph`. Anonymous
Silhouette and Name nodes participate through their validated opaque public
keys. No real ID, category, icon, policy state, or server action is recovered.

Relationship focus remains entirely client-local. Preview and Full selection
continues through the existing server-authoritative Research Bench action, and
lower visibility tiers remain non-selectable by the server.

No packet, protocol, persisted progression, or datapack format changes in this
phase.

## Automated coverage

Tests cover:

- branch and merge adjacency;
- direct and transitive ancestor roles;
- direct and transitive descendant roles;
- unrelated components;
- connector relationship roles across a complete focused path;
- direct-only hover paths;
- canvas focus and hover integration;
- immutable deterministic results; and
- focus construction over the maximum 4,096-node public graph within the
  bounded performance gate.

The complete graph, layout, viewport, canvas, disclosure, screen, packet,
progression, artifact, and resource suites remain part of the build gate.

## Deferred presentation work

Phase 2 does not change authored node placement or connector geometry. Tier and
category layout is Phase 3, arrowheads and improved routing are Phase 4, and
status/card iconography is Phase 5.
