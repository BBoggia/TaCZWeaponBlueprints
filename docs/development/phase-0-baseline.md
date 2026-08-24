# Phase 0 Baseline

Date: 2026-08-24

Phase 0 preserved the existing work, restored a working build, verified the exact TaCZ dependency, and established build, data-generation, client, and dedicated-server baselines. No pre-existing file was reset, stashed, deleted, or regenerated in the real worktree.

## Repository identity

- Branch: `v1.1.5-support-NEW`, tracking `origin/v1.1.5-support-NEW`
- Commit: `bc9e2b099fe9b0fa77efa57412e98d7ce79e5abe`
- Commit date: 2025-05-30
- Declared mod version: `1.0.3-beta7`
- No staged changes existed at the start of Phase 0.

## Preserved pre-Phase-0 worktree

The initial worktree contained 394 status entries:

| Status | Count |
| --- | ---: |
| Modified | 294 |
| Deleted | 84 |
| Untracked | 16 |

The changes were classified as follows:

| Area | Count | Notes |
| --- | ---: | --- |
| `src/generated/resources` | 371 | 271 modified, 84 deleted, 16 untracked |
| Tracked `run-data` | 12 | Config and log artifacts |
| `.DS_Store` outside those groups | 10 | Incidental macOS metadata |
| Authored main resources | 1 | `blueprints/loot_table_lists/village.json` |

No Java source file was modified in the pre-existing worktree.

A durable recovery snapshot is stored in the ignored directory `misc_files/phase0-baseline-2026-08-24/`. It contains:

- `tracked-working-tree.patch`: full-index binary patch for every initial tracked modification and deletion.
- `staged-index.patch`: empty, confirming that the index had no staged changes.
- `untracked-files.tar.gz`: archive of all 16 initially untracked files.
- `git-status.txt`, `diff-stat.txt`, and `untracked-files.txt`: inventory records.
- `SHA256SUMS`: checksums for every snapshot artifact.
- Build/runtime logs, the full-runtime server crash report, and the minimal-server thread dump.

The real `runData` output was not touched. Data generation was tested in an isolated copy under `/private/tmp`.

## Toolchain and exact dependencies

| Component | Baseline |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.3.0 |
| Mappings | Official 1.20.1 |
| Gradle | 8.8 |
| Build JVM | Oracle JDK 17.0.12, arm64 |
| Host default JVM | Oracle JDK 21.0.3 |
| TaCZ | 1.1.5, Curse artifact `6518539` |
| Fzzy Config | 0.5.9+1.20.1+forge |
| KotlinForForge | 4.11.0 |

The mapped TaCZ dependency is a valid 45 MB JAR with 3,846 entries. Its manifest reports `Implementation-Version: 1.1.5`; its SHA-256 is `2d92a5f080598886f4fbe506ed30748fd023102cb8b9ad6c9bb4608623b1e202`.

TaCZ 1.1.5 exposes the APIs needed for the Phase 1 repair:

- `IGun.getGunId(ItemStack)`
- `IAmmo.getAmmoId(ItemStack)`
- `IAttachment.getAttachmentId(ItemStack)`
- `GunSmithTableRecipe.getOutput()`, `getResult()`, and `getTab()`
- `CommonAssetsManager` gun, ammo, attachment, and block index lookups

These let blueprint discovery use the recipe result item rather than parsing the recipe ID path.

## Build baseline

The first build attempt failed during dependency configuration because the obsolete Progwml6 JEI repository returned HTTP 523 while Gradle queried it for the unrelated `tomlkt-jvm` dependency.

Phase 0 added a content filter to that repository so it is queried only for `mezz.jei` artifacts. JEI dependencies are currently commented out, but retaining the scoped repository is harmless and prevents its outage from blocking unrelated dependencies.

Results after the correction:

- First cache-populating build: `BUILD SUCCESSFUL in 23m 14s`.
- Warm-cache verification build: `BUILD SUCCESSFUL in 6s`.
- `compileJava`: successful.
- Tests: `NO-SOURCE`; the repository currently has no automated test source.
- Only build-level warning: deprecated Gradle features will be incompatible with Gradle 9.
- Current release artifact: `build/libs/taczweaponblueprints-1.0.3-beta7.jar`.
- Current artifact size: 690,242 bytes; 660 ZIP entries; ZIP integrity passes.
- Current artifact SHA-256: `ddb7c513dfe30651a989bbb0268f435a831a8bafbda2ba834136fabdad98dc74`.

