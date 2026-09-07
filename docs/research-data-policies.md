# Research Data policies and shared reconstruction

Pack authors can define Research Data target pools and fragment/blueprint outcome tables, and configure shared analysis-fragment reconstruction. The Blueprint Analyzer can process one Data item into one physical blueprint or a stack of same-target fragments. Analysis is the default, with shipped Note, Report and Dossier policies. Explicit `LEGACY_CURRENCY` and `DISABLED` settings take precedence over loaded analysis definitions. See [economy balance](research-economy-balance.md) for the default pools and odds.

Shared reconstruction settings already apply to owned analysis fragments and to the reconstruction threshold shown for partial weapon-analysis outcomes. Existing packs without these definitions retain their Analyzer reconstruction settings. Player save version 5 remains unchanged. The new Analyzer action requires matching protocol-62 clients and servers.

## Start from the shipped definitions

For Minecraft 1.20.1, create a datapack with `pack_format: 15` in `pack.mcmeta`.
Place it in the world's `datapacks` directory with that file at the archive root.
Copy the existing file into your higher-priority datapack to replace it:

| Definition | Path beneath `data/taczweaponblueprints/taczweaponblueprints/` |
| --- | --- |
| Research Note | `research_data/note.json` |
| Research Report | `research_data/report.json` |
| Research Dossier | `research_data/dossier.json` |
| Shared fallback | `analysis_reconstruction/research_data.json` |

These source files are under `src/main/resources/`. A replacement is the whole
JSON document, so retain fields you still need. A new Data definition in your
own namespace can instead use a higher `priority` to win for an input. A new
shared fallback in another namespace conflicts with the bundled fallback;
replace the existing fallback ID or add a target/profile-scoped definition.

Common edits:

- Set `allow_learned: false` to override the global duplicate setting for one
  item. All-learned pools stop before input consumption; owned fragments still
  reconstruct.
- Change `pool.tier_weights` to restrict or favor weapon tiers. Set Report
  weights to `tier_1: 3, tier_2: 1` for a 75%/25% split when both tiers are usable.
  Weapon weights act only within a selected tier.
- Change each station's `rows` to set blueprint weights and fragment ranges.
  All three station rows are required even when `minimum_analyzer_tier` blocks
  lower stations. Data never supports a paid no-result outcome.
- Set `cost` for an additional active-currency/material price. Omitting it is free.
- Set `reward_class` for future outputs. To allow recyclable reconstruction,
  also permit recycling in the effective shared definition. Its default
  fallback explicitly has `recyclable: false`. Existing protected items cannot
  gain recycling permission through a policy change.
- Change thresholds or retention in the shared definition. Overrides and legacy
  tiered-Analyzer reconstruction can take precedence; inspect the resolved export.

Use `/reload`, `/gg research status`, and `/gg research export` to validate a
change. A rejected research reload keeps the previous publication. Then inspect
all affected Data items in an Analyzer; global processing mode can override a
valid datapack definition. The [installable examples](../examples/research-economy/README.md)
cover duplicate exclusion, weighted pools, and recyclable outputs.

## Resource families and publication

Place definitions under either directory:

- `data/<namespace>/taczweaponblueprints/research_data/<name>.json`
- `data/<namespace>/taczweaponblueprints/analysis_reconstruction/<name>.json`

The definition ID is `<namespace>:<name>`. Both families use format `1`. They publish together with research profiles, rules, and tags. A malformed file, missing profile/tag reference, conflicting reconstruction scope, or unusable reconstruction reference prevents replacement of the current research snapshot. Errors identify the resource and pack; reference errors identify the owning definition. Removing a weapon from the catalog makes it unavailable to the pool without substituting an unrelated weapon.

Each family supports up to 256 definitions, with 65,536 aggregate selector, override, and ingredient terms. Each JSON file is limited to 2,000,000 characters. New-family JSON rejects duplicate object keys and excessive nesting. Unknown fields, explicit nulls, noncanonical IDs, fractional counts, invalid booleans, and out-of-range values are rejected. Use fully qualified lowercase `namespace:path` IDs. Higher-priority resource packs can replace a file with the same ID using normal resource-pack precedence.

## Research Data definition

