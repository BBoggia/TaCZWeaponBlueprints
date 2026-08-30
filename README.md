# TaCZ Weapon Blueprints

TaCZ Weapon Blueprints adds persistent blueprint progression to Timeless and Classics Zero. Players find blueprints in configured loot, use them to learn TaCZ gun-smithing recipes, and retain those unlocks across death, dimension changes, logout, and content-pack reloads.

The mod is server-authoritative: the client UI hides locked recipes for convenience, while the server independently rejects attempts to craft recipes the player has not learned.

## Requirements

- Minecraft 1.20.1
- Forge 47.x (validated with 47.3.0)
- Timeless and Classics Zero 1.1.8-hotfix (`[1.1.8,1.2)`)
- Fzzy Config 0.5.9 (`[0.5.9,0.6)`)
- Kotlin for Forge 4.11.x, required by Fzzy Config

JEI 15.x and EMI 1.1.x are optional client-side integrations. Neither is
needed on a server or required to use the mod.

Install the same mod and dependency versions on both client and server. TaCZ content packs may be added or removed independently; the blueprint catalog is rebuilt from the recipes currently available on the server.

## Core behavior

- Blueprint items persist unlocks by TaCZ output ID and expose one deterministic canonical recipe per output.
- Legacy exact-recipe unlocks migrate through current duplicate aliases without discarding rollback-compatible data.
- Player and catalog state are synchronized with byte-budgeted, atomic chunks.
- Crafting enforcement occurs in the server-side TaCZ gun-smithing menu.
- Blueprint loot uses live chance, roll-range, and blacklist configuration.
- Datapacks can replace or extend loot pools and rules without rebuilding the mod.
- Format-2 datapacks support tags, pool inheritance, catalog selectors, loot-table selectors, dimensions, and luck predicates.
- Catalog and loot reloads publish complete immutable snapshots; failed rebuilds preserve the previous working state.
- The Blueprint Journal presents disclosure-filtered discovery, completion, research, and recycling policy.
- A first-visit Getting Started page in the Journal gives fresh players a short
  progression path and live, server-filtered ways to earn RP. It can always be
  reopened with the Journal's `?` button after dismissal.
- Optional JEI and EMI support adds generic information pages for the Research
  Bench, Blueprint Analyzer, blueprints, and Research Data. It deliberately
  provides no recipe transfer and never lists hidden research targets.
- The Research Bench opens directly into an edge-to-edge Research Tree and
  performs atomic, server-authoritative research from the player's inventory.
  Its packaged mode learns the recipe immediately; servers can temporarily
  retain the legacy physical-blueprint result through synchronized config.
- The dedicated Blueprint Analyzer handles physical TaCZ item reverse
  engineering, voluntary duplicate recycling, and configured Research Data
  redemption. It owns one input plus an extract-only blueprint output, shows
  the server-resolved item/RP/material cost, and never sacrifices equipment
  until the player explicitly confirms the action.
  It has a datapack-replaceable survival recipe, appears in Functional Blocks,
  and enters the recipe book when a player obtains a blueprint, Research Bench,
  or Research Data. Its directional worn-steel model, paper intake, output
  drawer, and fitted collision shape distinguish it from the Research Bench.
- Research profiles and rules can configure point costs, item/tag ingredients,
  prerequisites, visibility, tree inclusion, ordered entry-point fallbacks, and
  recycling values. Format-2 profiles can also apply final per-domain tree and
  research gates for Weapons, Attachments, and Ammo.
- Servers can exempt exact blueprints, complete gun/ammo/attachment categories,
  or TaCZ item subgroups from progression. Exempt recipes remain server-checked
  live access rather than fake learned records. An exact starting-blueprint
  list can also teach durable knowledge idempotently on login, reload, or a
  synchronized config update without replaying Research Point awards.
