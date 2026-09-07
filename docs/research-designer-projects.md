# Research Designer project contract

Audience: developers implementing the Research Designer and pack-author tools.

Status: the version-1 project reader, editing session, undo/redo, local storage,
operator capture, and explicit custom-tree conversion are implemented. The
detached compiler is described in [compilation](research-designer-compilation.md).
The native workspace and publishing UI remain future work. Tracked fixtures
exercise storage, capture/conversion, compilation and existing research loaders.

The Research Designer uses three different artifacts: an editable project, a
captured game context, and a compiled datapack. A catalog export is evidence
about resolved behavior; it cannot recover every original rule or selector.

The numeric versions, bounds, capabilities, and diagnostic vocabulary are in
[contract.json](contracts/research-designer/contract.json). Field ownership is
defined in [the mapping contract](research-designer-mapping.md), and behavioral
comparisons in [the validation contract](research-designer-validation.md).

## Project version 1

Projects are UTF-8 JSON. Read at most 16 MiB before parsing, reject duplicate
object keys, nonfinite numbers, control characters in identifiers/titles, and
nesting deeper than 64. Counts are integral JSON numbers. Do not silently round,
truncate, substitute IDs, ignore unknown top-level fields, or accept partial
parser results. Unknown project/extension versions open read-only when safe
inspection is possible; they cannot be rewritten or exported as version 1.

| Field | Type and meaning |
| --- | --- |
| `format` | Integer `1`. Independent of datapack resource versions. |
| `project_id` | Canonical lowercase UUID. Stable across edits and normal saves. Save As creates a new project ID. |
| `namespace` | Valid lowercase Minecraft resource namespace, at most 64 characters. Generated IDs use this namespace. |
| `title` | Trimmed nonempty text, at most 80 characters; not an identity. |
| `origin` | Object described below; conversion provenance, never publishing authority. |
| `dependencies` | Object with `mods`, `content`, and `resources` arrays. |
| `settings_assumptions` | Expected Blueprint settings. Blank projects may use a field-name map; captured projects retain `raw` and `effective` objects, activation profile, capture fingerprint, and required-equivalence-check flag. |
| `draft` | Editable profile, tree, and nodes described below. |
| `supporting_resources` | Array of resource records needed by the managed profile. |
| `extensions` | Map from namespaced extension ID to `{version, data}`. Empty for the core fixtures. Unknown extensions prevent editing/export until supported. |

`origin.kind` is `blank`, `captured`, or `opened`. A captured origin requires
`capture_id`, a 64-character lowercase hexadecimal `content_fingerprint`, and
`conversion: "resolved_to_custom"`. An opened origin can retain its original
capture fields. No original host paths, network address, account credentials,
or player save records belong here. The origin fingerprint describes evidence;
it is not a signature and a server must not trust a client's claimed digest.

Dependency records use `{id, version}` for mods; `{id, fingerprint}` for content
packs; and `{kind, id, fingerprint}` for external resources. Version is a string
describing the expected build/range; the later compatibility resolver must not
equate two different version schemes. Fingerprints use the capture procedure
below. `kind` is one of the supported resource families in the mapping contract.
Duplicate dependencies are invalid. A missing dependency is a draft problem,
not a reason to discard its identity or replace it with similar content.

### Draft source and ownership

`draft` contains exactly `profile`, `tree`, and `nodes` in version 1:

- `profile` is `{id, definition}`. `id` is the managed research profile resource
  ID. `definition` contains the complete supported profile source, including
fields not editable by the first UI. Its `tech_tree` identifies `draft.tree.id`.
- `tree` is `{id, definition}` with a complete supported tree source. Custom
  projects use `weapon_placement_mode: "authored_only"`. Branch declarations
  live in `definition.branches`; each branch has its own stable resource ID.
- `nodes` is an ordered array of the records below. Array order is retained for
  editing, but `placement.rank` and `placement.order` control runtime placement.

Resource IDs must be canonical `namespace:path` strings within the existing
256-character bound. The managed profile/tree and generated rule resources use
the project's namespace. Referenced blueprint IDs retain the content pack's
namespace. Profile, tree, and blueprint IDs are not interchangeable.

