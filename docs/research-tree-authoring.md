# Research Tree Datapack Authoring

> Research Tree data is loaded, validated, audited, exported, synchronized,
> and rendered in the unified Tech Tree view.

[TaCZ] Weapon Research & Blueprints builds the Research Bench tree from the active research
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
world folder. Format 18 is stable and sorted. It includes every live blueprint
ID, selected rule, visibility, cost, legacy prerequisite union, canonical
prerequisite groups, authored group placement,
fallback status, missing authored members, the selected tree's presentation-band
policy, revision-matched automatic placement decisions, topology/economy audits,
and a decision explanation for every weapon. The audit is built from a detached
fully disclosed view, independent of any player's progress and the configured
undiscovered-visibility ceiling. Use these exact IDs in rules and groups; do not
infer them from display names.

The topology section covers connectivity, reachability, rank population and
gaps, fan-in/fan-out, depth, merges, approximate crossings/edge length, origin
counts, and optional comparison with an explicitly supplied prior parent
fixture. The economy section compares finite configured RP income with policy
costs, leaf paths, AND-of-OR-aware prerequisite closures, and the full tree. Rank,
tier, band, and layout never set costs; the current export reports
`cost_authority: research_policy` and `automatic_cost_curve_enabled: false`.
The export itself defines these fields and records the packaged baseline used
for comparison.

## Generate Tech Tree weapon-rating evidence

The separate offline authoring task reads an unpacked TaCZ gun pack and writes
reviewable mechanical rating suggestions without loading Minecraft or changing
the live tree:

```text
./gradlew generateResearchTechTreeRatings
```

The default input is `run/tacz/tacz_default_gun`; the report is written to
`build/reports/research-tech-tree/tacz-weapon-ratings.json`. Use
`-PresearchGunPack=/path/to/pack` for another unpacked pack and
`-PresearchExpectedGunCount=<count>` to make coverage explicit.

Stats determine combat and utility evidence only. Player appeal requires a
separate reviewed score and reason. The built-in task uses the reviewed TaCZ
1.1.8 document in `src/authoring/resources`; use
`-PresearchAppealRatings=/path/to/appeal.json` to replace it for a custom pack.
An omitted custom score is neutral and marked `appeal_unreviewed`. The report
is non-authoritative: it cannot create costs, prerequisites, research
eligibility, or Tech Tree placements. Review the generated evidence and its
recorded formula version before adopting suggestions in a pack.

The add-on automatic-placement path shares this report's normalized raw
mechanical derivation, but not its subjective appeal score or its final
55/20/25 rating. Its production-safe scorer uses a versioned 75/25
combat/utility result and reports missing-evidence confidence separately. A
revision-matched eligible proposal may now control presentation position and,
only in explicit `connected` mode, add a bounded validated prerequisite set. It cannot
create research costs or otherwise change eligibility. The packaged scoring
model is documented in
[Capability scoring](research-capability-scoring-v3.md).

To regenerate the pinned appeal-free TaCZ 1.1.8 comparison population used by
the runtime evidence snapshot, run:

```text
./gradlew generateAutomaticWeaponReference
```

This writes the strict bundled resource under
`assets/taczweaponblueprints/research/automatic`. Its source and metric
fingerprints are regression contracts; change the formula/reference version
when an intentional update changes either fingerprint. The reference remains
evidence only and cannot itself place or unlock a blueprint.

Automatic placement uses fixed score bands, stable sibling slots, a strict
activation profile, a fallback-only candidate gate, and atomic revision
publication. Connected mode adds only validated prerequisite groups. Invalid
plans fail open without changing research costs or player progress. Status,
inspection, and export commands provide deterministic operator evidence, while
hard population limits and safe defaults keep large content packs bounded. See
[Dynamic Research Tree width](research-tree-dynamic-width.md) for the current
layout limits.

