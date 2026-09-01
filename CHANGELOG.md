# Changelog

## Unreleased

### Changed

- Research prerequisites now require root-connected knowledge rather than only
  direct ownership of the immediately preceding blueprint. Weapons learned out
  of order through loot, reverse engineering, starting grants, migration, or
  administrator actions remain learned and usable, but cannot unlock research
  above themselves until a complete required route reaches a tree root. AND
  groups require every group to be connected, OR groups require one connected
  alternative, and shortest-path purchases can repair missing ancestry without
  charging for or relearning already-owned support nodes. Journal, tree group
  indicators, Bench previews, and server transactions share the same derived,
  memoized rule; player saves require no migration.
- Simplified weapon-tree authority into two explicit, mutually exclusive modes.
  An `automatic` format-2 tree now scores and places every catalog weapon and
  exclusively owns generated weapon prerequisites; authored weapon coordinates
  and prerequisites cannot leak into it. An `authored_only` tree publishes only
  non-fallback exact, tag, or selector placements and omits every unspecified
  weapon. Automatic profiles are required exactly once for automatic trees and
  forbidden for authored-only trees. Ammo and attachment authoring is unchanged.
- Migrated the bundled tree to full automatic authority with one generated
  foundation, aligned the packaged weapon costs with capability tiers, and
  updated topology fingerprints, release metadata, examples, diagnostics, and
  migration guidance. Missing or stale automatic plans now fail closed instead
  of silently restoring the former hybrid graph.
- Bounded generated automatic prerequisite edges to the preceding two occupied
  ranks for normal catalogs, removing disruptive foundation shortcuts such as
  low-row weapons connecting directly into the upper tree. Sparse branches use
  a nearby cross-family bridge only when neither preceding row contains a local
  parent. Extremely tall catalogs relax the span only as far as required by the
  global 64-node prerequisite-depth limit, with a bounded legacy-AND fallback
  that preserves reload performance at the 4,096-weapon ceiling.

## 1.3.0 - 2026-08-31

### Added

- Migrated 49 of the 53 bundled TaCZ weapons from legacy authored ranks and
  prerequisite chains to the same `capability_v3` automatic placement and
  grouped-route pipeline used for add-on guns. Glock 17 remains the shared
  entry and M320, RPG-7, and Minigun retain reviewed exceptions. Priority-zero
  single-exact fallback entries preserve safe coordinates when evidence is not
  ready, automatic tier bands now own the matching RP costs, and generated
  prerequisites rebase with live entry-point fallbacks when the preferred root
  is missing, blocked, or progression-exempt.

- Added the versioned `capability_v3` automatic weapon scoring model and made it
  explicit in the packaged format-4 automatic-placement profile. V3 captures
  projectile, falloff, explosive, burst, heat, charge, and tactical-reload
  evidence; scores six explainable capability packages; calibrates the result
  across the full progression range; and uses matching normalized role evidence
  for branch formation. Shotgun pellets no longer risk multiplying total TaCZ
  damage, launchers receive area-control credit, scripted/missing evidence stays
  review-marked, and the 53-gun reference is SHA-256 pinned. Existing custom
  profiles remain on `mechanical_v2` unless they opt into format 4. Status,
  inspection, comparison reports, and catalog exports expose model/version and
  authored-tier divergence without changing research authority or player saves.
  The bundled M320 is deliberately migrated from the opening Basic branch root
  to Advanced rank 5 behind the AUG and M870, with its RP/material cost raised
  to the matching authored band. The final scoring audit now shares TaCZ's
  deserialized defaults between runtime and offline extraction, evaluates real
  per-mode adjustments and burst trigger intervals, models charge overlap and
  heat recovery, separates armor bypass from multi-target pierce, treats
  explosion delay as an unscored seconds-based fuse, includes the expanded v3
  evidence in branch identity, and isolates v3 scoring failures from the v2
  rollback publication. Capability reports now identify formula and reference
  versions separately and replace outputs atomically.

- Added synchronized `POINTS_AND_ITEMS`, `POINTS_ONLY`, and `ITEMS_ONLY`
  Research Tree cost modes. Effective costs are masked non-destructively across
  single-node and shortest-path research, previews, operator economy output,
  and catalog exports, while authored datapack costs remain intact and the
  combined mode remains the default.
- Added versioned physical-gun origin tracking and configurable found-weapon
  recovery. New TaCZ survival crafts are marked crafted, generated loot guns
  are marked with their loot table, and unknown or legacy guns fail closed.
  Verified found guns can retain protected blueprint extraction, create a
  recyclable blueprint, convert directly to the existing recycling RP value,
  or offer both actions. Direct recovery pays current reverse-engineering
  costs, respects recycling policy and the RP cap, requires confirmation for
  an unlearned weapon, and commits or rolls back atomically. Matching clients
  and servers advance to protocol 40; player saves and existing items require
  no migration.

- Added atomic shortest-path Research Tree purchases for the packaged
  `DIRECT_LEARN` mode. Selecting a higher locked node now resolves every
  mandatory prerequisite group, retains a bounded nondominated route frontier,
  and chooses a globally fewest-new-node closure. Among equally short closures,
  an affordable route is preferred before RP, material-burden, and canonical-ID
  tie breaks. The Bench previews the full distinct-node RP/material cost and
  unlocks the complete prerequisite-first closure in one transaction. Shared
  prerequisites are charged once, already learned or progression-exempt
  prerequisites are free, invalid or undisclosed alternatives are not silently
  traversed, and any commit failure restores RP, inventory, blueprint
  knowledge, discovery, and legacy recipe aliases. Protocol 39 adds bounded
  unlock and total-material-type counts, explicit oversized/complex planning
  states, and append-only matching route results to the authoritative Bench
  preview. Path awards are dispatched
  as one ordered post-commit batch with correct intermediate milestone counts;
  `CREATE_BLUEPRINT` keeps its compatibility-safe single-node behavior.
- Completed grouped-prerequisite Phase 12 with bounded branch-aware visual
  refinement. Responsive wrapping now uses the minimum required row count while
  rejecting severely underfilled rows before preferring boundaries between
  mature weapon families; oversized families split only when capacity requires
  it and the shared base remains densely balanced. Partition state is bounded
  by row count times row capacity, a lone mature family remains coherent around
  authored nodes even with zero ordering sweeps, and responsive family wrapping
  is covered end to end. Multiple drawable any-of groups receive dedicated
  connector lanes, including the maximum valid 32-group stack, and
  alternative-route pressure can open one small capped branch seam. These
  client-only changes preserve node order, semantic ranks, canonical AND-of-OR
  authority, dynamic width, the 15% zoom floor, protocol 37, export format 18,
  and default-disabled Ammo and Attachment research.
  `verifyGroupedVisualRefinementContract` pins
  `branch-aware-visual-refinement-v1`, covers the 287- and 4,096-node fixtures,
  and records the remaining in-client screenshot comparison as a manual release
  gate.
- Completed grouped-prerequisite Phase 11 with the opt-in
  `hybrid_routes_v1` generator. It publishes explicit mandatory, alternate-route,
  or alternate-route-plus-gateway requirement shapes; schedules at most one
  deliberate gateway per eligible shared/transition rank; prices hybrid
  ancestry from canonical AND-of-OR groups; preserves ancestor, depth, cost,
  authored-authority, and specialization/terminal safeguards; and retains the
  packaged `grouped_routes_v1` default. Status, inspect, and deterministic
  export format 18 expose relationship identity and aggregate counts.
  `verifyHybridRouteGenerationContract` pins
  `deliberate-hybrid-generation-v1` without changing protocol 37, player
  knowledge, or the default-disabled Ammo and Attachment domains.
