# Research Tree Navigation Phase 1

Date: 2026-08-25

Phase 1 implements the server-side presentation-data foundation accepted in
Phase 0. It does not change progression authority, the network payload, or the
current client renderer.

## Implemented outcome

- Added strict, reloadable `research_tree_groups` format 1 resources.
- Compiled immutable group order and per-blueprint rank placement into each
  research snapshot.
- Added seven stock TaCZ groups covering all 54 bundled TaCZ 1.1.8 weapons
  exactly once.
- Extended operator status, inspection, audit, and catalog export data.
- Preserved compatibility for datapacks with no group definitions.

## Resource contract

Resources load from:

```text
data/<namespace>/taczweaponblueprints/research_tree_groups/<path>.json
```

Each definition contains format, owning profile, fallback title, optional
translation key, member icon, stable order, and non-empty ordered ranks. Exact
membership is bounded and duplicate-free. A group icon must be one of its
members.

The loader caps each definition before JSON materialization, and the group codec
enforces rank and cumulative-member limits while decoding. It also rejects
unknown fields, unsupported versions, malformed text, oversized IDs, duplicate
members, empty ranks, and invalid icons. Snapshot compilation additionally
rejects missing profiles, duplicate
membership across groups in one profile, and authored ranks that fail to rise
along an effective exact prerequisite edge.

## Runtime and failure behavior

Group definitions publish atomically with profiles and rules. Any fatal group
error fails preparation before publication, leaving the last valid research
snapshot active. A group member missing from the current live TaCZ catalog is
non-fatal because optional content packs may be absent; diagnostics and export
surface the missing ID.

Blueprints omitted from authored groups remain valid. They are marked for
automatic fallback presentation, which later client synchronization and layout
phases will materialize without changing research rules.

## Default presentation data

The built-in profile now has ordered Pistols, SMGs, Shotguns, Rifles, Snipers,
Machine Guns, and Special Weapons groups. Their current ranks mirror the
existing prerequisite topology so Phase 1 introduces no balance change. The
weapon-quality review and any intentional progression rebalance remain Phase 6
work.

## Diagnostics and export

`/gg research status` reports authored group and member counts. `/gg research
inspect` reports an authored group, rank, and sibling order or identifies the
blueprint as automatic fallback. Group audits distinguish live fallback
blueprints from authored IDs missing in the installed catalog.

The research catalog export is now format 2. It contains group summaries,
missing members, and an authored or automatic-fallback presentation record for
each live blueprint.

## Compatibility boundary

The original three-map snapshot factory remains supported and produces no
groups. Existing profile/rule datapacks require no edits. No persisted player
data changes, and the network protocol is unchanged in this phase.

Phase 2 introduces disclosure-safe published group DTOs and sanitization.
Phase 3 will synchronize those published groups. Phase 4 will consume them in
the Branches and All Weapons client projections.

## Verification

Automated coverage includes strict codec failures, immutable construction,
snapshot indexing and ordering, duplicate and rank-conflict rejection, missing
catalog members, fallback auditing, format-2 export, default 54-weapon group
coverage, example-pack parsing, and failed-reload publication preservation.