This example describes a custom Note-style pool with smaller fragment quantities
than the shipped preset. Its reconstruction reference uses the bundled shared
fallback. To change that fallback, replace its existing resource with the shared definition shown below
instead of adding a second global fallback.

```json
{
  "format": 1,
  "input": { "item": "taczweaponblueprints:research_note" },
  "behavior": "analysis",
  "pool": {
    "tier_weights": { "tier_1": 1 }
  },
  "reconstruction": "taczweaponblueprints:research_data",
  "rows": {
    "tier_1": {
      "default": {
        "blueprint_weight": 0,
        "fragment_weight": 1,
        "no_result_weight": 0,
        "min_fragments": 1,
        "max_fragments": 1
      }
    },
    "tier_2": {
      "default": {
        "blueprint_weight": 0,
        "fragment_weight": 1,
        "no_result_weight": 0,
        "min_fragments": 1,
        "max_fragments": 2
      }
    },
    "tier_3": {
      "default": {
        "blueprint_weight": 0,
        "fragment_weight": 1,
        "no_result_weight": 0,
        "min_fragments": 2,
        "max_fragments": 3
      }
    }
  }
}
```

| Field | Meaning and default |
| --- | --- |
| `enabled` | Defaults to `true`. A disabled winning definition still owns the input. |
| `priority` | Integer from −1,000,000 to 1,000,000; default `0`. Higher wins. |
| `input` | Exactly one `item` or item `tag`. Missing exact items fail reload; unresolved item tags remain unavailable and are diagnosed. |
| `profile` | Optional research profile ID; omission applies to all profiles. A specified profile must exist. |
| `behavior` | Required: `analysis`, `legacy_currency`, or `disabled`. In global analysis mode this determines the selected input owner’s action. Disabled owners never fall through to another reward. |
| `minimum_analyzer_tier` | `tier_1`, `tier_2`, or `tier_3`; default `tier_1`. |
| `cost` | Existing `{ "points": 0, "ingredients": [] }` cost format; defaults to zero. The numeric channel uses the active currency. |
| `pool` | Required target-pool object described below. |
| `allow_learned` | Optional override of the caller's global default, which is `true`. This is independent of weapon-analysis `allow_known`. |
| `allow_undiscovered` | Optional override of the caller's global default, which is `true`. Unmet Tech Tree prerequisites do not filter the pool. |
| `progress_bonus` | Integer from `0` to `1000`; default `0`. Adds within-tier weight for compatible archived partial progress. |
| `reward_class` | Defaults to `protected_prerequisites`. Also accepts `protected_bypass`, `recyclable_prerequisites`, or `recyclable_bypass`. |
| `reconstruction` | Required when any row has positive fragment weight. References the single enabled shared fallback definition. Target/profile/legacy reconstruction authority can take precedence over that fallback. |
| `rows` | Required for analysis: all three Analyzer tiers. Non-analysis behavior must omit rows and reconstruction. |

Input selection uses priority, then exact item before item tag, then ascending definition ID. Profile scope filters matches; it does not add another priority. Selection returns the winner and a list of shadowed definitions for diagnostics. Definitions are selected whole, without accumulating fields from losing matches. A disabled or legacy winner must never fall through to a lower analysis definition.

Every station row has a required `default` outcome table and an optional `targets` map keyed by target tier. For example, `targets.tier_3` overrides the default only for Tier 3 targets at that station. Outcome tables use the same integer shape as weapon analysis, but `no_result_weight` must be zero. Positive fragment weight requires a quantity range from 1 to 64; zero fragment weight requires `0/0`. Blueprint and fragment weights range from zero to 1,000,000,000, with a positive combined total. A full result is exactly one blueprint.

## Pool rules

| Pool field | Meaning |
| --- | --- |
| `tier_weights` | Required map of allowed tiers and weights. Omitted tiers and zero weights are excluded. At least one tier must have positive weight. |
| `include` | Optional existing research-target selector: exact `blueprints`, blueprint `tags`, or a catalog `selector`. Omission includes all otherwise valid weapons. |
| `exclude` | Optional research-target selector. Exclusion wins over inclusion. |
| `weapon_weights` | Optional map of canonical weapon IDs to weights; unlisted weapons weigh `1`, zero excludes. Up to 256 entries. |
| `tier_overrides` | Optional map of canonical weapon IDs to pool tiers. Up to 256 entries. |

