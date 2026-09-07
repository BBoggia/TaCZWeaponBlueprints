# Research currency primitives

Shared payment, delivery, and rollback primitives support both banked RP and physical currency. Gameplay integrations are described in [research currency integrations](research-currency-integrations.md); the complete behavior contract is defined in [the currency contract](research-currency-contract.md). Saved balances are not automatically converted.

## Policy publication and transport

`BlueprintConfig.currencyPolicy()` publishes the selected banked or physical policy. Unchanged denomination, item ID, and applicable cap retain the same policy object and revision; changing any of them advances the revision. The revision is process-local, is not serialized into the config file, and must be taken from the server's quote when validating purchases. Client-local publication counters are not server authorization tokens.

`ResearchCurrencyPolicy` represents either banked RP with a cap or a physical item ID with no RP cap. `physicalUnit()` resolves the exact registered item and rejects missing IDs, air, and unsupported stack sizes. The current physical primitive supports currency stack sizes 1–64. It never substitutes air for a missing item or treats an item tag as an exchange-rate registry.

`ResearchCurrencyPolicyCodec` provides a strict format-1 JSON descriptor and a bounded format-1 binary payload. Kinds use explicit encodings, canonical item IDs, and positive revisions. The current protocol 63 includes an active-currency publication, reward denominations, and the appended Research Data analysis action. Clients and servers must match. Existing Analyzer preview/result layouts retain their old field and enum positions; frozen protocol-59 payload fixtures remain readable. The currency selector is available to server operators, with physical Scrap as the default.

## Preparing payment and delivery

Create a `ResearchCurrencyState` from exactly the 36 main-inventory/hotbar slots, the operation's output stack, and the saved banked balance. It takes defensive copies and returns defensive copies. Current supported snapshots require legal item counts and stack limits at most 64. An operation without workstation output supplies an empty output and uses an inventory-only or event destination.

Pass a `ResearchCurrencyPlanner.Request` with the already resolved gross debit, complete material requirements, total reward, destination, and banked overflow policy. Cost channels, fragment discount rounding, and per-node Creative bypass are applied by the caller before preparation. The existing research ingredient allocator receives physical currency and materials together, so exact-item and tag demands cannot reuse the same inventory units.

Amounts use nonnegative `long` totals. There are at most 6,144 material terms. Total demand is compared against actual inventory supply before entering the allocator; huge costs fail without constructing unbounded allocations or narrowing into a negative integer. After gross currency affordability is checked, physical currency demand fits the bounded inventory before conversion to the allocator's integer count. Material failures return an unchanged plan.

Delivery destinations are explicit:

- `MANUAL_OUTPUT`: fill compatible output first, then main inventory. Incompatible output blocks the action. Complete fit is required; no world overflow.
- `INVENTORY_ONLY`: complete fit in main inventory, used by withdrawal and other adapters that must not drop overflow.
- `EVENT`: fill main inventory, then prepare up to 16 physical drops. The total event request is limited to 64 stack-equivalents, including fills of existing stacks. A caller must aggregate the complete event batch before planning rather than bypass these limits by submitting every group independently.

Fresh currency stacks merge only with identical item tags. A named currency item has the same debit value, but its name/NBT is not erased by merging newly minted plain currency into it. A full compatible output can spill to inventory. Reward capacity is evaluated after payment, including slots freed by payment. Rewards cannot fund their own gross debit.

Banked plans preserve require-full and clamp behavior after gross payment. A clamp at the cap can prepare zero delivered units so a caller can commit the existing ledger-only claim behavior. The quote does not itself grant permission to consume an input or record a claim; the caller's reward policy remains authoritative. An already-over-cap balance is preserved or reduced by payment, never forcibly trimmed. A zero-cost, zero-reward operation does not require RP capacity.

The returned plan holds before, after-payment, and after-delivery snapshots plus proposed drops. `ResearchCurrencyQuote` reports eligibility, policy, requested debit/reward, available currency, delivered reward, and physical destination counts. These counts describe the planned outcome, not proof of a committed action.

## Committing as part of an operation

`ResearchCurrencyTransaction.commit` requires server-owned context and participant adapters. It does not accept a client-created plan or independently discover a workstation.

The context must validate server thread, player identity, menu/station identity where relevant, and current policy publication. It supplies compare-and-replace state access, compensation, and unpublished drop handles. `stageDrop` must not spawn anything; its handle must be retained before `publish` can insert an entity or invoke events. A cancelled or throwing publication must remain discardable through that handle.

The participant owns input consumption, knowledge, or award claims. It must support preflight, commit verification, and rollback. It must not independently edit the currency-owned inventory/output/balance. The coordinator applies gross payment, commits the participant, applies reward delivery, publishes prepared drops, and verifies all postconditions. Notifications and irreversible external effects belong after a successful return, not in participant preflight.

Prepared plans are one-use, including after stale rejection or failure. The coordinator rechecks after participant preflight because listeners can change inventory or policy. Currency writes, participant failures, changed leases, cancelled drops, and altered earlier drops enter compensation. Cleanup attempts all owned drops, participant rollback, and currency restoration even if an earlier cleanup step fails.

`ResearchCurrencyState.restoration` builds a compensation plan only when touched values still match an operation's known before/paid/after states. It preserves unrelated changes in untouched slots and refuses unknown replacements of owned slots. Context adapters must use this or equivalent compare-and-restore logic; they must not blindly replace the whole inventory from an old snapshot. The coordinator reports `ROLLBACK_FAILED` if the full expected state cannot be verified, including external mutation conflicts. That status must not be presented as an ordinary untouched failure.

Only a successful transaction has nonzero `Result.deliveredUnits()`. A failed result can retain its original quote for diagnostics, so consumers must not use quote amounts as committed rewards. Live player/container adapters use the same coordinator for manual physical returns and event delivery. Purchase adapters reuse the combined payment plan inside their existing knowledge/output transactions. Unit checks cover controlled write and publication failures; native checks exercise registered Scrap, real Analyzer output slots, and item entities.