Successful repeated builds are established. Byte-for-byte deterministic JAR output is not yet established because successive JAR tasks produced different ZIP bytes/size, most likely from archive metadata.

## Resource and data-generation baseline

- All 518 JSON resources parse successfully.
- The generated global loot-modifier index contains 485 entries.
- Exactly 485 modifier JSON files exist.
- The index and file set match exactly; the comparison has zero missing or extra entries.
- Isolated `runData`: `BUILD SUCCESSFUL in 1m 10s`.
- DataGenerator processed 486 JSON outputs and wrote zero changes relative to the current worktree snapshot.
- The isolated data-generation latest log contains no WARN or ERROR entries.

This establishes that the current large generated-data diff is internally coherent and reproducible from the current source/configuration. It does not establish that all loot-design choices are correct.

## Client smoke baseline

The full development client reached the active render loop successfully:

- OpenGL 4.1 initialized on the Apple M4 Max.
- Mod construction and client setup completed.
- Shader sources loaded.
- OpenAL and the sound engine initialized.
- Texture atlases were created.
- TaCZ and the configured content packs loaded far enough to reach the normal client loop.
- No new client crash report was produced.

The smoke log contains 6 ERROR and 35 WARN records:

- 4 errors are the known `GunSmithTableScreenMixin` writes to TaCZ `final` fields.
- 2 errors are invalid Suffuse pack resource paths (`.WAV` uppercase and a filename containing a space/parentheses).
- Remaining warnings are mostly third-party pack/model/sound/config issues.

The client was terminated after the smoke check, so the Gradle session exit code was the expected interrupt code rather than a Minecraft crash.

## Dedicated-server baseline

### Full declared development runtime

The default `runServer` classpath does not reach the blueprint mod's server lifecycle. It fails during mod construction because optional structure/data-generation dependencies are configured as normal `implementation` runtime mods:

- Library Ferret attempts to load `net.minecraft.client.gui.screens.Screen` on `DEDICATED_SERVER`.
- Quark/Zeta registers a client event interface from a non-client-only class.

This is a development-classpath problem, not a TaCZ Weapon Blueprints server crash. The crash report is preserved as `full-runtime-server-crash.txt` in the recovery snapshot.

### Isolated core runtime

An isolated temporary copy was run with the optional structure-mod block disabled. It included Forge, TaCZ 1.1.5, Fzzy Config, KotlinForForge, Packet Fixer, this mod, and the existing TaCZ packs.

Results:

- Dedicated server reached `Done (1.424s)`.
- A JVM thread dump confirmed the server was in its normal `waitUntilNextTick` loop with no deadlock.
- Shutdown saved the overworld, Nether, and End successfully.
- The blueprint manager initialized but logged 242 failed blueprint registrations.
- 237 of those were accompanied by `Unknown item type` messages caused by the TaCZ 1.1.5 recipe-ID schema.
- 5 additional messages involved recipe outputs resolving to `AirItem` for malformed or incompatible content-pack entries.
- A few separate TaCZ pack recipes/data files were malformed and should be skipped safely rather than treated as valid blueprints.

This proves that the core mod is dedicated-server loadable once data-generation-only dependencies are removed from the runtime classpath.

## Phase 1 entry criteria

Phase 1 can begin from a controlled baseline with the following gates:

1. Run Gradle with JDK 17.
2. Keep `./gradlew build` green.
3. Run `runData` only in an isolated copy until generated-data changes are intentionally authorized.
4. Use the minimal core dependency set for dedicated-server validation until optional data-generation dependencies are separated properly.
5. After catalog changes, require:
   - no blueprint registration failure caused by recipe-ID path parsing;
   - malformed/removed pack results skipped safely with aggregated diagnostics;
   - all 518 JSON resources still valid;
   - the 485-entry modifier index still exactly matching the 485 modifier files;
   - client startup and minimal dedicated-server startup remaining green.

## Files changed by Phase 0

- `build.gradle`: scopes the obsolete Progwml6 repository to `mezz.jei`.
- `docs/development/phase-0-baseline.md`: this report.
- `misc_files/phase0-baseline-2026-08-24/`: ignored local recovery/evidence bundle.

Build output and runtime smoke logs changed only under already ignored `build`, `run`, `/private/tmp`, and `misc_files` locations.