Weapon placement now has one explicit authority per tree; it is never a hybrid.
An `automatic` tree sends every catalog gun through scoring, placement, and
generated prerequisite planning, and ignores all authored weapon coordinates
and prerequisite declarations. An `authored_only` tree ignores automatic
proposals and publishes only guns matched by an exact, tag, or non-fallback
selector placement. Unmatched guns and entries marked `fallback` are omitted.
Ammo and attachment placement and prerequisites remain authored in either mode.

## Resource locations

```text
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
data/<namespace>/taczweaponblueprints/research_tech_trees/<path>.json
data/<namespace>/taczweaponblueprints/research_tech_tree_entries/<path>.json
data/<namespace>/taczweaponblueprints/research_automatic_placement_profiles/<path>.json
```

The complete example pack is in
`examples/research-tree-datapack`. It uses a format-2 automatic weapon profile,
tree, and automatic-placement policy. Its placement-entry examples remain
useful when changing the tree to `authored_only`, but do not control weapons
while the tree is automatic. Copy it into a world's `datapacks` directory,
replace the placeholder `example_guns` IDs, and select its profile with the
`activeResearchProfile` server config.

## Profile domain policy

Research profile format 2 adds one explicit policy for every Tech Tree domain:

```json
{
  "format": 2,
  "domain_policies": {
    "weapons": {"tree_enabled": true, "research_enabled": true},
    "attachments": {"tree_enabled": false, "research_enabled": false},
    "ammo": {"tree_enabled": false, "research_enabled": false}
  }
}
```

Both fields are final gates. The effective value is the profile/rule result
AND the matching domain value, so an exact, tag, or selector rule cannot
reactivate a domain disabled by its profile. Dormant costs, prerequisites,
placement bundles, and fallback rules stay ready for a datapack that opts the
domain back in.

Format-2 profiles must declare all three keys and both booleans. Format-1
profiles remain supported, must not declare `domain_policies`, and behave as if
all domains were enabled. Domain policy does not disable the Journal,
discovery, recycling, reverse engineering, physical-blueprint learning, or
progression exemptions. The packaged profile enables Weapons and disables
Attachments and Ammo.

## Reverse-engineering policy

Reverse engineering reuses the same profile, target selector, specificity, priority, and
resource-ID tie-break used by ordinary research rules. There is no separate
matching language to keep synchronized. Profiles may provide defaults and any
research rule may overlay individual fields:

```json
{
  "reverse_engineering": {
    "enabled": true,
    "cost": {
      "points": 2,
      "ingredients": [
        { "items": ["minecraft:paper"], "count": 1 }
      ]
    },
    "allow_known": false,
    "allow_modified": true,
    "physical_blueprint_learning": "bypass_tree_prerequisites",
    "output_recyclable": false
  }
}
```

`input_count` is optional and bounded to 1–64. Without it, guns and
attachments require one physical item while ammunition requires the canonical
TaCZ recipe output count. A rule overlay changes only the fields it contains.
`physical_blueprint_learning` accepts `bypass_tree_prerequisites`,
`require_tree_prerequisites`, or `disabled`.

The server resolves the logical TaCZ content ID stored in `IGun`, `IAmmo`, or
`IAttachment` stack data. It never uses the shared Forge backing-item ID, so
add-on namespaces work without code changes. Only canonical recipe-backed
catalog entries can become eligible. Loaded guns and guns containing removable
attachments are always rejected; `allow_modified` controls cosmetic/customized
equipment separately. Merely resolving or previewing an item changes no stack,
inventory, RP, discovery, or learned state.

The safe defaults disallow known items and make reverse-engineered blueprint
output non-recyclable. Enabling known-item sacrifice and recyclable output at
the same time as positive-value recycling creates a direct item-to-RP loop and
rejects snapshot publication. `expert_allow_economy_loop` is an explicit
last-resort acknowledgement; diagnostics continue to report every affected
live target when it is used.

Reload/startup diagnostics report reverse rules that match no catalog entry,
eligible entries without canonical recipes, blocked or progression-exempt
targets, and acknowledged expert economy loops. Invalid counts, malformed
costs, unknown fields, and unsafe unacknowledged economies fail before the new
immutable snapshot is published.

