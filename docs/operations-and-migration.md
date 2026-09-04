# Operations and Migration

## Supported runtime

The current build targets Minecraft 1.20.1, Forge 47.x, TaCZ 1.1.8-hotfix,
and Fzzy Config 0.7.6. Declared dependency ranges intentionally stop before
TaCZ 1.2 and Fzzy Config 0.8 because those API lines have not been validated.

TaCZ 1.1.8 can inject a Model 943 revolver and ammunition into a world's optional bonus chest, but does not provide a gun-smithing recipe for that weapon. It is therefore not part of the recipe-backed blueprint tree. The automatic generator selects the live foundation from the complete recipe-backed weapon catalog.

The packaged 53-weapon tree uses the same `capability_v3` automatic placement
and grouped-route generator for every recipe-backed weapon, including add-on
weapons. Its old authored coordinates and prerequisite declarations remain
dormant compatibility data and never mix into the active automatic weapon
graph. Research costs follow
the generated capability tier (2/4/6/8/10/12 RP), while attachment and ammo
research remains authored but disabled by default.

Install the blueprint mod and required dependencies on both client and server. TaCZ gun content packs remain normal TaCZ resources and do not need to be declared as Forge mods.

Research Tree purchases use synchronized `treeResearchResultMode`. The packaged
`DIRECT_LEARN` value learns the recipe immediately without creating a physical
blueprint. `CREATE_BLUEPRINT` retains the former two-step result for a server
that needs a temporary compatibility window. Changing the setting requires no
world conversion and does not rewrite existing knowledge or physical items.
In `DIRECT_LEARN`, selecting a higher locked node previews and can purchase a
globally shortest viable prerequisite closure. Among equally short closures,
the server compares RP cost, total material units, and canonical blueprint IDs.
Inventory and RP affect readiness without changing that canonical route.
Progression-exempt alternatives satisfy their groups without being purchased.
The server charges every
distinct newly learned node's aggregate RP and materials exactly once and
commits the complete path atomically. `CREATE_BLUEPRINT` intentionally retains
single-node behavior because creating one physical item cannot represent a
multi-node permanent unlock.

Learned knowledge does not by itself become a shortcut through the tree. A
weapon acquired out of order remains learned and usable, but it satisfies a
later prerequisite only when its own effective requirements contain a complete
learned route back to a root. Mandatory groups retain AND semantics and each
choice group needs one root-connected alternative. Selecting a higher node in
`DIRECT_LEARN` may therefore include missing nodes below an already-learned
weapon; the already-learned support is validated but is not charged or learned
again. Connectivity is recalculated from current knowledge and policy, so old
worlds need no save conversion and automatically become connected when the
missing ancestry is later learned.

One path purchase is capped at 1,024 unlocks. Exact planning retains at most
4,096 nondominated closures per node and explores at most 262,144 route states;
oversized or combinatorially excessive authored routes fail closed with a
specific action result and consume nothing.

Synchronized progression exemptions are live access policy, not saved
knowledge. Exact IDs, coarse `gun`/`ammo`/`attachment` kinds, and TaCZ item
subgroups are additive and re-evaluated against the current catalog after
reload. An exempt target is craftable and satisfies prerequisites but is
omitted from research, blueprint loot, and Analyzer acquisition. Removing the
exemption restores normal progression unless the player independently learned
the target.

`startingBlueprints` is an exact-ID, additive knowledge grant. It is applied
idempotently on login, successful resource reload, and synchronized config
update. Removing a configured starter never revokes it, and repeated callbacks
do not issue Research Point awards. Audit missing IDs and unmatched subgroup
selectors with `/gg research status` after changing packs or configuration.

## Research costs and found-weapon recovery

`researchCostMode` is a synchronized server policy. `POINTS_AND_ITEMS` is the
compatibility default; `POINTS_ONLY` suppresses authored item/tag ingredients,
and `ITEMS_ONLY` suppresses authored RP costs. The source datapack costs are not
rewritten, so changing the mode or returning to the default restores them. The
same effective cost is used by single-node research, shortest-path purchases,
server previews, commands, and economy exports.

`foundWeaponRecoveryMode` defaults to `PROTECTED_BLUEPRINT_ONLY`. Newly crafted
survival guns are stamped as crafted by the TaCZ gun-smithing transaction, and
guns observed in TaCZ's generated-loot pass are stamped with a versioned loot
origin. Recovery trusts only a valid positive loot-origin marker. Unknown guns,
including guns created before this feature or supplied through an unobserved
third-party path, fail closed and retain protected behavior.

The other modes are `RECYCLABLE_BLUEPRINT`, `DIRECT_RP_ONLY`, and
`PLAYER_CHOICE`. Direct conversion uses the target's existing recycling value,
still pays the target's reverse-engineering RP/material cost, applies the RP cap
after that cost, consumes the weapon once, and does not learn the blueprint.
The Analyzer requires a second confirmation before directly converting an
unlearned weapon. Target recycling gates and the global duplicate-recycling
policy remain economy controls; a recovery mode does not silently override
them. Disabling `physical_blueprint_learning` blocks protected blueprint
learning, but it does not block direct RP recovery or an explicitly
recyclable-only output because neither path teaches the recipe. See
`docs/research-cost-and-found-weapon-recovery.md` for the full matrix.

## Balance presets and setup assistant

