# Research Tree Phase 8: Release Certification

Date: 2026-08-24

Phase 8 makes the completed Research Bench line repeatably certifiable without
pretending automated checks can prove visual behavior. It adds two Gradle
verification surfaces:

- `verifyRuntimeSmokeLog` accepts either a complete client or dedicated-server
  log, requires lifecycle markers for that runtime, and rejects mod-local
  classloading, mixin, and initialization failures.
- `certifyReleaseCandidate` runs the publication and packaged-artifact gates,
  then writes `build/reports/release-candidate.json` with dependency versions,
  network protocol, JUnit totals, artifact size, and SHA-256.

The CI workflow runs the complete clean build on every push and pull request.
Tagged builds and manually dispatched release runs additionally execute the
certification task and retain both the JAR and report as one evidence artifact.
The report does not contain a timestamp or machine-specific absolute path, so
identical inputs remain comparable across machines.

## Runtime evidence

The macOS development client reached the main menu on Minecraft 1.20.1, Forge
47.3.0, TaCZ 1.1.8-hotfix, and Fzzy Config 0.5.9. Its log contains the expected
mod configuration load and completed sound/resource startup markers with no
mod-local fatal, mixin-application, injection, or classloading error.

The dedicated development server loaded the same dependency set, published
loot snapshot revision 1, published the 30-rule default research snapshot with
54 exact targets, resolved 481 blueprint outputs from 724 TaCZ recipes, reached
the `Done` marker, and saved every dimension cleanly when stopped.

The installed `ccrp` TaCZ content pack has a malformed `en_us.json` entry. Forge
logs that external warning and continues startup; the release gate intentionally
does not misattribute arbitrary content-pack warnings to this mod.

## Residual visual certification

ForgeGradle launches LWJGL on macOS as an app-less Java process with no bundle
identifier. The available UI harness therefore cannot attach to the game
window. The display-scale, model-orientation, mouse, keyboard, narration,
two-player, and screenshot items in `docs/research-tree-manual-qa.md` remain
explicit hands-on release checks and are not marked passed by Phase 8.