- Research Bench tree topology is derived directly from those prerequisites, with hidden policies remaining undisclosed.
- The built-in TaCZ 1.1.8 progression covers all 53 recipe-backed default weapons once in one server-enforced, weakest-to-strongest tree with seven directly accessible class branches. Glock 17 is preferred as the shared entry, with ordered pistol fallbacks if its recipe is unavailable.
- All 95 recipe-backed default attachments and 24 ammunition types retain
  tier-scaled authored rules, placements, and catalog-aware entry candidates,
  but the built-in profile disables their Tech Tree publication and Research
  Bench research by default. Their physical-blueprint and reverse-engineering
  routes remain available. Datapack profiles can opt either domain back in.
- Branches and All Weapons views support mouse pan/zoom, an overlaid tree
  sidebar, cross-branch portals, relationship-first arrow-key browsing with
  explicit Enter selection, and keyboard-selectable search results.
- Players can track one revealed blueprint as a session goal. Its complete
  prerequisite route remains highlighted across Research Bench views, `Next`
  stays on that route, and the tooltip summarizes remaining published RP and
  material needs without revealing server-hidden policy details.
- Branch and portal lookup uses bounded per-group indexes at the published 4,096-node and 65,536-edge limits.

## Configuration

The synchronized Fzzy Config screen exposes:

- global blueprint enable/disable;
- default blueprint loot chance;
- default minimum and maximum rolls, bounded to 64;
- a reversible Custom/Accessible/Balanced/Scarce discovery-pacing preset;
- gun, ammo, and attachment blacklists.
- Journal, discovery tracking, research, the direct/physical tree result, and
  manual recycling enablement;
- undiscovered visibility, Research Point cap, Creative cost bypass, and active research profile.
- progression-exempt exact IDs, coarse categories, and TaCZ item subgroups;
  plus an additive exact list of starting blueprints.

A separate client-only **Research Tree Settings** config provides Reduce Motion,
an optional background grid, the guarded hold shortcut, and bounded advanced
spacing, wrapping, crossing-reduction, and compaction controls. The cleaner
canvas defaults to no grid; display changes apply to an open Research Bench,
and each player may choose different settings without changing server
progression or datapacks.

Datapack rules may override chance and rolls for individual loot policies. Rules that omit those fields continue using the live global defaults.
Named balance presets override only the global undiscovered-visibility and
default loot values. They never rewrite the custom fields, datapack research
costs or prerequisites, starter grants, exemptions, blacklists, or player
knowledge. Switching back to **Custom** restores the hand-tuned values.

## Operator commands

All `/gg` commands require permission level 2.

| Command | Purpose |
| --- | --- |
| `/gg clearRecipes` | Clear the invoking player's learned blueprint recipes. |
| `/gg reloadRecipes` | Rebuild the authoritative catalog and synchronize players. |
| `/gg loot status` | Show snapshot, catalog, configuration, and distribution-mode status. |
| `/gg loot inspect <loot_table>` | Explain dynamic ownership, targeting, predicates, and candidates. |
| `/gg loot pool <pool_id>` | Inspect a prepared pool and its current catalog candidates. |
| `/gg loot preview <loot_table>` | Show effective chance, rolls, weights, probabilities, and expected additions. |
| `/gg progression inspect <player>` | Inspect a player's durable blueprint progression counts and Research Points. |
| `/gg progression reset <targets> <learned\|discovered\|points\|awards\|all>` | Explicitly reset one progression state while preserving invariants. `awards` clears only RP claim/rate history. |
| `/gg progression points give <targets> <amount>` | Give RP without exceeding the live server Research Point cap. |
| `/gg research status` | Audit the active research profile and presentation groups against the live TaCZ catalog. |
| `/gg research setup assess` | Assess effective discovery workload, add-on coverage, runtime readiness, research structure, starters, and exemptions, then recommend a discovery preset. |
| `/gg research setup preview <preset>` | Preview a preset without changing server state. |
| `/gg research setup apply <preset> confirm` | Persist and synchronize one explicitly confirmed preset selection. |
| `/gg research setup export` | Export the deterministic setup assessment inside the current world folder. |
| `/gg research awards status` | Inspect the immutable RP award publication, trigger counts, and last rejected reload. |
| `/gg research awards inspect <definition_id>` | Inspect one RP award's trigger, group, reward, repeat policy, profile scope, and budget. |
| `/gg research inspect <blueprint_id>` | Inspect the selected rule, visibility, cost, prerequisites, and authored placement. |
| `/gg research export` | Export a sorted format-12 authoring catalog with topology, resolved-width evidence, planned/published ranks, per-weapon decisions, and economy review inside the current world folder. |