Fresh configurations default `balancePreset` to `BALANCED`. A version-zero
configuration created by an earlier release automatically migrates to
`CUSTOM`, preserving its existing undiscovered-visibility and blueprint-loot
values. `ACCESSIBLE`, `BALANCED`, and `SCARCE` are reversible overlays for
maximum undiscovered visibility plus the global default blueprint loot chance
and roll range. Per-rule datapack overrides continue to win. A preset does not
alter the active research profile, costs, prerequisites, RP economy,
blacklists, exemptions, starting grants, or any player's learned and
discovered IDs.

Fzzy Config groups are presentation-only and do not nest or rename the
existing TOML fields. The Server Settings screen shows related controls in
collapsible sections and makes dependent controls inactive when their parent
feature or preset is unavailable. Their stored values remain intact and become
active again when the dependency is restored.

Client Tech Tree settings now default to the `BALANCED` layout preset on a new
installation. Existing version-zero client files migrate to `CUSTOM`, so their
individual spacing, wrapping, ordering, and compaction values remain
authoritative. Named presets are reversible overlays and never overwrite the
dormant Custom values. Restore Tree Appearance is the only control that
intentionally resets those advanced values.

Operators can use this preview-first flow after installing or changing TaCZ
content packs:

1. Run `/gg research setup assess` after the catalog is available.
2. Resolve any reported research-structure warnings; a pacing preset cannot
   repair missing prerequisites or competing rules.
3. Run `/gg research setup preview <custom|accessible|balanced|scarce>`.
4. Apply only with `/gg research setup apply <preset> confirm`.
5. Optionally run `/gg research setup export`; the deterministic format-1 file
   is written to `<world>/taczweaponblueprints/setup-assessment.json` and
   contains no player knowledge.

The assistant subtracts effective progression exemptions and valid starting
blueprints before evaluating discovery workload. It recommends `ACCESSIBLE`
when at least 240 effective entries or 96 effective non-TaCZ entries remain,
otherwise `BALANCED`. Disabled blueprints block readiness; disabled research or
unavailable loot distribution produce explicit review warnings. It never
automatically applies a choice and never recommends `SCARCE`. Switching to
`CUSTOM` restores the saved custom values because named presets do not rewrite
them.

## Upgrade procedure

1. Stop the server cleanly.
2. Back up the world, player data, configuration, and custom datapacks.
3. Replace the mod JAR and confirm required dependency versions.
4. Start the server and wait for the blueprint catalog and loot snapshot messages.
5. Run `/gg loot status` and record the revision, catalog count, and distribution mode.
6. Run `/gg research status`, inspect representative roots and leaves, and optionally export the live catalog.
7. Run `/gg research setup assess` and preview the recommendation; do not apply it until any structural warnings are understood.
8. Inspect and preview representative loot tables before reopening the server to players.

Existing server configuration files from version 2 migrate to Classic
progression with Blueprint Fragments disabled. This preserves their established
research, crafting, and loot behavior. New installations use the tiered
progression and targeted-fragment defaults; operators of upgraded worlds can
choose those presets explicitly after reviewing their economy.

Server configuration version 4 adds independent crafting category strategies,
no-level and disabled category or subgroup selectors, and exact crafting
overrides. Version-3 files migrate with every new control at its neutral
`PROFILE` or empty default. Existing blueprint-free selectors remain
knowledge-only exemptions and are not copied into the new crafting selectors,
so their established Workbench behavior does not change during migration.

When Blueprint Fragments are enabled, `fragmentLootReplacementPercent` replaces
that share of successful blueprint opportunities rather than adding more loot.
The default is 20 percent, and the fragment uses the same rule candidates,
blacklists, roll budget, and active research profile as the full blueprint it
replaces. Loot generated with player context favors unlearned targets. Loot
without player context preserves the configured catalog weights. A reload with
mismatched catalog, research, progression-policy, or configuration revisions
temporarily preserves full-blueprint drops instead of creating fragments from
stale policy. See the [Blueprint Fragment supply guide](blueprint-fragment-supply.md)
for item validation, target weighting, and preview diagnostics.

The Research Point award foundation advances persisted player progression from
data version 1 to 2 by adding a bounded, server-only `ResearchPointAwards`
ledger. Version-1 saves migrate automatically with an empty ledger; learned and
discovered blueprint IDs, Research Points, and legacy recipe aliases remain
unchanged and require no world conversion. The ledger is copied across every
player clone and is not sent to clients. Player data version 4 adds bounded
`ArchivedBlueprintFragments` and `ProgressionCriteria` maps. Version-3 saves
migrate with both maps empty; malformed entries are skipped, duplicate IDs keep
their highest value, and an oversized but inspectable list retains the
lexicographically earliest 4,096 canonical IDs. A list with more than 8,192
serialized entries is rejected as a unit without affecting learned blueprints,
discoveries, RP, award history, or recent unlocks.

