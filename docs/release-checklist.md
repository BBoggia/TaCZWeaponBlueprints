# Release checklist

Use this checklist for every public build. Release commands require JDK 17.

## Prepare

- Confirm `mod_version` in `gradle.properties` and use the same version in the
  changelog and Git tag.
- Confirm `mod_license` agrees with the root `LICENSE.txt`.
- Update the changelog heading with the release version and date.
- Merge or rebase the latest `origin/main` before the final validation build.
- Confirm `git status --short` contains only intentional release changes.
- Review generated-resource additions and deletions, especially the global
  loot-modifier index.

## Validate

Run:

```text
./gradlew cleanTest build --warning-mode all
./gradlew certifyReleaseCandidate
```

The first command runs the tests, reobfuscates the mod JAR, and validates the
packaged metadata, JSON, loot-modifier index, and archive hygiene. The second
also rejects unresolved publication choices such as the stock Forge MDK
license. The selected root license is packaged into the verified JAR.
Certification rejects a non-JDK-17 runtime and writes
`build/reports/release-candidate.json`; retain that report with the exact JAR it
describes.

For compatibility-sensitive changes, repeat the dedicated-server and client
smoke tests documented in `docs/operations-and-migration.md`.
Validate their complete logs with:

```text
./gradlew verifyRuntimeSmokeLog -PsmokeKind=client -PsmokeLog=<client-log>
./gradlew verifyRuntimeSmokeLog -PsmokeKind=server -PsmokeLog=<server-log>
```

For Journal/research releases, complete one integrated-server Research Bench
interaction pass:

- place the bench with two horizontal spaces available, verify both parts face
  correctly, and confirm interacting with either part opens the same menu;
- break each half in separate tests and confirm the complete bench is removed
  with exactly one item drop; confirm pistons cannot split the bench;
- close it with a duplicate in the recycling input and confirm the unused item returns;
- browse without exposing inventory slots, select an unlearned blueprint, and
  verify the exact RP and player-inventory material preview in its tooltip;
- test both the floating Research button and node double-click shortcut, then
  switch to Recycle and confirm only its duplicate/player slots are active;
- confirm insufficient points, missing ingredients, and unmet prerequisites
  consume nothing;
- complete one research transaction, receive one normal physical blueprint,
  then use it to learn the recipe;
- recycle one learned duplicate and confirm exactly one item is consumed for the
  complete configured award;
- keep a bench open across `/reload` and confirm its preview refreshes while the
  next action uses the new policy; confirm the research tree publication also
  refreshes without briefly showing a partial graph.

Complete and retain the environment details and results from
`docs/research-tree-manual-qa.md`. This includes GUI scales 1 through Auto,
minimum window sizes, long translations, mouse/keyboard/narration, two players,
content-pack removal and restoration, protocol mismatch, model orientation, and
the required release screenshots.

## Publish

- Record the SHA-256 hash of `build/libs/taczweaponblueprints-<version>.jar`.
- Create an annotated `v<version>` tag on the exact validated commit.
- Push the commit and tag without force-updating an existing release tag.
- Create the GitHub release from that tag and use the changelog section as its
  release notes.
- Attach the reobfuscated JAR from `build/libs`; do not attach a development or
  sources JAR in its place.
- Download the uploaded asset and verify its SHA-256 against the local artifact.
