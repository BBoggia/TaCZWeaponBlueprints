# Research Bench interface validation

Validate the Research Bench in a disposable world before releasing interface
changes. Automated checks establish state and transaction contracts; native
Minecraft checks establish readability, input ownership, and integration.
Neither replaces the other.

## Automated checks

Use Java 17 and the repository's Gradle wrapper:

```sh
./gradlew build researchTreePerformance
./gradlew certifyReleaseCandidate
```

The build runs the test suite and packaged artifact/version checks. The scene
performance task reports CPU and allocation measurements for the stored public
tree and larger fixtures. A scene rejected by a documented work budget must
remain explicitly unavailable; successful task execution does not mean every
stress fixture can be rendered.

Release certification also requires finalized release metadata and an empty
Unreleased changelog section. An intentional development build can pass tests
and artifact checks while failing publication readiness. Record that distinction;
do not remove release guards to obtain a successful certification report.

| Contract | Principal regression coverage |
| --- | --- |
| Currency, bypass, blockers, crafting and fragments | `ResearchTreeSelectedNodePresenterTest`, workstation/menu suites |
| Matching quote and optional route review | `ResearchTreeRouteReviewStateTest`, `ResearchRouteReviewPacketTest` |
| Delayed replies, disconnect, partial publications | `ResearchTreeFeedbackStateTest`, `ClientResearchStateTest`, publication packet suites |
| Inspector navigation, scrolling and camera | `ResearchTreeInspectorTest`, `ResearchTreeCameraInputTest` |
| Search editing, no matches and recovery | `ResearchTreeSearchControllerTest` |
| Full material list, page bounds and allocated counts | `ResearchMaterialPagePacketTest`, `ResearchSelectionCurrencyPreviewTest`, `ResearchTreeInspectorCostsTest` |
| Detail levels, labels and connector emphasis | `ResearchTreeReadabilityTest`, `ResearchTreeSceneMigrationTest` |
| Minimap coverage, placement and captured dragging | `ResearchTreeMinimapTest` |
| Bounded artwork work and reload cancellation | `ResearchTreeArtPreparationTest` |
| Topology, disclosure and layout admission | Scene, projection, grouped-prerequisite and graph suites |

## Native checks

Use the [manual QA matrix](research-tree-manual-qa.md). Include Overview, tracked
and independently inspected targets, exact purchase review, requirement Back,
keyboard search, no matches, fractional scrolling, help, minimap dragging,
resource reload, and close/reopen. Exercise 320×240 GUI pixels, 419 and 420 GUI
pixels of width, ordinary desktop sizes, and long names. Distinguish logical GUI
size from framebuffer resolution when selecting display and GUI scale settings.

In portable research, verify that the Journal and branch-label buttons have
separate hit targets. Search a large result set, press Page Down then Enter,
and verify that the selected result was visible. Test a path with more than six
material types: browse all pages by mouse and keyboard, change inventory, and
confirm that the Have counts and whole-purchase readiness update together.

For fault injection, record whether a publication, response, inventory change,
or failure message was injected rather than produced by ordinary gameplay.
A UI fault fixture cannot certify server transaction behavior. Report unsupported
hardware-specific checks, such as physical trackpad feel or actual network
latency, separately from simulated input and correlated-packet tests.

Performance records should include cold opening, first-seen artwork, warm camera
movement, search, inspector interaction, and repeated resource reloads. Preserve
settings, camera, input identity, and artifact identity when comparing changes.
Record render-call timings separately from paced frame intervals and GPU time.
Use a fixed-camera cold-crop sample to distinguish metadata preparation from
texture upload and Minecraft's wider resource reload.

Archive the exact JAR hash, test reports, performance data, and native captures
with the release evidence. Keep temporary QA worlds and agent implementation
journals outside published documentation. Confirm that the original world,
learned state, inventory, and progression topology were preserved.
