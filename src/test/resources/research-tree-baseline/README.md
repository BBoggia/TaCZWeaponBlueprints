# Recorded client Tech Tree input

`live-weapons-287.json` is a replay of the typed public projection received by
the development client on 2026-09-04. It is not a synthetic catalog or a server
catalog export. It includes only public tree nodes and presentation fields;
it contains no player UUID, world name, inventory, or connection address.

The capture contains one Weapons domain, 287 nodes, 454 edges, 286 requirement
groups, 168 groups with alternatives, 19 occupied ranks, and seven automatic
families. All 287 members have automatic placement hints. Progress and
availability reflect that captured client state, not a universal starting state.

Environment: Minecraft 1.20.1, Forge 47.3.0, TaCZ 1.1.8-hotfix, Fzzy Config
0.7.6+1.20.1+forge, JDK 17.0.12, mod 1.3.1-dev based on commit 63b897d.
The viewport was 494×273 GUI units at effective GUI scale 7, with a
3456×1908 framebuffer. The file stores the effective layout policy and width.

Canonical input SHA-256:
`378c48b56b5cd1843cd64addb0bf837f112d7ec32f7ebab4733f1e9082edf074`.

Keep this input stable when changing the renderer. Capture additional inputs
under new descriptive names when catalog or disclosure coverage changes.
The replay parser reconstructs production graph/projection types so invalid
members, groups, counts, or placements fail validation.

See [the baseline workflow](../../../../docs/research-tree-baseline.md) for
capture and measurement commands. Runtime timings and screenshots are local
development evidence and are not required to run tests.