| Node field | Meaning |
| --- | --- |
| `id` | Stable editor ID, 1–64 lowercase ASCII letters, digits, `_`, or `-`; UUID strings are recommended. IDs are unique across nodes and requirement groups in a project. |
| `blueprint_id` | Optional canonical blueprint ID. Omitted for an unassigned placeholder; an unknown ID is retained as an unresolved reference. |
| `placement` | Normal placement fields except `target`: `domain`, `lane`, `tier`, optional `level`, `rank`, `order`, and any preserved supported `rating`, `tier_override_reason`, or `fallback` fields. Missing/incomplete values can be retained in a draft but block candidate compilation. |
| `branch_id` | Optional direct branch owner. Membership is generated from this field; no second membership list is stored. |
| `policy` | Supported research-rule fields except `format`, `profile`, `priority`, `target`, `prerequisites`, and `prerequisite_groups`. Omitted optional fields inherit from the managed profile. Preserve nested crafting, Analyzer, recycling, and progression data. |
| `requirements` | Array of `{id, any_of}` groups. Every group is mandatory; each group accepts any one of its alternatives. An empty array makes this node a root. |

`any_of` contains blueprint IDs, including explicit unresolved references.
An unfinished empty group is saveable but invalid for compilation. It does not
mean an automatically satisfied requirement. Runtime groups omit editor IDs.
Alternative order is presentation/source intent; runtime comparison
canonicalizes alternatives without changing their inclusive meaning.

Compilation supplies an exact target, the managed profile ID, a generated rule
ID derived from the stable node ID, and priority zero within this independent
profile. The compiler chooses a compatible explicit rule format, translates
groups to `prerequisite_groups`, and generates placement/membership bundles.
Legacy mandatory prerequisite lists are converted to singleton groups once
during import. They cannot coexist as a second editable source of requirements.

Supporting records have `{kind, id, definition}`. They preserve required tags,
secondary profiles/rules, ordinary fragment policy, crafting policy, Research
Data, reconstruction, and other referenced resources. They cannot collide with
the managed profile/tree or generated IDs, add competing active-profile rules,
or attach an automatic placement profile to the managed custom tree. Such
conflicts require explicit conversion or remain nonexportable. Preserve useful
source provenance in the capture, not as extra runtime JSON fields.

### Incomplete input and recovery

A well-formed project is not necessarily a valid research tree. Empty projects,
missing blueprints, temporarily empty branches, unresolved groups, and rank
conflicts remain saveable. The initial Designer requires at least one resolved
research node for export; deliberately disabling an entire tree is a settings
operation, not an accidental empty-project export.

An unparseable text-box value lives in recovery/session `pending_inputs`, keyed
by editor object ID and field path, while source retains its prior typed value.
Pending inputs block preview/export acceptance until resolved or discarded.
They must not silently export the previous value. Recovery files store draft
content plus pending input and a monotonic edit sequence. The shareable project
file contains valid JSON source only.

## Local storage and identity

Reserve **Research Designer** for the workspace title and **Open Research
Designer** for its Controls action (`key.taczweaponblueprints.open_research_designer`,
under `key.categories.taczweaponblueprints`). The key is unbound by default to
avoid taking an existing gameplay shortcut. The client command `/researchdesigner`
opens the project chooser; it does not share the privileged server `/gg research`
command path. These entry points are for the later native workspace, not commands
available in the foundation release. Opening a local draft requires no Research
Bench or operator status; requesting full server data checks permission separately.

Use `<gameDir>/taczweaponblueprints/designer/projects/<project_id>/project.json`.
Recovery and view preferences are separate files in that project directory;
camera position/selection are not research edits. Exports use
`<gameDir>/taczweaponblueprints/designer/exports/` and never an implicit world
datapack directory. A supplied filename is not a trusted filesystem path.

Normal saving preserves project, blueprint, branch, and editor identities.
Save As allocates a new project directory/ID and retains blueprint IDs; choosing
a new output namespace remaps owned resource IDs and every corresponding
reference together. A display-name edit never changes a blueprint identity.
Remote sharing cannot include an unrestricted filesystem destination.

Project content is the source of truth. History is session state with bounded
recovery checkpoints, not a required event log for replaying a shared project.
Support atomic replacement where available, a recoverable previous save, and
checksummed recovery records with explicit schema versions. Limits on autosave
frequency and retained history are implementation choices for the project
storage milestone and must be tested against real write/undo sizes.

### Headless editing and storage API

The common `research.designer` package contains `ResearchDesignerProject`,
`ResearchDesignerDraft`, `ResearchDesignerEdits`, and `ResearchDesignerSession`.
`research.designer.io.ResearchDesignerProjectStore` handles local files. These
classes do not acquire a Minecraft client, publish definitions, or change player
progress. Their contracts and fixtures are available in the normal source sets.

