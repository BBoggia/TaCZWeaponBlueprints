# Blueprint Analyzer transactions

Tiered weapon analysis and marked analysis-fragment reconstruction run on the
server. A weapon attempt pays one configured cost and commits one result: a
physical blueprint, a matching fragment stack, or a configured no-result.
Legacy profiles, ammo, attachments, Research Data, ordinary fragment operations,
and direct found-weapon RP recovery retain their existing policies.

## Weapon evaluation and commit

`BlueprintReverseEngineeringService.evaluate` and `reverseEngineer` route
resolved guns through `BlueprintWeaponAnalysisService` whenever the effective
Analyzer policy is non-legacy. Disabled or invalid tiered policies cannot fall
back to guaranteed blueprint extraction. Direct RP recovery deliberately calls
its separate evaluator and retains the legacy reverse-engineering cost.

A tiered operation requires a valid physical station supplied by the menu's
`WorkstationTransaction`. Old transaction implementations that omit station
access remain compatible with legacy operations and cannot authorize tiered
weapon attempts. The station tier comes from the server-bound block.

The preparation step resolves the effective station/weapon/origin policy and
runs the existing physical, catalog, blacklist, progression, known-target,
loaded-ammo, attachment, modification, count, RP, and material checks using that
policy's attempt settings. It snapshots both slots, materials, and serialized
player progression, and binds the current policy publications and configuration.
Preparing a quote never draws randomness.

Commit first validates every positive-weight output at its maximum possible
quantity. Blueprint identity, provenance, fragment target/class, count, and item
stack limits must all match. Random attempts always require an empty output
slot, including no-result-only rows. An unavailable output never removes an
outcome from the table or changes its probability.

Only after preflight passes does the selector draw the result. Successful
writes consume materials, the physical weapon count, and RP, place the result
when applicable, and discover the target. They do not learn it or deposit the
same physical fragments in an archive. Postconditions verify the expected item,
material, RP, and discovery changes and preserve other progression channels.

Failures restore the two slots, material inventory, and complete player-data
snapshot. Return status distinguishes rollback failure. The `AttemptResult`
contains a committed outcome only on success; failed candidates remain private.
A `Prepared` instance can be committed only once, including after failure.
Post-commit discovery awards and synchronization use the existing dispatcher and
retry handling.

## Request and preview validity

The open menu rejects non-increasing request IDs with constant storage. A client
that exhausts the positive integer request range must reopen the menu. Protocol
59 carries the bounded preview and committed result extensions, including the
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
RP or discount service. The existing wire fields carry the matching class's
before/deposit/after counts and one-set reconstruction result. Known-target
status does not turn marked fragments into RP.

Reconstruction uses the current target threshold and shared retention cap.
Weapon-attempt origin, known-target, row-enable, and minimum-station gates do not
turn previously earned fragments into a new weapon attempt. Disabled Analyzer
policies, missing targets/tiers, stale progression, disabled learning, global
blueprint exclusions, and unavailable player data block reconstruction while
preserving stored counts. Current learning policy can restrict prerequisite
bypass. Recycling permission remains recorded in the fragment class; changing
found-weapon recovery mode does not rewrite acquired fragments.

The current station/configuration/publication is checked throughout fragment
commit as well. A completed archived set can reconstruct with zero newly accepted
fragments, retaining the identifying input stack. Excess progress remains stored.
Malformed markers and oversized fragment stacks are rejected without selecting
another input's reward route.

## Screen synchronization

The server supplies station/weapon tiers, integer outcome weights, matching-class
progress, total retained progress, protection, and separate analysis/direct-RP
costs. Preview construction never rolls. The result packet carries the exact
committed blueprint/fragment/no-result outcome, or the committed fragment
transfer and reconstruction counts. Failed transactions contain neither.

See [Analyzer screen and synchronization](analyzer-screen.md) for the display,
packet bounds, request feedback, and compatibility contract. The bundled profile
stays legacy; existing player worlds are not switched automatically.

See the [policy guide](analyzer-policy-authoring.md),
[fragment storage guide](analysis-fragment-storage.md), and
[station guide](blueprint-analyzer-stations.md) for their respective contracts.
