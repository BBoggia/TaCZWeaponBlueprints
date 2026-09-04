# Research Bench and crafting Workbench tiers

Research and crafting use separate tiered workstation families. Research
Benches open the Tech Tree, while crafting Workbenches open TaCZ's native
crafting screen. The server checks the physical station before allowing either
action.

This document defines the server, datapack, and compatibility rules for tiered
research and crafting. Blueprint Fragment behavior is covered in the
[Blueprint Fragment supply guide](blueprint-fragment-supply.md), and reusable
event requirements are covered in the
[Progression Gate criterion API](progression-gate-api.md).

## Progression presets

`progressionPreset` selects the normal server behavior:

| Preset | Research tiers | Crafting tiers |
| --- | --- | --- |
| `CLASSIC` | Not enforced | Not enforced |
| `TIERED_RESEARCH` | Enforced | Not enforced |
| `TIERED_RESEARCH_AND_CRAFTING` | Enforced | Enforced |
| `CUSTOM` | Uses the two advanced switches | Uses the two advanced switches |

The default is `TIERED_RESEARCH_AND_CRAFTING`. Servers upgraded from a version-2
configuration migrate to `CLASSIC` with Blueprint Fragments disabled. This
keeps an existing world's access rules unchanged until its operator chooses a
tiered preset.

The `enableResearch` switch disables Research Bench learning without disabling
the separate Workbench crafting policy. The progression preset and active
profile therefore remain configurable while research is off. Blueprint
Fragments also remain independently configurable because reconstruction mode
can still produce learnable blueprints; servers that do not want fragment
replacement should select the disabled fragment preset or set its replacement
percentage to zero.

Creative mode can bypass the ordinary workstation tier when
`creativeBypassesWorkbenchTiers` is enabled. Progression Gates use their own
Creative bypass setting, so enabling one bypass does not silently enable the
other.

## Crafting access settings

The main settings screen provides two category strategies without requiring a
datapack:

- `ammoCraftingStrategy` can keep the active research profile's behavior,
  match the earliest tiered compatible gun, require a fixed Workbench level,
  ignore ordinary Workbench levels, or disable Workbench crafting.
- `attachmentCraftingStrategy` can keep the active research profile's
  behavior, require a fixed Workbench level, ignore ordinary Workbench levels,
  or disable Workbench crafting.

Both settings default to `PROFILE`, which preserves the loaded profile's
resolved crafting behavior. These strategies change crafting access only;
they do not add ammo or attachments to the Tech Tree.

Advanced settings provide no-level and disabled selectors for whole blueprint
categories and loaded TaCZ item subgroups. Exact Crafting Overrides accept a
loaded blueprint name or resource ID and assign its final Level 1, Level 2,
Level 3, no-level, or disabled result. Valid resource IDs from a temporarily
unavailable content pack remain stored, and loaded IDs and item subgroups use
searchable or validated controls.

Configuration precedence is deliberate:

1. an exact crafting override;
2. a disabled category or subgroup selector;
3. a no-level category or subgroup selector;
4. the ammo or attachment category strategy; and
5. the active research profile and its crafting rules.

An exact override can therefore deliberately re-enable an entry covered by a
broader disabled selector. When a category and subgroup match both broad
lists, disabled wins. `linkedAmmoFallbackTier` is used only by the
`LINKED_WEAPON` ammo strategy and applies when no compatible tiered gun can
supply a level.

Blueprint-free selectors remain a separate knowledge rule. They remove the
learned-blueprint requirement but do not bypass a resolved Workbench level or
crafting-scoped Progression Gates. Conversely, a no-level crafting selector
removes only the ordinary Workbench-level requirement; knowledge and gates
still apply.

## The two workstation families

- `taczweaponblueprints:research_bench` is Research Tier 1.
- `taczweaponblueprints:advanced_research_bench` is Research Tier 2.
- `taczweaponblueprints:experimental_research_bench` is Research Tier 3.

TaCZ crafting uses these blocks:

- `taczweaponblueprints:workbench_lvl1` is Crafting Tier 1.
- `taczweaponblueprints:workbench_lvl2` is Crafting Tier 2.
- `taczweaponblueprints:workbench_lvl3` is Crafting Tier 3.

A higher tier satisfies lower-tier requirements within the same family. Every
station is a two-block structure, and either half opens the correct screen.
There is no Research/Craft mode switch: the block you use determines the
action. The server rechecks the dimension, distance, root position, matching
extension, registry ID, tier, interaction type, and menu session before
accepting an action.

Both families provide independent recipes for all three levels. A player can
therefore build a higher-tier station directly without first crafting a lower
tier. Pack authors who prefer cumulative workstation progression can replace
these recipes through a datapack and require the preceding station as an
ingredient.

## Assigning weapon tiers

