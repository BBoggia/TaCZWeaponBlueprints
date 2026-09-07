# Blueprint Analyzer integration validation

The automated suite covers policy parsing, rule precedence, probability bounds,
fragment protection, persistence, transaction rollback, request replay, and packet
validation. Native acceptance adds a real client connection to a dedicated server
and checks the distributed tiered preset through the Blueprint Analyzer screen.

## Automated checks

Use Java 17 and the repository's Gradle wrapper:

```sh
./gradlew check packageAnalyzerDatapacks generateAnalyzerBalanceReport \
  -PanalyzerCatalog=build/reports/analyzer/example-catalog.json
```

The preset integration test writes the example catalog consumed by the balance
report task. See [presets and balancing](analyzer-presets-and-balancing.md) for
reports based on a server's exported catalog.

## Dedicated native acceptance

The opt-in probes live in `src/development/java`. Release jars and ordinary runs
exclude them. They reset player inventories and progression, replace nearby
station blocks, disconnect the client, and stop the QA server when finished.
Use a disposable world copy and a separate server directory.

Prepare an output directory, for example `build/analyzer-acceptance`, with:

- `server/world/`: a copy of a working test world containing the marker file
  `.analyzer-release-copy`.
- `server/world/datapacks/`: the packaged `blueprint-analyzer-tiered.zip`. Remove
  conflicting test overrides from this copy.
- `server/config/` and `server/tacz/`: compatible development configuration and
  the TaCZ gun pack. The active profile must be `duplicate_recovery`, with
  blueprints enabled and found recovery set to `PROTECTED_BLUEPRINT_ONLY`.
- Set `researchCurrencyKind = "BANKED_RP"` and
  `researchDataBehavior = "LEGACY_CURRENCY"` explicitly in this disposable
  server's Blueprint configuration. This harness validates the banked weapon
  preset and legacy Data path. Use the [economy acceptance harness](research-economy-validation.md)
  for default Scrap/Data analysis and two-player lifecycle coverage.
- `server/eula.txt`: an already accepted server EULA, supplied by the operator.
- `server/server.properties`: `level-name=world`, `server-ip=127.0.0.1`,
  `server-port=25577`, `online-mode=false`, and `max-players=1`. Bind only to
  localhost; the probe uses an offline development client identity.

The client uses the normal development run directory and restores its GUI scale.
Launch the following in separate terminals, starting the server first:

```sh
./gradlew runServer -PanalyzerReleaseQA=build/analyzer-acceptance
./gradlew runClient -PanalyzerReleaseQA=build/analyzer-acceptance
```

The gameplay pass checks all nine station/weapon combinations against the shipped
probability matrix and costs. Actual client button requests exercise a guaranteed
full blueprint, a partial fragment, reconstruction, Research Data redemption,
ordinary fragment discounts, duplicate blueprint recycling, and direct RP
recovery. It also checks known targets, occupied Output, and insufficient RP or
materials. The direct RP scenario temporarily changes only the running process's
recovery mode and restores it before saving.

Partial outcomes use the real random selector and unchanged preset. The probe
repeats fresh attempts if a full blueprint occurs, with a limit of 64 attempts;
this is outcome-path coverage, not a statistical balance test. Every attempt
must consume the exact configured payment.

The pass then saves RP, knowledge, ordinary progress, criterion progress, all
four analysis archive classes, and four physical fragment classes. It disconnects
and reconnects the client before comparing the loaded state. Server and client
must both produce `gameplay-*-complete.json` with `passed: true`.

Restart both processes against the same QA directory with each phase in order:

```sh
./gradlew runServer -PanalyzerReleaseQA=build/analyzer-acceptance -PanalyzerReleasePhase=restart
./gradlew runClient -PanalyzerReleaseQA=build/analyzer-acceptance -PanalyzerReleasePhase=restart

./gradlew runServer -PanalyzerReleaseQA=build/analyzer-acceptance -PanalyzerReleasePhase=migration
./gradlew runClient -PanalyzerReleaseQA=build/analyzer-acceptance -PanalyzerReleasePhase=migration
```

Run each server/client pair together and wait for both to exit before starting
the next pair. `restart` checks the saved state in a fresh server process.
`migration` first changes only the copied player's capability to the version-4
shape, removing its analysis archive, then loads it through a real login. Existing
knowledge, RP, ordinary fragments, criteria, and item identity must remain; the
new archive must initialize empty and serialize as version 5.

Inspect each phase's server and client completion files and any `*-error.txt`.
A successful Gradle exit alone is insufficient because probes stop Minecraft
cleanly after a failed assertion. Screenshots are written under `screenshots/`;
review the odds, progress, blocked requirements, results, and station titles.
Archive previous phase output before rerunning that same phase so stale control
or completion files cannot be mistaken for a fresh result. The server rejects
existing files for the same phase, and client completion requires a live
connection during the current run. Archive the screenshot directory before
repeating gameplay; existing captures are never counted as new evidence, and
Minecraft must report a successful write for each new capture. Use a fresh disposable
copy to restart the entire sequence after migration.

Native tests cover the installed catalog and configurations. Use the policy and
transaction tests for malformed data, explicit no-result outcomes, unusual odds,
permission restrictions, and rollback faults that the standard preset does not
naturally produce. A pack still needs gameplay tuning for its own loot rate,
weapon supply, material costs, and progression pace.
