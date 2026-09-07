# Detached Research Designer compilation

Audience: developers integrating the Research Designer.

`ResearchDesignerCompiler` accepts an immutable project, an explicit catalog,
and an optional saved capture. It does not load or activate a datapack. It emits
an in-memory candidate containing normal resource definitions and a validated
`BlueprintResearchSnapshot`.

Each assigned node generates an exact format-5 rule in the project namespace.
The node ID determines the rule ID, so reordering nodes does not rename rules.
Requirement groups retain their AND-of-OR meaning. Placement and branch
membership are emitted separately using the format-3 entry codec. The project
profile must select its owned tree, which must use authored placement.

Only declared resource dependencies enter the candidate. Captured source
profiles and selector rules do not silently compete with the generated rules.
Declared resource, content and mod fingerprints must match the supplied capture.
Missing content remains an error; compilation never substitutes another weapon.
Profiles, policies and supporting resources pass through their normal codecs,
snapshot validation and catalog-dependent placement validation. The complete
resource map is decoded again before returning a candidate.

A result contains bounded diagnostics, the project digest, and an optional
candidate. A malformed or incomplete draft has no candidate. The state holder
retains the last successful candidate separately and rejects results for a
superseded requested digest. That older candidate is not proof that the current
draft is valid.

## Explicit scenario evaluation

`DetachedResearchEvaluation` provides the read-only policy resolution used by
both the live research manager and Designer scenarios. Its inputs include the
catalog, research snapshot, progression settings, knowledge, blacklist and
exemption predicates, and optional automatic prerequisite plan. It preserves
entry-point rebasing and learned-support connectivity without writing player
state or rebuilding a manager publication.

`ResearchDesignerPreview` uses the shared evaluator, normal public tree builder,
progression resolver and route planner. It accepts explicit settings and
knowledge. Route checks retain the planner's existing deterministic work budgets
and emergency timeout. Checking one route does not certify every possible
route or every inventory state.

`DesignerCapturedSettings` reconstructs the immutable progression and feature
settings written by capture. It does not construct a live config object or
assign global settings. Unknown or incomplete captured settings are rejected.

## Validation boundaries

Compilation and scenario evaluation are separate results. A compilable graph is
not an export or activation certificate. Missing captured item/tag facts block
cost validation; an empty known tag produces a warning. Captured facts describe
the capture time, not a guarantee that a server still has the same dependencies.

Inventory affordability, external gate providers, survival supply and runtime
activation require their own explicit checks. Keep unassessed conditions visible
rather than treating an acyclic graph or successful codec parse as complete
behavioral validation. The compiler never invokes purchases, awards, item
delivery, reload, manager publication or player-data writes.

`DesignerCapturedContext` reconstructs catalog metadata and source snapshots
without installing a gun pack or executing a recipe. Captured recipe IDs and
output counts are retained; the metadata catalog has no executable recipe
objects. New captures also retain positioned automatic candidates and weapon
scoring evidence so progression and crafting can be evaluated offline. Older
captures without that evidence require a refresh for those comparisons.

`DesignerCaptureComparison` compares the captured source and candidate using
explicit knowledge scenarios. It covers research policy fields, public graph
membership and requirements, domain/rank placement, progression and ordinary
fragment policy, crafting, Analyzer tiers/origins, and Research Data pools.
Resource-selection provenance is excluded from policy equality; the fragment
threshold's exact/inherited distinction is retained because it affects loot.
The comparison is bounded to 16 scenarios, 1,048,576 estimated evaluation units
and 1,000 reported differences. A difference can represent an intentional edit;
this report is not activation approval or proof of survival obtainability.

`ResearchIngredientPlanner.allocationFromFacts` shares the live allocator while
accepting explicit item IDs, counts and tag memberships. Designer quote checks
combine physical currency and materials in one allocation, so a stack cannot
pay both requirements twice. Simulated inputs are limited to 256 slots and
4,096 requirements. A successful allocation establishes affordability of that
quote, not workstation access, external gate satisfaction or output delivery.

## Property and scenario integration

`DesignerResearchProperties` replaces a single selected-rule property while
retaining unrelated fields. Inheritance is evaluated by compiling a separate
candidate with only that override removed. Runtime policy, progression, crafting
and Analyzer resolvers provide effective values from the saved capture.

`DesignerSimulation` owns immutable synthetic knowledge and inventory facts. It
uses `ResearchDesignerPreview` for disclosure and route selection and requires a
complete shared ingredient allocation before a synthetic unlock. External gate
providers and world delivery are deliberately unassessed. The native player
view receives only the disclosure-safe publication; authoring details remain in
the separate scenario report. Worker results are rejected after navigation,
replacement or screen removal.
