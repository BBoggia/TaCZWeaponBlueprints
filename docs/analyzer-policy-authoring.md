# Blueprint Analyzer policy authoring

Research profiles and rules accept an optional `analyzer` extension in format 5. It describes weapon analysis odds, costs, station requirements, and blueprint reconstruction thresholds. **Tiered policies now control weapon outcomes at the Blueprint Analyzer.** Legacy profiles remain deterministic. The screen displays resolved odds and separate analysis/direct-recovery costs from the server; see the [screen guide](analyzer-screen.md).

The [behavior contract](analyzer-behavior-contract.md) defines the gameplay, item protection, and save compatibility. This guide defines the implemented data format. Existing format 1–4 resources and profiles without the extension retain their meaning. The bundled `duplicate_recovery` profile remains format 4.

## Profile fields

Set the research profile's `format` to `5` and add `analyzer` alongside its existing fields. Resource locations remain under `data/<namespace>/taczweaponblueprints/research_profiles/` and `research_rules/`.

| Field | Meaning |
| --- | --- |
| `mode` | Required: `legacy`, `tiered`, or `disabled`. Missing the entire extension means `legacy`. These modes describe weapon analysis; ammo and attachment conversion keep their existing policy. |
| `fallback_weapon_tier` | Optional `tier_1`, `tier_2`, or `tier_3`, used only when no progression assignment exists. Omit it to report an unavailable tier. |
| `minimum_analyzer_tier` | Optional station minimum, default `tier_1`. Higher weapon tiers do not themselves block a lower station. |
| `allowed_origins` | Optional list of `found`, `crafted`, and `unknown`; defaults to all three. An empty list blocks every origin; duplicates are invalid. Found means verified loot provenance. |
| `attempt` | Optional overrides described below. |
| `reconstruction` | Required when any row has a positive fragment weight. Contains complete weapon-tier `thresholds` and `retained_progress_cap`. |
| `tiers` | Required in tiered mode: outer keys are Analyzer tiers, inner keys are weapon tiers. All nine rows must exist. |

Legacy and disabled modes forbid tier tables and reconstruction settings. Omitted optional fields use the documented defaults; explicit `null`, unknown fields, unknown tiers, and invalid numeric types are errors. Disabled rows are validated too.

The following is a complete `analyzer` object, to insert into an otherwise valid format-5 profile. Its odds match the optional tiered starting preset; it leaves costs inherited. The installable preset supplies an explicit cost and fallback tier. See [presets, migration, and balance reporting](analyzer-presets-and-balancing.md) for complete files and resolved acquisition-cost estimates.

```json
{
  "mode": "tiered",
  "minimum_analyzer_tier": "tier_1",
  "allowed_origins": ["found", "crafted", "unknown"],
  "attempt": {
    "allow_known": false,
    "physical_blueprint_learning": "require_tree_prerequisites",
    "output_recyclable": false
  },
  "reconstruction": {
    "thresholds": {"tier_1": 5, "tier_2": 10, "tier_3": 15},
    "retained_progress_cap": 1000
  },
  "tiers": {
    "tier_1": {
      "tier_1": {"outcomes": {"blueprint_weight": 70, "fragment_weight": 30, "no_result_weight": 0, "min_fragments": 1, "max_fragments": 1}},
      "tier_2": {"outcomes": {"blueprint_weight": 30, "fragment_weight": 50, "no_result_weight": 20, "min_fragments": 1, "max_fragments": 1}},
      "tier_3": {"outcomes": {"blueprint_weight": 10, "fragment_weight": 40, "no_result_weight": 50, "min_fragments": 1, "max_fragments": 1}}
    },
    "tier_2": {
      "tier_1": {"outcomes": {"blueprint_weight": 100, "fragment_weight": 0, "no_result_weight": 0, "min_fragments": 0, "max_fragments": 0}},
      "tier_2": {"outcomes": {"blueprint_weight": 70, "fragment_weight": 30, "no_result_weight": 0, "min_fragments": 1, "max_fragments": 1}},
      "tier_3": {"outcomes": {"blueprint_weight": 30, "fragment_weight": 50, "no_result_weight": 20, "min_fragments": 1, "max_fragments": 1}}
    },
    "tier_3": {
      "tier_1": {"outcomes": {"blueprint_weight": 100, "fragment_weight": 0, "no_result_weight": 0, "min_fragments": 0, "max_fragments": 0}},
      "tier_2": {"outcomes": {"blueprint_weight": 100, "fragment_weight": 0, "no_result_weight": 0, "min_fragments": 0, "max_fragments": 0}},
      "tier_3": {"outcomes": {"blueprint_weight": 80, "fragment_weight": 20, "no_result_weight": 0, "min_fragments": 1, "max_fragments": 1}}
    }
  }
}
```

Each row accepts `enabled` (default true), required `outcomes`, and optional `attempt`. Replacing a row replaces all its fields, including omitted defaults.

All five outcome fields are required integers. Each weight is between 0 and 1,000,000,000, and their total must be positive. The probability is its weight divided by the total: weights 1 and 7 produce 12.5% and 87.5%. This permits fractional percentages while currency payments remain integer
units: Scrap by default or optional Research Points (RP). The total uses a 64-bit integer, so three maximum weights are valid.

A positive fragment weight requires `1 <= min_fragments <= max_fragments <= 64`. The quantity is uniformly selected from that inclusive range. If fragment weight is zero, both quantity fields must be zero. A blueprint result always has quantity one. A positive no-result weight explicitly permits consumption without an item reward; it is never inferred. A no-result-only row is valid. Deterministic outcome kinds require no outcome-selection draw; a variable fragment quantity still requires its own draw.

