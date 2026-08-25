# Research Tree Navigation Phase 8

Date: 2026-08-25

Phase 8 turns the grouped Research Tree implementation into a versioned,
auditable release candidate. Automated validation and runtime startup checks
are complete. The hands-on interaction and visual matrix remains an explicit
publication gate rather than being inferred from unit tests or startup logs.

## Release identity

- candidate version: `1.2.0`;
- Minecraft: `1.20.1`;
- Forge: `47.3.0`;
- TaCZ: `1.1.8-hotfix`;
- Fzzy Config: `0.5.9+1.20.1`;
- network protocol: `15`; and
- license: All Rights Reserved.

The changelog now has an empty Unreleased section followed by one dated 1.2.0
entry. The operations guide records the protocol-only compatibility break and
the absence of a player-progression or world-data migration. The packaged mod
description now explains the Research Bench and configurable research system
instead of describing only recipe-learning items.

## Release gates

`verifyReleaseArtifact` now fails if the reobfuscated JAR omits the grouped-tree
client state, projections, navigation, synchronization, resource
definition, graph, layout, presentation, or publication classes. It also
requires exactly:

- one built-in research profile;
- 32 built-in default-tree research-rule resources; and
- seven built-in research-tree group resources.

`certifyReleaseCandidate` rejects any build runtime other than JDK 17. It runs
the publication and package gates, parses the JUnit results, hashes the exact
JAR, and records the build JVM, dependency versions, protocol, tests, size, and
SHA-256 in `build/reports/release-candidate.json`. Tag and manually dispatched
GitHub Actions builds retain that JSON beside the candidate JAR.

## Automated evidence

The final candidate was built with Oracle JDK 17.0.12.

| Gate | Result |
| --- | --- |
| `cleanTest build --warning-mode all` | Passed |
| Unit and contract tests | 291 passed; 0 failed, errored, or skipped |
| Reobfuscated artifact verification | Passed |
| Publication readiness | Passed |
| Release-candidate certification | Passed |
| Client runtime-log validation | Passed |
| Dedicated-server runtime-log validation | Passed |

The only build warnings are Gradle 9 deprecations in Forge/Mixin Gradle APIs.
They do not fail compilation, tests, reobfuscation, or packaging.

## Runtime smoke evidence

The dedicated-server smoke run reached the Minecraft `Done` marker using Forge
47.3.0, TaCZ 1.1.8-hotfix, and mod version 1.2.0. It published one research
profile, 32 rules, and seven groups, then initialized the live blueprint
catalog and dynamic loot distribution. The disposable test world is
`run/phase8-smoke`; the retained validated log is
`run/logs/2026-08-25-1.log.gz`.

The client smoke run reached the title flow, loaded an existing 1.1.0 test
world under 1.2.0, started its integrated server, published the same 32-rule,
seven-group snapshot, saved the world, returned, and shut down cleanly. The
retained validated log is `run/logs/latest.log`.

Both runs discovered malformed recipes, sounds, or gun definitions in optional
third-party TaCZ content packs already installed under `run/tacz`. The failures
name those packs, while the valid catalog and this mod's snapshots still
initialize. The runtime-log gate correctly does not attribute those external
pack errors to TaCZ Weapon Blueprints.

## Candidate artifact

- path: `build/libs/taczweaponblueprints-1.2.0.jar`;
- size: 3,136,749 bytes; and
- SHA-256: `558325dd40a362e692fc2d78098f9454783782633a38555f4a1abb155ce8f157`.

The packaged metadata reports version 1.2.0, All Rights Reserved, Forge
`[47,48)`, Minecraft `[1.20.1,1.20.2)`, TaCZ `[1.1.8,1.2)`, and Fzzy Config
`[0.5.9,0.6)`.

## Hands-on publication gate

The available desktop-control bridge could not attach to Minecraft's Java/GLFW
window, so Phase 8 does not claim that startup-log evidence proves visual or
pointer behavior. Before tagging 1.2.0, complete and retain
`docs/research-tree-manual-qa.md`, especially:

- GUI scales 1 through Auto, minimum window sizes, and long translations;
- sidebar/search ownership, pan/zoom, portals, Branches/All Weapons cameras,
  keyboard traversal, narration, and tooltip/card placement;
- automatic inventory-backed research, double-click and button activation,
  failure atomicity, recycling, and two-player authority;
- `/reload`, content-pack removal/restoration, reconnect, and protocol mismatch;
- Research Bench placement, orientation, both-part interaction/break behavior,
  item rendering, and collision; and
- the required compact, fullscreen Branches, fullscreen All Weapons, and pinned
  details screenshots.

The candidate is therefore automated-gate-complete and runtime-startup-complete,
but not yet publication-cleared. Publication additionally requires the manual
matrix, review of the intentional working-tree diff, an exact validated commit,
and the normal commit/tag/upload/hash verification steps in the release
checklist.