- Completed grouped-prerequisite Phase 10 with the release-blocking
  `default-rollout-migration-v1` stabilization contract. The packaged format-3
  automatic profile is certified as `grouped_routes_v1` by default while
  explicit and omitted `legacy_and` remain the migration-safe compatibility
  path; grouped failures never silently change semantics. The aggregate gate
  consumes the Phase 7–9 evidence and adds exact save-compatibility,
  disclosure, packet-bound, cache-invalidation, 53/287/4,096-scale, and
  viewport checks. It writes
  `build/reports/grouped-route-stabilization.json`, records the required manual
  visual acceptance boundary, and pins the contract in the JAR and release
  report without changing protocol 37, export format 17, player knowledge, or
  the default-disabled Ammo and Attachment domains.
- Completed grouped-prerequisite Phase 9 with strategy-correct automatic
  route selection. `grouped_routes_v1` now evaluates a proposed second parent
  by bounded individual-route cost and mandatory-ancestry diversity instead of
  pricing the mandatory union of both closures; only a proven cost ratio above
  the 8.0x extreme-review ceiling is rejected (4.0x remains a p95 warning),
  while uncertain authored AND-of-OR ancestry remains
  eligible with explicit bounds. Legacy AND retains its former union guard.
  Status, authoring evidence, and export format 17 expose review outcomes,
  exactness, cost bounds, ancestry overlap, divergence, and aggregate counts.
  `verifyGroupedRouteSelectionContract` binds 12 exact invariants and writes
  `build/reports/grouped-route-selection.json`; protocol 37, RP authority, and
  default-disabled Ammo and Attachment research remain unchanged.
- Completed grouped-prerequisite Phase 8 with the release-blocking
  `grouped-routes-v1-rollout-v1` contract. The planner now explicitly ignores
  `merge_interval` for `grouped_routes_v1` instead of relying on the current
  two-parent ceiling to make it inert; legacy second/third-parent scheduling is
  preserved and classified separately. Operator status reports that behavior
  and labels the retained generated-parent cost check as the conservative
  legacy-AND union-closure guard. `verifyGroupedRouteRolloutContract` requires
  20 exact invariants across strategy validation, deterministic parent/rank
  planning, authored authority, server/public/network/UI/export truth, client
  routes and recommendations, group-aware economy, and packaged domain
  defaults, then writes `build/reports/grouped-route-rollout.json`. The release
  manifest/report pin the contract; protocol 37, export format 16, RP authority,
  and default-disabled Ammo and Attachment research are unchanged.
- Completed grouped-prerequisite Phase 7 with a release-blocking semantic and
  integration acceptance matrix. Direct tests now freeze legacy two-parent AND,
  single-group inclusive OR (including ownership of both alternatives), and
  AND-across-groups truth tables; legacy JSON decoding into singleton groups;
  malformed/cyclic rejection; safe generated filtering; authored authority;
  disclosure-safe cardinality; server/network group identity; the grouped
  two-parent ceiling; legacy topology compatibility; and repeated-run
  determinism. `verifyGroupedPrerequisiteAcceptanceContract` requires the exact
  20 clean JUnit cases and writes a machine-readable report. The release
  manifest/report pin `truth-tables-integration-v1`; gameplay, protocol 37,
  export format 16, and default-disabled Ammo and Attachment research are
  unchanged.
- Added the grouped-route Phase 6 motif decision gate. It consumes the live
  Phase 5 semantic-quality and topology audits, deterministically retains the
  current grouped routes when evidence is healthy, names only bounded motif
  prototypes when ineffective alternatives, branch bottlenecks, long ladders,
  or route-cost imbalance warrant them, and refuses to recommend changes from
  incomplete authority. Pre-junction crossing estimates remain explicit manual
  visual evidence and cannot mutate prerequisites. Sparse branches are measured
  at their earliest actual post-split cohort rather than disappearing when the
  global family-start row is empty. `/gg research status` and
  deterministic export format 16 expose the `evidence-gate-v1` assessment;
  gameplay, protocol 37, placement version, and default-disabled Ammo and
  Attachment research are unchanged.
- Added the live grouped-route Phase 5 quality audit. Revision-matched
  `grouped_routes_v1` trees now report structurally effective alternatives,
  requirement-aware mandatory ancestry, route-cost balance, branch-entry
  redundancy/overlap, single-route chains, same/cross-family OR density,
  phase fan-out, and bounded minimum-route affordability for every terminal.
  The audit uses bitset ancestry at the 4,096-node ceiling and reports safe
  lower/upper bounds for authored multi-group routes instead of claiming a
  greedy estimate is exact. `/gg research status` exposes the live summaries;
  deterministic export format 15 adds full distributions, terminal evidence,
  and machine-readable warnings. Warning counts remain observational and do
  not become catalog-size-independent release gates. The manifest and release
  report pin `distributions-warnings-v1`; protocol 37 and gameplay authority
  are unchanged.
- Completed the truthful grouped-route Phase 4 client rollout. Inclusive
  any-of requirements now converge through disclosure-safe diamond junctions
  with one outgoing arrow, live disclosed satisfaction fill, spatially indexed
  culling, and support for separate AND-combined groups; singleton mandatory
  requirements retain direct arrows. Focus styling distinguishes alternative
  legs from mandatory edges, while selected-node summaries and tooltips say
  `Requires one of`, name only published alternatives, and report hidden or
  outside-view members as bounded anonymous counts. Existing group-aware layout
  cache comparison invalidates geometry on boundary changes but reuses it for
  satisfaction-only updates. Protocol 37 and export format 14 already carry the
  required canonical identity, so neither format changes.
- Activated automatic grouped routes in Phase 3. Automatic-placement profile
  format 3 adds the versioned `prerequisite_strategy`; `grouped_routes_v1`
  reuses the current branch/layer parent selection but publishes a selected pair
  as one inclusive any-of group, keeps singleton and generated-root behavior,
  and prohibits generated third alternatives. The packaged connected profile
  enables it while `legacy_and` and all older profiles remain compatible.
  Strategy identity now participates in atomic plan matching, diagnostics,
  status, the release manifest/report, and deterministic format-14 export.
  Authored requirements still win, rank reconciliation preserves canonical
  groups, either generated route satisfies research, and the obsolete Phase-0
  counterfactual is suppressed when grouped routes are already authoritative.
- Hardened the grouped-route rollout after its post-implementation audit.
  Resolved Tech Tree entry points now explicitly suppress generated
  prerequisites in both planning and live fallback resolution; diagnostic
  entries retain the exact canonical requirement groups used at runtime;
  format-14 export writes those groups without reconstructing their order; and
  future prerequisite strategies are forced through exhaustive planner,
  validation, and diagnostic handling. Format-3 compatibility is pinned to its
  actual introduction version rather than the moving current-format constant.
  Operator output now calls multi-parent selections what they are under both
  AND and OR strategies while legacy export field names remain available.
- Completed grouped-prerequisite Phase 2 across the live research pipeline.
  Research-rule format 2 now accepts strict, mutually exclusive
  `prerequisite_groups` with AND semantics across groups and inclusive OR
  semantics within each `any_of` group; legacy `prerequisites` retains authored
  order and mandatory singleton-AND behavior. Canonical groups now survive
  policy resolution, entry-point rebasing, automatic-plan adapters, per-group
  fail-open filtering, disclosure-safe public graphs, projections, protocol 37
  synchronization, client summaries, Journal counts, inspect output, and
  deterministic format-13 exports. Public groups retain stable ordinals,
  visible alternatives, hidden counts, and satisfaction only when the
  dependent's visibility permits exact policy disclosure. Topology and economy
  audits now distinguish real AND merges from OR choices while the existing
  singleton automatic trees retain their Phase-0 topology and fingerprints.
- Added the behavior-neutral grouped-prerequisite Phase 1 model.
  `ResearchRequirements` now represents AND across strict
  `ResearchPrerequisiteGroup` any-of groups with deterministic codecs,
  ordering, union-edge cycle validation, and bounded duplicate/self-reference
  checks. Existing rules, resolved policies, and generated plans expose their
  flat prerequisites as singleton groups, and the resolver evaluates that
  canonical singleton model without changing the current mandatory-AND truth
  table. That phase deliberately deferred grouped datapack authoring, public
  graph identity, protocol, layout, and gameplay to the end-to-end Phase 2
  migration above.
