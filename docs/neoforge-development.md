# NeoForge development

The `neoforge-1.21.1` branch ports [TaCZ] Weapon Research & Blueprints to
Minecraft 1.21.1. The completed Forge 1.20.1 source remains on branch and tag
`v1.3.8`, commit `8748be8cfbadcb54a0fc7e047302a8339f549b3b`.

The current NeoForge candidate is a beta. The
full production source compiles. Player knowledge uses persistent NeoForge
attachments, networking uses direction-specific play payloads, and blueprint
items and crafted-gun provenance use Minecraft data components. The workbench
integration retains recipe-holder IDs and server-authoritative recipe access.
Research, Analyzer, villager trading, and Designer workflows have focused
client/server acceptance coverage. The optional recipe-viewer plugins are
checked with their pinned NeoForge runtimes. No production sources are
excluded to make a partial build pass.

## Pinned environment

| Component | Development version |
| --- | --- |
| Java | 21 |
| Gradle | 8.12, wrapper distribution verified by SHA-256 |
| ModDevGradle | 2.0.74 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.233 |
| Unofficial TaCZ port | 1.1.8-hotfix-r7; Modrinth project `OypNE65K`, version `62q6mB5Q` |
| Fzzy Config | 0.7.7+1.21+neoforge; Modrinth version `uG7oHgw6` |
| Kotlin for Forge, NeoForge variant | 5.12.0 |
| Optional EMI | 1.1.24+1.21.1, NeoForge |
| Optional JEI | 19.21.0.247, Minecraft 1.21.1 NeoForge |

