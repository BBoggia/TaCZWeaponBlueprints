# Research economy release handoff

The Scrap and Research Data implementation is ready for pack playtesting. This
document records the development candidate, its compatibility requirements,
and the remaining production-install checks. It does not select a release
version or certify publication. Follow the [release checklist](release-checklist.md)
when preparing a release.

## Tested development candidate

| Property | Recorded value |
| --- | --- |
| Version | `1.3.1-dev` |
| Artifact | `build/libs/taczweaponblueprints-1.3.1-dev.jar` |
| SHA-256 | `54e3d4144f108b0587a979b6fc7ed23754709de79baf2c0e830068bf8df2b5cc` |
| Network protocol | 63; matching client and server builds required |
| Player data / server config | 5 / 4 |
| Research catalog export | 22, with optional `research_data_economy` format 1 |
| Data / shared reconstruction resources | Format 1 |
| Weapon Analyzer policy | Bundled format 5 tiered profile; explicit legacy profiles remain supported |
| Tree / placement | Formats 1–2 remain readable; authored branches use format 3 |

The current candidate passes 2,394 automated tests with no failures, errors, or
skips, including the standard full suite, packaged artifact checks, and both
sets of example datapacks. The Analyzer screen now uses a horizontal work area,
a separate cost/action strip, and optional Details. Regression coverage includes
selected-action costs, omitted impossible outcomes, aggregate Research Data odds,
fragment accounting, and slot bounds.

The native Analyzer verification uses compiled main-source outputs in Forge's
development environment and a marked disposable world. It covers all three
station tiers, Research Data, weapon analysis/recovery, blueprint recycling,
fragment deposits and reconstruction, output blockage, and legacy redemption
with physical Scrap or banked RP. The run passes 120 assertions with 17 captured
views at logical viewports 427×318 and 366×273. It checks native action requests and result receipts,
non-consuming mode selection, recovery confirmation, and hiding/restoring slots
behind Details. The 320×240 panel fits both tested GUI scales. This is separate
from testing the unmodified production JAR in a normal Forge installation.

## Current multiplayer verification

The current candidate also passes dedicated-server checks with two independent
Minecraft clients. Gameplay passes 70 server assertions, including a shared
Research Bench purchase of a six-node path, exact Scrap payment, per-player
knowledge and funds, retained-menu ownership, stale/replayed Analyzer requests,
Details visibility, and action/result packets. Full process restart and
version-4 player-data loading pass another 9 and 11 server assertions. Both
clients complete each lifecycle phase without login retries.

A separate tiered Analyzer run with explicit banked RP and legacy Data
redemption passes 99 server assertions and 60 client assertions across 20
captured cases. It covers all nine station/weapon tier pairings, full and
partial results, reconstruction, known targets, occupied output, insufficient
RP/materials, ordinary fragments, duplicate blueprints, recovery confirmation,
and reconnect persistence. The tiered datapack's contents match the current
example files.

These runs load the packaged candidate adapted to Forge's development mappings,
with main source outputs excluded. The adapted JAR SHA-256 is
`978a44752caa6966e4efbe789cb4ac522599f7c4bd55b834d43313fea66376de`;
all non-class resources remain byte-identical. Research Bench purchases use
real server menus and inventories under currency-only costs; Analyzer actions
cross real client connections. The runs use localhost on macOS ARM64 and the
base TaCZ catalog, with the installed development dependencies. They do not
measure WAN latency, high player counts, arbitrary gun packs, or an untouched
production Forge installation.

The successful dedicated-server logs contain no ERROR or FATAL entries.
Client logs contain EMI errors for its synthetic vanilla brewing recipes;
no mod transaction failure accompanied those messages. Netty capability
fallback diagnostics and unavailable Realms authentication also appear in
these offline development clients. Optional-viewer compatibility still needs
the intended production pack check below.

## Earlier focused currency regression baseline

