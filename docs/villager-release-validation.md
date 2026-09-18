# Villager release validation

Use this matrix alongside the [release checklist](release-checklist.md) when
changing villager stock, native trading, job-site acquisition, or purchased-item
analysis. The [trading contract](villager-trading.md) defines intended behavior;
the [economy reference](villager-stock-and-economy.md) records the stock tradeoffs.

## Candidate artifact

Build with Java 17 and run the normal unit, artifact, and publication gates.
Record the version, SHA-256, Java/Forge versions, dependency JARs, and enabled
packs for every native run. Run the exact reobfuscated production JAR unchanged.
A development run or a JAR containing production classes plus embedded probes
is supplementary evidence, not a substitute for the distributable check.

The optional `villagerAcceptanceJar` task creates a separate development-only
companion mod. Create an empty disposable directory with a
`.disposable-villager-qa` marker, then build it with:

```text
./gradlew reobfVillagerAcceptanceJar -PvillagerQA=<disposable-directory>
```

The result is `build/villager-acceptance/villager-acceptance-qa.jar`. Put it beside
the unchanged candidate only in isolated acceptance installations. It contains
no production classes, gameplay resources, or production mixin configuration.
Do not distribute the companion or install it into a player-owned world.
Its opt-in probes are under `src/development/java/` and require the matching
`tacz.villagerQA.root` JVM property. Each scenario uses an explicit mode such as
`stock`, `economy`, `reloadRecovery`, `lateReload`, `fallbackRegression`,
`persistence`, `lifecycle`, or `presentation`.
The lifecycle probe drives actual client select/click packets; server-only
scenarios use supplied FakePlayer inputs. Keep their results distinct.

Also boot with required dependencies only and without the companion. Confirm
startup, `/gg villager_trades status`, `/reload`, settled stock readiness, and
clean shutdown. Retain original logs. For a supported runtime Forge patch that
differs from the build dependency, the log checker accepts an explicit expected
version, for example:

```text
./gradlew verifyRuntimeSmokeLog -PsmokeKind=client -PsmokeLog=<client-log> -PsmokeForgeVersion=47.4.10
```

## Required scenarios

- New and established worlds: both job sites, native acquisition, disabled
  acquisition releasing its POI ticket, reassignment before trading, and locked
  professions after a trade. Check all four facing states and block drops.
- Equipment: guns arrive empty; correct ammo and basic magazine are available
  by the gun's level. Purchased equipment preserves its origin through transfer,
  save/load, modification, and Analyzer use. Buying never grants knowledge.
- Research: protected Data/fragments/blueprints respect prerequisites and
  recycling restrictions. Disabled analysis and unsafe legacy exchanges cannot
  become an unintended renewable research-currency source.
- Payments: normal pickup, shift-click, hotbar swap and drop paths, exact funds,
  full inventory, identical currency/material inputs, price floors, demand,
  customer switching, and native stock use. Rejected actions preserve inputs.
- Lifecycle: level-up, restock, save/load, zombification/cure, optional addon
  removal/restoration, relevant and unrelated content changes, capacity changes,
  and invalid/aborted/overlapping reloads. Include late synchronization failures
  before and after a successful reload. Saved choices and uses remain intact.
- Custom fallbacks: automatically supplied magazines for fallback guns, disabled
  and orphan branches, and shared fragment targets when a preferred attachment
  is incompatible. Verify the resulting native offers, not only candidate reports.
- Two connected clients: status/menu identity, shared stock, login regeneration,
  customer-specific discounts, stale quote refund, disconnect, and changed
  content while payment is held. Repeat core purchase/reload flows in singleplayer.
- Presentation: villager and zombie biome outfits, level badges, job models,
  small screens, GUI scales, long names, unavailable versus sold-out states,
  font overrides, resource reload, and optional recipe viewers.
- Upgrade: save a player and world with the previous published JAR, replace only
  the mod version, and compare native capability NBT, inventory, saved RP,
  settings, and existing world content. Introduce the new jobs in that world.

## 1.3.6 candidate coverage

The candidate was tested on Forge 47.4.10 with TaCZ 1.1.8-hotfix, Fzzy Config
0.7.6, and Kotlin for Forge 4.12.0, both with and without TaCZ Magazines
0.2.0-Beta. Native stock, Analyzer/economy, recovery, persistence, integrated
singleplayer, two-client multiplayer, and renderer probes passed with the
unchanged candidate JAR and separate companion. A companion-free server also
passed startup, status, reload, and shutdown.

The upgrade fixture was created by the actual 1.3.5 release. Its complete saved
research capability, inventory, RP, and world marker survived 1.3.6; existing
configuration fields retained their values, and both professions acquired jobs
in the same world. Artifact-bound results accompany the candidate package as
`villager-native-acceptance.json`. The subsequent outfit update was checked in
native front/side/back and seven-biome galleries for both villager models. Its
report separately identifies the prior gameplay artifact and records that only
the four profession textures and their headgear metadata changed; all other
archive entries, including production classes, are byte-identical.

The earlier presentation/economy pass also checked JEI and EMI separately and a
synthetic expanded gun catalog. These checks do not certify combined JEI/EMI
installations, villager-overhaul mods, arbitrary font packs, or third-party gun
prices. Accelerated purchases measure transactions and materials, not survival
playtime. Keep unrelated Research Tree/economy manual gates and their evidence
separate; this matrix does not mark every other subsystem's manual QA complete.