## Research Tech Tree presentation data

A profile may select one map with `"tech_tree": "namespace:path"`.
The format-1 map defines its title, six Starter-through-Apex labels, domain
labels, and ordered lanes. Every domain declares a fallback lane and tier.
Tree format 2 requires a shared layout policy and makes visible progression
bands optional. It also accepts `weapon_placement_mode`, which defaults to
`authored_only`. Choose exactly one model:

- `"weapon_placement_mode": "automatic"` requires exactly one automatic
  placement profile for this tree. Every catalog gun is owned by the generator;
  authored weapon positions and prerequisites are ignored. The profile mode and
  review handling must place every candidate, so `independent` and `exclude`
  are invalid for this authority mode.
- `"weapon_placement_mode": "authored_only"` forbids an automatic placement
  profile for this tree. Only non-fallback exact, tag, or selector matches are
  published; unspecified guns are intentionally absent.

Format-1 trees are always `authored_only` and cannot opt into automatic weapon
authority. `layout.max_nodes_per_layer` accepts 8–28 and defaults to 9 for
legacy format-1 trees. Omitting `width_mode` preserves fixed-width behavior.
With `width_mode: "dynamic"`, `min_nodes_per_layer` also accepts 8–28, defaults
to 9, and may not exceed the maximum. The server resolves the effective width
as `ceil(sqrt(4 × eligible automatic weapons / 3))`, clamped to
the configured range. This targets a wider 4:3 semantic topology instead of a
square one. Excluded, unplaced, attachment, and ammunition entries do
not inflate it. It bounds generated semantic layers and the client's maximum
visual row width; it never creates a prerequisite. A manual rank wider than
the limit wraps into balanced presentation-only rows. Normal compact and
fullscreen canvases retain the server-resolved capacity; an unusually narrow embedding may
wrap more aggressively but cannot exceed the authored maximum. Branch-aware
ordering and compaction are deterministic client geometry and do not modify the
published graph.

Format-2 `bands.mode` accepts:

- `none` (also the default when `bands` is omitted): one continuous rank canvas;
- `dynamic`: groups only occupied ranks using `ranks_per_band` (1–64, default 3);
  the runtime coalesces groups when necessary to remain within 32 visible bands;
  or
- `configured`: uses one to 32 author-defined bands and `basis: "rank"` or
  `basis: "score"`.

Configured definitions are ordered bottom-to-top. Every definition except the
last requires an inclusive, strictly increasing `maximum`; the last omits it as
the catch-all. Each definition requires `id` and `title`, and may provide
`translation_key`, RGB integer `color`, and an `icon` blueprint ID that belongs
to that band. Icons are published only when the referenced member is visible
and allowed to reveal its icon. Score
boundaries accept 0–100 and are used only when every published member has
automatic mechanical-score evidence and every occupied rank falls wholly within
one score interval. A mixed/manual graph or a rank that straddles intervals
safely renders without bands rather than inventing a rank-wide classification.
Empty configured bands are never published and reserve no canvas height. Portal
clearance is likewise reserved only around a row that owns an actual
cross-domain requirement or unlock portal.

```json
{
  "format": 2,
  "weapon_placement_mode": "automatic",
  "layout": {
    "width_mode": "dynamic",
    "min_nodes_per_layer": 9,
    "max_nodes_per_layer": 20
  },
  "bands": {
    "mode": "configured",
    "basis": "rank",
    "definitions": [
      {"id":"example:field","title":"Field","maximum":3,"color":3368601},
      {"id":"example:specialized","title":"Specialized"}
    ]
  }
}
```

Changing `none`, `dynamic`, or `configured`, including labels and styling,
cannot alter ranks, prerequisites, costs, discovery, or eligibility. Version-1
trees continue to publish the six recognizable legacy labels.
Format-1 placement bundles contribute exact IDs, tags, or catalog selectors
plus a domain, lane, tier, optional zero-based `level`, and stable sibling
order. Omitting `level` retains the legacy level-zero behavior.