The custom network protocol is `55`, so clients and servers must update
together. Protocol 50 transfers each
disclosure-safe research graph, matching group presentation, and optional
identity-safe Tech Tree metadata as one bounded atomic publication. It also
transfers canonical prerequisite-group boundaries, visible alternatives,
hidden-alternative counts, and only disclosure-safe satisfaction state. It retains
the server-resolved curated-overview flag and the coarse gun/ammo/attachment
kind used by research selectors, plus bounded Tech Tree rank, long sibling
order, optional visual-band references, placement-origin metadata, an ordered
bounded table of custom band labels, and the tree-owned resolved 8–28 node
layer capacity. It additionally sends only fragment targets whose blueprint ID
is disclosed by the current Blueprint Journal or Tech Tree and only durable
criterion IDs declared public by a disclosed Progression Gate. Hidden and
unrelated criterion IDs are never written to the client packet.
It retains hardened progression chunk generations and uses a research-only,
live-inventory Research Bench preview and correlated authoritative guidance,
including multi-node unlock count,
aggregate path costs, and the complete material-type count even when only the
first six bounded material rows are shown. The preview also carries the
effective research-cost mode, an opaque route-and-quote fingerprint, and the
difference between an unavailable automatic-tree publication and a published
graph with no complete authorized route. The server rejects a Research action
whose fingerprint no longer matches the freshly prepared attempt, refreshes
the current preview, and consumes nothing. A matching attempt commits the same
prepared plan rather than planning the path again.
For the exact selected identity, that preview also carries the current and
highest required Research Bench level, its Blueprint Fragment count and
threshold, and a minimal disclosed crafting result: required Workbench level,
any Workbench, or unavailable. The client uses this existing response for
selected-node details, narration, and two tiny selection-only markers; it does
not poll the server, scan the world, or decorate every ordinary node. Journal
fragment progress is included only on entries whose identity is already
disclosed by that Journal publication, while crafting access is limited to
entries permitted to show full policy details. Assignment rules, sources,
reasons, and warnings remain operator-only.
Authoritative guidance requests are accepted only for the matching open Bench
and latest research publication. Responses expose public route identities only,
cap route nodes, purchases, selected requirement proofs, and displayed material
rows independently of the larger transaction limits, and leave guidance
unavailable rather than sending a partial or oversized proof.
The fullscreen Affordable Now filter reuses that server authority through
ordered batches of at most eight visible targets. The server evaluates no more
than one queued target per tick across open Research Benches, and each player
has a separate bounded batch-admission window. Regularly reserved background
ticks prevent interactive requests from starving those queued sweeps, while
selection, research, tracked guidance, and live preview refreshes share the
same server-wide planning fuse. Repeated batch failures become bounded
unavailable results instead of leaving the client in Checking indefinitely.
If a response is lost entirely, tracked guidance retries once after ten
seconds and then reports the route as unavailable. An Affordable Now batch
uses a longer sixty-second window for shared queued work, retries once, and
then advances past only that batch with bounded unavailable results. Late
responses retain their original request identity and cannot replace newer
state. Unexpected fail-closed route exceptions are logged at most once per
minute with a suppressed-failure count so broken content or capability
integrations remain diagnosable without allowing request spam to flood logs.
Unchecked nodes remain neutral;
the client never estimates affordability or changes the published layout.
Exact tracked-goal pricing and Affordable Now are hidden when the server uses
the `CREATE_BLUEPRINT` compatibility result mode because that mode does not
purchase a complete prerequisite path.

Protocol 55 also binds each open crafting Workbench recipe response to the
exact menu request, response sequence, active profile, loaded catalog,
research data, automatic evidence, ammo associations, crafting rules, and
effective Workbench context that produced it. The first chunk of a newer
response immediately clears the previous selection. Until the full response
arrives, the screen keeps its TaCZ tabs as non-selectable placeholders and
shows that it is checking access. Late, mixed, conflicting, and duplicate
response data cannot restore an older selection. Disabling blueprint
progression uses an explicit server-confirmed unrestricted response so TaCZ
recipes that are not represented in the blueprint catalog remain available.

Operators can inspect one player's resolved target progress or a crafting
workstation mapping without opening a Bench:

```text
/gg progression inspect <player> <blueprint_id>
/gg progression workstation <workstation_id>
```

The target form reports tier source, research and crafting tiers, archived
fragments and threshold, fragment mode, and current unmet Progression Gate
groups. The workstation form reports the resolved external or native tier,
mapping source, and unrestricted state.
Blueprint Analyzer previews independently carry
duplicate, Research Data, Blueprint Fragment archiving, physical-item
reverse-engineering, trusted weapon origin, and direct found-weapon recovery
decisions plus an opaque
workstation-state token. It correlates Research Bench
selection/research results and sends
bounded disclosure-filtered RP help plus aggregated award feedback. This
compatibility change does not alter learned
blueprints, discoveries, Research Points, or the required format number of
existing research-tree group datapacks. The Research Bench now requires a
publishable Tech Tree presentation: missing or unusable presentation data keeps
the player in an explicit unavailable Tech Tree state and records the failure
server-side. Dormant Branches and All Weapons data remains internal and is not
used as a player-facing fallback.

Cross-domain Tech Tree navigation adds no packet or migration field. Clients
derive one immutable reciprocal relationship index from the graph and placement
metadata after the current atomic publication completes. A portal is usable
only while its exact endpoints and direction remain in that current index.

Existing valid learned recipe IDs remain readable from the `Recipes` capability
list. When an ID matches any current recipe alias, it is migrated to a durable
blueprint output ID in the new `Blueprints` list and synchronized as the
catalog's deterministic canonical recipe. Both lists are retained so rollback to
an older release remains possible. IDs from an entirely removed content pack
remain persisted; reinstalling a pack that supplies the alias makes them
migratable again.

## Dynamic and legacy distribution

This release packages one dynamic modifier and 485 generated legacy modifiers.

- An enabled dynamic rule owns only its exact or selector-matched tables.
- A targeted disabled rule suppresses dynamic and legacy distribution for its targets.
- A disabled rule with no targets is the explicit global legacy opt-out.
- Tables not owned by any dynamic rule may continue using their legacy modifier.
- Pools without rules can be staged without changing existing loot.

This table-selective bridge supports incremental datapack migration. The legacy set is intentionally retained for the 1.0.x release line; removing it is a future breaking migration and should occur only after deployed packs have moved to dynamic rules.

## Safe datapack rollout

