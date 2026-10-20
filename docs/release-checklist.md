# Release checklist

For the NeoForge 1.21.1 branch, use JDK 21 and the
[packaged runtime matrix](neoforge-development.md#packaged-runtime-matrix),
then run `verifyPublicationReadiness` after finalizing the release version
and copy. NeoForge metadata is `META-INF/neoforge.mods.toml`, and its build
output is under `build/neoforge/`.

The checklist below describes the preserved Forge 1.20.1 release workflow.
Its release commands require JDK 17.
The detailed contract checks and manual test coverage are documented in the
[release validation reference](release-validation.md).

## Prepare

- Choose the release version. During development, keep `mod_version` in
  `gradle.properties` on the next patch version with a `-dev` suffix, such as
  `1.1.1-dev`.
- For the release candidate, remove `-dev`, move the applicable entries from
  `## Unreleased` to `## <version> - <YYYY-MM-DD>` in `CHANGELOG.md`, and leave
  a fresh Unreleased section.
- Create `docs/releases/<version>.md` with player-focused notes. Update
  `docs/curseforge-description.md` only when the evergreen mod-page copy has
  changed.
- Confirm `mod_license` agrees with `LICENSE.txt`, and verify public dependency
  and compatibility statements against `gradle.properties` and
  `src/main/resources/META-INF/mods.toml`.
- Review generated-resource changes, especially the global loot-modifier index.
- Merge or rebase the latest `origin/main`, then confirm `git status --short`
  contains only intentional release changes.

## Validate

Run the shared writing check from this repository's normal workspace:

```text
../Content&DocumentationStandards/scripts/check-writing.sh .
```

Build and certify the exact release candidate:

```text
./gradlew cleanTest build --warning-mode all
./gradlew certifyReleaseCandidate
```

- Complete the relevant Minecraft checks in
  [Research Tree manual QA](research-tree-manual-qa.md) and the compatibility
  smoke tests in [Operations and migration](operations-and-migration.md).
- Confirm `build/reports/release-candidate.json` describes the exact JAR in
  `build/libs` and records the expected version and SHA-256 digest.
- Confirm `build/reports/research-guidance-candidate-handoff.json` references
  the same JAR digest and remains marked `requires_manual_qa` until the linked
  Research Tree runtime checks are completed.
- Boot the unmodified, reobfuscated JAR in a standalone Forge server installation
  with the supported dependency JARs. Record its SHA-256, Forge/Java versions,
  successful startup, `/reload`, and clean shutdown. A Gradle development server
  or a remapped copy does not satisfy this check: production method names must
  be exercised. Test the packaged client crafting screen when changing mixins.
- Inspect the JAR for development files, local paths, logs, and editor output.
- Run the release build again after any source, resource, metadata, or
  documentation correction that affects the candidate.

Also complete the [research economy handoff](research-economy-release-handoff.md)
for currency or Data releases. Retain the actual mode/configuration matrix,
client/server versions, catalog and datapack list, native lifecycle evidence,
and production-install playtest results with the exact artifact digest. Review
[currency migration](research-currency-migration.md) before updating established
servers. A development handoff and adapted-artifact run do not mark the final
production-install checklist complete.

For villager changes, complete the [villager release matrix](villager-release-validation.md)
and retain its artifact-bound native results with the candidate.

## Publish

- Create an annotated `v<version>` tag on the validated commit. The tag must
  match `mod_version`, and a release tag must not use a `-dev` version.
- Push the commit and tag without replacing an existing release tag.
- Create the GitHub release from that tag. Use
  `docs/releases/<version>.md` as the release notes and attach the reobfuscated
  `taczweaponblueprints-<version>.jar`.
- Upload the same verified JAR and release notes to CurseForge.
- Download the published asset and verify its SHA-256 digest against the local
  candidate.
- After publication, advance `mod_version` to the next intended development
  version with a `-dev` suffix before beginning new work.