Use vanilla `/reload` after changing blueprint loot datapacks. A successful reload advances the revision reported by `/gg loot status`; an invalid reload leaves the last-known-good revision active.

The current client/server network protocol is `36`; matching mod versions are
required on both sides. Protocol 36 publishes each disclosure-safe research
graph, its weapon-only legacy group subset, optional identity-safe Tech Tree
metadata (including rank, long sibling order, optional visual-band references,
canonical automatic branch coordinates, and a bounded ordered custom-band
label table), including the opt-in 21–28 layout-capacity envelope, and
the server-resolved curated-overview flags atomically. It also
synchronizes each catalog entry's coarse gun/ammo/attachment kind, transfers
research-only, live-inventory Research Bench previews, correlates
selection and research results with their requests, sends the Analyzer's
bounded contextual preview and opaque state token, sends bounded server-filtered
RP earning help and short award feedback, and rejects stale or conflicting
progression chunks. Analyzer previews also carry the authoritative learned
state needed to present allowed physical blueprint copies without inferring
player knowledge on the client. It does not change persisted player
progression or require a world migration.

## Datapack resources

Definitions use these locations:

```text
data/<namespace>/taczweaponblueprints/blueprint_tags/<path>.json
data/<namespace>/taczweaponblueprints/loot_pools/<path>.json
data/<namespace>/taczweaponblueprints/loot_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_rules/<path>.json
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
data/<namespace>/taczweaponblueprints/research_tech_trees/<path>.json
data/<namespace>/taczweaponblueprints/research_tech_tree_entries/<path>.json
data/<namespace>/taczweaponblueprints/research_automatic_placement_profiles/<path>.json
data/<namespace>/taczweaponblueprints/research_point_awards/<path>.json
```

Format 1 provides exact weighted pools and exact loot-table rules. Format 2 adds reusable composition, current-catalog selection, table-family selection, and runtime predicates. Research profiles provide defaults while deterministic exact, tag, namespace, category, and catalog-selector rules provide per-blueprint overrides. Separate research-tree group resources author presentation without changing progression. See the [research-tree authoring guide](docs/research-tree-authoring.md), [grouped-navigation contract](docs/development/research-tree-navigation-phase-0.md), [Phase 1 group-data implementation](docs/development/research-tree-navigation-phase-1.md), [Phase 2 publication boundary](docs/development/research-tree-navigation-phase-2.md), [Phase 3 synchronization](docs/development/research-tree-navigation-phase-3.md), [Phase 5 navigation and layout](docs/development/research-tree-navigation-phase-5.md), [Phase 6 default progression](docs/development/research-tree-navigation-phase-6.md), [Phase 7 adversarial hardening](docs/development/research-tree-navigation-phase-7.md), [Phase 8 release preparation](docs/development/research-tree-navigation-phase-8.md), [unified-overview Phase 6](docs/development/research-tree-unified-overview-phase-6.md), [unified-overview Phase 7](docs/development/research-tree-unified-overview-phase-7.md), [unified-overview Phase 8 validation](docs/development/research-tree-unified-overview-phase-8.md), [fullscreen Phase 8 validation](docs/development/research-tree-fullscreen-phase-8.md), [Journal/research Phase 8](docs/development/journal-research-phase-8.md), and [operations and migration](docs/operations-and-migration.md).