1. Add tags and pools first; verify they load with `/reload` and `/gg loot pool`.
2. Add a rule targeting one test table.
3. Use `/gg loot inspect` to confirm ownership and predicates.
4. Use `/gg loot preview` to confirm blacklists, effective defaults or overrides, and weights.
5. Expand exact targets or selectors after the test rule behaves as intended.
6. Use a targeted disabled override when intentionally suppressing one built-in policy.

Definitions with the same namespace and path replace lower-priority definitions. Different IDs are additive. A failed reload does not publish a partial snapshot; verify that `/gg loot status` retains the previous revision.

Research progression and presentation use three independent resource
directories:

```text
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
data/<namespace>/taczweaponblueprints/research_tech_trees/<path>.json
data/<namespace>/taczweaponblueprints/research_tech_tree_entries/<path>.json
data/<namespace>/taczweaponblueprints/research_automatic_placement_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_point_awards/<path>.json
```

Roll out group resources after their referenced profile and rules. Group ranks
organize the UI only; they never replace prerequisite rules. After `/reload`,
use `research status`, inspect representative members, and run `research
export`. Export format 20 retains the format-2 research and presentation fields
and the revision-matched automatic-placement summary and per-weapon
decision: authored, automatic, excluded automatic, or legacy-compatible
unplaced. Automatic entries
record their score, confidence, semantic position, generated rank, optional
band, version identities, and planned prerequisite list or omission reason.
Format 8 added live topology/economy audits and per-weapon parent, role
similarity, fan-out, merge, and review evidence. Costs remain owned by research
policies; the report does not derive them from ranks or tiers. Format 19 also
records the revision-matched workstation-tier, Blueprint Fragment, and
Progression Gate publication, including assignment sources and explicit
omissions.
Format 9 adds the topology weapon population, effective layer width, width
mode, and configured minimum/maximum width so an operator can reproduce the
server's dynamic-width decision.
Format 10 adds the exact branch-aware prerequisite decision captured by the
planner, parent-family relationships, transition boundaries, terminal/depth
flags, and an aggregate branch-prerequisite summary. It replaces heuristic
merge labels in the authoring report without changing prerequisite authority.
Format 11 adds the deterministic second-parent quota, eligibility result, and
exact RP-closure costs whenever an optional merge is rejected for inflation.
The legacy singular planned-
prerequisite field and `excluded_fallback_count` summary remain as compatible
aliases. Format 4 introduced `excluded_automatic`, format 5 added dynamic rank
and band coordinates, format 6 added tree-owned layer capacity, format 7 added
`tech_tree_presentation` with the tree-owned band mode and configuration, and
format 8 adds the diagnostic and economy review sections, format 9 adds
configured/effective width evidence, format 10 adds exact branch decision
provenance, format 11 adds gradual-merge and closure-guard evidence, and format
12 separates planned rank indexes from finalized publication ranks. Format 12
also adds an automatic `publication` summary with exact canonical-branch,
decision, and finalized-rank counts and a combined completeness result. A false
result is actionable for a current connected publication; legacy compatibility
plans report unavailable branch coordinates instead of fabricating them. Format
13 adds canonical `prerequisite_groups` to every entry while retaining the
legacy conservative `prerequisites` union for existing tools. Strict
format-3 through format-13 consumers must explicitly
migrate instead of receiving new fields or enum values under an older schema.
Format 14 adds the automatic `prerequisite_strategy`, authoritative generated
group counts, alternate-route group counts, and per-entry
`planned_prerequisite_groups`. The flat planned-prerequisite list remains its
conservative compatibility union. The grouped field is copied from the
canonical runtime plan rather than reconstructed from selection order. Legacy
branch-summary field names containing `merge` remain available, while the
operator status and format-14 aliases describe the same values as multi-parent
sets so both mandatory AND and alternative OR strategies are represented
accurately.
Format 15 adds the live `grouped_route_quality` report. It contains
requirement-aware mandatory ancestry, effective-alternative and route-cost
distributions, branch-entry redundancy/overlap, phase fan-out and family OR
density, single-route chains, per-terminal minimum-route bounds, configured-
income affordability, and non-blocking warning codes. Strict format-14
consumers must migrate explicitly; the underlying format-14 group and strategy
fields retain their meanings.
Format 16 adds `grouped_route_motif_assessment`. It records the versioned
retain/prototype/insufficient-evidence decision, every semantic review signal,
the bounded motif families a future prototype would target, and the explicit
boundary between pre-junction crossing estimates and unavailable rendered-
junction evidence. The assessment is read-only and cannot change generation,
research, or layout authority. Strict format-15 consumers must migrate
explicitly; the format-15 quality report retains its meaning.
Format 17 adds the strategy-specific grouped `alternative_route_review` for
automatic prerequisite decisions and aggregate reviewed/accepted/proven-cost-
rejected counts. Each review carries lower/upper route costs, lower/upper cost
ratios, mandatory-ancestry overlap, divergent-node count, outcome, and
exactness. Legacy closure-inflation evidence remains available for
`legacy_and`; strict format-16 consumers must migrate explicitly.
Format 18 adds the explicit automatic `requirement_shape` and aggregate
pure-alternative, mandatory-convergence, and mixed hybrid relationship counts.
Strict format-17 consumers must migrate explicitly; the canonical group and
route-review fields retain their meanings.
Format 19 adds the revision-matched resolved progression-policy summary and
per-entry workstation tiers, tier-assignment source, Blueprint Fragment policy,
Progression Gate counts, automatic score/percentile evidence, review fallback,
and explicit omission reason. It also records the config snapshot that produced
the publication, including external workstation mappings. Strict format-18
consumers must migrate explicitly; earlier tree and route fields retain their
meanings. `selected_progression_rule` is present only when the selected research
rule contains a non-empty progression override; the entry's existing selected
rule field remains the source for ordinary research-policy attribution.
The eager progression-policy publication is limited to 262,144 total
profile-catalog assignments. Reloads above that aggregate budget retain the
last working publication instead of attempting an unsafe allocation. For each
exported profile, the policy and omission maps must be disjoint and together
cover the complete catalog.
Format 20 adds a complete independent crafting-policy projection for every
catalog entry. It records tiered, unrestricted, and disabled dispositions,
Workbench levels, assignment sources, selected crafting rules, reason codes,
bounded warnings, and coverage totals separately from Tech Tree research
inclusion. Research and crafting choose the most-specific applicable rule in
independent precedence passes, so a rule scoped to one action cannot hide a
broader rule for the other. Strict format-19 consumers must migrate explicitly;
the format-19 research, fragment, and gate fields retain their meanings.
Tech Tree-only members still record
their domain/lane/tier/order, and groups still record authored IDs absent from
the live TaCZ catalog. A malformed group,
missing profile, duplicate active-profile membership, or rank that contradicts
an effective prerequisite rejects the complete research reload and preserves
the last published research snapshot.