Versions and artifact IDs are defined in `gradle.properties`. TaCZ's
[upstream build](https://github.com/MUKSC/TACZ-1.21.1/tree/c471821c771a62cc8850694bbea579c2ba0b9e3a)
provides the NeoForge, ModDevGradle, Gradle, and Java baseline. The
[ModDevGradle documentation](https://github.com/neoforged/ModDevGradle) explains
the development runs and additional source sets. Kotlin uses its
[1.20.6–1.21.8 integration](https://github.com/thedarkcolour/KotlinForForge/tree/5.x).

The TaCZ dependency refers to the unofficial NeoForge project, not the Forge
project with a similar version name. It already packages its support libraries.
JEI and EMI APIs are available for compilation, but neither viewer is installed
in normal development runs unless requested. TaCZ Magazines and the old Forge
structure-mod datagen dependencies are not enabled on this branch.

## Java and Gradle

Select JDK 21 as the IDE's Gradle JVM. The Java toolchain and compiler release
are both pinned to 21; no machine-specific JDK path is committed.

On macOS, select an installed JDK 21 for a command with:

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew --version
```

Resolve the dependencies, expand NeoForge metadata, and compile the setup probe:

```sh
./gradlew verifyPortDependencies processResources portBootstrapClasses verifyVersionState
```

Build output is isolated under `build/neoforge/`. Running `clean` on this branch
does not delete `build/releases/1.3.8/` or the Forge `build/libs/` artifacts.
Development versions have a `-dev` suffix; prepared beta versions use
`-neoforge-beta.<number>`. Archive names include `1.21.1-neoforge`.

## Dependency startup checks

The `portBootstrap` source set contains a small, separate mod that verifies the
required dependencies load. It has no addon gameplay, production resources, or
production mixins. It is never packaged in the main JAR. Launch it with:

```sh
./gradlew runPortBootstrapClient
./gradlew runPortBootstrapServer
```

These use `run/neoforge/bootstrap-client/` and
`run/neoforge/bootstrap-server/`. Server setup follows Minecraft's normal EULA
and server configuration requirements. Stop the server through its console.
Use disposable worlds for port development; do not open a Forge release world
in this environment.

The log marker `WRB_PORT_BOOTSTRAP load complete` confirms mod loading. A usable
client title screen (`WRB_PORT_BOOTSTRAP client title screen ready`) and
`WRB_PORT_BOOTSTRAP dedicated server ready` provide
separate client and server startup evidence. These checks do not establish
blueprint learning, crafting restrictions, research, or saved-data compatibility.
Add `-PportBootstrapAutoStop=true` to exit the probe cleanly after readiness.
For a fresh probe client, this option creates a minimal options file that skips
Minecraft's first-launch accessibility onboarding. Existing options are preserved.

For an optional recipe viewer, add one of:

```sh
./gradlew runPortBootstrapClient -PrecipeViewer=emi
./gradlew runPortBootstrapClient -PrecipeViewer=jei
```

The same option applies to ordinary addon runs. The default
is `none`; unsupported values fail configuration instead of silently falling back.

## Full addon and release validation

`compileJava`, `build`, `runClient`, `runServer`, and `runData` retain their normal
meaning and use `src/main/`. Their game directories are separated under
`run/neoforge/`. The complete addon is compiled by `compileJava`; the dependency probe alone
does not establish addon behavior.

The retained unit tests, authoring tools, and development harness use NeoForge
APIs and Java 21. Forge reobfuscation and SRG refmap checks do not apply to
NeoForge's runtime mappings. Plain JUnit tests inspect untransformed mixin
classes and domain contracts; the separate game fixtures exercise actual
mixin application. The JUnit bootstrap supplies an empty loader discovery list
for registry initialization. It does not pretend to load the addon into a game.

CI runs the full regression suite, checks the assembled production JAR,
compiles the isolated game fixtures, and packages the Analyzer and Research
Data examples. It uploads validation reports without publishing artifacts.
Installed-game certification requires the separate runtime matrix below.

## Packaged runtime matrix

Use JDK 21 and Python 3 with a graphical desktop, or Xvfb on Linux. Close any
previous verification games before staging a new artifact:

```sh
./gradlew verifyRegressionEvidence preparePackagedVerification
python3 scripts/verify-neoforge-runtime.py --suite all
./gradlew certifyReleaseCandidate
```

The runner installs the pinned official NeoForge loader under
`run/neoforge/production-server/` and `production-client/`. It uses the built
addon JAR, a separate verification-mod JAR, and pinned dependencies. The
verification mod checks the actual loaded addon file's SHA-256. No loose
production classes are used. All fixture classes are excluded from the addon.

The local server listens on `127.0.0.1:25594` in offline mode. The runner accepts
the Minecraft EULA for these disposable installations and creates its own
flat world. It controls `WRBCoreQA` and, where required, `WRBPeerQA` in a separate
`production-peer/` client. These installations contain test inventories,
progression, blocks, and villagers. Never use their verification mod with a
personal world. The runner does not use launcher credentials or personal saves.
It can reuse a complete Gradle asset cache read-only; `WRB_ASSETS_DIR` selects
another complete cache. An empty cache location makes it download assets into
the disposable client.

| Suite | Coverage |
| --- | --- |
| `core` | Physical learning, denied and allowed native crafts, ingredients, provenance, bench tiers, disconnect/reconnect |
| `features` | Research, Analyzer, villagers, Designer, and screens without an optional viewer |
| `review-regressions` | Merchant pricing and payment, Designer round trips, and screen rendering |
| `lifecycle` | Two simultaneous players with different knowledge, unauthorized crafting, native death/respawn, dimension travel, successful and failed reloads, recovery, and saved restart fixtures |
| `restart` | A new server process loads both players' progression and inventories, a used gunsmith offer, and the workstation; both clients verify their own recipe access |
| `integrated` | The core learning/crafting flow in a singleplayer integrated server using a copy of the disposable server world |
| `emi`, `jei` | Feature coverage with each pinned viewer separately, including production information-page registration |

Use `--suite <name>` to investigate one environment. Run `lifecycle` immediately
before `restart`; run `core` before `integrated` to provide the disposable world.
The runner creates a fresh world for each independent suite and resets the
addon's settings in the disposable profiles, so a prior suite's temporary
workbench or award settings cannot affect the next run. Restart retains its
saved world and inventories. The runner stops its child processes after each suite. A process exiting with
code zero is insufficient: fresh successful server and client reports are
required, including the second player's report where applicable. Starting a
rerun invalidates that suite's old success even if the new run fails.

Reports live under `build/neoforge/reports/`. The matrix binds results to the
addon JAR, fixture JAR, runner, dependencies, Minecraft, and NeoForge. Changing
one of those inputs requires fresh evidence. Optional viewers are staged
separately and never added to the dedicated server by this matrix.

`verifyReleaseArtifact` checks all compiled production classes and processed
resources against the JAR, Java 21 bytecode, NeoForge metadata, mixin entries,
resource formats, and exclusion of development classes. `verifyRegressionEvidence`
rejects excluded, disabled, skipped, or failed tests and filtered test runs.
Up-to-date and cached test results are accepted only after Gradle validates the
test task's current inputs and outputs; leftover XML alone is insufficient.
`verifyPackagedRuntimeEvidence`
rejects stale or incomplete game reports. `certifyReleaseCandidate` combines
these technical gates and writes `reports/release/candidate.json`.

Starting a release gate clears earlier release success reports before any
prerequisite runs, so a failed compile, test, or runtime check cannot leave an
old certificate looking current. This does not erase the runtime matrix itself.
`./gradlew testReleaseGates` exercises these rules in isolated Gradle builds,
including successful, cached, filtered, excluded, and failed runs. Both `check`
and `certifyReleaseCandidate` include these build-tool regressions.

`verifyPublicationReadiness` additionally requires a release version, matching
release notes and changelog, and NeoForge requirements in the mod-page copy.
It intentionally rejects `-dev` versions. Technical certification
does not publish the mod, choose a release version, or certify migration of
Forge 1.20.1 worlds to NeoForge 1.21.1.

## Core learning and crafting verification

The `portVerification` source set loads alongside the real addon and pinned
TaCZ dependency. It is excluded from the production JAR. It exercises a real
client connection to an isolated server: physical blueprint use, server-denied
crafts, native recipe-screen filtering, ingredient consumption, crafted-gun
metadata, attachment save/load and death copying, and disconnect/reconnect.
The native TaCZ table is checked at its default tier and again after the
fixture applies the supported external-workstation tier setting in memory.

Compile once before launching the two processes, so concurrent Gradle builds
do not write to the same compiler output:

```sh
./gradlew compileJava portVerificationClasses processResources verifyVersionState
```

Create `run/neoforge/verification-server/server.properties` with these settings
for the disposable fixture:

```properties
server-ip=127.0.0.1
server-port=25594
online-mode=false
level-name=core-flow-world
level-type=minecraft:flat
view-distance=3
simulation-distance=3
spawn-protection=0
```

Accept Minecraft's EULA in that isolated server directory. For an unattended
client run, create `run/neoforge/verification-client/options.txt` with
`onboardAccessibility:false` and `pauseOnLostFocus:false` on separate lines.
Run these in separate terminals, starting the client after the server logs
`WRB_CORE waiting for QA client`:

```sh
./gradlew runVerifyCoreServer
./gradlew runVerifyCoreClient
```

The directories are `run/neoforge/verification-server/` and
`run/neoforge/verification-client/`. The server must bind to `127.0.0.1:25594`
in offline mode, with a disposable flat world. The test player is `WRBCoreQA`.
The fixture changes that player's inventory and knowledge, and places blocks
near `(0, 80, 0)`; never point it at a personal world. Both processes stop after
completion. Each writes `core-verification.json`; both reports must say
`"passed": true`. Each run removes its previous report before launch, so a
missing report is a failure too. A Gradle launch exiting successfully alone is insufficient,
because Minecraft can report a startup failure and return a zero process code.

For startup-only checks that load the full addon, use `runCoreServer` and
`runCoreClient`. These use separate `core-server` and `core-client` directories
and stop at server readiness or the client title screen.

Saved knowledge has been tested within new NeoForge worlds. This does not
establish migration of Forge 1.20.1 worlds or their `ForgeCaps` data into 1.21.1.
The preserved Forge branch and release artifacts remain separate.

## Port regression checks

The verification fixture also has a focused mode for merchant payment,
Designer capture/export, and screen rendering. It uses the same isolated
directories and `core-verification.json` reports as the core flow. Compile the
fixture first, then run the server check to completion before the client check:

```sh
./gradlew compileJava portVerificationClasses
./gradlew runVerifyCoreServer -PportReviewChecks=true
./gradlew runVerifyCoreClient -PportReviewChecks=true
```

Both reports must say `"passed": true` and `"suite": "review-regressions"`.
The server exercises discounted owned offers, vanilla discounts, save/network
round trips, and normal/reversed payment slots through a real merchant menu.
It also verifies missing-item offers with implicit counts remain suspended and
retain their raw items, components, bindings, and spent uses through copies
and repeated saves.
It then captures the installed TaCZ catalog, verifies large recipe-batch
quantities, and exports and reimports an editable project. Generated files
use the `review-` prefix in the verification server directory; they are not
installed into the world.

The client opens the Journal, Designer chooser, captured project, editing and
properties panels, and export/import screens. It checks background-render
counts, Designer library scrolling on both axes, and the prepared export's
compatibility label, and saves screenshots
as `review-<screen>.png` in the verification client directory for visual
inspection. These checks cover the port regressions; they do not replace
full gameplay and release validation.

The packaged `features` suite checks horizontal, vertical, and diagonal input
in the Blueprint Analyzer's scrollable details pane during its existing
client/server transaction flow. Run these targeted game checks with
`python3 scripts/verify-neoforge-runtime.py --suite review-regressions` and
`python3 scripts/verify-neoforge-runtime.py --suite features` after
`./gradlew preparePackagedVerification`. Partial runs do not certify the full matrix.


## Remaining-feature acceptance

The feature mode uses the same disposable server, client, and reports as the
core flow. Compile once, launch the server, then connect the client when the
server logs its ready marker:

```sh
./gradlew compileJava portVerificationClasses authoringClasses
./gradlew runVerifyCoreServer -PportFeatureChecks=true
./gradlew runVerifyCoreClient -PportFeatureChecks=true
```

Both reports must say `"passed": true` and `"suite": "features"`. Do not combine
feature and regression modes. This mode also places temporary stations and
villagers around `(0, 85, 0)`, changes the fixture player's operator status,
and changes research settings in memory. It is intended only for the disposable
verification world.

Coverage includes all nine Analyzer station/weapon tier combinations, occupied
outputs, Research Data analysis and legacy redemption, protected-fragment
reconstruction, duplicate recycling, loaded-ammunition recovery, replay guards,
and stored-magazine protection while the optional addon is absent. Villager
checks exercise native profession acquisition, all five career levels, stock
save/load, restocking, and an actual client purchase. Research checks cover
server-synchronized weapons, ammo and attachment domains, path quotes and
payment, and visible canvas pixels. Designer checks exercise operator capture
through the chunked network protocol, compilation/export/import, revoked access,
and a direct unauthorized server request.

For each optional viewer, start a fresh feature server and add
`-PrecipeViewer=emi` or `-PrecipeViewer=jei` to the client command. Runtime probes
inspect the information pages registered by the production plugins. EMI checks
also cover blueprint component identity and the Analyzer overlay exclusion.
Screenshots are saved as `feature-*.png` in the client directory for inspection.
These development runs do not certify an unmodified production JAR.

## Pack-author tools

Example datapacks under `examples/` declare format 48 and use common `c:` item
tags for Minecraft 1.21.1. Packaging does not activate them in a world:

```sh
./gradlew packageAnalyzerDatapacks packageResearchEconomyDatapacks
./gradlew generateAnalyzerBalanceReport -PanalyzerCatalog=/path/to/research-catalog.json
./gradlew generateResearchTechTreeRatings -PresearchGunPack=/path/to/unpacked-gun-pack
```

The standalone gun-pack reader accepts both the 1.21 `recipe/gun` directory
and the older `recipes/gun` directory. Its default source is
`run/neoforge/client/tacz/tacz_default_gun`. It does not convert an old pack for
use in a new game. The existing reference-generation and comparison commands
are also restored; supply explicit output paths when producing experimental
references. Authoring-only runtime libraries are separate from the game loader's
TaCZ libraries to avoid duplicate modules.