`ResearchDesignerProject.open(bytes)` returns the source plus either an editable
project or read-only reasons. Malformed JSON, duplicate identities, fractional
integer units, and hard document limits fail explicitly. Known but incomplete
source remains editable; full structural/context validation belongs to the
compiler. Runtime strict codecs help identify unsupported source fields without
rewriting the retained source or treating their decoded defaults as author intent.
Unknown extensions and source versions retain an inspectable read-only envelope.

Projects and typed draft identities are immutable. JSON accessors return copies.
Use `ResearchDesignerEdits.nodeField` to change a placement/policy leaf while
retaining unrelated fields. Complete definition/policy replacement is also
available for deliberately replacing source. `batch` commits one validated
document or leaves the original intact. Missing content and unfinished branches
do not require a live catalog to save.

Sessions are thread-confined. `execute` records one edit; `beginEdit`,
`commitEdit`, and `cancelEdit` group a drag or continuous input into one history
entry. Undo/redo increments the edit sequence, including when returning to the
saved document. View changes are independent of history, dirty state, and the
edit sequence. Preliminary `problems(catalogIds)` reports placeholders,
incomplete placement, missing requirements, and pending text; an empty result
does not authorize export or claim compiler validation.

`integerInput` takes the field's actual bounds and an edit factory. Invalid or
out-of-range text becomes a pending input; valid input and removal of its pending
text form one undo step. Deleted objects' pending text is retained until the
user restores the object or deliberately discards that text. Node deletion reports
remaining source references without reconnecting prerequisites. Ungrouping a
branch clears its direct members' ownership and promotes child branches to its
parent. Deleting a branch's subtree removes its descendants and directly owned
nodes, while reporting references that remain elsewhere.

Implemented local limits:

| State | Limit |
| --- | --- |
| Undo/redo | 100 entries and 64 MiB of accounted serialized snapshots combined; not an exact JVM heap measurement. Oldest entries are evicted first. |
| Pending inputs | 1,024 fields, 4,096 UTF-16 characters per field, 1 MiB of UTF-8 text in total. |
| Autosave attempts | At most once every five seconds per session; unchanged checkpoints are skipped. Returning to saved source still updates an obsolete recovery record. |
| Recovery file | 32 MiB, format 1, SHA-256 checksum over the encoded payload. Two wrapper levels are allowed in addition to the project's depth limit. |
| View preferences | Separate 16 KiB format-1 file with finite camera coordinates. |
| Retained disk history | Current and previous project/recovery files. Abandoned temporary files with the store's exact UUID naming pattern are removed while holding the project lock. |

`save` writes recovery before replacing `project.json`, so a stopped save can
retain its pending text and draft. Files are synced, replaced atomically where
supported, and backed by a previous version. Directory syncing is best effort on
filesystems that do not support it. Checksums detect damaged recovery data; they
are not authentication. A valid newer-format project or recovery file prevents
an older editor from overwriting it.

`load` reports fallback, damaged, stale, or unsupported files. The caller chooses
the saved document or the offered recovery through `Loaded.session(recover)`.
Choosing saved source does not immediately delete the recovery file; the next
checkpoint records that choice. Both saved-source and recovery-file digests
protect writes against another editing session. A conflict leaves the current
in-memory draft intact. Reopen/review or use Save As to preserve a separate copy.

Save As creates a new UUID directory and retains the source namespace and all
blueprint/branch/node IDs. Project-scoped pending inputs receive the new project
ID. Namespace changes require the future compiler's complete owned-reference
mapping before that option can be exposed. View state transfers in memory and
can be saved independently. No supplied filename becomes a filesystem path;
directory and file symlinks are rejected beneath the canonical game directory.

The store provides synchronous session helpers and immutable `Checkpoint` values
for IO handoff. A future native adapter must serialize writes and deliver storage
receipts on the owning session thread. Stale receipts cannot regress the saved
baseline. Autosave timing is supplied as monotonic nanoseconds; no background
thread or scheduled disk activity starts merely by constructing a project.

## Capture version 1

A full server capture is read-only and requires operator permission level 2 by
default. Local project editing requires no server permission. Server-side draft
evaluation is a separate privileged capability. Publishing is unavailable
until its own milestone and cannot be inferred from a capture token.

Production capture fields:

| Field | Contents |
| --- | --- |
| `format`, `capture_id` | Format `1` and an opaque UUID. |
| `source_kind`, `disclosure` | `server` and `operator_full`. Synthetic fixtures use `synthetic_fixture`; they are not complete server evidence. |
| `session_token` | Opaque request UUID, bound to the current connection; contains no host address. |
| `revisions` | Catalog, research, automatic publication, config, access, and resolved tag revision/digest identities. Coherent for the complete capture. |
| `profile_id` | The selected active profile. |
| `catalog_export_format` | Current resolved-export format, presently 22. Does not turn the capture into an export reader. |
| `definitions` | Source resource records plus origin resource/pack IDs and content fingerprints. No raw host paths. |
| `settings` | `raw` and `effective` named settings plus resolved access/currency/progression policy facts. |
| `catalog` | Researchable blueprint identities, recipe/kind/subgroup facts, display references, inclusion/exemption decisions, and required registry/tag facts. |
| `resolved_nodes` | Effective topology, placement, research costs/gates, selected source rules, branch membership, and resolved crafting/Analyzer/fragment/Data policy facts used by conversion/equivalence. |
| `registry_facts`, `dependencies`, `conversion_notes` | Detached recipe/item/tag/advancement facts, mod dependencies, and conversion notices. |
| `dependency_fingerprint` | Digest of the coherent semantic context. |
| `context_completeness` | `complete` for a production authoring context; `fixture_only` or `incomplete` cannot claim runtime-equivalent conversion. |

Source records and resolved facts must both be captured. A cost snapshot alone
cannot reconstruct a selector, an omitted inherited field, conditional gates,
or existing reward permissions. These families' payloads retain their current
typed runtime meanings; implement field-by-field capture adapters rather than
serializing arbitrary live objects or a player's complete capability.

Acquire catalog/research/automatic/config/access/tag inputs from a coherent
server context. Recheck identities before returning it. On a change, retry
within a bound or return `stale_capture`; never mix revisions. Server registries
and mutable game objects must be captured on their required thread. Detached
immutable data can be evaluated afterward.

Fingerprint algorithm: SHA-256 of normalized UTF-8 semantic JSON. Sort object
keys and keyed definition/catalog records; normalize integral numbers; omit
session counters, capture/editor IDs, timestamps, translated display strings, and
filesystem paths. Preserve meaningful list order (entry candidates, branch and
band order, ingredient alternatives where source order is meaningful). Include
definition contents, referenced content identities, resolved tag membership,
recipe associations, relevant settings, and automatic scoring model/reference
versions. Equivalent captures from different sessions must share a fingerprint;
changes to any semantic dependency must invalidate it. Resource fingerprints
apply the same procedure to the referenced definition/content facts.

The synthetic fixture hashes only its catalog and resolved node subset, sorted
by blueprint ID with editor/group IDs removed. Its ASCII strings and integral
numbers do not define canonical encoding for a complete production capture.
Production capture tests separately cover Unicode, decimal normalization, keyed
record order, dependency changes, and meaningful array order. Currency revision
counters and provenance labels are excluded from the semantic hash.

Captures are bounded at 32 MiB, with incoming lengths checked before buffer
allocation. Oversized source closures return a limit status; they never truncate
rules or requirements. Protocol 63 implements bounded, uncompressed transfers
with cancellation and final authorization. See the [capture contract](research-designer-capture.md)
for request limits, offline storage, and conversion behavior.

Full captures contain operator-only research details. Ordinary public tree
packets and player previews cannot request or return them. Permission loss,
disconnect, or cancellation invalidates pending privileged replies. Sharing a
full authoring project is a deliberate export action and exposes its authored
research; simulated player views still apply normal disclosure rules.

## Diagnostics and lifecycle

Diagnostic version 1 contains `format`, stable `code`, `severity`, `stage`,
`project_revision`, `object_id`, JSON `pointer`, `message_key`, primitive
`arguments`, blocking-stage names in `blocks`, and suggested action IDs in
`actions`. Codes are stable identifiers; prose is localized. Do not embed
server paths, hidden facts in a player view, or executable fix commands.

Severity is `error`, `warning`, or `info`. Stages are `document`, `structure`,
`context`, `simulation`, and `export`. A diagnostics list is bounded at 1,000;
an omitted-count summary must remain visible and blocking errors must never
disappear behind truncation. Deduplicate by code/object/field and revision.

The lifecycle is: open/new → draft edits → document validation → structural
candidate → context evaluation → simulated player projection → export review.
Any new edit invalidates later stages for that edit sequence. A last valid
scene can remain visible only with an explicit stale marker; it cannot supply
an export success or a new simulated action. Saving drafts does not require a
valid runtime candidate. Closing or disconnecting preserves local draft work.

Bounds in the machine contract cap drafts and IO, not a promise that every
bounded graph can render or plan a research path. Runtime definition, branch,
ingredient, routing, and planner limits still apply independently.