Placement-bundle format 2 additionally requires a non-negative `rank` no
greater than 1,000,000 on every entry. A prerequisite must have a strictly
lower rank than its dependent. Sibling order stabilizes horizontal peers and
never makes a same-rank dependency legal. `tier`/`level` remain legacy
presentation metadata and are converted to optional band references during
publication; they do not decide format-2 edge legality or client rows. Format 1
must omit `rank` and is converted losslessly using a wide tier stride followed
by deterministic topological lifting.

A minimal format-2 manual placement pair looks like this:

```json
{
  "format": 2,
  "tree": "example:dynamic_weapons",
  "priority": 100,
  "entries": [
    {
      "target": {"blueprints": ["example_guns:starter_pistol"]},
      "domain": "weapons",
      "lane": "example:weapons/handguns",
      "tier": "starter",
      "rank": 0,
      "order": 0
    },
    {
      "target": {"blueprints": ["example_guns:advanced_pistol"]},
      "domain": "weapons",
      "lane": "example:weapons/handguns",
      "tier": "advanced",
      "rank": 1,
      "order": 100
    }
  ]
}
```

The dependent research rule still owns
`"prerequisites": ["example_guns:starter_pistol"]`; placement data does not
create that edge. For format 1, omit `rank` and keep using `tier`, optional
`level`, and `order`. The server converts that legacy coordinate losslessly.
Format-1 profiles continue to imply all three domains when `domain_policies`
is absent, so migrate a profile to format 2 before relying on weapon-only
defaults.

This data is presentation-only. It cannot define costs, prerequisites,
visibility, discovery, or research eligibility; those remain in profiles and
research rules. A weapon placement may retain reviewed `combat`, `utility`,
and `appeal` evidence. If its authored tier differs from the suggested rating
tier, `tier_override_reason` is required. Ratings are forbidden for ammo,
attachments, tags, and broad selectors.

In an `authored_only` tree, non-fallback authored placement always outranks an
entry marked `fallback`; fallback entries are not published by the simplified
weapon population rule.
Within the same fallback class, placement precedence is exact ID, tag, then
selector; higher bundle priority wins within one specificity. A deterministic
resource-ID and entry-index tie-break keeps reloads stable. Unknown add-on
content should use a conservative General-lane selector instead of fabricated
dependencies.

Automatic-placement profiles accept formats 1 through 4. Format 1 preserves the
legacy six-tier score buckets; `levels_per_tier` accepts 1–5 and defaults to 3.
Formats 2 through 4 instead create contiguous stat-sorted ranks across the complete
eligible weapon population. `foundation_count` accepts 1–3 and defaults to 2;
that many low-scoring weapons reserve the foundation rank, and later
provisional layers fill to the selected tree's resolved layer capacity.
Dynamic capacity is computed once from the complete eligible automatic weapon
population before ranks and prerequisites are generated. Generated
prerequisites are selected from earlier automatic layers. The
graph-aware finalization and presentation passes then lift dependent nodes and
compact empty ranks. The legacy profile field
`max_nodes_per_rank` remains readable for format-1-tree compatibility but a
format-2-or-newer tree is authoritative. Numeric score gaps therefore cannot create
empty rows. `mode` accepts `independent`, `distributed`, or `connected`;
`review_confidence_threshold` accepts 0–100 and defaults to 60.
`max_prerequisites` accepts 1–3 and defaults to 2. `merge_interval` accepts
0–64, defaults to 4, and uses zero to disable optional third-parent convergence
under the legacy strategy.
Connected branch-aware format-2-or-newer placement uses a deterministic second-parent
quota: 100% through the shared trunk, a gradual decline through the family
transition, and a 20% branch-local floor through specialization and terminal
cohorts. This keeps upper branches mostly narrow without forcing every branch
into a single chain. A configured `merge_interval` never controls ordinary
two-parent tapering; under `legacy_and`, it only schedules bounded third-parent
opportunities when `max_prerequisites` is 3 and the second-parent quota also
admits the node. Under `hybrid_routes_v1`, it instead schedules at most one
deliberate mandatory-gateway opportunity per eligible shared or transition
rank, and never schedules one in specialization or terminal cohorts.

