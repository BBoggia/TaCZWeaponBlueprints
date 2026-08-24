# Phase 2 Implementation

Date: 2026-08-24

Phase 2 makes blueprint progression authoritative and durable. Learned TaCZ recipe IDs now have one validated player-state implementation, one synchronization path, and a server-side crafting boundary that does not trust the gun-smithing screen.

## Player recipe state

The player capability now owns all mutation of learned recipes.

- Recipe IDs are normalized through `ResourceLocation` and invalid or oversized IDs are discarded.
- Add and remove operations report whether state actually changed.
- Callers receive a read-only learned-recipe set instead of the mutable backing collection.
- Full replacements and clears use explicit capability methods.
- NBT output is sorted, making equivalent player state serialize deterministically.
- Existing valid `Recipes` NBT remains compatible; invalid and duplicate legacy entries are cleaned while loading.
- The capability type is explicitly registered during the Forge mod event lifecycle.
- Providers invalidate their `LazyOptional` when the owning player capability is invalidated.

Death persistence now uses Forge's normal clone lifecycle: revive the old player's capabilities, copy serialized state to the new player, and invalidate the old capabilities in a `finally` block. The temporary static death cache and duplicate respawn restoration path were removed. Normal Forge capability serialization continues to cover logout and login persistence.

The server synchronizes learned recipes on login, respawn, dimension changes, and successful unlocks.

## Blueprint unlock behavior

Blueprint use remains server authoritative and now treats unlock as an atomic state transition.

- The item is consumed only when a previously unknown valid recipe is successfully added.
- Creative-mode players can unlock without consuming the blueprint.
- Reusing a known blueprint reports that the recipe is already known and does not consume the item.
- Invalid blueprints and missing player capability data fail without consuming the item.
- A successful unlock immediately sends the server's new learned-recipe snapshot to the player.

## Server-side crafting enforcement

Client filtering is an interface convenience, not a security boundary. Phase 2 adds a common mixin at `GunSmithTableMenu#doCraft`, the method reached by TaCZ's client craft packet.

When blueprints are enabled, the server cancels a craft unless the requesting player's capability contains the exact recipe ID. A missing capability fails closed. When blueprints are disabled in configuration, TaCZ crafting is left unchanged.

This closes the prior bypass where a modified or stale client could submit a locked TaCZ recipe ID directly even though the normal gun-smithing screen hid it.

## Network synchronization

The custom channel protocol is now version `2`.

- Player and blueprint packets copy immutable snapshots at construction time.
- Entries are encoded in stable sorted order.
- Decode counts and string lengths are bounded before allocation.
- Invalid learned recipe IDs are rejected during packet construction and decoding.
- Blueprint packets encode the catalog map key as the blueprint ID instead of reparsing a value field.
- Player and blueprint synchronization are centralized in `NetworkHandler` helpers.
- The unrelated TaCZ `ServerMessageSound` packet registration was removed from this mod's private channel.
- The obsolete fully commented `PlayerRecipeDataSyncPacket` source was removed.

## Administrative controls

The existing operator-only `/gg` commands are no longer no-ops.

- `/gg clearRecipes` clears the invoking player's learned recipes and synchronizes the empty state. It reports the number cleared and rejects non-player sources.
- `/gg reloadRecipes` rebuilds the blueprint catalog from the currently loaded TaCZ recipes and synchronizes the resulting catalog to every online player. It reports the resulting blueprint count.

The legacy command names are preserved for compatibility, while their messages now describe what they actually do.

## Automated tests

JUnit 5 support was added to the Gradle build. Seven Java 17 tests now cover:

- valid, invalid, and duplicate recipe IDs;
- read-only capability views;
- replacing state from the capability's own exposed view;
- deterministic NBT ordering and round-trip persistence;
- cleanup of invalid and duplicate legacy NBT entries;
- stable learned-recipe packet ordering;
- player packet count bounds;
- stable blueprint packet ordering and use of catalog map keys.

Result: 7 tests, 0 failures, 0 errors, 0 skipped.

## Runtime validation

- Java 17 `build`: successful, including reobfuscation and all seven tests.
- Dedicated server: reached `Done (1.487s)` and the catalog remained at 452 registered blueprints.
- Dedicated-server mixin log: `GunSmithTableMenuMixin` applied to TaCZ's `GunSmithTableMenu` with no injection error.
- Dedicated-server lifecycle log: `ModCapabilities` subscribed to the MOD bus with no capability registration error.
- Client: reached the normal render loop; OpenAL initialized and texture atlases were created.
- Client mixin log: both the common menu mixin and client screen mixin applied without injection errors.
- Client errors remain limited to the two pre-existing invalid Suffuse asset filenames documented in Phase 1.
- Both modified JSON resources parse successfully.

## Remaining hands-on acceptance checks

The automated and smoke environments do not include a controllable multiplayer player. Before a release candidate, perform one short in-game progression pass:

1. Confirm a locked recipe is absent from the gun-smithing table.
2. Unlock its blueprint in survival and confirm exactly one item is consumed.
3. Confirm the recipe appears and can be crafted.
4. Confirm a duplicate blueprint is not consumed.
5. Die and respawn, change dimension, and reconnect; confirm the recipe remains learned each time.
6. Repeat the unlock in creative and confirm the blueprint is retained.
7. Run `/gg clearRecipes` and confirm the recipe disappears when the table is reopened.
8. With a packet-testing client, submit a locked recipe ID and confirm the server produces no output or ingredient consumption.

The server-side `doCraft` guard is already compiled and runtime-applied; the final packet-submission check is listed because it requires an actual connected player and crafted client request.