Before the Analyzer redesign, candidate
`adde2982ae7a663938245ea10eeb7311f20c215002aaa818fca80e5ec8cf5728`
passed 2,385 tests and a focused native check: 53 assertions and six Analyzer
views verified invalid-item startup, partial/complete sets, empty inventory,
unavailable targets, and currency changes with the same menu open. A player
with 137 saved RP correctly saw 12 Scrap, 137 RP, or zero Scrap according to mode
and inventory. The adapted JAR SHA-256 was
`51e04caaaa32e03dae3d293f5921789077093e9212dc1763d29bef10164c6cd1`;
all non-class resources remained byte-identical. That earlier run's rendered
sidebar evidence does not validate the redesigned screen.

## Earlier multiplayer acceptance baseline

The broader dedicated-server acceptance run used an earlier candidate with
SHA-256 `3b34243652cc90d746dcb91a628ba5d1c2fe23b3d2e4f7395c41a3f09faf52b5`.
It passed 2,375 automated tests at that time. Two real clients covered one-item
Data analysis through normal screen requests, output rejection, Scrap trading,
banked redemption, withdrawal confirmation/retries, reloads, menu closure,
death/respawn, reconnect, restart, and version-4 player-data loading. The three
server phases passed 53, 9, and 11 assertions respectively. The respawn test
verified saved progression survives an invalidated capability handle.

That native run loaded the packaged candidate adapted to Forge's development
mappings, with original source outputs excluded. Its adapted JAR SHA-256 was
`219a4b97453a250774727cab6d791838549814b8f860d009a2163edb636c8836`;
all non-class resources remained byte-identical. This is not a test of an
untouched production installation or a repeat of the full multiplayer matrix
on the current candidate. The [validation guide](research-economy-validation.md)
describes how to reproduce acceptance without local agent notes.

Each identity applies only to its recorded artifact. A release version,
resource edit, dependency change, or other candidate change requires a new
digest and appropriate validation. Keep raw evidence with the artifact;
automated reports marked `requires_runtime_matrix` or `requires_manual_qa`
retain that status until their own runtime matrix is completed. Repeat the
relevant native acceptance and production checks with the final release build.

## Runtime and dependency requirements

| Component | Declared compatibility | Native reference |
| --- | --- | --- |
| Minecraft | `[1.20.1,1.20.2)` | 1.20.1 |
| Forge / FML | `[47,48)` | 47.3.0 |
| Timeless and Classics Zero (TaCZ) | `[1.1.8,1.2)` | 1.1.8-hotfix |
| Fzzy Config, Forge build | `[0.7.6,0.8)` | 0.7.6+1.20.1 |
| Kotlin for Forge, required by Fzzy Config | `[4.11.0,4.99.0]` in Fzzy 0.7.6 metadata | 4.11.0 |
| Java | JDK 17 for development and validation | 17.0.12 |
| Optional JEI / EMI | Client metadata: `[15,)` / `[1.1,)` | Build API references: 15.12.2.50 / 1.1.24+1.20.1 |

The mod and required dependencies belong on both sides. Optional viewer API
versions do not prove every later viewer release works. The native economy run
used macOS ARM64 and the base TaCZ catalog: 172 recipe-backed entries, including
53 weapons. Other installed gun packs need their own gameplay checks.

Version, name, description, license, and dependency ranges come from
`gradle.properties`, `build.gradle`, and packaged `META-INF/mods.toml`.
The proprietary notice matches `All Rights Reserved`. The current description
remains an accurate general summary; no version or metadata change is required
for this documentation handoff.

## Configuration handoff

