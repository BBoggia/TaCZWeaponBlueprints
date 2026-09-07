# Blueprint Analyzer presets and balance reports

The optional Analyzer datapacks provide a complete tiered starting point, a
legacy reference, and working examples of weapon-specific overrides. They use
the existing `taczweaponblueprints:duplicate_recovery` profile so its Tech Tree,
research rules, and crafting assignments remain connected. Installing the mod
alone uses tiered weapon analysis. Research Data analysis and shared
fragment reconstruction are enabled independently by default.

## Choose and install a preset

The sources are in [examples/blueprint-analyzer](../examples/blueprint-analyzer/README.md).
From the source checkout, package them with Java 17:

```sh
./gradlew packageAnalyzerDatapacks
```

The command writes these archives under `build/distributions/analyzer/`:

| Archive | Use |
| --- | --- |
| `blueprint-analyzer-tiered.zip` | Reference matching the bundled tiered policy; install only to customize it. |
| `blueprint-analyzer-legacy.zip` | Restores optional legacy guaranteed weapon analysis. |
| `blueprint-analyzer-overrides.zip` | Optional exact-target and blueprint-tag examples. Requires the tiered pack or another complete tiered policy for the same profile. |

1. Copy the chosen zip into a test world's `datapacks` directory. Keep
   `pack.mcmeta` at the archive root. Enable it using `/datapack enable` if it
   is not already enabled, then run `/reload` or restart the server.
2. Keep `activeResearchProfile = "taczweaponblueprints:duplicate_recovery"` in
   the mod's Blueprint configuration. If you already use a custom profile,
   merge the `analyzer` object into that profile and set its format to 5 instead
   of switching your whole progression profile.
3. Use `foundWeaponRecoveryMode = "PROTECTED_BLUEPRINT_ONLY"` for the protected
   starting behavior described here. Existing global settings are not changed
   by these datapacks. Direct-only mode still awards currency instead of rolling
   blueprint outcomes; recyclable and player-choice modes can make found-weapon
   rewards recyclable.
4. Check `/gg research status` and `/gg research export` after a successful reload.
   Inspect the inserted weapon in the Analyzer to confirm its actual station
   tier, weapon tier, costs, and available action.

Enable either the legacy or the tiered profile override, not both. Datapacks
replace a profile file as a whole; they do not merge JSON objects from multiple
files. These full profiles match the bundled settings outside `analyzer`, but
they can overwrite another pack's customizations at the same resource ID.
Merge only the Analyzer extension when preserving those customizations. The
override examples also depend on the TaCZ AK-47 and M4A1 blueprint IDs.

## Tiered starting values

Prices below use active currency units: Scrap by default, or banked RP when
selected. Each accepted weapon attempt consumes one weapon, **3 currency units**, and **2 Iron Ingots**.
Known targets are blocked; permitted modifications are discarded. Loaded weapons return recoverable ammunition after successful consumption;
installed attachments still block consumption. The new Analyzer override
does not change ammo, attachment, or direct-currency recovery costs.

| Analyzer | T1: blueprint / fragments / nothing | T2: blueprint / fragments / nothing | T3: blueprint / fragments / nothing |
| --- | --- | --- | --- |
| T1 | 70% / 30% / 0% | 30% / 50% / 20% | 10% / 40% / 50% |
| T2 | 100% / 0% / 0% | 70% / 30% / 0% | 30% / 50% / 20% |
| T3 | 100% / 0% / 0% | 100% / 0% / 0% | 80% / 20% / 0% |

A no-result attempt consumes the weapon and its analysis cost; recoverable
ammunition is still returned. No-result chances apply only above the station's
tier in the bundled policy. All nine rows, quantity ranges, costs, eligibility,
and reconstruction requirements remain configurable through profile/rule data.

Every partial result contains one matching analysis fragment. T1/T2/T3 weapons
need 5/10/15 matching fragments respectively; the per-target retention cap is
1,000 across the applicable shared progress channels. Outputs require normal Tech Tree prerequisites to learn. The profile
allows found, crafted, and unknown weapon origins with protected outputs under
the recommended recovery mode. Weapon tiers come from progression, with an
explicit T2 fallback marked for review when no assignment exists.

These are starting values for pack testing, not a claim of finished balance.
For example, a T3 weapon in a T1 Analyzer needs about **9.65 attempts** on average
to obtain a first blueprint, including reconstruction. That is about 28.94 currency
units and 19.30 Iron Ingots, plus 9.65 weapons. There is no finite worst-case
attempt count because a no-result roll adds no fragment progress. Trading,
existing progress, blocked deposits, policy changes, and insufficient resources
change that interpretation.

## Override examples and common changes

The optional override pack contains a blueprint tag with AK-47 and M4A1:

- The tag rule assigns T3, costs 6 currency units plus 4 Iron Ingots, and makes T1 analysis
  award 2–4 fragments with no full-blueprint or no-result outcome. From zero
  progress, M4A1 then needs about 5.37 attempts on average and at most 8.
- The exact AK-47 rule sets a minimum T2 Analyzer, costs 4 currency units plus 1 Iron Ingot,
  and guarantees a full blueprint at T2. Its priority is lower than the tag
  rule's, deliberately demonstrating that exact matches win first.

Only the winning Analyzer rule applies. AK-47 does not inherit the tag rule's
cost or row. Other research and crafting rule categories select independently.

