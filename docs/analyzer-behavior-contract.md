# Blueprint Analyzer behavior contract

This document records compatibility requirements and the agreed implementation contract for tiered Blueprint Analyzers in [TaCZ] Weapon Research & Blueprints. **Tiered weapon transactions, protected fragment reconstruction, and all three station tiers are implemented.** Profiles without the Analyzer extension retain deterministic behavior. The screen shows tiers, odds, matching progress, separate action costs, and committed results; see the [screen guide](analyzer-screen.md). The [policy authoring guide](analyzer-policy-authoring.md) defines the implemented fields, precedence, validation, and export. The [analysis fragment guide](analysis-fragment-storage.md) defines item identity, persistence, reconstruction, and synchronization.

All numeric prices and returns use the active currency: physical Scrap by
default or optional banked Research Points (RP). Data defaults to analysis, while
the bundled weapon policy defaults to tiered analysis. See [currency configuration](research-currency-configuration.md)
and [Data policies](research-data-policies.md).

## Input actions

The Blueprint Analyzer has one input slot and one output slot. Materials are taken from the player's inventory. The output slot does not accept player insertion. Its inventory belongs to the open menu; closing the menu returns the items through the existing container-close behavior. There is no persistent machine inventory or processing timer.

| Input | Current action and result | Relevant restriction |
| --- | --- | --- |
| Empty | No action | Nothing is consumed. |
| Unsupported or malformed item | No action | Invalid identity must never select another input type's reward. |
| Ordinary physical blueprint | Recycle for its configured currency value | Recycling policy, known-target policy, provenance, and complete delivery capacity apply. |
| Protected physical blueprint | Recycling is rejected | Changing its holder or learning the target does not remove protection. |
| Research Data, analysis mode | Analyze one item for a blueprint or matching fragments | Item pool, station odds, learned/discovery rules, and empty output apply. |
| Research Data, legacy mode | Redeem one or redeem the available stack for currency | Award rules and complete delivery capacity determine the accepted count; no random roll. |
| Research Data, disabled mode | No processing | Input remains intact. |
| Eligible weapon | Create that weapon's physical blueprint | Found-weapon mode may instead expose direct currency; see below. |
| Eligible ammo | Create its physical blueprint | Default input count is one canonical recipe output batch; an explicit count may override it. |
| Eligible attachment | Create its physical blueprint | Default input count is one; an explicit count may override it. |
| Ordinary fragments, unknown target, discount mode | Archive target-specific progress | Completed sets become a matching Research Bench currency discount, not immediate learning. |
| Ordinary fragments, unknown target, reconstruction mode | Archive and reconstruct when a set completes | One protected blueprint requiring normal prerequisites; output must be available. |
| Ordinary fragments, known target | Redeem a completed set for configured currency | Only if enabled and the complete award fits. No automatic conversion on learning. |
| Removed target or disabled fragment policy | Reject the fragment action | Saved counts are retained, not reassigned or erased. |

Physical-item analysis also checks catalog membership, blacklists, progression exemptions, enabled policy, player data, known status, loaded ammunition, installed attachments, permitted modifications, input count, currency, materials, and output space. Loaded weapons are accepted when their ammunition can be recovered safely; installed attachments still block consumption. Successful analysis returns physical magazine and chamber ammunition to the player, dropping inventory overflow at their feet. Failed actions leave the loaded weapon unchanged. A blueprint result discovers the target without learning it. Transaction failure restores consumed input, materials, currency, output, and affected progression.

The bundled `duplicate_recovery` profile explicitly permits known copies, costs 0 currency for reverse engineering, allows permitted modifications, and creates non-recyclable blueprints that bypass tree prerequisites. Its ordinary fragments use research discounts, with thresholds 5/10/15, a retention cap of 1000, a 25% currency discount, and 1 currency per completed learned-target set. These profile values differ from generic policy defaults and can be overridden by effective server policy.

## Found-weapon recovery matrix

“Found” means positive loot-generation provenance. An unmarked gun is unknown, even if the player obtained it from a chest. These modes do not waive catalog, eligibility, cost, known-target, or capacity checks.

