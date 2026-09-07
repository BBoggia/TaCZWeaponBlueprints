# Research currency and Research Data contract

Physical Scrap and Research Data analysis are the defaults. Spending, rewards, shared reconstruction, migration tools, loot supply, and economy reports are implemented. Explicit banked/legacy settings remain supported. See [economy balance](research-economy-balance.md), [migration](research-currency-migration.md), and [currency integrations](research-currency-integrations.md).

## Currency and costs

The server uses one active currency: physical `taczweaponblueprints:scrap` by default, or banked Research Points (RP). Scrap already exists and stacks to 64. Physical currency is a single canonical registered item, configurable by pack authors. Match its item ID; custom names do not create extra value. Do not accept other items through the Scrap tag, arbitrary NBT values, or exchange denominations. Normal item-stack limits apply to a replacement item.

One legacy authored `points` unit represents one active currency unit. Keep this integer field readable in old profiles, rules, rewards, and fragment settings. The UI uses the active name and icon. A physical currency setting does not turn the saved RP balance into an inventory count.

Research Bench cost channels stay separate from currency storage:

| Existing serialized mode | Physical currency | Banked currency |
| --- | --- | --- |
| `POINTS_AND_ITEMS` | Scrap and authored materials | RP and authored materials |
| `POINTS_ONLY` | Scrap | RP |
| `ITEMS_ONLY` | Authored materials | Authored materials |

Display currency-neutral labels for these modes, retaining the serialized values. Analyzer costs keep their existing independent policies; this Bench switch does not start masking Analyzer materials or prices.

Read and spend only main inventory/hotbar slots 0–35. Exclude armor, offhand, carried cursor, open-menu input/output, nested inventories, and nearby containers. An input consumed by an Analyzer action is separately reserved. This preserves the current main-inventory payment scope. Available counts and server commit must use the same slots and matching rules.

Combine physical currency and ordinary ingredient demands in one exact inventory allocation. A stack matching both Scrap and an authored material/tag cannot be counted twice. Apply existing integer currency discounts before allocation. Preserve complete multi-node costs, shared-prerequisite deduplication, and per-node Creative bypass. Amounts above a stack use multiple stacks. Amounts beyond payable inventory capacity report insufficient resources without integer overflow, clipping, or becoming free.

Check gross cost before reward. An operation cannot pay itself with the currency it is about to produce. Research route selection remains based on structural closure and existing tie-breaks; live inventory changes readiness, not the selected route. Include active currency identity and policy revision in quotes. Revalidate exact inventory before committing. The RP cap has no effect on physical currency availability.

## Currency delivery and award history

Manual Analyzer currency rewards use the output slot first, then main inventory for the remainder. Fill a compatible existing currency stack or use an empty output; incompatible output blocks the action even if the inventory has room. Preflight complete delivery against the projected post-payment inventory before consuming anything. Report how much goes to output and inventory. Do not drop manual overflow into the world or partially consume an input for a partial reward. Deterministic bulk redemption repeats complete single-item transactions and stops before the first item whose complete result cannot fit.

Gameplay event awards use main inventory first, then physical item drops at the player's current location. Preflight the whole event batch. Bound work to at most 64 new stacks across the batch, with no more than 16 world item entities; these are execution limits, not currency caps. With default Scrap, the stack bound limits one event batch to 4,096 units, and physical inventory/drop capacity can impose a lower limit. Validate configured rewards against stack size. Reject an oversized physical batch without claims or currency; finite events remain eligible for their existing reconciliation. Provide an operator diagnostic. Do not silently clamp or reinterpret a banked `clamp` rule as permission to destroy undelivered physical rewards. Banked mode retains both existing overflow policies.

Only successful delivery can consume a finite claim, cooldown, or budget entry. Retain source validation, combat eligibility, award grouping, once-per-target identity, and post-commit ordering. No automatic mode change resets award history. Unclaimed finite events can reconcile under their existing rules; transient failures do not create a saved currency wallet or a new pending-payment queue.

