# Research Tree Navigation Phase 0 Contract

Date: 2026-08-25

Phase 0 defines the product, data, disclosure, compatibility, and verification
contracts for the grouped Research Tree redesign. It deliberately does not
change the live graph synchronization or node layout. Phases 1 through 8 must
implement this contract without changing blueprint progression authority.

Phase 1 accepted the provisional resource spelling and JSON shape unchanged.
Its implementation record is
`development/research-tree-navigation-phase-1.md`.

## Player outcome

The Research Bench must answer three questions before presenting secondary
information:

1. Which kind of weapon do I want to work toward?
2. Which earlier weapon unlocks this one?
3. Can I research it now?

The normal interface must use short player-facing language. Terms such as
projection, topology, disclosure ceiling, publication generation, and packet
validation remain implementation details.

## Locked terminology

The player-facing view names are:

- **Branches**: one authored or fallback weapon group at a time;
- **All Weapons**: every published group on one canvas.

Internal code may use `group projection` and `atlas projection`, but those
terms do not appear in normal UI copy. `Branches` is the default for a player
with no saved local preference. A later client-only preference may restore the
last view and selected group; it never affects server progression.

The complete tree remains one authoritative graph. Switching views does not
create a second progression, change prerequisites, or send a progression
action to the server.

## Progression direction

Logical tier numbers keep their existing meaning:

- tier zero is a prerequisite root;
- tier one requires a tier-zero path;
- larger tiers are farther along the progression.

Presentation reverses the current screen direction:

- lower tiers appear toward the bottom;
- higher tiers appear toward the top;
- a connector leaves the top edge of a prerequisite;
- it enters the bottom edge of the blueprint it unlocks;
- its arrowhead points upward at the dependent blueprint.

This is a presentation change only. Research rules continue to list
prerequisite IDs in the same order and with the same meaning. Stored learned
blueprints require no migration.

Keyboard direction follows the new visual direction. Up prefers an immediate
unlock; Down prefers an immediate requirement. Left and Right remain spatial
movement among comparable nodes.

## View contracts

### Branches

Fullscreen Branches reserves a scrollable sidebar outside the canvas. The
sidebar begins with `All Weapons`, followed by explicit groups in authored
order and deterministic fallback groups. Selecting a group replaces only the
client projection and restores that group's previous camera.

The selected group displays its members and the published relationships among
them. A published edge crossing the group boundary becomes a portal:

- a disclosed destination can name its destination group and navigate to it;
- an undisclosed destination uses generic copy and a generic marker;
- no portal may expose an unpublished edge, ID, category, icon, or group.

Compact mode uses a bounded group selector instead of forcing a permanently
visible sidebar into the existing 310 by 240 bench.

### All Weapons

All Weapons displays every published group in one logical canvas. Groups form
ordered horizontal regions, while tiers share the same bottom-to-top
direction. The view may be much wider than the viewport.

Selecting a group in the sidebar does not filter the graph. It fits or centers
that group's region. `Fit` restores the complete atlas. Search centers a
matching node without changing its server selection state.

All Weapons is not required to invent a false shared prerequisite. The seven
default roots may remain independent components presented together.

## Canvas and control ownership

Toolbar, sidebar, compact selector, and floating details receive disjoint
screen rectangles from the active layout. The canvas handles input only inside
its assigned rectangle. An overlay may never rely on render order alone to
win mouse events from the canvas.

Input ownership is:

| Pointer location | Wheel | Primary click | Drag |
| --- | --- | --- | --- |
| Sidebar | Scroll groups | Select/focus group | Scroll if supported |
| Search or toolbar | Widget behavior | Widget behavior | None |
| Node | Canvas zoom | Focus; second click may research | Node does not start pan |
| Empty canvas | Canvas zoom | Clear transient hover | Pan canvas |
| Floating details | Scroll details if needed | Pin/action/navigation | Move only if later authored |

The existing direct-inventory Research action remains server-authoritative.
The presentation must not reintroduce a player-facing Fill or Prepare workflow.

## Node details

Fullscreen does not reserve a permanent bottom information strip. Hover opens
a concise tooltip. Clicking a node pins the same information in a compact
floating card whose placement avoids the toolbar, sidebar, cursor, screen
edges, and selected node when possible.

The information order is:

1. published name or anonymous label;
2. plain-language state;
3. direct requirements and immediate unlocks;
4. disclosed RP/material summary;
5. Research action when allowed.