Reconstruction thresholds are integers from 1 to 1,000,000 for each weapon tier. The retention cap is from 1 to 1,000,000,000 and must cover every threshold. Thresholds depend on the weapon, not the station, so changing stations does not change an existing set's completion requirement. The [analysis fragment storage and reconstruction API](analysis-fragment-storage.md)
uses these fields unless an explicit shared target/profile definition takes
precedence. Shared reconstruction can also operate when weapon analysis is
legacy or disabled; see [shared settings](research-data-policies.md#shared-reconstruction-definition).

## Costs and result permissions

The tiered base inherits `enabled`, `input_count`, `cost`, and `allow_modified` from the target's effective legacy reverse-engineering policy. It starts with `allow_known: false`, prerequisite-respecting learning, protected output, and no expert economy-loop opt-in. The legacy policy itself is unchanged.

Apply overrides in this order:

1. The profile's `analyzer.attempt`.
2. The selected station/weapon row's `attempt`.
3. The winning Analyzer rule's top-level `attempt`.

An attempt accepts `enabled`, `input_count`, `cost`, `allow_known`, `allow_modified`, `physical_blueprint_learning`, `output_recyclable`, and `expert_allow_economy_loop`. These reuse reverse-engineering semantics. `input_count` is 1–64; an omitted effective value means the existing gun default of one. A `cost` replaces the complete cost, with integer `points` from 0 to 1,000,000,000 and up to six ingredient terms of 1–64 items each. Item and tag syntax follows [research cost authoring](research-cost-and-found-weapon-recovery.md). Missing exact items reject reload; unresolved tags are reported and their costs remain unavailable.

Learning modes are `require_tree_prerequisites`, `bypass_tree_prerequisites`, and `disabled`. Positive fragment outcomes forbid disabled learning. Crafted or unknown inputs producing recyclable rewards require `expert_allow_economy_loop: true`. A found-only policy can permit recyclable output without that opt-in. Verified-found recovery modes can grant recycling permission to full and partial results; those permissions never transfer to crafted or unknown inputs.

New Analyzer attempt overrides do not change the deterministic direct-recovery action's cost. See the [found-weapon recovery matrix](analyzer-behavior-contract.md#found-weapon-recovery-matrix) for the separate action behavior.

## Rule selection and weapon tiers

Analyzer overrides use their own rule-selection category. Exact blueprint matches win over blueprint-tag matches, which win over selectors. Within the winning category, higher priority wins; lexicographically earlier rule IDs resolve ties. Only the one selected rule applies. Lower matching Analyzer rules do not contribute fields. An Analyzer-only rule cannot hide a broader research cost, prerequisite, reverse-engineering, or crafting rule.

A rule may override `mode`, `weapon_tier`, `minimum_analyzer_tier`, `allowed_origins`, `reconstruction`, `attempt`, and individual `tiers` rows. Reconstruction replacement must supply all three thresholds and the cap. There is no rule-level fallback tier. Switching a legacy profile into tiered mode requires a complete valid table; merely supplying rows does not implicitly enable it.

This example targets one weapon under an existing tiered profile and changes only its T1 Analyzer/T3 weapon row:

```json
{
  "format": 5,
  "profile": "example:research",
  "priority": 10,
  "target": {"blueprints": ["tacz:ak47"]},
  "analyzer": {
    "weapon_tier": "tier_3",
    "attempt": {"cost": {"points": 4}},
    "tiers": {
      "tier_1": {
        "tier_3": {
          "outcomes": {
            "blueprint_weight": 1,
            "fragment_weight": 7,
            "no_result_weight": 0,
            "min_fragments": 2,
            "max_fragments": 4
          }
        }
      }
    }
  }
}
```

Weapon tier resolution uses the selected rule's `weapon_tier`, then the revision-matched progression research tier, then the explicit profile fallback. The progression source and review flag remain visible. An outdated progression publication reports `stale_progression` instead of falling back to a different tier. An explicit Analyzer rule tier remains usable without progression. An absent tier reports `tier_unavailable`.

Disabling Tech Tree display does not disable an explicitly assigned Analyzer tier or fallback. Visual coordinates, tree depth, and crafting workstation requirements never supply an analysis tier.

## Reloads, quotes, and export

All profile policies and individual rule overlays are validated before publication, including shadowed rules. Invalid data retains the previous research snapshot. Successful reloads replace the snapshot and revision together. Quote consumers must acquire a new context after reload rather than retaining old policy data.

`/gg research export` now writes catalog format 22. Gun entries contain `analyzer_policy`. Legacy entries without an extension stay compact. Configured entries include nine quotes: three station tiers times three input origins for that weapon's resolved tier. Quotes expose the selected rule, tier source/review flag, configuration status, effective attempt cost and permissions, weights, probabilities, fragment range, threshold, and retention cap. Probabilities use fractions from 0 to 1.

The catalog export uses `recovery_mode_scope: "protected_mode_baseline"`; it does not claim to report the server's current found-weapon recovery mode. The diagnostics API accepts an explicit recovery mode for callers that need that projection. All results carry `scope: "policy_only"`, `gameplay_enabled: true`, and `player_eligibility_checked: false`. The gameplay flag records that this build implements Analyzer transactions; it does not assert that a particular player can perform the exported action. `configured` means that the configuration permits this combination, not that the player's inventory, knowledge, or resources permit an action. Exports never draw randomness or expose a future result.

Strict catalog consumers must accept format 22 explicitly. Existing format-21 fields retain their meanings. No network or player-data format change is required by this data-only extension.
