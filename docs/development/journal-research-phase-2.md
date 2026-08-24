# Journal and Research Phase 2: Blueprint Discovery

Date: 2026-08-24

## Scope

Phase 2 connects physical blueprint items to the versioned discovery state
implemented in Phase 1. Discovery is server-authoritative, permanent,
idempotent, and synchronized only when state changes.

This phase does not add the Journal screen, visibility rules, duplicate
recycling, research definitions, or the Research Bench.

## Discovery lifecycle

A blueprint is now discovered through two complementary server paths:

1. Forge's post-insertion `PlayerEvent.ItemPickupEvent` handles successful
   ground-item pickups immediately.
2. `BlueprintItem.inventoryTick` provides a fallback for commands, container
   transfers, starting inventory, and third-party insertion paths.

Forge fires the selected pickup event only after an item was accepted into the
player inventory. Cancelled or failed pickup attempts do not discover it.
Generating a blueprint in a chest or other loot container does not run either
player path, so unopened loot remains undiscovered.

Successful blueprint learning continues to discover the output atomically
through the Phase 1 learned-is-discovered invariant, even if automatic
inventory discovery is disabled.

## Validation and outcomes

`BlueprintDiscoveryService` centralizes the transaction. A newly encountered
item must:

- be the add-on's actual `BlueprintItem`;
- contain an ID present in the current authoritative server catalog;
- have player capability data available;
- fit within the 4,096-entry discovery limit;
- pass the synchronized discovery-tracking toggle.

The service reports explicit outcomes for newly discovered, already
discovered, disabled, non-blueprint, invalid, capacity-limited, and
data-unavailable cases. Only `DISCOVERED` mutates state or sends a packet.

Already-discovered IDs are checked before current catalog validity. This keeps
removed content-pack IDs as permanent history and prevents pack removal from
rewriting player progression.

Discovery never learns a recipe, spends or awards Research Points, consumes an
item, or changes duplicate status.

## Configuration

The synchronized Fzzy Config blueprint section now includes:

```text
enableDiscoveryTracking = true
```

Operators with the existing level-2 config permission can disable automatic
inventory discovery. Disabling it does not remove discoveries and does not
break the invariant that learning a blueprint also discovers it.

## Synchronization and fallback cost

`NetworkHandler.syncPlayerProgressionData` sends only the Phase 1 progression
snapshot. A first discovery no longer rebuilds or resends the active TaCZ
recipe filter or presentation catalog.

The inventory fallback runs only on the logical server. Once an ID is already
discovered, direct canonical-string membership avoids reparsing a
`ResourceLocation`, catalog lookup, collection mutation, and synchronization.
This makes the recurring known-blueprint path allocation-free inside the
progression model.

The network protocol remains `4`; Phase 2 changes when the existing
progression packet is sent, not its wire format.

## Verification

The clean Phase 2 validation completed successfully:

- 65 automated tests;
- 0 failures, errors, or skipped tests;
- `clean build`;
- `verifyReleaseArtifact`;
- `verifyPublicationReadiness`;
- `git diff --check`;
- generated Fzzy Config contains `enableDiscoveryTracking = true`;
- dedicated server reached `Done (1.426s)` with TaCZ 1.1.8-hotfix;
- client initialized OpenGL 4.1, OpenAL, texture atlases, and the gun-smithing
  screen mixin.

Focused discovery tests cover first discovery, repeat idempotency, disabled
tracking, missing capability data, invalid and unavailable IDs, retained
history, capacity failure, and isolation from learned state and Research
Points.

The installed CCRP and Suffuse packs continue to report their documented
malformed language, sound-path, and unresolved-content errors. No Phase 2
event-registration, config, packet, or item-tick error appeared.

## Deferred to later phases

Phase 2 intentionally does not implement:

- the Blueprint Journal screen and entry presentation;
- Journal visibility policy or completion calculations;
- discovery toast/chat notifications;
- duplicate recycling and Research Point rewards;
- research profiles, rules, costs, or prerequisites;
- the Research Bench block and transaction UI;
- administrator progression reset and inspection commands.