Closing the pinned card does not clear the selected graph path. Anonymous Name
or Silhouette nodes may receive local focus but cannot produce a server
selection or Research action.

## Search and camera state

Search is global to the current publication.

- In Branches, selecting a result switches to its disclosed group and centers
  it. An undisclosed result switches to the shared Undisclosed group.
- In All Weapons, selecting a result centers it without filtering the atlas.
- Repeated next/previous commands cycle deterministic result order.
- A publication change removes results that are no longer public.

Camera state is independent for compact, All Weapons, and each Branches group.
Camera state is client-only and may be discarded safely if its group or node
vanishes after reload.

## Presentation resource boundary

Research dependencies, costs, discovery requirements, visibility, and actions
remain in `research_profiles` and `research_rules`. Group presentation is a
separate reloadable resource so old rules do not acquire a new required field
and existing datapacks continue to load.

The provisional resource location is:

```text
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
```

A group definition is associated with one research profile and contains:

- bounded player-facing title metadata;
- a disclosure-safe representative icon;
- a deterministic sidebar order;
- exact blueprint membership arranged into authored ranks;
- optional stable ordering among siblings.

Provisional authoring shape:

```json
{
  "format": 1,
  "profile": "taczweaponblueprints:duplicate_recovery",
  "title": "Pistols",
  "translation_key": "gui.taczweaponblueprints.research_group.pistols",
  "icon": "tacz:m1911",
  "order": 10,
  "ranks": [
    ["tacz:m1911"],
    ["tacz:glock_17", "tacz:cz75", "tacz:p320", "tacz:taurus943"],
    ["tacz:m9a4", "tacz:hk_mk23", "tacz:b93r", "tacz:lonetrail", "tacz:rhino357"]
  ]
}
```

Phase 1 may refine field spelling if production codec constraints require it,
but the following semantics are locked:

- membership affects presentation only;
- exact membership is deterministic and auditable;
- rank zero is lowest and later ranks are visually higher;
- every visible prerequisite must have a lower effective rank than its
  dependent, unless automatic depth is used for both;
- one blueprint has at most one explicit group in an active profile;
- omitted blueprints receive a deterministic fallback;
- invalid presentation data cannot invalidate the last good progression
  snapshot.

An explicit rank never makes a blueprint researchable. If presentation ranks
contradict prerequisites, reload validation reports the contradiction instead
of drawing a misleading edge.

## Fallback grouping

Fallback order is:

1. an explicit active-profile group;
2. an automatically generated group using a server-published item type when
   identity is disclosed;
3. the shared `undisclosed` group.

Fallback rank is longest published prerequisite depth. Disconnected nodes of
the same rank use a bounded near-square grid. They must not consume one global
column per category or form a single unbounded vertical line.

Explicit built-in groups use orders below the default fallback range, ensuring
the stock TaCZ branches appear before unconfigured add-on content. Add-on packs
may author their own stable order without editing built-in resources.

## Disclosure contract

Group presentation follows the existing Journal visibility boundary:

| Effective visibility | Tree node | Group publication |
| --- | --- | --- |
| Hidden | Omitted | Omitted |
| Silhouette | Anonymous | Shared Undisclosed group only |
| Name | Published name only | Shared Undisclosed group only |
| Preview | Identity/icon/summary | Authored or disclosed fallback group |
| Full | Exact published state | Authored or disclosed fallback group |

The server performs this sanitization before synchronization. The client does
not receive a real group and then decide to hide it. Group titles, icons,
orders, progress counts, portal labels, and region dimensions all count as
potential side channels and follow the same rule.

Group progress counts include only public nodes. They must not imply the number
of Hidden nodes. Undisclosed node identity remains opaque across publications.

## Default TaCZ progression contract

Phase 6 will author seven groups covering the exact 54-weapon TaCZ 1.1.8
catalog once:

1. Pistols
2. SMGs
3. Shotguns
4. Rifles
5. Snipers
6. Machine Guns
7. Special Weapons

Ranking is authored, not calculated at runtime. The initial review uses
role-normalized sustained and burst damage, capacity/reload, range, armor
interaction, handling, fire modes, attachments, special scripts, and ammo
constraints. Manual judgment may keep comparable weapons as siblings.

The default graph avoids unrelated mandatory cross-category research. The
combined view presents one canvas, not one forced linear progression. The
existing 4, 6, 8, 10, and 12 RP curve remains the baseline until playtesting
justifies a balance change.