Format 3 adds the strict `prerequisite_strategy` field. It defaults to
`legacy_and`, preserving the format-2 interpretation in which every selected
parent is a mandatory singleton group. `grouped_routes_v1` is permitted only
with `mode: connected` and `max_prerequisites` no greater than 2. It reuses the
same deterministic branch/layer parent selection but publishes the selected set
as follows: zero parents creates a generated root, one parent creates one
singleton group, and two parents create one inclusive `any_of` group. Thus a
selected pair means `A OR B`, not `A AND B`. Generated third alternatives are
not part of version 1. Existing format-1 and format-2 profiles continue to
decode as `legacy_and`; selecting grouped routes under either older format is a
reload error.

Format 4 adds the strict `scoring_model` field. `mechanical_v2` is the omitted
default for migration safety; `capability_v3` selects the package-based scorer,
expanded TaCZ evidence, calibrated progression score, and matching v3 role
evidence. Older profile formats cannot select v3. The bundled profile explicitly
uses `capability_v3`; existing custom datapacks remain on v2 until they opt in.
See [`research-capability-scoring-v3.md`](research-capability-scoring-v3.md) for
the formula, pinned reference, review behavior, and rollback procedure.

`hybrid_routes_v1` is also format-3, connected-only, and requires dynamic stat
layers. It accepts up to three generated parents and chooses one explicit
relationship shape before materializing them: mandatory singletons
`[[A],[B]]`, alternatives `[[A,B]]`, or an alternative pair plus a mandatory
gateway `[[A,B],[C]]`. A mixed opportunity that cannot safely retain its third
parent degrades to `[[A,B]]`; it is never mislabeled or reinterpreted from
parent count. The packaged profile does not enable this strategy.

Before an optional parent is accepted, the planner applies a strategy-specific
RP review. `legacy_and` compares the union of both prerequisite closures with
the more expensive individual path. Its normal ceiling is about 1.5 times the
dominant path, with a one-direct-parent-cost grace for cheap foundation merges;
deeper disjoint paths that exceed the ceiling retain their primary parent and
report `merge_rejected_closure_inflation`. `grouped_routes_v1` instead prices
the parents as alternative routes. It records exact or bounded individual
route costs plus mandatory-ancestry overlap and rejects only when the lower
bound proves a route-cost ratio above 8.0x. The 4.0x p95 ratio remains a
diagnostic warning rather than a hidden per-route veto. Both reviews use
resolved research-policy point costs and never derive cost from rank, tier,
branch, or weapon statistics. Hybrid alternatives use that same group-aware
review against canonical generated ancestry. Its mandatory gateway is then
reviewed as a bounded addition to the selected OR route, while the sparse
schedule and ancestor/descendant check prevent rank-wide gates and redundant
parents. Every generated edge must move to a strictly higher published rank.
An authored foundation bridge, or the reconciliation from generated foundation
peers to one promoted automatic entry point, may begin on the same provisional
rank; rank finalization lifts the dependent before publication. Format 1 retains the earlier
anchor and tier-gateway behavior from the compatibility planner. Reviewed
`place_independent` proposals are excluded from the anchor pool as well as from
generated target edges.
The optional `review_handling` accepts `exclude`, `place_independent`, or
`place_connected` and defaults to `exclude` for compatibility. A reviewed
proposal always retains its warning evidence in diagnostics and exports.
`place_independent` permits its position but no generated edge, while
`place_connected` also permits connected-mode anchor planning. Truly
unscoreable guns use a conservative weapon-type band only when reviewed
placement is explicitly enabled; a stable ID determines their exact position
within that band rather than catalog discovery order.

Requirement authority follows the tree mode. An `authored_only` tree retains
the selected research rule's legacy `prerequisites` or canonical
`prerequisite_groups`. For guns in an `automatic` tree, generated groups replace
authored requirements completely; ammo and attachment requirements remain
authored. Unsafe generated alternatives are filtered independently; another
safe route survives, a progression-exempt route satisfies the group, and a
group with no safe alternatives fails open rather than stranding an add-on
weapon.