- Added the behavior-neutral grouped-prerequisite Phase 0 baseline. A new
  read-only audit compares current mandatory generated parent closures with a
  counterfactual in which each matched generated multi-parent set is one
  inclusive any-of group. It reports phase-specific parent density, fan-out,
  branch entries, single-parent chains, ancestry overlap, deterministic route
  estimates, finite-income affordability, and an input fingerprint without
  changing policy resolution, automatic placement, published edges, layout,
  protocol, datapacks, or saves. `/gg research status` labels both new evidence
  lines as diagnostic-only; 53- and 287-weapon fixtures freeze the results while
  the existing 4,096-node boundary remains intact.
- Rebalanced mixed authored/automatic trees after large-catalog visual QA.
  Automatic shared-trunk members now consume residual capacity beside authored
  nodes instead of lifting an otherwise full generated row into an empty upper
  rank. Mature same-family cross-sections remain atomic, preserving gradual
  multi-node tapers and terminal peer cohorts. Upper family boundaries now grow
  from a clearly visible initial gutter to a modest overview-scale branch
  envelope. Automatic topology `tacz-gun-placement-v12` identifies the changed
  mixed-rank finalization. Responsive wrapping now preserves a complete
  landscape row down to roughly one-third scale, preventing portrait windows
  from turning healthy 20-node semantic ranks into unnecessarily tall stacks.
  Protocol 36 and export format 12 are unchanged.
- Hardened large-catalog automatic trees after the tapered-branch review.
  Current dynamic connected publications now retain canonical decision evidence
  even when a candidate intentionally remains independent, authored
  prerequisites win, or its research policy is not selectable. Mature branch
  families remain visually contiguous, extreme Fit/hit-test queries use sparse
  spatial lookup, and operator wording now distinguishes semantic-rank width
  from visual-row capacity. Datapacks may opt into a 28-node authoring ceiling;
  the bundled tree remains at 9–20 for compatibility. The exact-match network
  protocol advances to 36 because older clients reject the newly valid 21–28
  capacity range.
- Added Phase 12 automatic-publication health and recovery diagnostics. The
  revision-coupled manager now distinguishes empty, awaiting-rebuild, ready,
  and failed states; failures retain the exact classification, positioning,
  prerequisite-planning, rank-finalization, reconciliation, or publication
  stage plus a normalized bounded reason. `/gg research status` exposes that
  evidence while partial payloads remain unavailable, and beginning a new
  revision clears stale failures. The manifest and release metadata pin health
  contract `staged-failure-recovery-v1`; a dedicated recovery gate writes a
  machine-readable Phase 12 report. Topology v11, export format 12, research
  authority, and disabled-by-default Attachment/Ammo domains are unchanged;
  the final release protocol is 36 after the later width-envelope hardening.
- Completed the tapered automatic-tree Phase 10 rollout. Current classified
  connected plans now publish atomically only when every candidate retains a
  canonical branch coordinate, prerequisite decision, and finalized rank;
  compatibility plans remain valid without fabricated branch evidence. The JAR
  manifest and release-candidate metadata pin placement version
  `tacz-gun-placement-v11`, protocol 36, export format 12, and publication
  contract `canonical-branches-decisions-finalized-ranks-v1`. A dedicated
  `verifyTaperedAutomaticTopologyContract` gate records the clean planner,
  finalizer, diagnostics, client-layout, network, and packaged-data suites in a
  machine-readable Phase 10 report, while artifact verification now requires
  all tapered-branch runtime classes.
- Hardened tapered automatic trees at their publication boundary. Canonical
  server branch coordinates now travel with every automatic member over
  protocol 35, so the client no longer guesses families or suppresses spacing
  for two- and three-branch trees. Rank-capacity lifting keeps complete
  semantic rows and terminal cohorts together; second-parent opportunities are
  selected by exact deterministic per-rank stratification; RP grace is limited
  to foundation merges; and accepted alternate parents retain the successful
  merge reason. Export format 12 and inspect output distinguish the planned rank
  index from the finalized published rank; status and export also report whether
  every connected candidate retained canonical branch coordinates, a prerequisite
  decision, and a finalized rank. The 287-weapon acceptance fixture now exercises
  that complete publication path. Automatic topology
  `tacz-gun-placement-v11` identifies these intentional parent-selection and
  publication changes.
- Added gradual, economy-aware prerequisite generation for the tapered-tree
  redesign. Branch-aware dynamic trees now use a deterministic second-parent
  quota that declines from a fully connected shared trunk to a 20% branch-local
  specialization and terminal floor instead of ending at a hard cutoff.
  `merge_interval` now controls only optional third-parent convergence. Optional
  parents whose combined RP prerequisite closure exceeds the dominant path by
  roughly 50% are rejected, with a one-direct-node allowance preserving cheap
  foundation meshes. Export format 11 and operator diagnostics record the exact
  quota and `merge_rejected_closure_inflation` cost evidence. Automatic topology
  `tacz-gun-placement-v10` identifies the intentional parent migration; authored
  prerequisite authority, manual trees, research costs, and protocol 34 are
  unchanged.
- Added adaptive terminal-cluster resolution for the tapered-tree redesign.
  Each branch now ends in one to three same-rank weapons only when reliable
  full-metric, per-metric, role, explosive-role, and locally adaptive score
  evidence supports equivalence. Oversized groups are divided by secondary
  role evidence when possible; otherwise three deterministic representatives
  are retained, the remainder stays below the apex, and
  `terminal_cluster_truncated` is reported by status and inspection. Automatic
  topology `tacz-gun-placement-v9` identifies the intentional terminal-rank and
  parent migration.
- Added exact branch-prerequisite provenance and evidence-driven branch
  spacing for the tapered-tree redesign. Status, inspect, the authoring report,
  and deterministic export format 10 now identify each generated node's
  foundation/trunk/transition/specialization strategy, family relationship,
  transition bounds, terminal cohort, and depth shortcut, plus aggregate
  same/cross-family merge and fan-out evidence. Client compaction now shares
  the generator's one-third family boundary, can infer up to twelve families,
  and increases early gutters when synchronized graph fan-out or crossings make
  a split harder to read. This is presentation and diagnostics only; it does
  not change generated parents, progression authority, or protocol 34.
- Added branch-aware prerequisite generation for the tapered-tree redesign.
  Connected dynamic weapon trees now keep two-parent cross-family density in
  the shared trunk, reduce merge frequency deterministically through the family
  transition, prefer same-family simultaneous parents in its later ranks, and
  become branch-local near each one-to-three-node apex cohort. Matching authored
  role anchors seed automatic foundations without becoming mutable inputs, and
  same-family depth shortcuts preserve the 64-node graph limit at the 4,096-item
  boundary. Legacy/manual placement, authored prerequisites, and existing AND
  requirement semantics remain unchanged. Automatic topology
  `tacz-gun-placement-v8` identifies the deliberate parent migration.
- Added branch-aware semantic rank allocation for the tapered-tree redesign.
  Current dynamic automatic weapon trees now use a dense shared lower trunk,
  begin family separation around the lower third while the multi-parent mesh is
  still active, and pack deterministic stat-ordered family levels that narrow
  toward one-to-three-node terminal cohorts. Per-family trunk limits prevent a
  dominant role from consuming the whole common base, the 287- and 4,096-weapon
  scales remain width-bounded, and legacy/manual placement is unchanged.
  Automatic topology `tacz-gun-placement-v7` identifies the intentional rank
  migration that the Phase 5 family-aware prerequisite selector now consumes.
- Added deterministic pre-topology branch discovery for the tapered-tree
  redesign. Trusted role signatures form population- and layout-bounded
  farthest-first families without splitting mechanically identical catalogs;
  uncertain and unscored weapons join existing families but cannot create a
  semantic one. Seedless catalogs use balanced neutral fallback families,
  ambiguous similarity ties use stable affinity instead of a first-branch
  bias, and authored weapons contribute read-only role anchors. Each family
  exposes one to three bounded layout strands and at most three representative
  terminal peers. Stable role keys and retained publication metadata make the
  analysis inspectable without changing ranks or prerequisites before the
  branch-aware allocation phase.