## Compatibility and authority

- Old group-less format-1 research datapacks remain valid.
- Fallback grouping provides a complete UI for old and third-party content.
- Existing learned blueprint IDs are not rewritten or revoked.
- The server remains authoritative for selection, affordability, ingredient
  consumption, point consumption, and research completion.
- View, group selection, camera, hover, pinned details, and search are
  client-local presentation state.
- No FTB Quests, external UI library, or additional required mod is introduced.
- A protocol revision in Phase 3 may require matching client/server mod
  versions, as normal for Forge network changes; it does not change save data.

## Boundedness requirements

Phase implementations must retain current hard limits and add explicit limits
for group metadata. The final system must remain valid at 4,096 public nodes.

- Group count, title length, translation-key length, and membership count are
  bounded before allocation.
- Layout dimensions remain below `ResearchTreeLayout.MAX_DIMENSION`.
- All group and atlas ordering is deterministic.
- Layout work is linear or bounded-sweep `O(nodes + edges)` per projection.
- Group projections are lazy and cached per topology generation.
- A state-only publication reuses topology and presentation layouts.

## Automated acceptance matrix

### Contract and resource parsing

- default view is Branches;
- All Weapons group selection focuses instead of filters;
- higher tier is visually above lower tier;
- negative ranks and orders outside their bound reject;
- duplicate group IDs and duplicate membership reject;
- missing profiles, icons, or blueprint members report diagnostics;
- contradictory explicit ranks reject;
- a group-less datapack receives fallback groups;
- a failed reload preserves the last valid publication.

### Disclosure

- Hidden contributes no node, group, count, region, or portal;
- Silhouette and Name publish only Undisclosed membership;
- Preview and Full may publish authored groups;
- mixed-visibility cross-group edges never leak the concealed group;
- group icons cannot identify an anonymous member;
- state-only changes cannot reveal a previously hidden group.

### Projection and layout

- Branches contains only its group plus disclosure-safe portals;
- All Weapons contains all public nodes and edges;
- tier zero is below every visible dependent;
- arrowheads terminate upward at dependents;
- group regions and nodes do not overlap;
- disconnected populations use more than one column when appropriate;
- layouts are deterministic for branch, merge, forest, and maximum graphs;
- every projection respects dimension and time bounds.

### Interaction

- toolbar and search win clicks over any underlying canvas;
- sidebar wheel input never zooms the canvas;
- Branches group selection replaces projection;
- All Weapons group selection retains all nodes and centers a region;
- global search switches or centers according to the active view;
- compact, atlas, and per-group cameras remain independent;
- hover details do not reserve permanent canvas space;
- click pins details and keeps local path focus;
- anonymous focus cannot submit a research action;
- direct-inventory research remains one server-authoritative transaction.

### Compatibility and lifecycle

- existing player progression loads unchanged;
- group resource addition and removal survives `/reload`;
- stale group, focus, portal, and search state is discarded safely;
- profile switching rebuilds groups atomically;
- disconnect clears publication and presentation caches;
- integrated and dedicated server publications behave identically.

## Manual acceptance matrix

Test in Minecraft at minimum, compact, medium, wide, and ultrawide window
sizes, GUI scales 1 through 4, and at least one language with longer strings.
For each relevant size verify:

- Branches sidebar or compact selector is readable and scrollable;
- All Weapons fits, pans, zooms, and centers group regions;
- bottom-to-top direction is understandable without a tooltip;
- search accepts mouse focus and keyboard navigation;
- portals communicate cross-group requirements clearly;
- tooltips and pinned cards avoid controls and screen edges;
- every visibility level matches the disclosure table;
- unconfigured add-ons form a usable fallback grid;
- research consumes inventory resources exactly once;
- resize, fullscreen exit, Recycle, reconnect, and `/reload` recover safely.

## Phase boundaries

- Phase 1 implements resources, validation, diagnostics, and export.
- Phase 2 implements disclosure-safe published group DTOs and sanitization.
- Phase 3 synchronizes published groups and projection metadata atomically.
- Phase 4 implements Branches and All Weapons projections and navigation.
- Phase 5 implements clustered layout, portals, search, cameras, and input polish.
- Phase 6 authors and validates the complete default TaCZ progression.
- Phase 7 stress-tests disclosure, fallback, reload, and maximum scale.
- Phase 8 completes in-game QA, documentation, and release preparation.