Research Point awards use a separate strict format-1 publication. Definitions
select one typed server event, a bounded target, fixed RP, repeat behavior,
optional shared rolling budget, active-profile scope, and disclosure-safe
presentation. Within an award group the most specific target wins, followed by
priority and definition ID; different groups intentionally stack. Advancement,
first-discovery, first-learning, filtered milestone, and opt-in combat sources are live and
use atomic repeat/budget accounting plus bounded streaming opt-in retroactive
catch-up that revalidates its source state and parks cap-limited finite claims
until the player's RP balance decreases. Combat awards record spawn provenance,
distinguish direct, TaCZ/vanilla projectile, indirect, pet, fake-player, and PvP
credit, reject farmable sources by default, and remain disabled by the separate
server kill switch.
Physical Research Notes, Reports, and Dossiers are now registered and can be
explicitly redeemed through the dedicated Blueprint Recycler. The built-in
economy values them at 1, 3, and 6 RP and places them
at conservative 12%, 8%, and 5% chances in a small set of matching exploration
chests; both the award definitions and Forge loot modifiers remain replaceable
by datapacks.
Redemption is never passive, does not trust item NBT or a client-supplied value,
and consumes a real stack in Creative mode only after a full award commits.
The default pack also grants 1 RP for each first blueprint discovery, 18 finite
RP across discovery/research milestones, and 28 finite RP from six vanilla
progression advancements. Those finite rewards wait and retry when their full
value cannot fit under the RP cap, rather than consuming a claim for a partial
payout. Combat income remains opt-in and disabled by default.
The Research Tree offers disclosure-filtered earning help, and committed
rewards use bounded,
aggregation-aware feedback that players may hide with a client-only setting.
Optional server mods can register stable integration event IDs through
`ResearchPointAwards`, while permission-level-2 command functions can invoke
the same datapack-authored `integration` triggers with
`/gg research awards trigger <targets> <source>`. Cancellable pre-award and
immutable committed post-award Forge events wrap the shared transaction without
allowing listeners to replace datapack-authored point values.
See the
[award contract](docs/research-point-awards-phase-0.md),
[accounting foundation](docs/research-point-awards-phase-1.md), and
[datapack publication](docs/research-point-awards-phase-2.md),
[safe finite sources](docs/research-point-awards-phase-3.md), and
[combat sources](docs/research-point-awards-phase-4.md), and
[Research Data redemption](docs/research-point-awards-phase-5.md), and
[player feedback and help](docs/research-point-awards-phase-6.md), and
[server integrations](docs/research-point-awards-phase-7.md), and
[packaged economy and release hardening](docs/research-point-awards-phase-8.md).