| Mode | Verified found gun in legacy mode | Verified found gun in tiered mode | Crafted or unknown gun |
| --- | --- | --- | --- |
| `PROTECTED_BLUEPRINT_ONLY` | Guaranteed protected blueprint | Configured blueprint, fragments, or no result; protected outputs by default | Blueprint analysis under the active legacy/tiered policy; no direct currency |
| `RECYCLABLE_BLUEPRINT` | Guaranteed recyclable blueprint | Configured full blueprint or fragments whose reconstructed blueprint has the same recycling permission | Ordinary configured analysis; no found-only recycling permission or direct currency |
| `DIRECT_RP_ONLY` | Deterministic direct currency; no blueprint extraction | Same deterministic direct currency; no randomized weapon extraction | Ordinary configured analysis remains available; this mode only redirects verified found guns |
| `PLAYER_CHOICE` | Choose recyclable blueprint or deterministic direct currency | Choose configured blueprint/fragment analysis or deterministic direct currency | Ordinary configured analysis; no direct currency |

Direct currency recovery pays the applicable reverse-engineering cost, consumes the configured input count, delivers the shared recycling value, and discovers without learning. In physical mode it fills compatible Output then main inventory and requires the complete reward to fit. Banked mode creates no item and uses capacity under the RP cap after payment. It retains its existing cost policy when new tiered analysis costs are introduced; pack documentation must identify the two effective costs explicitly. A direct currency action must not roll, deposit fragments, or grant a blueprint.

## Tiered analysis decisions

### Tiers and eligibility

The existing block/item ID `taczweaponblueprints:blueprint_recycler` remains tier 1. Advanced Blueprint Analyzer and Experimental Blueprint Analyzer provide tiers 2 and 3, sharing the existing menu type and controller. Analyzer capability uses a separate tier type from Research Bench and crafting requirements. Server code resolves it from the actual block at the menu position. The [station guide](blueprint-analyzer-stations.md) describes recipes, harvesting, existing-world compatibility, and menu invalidation.

Weapon analysis tier resolves in this order:

1. The selected Analyzer rule's explicit analysis-tier override.
2. The active progression policy's `ResolvedBlueprintProgressionPolicy.researchWorkbenchTier()` result, including any explicit authored tier or automatic allocation already resolved there.
3. An explicit Analyzer fallback when progression cannot resolve that weapon; otherwise analysis is unavailable.

The progression resolver's review/fallback evidence remains visible in diagnostics. Visual tree depth, branch placement, and crafting workstation tier are never substitute analysis tiers. Disabling the Tech Tree display does not disable explicitly configured analysis tiers. Global blueprint enablement, exclusions, and input validation still apply.

Use one selected rule per policy category with the existing priority, specificity, and stable tie-break semantics. Analyzer-only rules must not shadow unrelated legacy research-cost or prerequisite rules. This is not a cumulative merge of every matching tag and exact-target rule.

### Optional tiered preset

The optional tiered preset permits under-tier attempts and uses full-blueprint or positive fragment outcomes. No-result probability defaults to zero. It rejects already-known weapon analysis by default, while preserving an explicit pack override to allow copies. Legacy profiles, including their explicit `allow_known` values, are unchanged.

Full and reconstructed results share a learning mode and recycling permission. The optional preset requires normal Tech Tree prerequisites and protects the blueprint against recycling. A pack may choose prerequisite bypass for both outcomes. Explicit found-recovery permissions from the matrix apply to both full and reconstructed rewards; crafted/unknown-derived recyclable rewards require the existing expert economy-loop opt-in.

Probability and quantity values remain tuning parameters. The optional preset ships the 70/30/10, 100/70/30, and 100/100/80 percent table as starting values for pack testing. Costs remain integer currency/material amounts. Fractional percentages do not require fractional currency storage.

### Physical outcomes and progress

One accepted weapon analysis consumes its configured input count and costs and creates exactly one outcome: a physical blueprint, a stack of matching physical fragments, or an explicitly configured no-result. It never both grants physical fragments and archives those same fragments. A target becomes discovered on a successful committed attempt; none of these outcomes automatically learns it.

Analyzer fragments reconstruct a matching physical blueprint. They are independent of the ordinary loot-fragment discount/reconstruction preset. Their saved progress cannot discount Research Bench costs or directly return currency, including after the target is learned. Previously acquired Analyzer fragments may reconstruct duplicate blueprints for trading, with their recorded protections intact.

An explicit fragment action deposits input and may reconstruct one complete set. It may also reconstruct an already archived complete set while matching fragment input identifies the target, even when the archive is full and zero new fragments can be accepted. The screen must show consumed versus retained input and one-set consumption. Excess physical fragments and saved progress remain intact. A future empty-input archive browser is outside this feature.