To customize these examples:

- Block under-tier attempts with `minimum_analyzer_tier` on a target/tag rule.
  For one particular pair, add `enabled: false` to its **complete existing row**;
  replacing a row still requires all five outcome fields.
- Guarantee a blueprint with weights `1/0/0` and fragment range `0/0`.
- Guarantee partial progress with weights `0/1/0` and a positive inclusive range.
- Add paid failures only by explicitly setting a positive `no_result_weight`.
  Any possible no-result removes a finite worst-case attempt guarantee.
- Change `attempt.cost` as a complete replacement. Include both currency and materials
  if both should remain; a points-only cost removes inherited ingredients.
- Change reconstruction with all three thresholds and a cap covering them.
  A rule replaces the complete reconstruction object.

See [policy authoring](analyzer-policy-authoring.md) for strict fields,
precedence, origin permissions, and reload validation.

## Generate a balance report

Run `/gg research export` on the server after loading your intended datapacks.
It writes `<world>/taczweaponblueprints/research-catalog.json` for the active
profile. In a source checkout with Java 17, run:

```sh
./gradlew generateAnalyzerBalanceReport \
  "-PanalyzerCatalog=/path/to/world/taczweaponblueprints/research-catalog.json"
```

The default output is `build/reports/analyzer/balance.json`. Optional arguments:

```sh
./gradlew generateAnalyzerBalanceReport \
  "-PanalyzerCatalog=/path/to/research-catalog.json" \
  -PanalyzerTarget=tacz:ak47 \
  "-PanalyzerBalanceReport=/path/to/ak47-balance.json"
```

This is an offline authoring task, not an in-game command. It reads resolved
format-22 quotes, including selected rules and effective weapon tiers. Its weapon estimates use a deterministic recurrence. When the optional
`research_data_economy` export section is present, the report also runs seeded
Data simulations. It never changes datapacks or reads or writes player saves. Catalog
exports describe the protected found-weapon recovery baseline; each report row
preserves that scope. They do not establish the current player's eligibility or
the server's current direct-currency/player-choice behavior.

Report format 1 contains a sorted row per weapon/station/origin combination:

- The source quote, selected rule, tier source, permissions, costs, and weights.
- `estimate_status`: `reachable`, `unreachable`, or `not_configured`.
- Expected paid attempts, weapons consumed, currency consumed, and material counts.
  Ingredient alternatives remain one term; the report does not choose a specific
  item from a tag or count every alternative as an additional cost.
- `maximum_attempts`, or `null` when no finite bound exists. Blocked combinations
  have no estimate; no-result-only rows have no finite expected resource cost.

Compact legacy targets are counted as omitted. The report starts at zero
matching progress and assumes a fixed station/policy, enough inputs and currency,
empty output space, no competing archive progress, and fragment deposits between
attempts. Reconstruction itself costs no additional weapon or currency. The stopping
point is the first physical blueprint, not learning the target. Expected
fractional currency units here is an average across attempts; individual currency transactions
remain integers.

For remaining fragments `r`, total weight `W`, full weight `B`, partial weight
`F`, and uniform quantity `k`, the calculator uses:

```text
E(0) = 0
E(r) = [W + F × mean(E(max(0, r-k)))] / (B + F)
```

This includes the no-result self-loop and fragment overshoot. It evaluates the
recurrence with double precision and a sliding window of at most 65 states.
It caches matching tables/thresholds across weapons, and rejects work exceeding
20 million distinct recurrence states; select one target if that limit is hit.
Inputs are limited to 256 MiB and 65,536 catalog entries. Weapon estimates
are deterministic floating-point calculations, not Monte Carlo samples. The
separate Data section uses seeded simulations; see [economy reports](research-economy-balance.md#reports-and-examples).
The JSON contains no `Infinity` or `NaN`; all-no-result rows are explicitly
unreachable. Failed input validation leaves an existing report untouched.

## Migration and rollback

The bundled profile now enables tiered weapon analysis. Updating it does not convert currency, learned blueprints, discoveries, or
ordinary fragments. Economy selector upgrades are a separate decision described
in [currency migration](research-currency-migration.md). Ordinary loot fragments keep their configured discount/reconstruction
behavior; new analysis fragments use their separate protected archive classes.

Saved analysis counts remain raw counts when thresholds or caps change. Lowering
a cap does not erase excess stored progress. Existing fragment permissions do
not become more permissive when a holder trades them or learns the target.
Current learning policy can restrict reconstruction; it does not rewrite item
identity. See [fragment storage](analysis-fragment-storage.md).

To return to legacy behavior, install the legacy profile override and disable
conflicting tiered rule/profile packs, then reload or restart. Restore your own previous profile file if you
merged the extension into a custom pack. Retained analysis fragments and saved
analysis progress remain preserved. They can still reconstruct when an enabled
shared target/profile rule or fallback applies, including the bundled Data
fallback. Only an unavailable or disabled effective reconstruction policy blocks
that action. Removing the tiered extension can change which shared thresholds
and restrictions take precedence. There is no
automatic conversion into ordinary fragment discounts or currency.

These datapacks and the report tool add no network or player-data format change.
Matching protocol-63 client/server builds are still required; player-data version
5 and catalog export format 22 remain current. Wider dedicated-server and
reconnect testing is separate from validating these authoring examples.