Stage physical delivery and ledger changes on the server thread, with revalidation after cancellable pre-events. Ordinary insertion/spawn/commit failures restore affected inventory, discard only newly spawned entities from that attempt, and restore the attempt's ledger changes. Do not mutate unrelated inventory in listeners or assume third-party side effects can be undone. Persistence across abrupt process termination has ordinary Minecraft save limits; it is not a database transaction across player and world files.

Current finite awards supplement renewable physical loot. Their values and the balance-report assumptions are documented in the economy guide. Separate enablement of gameplay currency awards, Research Data loot, and Scrap loot. Disabling gameplay awards must not silently disable data processing or its independent loot supply.

## APIs and commands

`ResearchCurrencyAwards` returns the active policy, awarded units, committed award count, and status. It shares source registration with the banked compatibility API. Physical mode emits cancellable `ResearchCurrencyAwardEvent.Pre` and committed `Post` events; banked mode retains the existing RP events. One award is not dispatched through both event families. See [currency integrations](research-currency-integrations.md#commands-and-java-api).

The existing `ResearchPointAwards.trigger` and `triggerFromCommand` return a result explicitly named `awardedPoints`. Preserve them as banked-only compatibility methods. In physical mode they return a new append-only `UNSUPPORTED_CURRENCY_MODE` failure before event dispatch, delivery, or claim mutation. Never put a Scrap amount in `awardedPoints` or add invisible RP through this path. `registerSource`, `isRegistered`, and `registeredSources` remain usable in either mode.

`/gg research awards trigger` uses the new API and reports active currency. `/gg progression points give` and points-only resets remain banked and report a dormant balance in Scrap mode. Active-currency grants and previewed withdrawal commands are available. Direct capability RP mutations remain banked bookkeeping, not inventory operations; unsupported external capabilities must fail payment/delivery preflight rather than lose items.

Internal services that accept only player progression data cannot mint physical items. Use an explicit inventory/workstation delivery context for physical mode. Keep banked overloads for old integrations and tests. Audit each call site rather than silently adapting an RP-only overload to a missing physical destination.

## Research Data behavior

Currency mode and data behavior are independent server settings. Data can use analysis, deterministic currency redemption, or be disabled. Analysis is the default; deterministic redemption preserves the old 1/3/6 values unless authored otherwise.

| Item | Default weapon pool | Default result types |
| --- | --- | --- |
| Research Note | Tier 1 | Fragments |
| Research Report | Tiers 1 and 2 | Fragments or a full blueprint |
| Research Dossier | Tiers 1, 2, and 3 | Fragments or a full blueprint |

The data item controls its pool. Analyzer level controls fragment quantity and full-blueprint odds. Notes retain zero full-blueprint chance at all station levels by default. Data processing consumes one item, charges zero additional Scrap/materials by default, and produces one physical blueprint or a fragment stack for one weapon. One random action processes one data item; it requires an empty output slot that can hold every possible result.

Determine target tier from current resolved Research Bench progression. A dedicated data-policy override can assign a different pool tier. Do not inherit a weapon-analysis difficulty override, crafting tier, or visual depth accidentally. Exclude unresolved tiers with diagnostics; optional explicit fallback is pack-controlled. Normal blacklists, missing catalog entries, and progression exemptions still apply. A prerequisite-respecting blueprint can be acquired before the player meets its learning prerequisites. The default pool can include undiscovered weapons without leaking those identities in previews.

Learned targets remain valid by default, with a global setting and per-data override. If exclusion is enabled, filter them before any draw. Empty tiers are removed and remaining positive tier weights renormalized. Choose a tier first, then a canonical weapon within it. No remaining valid tier disables processing and leaves the input untouched. A target being learned does not itself cause failure when duplicates are allowed.

Outcome quantity and full-blueprint odds are configurable per item and Analyzer tier, with optional target-tier rows. Tier and weapon weights use bounded integers and checked wide totals. Bundled starting values are listed in the [balance guide](research-economy-balance.md). Compatible archived-partial-progress bias is optional and disabled by default; enabling it must not change the tier distribution or default learned-target eligibility.

Do not roll on preview, hover, GUI refresh, or rejected action. Do not reveal a predicted target. Validate output, station, player, input, current policy/pool, and capacity before rolling. Commit input, optional price, physical output, and discovery together. Full blueprint creation does not automatically learn it. Dispatch discovery awards only after the result commits.

Analysis owns its input exclusively. Existing deterministic turn-in rules remain loaded for legacy mode, but must not also reward data processed through analysis. No fallback redemption follows a failed analysis. Inventory-wide legacy redemption APIs in analysis mode return a station/action-required result; they cannot synthesize an Analyzer tier or bypass the menu. Bulk redemption remains available in deterministic mode.

## Reconstruction authority

Use a shared reconstruction policy independent of weapon-attempt eligibility. Both weapon and data fragments use the existing target plus four-class analysis marker. Equivalent target/classes combine normally; ordinary unmarked fragments retain their existing discount/reconstruction meaning.

Resolve one reconstruction threshold/cap per target, not per data source. Precedence is: explicit shared target rule, explicit shared profile setting, legacy Analyzer reconstruction setting, then the shared fallback when enabled. The new shared fallback uses thresholds 5/10/15 and a combined retention cap of 1,000 as initial values. Existing legacy Analyzer values retain precedence over that fallback. Definitions explicitly disabling shared reconstruction override fallback. Candidate pools exclude targets lacking usable reconstruction when fragments have positive weight.

When common learning/recycling restrictions are explicitly configured, apply them as restrictions to the recorded class. Otherwise preserve existing restrictions for legacy Analyzer profiles; use normal prerequisites and protected output for newly enabled data reconstruction. Configuration cannot promote a protected item to recyclable or a prerequisite-respecting item to bypass output. A shared-policy change must not erase raw counts or emit items on reload.

Reject simultaneous equally authoritative shared declarations with conflicting values instead of choosing based on which source produced a stack. Disable new partial outcomes if no unambiguous usable policy exists. Weapon-analysis `allow_known`, mode, station matrix, and found-weapon origin are not reconstruction gates. Already-owned fragments can reconstruct learned targets even when new data selection excludes them.

## Upgrade and conversion

The feature defaults new configurations and older files missing the new selectors to Scrap plus data analysis. Explicit selectors take precedence. Preserve existing non-currency configuration values and saved banked balances; a nonzero RP balance does not implicitly select legacy mode. The explicit banked/legacy preset and startup notice provide balance inspection and conversion guidance.

Conversion is an operator-controlled, previewable one-way RP withdrawal into the currently selected Scrap item, 1:1. Reserve inventory capacity, revalidate balance and active item/policy, deliver only the requested bounded amount, and subtract exactly the delivered amount in one server transaction. Withdrawing only part of the bank requires previewing that smaller amount; each confirmed request must deliver its whole amount and reports the retained RP. Do not automatically withdraw on login, drop conversion overflow, grant a second copy, or offer automatic Scrap-to-RP deposits. Refuse offline-player conversion in the initial implementation; preserve offline balances until an online operation is possible.

Keep knowledge, recipes, discoveries, ordinary/analysis archives, and award ledgers intact. Changing the configured currency item does not transform old physical items. Reconnecting, restarting, or toggling modes does not reset claims or reissue converted balances. A deliberately restored old save restores old state and is outside normal retry protection.

The four active combinations are Scrap/analysis, RP/analysis, Scrap/deterministic redemption, and RP/deterministic redemption. Either currency also supports disabled Data processing. Current builds require protocol 62 on both client and server. Player data remains version 5 and server configuration version 4. Historical network fixtures are regression evidence, not compatibility with older clients. See the [mode matrix](research-currency-configuration.md#choose-the-economy).