Permission-level-2 operators may grant RP with
`/gg progression points give <targets> <amount>`. Grants respect the live
`researchPointCap`, synchronize successful balances immediately, and leave
learned/discovered blueprint state unchanged.

New configurations use a 10,000 RP balance cap. The Server Settings slider is
bounded to 0-100,000 so accidental extreme values do not make the control
impractical. Upgrading a configuration whose cap is above 100,000 bounds the
configured cap to 100,000, but never deletes an existing player's stored RP.
A player already above the new cap keeps that balance and simply cannot receive
additional RP until spending brings it below the configured limit.

Research Point award resources reload independently from research progression
and loot. A malformed award file, conflicting shared-budget declaration, or
hard-limit violation rejects only the candidate award publication and keeps
its previous revision active. After adding or changing award data, run
`/reload`, then `/gg research awards status`. Use
`/gg research awards inspect <definition_id>` to confirm the selected trigger,
group, priority, points, overflow, repeat policy, profile scope, budget, and
target-term count. Normal resource-pack priority replaces the same definition
ID; different definition IDs are additive. Use
`/gg research awards sources` to list optional-mod integration event IDs and
`/gg research awards trigger <targets> <source>` as the permission-level-2,
repeat-safe command-function bridge for datapack-authored `integration`
definitions. Advancement completion, first
blueprint discovery, first learning, and filtered discovery/learning milestones
now dispatch this data. Definitions must explicitly opt into retroactive login
and reload catch-up. `enableResearchPointAwards` disables only these datapack
awards. `entity_killed` definitions additionally require
`enableCombatResearchPointAwards`, which remains off by default. Combat rules
fail closed for missing spawn provenance unless explicitly authored otherwise;
their safe defaults reject indirect, fake-player, pet, PvP, baby, tamed,
spawner, bred, and summoned sources. Neither setting
changes duplicate recycling, trusted operator grants, balances, or saved award
history.

### Built-in Research Point economy

The bundled publication contains 15 enabled definitions. A first discovery of
any current-catalog blueprint grants 1 RP once for that blueprint, including
retroactive reconciliation. Discovery thresholds at 10 and 25 and learned
thresholds at 5, 15, and 30 grant 18 finite RP in total. Acquire Hardware,
Diamonds!, A Terrible Fortress, Into Fire, The End?, and Free the End grant a
further 28 finite RP and reconcile for existing players. Every bundled finite
reward uses `require_full`: when its full value cannot fit below the RP cap, the
claim remains unconsumed and the reconciliation scheduler retries it after the
player spends RP.

For the pinned TaCZ 1.1.8 catalog, the maximum first-discovery plus fixed
milestone/advancement income is 218 RP against 1,246 RP of complete default-tree
research cost. It is intentionally supplemental; duplicate recycling,
exploration, and pack-authored sources remain relevant.

Research Notes, Reports, and Dossiers redeem for exactly 1, 3, and 6 RP with
`require_full` overflow, so a near-cap balance never consumes an item for a
partial award. Notes have a 12% chance in abandoned-mineshaft, simple-dungeon,
and pillager-outpost chests. Reports have an 8% chance in stronghold-library,
woodland-mansion, and bastion-treasure chests. Dossiers have a 5% chance in
ancient-city and End-city-treasure chests. These eight
`taczweaponblueprints:research_data/*` global loot modifiers can be replaced or
removed by a datapack independently of the award values. The global RP award
kill switch also prevents their built-in loot injection while disabled.

No built-in `entity_killed` or `integration` award is shipped. Combat remains
an explicit pack-author choice and still requires the separate combat kill
switch.

The progression reset command accepts `learned`, `discovered`, `points`,
`awards`, `fragments`, `criteria`, and `all`. The first three preserve RP award,
fragment, and Progression Gate criterion history. `awards`, `fragments`, and
`criteria` each clear only their named state, while `all` clears every category.
Point spending, datapack removal, and configuration changes never clear these
independent histories.

Operators can inspect or change one custom Progression Gate criterion without
resetting the rest of a player's state:

```text
/gg progression criteria inspect <player> <criterion>
/gg progression criteria grant <targets> <criterion> [value]
/gg progression criteria increment <targets> <criterion> <amount>
/gg progression criteria reset <targets> <criterion>
```

Grant sets a minimum and never lowers an existing value. Increment saturates at
the saved-data limit. The specific reset does not revoke vanilla advancements.
See the [Progression Gate criterion API](progression-gate-api.md) for integration
semantics and Forge events.

