# Journal and Research Phase 5: Journal Data Plane

## Scope

Phase 5 implements the server-authored Blueprint Journal model and its atomic
client synchronization. It supplies the safe data plane for the later Journal
screen without giving the client authority over discovery, learning, research,
recycling, costs, or crafting.

This phase does not register a Journal item or key binding, draw a screen, or
add recycling and Research Bench transactions.

## Disclosure tiers

The server converts each current catalog entry into a view model only after
resolving the active Phase 3 datapack policy and applying the Phase 4 runtime
configuration. The synchronized shapes are deliberately different:

- `HIDDEN`: no entry is transmitted and it contributes nothing to the visible
  completion summary;
- `SILHOUETTE`: only a contiguous anonymous ordinal is transmitted;
- `NAME`: the ordinal and translation key are transmitted, without blueprint
  identity, category, icon source, discovery state, or policy details;
- `PREVIEW` and `FULL`: the blueprint ID, translation key, item type, display
  slot, visible progression flags, and bounded policy summary are transmitted.

Learned entries become `FULL` when their Journal policy is enabled. A lower
configured visibility ceiling can reduce an unlearned entry to `NAME`,
`SILHOUETTE`, or `HIDDEN` before encoding.

Exact ingredient alternatives, item-tag IDs and contents, prerequisite IDs,
selected rule IDs, and selector internals remain server-only. The Journal sends
only point cost, ingredient-type count, prerequisite count, recycling value,
and current eligibility booleans where preview disclosure is allowed. This both
limits information disclosure and keeps a valid maximum-size datapack within
the bounded packet design.

These tiers are a presentation contract, not an anti-cheat secrecy boundary.
The existing crafting and blueprint-item systems already synchronize the active
TaCZ catalog to clients. Phase 5 ensures that the Journal API and future screen
cannot accidentally render fields forbidden by the selected visibility tier.

## Snapshot contents

`BlueprintJournalBuilder` iterates the immutable catalog in ascending blueprint
ID order and publishes:

- contiguous disclosure-filtered entries;
- visible learned, discovered, and researchable counts;
- current Research Points and configured point cap;
- sorted unavailable history for discovered or learned IDs whose content packs
  are no longer in the active catalog.

Unavailable history carries only the normalized resource ID and whether it was
learned. It does not invent names, icons, categories, costs, or pack metadata.
Global blueprint or Journal disablement publishes an empty Journal without
deleting player progression.

The snapshot constructor revalidates entry limits, contiguous ordinals,
disclosed-ID uniqueness, sorted unique history, point bounds, and summary
bounds. Client code sees only a complete immutable publication through
`ClientBlueprintJournal`.

## Network protocol 5

Phase 5 intentionally bumps the mod protocol from `4` to `5` and registers one
client-bound `SyncBlueprintJournalPacket` family. Old clients cannot interpret
the new disclosure-filtered state and are rejected by the normal exact-version
handshake.

Journal packets:

- use the shared random, monotonic per-server synchronization sequence;
- are split below the 900,000-byte chunk budget;
- permit at most 16 chunks and 4,096 entries/history records;
- repeat and validate bounded summary metadata on every chunk;
- accept out-of-order chunks;
- replace an incomplete older synchronization when a new sync ID arrives;
- tolerate duplicate chunk delivery without premature publication;
- assemble and validate the complete snapshot before one volatile client swap.

The estimator reserves more bytes than the maximum fixed wire fields require,
and the encoder independently checks actual bytes written.

## Lifecycle refreshes

The Journal is synchronized after the existing progression snapshot, so every
path that already synchronizes learning, discovery, or Research Points also
refreshes the Journal:

- player login;
- respawn and dimension transition;
- learning or clearing blueprints;
- coalesced inventory discovery;
- catalog rebuild and `/gg reloadRecipes`;
- server-wide datapack reload.

If catalog rebuild fails during datapack synchronization, the last-known-good
catalog is retained and the new research policy still republishes Journals
against it. A permitted server config edit atomically publishes the Phase 4
configuration and refreshes all connected players. Client logout clears the
Journal so one server's disclosed data cannot remain visible in another
session.

## Verification

Phase 5 adds automated coverage for:

- deterministic `SILHOUETTE`, `PREVIEW`, and `FULL` construction;
- `NAME` ceiling identity and policy redaction;
- hidden/disabled and missing-capability behavior;
- unavailable-history ordering and learned state;
- constructor rejection of tier metadata leaks;
- maximum-size multi-chunk encoding and byte budgets;
- round-trip decoding and reverse-order atomic assembly;
- incomplete-sync replacement and duplicate chunks;
- packaged presence of the builder, model, client publication, and packet.

The completed Phase 5 tree passes 97 automated tests. The dedicated server
registered protocol 5, applied research revision 1, rebuilt 481 catalog entries,
and reached `Done`. The client registered the client-only logout subscriber,
applied both gunsmith mixins, created the normal texture atlases, and reached
the render loop. The remaining malformed third-party content-pack and Realms
authentication messages are unchanged from earlier phases.

## Deferred to later phases

- the Journal item/key binding and screen UI;
- client search, filters, categories, pagination, and completion presentation;
- manual duplicate-recycling transactions;
- Research Bench registration and atomic research commits;
- administrator progression inspection/reset commands.
