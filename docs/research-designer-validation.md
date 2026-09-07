# Research Designer validation contract

Audience: developers implementing the editor and pack-author validation tools.
Status: project/session/storage, bounded capture/conversion, detached compilation,
and explicit runtime scenario evaluation are implemented. No Designer UI or
publish operation is available. See the [capture contract](research-designer-capture.md)
and [compilation contract](research-designer-compilation.md).

## Semantic equivalence

The comparison chain is capture → custom project → compiled candidate → emitted
bytes → normal loaders → resolved policy/player projection. Successful JSON
parsing is necessary but insufficient. Compare the following using explicit
captured settings and synthetic player scenarios:

1. Catalog identities, enabled/omitted/exempt targets, domain membership, roots,
   canonical AND-of-OR groups, cross-domain links, rank legality, and branch
   ownership/disclosure. Normalize only intentionally remapped resource IDs.
2. Research costs, ingredient predicates, recycling and analysis policies,
   visibility/discovery, Research Bench tiers, scoped gates, crafting dispositions,
   and preserved ordinary/analysis fragment and Research Data behavior.
3. Public node identities, names/icons allowed to appear, hidden counts,
   navigable routes, selected path, aggregate payment, and readiness under
   synthetic inventory/workstation/knowledge facts.
4. Cases with out-of-order learned support, shared prerequisites, both learned
   OR alternatives, exemptions, direct learning versus blueprint creation,
   currency also matching a material predicate, and stale/missing dependencies.

Material demand is compared as predicates plus quantities, not item display
names. Optional-field absence is not automatically equal to an explicit false,
zero, empty list, or disabled policy. Canonicalize set-valued alternatives;
preserve semantically ordered entry candidates and band definitions.

Do not execute live purchase services, player capability writes, award callbacks,
item delivery, global config edits, manager publication, or `/reload` to validate
a draft. Runtime evaluation must be extracted into detached read-only functions
before preview or conversion can claim this complete equivalence.

An automatic snapshot is only equivalent in its captured context. Generation
after a gun-pack/scoring change is a new result. Preserving a graph is not a
promise that every survival player can obtain its required items or bench.

## Foundation fixtures

Tracked inputs live in
[`src/test/resources/research-designer`](../src/test/resources/research-designer).
`cases.json` names their catalogs and expected public graph outcomes. Projects
use the version-1 contract; `synthetic-resolved-capture.json` is deliberately
marked `fixture_only` and is not presented as a real server export.

| Fixture | Contract exercised |
| --- | --- |
| Blank | Empty but saveable draft, nonexportable until research is assigned. |
| Unassigned | A stable draft node can exist before choosing its blueprint. |
| Single | One valid root and exact blueprint identity. |
| Split/merge | Two mandatory paths joining one shared node. |
| Inclusive OR | Either route plus a separate mandatory support requirement; learning both routes remains valid. |
| Nested | Visual containment and ownership without changing progression. |
| Cross-domain | Weapon → ammo → attachment requirements retain real directed links. |
| Hidden | A secret prerequisite remains absent from public identities and edges. |
| Missing | An unresolved target stays named in the draft and is not replaced. |
| Automatic conversion | A synthetic resolved reference pins the intended explicit custom result; production conversion is later work. |
| Large add-on | 512 nodes from two content namespaces in a reproducible branching graph. |

`ResearchDesignerContractTest` translates only the fixed fixture subset into
existing codecs and snapshots. It checks graph counts/costs, choice semantics,
hidden identities, branch invariance, version/field inventory, synthetic digest
changes, diagnostic references, and invalid cycles/cost amounts. This helper is
test support, not the production compiler. `ResearchDesignerCompilerTest` runs
these fixtures through the production compiler, including converted captures,
codec round trips, incomplete drafts, stale results and learned OR support.

`ResearchDesignerProjectTest` runs every draft through the production reader and
local store. It covers malformed UTF-8/JSON, duplicate keys/IDs, version guards,
pending text, grouped edits, deletion references, undo/redo, navigation isolation,
Save As, damaged saves, injected write failures, checksums, stale writers, file
containment, and autosave after undo. Tests use temporary game directories and
also preserve the bundled Research Data/reconstruction resources through edits.
The 512-node case records editing/save/reopen timings and checks history/file
limits without imposing portable CI timing assertions.

Capture and compiler checks have separate production coverage. Exported datapack
paths and activation remain future acceptance requirements; local project tests
do not certify those operations.

## Performance baseline and budgets

Run `./gradlew researchTreePerformance` with Java 17. It uses pinned public
fixtures, including the 287-node live projection, connected 512-node graph,
dense 256-node graph, and runtime collection extremes. It measures current-thread
CPU/allocation for geometry, routing, validation, and navigation. It does not
measure GPU rendering, native input latency, capture or detached compilation.

The tracked [baseline summary](../src/test/resources/research-designer/baseline.json)
records fixture hashes, hardware/JVM context, sample counts, and observations.
The full run log/report remains local development evidence. No build or test
requires that local evidence directory.

Proposed same-machine regression budgets derive from observed scene/query p95:
25% headroom, rounded up to 5 ms for scene rebuild and 0.5 ms for 200 navigation
queries. These are future editor integration targets, not portable CI timing
assertions. Re-measure on baseline hardware if a target fails; keep the same
input hash, sample method, JVM, and renderer policy when comparing results.
Small samples describe this run and do not establish universal latency claims.

The dense fixture is substantially slower than an ordinary frame. Dragging must
use a lightweight overlay, commit one semantic command, and debounce/cancel
superseded rebuilds. The 65,536-edge maximum fixture is currently unavailable
under routing limits: structural bounds alone do not promise a renderable scene.
The Designer must retain the draft and show an explicit complexity/rendering
diagnostic rather than claim an accurate preview or silently drop connections.

Capture, compiler, IO and native-frame measurements describe different paths;
scene CPU timings cannot establish their budgets. Changes to the native
workspace require real-window captures and interaction checks, including small
GUI sizes, incomplete drafts, recovery and resource reloads.

## Publishing and progress boundary

Export validation includes declared dependencies, current settings assumptions,
generated resource references, codec round-trip, and an explicit activation
report. A successful file write does not prove a server selected the profile.

Future publishing must validate on the server, bind a reviewed candidate to its
baseline state, and reject stale edits. Recovery restores managed resources and
reviewed settings; it cannot automatically undo player purchases or other mods'
reload side effects. Changing labels/branches never changes blueprint IDs or
resets saved knowledge, currency, discoveries, or fragments. Whole-world
progression migration remains a separate explicit action.