- Added deterministic pre-topology weapon role signatures for the tapered-tree
  redesign. Role comparison now uses strength-relative mechanical metric shape
  with bounded archetype and explosive modifiers, so vertical power and
  horizontal specialization are independent. Low-confidence, script-controlled,
  incomplete, and unscored candidates cannot seed future branches, while every
  eligible fallback still receives immutable role metadata. The authoring report
  uses the same shared role comparison; published ranks and prerequisites remain
  unchanged in this phase.
- Added population-aware Research Tree widths. Format-2 trees can retain a
  fixed 8–20-node capacity or select deterministic landscape-biased sizing
  from the complete authored-plus-eligible weapon topology. The built-in
  Weapons tree now grows from 9 to 20 nodes per semantic layer as larger TaCZ
  catalogs are loaded, reserves authored occupancy while finalizing automatic
  ranks, and allows late specializations to branch earlier. Client-only
  responsive wrapping remains independent and manual zoom now reaches 15%.
  The automatic topology now leaves its shared mesh after the lower two-fifths
  of a deep tree, keeps periodic merges out of later specialization ranks, and
  uses progressive branch gutters plus bounded sparse-corridor compaction.
  Protocol 34 synchronizes the resolved capacity, automatic topology
  `tacz-gun-placement-v6` pins the new result, and export format 9 records
  configured and effective width evidence.
- Completed the Phase 10 Research Tree rollout. The built-in 53-weapon manual
  bundle now uses contiguous explicit format-2 ranks with at most eight nodes
  per rank, while attachment and ammunition research stays disabled by default
  and its existing format-1 placement data remains available for opt-in
  profiles. Release verification now validates dynamic bands, bounded
  configurable merges, mixed-format compatibility, and a pinned topology
  fingerprint instead of requiring six tiers, three levels per tier, or a
  fixed merge interval.
- Added Phase 9 Research Tree diagnostics and economy review. Operator status,
  inspection, and deterministic export format 8 now expose topology quality,
  optional prior-fixture parent retention, per-weapon score/rank/parent/merge
  evidence, finite RP coverage, path and AND-closure costs, and the recalculated
  418 RP weapon-only default total. Costs remain research-policy owned and the
  automatic cost curve remains disabled.
- Added Phase 8 bounded rank-aware client layout. The normal client honors
  tree-owned bounded node rows, applies deterministic branch-aware barycentric
  ordering and compaction, routes long edges without widening intermediate
  ranks, reserves portal clearance only for actual cross-domain links, caches
  narrow responsive reflows, and reports disconnected components explicitly.
- Added Phase 7 tree-owned presentation bands. Format-2 trees may render no
  bands, compact dynamic occupied-rank bands, or configured rank/score bands
  with stable IDs, labels, colors, and icon metadata. Empty bands allocate no
  canvas space, format-1 trees retain their six legacy labels, protocol 32
  synchronizes styling, and export format 7 records the presentation policy.
- Added Phase 5 automatic-tree generation: format-2 profiles now build
  deterministic stat-sorted, append-stable ranks bounded to a configurable width
  (nine by default), create an interconnected lower progression that branches into
  later specializations, and support optional custom visual bands without
  forcing tiers into progression authority. Protocol 30 synchronizes custom
  band labels and research export format 5 records generated rank/band data.
- Added reversible Custom, Accessible, Balanced, and Scarce discovery-pacing
  presets. Named presets affect only global undiscovered visibility and default
  blueprint loot pacing; the underlying custom settings, datapack progression,
  access policy, and player knowledge remain unchanged.
- Added a permission-level-2, preview-first modpack setup assistant under
  `/gg research setup`. It audits the live catalog and research graph,
  recommends a preset without applying it, requires a literal `confirm` for
  changes, synchronizes online players, and exports a deterministic
  player-neutral format-1 assessment.

### Fixed

- Corrected crafted-gun provenance stamping to target TaCZ's actual
  compiler-generated crafting lambda, with a bytecode contract test that pins
  the external call site. Non-learning found-weapon recovery is now independent
  from physical-blueprint learning permission while retaining every content,
  equipment, cost, recycling, and RP-cap gate.
- Made Research Tree selected-node summaries, recommendations, tracked-plan
  summaries, tooltips, and narration respect `POINTS_ONLY`, `ITEMS_ONLY`, and
  `POINTS_AND_ITEMS` before an exact server preview arrives.
- Completed the post-Phase-4 automatic-tree compatibility audit: non-authored
  bundled reference guns now remain eligible for automatic-only trees,
  catalog-resolved format-2 selector ranks are validated before their
  catalog/research pair becomes live, and the expanded per-weapon export state
  is published under format 4 instead of silently changing format 3.
- Matched the Blueprint Analyzer's loaded-gun check to TaCZ's effective ammo
  rules. Empty open-bolt weapons are no longer rejected because of TaCZ's
  non-usable barrel-state flag, while magazine ammo, real chambered rounds,
  and unresolved add-on gun data remain safely blocked.
- Kept EMI out of the focused Blueprint Analyzer screen and made learned-item
  reverse engineering explicit. The bundled profile now permits creating a
  protected physical blueprint copy from already-learned equipment, while the
  Analyzer highlights that the recipe is already known, distinguishes the
  copy action from an unavailable duplicate, and describes transaction costs
  as items used instead of items missing. The authoritative known-state marker
  advances the exact-match network protocol from 27 to 28.
- Corrected discovery presets across legacy loot fallback, runtime diagnostics,
  persistence acknowledgement, dedicated-server client configuration sync, and
  effective-workload setup recommendations. The appended preset-sync packet
  advances the exact-match network protocol from 26 to 27.

- Made physical-blueprint consumption part of the authoritative learning
  transaction: the item is consumed before the atomic knowledge commit,
  restored on rejection, and never duplicated by a post-commit sync failure.
  Failed immediate recipe publication now queues a stronger deferred retry.
- Made direct-research previews preflight progression capacity, report an
  explicit full-knowledge state, and disable research before any RP or material
  cost can be attempted. Exceptional inventory or RP rollback failures now have
  honest typed feedback instead of claiming restoration succeeded.
- Treated repaired legacy blueprint invariants as full-tree migration changes,
  retained their required synchronization, clarified the pre-award research
  balance result, and added player-facing labels and descriptions for both
  Research Result configuration choices.
- Cleared keyboard focus whenever a fullscreen Research Tree overlay hides its
  focused control, preventing invisible card or guidance buttons from blocking
  graph navigation. Blueprint Recycler results now follow the exact post-action
  input kind and count, preserve successful empty-slot confirmation, and clear
  failures or old same-item feedback as soon as the physical input changes.
- Preserved both axes of reusable group-skeleton geometry while composing All
  Weapons, so multi-row automatic/add-on components no longer collapse onto
  one row and invalidate the entire Research Tree. Overview-only composition
  failures now use a bounded layout of the same publication instead of
  blanking Branches and Tech Trees, and terminal display failures no longer
  masquerade as a genuinely empty catalog. If a replacement publication is
  rejected after a valid tree is already visible, the retained tree now carries
  a persistent warning and cannot send stale selection or research requests.
  Reusable connector hints are also discarded per edge when final atlas
  placement would route them through another node, allowing the obstacle router
  to recover locally without losing the rest of the authored geometry.
- Expanded overlap diagnostics with both blueprint IDs, coordinates, tiers,
  orders, and components, making malformed custom layouts actionable without
  weakening the no-overlap invariant.
- Enrolled live cap-blocked finite RP rewards in retroactive reconciliation, so
  spending RP now wakes the saved complete reward without requiring a relog or
  datapack reload, and added clear feedback when a reward is parked.
- Made stacked RP previews simulate award groups sequentially on a detached
  balance-and-ledger copy. Previewed values now match cap, claim, cooldown,
  rolling-window, and shared-budget commit order without integer overflow or
  mutating live protection history.