The [complete mode matrix](research-currency-configuration.md#choose-the-economy)
covers Scrap/analysis, RP/analysis, Scrap/legacy redemption, RP/legacy redemption,
and disabled Data under either currency. The first combination is the default.
Explicit saved selectors win; missing selectors adopt the new defaults.

Before reopening an established server, follow [migration](research-currency-migration.md)
to select the intended economy. Saved RP remains banked and is never converted
on login. Operators can preview a bounded 1:1 withdrawal to the selected item;
the full requested amount must fit without drops. Existing currency items do
not transform when the configured item changes. Knowledge, archives, and reward
claims survive mode changes.

Pack authors should retain their final Blueprint configuration, datapack ZIPs,
active profile, and `/gg research export` together. Use the
[Data authoring guide](research-data-policies.md) for pools and reconstruction,
the [supply report](research-economy-balance.md) for pacing, and
[currency integrations](research-currency-integrations.md) for API migration.

## Production-install playtest checklist

Use a disposable copy of the intended pack/world and the exact unmodified
candidate JAR in a normal Forge installation. Record tester, date, versions,
datapacks, mode, actual result, and screenshots or logs for each relevant row.
These checkboxes are pending production playtests, not the automated results above.

- [ ] Install matching client/server builds with the intended gun packs. Open
  the Journal, Research Bench, and all three Analyzers. Check startup/reload logs
  and optional JEI/EMI both present and absent where supported by the pack.
- [ ] On a fresh survival player, obtain Scrap from an early chest and a player
  kill, buy a single node and a whole route, then craft the learned weapon.
  Record how much exploration and carrying space that progression required.
- [ ] Compare all nine Data-item/station combinations against the actual
  preview. Notes target T1, Reports T1–2, and Dossiers T1–3 under defaults.
  Clear Output between attempts and confirm each click consumes one item.
- [ ] Archive and reconstruct matching analysis fragments, including a target
  already learned and fragments traded from another player. Verify ordinary
  loot-fragment discounts remain separate and protected outputs cannot recycle.
- [ ] Exercise every intended currency/Data combination from the mode matrix,
  including disabled Data and an alternate currency item if used. Check carried
  balance freshness in the Bench and Journal after moving or trading items.
- [ ] Disable Scrap loot, Data loot, and gameplay awards independently. Confirm
  only the selected source stops. Test `ITEMS_ONLY`, `POINTS_ONLY`, and combined
  Bench costs; Analyzer prices remain their own policy.
- [ ] Test full inventory, split currency stacks, an item serving as currency
  and a material, incompatible Output, and Offhand-only currency. Failed manual
  actions must not consume input or drop partial rewards. Event awards follow
  their separate bounded overflow rules.
- [ ] With learned targets excluded, compare fresh, partly learned, and
  exhausted pools. Exhausted pools preserve Input. Re-enable duplicates and
  confirm processing resumes. Remove and restore a target pack in the copy;
  archived counts must survive and missing targets must not be substituted.
- [ ] Keep screens open through settings edits, `/reload`, and station removal
  or replacement. Old requests must not spend against a stale quote. Close with
  unclaimed contents, including a full inventory, and recover each item once.
- [ ] Inspect an old RP balance, retain legacy settings, then switch explicitly
  and preview a partial-bank withdrawal. Confirm exactly the requested amount,
  retry its token, and test insufficient inventory space. Relog/restart and
  confirm retained RP, physical items, knowledge, and claims.
- [ ] Die and respawn, trade dropped Scrap, reconnect, change dimensions, and
  restart the server. Compare physical inventory and saved knowledge/archives.
  Use the pack's intended `keepInventory` behavior when evaluating death drops.
- [ ] Play through a representative stretch of survival progression with the
  intended catalog, alone and with shared resources. Review duplicate utility,
  workstation costs, Scrap carrying pressure, and late-tier fragment pacing.
  Review any custom recipes or recyclable outputs for unintended currency loops.

## Remaining limits

Supply and Data pacing have modeled estimates, not a completed survival
campaign. A 4,096-weapon fixture checks bounded pool work; it does not validate
every third-party pack. Native two-client acceptance does not certify login
stress; the local gameplay run needed one reported client login retry.

Payments use main inventory/hotbar only. There are no wallets, automatic deposits,
nearby-container payments, persistent Analyzer inventories, or bulk random Data
actions. Ordinary Minecraft saves do not provide a single crash-atomic commit
across player files and world item entities; back up the world and player data
together for administrative migration. These limits remain documented behavior,
not unfinished implementation milestones.