The deprecated automatic-profile `bands` field remains readable for format-1
compatibility evidence, but a format-2 tree's `bands` policy owns the published
overlay. `levels_per_tier` likewise remains a format-1 compatibility field and
does not limit format-2 rank count. Neither field can authorize prerequisite
edges.

Domains render as one mixed prerequisite-driven canvas, not one column or box
per lane. Lane order and sibling order are stable tie-breaking hints when
several nodes otherwise have the same graph rank; they do not constrain a
node's horizontal region. The shared crossing-reduction kernel may therefore
interleave lane classifications to keep forks, merges, and dependency paths
readable. Rank remains vertical authority. Legacy or custom band labels render
only when their optional references form a coherent bottom-to-top overlay.

The built-in map contains 53 dormant authored weapon, 95 attachment, and 24
ammo placements. Its `automatic` authority ignores every authored weapon
coordinate and prerequisite, so all 53 live recipe-backed weapons use the same
revision-matched generator. The disabled attachment/ammo bundles
remain available as format-1 opt-in compatibility data.

For a release datapack, audit each domain independently: every exact placement
should resolve to exactly one research rule, every prerequisite should stay in
the intended domain unless a deliberate cross-domain portal is desired, and
every node should be reachable from a usable entry. The bundled mod's release
artifact gate enforces those rules for all 53/95/24 packaged placements while
publishing only Weapons by default; third-party
packs should apply the same checks to their exported live catalog and complete
the reload cases in the manual QA matrix.

The server publishes only placements whose blueprint is already present in the
player's disclosure-safe research graph and whose effective visibility reveals
its identity. Name-only, silhouette, and hidden entries publish no domain,
lane, rating, placement, or identifying icon. Domains and lanes with no public
members are omitted. The built-in resources retain all 53 Weapons, 95
Attachments, and 24 Ammo placements with identity-revealing authored rules.
Its format-2 domain policy publishes only the 53 Weapons by default;
Attachments and Ammo remain dormant. Branches and All Weapons still derive
from a separate weapon-only presentation subset.

## Prerequisite groups

Research-rule format 2 can express inclusive route choices directly:

```json
{
  "format": 2,
  "profile": "example:default",
  "target": {
    "blueprints": ["example_guns:advanced_rifle"]
  },
  "prerequisite_groups": [
    {
      "any_of": [
        "example_guns:assault_route",
        "example_guns:marksman_route"
      ]
    },
    {
      "any_of": ["example_guns:advanced_receiver"]
    }
  ]
}
```

The rule above means `(assault_route OR marksman_route) AND
advanced_receiver`. `any_of` is inclusive: learning both alternatives is valid;
this is not exclusive XOR.

Legacy `prerequisites` remains supported and keeps its original mandatory-AND
meaning. For example, `"prerequisites": ["example:a", "example:b"]`
normalizes to two singleton groups and still requires both A and B. A rule must
use either `prerequisites` or `prerequisite_groups`, never both. Both forms must
use exact blueprint targets.

Each group contains 1–64 unique alternatives, one dependent may contain at
most 64 groups and 64 alternatives in total, and all IDs and groups are sorted
canonically after decoding. Empty groups, duplicate groups or alternatives,
selected self-references, cycles through any possible alternative, and paths
deeper than 64 nodes reject the complete reload. Valid legacy format-1 rules
need no migration.

## How a tree is formed

- A rule-resolved policy and its format-2 domain policy must both have
  `tree_enabled: true` before it can become a node. This is
  independent of `journal_enabled`, so ammo and attachments can remain in the
  Journal without crowding a weapon-only tree.
- Every tree-enabled, non-hidden effective visibility can become a node. `silhouette` uses an
  anonymous question-mark node, `name` reveals only the translated name,
  `preview` adds identity/icon and aggregate requirements, and `full` adds exact
  policy state and details.
