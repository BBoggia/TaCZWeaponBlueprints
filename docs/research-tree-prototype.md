# Native Tech Tree comparison

Audience: developers evaluating the Research Bench's replacement scene renderer.

The opt-in prototype renders public Tech Tree snapshots in Minecraft using the
real font and TaCZ slot artwork. It compares a compact named tile, a horizontal
card, and the retained legacy canvas. The normal Research Bench uses the compact
tile scene for gameplay; see the [scene consumer contract](research-tree-scenes.md).
Selections in the comparison are local and cannot purchase research.

## Launch

Use JDK 17 and an already resolved dependency cache:

```sh
./gradlew --offline runClient \
  -PresearchTreePrototype=build/reports/research-tree/prototype \
  -PresearchTreePrototypeInput=src/test/resources/research-tree-baseline/live-weapons-287.json
```

The input property opens the recorded comparison from the title screen after
resources load. Without it, open a test world and the Research Bench. The first
Bench opens a comparison using its public snapshot. F8 can reopen the comparison
from a later Bench session. Returning from that comparison goes back to the world;
it does not restore a closed menu instance.

The development classes are excluded from ordinary runs and release artifacts.
Do not enable the baseline observer and prototype together when collecting timing
evidence: they serve different screens and camera workloads.

## Controls

| Control | Action |
| --- | --- |
| Drag in the canvas | Pan; movement beyond three GUI units cancels a click |
| Scroll in the canvas | Zoom around the pointer |
| Click a node | Select without recentering |
| Overview / O | Fit the whole scene |
| Branch / B | Frame the selected owner's region with a readable zoom floor |
| Target / F | Center the selected blueprint at browse scale |
| Tile / Card | Compare tile and card measurements |
| Current / New | Compare the new scene and current canvas on the same public input |
| Quiet / World | Compare the quiet surface and world-backed surface when a world is open |
| Sample | Cycle recorded and structural fixtures |
| D | Cycle available domains in the current sample |
| Scroll in inspector | Read additional requirement groups and alternatives |
| F9 | Restart the scripted capture at the current window size |
| Back / Escape | Return to the title screen or world |

The inspector owns a separate rectangle. It docks to the right at ordinary widths
and below the canvas at narrow widths. Its content scrolls independently and stays
in screen space while the tree zooms. It reports public availability, cost, and
AND-of-OR groups; material balances and Bench eligibility require the real menu's
matching server preview and are not inferred here.

Overview and browse detail use hysteresis: browse switches to overview below 0.48,
and overview switches to browse above 0.58. Detail changes do not rebuild geometry.
The two node treatments are measured before layout and deliberately have different
geometry. Native font width determines their bounded width and two-line labels.

The icon provider resolves only identity/icon-disclosed nodes through TaCZ's client
display API. It crops transparent PNG padding and preserves aspect ratio within
the measured art box. Missing or unsupported artwork uses the blueprint item.
Resource reload clears cached art measurements. Physical blueprint item rendering
is independent of this provider.

## Evidence

Automatic capture records 30 warmup frames and 60 sample frames per view, followed
by a framebuffer screenshot. The sequence covers both node treatments, overview,
target and branch focus, current/new canvases, world surface, nested branches,
unknown alternatives, and a dense graph. Mouse/key input interrupts the sequence;
resize ends it with `view_changed`. F9 starts a fresh timestamped run.

Each capture folder has `runtime.json` and native PNGs under `screenshots/`.
Reports bind each state to its public input fingerprint, measured node size,
screen/framebuffer dimensions, selected target, icon resolution count, and scene
counts. A `complete` capture only means the sequence finished; inspect each state's
failure field and screenshots before accepting the design.

Timing measures the prototype's screen-render CPU interval including its final
GUI flush. It excludes world drawing, frame wait, screenshot work, and GPU
completion. Current/new canvas samples use the same surrounding prototype screen.
They are not directly interchangeable with baseline timings for the entire
production Research Bench. Rebuild timing includes preparing both canvases and
artwork and is kept outside the sampled render interval.

Use native screenshots and user feedback to select a treatment before migrating
the production screen. Headless layout tests establish geometry and interaction
rules; they cannot establish native text/art readability or item draw depth.

## Local QA commands

An enabled prototype accepts JSON at `<output>/control.json`, consumes the file,
and writes `control-result.json`. Requests contain `action` and an optional string
`value`. Actions include `capture`, `snapshot` (a lowercase filename), `overview`,
`branch`, `target`, `tile`, `card`, `current`, `new`, `quiet`, `world`, `sample`
(fixture name), `select` (disclosed node ID), `scroll-inspector` (line delta), and
`reload`. Manual screenshots and reports go under `<output>/manual/`.

`resize` takes integer `width`, `height` (window units), and `guiScale`. It accepts
640–2000 by 360–1200 and scales 0–6. `restore` returns to the saved window size and
GUI scale. Back also restores these settings. `close` restores them and shuts down
the development client normally. These commands evaluate no code and cannot
purchase research.

Missing translations fall back to the disclosed blueprint ID's readable path.
The scrolling inspector includes the full name when its header cannot fit it.
Prototype routing uses two GUI units between tracks; the normal scene policy
retains its three-unit default. Both profiles use the same clearance validator.
Reports distinguish new-scene metrics from the displayed canvas's extent and node
size, including when the current canvas is selected.