Positive-weight partial outcomes require an enabled reconstruction policy and a learning mode that permits learning. Reject a partial-reward policy with `DISABLED` learning rather than creating unusable progress. Legacy deterministic recyclable-only recovery may retain its existing disabled-learning behavior.

## Fragment protection and storage boundary

The fragment item has an optional versioned analysis marker. Unmarked target-only fragments remain ordinary fragments. Present malformed, unknown-version, or incomplete analysis markers fail closed; they must never fall back to an ordinary fragment. Record no random seed, chance table, policy revision, or arbitrary policy ID in stacking identity.

Use four stable analysis reward classes:

| Class | Reconstructed blueprint learning | Recyclable |
| --- | --- | --- |
| Protected / prerequisites | Require prerequisites | No |
| Protected / bypass | Bypass prerequisites | No |
| Recyclable / prerequisites | Require prerequisites | Yes, subject to normal recycling policy |
| Recyclable / bypass | Bypass prerequisites | Yes, subject to normal recycling policy |

All four classes are reconstruction-only and grant zero direct fragment currency. Persist stable named values rather than enum ordinals. Record the resolved permission at fragment creation; do not recompute it from the current holder's inventory or from the recovery mode selected later. Different targets/classes do not stack or merge archive counts. Equivalent rewards may combine regardless of which Analyzer tier produced them.

Old player archive entries remain in the ordinary channel. Bounded analysis counts are stored alongside it. One analysis retention cap applies to the sum of its classes for a target, not separately to each class. The existing hard progress-value bound also limits combined ordinary/analysis counts for a target, and the existing target-count bound applies to the union of saved target IDs. Loading a legacy save must never discard existing valid ordinary counts to make space for new analysis data. Exceeding remaining capacity rejects additional deposits without consuming them.

Saved counts are raw counts. Current analysis thresholds determine reconstruction cost, and a threshold change neither erases counts nor produces output on reload. A lower retention cap blocks extra deposits without deleting prior progress. Disabled policies and removed targets retain progress for later restoration. Current policy may restrict use of a recorded reward but cannot promote protected rewards into recyclable or prerequisite-bypassing rewards.

## Random transaction and UI boundary

Eligibility and quoted outcomes use the same resolved server policy. Opening the screen, refreshing a preview, changing selection, or rejecting a request must not roll. The client receives probabilities and progress, never a predicted random result.

Random attempts require an empty output slot. Validate every positive-weight output and its maximum stack size before selecting an outcome. An occupied blueprint or fragment stack must not filter accepted results. Output probability uses bounded integer weights with a positive total and overflow-safe accumulation; fragment ranges are bounded and inclusive.

Revalidate station tier, input identity/count/NBT, inventory, player knowledge, matching progress, currency, policy/catalog publication, and menu state token on action. Successful requests consume once. Stale or duplicate requests cannot repeat the transaction. All mutations roll back together on failure. Candidate results from failed transactions must not be disclosed or selectively retried.

The screen retains both slots and presents Input, Output, station/weapon tier, full and partial probabilities, partial quantity, reconstruction progress, costs, and consumption. No-result chance is visible when configured. Action feedback describes the actual committed result. Pending, disabled, stale, and completed states remain distinct.

## Compatibility and validation

Missing Analyzer policy extensions preserve legacy deterministic behavior. Do not reinterpret existing blueprints, ordinary fragment items, player archives, or the active loot-fragment preset. The bundled Research Data analysis and shared reconstruction policies operate independently of weapon-analysis mode. Analyzer authoring uses profile/rule format 5 and catalog export format 22; existing formats remain readable. Analysis archives add player-data version 5; the current preview/result packets use protocol 62; versions 0–4 load with no analysis progress, and clients and servers must update together.

Frozen compatibility inputs live under `src/test/resources/analyzer-compatibility/`. Tests must decode these old inputs through current readers and assert player-visible meaning. Do not regenerate old inputs from a future serializer or change them merely to make a migration test pass. Current transaction, screen-model, request, fragment, recovery, and packet tests provide the deterministic behavior baseline.

The [integration validation guide](analyzer-validation.md) describes automated checks and opt-in dedicated-server acceptance with the packaged preset, real client actions, reconnects, and saved-player migration.
