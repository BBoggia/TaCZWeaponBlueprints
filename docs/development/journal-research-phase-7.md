# Journal and Research Phase 7: Atomic Blueprint Recycling

## Scope

Phase 7 implements the server-authoritative transaction that converts one
eligible physical blueprint into Research Points. It is the single commit API
the Research Bench menu will call in Phase 8.

This phase deliberately does not add a shift-click shortcut, Journal action
packet, command, automatic pickup conversion, or standalone recycling screen.
The Phase 0 contract places voluntary recycling at the Research Bench; adding a
temporary interaction elsewhere would create a second authority path that later
phases would have to preserve or remove.

Phase 7 does not register the Research Bench block/menu, perform research,
consume research ingredients, or add administrator progression commands.

## Physical input identity

`BlueprintItem.getBlueprintId` now accepts only a non-empty physical
`BlueprintItem` stack with a string `bpId`. The ID must parse canonically as a
Minecraft resource location and remain within the shared 256-character limit.
Missing, wrongly typed, malformed, oversized, empty, and non-blueprint inputs
fail before policy resolution.

Blueprint items still carry only the durable blueprint output ID. They never
carry a point value, duplicate flag, permission, or cached datapack policy.

## Server authority boundary

`BlueprintRecyclingService.recycle(ServerPlayer, ItemStack)` is the public
transaction entry point. A future menu must pass the actual input-slot stack,
not a client-supplied count or point value. Immediately before commit, the
service:

1. validates the living server player and physical blueprint stack;
2. resolves the player's progression capability;
3. migrates any still-valid legacy recipe unlock aliases;
4. resolves policy from the current authoritative catalog, active research
   snapshot, synchronized configuration, blacklist, and player state;
5. verifies that the resolved policy identity and point balance still match;
6. verifies catalog availability, administrative permission, recycling
   enablement, positive value, duplicate/unlearned permission, and complete cap
   headroom;
7. credits the complete Research Point award;
8. consumes exactly one item from the real input stack;
9. publishes the new progression snapshot and disclosure-filtered Journal.

No client packet is added and the network protocol remains `5`. Phase 8 may
send a bounded container action, but that packet will only request that the
server run this transaction against the still-open matching menu.

## Atomicity and Creative behavior

All eligibility and overflow checks happen before either economic resource is
changed. Research Point addition uses the existing validated capability
operation. Once it succeeds, the only remaining production operation is one
`ItemStack.shrink(1)` on the already-validated stack.

Every failure returns a typed result with a zero award and current balance. It
does not shrink the stack or alter Research Points. A successful result carries
the canonical blueprint ID, full award, and new balance.

Recycling always consumes one physical item, including when the player is in
Creative mode. Creative research-cost bypass is a separate configured policy;
it cannot turn a recyclable stack into an infinite point source.

## Eligibility outcomes

The bounded result status distinguishes:

- invalid physical input or unavailable player data;
- unavailable, mismatched, stale, or otherwise ineligible policy;
- removed catalog content and administratively blocked content;
- disabled recycling and zero-value definitions;
- an unlearned blueprint when unlearned recycling is not jointly permitted;
- insufficient point-cap headroom;
- success.

These outcomes give the Phase 8 menu deterministic feedback without teaching
the client how to authorize a transaction. A preview remains informational;
the commit always re-resolves current policy.

## Progression invariants

Recycling does not change learned or discovered collections. Learned duplicates
remain learned, and permanent discovery survives item consumption. When both
coarse configuration and the datapack explicitly permit unlearned recycling,
the transaction does not invent a learned state.

Legacy unlock migration and a successful award use one progression publication,
which also rebuilds the Journal. A failed economic transaction only triggers a
publication if legacy migration independently repaired durable progression.

## Verification

Phase 7 adds focused coverage for:

- exact one-item consumption and complete point credit;
- exact-cap success and cap-overflow failure;
- default learned-duplicate and explicit unlearned behavior;
- permanent discovery and learned-state preservation;
- physical-stack rejection before policy resolution;
- unavailable, mismatched, stale, removed, blocked, disabled, zero-value, and
  non-duplicate policies;
- resolver exceptions and unavailable progression data;
- malformed and oversized blueprint IDs;
- typed result invariants;
- zero-count input rejection.

The release-artifact verifier requires the recycling service in the packaged
JAR. Full automated tests, clean build, publication readiness, dedicated-server
startup, and client startup remain the phase gates.

Final Phase 7 verification completed on August 24, 2026:

- all 109 automated tests passed, including the 7 new recycling tests;
- the clean build and publication-readiness checks passed;
- the packaged JAR passed required-class and metadata verification;
- the dedicated server reached `Done` and initialized 481 blueprints;
- the client reached the render loop and completed texture-atlas creation.

The development content set still contains a malformed `ccrp` language JSON
reported by both runtime smoke tests. That pre-existing third-party pack warning
does not originate in this mod and did not prevent either startup.

## Deferred to Phase 8 and later

- Research Bench block, item, menu, screen, and bounded container action;
- recycling-slot preview and localized outcome feedback;
- atomic Research Point/ingredient consumption and physical blueprint output;
- administrator progression inspection and reset commands.
