# Blueprint Analyzer transactions

Tiered weapon analysis and marked analysis-fragment reconstruction run on the
server. A weapon attempt pays one configured cost and commits one result: a
physical blueprint, a matching fragment stack, or a configured no-result.
Legacy weapon profiles, ammo, attachments, ordinary fragment operations, and
direct found-weapon recovery retain their independent policies. Prices and
returns use physical Scrap by default or optional banked RP. Research Data has
its own [random analysis transaction](research-data-policies.md#integration-boundary).

## Weapon evaluation and commit

`BlueprintReverseEngineeringService.evaluate` and `reverseEngineer` route
resolved guns through `BlueprintWeaponAnalysisService` whenever the effective
Analyzer policy is non-legacy. Disabled or invalid tiered policies cannot fall
back to guaranteed blueprint extraction. Direct currency recovery deliberately calls
its separate evaluator and retains the legacy reverse-engineering cost.

A tiered operation requires a valid physical station supplied by the menu's
`WorkstationTransaction`. Old transaction implementations that omit station
access remain compatible with legacy operations and cannot authorize tiered
weapon attempts. The station tier comes from the server-bound block.

The preparation step resolves the effective station/weapon/origin policy and
runs the existing physical, catalog, blacklist, progression, known-target,
loaded-ammo, attachment, modification, count, currency, and material checks using that
policy's attempt settings. It snapshots both slots, materials, and serialized
player progression, and binds the current policy publications and configuration.
Preparing a quote never draws randomness.

Commit first validates every positive-weight output at its maximum possible
quantity. Blueprint identity, provenance, fragment target/class, count, and item
stack limits must all match. Random attempts always require an empty output
slot, including no-result-only rows. An unavailable output never removes an
outcome from the table or changes its probability.

Only after preflight passes does the selector draw the result. Successful
writes consume materials, the physical weapon count, and currency, place the result
when applicable, and discover the target. They do not learn it or deposit the
same physical fragments in an archive. Postconditions verify the expected item,
material, currency, and discovery changes and preserve other progression channels.

Failures restore the two slots, material inventory, and complete player-data
snapshot. Return status distinguishes rollback failure. The `AttemptResult`
contains a committed outcome only on success; failed candidates remain private.
A `Prepared` instance can be committed only once, including after failure.
Post-commit discovery awards and synchronization use the existing dispatcher and
retry handling.

## Loaded weapon ammunition

The production analysis and direct-recovery entry points use
`AnalyzerAmmoRecovery` around their existing transactions. The server resolves
ammunition from TaCZ gun data and stack state without modifying the input during
preview. Physical rounds include the magazine and, for a non-open-bolt weapon,
the chamber. Inventory-fed, dummy-ammo, and fuel-fed weapons produce no physical
ammunition refund, following TaCZ's unloading rules for those modes.

After successful weapon consumption, ammunition merges into matching main
inventory stacks, then empty main inventory slots. Overflow drops at the player's
feet with a pickup delay. Armor and offhand slots are untouched. A successful
configured no-result analysis still returns ammunition. Rejected actions return
nothing and preserve the loaded weapon.

Delivery failure removes any newly spawned ammunition entities and restores
input, output, main inventory, currency, and complete progression before success
feedback or discovery awards. A failed restoration reports rollback failure.
Malformed ammunition metadata, unknown ammunition, stacked guns, and unsafe
counts block consumption. A refund is bounded to 65,536 rounds and 1,024 stacks.
These bounds protect against malformed item data; they are not weapon capacity
settings. Low-level commit helpers retain their existing contracts; callers
requiring ammunition recovery must use the production entry points.

## Request and preview validity

The open menu rejects non-increasing request IDs with constant storage. A client
that exhausts the positive integer request range must reopen the menu. Protocol
62 carries the bounded preview and committed result extensions, including the
post-action preview token used to reject stale client feedback.
Clients and servers must update together.

Immediately before an action, the server refreshes the preview and compares its
full observed state, including both slot stacks, material inventory, player
knowledge and supplemental archives, policy/catalog revisions, and progression
and access settings. A state change advances the token even when the visible
preview happens to look identical. An accepted action reserves its token before
executing, so retries cannot reuse it after a failed roll.

Station removal or replacement invalidates the session immediately. Tiered
transactions also check their station/configuration/publication validity before
selection and at completion. Invalidated sessions cannot trigger a follow-up
preview refresh.

## Analysis-fragment reconstruction

Marked fragments route to `BlueprintAnalysisFragmentService` and
`AnalysisFragmentReconstructionService`; they never enter the ordinary fragment
currency or discount service. The existing wire fields carry the matching class's
before/deposit/after counts and one-set reconstruction result. Known-target
status does not turn marked fragments into currency.

Reconstruction uses the current target threshold and shared retention cap.
Weapon-attempt origin, known-target, row-enable, and minimum-station gates do not
turn previously earned fragments into a new weapon attempt. Disabled shared reconstruction, missing targets/tiers, stale progression, disabled learning, global
blueprint exclusions, and unavailable player data block reconstruction while
preserving stored counts. Shared reconstruction can remain enabled when weapon
analysis is legacy or disabled. Current learning policy can restrict prerequisite
bypass. Recycling permission remains recorded in the fragment class; changing
found-weapon recovery mode does not rewrite acquired fragments.

The current station/configuration/publication is checked throughout fragment
commit as well. A completed archived set can reconstruct with zero newly accepted
fragments, retaining the identifying input stack. Excess progress remains stored.
Malformed markers and oversized fragment stacks are rejected without selecting
another input's reward route.

## Screen synchronization

The server supplies station/weapon tiers, integer outcome weights, matching-class
progress, total retained progress, protection, and separate analysis/direct-currency
costs. Preview construction never rolls. The result packet carries the exact
committed blueprint/fragment/no-result outcome, or the committed fragment
transfer and reconstruction counts. Failed transactions contain neither.

See [Analyzer screen and synchronization](analyzer-screen.md) for the display,
packet bounds, request feedback, and compatibility contract. The bundled profile
uses tiered weapon analysis; Data analysis and shared reconstruction are enabled
by default when their new settings are absent.

See the [policy guide](analyzer-policy-authoring.md),
[fragment storage guide](analysis-fragment-storage.md), and
[station guide](blueprint-analyzer-stations.md) for their respective contracts.