Catalog selectors support namespaces, item types, path prefixes, exclusions, and blueprint kinds using the existing research-target syntax. Referenced blueprint tags must exist. Research Data pools always contain weapons; selecting another kind cannot make ammo or attachments eligible.

The bundled default pool shapes are:

| Research Data | Allowed tiers |
| --- | --- |
| Research Note | Tier 1 |
| Research Report | Tiers 1–2 |
| Research Dossier | Tiers 1–3 |

These definitions ship with the mod. Notes produce fragments only; Reports and
Dossiers also have full-blueprint chances. The [balance guide](research-economy-balance.md#data-outcomes)
lists the complete current odds and quantities. Pack authors can replace pools
and rows independently of currency mode and weapon-analysis policies.

Target tier comes from the current resolved Research Bench progression policy unless `tier_overrides` supplies a pool tier. It does not come from crafting tier, Tech Tree coordinates, or Analyzer weapon difficulty. A pool-tier override does not alter that target's reconstruction threshold. Partial outcomes still require usable shared reconstruction; an unknown reconstruction tier can therefore exclude a target even with a pool override.

The evaluator filters canonical catalog entries and configured selectors, then applies current access, discovery, and learned state. The server caller supplies the blacklist/exemption predicate. Hidden or undiscovered targets can remain eligible; later blueprint learning still enforces the reward's recorded permission class. Excluding learned targets affects new selection only, never reconstruction of already-owned fragments.

After filtering, empty tiers are removed and the remaining tier weights are renormalized. The evaluator first selects a tier, then a weapon within that tier. Adding many Tier 1 weapons cannot change Tier 3's overall probability. It then selects the outcome and fragment quantity for that weapon's station/target-tier row. No persistent random bag, inventory-fragment scan, or implicit progress deposit is used.

An empty result distinguishes `all_learned` from an otherwise empty or unavailable pool. The aggregate blueprint probability includes tier weights, weapon weights, and row overrides. Fragment ranges include only outcomes with positive fragment weight. A preview must use that aggregate or a tier breakdown; it must not display one arbitrary target row as the whole pool's odds.

For a compatible partial archive count `p`, threshold `t`, base weapon weight `w`, and configured bonus `b`, the effective weapon weight is `w + floor(w × b × p / t)`. This applies only when `0 < p < t` in the definition's exact reward class. Completed sets and other classes receive no bonus. The tier weight is unchanged. Arithmetic uses checked wide totals.

## Shared reconstruction definition

A replacement for the referenced `taczweaponblueprints:research_data` fallback
can be the following. Unlike the bundled file, this example omits `recyclable`,
so it permits recorded recyclable classes; protected classes stay protected:

```json
{
  "format": 1,
  "fragments": {
    "thresholds": { "tier_1": 5, "tier_2": 10, "tier_3": 15 },
    "retained_progress_cap": 1000
  },
  "learning": "require_tree_prerequisites"
}
```

Optional `profile` and `target` fields restrict the scope. Optional `enabled` defaults to `true`; optional `recyclable` defaults to `true` so recorded recyclable classes remain usable. This does not make protected fragments recyclable. `learning` defaults to `require_tree_prerequisites` and can be `bypass_tree_prerequisites`; `disabled` is valid only for a disabled definition. All three thresholds are required, from 1 to 1,000,000. Retention must cover every threshold and cannot exceed the existing 1,000,000,000 archive bound.

Authority, in order:

1. Explicit target plus matching profile.
2. Explicit target without a profile restriction.
3. Explicit profile.
4. Existing resolved Analyzer reconstruction settings.
5. The one shared fallback without target/profile fields.

Two definitions with the same scope are rejected, including duplicate fallback definitions. A selected explicit target/profile disable wins over legacy settings and fallback. Disabling the fallback disables that final fallback only. Data references must point to the enabled fallback; they cannot supply another threshold for an identical fragment stack. Legacy Analyzer thresholds retain their existing difficulty-tier interpretation. Explicit shared thresholds use the Research Bench tier.

Current shared permissions restrict the recorded fragment class. They cannot turn a protected fragment into recyclable output or change a prerequisite-respecting fragment into bypass output. Equivalent target/classes combine across sources, stations, and holders. Different classes stay separate. Already learned targets can reconstruct duplicate blueprints. Removed content, disabled reconstruction, and threshold changes preserve raw archived counts.

A policy reload invalidates reconstruction quotes through the existing research revision. Partial weapon-analysis previews use the same current threshold and retention cap as reconstruction. If their shared reconstruction is disabled, the partial attempt becomes unavailable. Existing weapon outcome probabilities and prices remain unchanged.

## Integration boundary

`ResearchDataPoolResolver.Context` belongs to one immutable research/catalog/progression publication. It caches static candidates for at most eight definition/profile/station combinations, capped at 262,144 candidate records and a 65,536-entry catalog. Dynamic access, discovery, learned state, and archive weights are reapplied from a fresh player view. Replace the context when any source publication changes. Preparing a pool never draws randomness or mutates progression.

`Pool.select` accepts an injected bounded random source and rejects unavailable pools. The server transaction checks station identity, input, full output capacity, combined currency/material payment, source revisions, and current player state before calling it. It validates output templates for every possible target and outcome, then reevaluates the pool after builders and before sampling. A successful transaction creates one physical result and discovers its target; it does not additionally learn the blueprint or archive emitted fragments. Failed writes restore input, output, payment, and progression; failures never disclose the sampled result.

## Server modes and workstation actions

`researchDataBehavior` is independent of `researchCurrencyKind` and gameplay currency-award enablement:

- `LEGACY_CURRENCY` retains deterministic redemption and Redeem Stack, using the configured currency and existing turn-in amounts.
- `ANALYSIS` enables the authored input router. A matching enabled `analysis` definition exposes Analyze Data; a matching enabled `legacy_currency` definition retains deterministic slot redemption. A disabled owner blocks processing, and unmatched items cannot use legacy Data redemption as a fallback.
- `DISABLED` prevents Research Data processing.

`researchDataAllowLearned` and `researchDataAllowUndiscovered` both default to `true`. Definition-level values override these settings. They affect target selection, not the ability to reconstruct fragments already owned. The bundled Note, Report, and Dossier definitions use these global defaults.

Analyze Data consumes exactly one item and requires an empty extract-only output slot. The output slot cannot be used to favor compatible targets or outcomes. Any candidate requiring a new discovery when discovery storage is full blocks the whole attempt before sampling. The combined bill uses the selected currency and main inventory materials; the input slot cannot also pay that bill. There is no bulk random action.

Menu requests bind container identity, station tier, input count, state token, and a monotonically increasing request ID. A used request, changed input/output/inventory, changed player state, replaced station, or updated policy invalidates the attempt. Transactions also reject reentrant menu actions while writes are in progress. Prepared service transactions are one-use and bound to their original player-data and workstation objects.

Inventory-wide `ResearchDataRedemptionService` calls return `STATION_REQUIRED` in analysis mode and `DATA_DISABLED` when disabled. They do not invent a station tier. Slot redemption and live inventory-turn-in awards recheck current input ownership, including after pre-award callbacks. Analysis results publish discovery transitions only after the physical transaction commits.

The Analyzer supplies Analyze Data, current status, active-currency costs,
material requirements, and the aggregate presentation described below. The
[configuration](research-currency-configuration.md) and
[migration](research-currency-migration.md) guides cover mode selection and
existing-world updates.

## Analyzer presentation

An analysis preview names the input Data item and shows its configured weapon
tiers, current eligible outcome odds, fragment quantity range, minimum station,
and comparisons for the other two Analyzer tiers. Scroll the detail panel or
use Page Up and Page Down to read duplicate eligibility, undiscovered-target
rules, output requirements, reward protection, and optional partial-progress
preference. Long rows expose their full text on hover and through narration.

The server computes these summaries from the player’s eligible pool. They
contain tier masks and aggregate probabilities, never candidate weapon IDs or
a preselected reward. Station comparisons describe potential outcomes, not a
guarantee that the player can afford that station’s configured action. Very small
possible outcomes use bounds such as `<0.01%`; rounding never labels a possible
outcome impossible or a mixed outcome certain. Exact zero and certainty are
shown only when the outcome possibilities justify them.

The preview shows the one-item consumption, active-currency cost and balance,
materials, and blocking status. A committed action reports the actual output;
fragments remain physical until the player chooses to archive or reconstruct
them. No preview reveals or learns a hidden blueprint.