- Closed the Research Data event reentrancy window by revalidating the live
  inventory slot after cancellable pre-award listeners, consuming exactly once
  after the first point commit, and firing post-award listeners only afterward.
- Removed exhausted one-time and fully claimed exact-target RP activities from
  the Research Bench earning-help list and refresh that list when claims commit.

### Changed

- Made Tech Tree the sole player-facing Research Bench view. Branches and All
  Weapons remain dormant compatibility projections but their compact selector
  and fullscreen rail action are hidden. Tech Tree is now the default and
  authoritative destination, including across reloads, search, recommendations,
  and restored client state.
- Removed the silent automatic-to-legacy presentation fallback. A failed Tech
  Tree publication now stays on an explicit unavailable Tech Tree screen and
  emits a server error instead of hiding the intended view or presenting a
  smaller legacy tree as though generation succeeded.

- Added strict research-profile format 2 domain policies. The packaged profile
  now publishes and researches Weapons only by default while retaining all
  authored Attachment and Ammo rules, placements, and entry candidates for
  datapack opt-in. Format-1 profiles keep their prior all-domain behavior, and
  physical-blueprint learning and reverse engineering remain available for
  disabled domains.

- Added a disclosure-safe first-hour Getting Started page to the Blueprint
  Journal, including a one-time key hint, persistent dismissal, an always
  available `?` reopen action, and live server-filtered RP earning suggestions.
  Added optional compile-only JEI 15 and EMI 1.1 information integrations for
  the Research Bench, Blueprint Analyzer, physical blueprints, and Research
  Data. The integrations enumerate no hidden catalog content and register no
  recipe transfer or research authority.

- Added synchronized progression exemptions by exact blueprint ID, coarse
  gun/ammo/attachment kind, or TaCZ item subgroup. Exempt content is available
  to craft but is not fabricated as learned knowledge, is omitted from
  research/loot/Analyzer surfaces, and satisfies prerequisites through live
  server policy. Added exact `startingBlueprints` grants that atomically and
  idempotently teach online and joining players without charging costs or
  issuing RP awards; removing a starter never revokes learned progress.

- Activated the Phase 5 Blueprint Analyzer while preserving the existing
  `blueprint_recycler` registry IDs. The workstation now owns one physical
  input and one extract-only blueprint output; eligible TaCZ guns,
  attachments, and canonical ammunition batches can be explicitly reverse
  engineered using the current server-resolved RP/material policy. Loaded or
  attached guns, blocked/exempt/known content, insufficient costs, occupied
  output, stale requests, and modified-item policy failures remain
  non-destructive. Commit revalidates the full physical stack, player
  inventory, policy, output, and opaque state token, rolls back RP/materials/
  equipment on any pre-output failure, records discovery only after the
  provenance-marked physical blueprint exists, and keeps that output out of
  the duplicate-RP loop by default. Duplicate recycling and Research Data
  redemption remain available contextually. The richer two-slot preview and
  request contract advances matching clients and servers once to protocol 26;
  registry IDs, player data version 2, and existing worlds are unchanged.

- Added the Phase 4 reverse-engineering policy and physical-item resolution
  foundation. Existing research profiles and deterministic exact/tag/selector
  precedence now own bounded per-target reverse costs, input overrides,
  known/modified-item eligibility, physical-blueprint prerequisite behavior,
  and output recyclability. The pure server evaluator resolves logical TaCZ
  gun, ammo, and attachment IDs (including add-on namespaces), derives ammo
  batches from canonical recipe outputs, rejects loaded/attached guns, and
  mutates no player or item state. Additive provenance fails protected output
  closed, unsafe direct RP loops reject reload unless explicitly acknowledged,
  and startup diagnostics report unmatched, unavailable, blocked, exempt, and
  expert-loop targets. The live two-slot Analyzer action and network changes
  remain deferred to Phase 5, so protocol 25 and current workstation gameplay
  are unchanged.

- Activated Phase 3 direct Research Tree learning. The packaged synchronized
  `DIRECT_LEARN` mode now preflights canonical knowledge capacity, spends the
  exact RP and live-inventory material plan, atomically learns the blueprint and
  downgrade recipe, and then dispatches exact discovery/learning awards and a
  full recipe sync. A failed prepared commit restores the complete inventory
  snapshot and original RP balance. Direct research creates no item and cannot
  fail on inventory space; server operators may select the compatibility-only
  `CREATE_BLUEPRINT` result without changing saves, datapacks, registries, or
  protocol 25.

- Added the Phase 2 atomic blueprint-learning authority. Physical blueprints
  now preflight and commit learned identity, discovery, and the canonical
  downgrade recipe together through one policy-aware service; exact transition
  flags prevent duplicate awards, legacy migration repairs invariants through
  the same capability operation, and every failure preserves the physical item.
  Research Tree output, Recycler structure, saves, registries, datapacks,
  configuration, and protocol 25 remain unchanged.

- Added the Phase 1 shared blueprint-access vocabulary and pure typed learning
  evaluator. Trusted origins, direct-versus-physical tree results, physical
  blueprint prerequisite modes, explicit block/exemption outcomes, bounded
  migration preservation, and live-award eligibility are now independently
  testable while all live research, learning, crafting, Recycler, save, datapack,
  configuration, and protocol-25 behavior remains unchanged.

- Established the Phase 0 contract and executable baseline for direct Research
  Tree learning and physical-item reverse engineering. The contract freezes one
  shared policy-aware learning authority, an eventual two-slot Blueprint
  Analyzer under the existing Recycler registry ID, explicit award and
  anti-loop behavior, and the current protocol-25 physical-output research
  baseline without changing live gameplay, saves, networking, or datapacks.

- Completed release hardening for the separated research workstations. The
  packaged-JAR gate now requires the complete Recycler runtime and validates
  its final model, facings, item parent, and 256x256 texture content; the JDK-17
  candidate report records exact Bench/Recycler ownership and keeps remaining
  in-game QA explicit. The live manual matrix now correctly requires protocol
  25 rather than its stale protocol-20 baseline.
- Replaced the Blueprint Recycler's temporary iron-block placeholder with a
  directional worn-steel workstation model, dedicated high-resolution texture,
  readable paper intake and output controls, and a matching non-cube outline.
  The item icon reuses the same model, all four placement facings remain
  resource-pack-native, and no registry, save, progression, or protocol change
  is introduced.
- Made the Blueprint Recycler survival-craftable from tagged iron, redstone, a
  grindstone, and a hopper without consuming the Research Bench. Possessing a
  blueprint, Research Bench, or Research Data now unlocks the recipe through
  normal recipe-book discovery, and the Recycler appears beside the Bench in
  Functional Blocks. The recipe is datapack-replaceable and changes no
  registries, progression data, saves, or protocol 25.
- Removed the Research Bench's unreachable combined-workstation compatibility
  adapter. Its server menu is now slotless, accepts only selection and research,
  and synchronizes a research-only live-inventory preview; duplicate recycling
  and Research Data redemption belong exclusively to the Blueprint Recycler.
  The narrowed Research Bench payloads advance matching clients and servers to
  protocol 25 without changing registries, progression data, or world saves.
- Split the player-facing workstations: the Research Bench now opens directly
  into one permanent edge-to-edge Research Tree with overlaid controls, while
  duplicate blueprints and Research Data use the dedicated Blueprint Recycler.
  The Bench no longer exposes compact mode, Research/Recycle tabs, inventory
  slots, or a fullscreen toggle; its close control and layered Escape behavior
  are now explicit and accessible. Registry IDs, save data, and protocol 24 are
  unchanged during this compatibility-preserving cutover.
- Packaged the default Research Point economy: first blueprint discoveries grant
  1 RP, five finite discovery/research milestones grant 18 RP, and six vanilla
  progression advancements grant 28 RP. Research Notes, Reports, and Dossiers
  redeem for 1/3/6 RP and now appear at conservative 12%/8%/5% rates in eight
  selected vanilla exploration chests through independently replaceable Forge
  loot modifiers. Combat income remains absent and disabled by default. Added
  economy-ratio, packaged-resource, loot-index, migration, and release-artifact
  gates for the complete 15-definition default publication. All finite defaults
  use lossless `require_full` overflow and retry after the player spends RP,
  preventing one-time rewards from being partially discarded at the cap.
