# Research Designer field ownership

Audience: developers implementing import, editing, compilation, and simulation.
Status: foundation mapping; the Designer UI and compiler are future work.

[field-inventory.json](contracts/research-designer/field-inventory.json) assigns
every component of the mapped runtime records and inventories all shared
Blueprint configuration fields. A reflection test fails when a component or
setting is added without classification. `edit` identifies an editor-owned
area, `structural` is compiler-owned identity/version/authority, and `preserve`
requires complete source retention or a declared dependency. Nested fields
follow the stricter rules below rather than granting blanket edit permission.

## Editable properties

| Editor property | Runtime source / output | Units and override behavior |
| --- | --- | --- |
| Blueprint choice | Catalog `BlueprintData` identity; exact rule/placement target | Canonical blueprint ID, not the shared gun item ID. No matching by display name. Missing recipe-backed content remains unresolved. |
| Tree title, icon, labels | Tree `title`, `translation_key`, `icon`, domain labels | Presentation only. Custom translations/art require resources separate from the datapack. |
| Row and horizontal order | Placement `rank`, `order`; `lane` and domain | Rank 0–1,000,000, order 0–1,000,000. Prerequisites must have lower ranks. No cost/tier implication. |
| Legacy tier/level, rating/reason | Placement `tier`, `level`, `rating`, `tier_override_reason` | Preserve imported metadata; do not repurpose as research or crafting authority. |
| Width and bands | Tree `layout`, `bands` | Runtime row bounds 8–28; fixed/dynamic width and named bands retain their existing meanings. Empty bands cannot invent ranks or costs. |
| Branch label/parent/order/anchor | Tree `branches`; generated membership bundle | At most one direct owner per blueprint, same-domain nesting, 128 branches, depth 16. Anchors control disclosure and representation, not coordinates. |
| Prerequisites | Canonical rule `prerequisite_groups` | AND across groups, inclusive OR inside each group. Empty node group list means root. Empty individual groups are unfinished/invalid. |
| Research cost | Rule `research_cost` or inherited profile cost | `points` means whole units of active currency, 0–1,000,000,000; at most six ingredient predicates, each count 1–64 and at most 64 item alternatives. Existing item/tag semantics and combined allocation apply. |
| Research Bench tier | Rule `progression.research_tier`; resolved progression policy | Tier 1–3. Preserve the remainder of the progression override. Global enforcement/presets can alter effective requirements. |
| Research/tree availability | Rule `research_enabled`, `tree_enabled`; profile/domain policies | A node cannot re-enable a disabled domain. Removing placement alone does not revoke existing crafting knowledge. |
| Discovery / visibility | Rule/profile `requires_discovery`, `visibility`; global discovery ceiling | Hidden/name-only/silhouette/full are distinct publication states. The editor's full view is not a player's disclosure view. |
| Journal availability | Profile `journal_enabled` and global journal setting | Boolean availability, separate from whether a node can be researched or appears in the Tech Tree. |
| Creative research cost bypass | Rule/profile `creative_bypasses_cost`, global cost setting | Explicit research-cost behavior, separate from Workbench tiers and other progression gates. |
| Roots/entry candidates | Profile entry candidate arrays and authored prerequisites | Order is gameplay data. Moving a visual branch does not change entry candidates. Preserve dormant domain candidates unless intentionally edited. |

Placement metadata uses the existing tier enum and level 0–4. Weapon rating
components are integers 0–100; override reasons are trimmed text up to 512
characters. These remain preserved metadata in the first UI. Dynamic bands use
1–64 ranks per band; configured bands support at most 32 definitions and retain
their rank/score basis, ordered boundaries, labels, icons, and RGB colors. Lane
IDs and fallback metadata remain distinct from named branch ownership.

The first tree UI can edit these supported fields and remove an optional
override to inherit. It must show the inherited source, explicit authored value,
and effective value separately. Normal compilation generates a complete rule
within an independent managed profile, not a high-priority partial override of
an unrelated profile.

## Preserved properties and dependencies

