# Research Tree Datapack Authoring

> Research-tree groups are loaded, validated, audited, exported, synchronized
> with their disclosure-safe graph, and rendered as selectable branches or one
> combined All Weapons atlas.

TaCZ Weapon Blueprints builds the Research Bench tree from the active research
profile and its research rules. A server or modpack can replace the entire
progression, rebalance individual weapons, or add content-pack branches without
writing Java code.

## Start with the live catalog

Run these operator commands after the server has loaded TaCZ recipes:

```text
/gg research status
/gg research inspect <blueprint_id>
/gg research export
```

`export` writes `taczweaponblueprints/research-catalog.json` inside the current
world folder. Format 2 is stable and sorted. It includes every live blueprint
ID, selected rule, visibility, cost, prerequisites, authored group placement,
fallback status, and missing authored members. Use these exact IDs in rules and
groups; do not infer them from display names.

## Resource locations

```text
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
```

The complete example pack is in
`examples/research-tree-datapack`. Copy it into a world's `datapacks` directory,
replace the placeholder `example_guns` IDs, and select its profile with the
`activeResearchProfile` server config.

## How a tree is formed

- Every non-hidden effective visibility can become a node. `silhouette` uses an
  anonymous question-mark node, `name` reveals only the translated name,
  `preview` adds identity/icon and aggregate requirements, and `full` adds exact
  policy state and details.
- Every `prerequisites` entry creates an arrow from the prerequisite to each
  targeted blueprint.
- A prerequisite-bearing rule must use exact targets. Tags and selectors remain
  useful for policy or economy overrides, but cannot invent one dependency for
  an unknown set of weapons.
- Rules may branch by giving several targets the same prerequisite, and may
  merge by giving one target several prerequisites.
- Layout is deterministic and automatic. Authors control topology through
  prerequisites instead of fragile pixel positions.
- Cycles, paths deeper than 64 nodes, unknown fields, oversized definitions, and
  invalid economies reject the reload. The last valid snapshot remains active.

## Author presentation groups

Group resources organize the same authoritative research graph into stable
player-facing branches. They do not change prerequisites, costs, visibility,
discovery, or whether a blueprint can be researched.

```json
{
  "format": 1,
  "profile": "example:pack_progression",
  "title": "Pistols",
  "translation_key": "gui.example.research_group.pistols",
  "icon": "example_guns:starter_pistol",
  "order": 10,
  "ranks": [
    ["example_guns:starter_pistol"],
    ["example_guns:service_pistol", "example_guns:machine_pistol"]
  ]
}
```

- `format` must be `1`.
- `profile` identifies the existing research profile that owns the group.
- `title` is the bounded fallback text. `translation_key` is optional and lets
  clients localize the title when a matching language entry exists.
- `icon` must identify one member of the group. It is presentation metadata,
  not an extra catalog entry.
- `order` sorts authored branches from low to high; the group resource ID is
  the deterministic tie-breaker.
- `ranks` lists exact blueprint IDs. Rank zero is the bottom of the visual tree
  and later ranks appear higher. Array order within a rank is stable sibling
  order.

A blueprint may appear once within a group and in at most one authored group
for a given profile. Every group must reference a loaded profile, and authored
ranks must increase along effective prerequisite edges. These structural
errors reject `/reload` and leave the last valid snapshot active.

It is valid for an authored member to be absent from the current live TaCZ
catalog, such as when an optional content pack is not installed. The missing ID
is reported by diagnostics and the export instead of invalidating the pack.
Live blueprints omitted from all authored groups use deterministic fallback
presentation in later grouped clients.

## Matching and overrides

Only one rule supplies the overlay for a blueprint. Matching order is exact ID,
blueprint tag, catalog selector, then the profile fallback. Within the same
specificity, higher `priority` wins; a remaining tie is deterministic and is
reported by `/gg research status`.

The built-in TaCZ 1.1.8 tree uses exact rules at priority `100`. A pack can:

- replace a built-in definition by using its exact namespace and path;
- add an exact rule at a priority above `100` for a focused override; or
- define a new profile for a fully independent progression.

Do not depend on a lower-specificity selector to change the cost of a weapon
that also has an exact prerequisite rule: the exact rule wins as a whole.

## Third-party content

Unknown add-on blueprints intentionally inherit the profile with no
prerequisites. Under the built-in profile they remain researchable anonymous
silhouette nodes, but they are not attached to an arbitrary TaCZ branch. Give
supported add-ons exact full-visible rules when you are ready to author their
progression; the server visibility ceiling can still reduce their disclosure.

## Cost guidance

The built-in tree uses 4 RP roots and rises through 6, 8, 10, and 12 RP tiers.
Material costs rise with depth as well. Keep every enabled research point cost
strictly greater than its recycling value, and preferably increase costs along
each dependency path so an upgrade never becomes cheaper than its prerequisite.

After changing a pack, run `/reload`, then check `status`, inspect representative
roots and leaves, and export again. Structural diagnostics report missing
prerequisites, visible nodes whose path is hidden, and competing rule selections.

The built-in seven-branch TaCZ ordering and its role-normalized balance method
are recorded in
[`research-tree-navigation-phase-6.md`](development/research-tree-navigation-phase-6.md).