- Added optional-mod and command-function RP integrations through a dedicated
  datapack-authored `integration` trigger, a bounded registered-source public
  server API, `/gg research awards sources`, and permission-gated repeat-safe
  `/gg research awards trigger`. Cancellable pre-award and immutable committed
  post-award Forge events now wrap every datapack award at the common atomic
  commit boundary without permitting point-value replacement. No optional mod
  dependency or player-data migration is introduced. Because earning-help
  packets carry trigger types, matching clients and servers advance to protocol
  23.
- Added disclosure-safe RP earning help to the Research Bench and short,
  aggregation-aware award notifications for live awards, retroactive claims,
  Research Data, and duplicate recycling. Public activities may be named,
  conditional activities are named only after their identity is already
  visible, and hidden activities remain generic. A client-only notification
  preference and bounded server-filtered packets advance the matching
  client/server network protocol to 22 without changing player data.
- Added physical Research Notes, Reports, and Dossiers plus explicit one/all
  Research Bench redemption from the player's live inventory. Exact item and
  tag values remain datapack-authored; server-side precedence, repeat/budget
  checks, full-cap rejection, a 64-item bulk bound, and consume-after-commit
  ordering prevent stale previews, Creative duplication, and client-authored
  values. The richer inventory-only preview advances the matching client/server
  network protocol to 21; default economy values remain deferred.
- Activated server-authoritative advancement, first-discovery, first-learning,
  filtered milestone, and opt-in combat RP awards with atomic repeat/budget enforcement,
  bounded streaming retroactive login/reload reconciliation, retry-on-balance-
  decrease handling, current-source revalidation, revision-cached blueprint
  selector facts, synchronized global/combat kill switches, and full-over-point
  synchronization priority. Combat captures persistent spawn provenance and
  lifetime, distinguishes direct/projectile/indirect/pet/PvP/fake-player facts,
  rejects farmable or unknown sources by default, and suppresses duplicate death
  callbacks with a bounded server-lifetime cache. No default award economy is
  packaged yet.
- Added strict, bounded format-1 Research Point award datapacks with an
  independent last-known-good reload revision, deterministic indexed
  specificity/group resolution, shared-budget consistency checks, safe combat
  defaults, and read-only `/gg research awards` diagnostics.
- Added the reusable server-authoritative RP transaction foundation with exact
  full/clamped outcomes, an explicit full-cap finite-claim path, and one atomic
  balance-plus-protective-history commit used by recycling and operator grants.
- Advanced player progression data to version 2 with a deterministic bounded RP
  award ledger for durable claims, cooldowns, rolling windows, and shared
  budgets; player cloning preserves it and the new `reset awards` mode clears
  it without erasing other progression.
- Made All Weapons include generated identity-visible fallback gun groups by
  default, so automatically placed content-pack weapons no longer appear only
  in Tech Tree or Branches. Attachments, ammunition, Undisclosed entries, and
  explicitly opted-out authored groups retain their existing scope.
- Kept dense Tech Tree levels compact by balancing them across presentation-only
  wrap rows, centering sparse rows, and bounding invisible connector waypoints
  to the configured maximum level width. Wrap rows do not change progression or
  create research prerequisites.
- Added a strict per-tree automatic-placement profile and a failure-atomic,
  catalog/research-revision-coupled eligibility gate. Automatic proposals may
  now outrank only genuine selector-only legacy fallbacks; authored placements
  remain protected. Profiles that omit review handling retain the original
  exclusion behavior.
- Projected revision-matched eligible automatic positions through the
  server-authored Tech Tree, protocol 20, and client layout using lossless
  tier/level/64-bit sibling-order metadata. Research prerequisites remain
  rule-authored, and any contradictory proposal falls back to the complete
  authored/legacy presentation atomically.
- Expanded the explicit `connected` automatic-placement mode from one parent to
  a configurable, depth-bounded prerequisite set. Ordinary add-on weapons keep
  one earlier anchor while tier gateways and periodic convergence points may
  use two (up to a configurable maximum of three); authored prerequisites retain
  precedence and blocked anchors still fail open rather than stranding content.
- Hardened connected placement so same-level anchors retain priority, reviewed
  `place_independent` proposals cannot become prerequisites for later weapons,
  convergence parents represent independent branches, primary-parent ordering
  remains stable in diagnostics, and generated edges respect the graph-wide
  prerequisite budget.
- Made Research Tree recommendations and selected-node summaries AND-aware:
  an outgoing relationship is counted as an immediate unlock only when every
  other prerequisite is already learned. Generic relationship navigation now
  uses the clearer `Leads to` wording.
- Kept maximum-size unified fan-out routing within its bounded performance
  contract by selecting the exact nearest unobstructed minimum-distance track
  before falling back to a full-canvas search.
- Added optional authored Tech Tree levels, same-tier progression validation,
  sparse cross-group rank alignment, and selective two-parent merges throughout
  the bundled TaCZ weapon graph. The default tree now combines Rust-like
  convergence with War Thunder-like within-tier steps instead of only jumping
  from one single-parent tier row to the next.
- Enabled bounded connected placement for unmatched add-on weapons in the
  bundled profile. Mechanically usable warning-bearing proposals now remain
  visibly review-marked while receiving tier/level placement and an earlier
  anchor; add-on weapons whose TaCZ runtime evidence is unavailable receive a
  deterministic conservative item-type band instead of collapsing into one
  disconnected Basic row. Datapacks can choose `exclude`, `place_independent`,
  or `place_connected` review handling, with omitted fields preserving the
  compatibility-first `exclude` behavior.
- Added revision-safe automatic-placement diagnostics to `/gg research status`,
  `/gg research inspect`, and deterministic research-catalog export format 3.
  Every weapon is explained as authored, automatic, excluded fallback, or
  unplaced, with proposal and planned-prerequisite evidence where applicable.
- Completed automatic-placement release hardening with a consistent
  4,096-weapon ceiling, maximum-scale deterministic regressions, exact packaged
  connected-profile and diagnostic-localization checks, and JDK 17 release
  metadata for every versioned placement contract.
- Added a disclosure-safe `Next` action to the Research Bench that recommends
  and focuses one currently available blueprint without selecting it for
  research, spending resources, or bypassing the authoritative server preview.
- Added a client-only tracked research goal with persistent prerequisite-path
  highlighting, route-aware `Next` recommendations, and disclosure-safe
  remaining-step, RP, and material-need summaries in compact and fullscreen.
- Rebased the built-in progression onto the 53 TaCZ 1.1.8 weapons that actually
  have gunsmith recipes, using Glock 17 as the preferred shared entry and an
  ordered catalog-aware pistol fallback when that recipe is unavailable.
- Added independent research-tree inclusion, profile entry-point candidates,
  and coarse gun/ammo/attachment catalog selectors for datapack authors.
- Added tier-scaled research policies for all 95 recipe-backed default
  attachments and 24 ammunition types, plus conservative selectable preview
  fallbacks for unmatched add-on content, while keeping both legacy weapon
  views weapon-only.
- Extended research exports with typed Tech Tree placement evidence and kept
  legacy group diagnostics scoped to their 53-weapon presentation subset.
- Replaced the temporary independent attachment and ammunition nodes with
  connected, single-entry, tier-monotonic domain trees rooted at RK-6 and 9mm,
  while retaining Glock 17 as the unchanged weapon entry.
- Added a UI-neutral Tech Tree topology audit and packaged gates for domain
  entries, connectivity, reachability, boundary prerequisites, and placement
  ordering.
- Generalized ordered entry fallback per Tech Tree domain so missing or blocked
  RK-6 and 9mm recipes rebase to the next selectable Starter instead of
  stranding an entire default tree; existing profiles remain compatible.
- Added one immutable disclosure-safe index for real cross-domain prerequisites,
  reciprocal requirement/unlock navigation, and exact per-node portal targets;
  portal activation now rejects stale or contradictory relationship metadata.
