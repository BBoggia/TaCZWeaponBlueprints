# Research currency integrations

Research purchases and rewards can use banked Research Points (RP) or a physical item. Physical Scrap is the default; banked RP remains an explicit option. Physical mode uses the existing Scrap item unless the server operator selects another registered item. Research Data defaults to analysis, which can produce blueprints or fragments from authored pools; see the [Research Data guide](research-data-policies.md).

## Server settings

In the Blueprint configuration, set `researchCurrencyKind` to `BANKED_RP` or `PHYSICAL_ITEM`. Set `researchCurrencyItem` to one canonical item ID, initially `taczweaponblueprints:scrap`. Each item represents one currency unit. Supported currency items stack to at most 64. An unavailable item cannot pay or deliver currency. The banked `researchPointCap` applies only in banked mode.

The existing `points` fields in costs, award definitions, and ordinary fragment returns retain their numeric values. In physical mode they count currency items. Research Bench cost-channel settings still determine whether currency, materials, or both are charged. Analyzer cost policies remain independent. Currency changes preserve stored RP, learned blueprints, fragment archives, and award claims; they do not exchange items or grant a new copy of previously claimed rewards.

Bundled physical supply comes from exploration chests and player kills; see [economy balance](research-economy-balance.md). Operators can preview and confirm one-way withdrawals of saved RP; see [currency migration](research-currency-migration.md).

## Purchases and manual returns

Single research, whole-path research, deterministic reverse engineering, and tiered weapon analysis combine physical currency and material requirements in one allocation. Only main inventory and hotbar slots can pay. Ordinary fragment discounts and per-node Creative bypass apply before allocation. Inventory changes affect readiness without selecting a different prerequisite route.

Blueprint recycling, direct found-weapon recovery, completed ordinary fragment returns, and legacy Research Data redemption deliver physical rewards through the Analyzer output first, then main inventory. Incompatible output blocks the action even if inventory has space. The complete result must fit; manual actions do not drop overflow. Data bulk redemption commits complete items one at a time and stops before the next item cannot fit. An inventory-only deterministic turn-in can use the slot freed by its consumed input.

Found-weapon recovery reserves its complete gross payment before adding its reward. Found/crafted origin rules, known-target restrictions, blueprint protection, and weapon outcome probabilities are unchanged. New rewards never finance their own initial price. Physical analysis fragments still reconstruct blueprints and do not become currency.

## Gameplay awards and claims

The live award dispatcher evaluates existing claims, cooldowns, rate windows, shared budgets, combat rules, and configured sources. Physical rewards require complete delivery; banked clamp policies never silently truncate an item reward. All winning groups in one resolved event share the 64-stack-equivalent limit and the limit of 16 overflow item entities. Manual turn-ins use their stricter output/inventory destination.

Item delivery and award-ledger updates participate in the same server transaction. Failed entity publication discards newly staged entities and restores owned inventory and claim changes when possible. Finite reconciliation retains failed physical deliveries and retries at a bounded interval. Claim identities survive save/load and currency-mode changes. Transient failed events do not become a saved spendable balance.

Compensation attempts each owned input, inventory, and ledger restoration even if a different participant throws while restoring. It is verified and reports a distinct rollback failure if another mod prevents complete restoration. These guarantees concern an executing transaction, not arbitrary process termination between separate Minecraft world/player saves. Physical items have Minecraft's normal persistence limits.

## Commands and Java API

- `/gg progression currency give <targets> <amount>` grants the active currency. Physical grants use bounded inventory/event delivery and require the complete amount to fit its limits. Banked grants require room under the point cap.
- `/gg progression points give <targets> <amount>` remains explicitly banked. Points resets affect saved RP; progression resets do not scan for or delete physical Scrap.
- `/gg research awards trigger` uses the active currency while preserving configured eligibility and claims.
- `ResearchCurrencyAwards` shares source registration with `ResearchPointAwards`. Its result includes a `ResearchCurrencyPolicy`, `awardedUnits`, and `committedAwards` alongside the existing award status vocabulary.
- `ResearchPointAwards.trigger` and `triggerFromCommand` remain banked compatibility APIs. Physical mode returns `UNSUPPORTED_CURRENCY_MODE` before awards, events, or claims. They never report Scrap as `awardedPoints`.
- Direct capability balance mutations remain banked bookkeeping. A low-level award commit that has only capability data rejects a physical configuration; physical delivery requires an explicit inventory context.

Banked mode retains cancellable `ResearchPointAwardEvent.Pre` and committed `Post` events. Physical mode emits `ResearchCurrencyAwardEvent.Pre` and `Post`, including the denomination and unit count. Pre events can veto their award; post events report committed delivery. Physical awards do not emit RP events. Both currency modes recheck configuration and award publication after pre-award callbacks. Feedback retains the awarded denomination if a post-award callback changes the active setting. Presentation results retain original definition order so named feedback remains attached to the correct reward. Live Research Bench and Research Data failure responses report the active currency balance rather than dormant RP.

## Synchronization and compatibility

Protocol 62 requires matching client and server versions. A separate currency packet carries the server policy revision and active main-inventory amount. Saved RP synchronization remains separate. Reward feedback identifies the actual delivered denomination. Research route fingerprints include currency identity and revision; Analyzer state tokens include the currency configuration through the progression snapshot and the independent Research Data settings.

The existing player save shape remains version 5, and configuration remains readable from version 4. Existing Analyzer preview/result fields and enum ordinals are preserved, with new failure values appended. Numeric fields retaining `point` names in those compatibility records describe the active currency on live physical-mode paths. Client currency labels use the connection’s server publication. Inventory changes refresh active balances and affordability without requiring a new selection. Disconnect clears the publication, and older policy revisions cannot replace a newer one. The currency packet also carries the independent Data mode and default eligibility settings.

## Player guidance

The Research Bench and Blueprint Journal show costs, balances, route shortages,
pinned goals, and affordability in the active currency. Physical mode shows a
currency item icon in balance headers. Saved banked RP stays separate and does
not appear as spendable Scrap. The Analyzer uses the same denomination for
analysis payments and recovery; recovery retains its own cost and status.

Research Data tooltips and Journal onboarding follow the server’s Data mode.
JEI and EMI information pages explain analysis, currency redemption, and disabled
processing, since packs can choose these independently from the currency.
Gameplay currency help excludes general Data redemption when analysis or
disabled processing is selected. Exact item policies are explained by the
Analyzer preview.

`enableResearchDataLoot` controls configured Research Data drops independently
from `enableResearchPointAwards`. Disabling gameplay currency awards therefore
does not remove Data loot. Data processing can also be disabled without changing
loot generation. The banked RP cap is inactive in the configuration UI while
physical currency is selected; its saved value is preserved.
