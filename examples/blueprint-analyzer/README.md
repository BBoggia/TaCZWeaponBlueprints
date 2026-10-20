# Blueprint Analyzer datapacks

These optional Minecraft 1.21.1 datapacks demonstrate complete legacy and tiered
analysis policies plus exact-target and blueprint-tag overrides. The mod's
bundled profile already uses tiered analysis; these packs are customization and legacy references.

Run `./gradlew packageAnalyzerDatapacks` in the repository root to create the
three installable zips in `build/distributions/analyzer/`. You can also copy a
single subdirectory into a world's `datapacks` directory; `pack.mcmeta` must be
at its root.

- `tiered`: all nine tier pairs, full/partial outcomes and above-tier no-result chances,
  normal learning prerequisites, and reconstruction thresholds 5/10/15.
- `legacy`: an optional override restoring guaranteed weapon analysis.
- `overrides`: optional examples for `tacz:ak47` and `tacz:m4a1`, requiring a
  complete tiered policy for `taczweaponblueprints:duplicate_recovery`.

Use either the tiered or legacy profile override, never both. The complete
profile files replace other definitions at the same ID. If a modpack already
customizes that profile, merge only the Analyzer extension instead.

Read [installation, migration, and balance reporting](../../docs/analyzer-presets-and-balancing.md)
for costs, permissions, activation, rollback, and the offline report command.
The [policy reference](../../docs/analyzer-policy-authoring.md) covers field
validation and rule precedence.