The optional Rust-style Research Tech Tree implementation is documented from
its [Phase 0 contract](docs/development/research-tech-tree-phase-0.md) through
the [Phase 7 live browse integration](docs/development/research-tech-tree-phase-7.md).
The subsequent unified-domain correction begins with its
[revised Phase 0 contract](docs/research-tech-tree-unified-phase-0.md) and
[Phase 1 authoritative graph boundary](docs/research-tech-tree-unified-phase-1.md).
[Phase 2](docs/research-tech-tree-unified-phase-2.md) enables the complete
attachment and ammunition research authority, and
[Phase 3](docs/research-tech-tree-unified-phase-3.md) connects all three
domains into independently reachable, tier-monotonic progression trees, and
[Phase 4](docs/research-tech-tree-unified-phase-4.md) provides stable typed
navigation metadata for genuine cross-domain prerequisites without creating
new locks or changing the then-current protocol. [Phase 5](docs/research-tech-tree-unified-phase-5.md)
replaces visible lane columns with one prerequisite-centered, six-tier canvas
for each of Weapons, Attachments, and Ammunition. [Phase 6](docs/research-tech-tree-unified-phase-6.md)
exposes those three mixed trees as direct compact selectors and named
fullscreen-rail destinations while preserving per-domain navigation state.
[Phase 7](docs/research-tech-tree-unified-phase-7.md) hardens both surfaces
behind one disclosure-safe stable-slot menu with keyboard traversal, narration,
reload fallback, and adversarial regression coverage. [Phase 8](docs/research-tech-tree-unified-phase-8.md)
certifies the reobfuscated JAR's exact 53/95/24 domain topology, roots,
fallback routing, localization, compatibility boundary, and release evidence.
The first guided-progression slice adds a compact `Next` action that focuses a
deterministically recommended available blueprint while leaving selection and
all research authority with the server. Its behavior and extension boundary
are recorded in the [guided progression note](docs/research-tree-guided-progression.md).
The automatic add-on distribution work begins with
[Phase 0](docs/research-automatic-placement-phase-0.md), which
freezes placement and compatibility semantics, while
[Phase 1](docs/research-automatic-placement-phase-1.md) implements
the loader-independent mechanical evidence and scoring core, and
[Phase 2](docs/research-automatic-placement-phase-2.md) adds the bounded TaCZ
server adapter, pinned default comparison catalog, and atomic diagnostic
snapshot, while [Phase 3](docs/research-automatic-placement-phase-3.md) turns
add-on evidence into versioned, deterministic three-level-per-tier proposals
with explicit review signals and stable mixed sibling slots. [Phase 4](docs/research-automatic-placement-phase-4.md)
adds the strict per-tree activation profile, genuine-fallback classifier, and
atomic revision-coupled eligibility publication. [Phase 5](docs/research-automatic-placement-phase-5.md)
projects eligible positions through the server presentation, protocol 20, and
client layout without creating prerequisite edges. [Phase 6](docs/research-automatic-placement-phase-6.md)
activates the explicit `connected` mode: eligible add-on weapons may gain a
bounded deterministic set of earlier-row prerequisites, with selective gateway
and periodic merges enforced consistently by the server transaction, Journal,
and tree graph. Authored prerequisites and fail-open compatibility remain
protected. [Phase 7](docs/research-automatic-placement-phase-7.md)
adds revision-safe `/gg research` status and inspection evidence plus a
deterministic format-3 authoring export, without changing gameplay authority.
[Phase 8](docs/research-automatic-placement-phase-8.md) closes the sequence
with a consistent 4,096-weapon boundary, packaged safe-default and diagnostic
contracts, JDK 17 release metadata, and an explicit hands-on QA boundary. The
[generation redesign](docs/research-tree-generation-redesign-phase-5.md) then
adds append-stable dynamic ranks, bounded width, a connected lower mesh that branches
into later specializations, and optional custom presentation bands while
preserving format-1 automatic profiles. [Phase 6](docs/research-tree-generation-redesign-phase-6.md)
moves bounded node width onto tree format 2, and
[Phase 7](docs/research-tree-generation-redesign-phase-7.md) makes visible bands
tree-owned and independently configurable as none, dynamic, or configured.
[Phase 8](docs/research-tree-generation-redesign-phase-8.md) refactors the
client into bounded rank rows with branch-aware crossing reduction, on-demand
portal clearance, responsive capacity caching, and explicit disconnected
component diagnostics. [Phase 9](docs/research-tree-generation-redesign-phase-9.md)
adds topology and parent-retention evidence, per-weapon decision explanations,
and a policy-owned economy review without introducing rank-derived costs.
[Phase 10](docs/research-tree-generation-redesign-phase-10.md) completes the
default-data rollout: the authoritative 53-weapon bundle now uses compact
explicit format-2 ranks, dormant attachment/ammo data remains available for
opt-in profiles, release gates no longer freeze legacy tier assumptions, and
versioned topology fingerprints guard deliberate graph changes. The subsequent
[dynamic-width contract](docs/research-tree-dynamic-width.md)
lets a format-2 tree resolve its semantic layer capacity from the complete
authored-plus-eligible weapon population within a configured 8–28 range. The
built-in tree uses a landscape-biased 9–20 range while fixed-width datapacks
remain compatible, and manual zoom can reach 15% for very large trees.
The tapered branch redesign is recorded in
[Phase 0](docs/research-tree-tapered-branches-phase-0.md),
[Phase 1](docs/research-tree-tapered-branches-phase-1.md),
[Phase 2](docs/research-tree-tapered-branches-phase-2.md),
[Phase 3](docs/research-tree-tapered-branches-phase-3.md), and
[Phase 4](docs/research-tree-tapered-branches-phase-4.md). Phase 4 activates
the dense shared-trunk and gradual stat-family rank envelopes, while
[Phase 5](docs/research-tree-tapered-branches-phase-5.md) makes generated
requirements follow those families: cross-family interconnection remains dense
at the bottom, tapers gradually through the transition, and becomes branch-local
near the one-to-three-node apex cohorts. [Phase 6](docs/research-tree-tapered-branches-phase-6.md)
adds exact branch-decision diagnostics and graph-evidence-driven family gutters
without changing prerequisite authority or the network protocol.
[Phase 7](docs/research-tree-tapered-branches-phase-7.md) resolves mechanically
equivalent one-to-three-weapon terminal clusters, and
[Phase 8](docs/research-tree-tapered-branches-phase-8.md) replaces the final hard
parent cutoff with a deterministic 100%–20% maturity curve plus an RP-closure
guard for optional merges. [Phase 9](docs/research-tree-tapered-branches-phase-9.md)
hardens publication and adds an explicit end-to-end completeness summary for
canonical branch coordinates, prerequisite decisions, and finalized ranks.
[Phase 10](docs/research-tree-tapered-branches-phase-10.md) makes that summary
an atomic production invariant and adds a versioned packaged release gate.
[Phase 12](docs/research-tree-tapered-branches-phase-12.md) adds staged failure
diagnostics and revision-safe recovery states for post-rollout operation.