Every `/gg progression reset <targets> <state>` reset also clears the player's
bounded recent-unlock list in the Blueprint Journal. The criterion-specific
command above leaves recent unlocks unchanged. This list records successful Tech Tree,
physical-blueprint, and administrator learning batches only; starting grants,
migrations, failed transactions, discoveries, and RP-only activity do not
enter it. Unavailable content remains in the separate Unavailable view.

The built-in profile assigns exact research policies to the complete
recipe-backed TaCZ 1.1.8 catalog: 53 weapons, 95 attachments, and 24 ammunition
types. The built-in profile uses format 4 and activates only the 53-weapon tree by default;
attachment and ammunition rules, placements, and RK-6/9mm entry candidates
remain authored but dormant. The legacy Branches and All Weapons projections
remain weapon-only and are retained only as hidden compatibility data; the
Research Bench exposes Tech Tree alone. Servers can opt either dormant domain
back in with a format-2 profile whose matching `domain_policies` entry is enabled.
Adding these prerequisite edges does not change persisted learned IDs, so
servers do not need a progression migration and already learned content remains
learned. Disabled domains do not resolve or rebase entry candidates. Once a
domain is enabled, its ordered candidates again select the first usable
fallback. Existing format-1 profiles retain their previous all-domain behavior.

Existing Tech Tree resources remain format 1 compatible and are always
`authored_only`. Tree format 2 adds explicit `weapon_placement_mode` authority.
In `authored_only`, only non-fallback exact, tag, and selector placements appear;
all unspecified weapons are omitted. In `automatic`, every catalog weapon is
generated and all authored weapon positions and prerequisites are ignored. An
automatic tree must have exactly one placement profile; an authored-only tree
must have none. This intentionally removes hybrid population and prerequisite
behavior. The 95 attachment and 24 ammunition placements remain authored in
format 1 and can be enabled by an opt-in format-2 profile. Lane and
sibling metadata is still accepted and remains useful as a deterministic authoring
hint, but the client no longer promises one visible lane, column, or box per
classification. A custom pack that depended on the old lane-shaped layout
should validate its prerequisite topology and visual result before rollout;
costs and unlock authority continue to come only from research rules.

Research Tech Tree entry format 2 adds a required explicit `rank` to every
placement. Rank, not tier/level/order, is the dependency-order authority, and
every prerequisite must have a strictly smaller value. Format-1 resources must
omit `rank`; the server converts their tier/level positions with a wide stride
and topologically lifts legacy same-position dependents without changing their
rules, costs, or stored progression. Format-2 equal/backward edges reject the
reload rather than being repaired. Protocol 39 publishes rank, sibling order,
an optional visual-band reference, its bounded label table, and the tree-owned
layer capacity. The client
compresses sparse ranks into contiguous visual rows; band metadata cannot
change vertical dependency order. Client rows are capped by the tree's resolved
8–28-node policy, with additional presentation-only wrapping allowed for unusually
narrow embeddings. Empty bands and rows without cross-domain links reserve no
portal geometry. Disconnected graphs remain visible on the rank canvas and are
reported through stable layout component diagnostics instead of being hidden
by component-grid packing.

Automatic-placement profile format 1 remains backward compatible, including
its six score tiers and `levels_per_tier` behavior. Omitting `review_handling`
uses `exclude`, so warning-bearing estimates retain their legacy fallback
position. Format 2 creates contiguous stat-sorted ranks under the selected
format-2 tree's layout policy. A fixed policy supports 8–28 nodes. A dynamic
policy resolves `ceil(sqrt(4 × eligible automatic weapons / 3))`
inside its configured minimum/maximum (the built-in range is 9–20), and may
define an ordered set of custom score bands or omit bands entirely. The legacy
automatic-profile width remains readable for format-1-tree compatibility.
Format 4 adds `scoring_model`: omission preserves `mechanical_v2`, while the
packaged profile explicitly selects `capability_v3`. This changes only generated
placement evidence and topology; it does not rewrite player progress, research
costs, learned blueprints, items, or packets. To roll back scoring, override the
profile with `"scoring_model":"mechanical_v2"` and reload. The
bundled tree uses format 2 and its automatic profile uses format 4,
`connected`, `place_connected`, at most two generated prerequisites, a
one-weapon foundation, a configured bounded merge interval of four, a dynamic
9–20-node layer range, and dynamic three-rank presentation bands. It omits the legacy
`levels_per_tier` field because format-2 rank count is dynamic. Its lower ranks
form a shared multi-parent mesh; second-parent opportunities then taper
deterministically through the transition and remain possible at a bounded
branch-local floor toward the one-to-three-member terminal cohorts. If TaCZ cannot expose
usable runtime evidence for an add-on gun, the bundled policy assigns an
explicitly review-marked conservative band based on its weapon type and a
stable ID hash instead of placing every such gun at Basic level zero.

Automatic-tree profiles may use `review_handling: "place_independent"` or
`"place_connected"`; `exclude` is rejected because automatic authority must
place the complete weapon population. Likewise, profile `mode` must be
`distributed` or `connected`; `independent` does not assign positions and is
rejected. `place_independent` permits a reviewed position without making it an
anchor, while `place_connected` permits connected-mode planning. Roll authority
changes out against a world copy and verify the tree after `/reload`; stale or
unsafe automatic plans never restore authored weapon positions or prerequisites.
Use `/gg research status` to confirm the automatic mode and revision pair, then
`/gg research inspect <blueprint>` to compare its effective policy with the
automatic proposal. For a current connected tree, the publication line should
report complete candidate/branch-coordinate/decision/finalized-rank coverage.
Planned prerequisites may be suppressed at runtime by a
blacklist or visibility ceiling; the effective policy prerequisite count is
authoritative.