- Every legacy `prerequisites` entry creates one mandatory singleton group and
  one arrow to each targeted blueprint. Every `prerequisite_groups` alternative
  creates an arrow while retaining its any-of group identity.
- The client renders a mandatory singleton as a direct arrow. A group with more
  than one visible, undiscovered, or outside-view alternative converges through
  a diamond and one outgoing arrow. Selected-node details use `Requires one of`
  and never name undisclosed or projection-external members.
- A prerequisite-bearing rule must use exact targets. Tags and selectors remain
  useful for policy or economy overrides, but cannot invent one dependency for
  an unknown set of weapons.
- Rules may branch by giving several targets the same prerequisite, and may
  merge by giving one target several prerequisites.
- Multiple legacy prerequisites and multiple canonical groups use AND
  semantics. Alternatives within one canonical group use OR semantics. Keep
  routine nodes single-route and reserve multiple mandatory groups for gateways,
  capstones, or meaningful specialization convergence.
- Format-2 dependencies require a strictly lower prerequisite rank. Tier,
  level, and sibling order do not authorize an edge.
- Format-1 dependencies retain their legacy compatibility rule: the
  prerequisite must have an earlier tier/level position, or the same tier and
  level with lower sibling order. Same-position dependents are then assigned a
  higher internal rank without rewriting the datapack.
- Layout is deterministic and automatic. Authors control topology through
  prerequisites instead of fragile pixel positions.
- Cycles, paths deeper than 64 nodes, unknown fields, oversized definitions, and
  invalid economies reject the reload. The last valid snapshot remains active.
- For an immediately researchable tree, use `preview` or `full`; silhouette and
  name-only nodes deliberately remain unselectable until another policy or
  discovery raises their disclosure. The built-in profile avoids that ambiguous
  combination, while custom packs may still use it intentionally.

## Entry-point fallback

Profiles may provide an ordered `entry_point_candidates` list. The first ID is
the preferred legacy/weapon root. Optional `tech_entry_point_candidates`
provides the same catalog-aware behavior independently for typed Tech Tree
domains. If a preferred blueprint is absent from the live recipe catalog, the
first later selectable candidate becomes that group's effective root:

```json
{
  "tree_enabled": false,
  "entry_point_candidates": [
    "example_guns:starter_pistol",
    "example_guns:service_pistol",
    "example_guns:backup_pistol"
  ],
  "tech_entry_point_candidates": {
    "attachments": [
      "example_pack:starter_grip",
      "example_pack:starter_sight"
    ],
    "ammo": [
      "example_pack:common_ammo",
      "example_pack:backup_ammo"
    ]
  }
}
```

Rules that place a candidate in the tree must override `tree_enabled` to
`true`, and the profile's matching domain policy must also be enabled. A
disabled domain keeps its candidates dormant and resolves no active root. When
an enabled domain fallback is used, its own prerequisites are cleared and direct
references to the missing preferred root are rebased to it. Candidate order is
therefore gameplay data, not alphabetical order. If no candidate is available,
the structural audit reports the missing root instead of silently inventing
progression. A resolved entry point is also excluded from lower-precedence
automatic prerequisite generation, including when a live block or exemption
selects a different fallback after the automatic plan was published. Generated
prerequisite alternatives that still reference the preferred entry are rebased
to that same live fallback before tree, journal, preview, or unlock evaluation.
When a missing preferred root promotes exactly one automatic foundation member,
the other generated foundation peers connect through it before rank finalization
so the live tree retains one free starting route. Automatic-only trees with no
configured entry point retain their configured number of generated roots.
`entry_point_candidates` and the `weapons` key of
`tech_entry_point_candidates` are mutually exclusive; older profiles remain
valid without the new field.

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
  "include_in_overview": true,
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
- `include_in_overview` is optional. Authored groups default to `true`; setting
  it to `false` keeps the complete branch and its search results while omitting
  its nodes from All Weapons. Automatically generated item-type fallback groups
  also default to `true`, so newly installed gun-pack weapons appear in All
  Weapons without manual authoring. Undisclosed groups default to `false`.