Research profile format 3 retains the established combined fallback-tier syntax
for existing datapacks. The server projects those values into separate runtime
research and crafting policies. Profile format 4 adds a distinct crafting
policy so research inclusion and crafting access no longer need to share one
fallback. A matching format-4 rule can independently set a tiered,
unrestricted, or disabled crafting disposition and crafting-scoped Progression
Gates.

Research and crafting select applicable rules independently. Exact-ID rules
take precedence over tag and selector rules within each action, followed by the
applicable profile or category default. A more specific crafting-only rule
therefore cannot hide a broader research rule, and a research-only rule cannot
hide a broader crafting rule.

For an automatic weapon tree, trusted mechanical scores are divided by the
configured percentile boundaries. The defaults assign the lower 35 percent to
Tier 1, the next 40 percent to Tier 2, and the remaining 25 percent to Tier 3.
Equal scores remain in the same tier even when they cross a percentile
boundary. Weapons that require review use the profile fallback instead of
pretending an uncertain score is authoritative.

Recipe-backed ammo uses TaCZ's canonical gun data rather than names or file
paths. Shared ammo takes the lowest crafting level among its compatible tiered
guns so an early weapon is never left without craftable ammunition.
`unrestricted` and `disabled` gun policies do not lower linked ammo. Ammo with
no tiered compatible gun, no recipe-backed association, or ambiguous source
data uses the profile's explicit ammo fallback and is reported in diagnostics;
an exact, tag, or selector crafting rule can deliberately override the result.

Only weapons in the effective Tech Tree receive the tree's research policy.
Format-4 crafting policy still assigns an explicit result to catalog entries
outside that research tree. Authored omissions use only
`authored_omitted_guns`; automatic score evidence is ignored unless that field
deliberately selects `automatic_tier`. Disabled domains, hidden presentation,
and already learned outliers do not rewrite another weapon's tier assignment.
An omitted gun whose automatic evidence requires review uses the configured
fallback instead of receiving an authoritative percentile tier.

See [Research Tree authoring](research-tree-authoring.md) for the full format-4
schema, legal category strategies, compatibility defaults, and conflict rules.

Use `/gg research inspect <blueprint_id>` to see the independent research and
crafting results, including the crafting disposition, required Workbench level,
assignment source, selected rule, reason, warnings, fragment policy, and
Progression Gate counts. `/gg research export` writes the same information for
every loaded catalog entry and confirms whether crafting coverage is complete.

## Native and external crafting tables

Crafting Workbenches use TaCZ's Gun Smith Table menu and recipe implementation.
Before a recipe is shown or crafted, the server requires canonical learned or
exempt knowledge, the current physical workstation, the resolved crafting
tier, and every crafting-scoped Progression Gate.

JEI and EMI provide help pages and blueprint information only. They do not
pretend that a context-free recipe page has an active, server-verified
Workbench. Use the recipe list inside a placed Workbench for current access;
the Blueprint Journal and selected Tech Tree details show the disclosed
Workbench requirement for a specific blueprint.

Native crafting Workbench tiers cannot be overridden by compatibility mappings.
For other TaCZ-compatible workstations, resolution order is:

1. an exact entry in `externalWorkstationTiers`;
2. unrestricted external access when
   `unknownExternalWorkstationsUnrestricted` is enabled; or
3. `unknownWorkstationFallbackTier`.

Unrestricted external access bypasses the ordinary crafting-tier band, but it
does not bypass Progression Gates. An external block must still use TaCZ's
native Gun Smith Table block entity so the server can authenticate the
workstation that opened the menu.

The mod suppresses only the ordinary recipe for `tacz:gun_smith_table`.
Players build the three crafting Workbench levels instead. The original TaCZ
block, item, menu, assets, and existing placed tables remain available. A
higher-priority datapack can deliberately restore or replace that recipe.

## Reload and multiplayer behavior

Research, crafting, fragment completion, and Progression Gate checks use
separate action-specific policy projections built from one revision-matched
publication. A valid `/reload` replaces both projections as one unit. Crafting
eligibility reads the complete crafting projection and does not require a
blueprint to have a Tech Tree research assignment. Invalid data keeps the last
complete publication, while a catalog or policy mismatch makes the affected
action unavailable until the next successful rebuild.

Changing a crafting assignment setting rebuilds and republishes the complete
server-owned policy before open Workbenches receive a refreshed allow-list.
Client configuration synchronization can update labels and local snapshots,
but it cannot rebuild or replace server crafting authority.

Minecraft processes the accepted actions on the server thread. A second action
prepared from the same old inventory, RP, fragment, criterion, or Bench state
is rejected as stale rather than consuming resources twice. Logout clears
deferred player publications, and server shutdown clears request admission,
route memoization, failure reporting, and derived automatic-placement state.

Complete the tier, direct-recipe, external-workstation, concurrency, reload, and
dedicated-server cases in the
[Research Tree manual QA matrix](research-tree-manual-qa.md) before publishing a
release candidate.