| Data | Required treatment |
| --- | --- |
| Rule matching/priority/source selector | Record provenance in capture. Exact > tag > selector > fallback. Research, progression, crafting, and Analyzer selectors choose their own winning rules. Snapshot conversion materializes its complete effective overlay and grouped requirements. |
| Recycling, unlearned recycling, reverse engineering, Analyzer overrides | Retain whole nested definitions. Editing research cost or layout must not reset output permissions, costs, or tier matrices. |
| Crafting policies and overrides | Preserve dispositions, levels, associations, exceptions, and scoped gates. Display separately from Research Bench tier; advanced crafting editing is later work. |
| Progression profile fallbacks, authored bands, gates, fragment policy | Preserve complete definitions. The initial node tier control edits only `research_tier`; it cannot remove a crafting tier, fragment threshold, or gate. Complex gate editing is not implied by displaying it. |
| Ordinary versus analysis fragments | Preserve separate identity/permission/threshold systems. Raw archived counts are not completed sets; a threshold edit is not a player-save migration. |
| Research Data / reconstruction | Preserve full resources and references, not just per-row odds. Data pool filtering, source/reward classes, shared reconstruction precedence, and duplicate handling remain independently configured. |
| Automatic profiles and source evidence | Capture model/reference versions, classifications, ranks, generated groups, and costs. The managed custom tree must not retain an attached automatic profile that overrides manual intent. |
| Legacy presentation groups | Preserve/remap if still needed for referenced profiles; do not emit a second competing source of placement for the managed custom tree. |
| Tags/recipes/content | Copy project-owned definitions or declare dependencies; resolve actual tag membership and recipe associations for equivalence. Referenced gun assets remain external. |
| Settings outside datapacks | Capture raw and effective Blueprint settings and their preset selectors. Treat as explicit activation assumptions; the tree editor must not silently write shared config or imply a datapack can override every setting. |

Supported resource families are `blueprint_tags`, `research_profiles`,
`research_rules`, `research_tree_groups`, `research_tech_trees`,
`research_tech_tree_entries`, `research_automatic_placement_profiles`,
`research_data`, and `analysis_reconstruction`. Preserve each resource's ID and
complete typed definition when it remains a dependency. The latter two currently
use format 1. Loot/award recipes and third-party sources may influence economy
or obtainability but are not implicit editing targets; record their relevant
dependencies/assumptions instead of copying unrelated server data.

Project format 1 is independent of supported runtime formats: profiles/rules
1–5, trees/placements 1–3, automatic profiles 1–4, legacy groups 1, and current
resolved export 22. Version gates and unknown nested fields still go through
the real strict codecs. Unknown source fields must remain inspectable in an
unsupported, nonexportable source envelope; they cannot be silently discarded
on decode/save. Merely increasing a `format` field is not a valid migration.

## Required captured context

Capture the selected profile and relevant resource closure, source precedence,
catalog and recipe associations, item/tag registry facts, active automatic
publication/scoring evidence, progression and crafting projections, currency
identity and cost/result modes, disclosure ceiling, research/discovery enablement,
exemptions/blacklists, creative bypass settings, and ordinary/analysis fragment
and Analyzer/Data settings. Capture all enumerated Blueprint fields as raw and
effective values; only fields relevant to the project need become activation
requirements, but dropping context during acquisition would prevent comparison.

Use synthetic player knowledge/discovery, ordinary/analysis progress, selected
workstation, and inventory for simulation. Exact third-party/world gate facts
require an explicit captured scenario or a marked unresolved condition. They
cannot be guessed. Registry access and capability callbacks are not safe
detached evaluation inputs; extract immutable facts on the correct thread.

## Conversion acceptance

Opening an editor project preserves its owned source and inheritance choices.
Creating a custom project from a capture deliberately freezes an effective game
state. Do not label the latter a lossless import of the original pack.

Conversion must normalize legacy prerequisites to canonical groups, materialize
automatic membership/requirements, preserve effective costs and domain policies,
and retain all unrelated rule extensions. Remap owned profile/tree/resource
references together; never remap blueprint IDs. Newly generated exact rules
must not inadvertently erase the old winning rule's fields.

Before export, compare under the captured assumptions. A changed currency mode,
global preset, content pack, or tag membership invalidates the old equivalence
claim. The user can review and accept new assumptions, never silently inherit a
different result while the preview still shows the old one.