Before enabling a changed Tech Tree on a public server, test it against a copy
of the world, run `/gg research status` and `/gg research export`, then keep a
bench open through `/reload`. Verify that unavailable domains disable in place,
the selected domain falls back safely if removed, and restoring the pack
restores the same domain slot. A rejected reload must leave the prior tree and
player progress active.

## Rollback

To roll back custom loot policy while keeping the current mod:

1. Remove or disable the higher-priority custom datapack.
2. Run `/reload`.
3. Verify built-in definitions and the new revision with `/gg loot status`.

To roll back the mod version, stop the server, restore the previous JAR and matching configuration/datapacks, then restore the backup if the older version cannot read newer state. Do not replace JARs while the server is running.

## Diagnostics

- `status` distinguishes dynamic, globally datapack-disabled, targeted-disabled, and legacy-fallback modes.
- `inspect` includes disabled ownership and global opt-out rules.
- `pool` reports flattened entries/selectors and current pre-blacklist candidates.
- `preview` applies the current catalog, predicates, defaults, overrides, and immutable blacklist snapshot without consuming RNG.
- `research status` audits rule assignment plus authored group coverage and missing members for the active profile.
- `research inspect` reports separate research and crafting results for one live blueprint, including crafting disposition, Workbench level, assignment source, selected rule, reason, and bounded warnings.
- `research export` writes a sorted format-20 authoring catalog with complete
  crafting-policy coverage, dispositions, Workbench levels, sources, selected
  rules, reasons, warnings, canonical prerequisite groups, presentation groups,
  fallback, revision-matched automatic placement, topology,
  configured/effective width, per-weapon decision, and economy evidence under
  the current world directory.
- `research awards status` reports the independent last-known-good RP award
  revision, definition/group/budget/index counts, trigger totals, and the last
  rejected reload without disclosing player ledger history.
- `research awards inspect` reports one strict award definition's normalized
  resolution and accounting policy without evaluating or granting it.
- `research awards sources` lists bounded stable integration event IDs
  registered by installed server mods.
- `research awards trigger` invokes the current matching integration
  definitions and reports per-status outcomes without accepting a point amount.

Third-party TaCZ content packs can contain malformed recipes, language files, models, sounds, or missing definitions. The blueprint catalog isolates invalid recipes and reports aggregate samples while preserving valid entries. Fix those resources in the originating content pack; they are not blueprint datapack failures.

JEI and EMI are optional client integrations. A server should not install them
just for this mod. When either viewer is present, [TaCZ] Weapon Research & Blueprints adds
generic localized item information only; it does not treat a context-free
viewer page as an active Workbench. Current costs and available research and
crafting actions still come from the Research Bench, crafting Workbench, or
Blueprint Analyzer. If an optional-viewer startup fails, capture
a complete client log and verify the installed viewer is within JEI 15.x or EMI
1.1.x before treating it as a blueprint catalog or world-migration problem.

## Release verification

Run with JDK 17:

```text
./gradlew cleanTest test build
./gradlew verifyTaperedAutomaticTopologyContract
./gradlew verifyHybridRouteGenerationContract
./gradlew verifyAutomaticPublicationRecoveryContract
./gradlew verifyResearchGuidanceCandidateHandoff
./gradlew verifyReleaseArtifact
./gradlew verifyPublicationReadiness
./gradlew certifyReleaseCandidate
```

The artifact verifier rejects missing runtime classes, malformed packaged JSON,
mismatched modifier indexes, incorrect Minecraft/Forge/FML/TaCZ/Fzzy dependency
ranges, non-reproducible manifest timestamps, and unwanted cache or `.DS_Store`
entries. Publication readiness additionally verifies that the selected root
license is not the stock Forge MDK placeholder.
The tapered-topology gate writes
`build/reports/tapered-automatic-topology.json` and rejects drift among the
placement version, protocol, export format, required regression suites, and
packaged manifest. The publication-recovery gate writes
`build/reports/automatic-publication-recovery.json`, requires the localized
`/gg research status` health surface, and rejects drift in the four-state,
six-stage `staged-failure-recovery-v1` contract. The final certification task
records the verified JAR hash and exact dependency, protocol, test,
unified-tree, and automatic-placement contract metadata in
`build/reports/release-candidate.json`. The automatic section must record the
bundled `connected` mode with `place_connected` review handling, the three
version identities, dynamic layering with a configured 9–20-node range and its
baseline resolved width, optional
tree-owned optional/dynamic/configured presentation bands, the 4,096-candidate
limit, protocol 55, export format 20, and automatic prerequisite strategy
`grouped_routes_v1` unless the
corresponding contract and compatibility documentation are deliberately
revised.

The Phase 10 research-guidance handoff writes
`build/reports/research-guidance-candidate-handoff.json`. It binds the complete
Phase 9 automated report and manual-QA matrix to the exact candidate JAR,
preserves the protocol 55/player-data 4/client-settings 3 compatibility tuple,
and carries 21 clean invariants, 16 packaged classes, and 48 localized surfaces
into release certification. Its `requires_manual_qa` state is intentional:
complete the linked runtime matrix before publication rather than treating an
automated handoff as visual, accessibility, or multiplayer evidence.

