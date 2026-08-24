# Phase 8 Implementation

Date: 2026-08-24

Phase 8 turns the recovered and redesigned mod into a verifiable release candidate. It tightens dependency metadata, makes the distributable archive reproducible, adds an artifact-level release gate, replaces the Forge MDK placeholder documentation, and records a safe migration and rollback procedure.

## Release metadata

The declared runtime contract now matches the versions used throughout development and smoke testing:

- Minecraft 1.20.1;
- Forge 47.x;
- TaCZ `[1.1.5,1.2)`;
- Fzzy Config `[0.5.9,0.6)`;
- Java 17 for development and verification.

The TaCZ Curse artifact and Fzzy Config dependency version are named Gradle properties rather than unexplained inline values. `processResources` expands the same bounded ranges into `mods.toml`, so development resolution and packaged metadata no longer drift independently.

## Reproducible archive

The release JAR now uses reproducible file ordering and discards source-file timestamps. The manifest retains implementation and specification versions but no longer embeds the wall-clock build timestamp.

Two independent clean `jar` invocations produced the same artifact digest:

```text
d97b1b2a7f9ec05bc921efc4285c701d66573faa0a0b15cbca4c8fc5e6c18fd4
```

This certifies reproducibility for the current source tree, Gradle/JDK environment, and dependency cache. A version, source, dependency, toolchain, or generated-resource change is expected to change the digest.

## Automated release gate

`verifyReleaseArtifact` depends on the reobfuscated JAR and the test suite. It rejects an artifact when any of these invariants fail:

- required dynamic-loot, policy-resolution, command, metadata, or manifest entries are missing;
- `.DS_Store` or data-generator `.cache` metadata is packaged;
- the number of blueprint modifier resources is not exactly 486;
- the six built-in pools and six built-in rules are not all packaged;
- any packaged JSON document is malformed;
- the global modifier index contains missing, extra, or duplicate IDs;
- packaged TaCZ or Fzzy Config ranges differ from the supported ranges;
- the manifest version differs from the project version;
- a non-reproducible implementation timestamp returns.

The normal Gradle `check` lifecycle depends on this gate. `./gradlew build` therefore validates the actual distributable rather than stopping at compilation and unit tests.

## User and operator documentation

The stock Forge MDK `README.txt` has been replaced by a project README covering:

- requirements and installation boundaries;
- authoritative progression and crafting behavior;
- live configuration and datapack capabilities;
- every operator command;
- datapack resource locations;
- normal and structure-aware build commands.

`CHANGELOG.md` summarizes the release candidate while explicitly preserving existing player `Recipes` data, format-1 datapacks, six-tier behavior, and all 485 legacy fallback modifiers.

`docs/operations-and-migration.md` defines supported versions, upgrade sequencing, dynamic/legacy ownership, staged datapack rollout, last-known-good behavior, rollback, and diagnostics for malformed third-party TaCZ packs.

## Validation status

- Java 17 `cleanTest build` succeeds through compilation, 43 tests, mixin processing, reobfuscation, release verification, `check`, and assembly.
- All source and generated JSON resources parse successfully.
- The JAR passes ZIP integrity validation and the artifact-level JSON, metadata, resource-count, and modifier-index checks.
- Two clean archives are byte-for-byte identical at the digest recorded above.
- Isolated structure-aware datagen reports one dynamic and 485 legacy modifiers. All 485 legacy files are byte-identical to the checked-in resources; the dynamic modifier and 486-entry index are semantically identical after normalizing Forge's hash-map entry order and trailing-newline behavior.
- An isolated dedicated server reaches `Done`, builds a 452-entry authoritative catalog from 694 TaCZ recipes, and activates 6 pools, 6 rules, and 748 exact bindings.
- An isolated client reaches the render loop with OpenGL, OpenAL, registries, texture atlases, and the blueprint UI mixin loaded.
- The JAR contains no data-generator cache or Finder metadata.

The smoke-test TaCZ content set still reports malformed `ccrp` localization JSON, an invalid Suffuse sound filename, empty-output recipes, duplicate aliases, and missing optional definitions. These originate in external content packs; the blueprint mod isolates invalid recipes, preserves valid catalog entries, and remains operational.

Repository-wide `git diff --check` continues to report mixed CRLF on earlier `build.gradle` additions and trailing spaces in historical checked-in `run-data` logs. The Phase 8 documentation, properties, metadata, and new Gradle release-verifier block introduce no new whitespace errors.

## Release decision still required

The only unresolved distribution blocker is licensing metadata. `gradle.properties` declares `All Rights Reserved`, while `LICENSE.txt` is the stock Minecraft Forge LGPL notice from the MDK. That file does not establish the intended license for this mod.

The author must choose the actual project license before public distribution. Then replace `LICENSE.txt` with the correct project license or proprietary notice and keep `mod_license` in exact agreement. Phase 8 intentionally does not infer or grant rights on the author's behalf.

The project version remains `1.0.3-beta7`. Selecting a public version number and release channel is likewise a release-management choice, not a code migration.

## Remaining hands-on acceptance

1. Back up a representative world and perform the documented upgrade and rollback sequence.
2. Verify unlock persistence and server-side crafting rejection with two real clients.
3. Inspect and preview representative exact, selector, predicate, disabled, and legacy-fallback tables in game.
4. Generate a physical loot sample and compare it with previewed probabilities.
5. Decide and install the project license, select the public version, then rebuild and record the final artifact digest.