## Building

Use JDK 17:

```text
./gradlew cleanTest build
./gradlew certifyReleaseCandidate
```

`certifyReleaseCandidate` runs the publication and packaged-artifact gates and
writes `build/reports/release-candidate.json` with the exact dependency
versions, build JVM, network protocol, unified-tree and automatic-placement
contracts, setup-assistant safety boundary, test totals, artifact size, and
SHA-256.
The build also prevents live Research Tree code from falling back to its
compatibility-only layout engines and verifies that the complete shared layout
kernel and client configuration are present in the reobfuscated JAR.

Normal builds do not resolve optional structure mods. Structure-aware legacy data regeneration is opt-in:

```text
./gradlew runData -PincludeStructureDataMods=true
```

Run structure-aware generation only in a clean or isolated copy and review the generated-resource diff before accepting it.

Packet Fixer is excluded from the normal client and server development runtime so
minimum-dependency smoke tests are representative. It can be enabled only for an
explicit compatibility run with `-PincludePacketFixer=true`.

Captured client and server startup logs can be checked independently:

```text
./gradlew verifyRuntimeSmokeLog -PsmokeKind=client -PsmokeLog=run/logs/client.log.gz
./gradlew verifyRuntimeSmokeLog -PsmokeKind=server -PsmokeLog=run/logs/server.log
```

These checks require complete logs from startup through the main menu or
dedicated-server `Done` marker. They reject missing lifecycle markers and known
mod-local classloading, mixin, and initialization failures without treating a
third-party content-pack warning as a failure of this mod.

The repository's complete release procedure is recorded in the
[release checklist](docs/release-checklist.md), with the hands-on tree matrix in
[research-tree manual QA](docs/research-tree-manual-qa.md).

The release artifact is written to `build/libs/taczweaponblueprints-<version>.jar`.

## Development history

The staged recovery and redesign are recorded under [docs/development](docs/development), from the preserved Phase 0 baseline through the final Phase 8 release certification.
The current knowledge-flow sequence concludes with
[Phase 8 presets and setup assistant](docs/blueprint-knowledge-flow-phase-8.md).
The current tree-generation redesign is documented in its
[Phase 0 compatibility baseline](docs/research-tree-generation-redesign-phase-0.md),
[Phase 1 domain defaults](docs/research-tree-generation-redesign-phase-1.md), and
[Phase 2 rank migration](docs/research-tree-generation-redesign-phase-2.md), and
[Phase 3 publication and layout migration](docs/research-tree-generation-redesign-phase-3.md),
through the [Phase 8 bounded client layout](docs/research-tree-generation-redesign-phase-8.md)
and [Phase 9 diagnostics and economy review](docs/research-tree-generation-redesign-phase-9.md),
then concludes with the
[Phase 10 default-data rollout](docs/research-tree-generation-redesign-phase-10.md).
