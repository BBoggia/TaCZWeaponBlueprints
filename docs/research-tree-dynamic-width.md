# Population-aware Research Tree width

The automatic Weapons tree no longer has to use one fixed semantic layer
width for every TaCZ catalog. Tree format 2 supports fixed and dynamic layout
policies; omitted fields preserve the previous fixed-width interpretation.

```json
"layout": {
  "width_mode": "dynamic",
  "min_nodes_per_layer": 9,
  "max_nodes_per_layer": 20
}
```

Both bounds must be between 8 and 28, and the minimum may not exceed the
maximum. `width_mode: "fixed"` requires equal bounds. For compatibility, a
layout containing only `max_nodes_per_layer` is fixed at that value.
The bundled tree intentionally remains at 9–20 so existing worlds retain their
current geometry; 21–28 is an explicit datapack opt-in for unusually large
weapon catalogs.

## Resolution rule

The server computes one effective semantic width before assigning automatic
ranks or generated prerequisites:

```text
population = authored weapons + eligible automatic weapons
effective width = clamp(minimum, maximum, ceil(sqrt(4 × population / 3)))
```

Only weapon nodes that can participate in the selected tree count. Excluded
automatic candidates, unplaced weapons, attachments, and ammunition do not
inflate the result. The calculation is integer-only, deterministic, independent
of catalog iteration order, and bounded before it reaches the layer planner.
The 4:3 factor intentionally favors a wider topology over the earlier square
target. During finalization, authored nodes reserve their occupied rank slots
before automatic nodes are placed, so the effective width bounds the combined
published topology instead of only the automatic subset.

For the built-in 9–20 policy, the transitions are:

| Topology population | Effective width |
| ---: | ---: |
| 0–60 | 9 |
| 61–75 | 10 |
| 76–90 | 11 |
| 91–108 | 12 |
| 109–126 | 13 |
| 127–147 | 14 |
| 148–168 | 15 |
| 169–192 | 16 |
| 193–216 | 17 |
| 217–243 | 18 |
| 244–270 | 19 |
| 271–4,096 | 20 |

Because the effective width is an input to semantic rank construction, a
larger pack can spread into broader ranks and allow specialization branches to
separate earlier. Responsive client wrapping remains presentation-only: a
narrow window may render one semantic rank as multiple visual rows without
changing rank identities, prerequisites, costs, or unlock authority.
Manual wheel/button zoom reaches 15%; Fit may still frame below that floor when
the complete canvas requires it.

## Compatibility and observability

- Existing format-1 trees remain fixed at their legacy default.
- Existing format-2 layouts that omit `width_mode` remain fixed.
- Manually authored trees may choose either fixed or dynamic width; automatic
  generation is not required.
- Attachment and ammunition research remains disabled by the packaged profile.
- Protocol 39 carries the resolved server width and the opt-in 21–28 capacity
  envelope to the client.
- Automatic topology version `tacz-gun-placement-v13` identifies the current
  gradual branch, terminal-cohort, and bounded local-prerequisite result.
- Export format 20 records the topology population, resolved width, width mode,
  configured bounds, canonical branch decisions, and finalized ranks.
  `/gg research status` reports the population and resolved visual-row capacity
  for the active revision-matched automatic publication.

Any future change to the population definition, formula, threshold behavior,
or point at which width enters topology generation must bump the automatic
topology version and update the deterministic threshold, export, network, and
artifact-verification fixtures together.