- `ranks` lists exact blueprint IDs. Rank zero is the bottom of the visual tree
  and later ranks appear higher. Array order within a rank is stable sibling
  order. Empty rank arrays are permitted as global alignment bands when a
  dependency enters or leaves another group. A group must end on a non-empty
  rank; sparse ranks remain presentation metadata and never create dependencies.

A blueprint may appear once within a group and in at most one authored group
for a given profile. Every group must reference a loaded profile, and authored
ranks must increase along effective prerequisite edges. These structural
errors reject `/reload` and leave the last valid snapshot active.

It is valid for an authored member to be absent from the current live TaCZ
catalog, such as when an optional content pack is not installed. The missing ID
is reported by diagnostics and the export instead of invalidating the pack.
Live blueprints omitted from all authored groups use deterministic fallback
presentation. Identity-visible fallback weapons appear in the fullscreen
sidebar, search, and All Weapons automatically. Put a weapon in an authored
group with `include_in_overview: false` when a pack intentionally needs to keep
that branch out of the overview.

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

The built-in profile has separate coarse selectors for `gun`, `attachment`, and
`ammo`. Unknown recipe-backed add-on content retains `preview` fallback rules
with no invented prerequisites. Guns are enabled and retain their TaCZ
item-type fallback in the legacy Branches view; attachment and ammunition
fallbacks remain dormant until a profile enables their matching domain. A
datapack can override a kind without Java code, but a rule cannot override a
disabled profile domain:

```json
{
  "format": 1,
  "profile": "example:pack_progression",
  "priority": 10,
  "target": {
    "selector": {
      "blueprint_kinds": ["attachment"]
    }
  },
  "visibility": "preview",
  "tree_enabled": true,
  "research_enabled": true,
  "research_cost": {
    "points": 4,
    "ingredients": [
      { "items": ["minecraft:paper"], "count": 2 },
      { "items": ["minecraft:iron_ingot"], "count": 1 }
    ]
  }
}
```

Supported values are `gun`, `ammo`, and `attachment`. Exact rules still win
over kind selectors, so packs can give selected add-on weapons authored costs
and prerequisites while leaving the generic fallback intact.

## Cost guidance

The built-in policies retain 2, 4, 6, 8, 10, and 12 RP authoring for generated
capability tiers in every Tech Tree domain. Automatic scoring chooses the live
weapon foundation; no named weapon is guaranteed to remain the root across
catalog or scoring changes. Only Weapons are Research Bench-researchable under
the built-in profile.
Material costs rise with depth as well. Keep every enabled research point cost
strictly greater than its recycling value, and preferably increase costs along
each dependency path so an upgrade never becomes cheaper than its prerequisite.

The bundled automatic Tech Tree activates all recipe-backed Weapons and lets
the generator choose its foundation and edges. RK-6 and 9mm remain authored
entry candidates for the disabled Attachment and Ammunition domains. Every
published node is reachable within its domain and prerequisite ranks never move
downward. In an authored-only tree, type, lane, and tier still do not authorize
dependencies; datapacks must author exact prerequisite edges. Genuine
cross-domain prerequisites are supported and publish as
boundary portals, but should not be added merely to force the domains together.
Each disclosed cross-domain edge is indexed once in its authoritative
prerequisite-to-dependent direction and exposed reciprocally as a Requirement
target from the dependent and an Unlock target from the prerequisite. Hidden or
unplaced endpoints publish no navigation target. Portal navigation therefore
uses only relationships present in the current atomic public graph; lane,
domain, tier, and rating metadata never manufacture an edge.

After changing a pack, run `/reload`, then check `status`, inspect representative
roots and leaves, and export again. Structural diagnostics report missing
prerequisites, visible nodes whose path is hidden, and competing rule selections.

The built-in TaCZ tree uses seven stable weapon branches with role-normalized
balance. Review the live export after changing branch membership, prerequisites,
or scoring inputs.
