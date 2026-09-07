# Research economy validation

Changes to physical currency or Research Data need both automated transaction
coverage and native lifecycle checks. Run JDK 17 with `./gradlew check
packageResearchEconomyDatapacks`. The checks validate the packaged artifact,
example datapacks, compatibility contracts, rollback behavior, and a
4,096-weapon pool. The large-pool case checks eligibility visits and random
draw bounds without imposing a machine-dependent timing threshold.

For the recorded candidate, dependency matrix, and remaining production-install
checks, use the [release handoff](research-economy-release-handoff.md).

## Dedicated acceptance

The opt-in development probes `ResearchEconomyServerProbe` and
`ResearchEconomyClientProbe` use two real Minecraft clients named Alice and
Bob, a dedicated server bound to `127.0.0.1:25578`, and a disposable world.
The server refuses to run without a `.research-economy-qa` file in that world's
root. Never add this marker to an ordinary player world.

Use separate working directories for the server and both clients. Supply an
existing accepted EULA file for the disposable server. The harness does not
accept the EULA. The directory passed as `researchEconomyQA` contains
`server/`, `Alice/`, and `Bob/`; evidence files are written alongside them.
Archive a previous run before repeating its phase. Existing phase files cause
the server to refuse the run instead of reusing stale acknowledgements.

The native runtime must load an extracted candidate artifact adapted to the
development runtime's Minecraft mappings. Set `researchEconomyArtifact` to
that directory. Adapt the reobfuscated candidate with ForgeAutoRenamingTool,
reverse the build's MCP-to-SRG mapping, and provide the Minecraft and mod
dependency jars for inheritance resolution. Record the original and adapted
jar hashes and verify that all non-class resources remain byte-identical.
The run replaces the main source outputs with the adapted artifact and checks
the runtime origin of the Analyzer menu class. Acceptance probes remain in the
development source set and must be absent from the distributable jar.

Launch `runServer` and two `runClient` processes with these properties:

```text
-PresearchEconomyQA=<disposable-root>
-PresearchEconomyArtifact=<extracted-adapted-candidate>
-PresearchEconomyRole=Alice
-PresearchEconomyPhase=gameplay
```

Use `researchEconomyRole=Bob` for the second client. The optional
`researchEconomyExportLaunch=<file.json>` exports a resolved Java command,
working directory, and `MOD_CLASSES` setting without launching Minecraft.
This lets a local runner launch all three processes after Gradle preparation.
Do not include main source output directories when replaying the commands.

The gameplay phase checks both clients' normal Analyzer button requests,
authoritative previews, result packets, and completed screenshot writes. It
also opens Details, checks that its covered slots are inactive, closes Details
with Escape without closing the station, and commits an action from Details.
Server-driven scenarios then exercise the live player inventories, item
entities, menus, commands, and capability storage:

- One-item Research Data consumption and output-slot rejection.
- Cross-player menu ownership, stale input counts/tokens, and replayed request
  sequence rejection without inventory or progression changes.
- Separate learned-target filters and restoration of the allow-learned mode.
- Exact RP withdrawal, rejected retries, operator confirmation, and full
  inventory refusal.
- Scrap drop and pickup between the two players.
- Explicit banked-mode Research Data redemption.
- Resource reload invalidation and recovery, closed-menu request rejection,
  and return/drop of unconsumed input.
- Death, respawn, disconnect/reconnect, and physical item conservation.

Add `-PresearchEconomyWorkbenches=true` to the gameplay server launch to include
shared Research Bench checks. These use actual server menus and inventories;
the purchases are invoked by the server probe, not client clicks. The fixture
uses a T3 station, the base TaCZ AK-47 route, currency-only research costs, and
disabled discovery awards to measure exact payment. Two players share the
station with separate funds. The test checks a multi-node purchase, unchanged
progression for the other player, rejected repeats, menu ownership, closure,
and distance. Fixture-only settings are restored before Analyzer acceptance.
Selections and purchases run on separate ticks to respect planning limits.
When replaying an exported Java command, use
`-Dtacz.economyQA.workbenches=true` instead of the Gradle property.

Run `researchEconomyPhase=restart` with new server and client processes against
the same disposable world. Then run `researchEconomyPhase=upgrade`; this phase
changes only the disposable saved players to the version-4 capability shape
before login and checks the normal loader's version-5 result. Both phases
compare saved inventory NBT and progression against the gameplay snapshots.

Require a passing server report and both client reports for each phase, no
corresponding error files, and successfully decoded PNGs for gameplay. Record
the artifact hashes, runtime versions, catalog size, test results, and any
remaining limitations with the acceptance evidence.

This is native execution of an artifact adapted for Forge's development
runtime. It does not certify an untouched production installation, every
third-party gun pack, or long-term survival balance. Keep those distinctions
in release evidence. The separate [economy report](research-economy-balance.md)
models configurable supply and Research Data outcomes; it is not a survival
playthrough.