- Replaced Tech Tree lane columns with one prerequisite-driven canvas per
  Weapons, Attachments, and Ammunition domain using the shared layered kernel;
  lanes now influence deterministic tie-breaking only, and all three canvases
  reflow atomically with the client Research Tree spacing policy.
- Exposed Weapons, Attachments, and Ammunition as three direct icon selectors
  in the compact Tech Tree toolbar and as named entries in the fullscreen rail,
  with independent focus, search, pin, and camera state per mixed tree.
- Generalized Research Bench search, guidance, status, context-card, and
  narration copy from weapon-only wording to blueprint wording so attachment
  and ammunition research uses the same first-class interaction language.
- Hardened compact and fullscreen Tech Tree navigation behind one immutable
  disclosure-safe domain menu: all three slots now remain stable through
  partial publications and reloads, unavailable domains are disabled, and both
  surfaces share the same selected state, public icon, and blueprint count.
- Added Page Up/Page Down domain traversal that skips unavailable trees, plus
  current-domain position/count narration and explicit unavailable-domain
  feedback without exposing hidden nodes or authored placement metadata.
- Completed unified Tech Tree release certification: the packaged-JAR gate now
  reconstructs all exact placements, requires independent 53/95/24 domain
  coverage rooted at Glock 17, RK-6, and 9mm, rejects cross-domain or backward
  prerequisites, verifies typed add-on fallbacks, and pins every referenced
  Tech Tree localization. Release-candidate reports now record this certified
  view/domain/tier contract.
- Advanced the network protocol to `20` to synchronize coarse blueprint kinds,
  optional identity-safe Tech Tree metadata, and lossless tier/level/long-order
  automatic placement coordinates.
- Generalized the authoritative research graph to every research-enabled
  blueprint kind while deriving Branches and All Weapons from an explicit
  weapon-only presentation subset.
- Added client-only Reduce Motion and Show Background Grid preferences, with a
  cleaner grid-free default and immediate application to an open Research Bench.
- Centralized motion and decoration choices in one immutable display policy so
  accessibility changes never rebuild topology or affect server progression.
- Made arrow-key traversal relationship-first and non-committing: arrows move a
  local graph cursor, Enter explicitly selects, and the camera moves only far
  enough to keep the next node clear of fullscreen overlays.
- Made rapid fullscreen keyboard traversal compose against the camera's target
  position and allowed horizontal navigation to cross tiers when no same-tier
  neighbor exists, without adding permanent navigation chrome.
- Reduced untouched Research Tree nodes to four readable Learned, Available,
  Locked, and Hidden badge/fill families while retaining exact causes in hover,
  selected context, relationships, and narration; Help now includes the legend.
- Added `/gg progression points give <targets> <amount>` for permission-level-2
  RP grants with live-cap enforcement and immediate point-only synchronization.
- Made the visible Research button the only default transaction gesture and
  added an optional configurable fullscreen hold shortcut that only arms for an
  already-selected, server-confirmed ready weapon.
- Added one-shot audible and persistent contextual feedback for correlated
  research success, rejection, and timeout results.
- Added bounded client-only Research Tree display settings and apply them to
  Branches and All Weapons from one immutable live policy snapshot.
- Invalidate shared skeletons, layouts, projections, and saved camera geometry
  together when that policy changes, while retaining the last valid cache after
  a rejected replacement.
- Consolidated Research Bench runtime layout ownership in the projection cache;
  atomic Journal/tree publications no longer build or retain a redundant legacy
  category-lane layout.
- Kept the historical client layout accessor source-compatible by deriving its
  result lazily through the shared skeleton composer, and marked the superseded
  category-lane and grouped engines as compatibility-only.
- Added release gates that prevent production Research Tree code from returning
  to either compatibility-only engine and require the complete shared kernel,
  client configuration, and localized settings in the reobfuscated JAR.
- Made Branch Spacing control disconnected components inside one branch and
  Connection Padding reserve real portal clearance in both tree views.

### Fixed

- Indexed connected automatic-placement anchors by progression bucket, reused
  per-candidate depth results, and independently enforced the 4,096-weapon
  ceiling in every evidence, proposal, prerequisite, and diagnostic result.
- Removed the unreachable Taurus 943 blueprint prerequisite, added startup-time
  live-catalog research auditing, and pinned the default tree to the real TaCZ
  1.1.8 gun-recipe set so missing roots fail verification.
- Made reduced motion complete an in-progress camera transition and keep focus,
  Fit, zoom, search, and sidebar camera changes immediate until re-enabled.
- Prevented arrow traversal from sending repeated server selection requests,
  recentering the tree on every step, clearing unrelated pending feedback, or
  stealing Enter/arrow input from a focused compact-screen control.
- Replaced Branches' full cross-group edge scan with immutable incident-edge
  and membership indexes, keeping all-branch work bounded at the supported
  4,096-group and 65,536-edge limits.
- Prevented a rejected lazy Branches composition from poisoning either derived
  cache, and verified repeated topology/policy churn releases stale geometry.
- Made shared-atlas edge validation linear at the supported 65,536-edge limit,
  made exceptional-rank wrapping policy-configurable, and retain the last valid
  tree instead of silently replacing a rejected atlas with legacy geometry.
- Reserved safe portal banks even when every configurable margin is zero, and
  made canvas publication transactional if portal or spatial-index preparation
  fails.
- Invalidate obsolete release-candidate reports before artifact verification,
  write successful reports atomically, and verify layout-kernel wiring from
  compiled bytecode dependencies instead of source-text name matching.

## 1.2.0 - 2026-08-26

### Added

- Synchronized each disclosure-safe research graph together with its matching
  branch titles, kinds, icons, ranks, sibling order, and complete membership.
- Added lazy client-side Branches and curated All Weapons projections,
  disclosure-safe overview-boundary portals, and deterministic branch navigation.
- Added an edge-to-edge fullscreen tree with an overlay Weapon Trees rail,
  expandable search, compact zoom/help controls, an opaque contextual action card,
  and independent camera restoration for All Weapons and every branch.
- Added a prerequisite-driven unified overview layout with centered forks and
  merges, semantic Fit behavior, deterministic crossing reduction, and compact
  multi-row packing for explicitly included disconnected add-on components.
- Added adversarial coverage for empty and 4,096-node publications, maximum
  fallback/grouped atlases, mixed-rank disconnected components, overview
  membership reloads, content-pack fallback churn, and disconnect cleanup.

### Changed

- Advanced the network protocol to `17`; research graph, presentation, and
  server-resolved curated-overview metadata now validate and publish atomically
  under one generation, Research Bench previews contain only the active
  inventory-backed workflow, and selection/research responses are correlated.
- Made Branches the default Research Bench view and added global search-aware
  view/group controls plus a two-way fullscreen view action; selecting an
  included group focuses it in All Weapons while excluded groups open Branches.
- Reoriented progression bottom-to-top and replaced category lanes and stacked
  group chains with one prerequisite-driven All Weapons canvas.
- Rebalanced the complete 54-weapon TaCZ 1.1.8 default progression into one
  server-enforced weakest-to-strongest tree, rooted at Taurus 943, while retaining
  seven directly accessible role-aware branches.
- Hardened release certification to require JDK 17 and verify the packaged
  unified-tree runtime classes, protocol metadata, 33 default rules, exact
  54-weapon connected topology, and seven overview-included presentation groups.
- Updated the packaged mod description to explain the Research Bench,
  inventory-backed research, recycling, and datapack customization.

### Fixed

- Kept fullscreen pinned context synchronized with pointer, keyboard, search,
  and relationship navigation; stale or rejected selections now time out with
  persistent feedback instead of checking forever.
- Removed immediate double-click spending, blocked duplicate research requests,
  and retained correlated success or failure feedback in the selected context.
- Made Creative cost bypass render consistently across node state, RP balance,
  ingredients, action state, and narration.
- Prevented compact detail tooltips from covering Research or relationship
  controls, wrapped long requirement tooltips, and narrated exact materials.
