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
./gradlew verifyPublicationReadiness
```

The first command runs the tests, reobfuscates the mod JAR, and validates the
packaged metadata, JSON, loot-modifier index, and archive hygiene. The second
also rejects unresolved publication choices such as the stock Forge MDK
license. The selected root license is packaged into the verified JAR.

For compatibility-sensitive changes, repeat the dedicated-server and client
smoke tests documented in `docs/operations-and-migration.md`.

For Journal/research releases, complete one integrated-server Research Bench
interaction pass:

- craft or place the bench and confirm its menu opens and closes normally;
- close it with items in every input type and confirm all unused items return;
- select a disclosed unlearned blueprint and verify the exact point/item preview;
- confirm insufficient points, missing ingredients, and a full inventory consume
  nothing;
- complete one research transaction, receive one normal physical blueprint,
  then use it to learn the recipe;
- recycle one learned duplicate and confirm exactly one item is consumed for the
  complete configured award;
- keep a bench open across `/reload` and confirm its preview refreshes while the
  next action uses the new policy.

## Publish

- Record the SHA-256 hash of `build/libs/taczweaponblueprints-<version>.jar`.
- Create an annotated `v<version>` tag on the exact validated commit.
- Push the commit and tag without force-updating an existing release tag.
- Create the GitHub release from that tag and use the changelog section as its
  release notes.
- Attach the reobfuscated JAR from `build/libs`; do not attach a development or
  sources JAR in its place.
- Download the uploaded asset and verify its SHA-256 against the local artifact.