The grouped-prerequisite acceptance gate writes
`build/reports/grouped-prerequisite-acceptance.json`. It rejects a missing,
renamed, skipped, failed, or errored Phase 7 acceptance case, and pins the
`truth-tables-integration-v1` contract in the JAR manifest and release-candidate
report. A deliberate change to legacy AND, grouped OR/AND, filtering,
publication, synchronization, parent bounds, or determinism must update the
affected tests, the acceptance contract version, and the migration notes
together.

The Phase 8 grouped-route rollout gate writes
`build/reports/grouped-route-rollout.json`. It consumes the Phase 7 acceptance
report, verifies the packaged format-4 `grouped_routes_v1`/`capability_v3`
profile and
default-disabled Attachment/Ammunition domains, and requires the exact rollout
evidence spanning planner, authority, publication, network, client, export, and
economy behavior. The report must identify `merge_interval` as
`ignored_grouped_routes_v1` for the grouped strategy and the generated-parent
cost guard as `group_aware_route_balance_v1`, while separately retaining
`conservative_legacy_and_union_closure_v1` for `legacy_and`. A deliberate
change to either control requires a rollout-contract version and
migration-note update.

The Phase 9 grouped route-selection gate writes
`build/reports/grouped-route-selection.json`. It consumes the Phase 8 rollout
report, rejects missing or non-clean selection/economy/client evidence, and
pins `group-aware-route-balance-v1`. Grouped selection rejects only a route
cost imbalance above the 8.0x extreme-review ceiling proven by its lower bound;
the 4.0x p95 value remains diagnostic. Uncertain authored AND-of-OR paths
remain eligible and retain explicit lower/upper bounds. The JAR manifest and
release-candidate report carry the same contract.

The Phase 10 stabilization gate writes
`build/reports/grouped-route-stabilization.json`. It consumes the Phase 7–9
reports and pins `default-rollout-migration-v1` after checking the packaged
format-4 `grouped_routes_v1`/`capability_v3` default, explicit and omitted
`legacy_and`
fallbacks, save compatibility, disclosure and packet bounds, cache
invalidation, and the 53/287/4,096-node scale matrix. Existing learned
blueprints are preserved and require no save migration. A grouped-generation
failure never silently switches semantics to legacy AND. The report records
the remaining screenshot/manual-acceptance checklist as a release gate rather
than treating visual judgment as an automated assertion. The JAR manifest and
release-candidate report carry the same stabilization contract.
The Phase 11 hybrid-generation gate writes
`build/reports/hybrid-route-generation.json`. It pins
`deliberate-hybrid-generation-v1`, verifies that `hybrid_routes_v1` remains an
explicit format-3 connected-mode opt-in, and requires deterministic mandatory,
OR, and mixed AND-of-OR planner evidence. The packaged profile remains the
bounded `grouped_routes_v1` default. A deliberate change to relationship-shape
semantics, gateway scheduling, hybrid cost/ancestry safeguards, or export
identity must update this contract and its migration notes together.

The grouped-prerequisite Phase 12 visual-refinement gate writes
`build/reports/grouped-visual-refinement.json`. It pins
`branch-aware-visual-refinement-v1` and verifies client-only family-preserving
responsive wrapping, bounded grouped-junction clearance, branch-seam pressure,
and deterministic 287/4,096-node geometry. It does not migrate saves or change
the published graph: prerequisite groups, semantic ranks, costs, protocol 55,
export format 20, and the packaged `grouped_routes_v1` strategy remain
authoritative. Before public release, retain the gate report and complete its
linked before/after screenshot checks at normal and maximum zoom-out.

The report's research-workstation split must record the permanent fullscreen
Research Bench, dedicated native TaCZ crafting Workbenches, one-input Blueprint
Analyzer, exact action ownership, final model and texture contract, recipe
discovery route, and manual-QA evidence path.

## Tiered Research Benches and crafting Workbenches

The Tier 1 Research Bench, Tier 2 Advanced Research Bench, and Tier 3
Experimental Research Bench open the permanent Tech Tree. The separate Level
1, Level 2, and Level 3 Workbenches open TaCZ's native Gun Smith Table interface
with the same recipe tabs and material-consumption behavior. Each family uses
its own two-block station, and there is no mode transition between them. Every
action is validated against the exact server menu, dimension, root position,
distance, complete structure, workstation ID, and tier.

Crafting remains server-authoritative. A recipe must be canonical and learned or
progression-exempt. When crafting tiers are enabled, the physical workstation
must satisfy the resolved crafting tier, and all crafting-scoped Progression
Gates must pass. The client receives only a bounded allow-list for recipes it
already knows; hidden gate identities and progress are not sent by this path.
The server builds this allow-list from the complete crafting-policy projection;
an intentionally omitted Tech Tree research assignment does not make its
crafting result missing or unrestricted.
The response is request-scoped and chunked. If it remains incomplete for five
seconds, the client may retry the original request once; the server rejects
additional retries and every attempt to advance the client-owned request ID.

Exact entries in `externalWorkstationTiers` take precedence for TaCZ-compatible
third-party workstations. Unknown workstations use
`unknownWorkstationFallbackTier` unless
`unknownExternalWorkstationsUnrestricted` is enabled. The latter bypasses the
ordinary crafting-tier band for compatibility, but criterion and advancement
Progression Gates still apply. Third-party workstations must use TaCZ's native
Gun Smith Table block entity so the physical source can be authenticated.

The addon overrides only `tacz:gun_smith_table`'s ordinary shaped recipe with a
false Forge conditional recipe. The TaCZ block, item, menu, assets, Creative
entry, and existing placed tables remain intact. A higher-priority world or
server datapack can intentionally restore or replace the recipe. Verify this
behavior after `/reload` whenever datapack priority changes.