- Stopped hidden prerequisites from contributing published counts, anonymous
  anchors, layout tiers, or tooltips; public counts now exactly match public
  prerequisite edges.
- Normalized authored, fallback, and Undisclosed ranks against the complete
  public graph and reject publications whose ranks contradict an edge.
- Bounded research-definition JSON before parsing, bounded group rank/member
  decoding while streaming the list, and reject synchronization totals as soon
  as accumulated chunks exceed their declaration.
- Prevented stale completed sync halves from regressing the client publication,
  and made point-only updates reuse a newer completed pending tree without
  discarding future generations.
- Rejected stale, conflicting, completed-duplicate, and cumulatively oversized
  player-progression chunks, and cleared partial progression state on logout.
- Cleared cached per-player tree publications when a server stops, preventing
  integrated-server sessions from retaining obsolete server-owned state.
- Removed the retired Prepare/Fill actions, menu mode, hidden ingredient/result
  slots, preview fields, and fill planner; research now has one inventory-backed
  transaction path from UI through server authority.
- Prevented fullscreen overlays and the selected-node card from rendering below
  graph nodes, labels, connectors, or item models.
- Prevented expanded fullscreen rail labels from overlapping the selected-node
  card or claiming pointer input through it at cramped window sizes.
- Prevented empty or replacement publications from retaining stale branch
  selections or crashing when entering fullscreen or switching tree views.
- Made projection publication failure-atomic so an unforeseen rejected add-on
  topology retains the last valid Research Bench tree without repeated retries.

### Compatibility

- Existing learned and discovered blueprint IDs, Research Points, datapack
  formats, and loot configuration remain compatible without a world migration.
- Protocol `16` requires matching `1.2.0` clients and servers for the new
  curated-overview publication metadata.
- Development and release verification remain pinned to TaCZ `1.1.8-hotfix`.

## 1.1.0 - 2026-08-25

### Added

- Blueprint Journal discovery, completion, filtering, and disclosure-aware policy presentation.
- Per-player Research Points with synchronized caps and durable persistence.
- Datapack-driven research profiles and deterministic per-blueprint rules.
- A non-ticking Research Bench for atomic physical-blueprint research and manual duplicate recycling.
- Overlap-safe point, explicit-item, and item-tag ingredient transactions.
- Operator progression inspection and explicit learned, discovered, points, or complete resets.

### Changed

- Replaced the original list-style Research Bench browser with a pannable,
  zoomable, searchable, keyboard-navigable research tree with responsive
  translucent fullscreen overlay, contextual node details, category focus,
  first-visit guidance, automatic inventory-backed research, and recycling.
- Advanced the network protocol to `13` for bounded Research Bench actions,
  atomic tree publication, per-player node state, and exact open-menu previews.
- Made all five undiscovered-visibility ceilings distinct in the Journal and
  Research Bench: hidden, anonymous silhouette, name-only, limited preview,
  and complete policy detail.
- Changed the packaged exact-tree rules to request full disclosure so the
  server visibility ceiling can select any of the five presentation tiers.
- Added a repeatable runtime-log gate and machine-readable release-candidate
  report containing dependency versions, test totals, artifact size, and SHA-256.

### Fixed

- Removed the manual Prepare/Fill step, made research consume exact materials
  directly from the player's inventory, and made Fit show the complete tree.
- Forced a complete tree publication when recycling also migrates legacy unlocks.
- Limited root, leaf, component, and independent-node diagnostics to the visible
  graph and delayed audit logging until the TaCZ catalog is initialized.
- Rejected stale or conflicting Journal chunks with the same fail-closed rules
  used by research-tree synchronization.
- Spatially indexed prerequisite edges so large authored graphs do not scan every
  edge on every rendered frame.
- Replaced real IDs on silhouette and name-only tree nodes with validated opaque
  publication keys and blocked server selection below preview visibility.
- Prevented release certification from accepting an older changelog version or
  nonempty Unreleased section.

### Compatibility

- Existing learned recipes migrate to durable learned/discovered blueprint output IDs.
- Research and recycling remain opt-in policy surfaces and never delete progression when disabled.

## 1.0.4 - 2026-08-24

### Changed

- Updated the development and runtime compatibility target to TaCZ 1.1.8-hotfix (`[1.1.8,1.2)`).
- Switched the pinned TaCZ development artifact from CurseMaven to the official Modrinth Maven endpoint.
- Let TaCZ own gunsmith result-button registration and ingredient-count null handling instead of replacing those paths with redundant client mixin injections.
- Resolved lazily created gun displays once per blueprint render while retaining the synchronized catalog texture fallback.

### Compatibility

- TaCZ 1.1.8-hotfix compiles successfully and passes the complete automated test suite.
- Dedicated-server and client startup probes load the blueprint menu and screen mixins without an injection error.
- Existing learned blueprint data, datapack formats, loot policies, and network formats are unchanged.
- Promotes the complete `1.0.3-beta7` feature and hardening set below to the stable release channel.

## 1.0.3-beta7 - 2026-08-24

### Added

- Authoritative TaCZ 1.1.5 blueprint catalog discovery for guns, ammunition, and attachments.
- Persistent, validated, server-authoritative learned-recipe progression.
- Server-side TaCZ crafting enforcement and bounded deterministic synchronization.
- Durable blueprint-output unlock identities with automatic duplicate-recipe alias migration.
- Byte-budgeted atomic synchronization chunks below Minecraft's custom-payload ceiling.
- Live configuration-aware loot selection and all three blacklist categories.
- Versioned, reloadable blueprint tags, loot pools, and loot rules.
- Pool inheritance, catalog selectors, loot-table selectors, and dimension/luck predicates.
- Atomic last-known-good catalog and loot snapshot publication.
- Operator status, inspection, pool, and analytical preview commands.
- Exact effective weights, per-roll probabilities, and expected-addition reporting.
- Automated tests and packaged-release verification.

### Changed

- Replaced TaCZ recipe-ID path guessing with result-item API resolution.
- Replaced global client/server catalog state with isolated authoritative and presentation catalogs.
- Replaced reflection-driven loot resource discovery with strict deterministic loading.
- Made generated legacy loot modifiers a table-selective compatibility fallback behind dynamic rules.
- Limited blueprint additions to one shared 64-item budget per loot event.
- Tightened declared compatibility to TaCZ `[1.1.5,1.2)` and Fzzy Config `[0.5.9,0.6)`.
- Tightened Minecraft to `[1.20.1,1.20.2)` and Forge/FML to `[47,48)`.
- Made archive ordering and timestamps reproducible.

### Fixed

- Content-pack namespaces being incorrectly treated as Forge mod IDs.
- Blueprint consumption on duplicate or invalid unlocks.
- Lost unlocks during player cloning, including return from the End.
- Client catalog synchronization overwriting integrated-server authority.
- Stale learned IDs exhausting or polluting active synchronization.
- Malformed optional datapack fields silently applying defaults.
- Partial reload publication, inheritance cycles, unsafe weights, and unbounded definitions.
- Multiple overlapping modifiers independently exceeding the per-event blueprint limit.
- Optional structure dependencies breaking the normal dedicated-server development runtime.
- Canonical duplicate recipes invalidating previously learned aliases.
- Maximum-count synchronization payloads exceeding Minecraft's byte limit.
- Selector inheritance underflow escaping reload validation.
- Removed content-pack blueprints rendering invisibly and spamming client logs.
- Open gun-smithing screens retaining stale unlock/configuration state.
- Translation overrides being interpreted as unsafe Java format strings.
- Targeted disabled loot rules being reported as a global datapack opt-out.
- Removed 69 orphaned legacy modifier files that were not referenced by the
  global loot-modifier index and therefore could never execute.

### Compatibility

- Existing valid player `Recipes` NBT remains readable and is migrated into durable `Blueprints` state.
- Format-1 loot pools and rules remain supported.
- The 485 generated legacy modifiers remain packaged for incremental migration and rollback.
- Existing six-tier weights and authored table overlaps are unchanged.
