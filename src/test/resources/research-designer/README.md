# Research Designer contract fixtures

These are version-1 project examples and synthetic catalogs used by
`ResearchDesignerContractTest` and the production reader/storage checks in
`ResearchDesignerProjectTest`. They are editable headless project fixtures;
the native workspace and datapack compiler remain future work.

`cases.json` specifies the expected graph/cost outcomes. The runtime fixture
adapter deliberately supports a small fixed subset and uses real research
codecs, snapshot validation, and player graph construction. Production import,
conversion, simulation, and export will be checked against these inputs later.

The automatic-conversion capture is synthetic, marked `fixture_only`, and pins
the desired explicit custom result. `large_addon` has 512 deterministic nodes
from two invented content packs; it is unrelated to the real user's world.
`baseline.json` records observations from the existing scene performance tool.

See [project format](../../../../docs/research-designer-projects.md),
[field mapping](../../../../docs/research-designer-mapping.md), and
[validation](../../../../docs/research-designer-validation.md).
