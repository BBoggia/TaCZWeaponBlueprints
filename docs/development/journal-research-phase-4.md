# Journal and Research Phase 4: Synchronized Runtime Policy

## Scope

Phase 4 adds the coarse server-policy layer shared by the future Blueprint
Journal, duplicate-recycling transaction, and Research Bench. Fzzy Config owns
the synchronized operator choices while Phase 3 datapacks continue to own
profiles, target selection, costs, values, discovery requirements, and
prerequisites.

This phase does not add a Journal screen, recycling transaction, Research Bench
block/menu, ingredient consumption, or client-authored progression actions.

## Synchronized configuration

The existing `blueprint` Fzzy Config is registered as `BOTH`, so its permitted
server values are synchronized by Fzzy Config. Phase 4 adds:

- Journal enablement;
- a maximum visibility ceiling for unlearned entries;
- research enablement;
- duplicate policy (`KEEP` or `MANUAL_RECYCLING`);
- permission for datapacks to enable unlearned recycling;
- a Research Point cap from 0 through the hard one-billion-point limit;
- permission for datapacks to enable creative research-cost bypass;
- a bounded active research-profile resource ID.

The default point cap is 1,000,000. The default active profile remains
`taczweaponblueprints:duplicate_recovery`. Manual recycling never converts an
item automatically; it only permits the explicit transaction planned for a
later phase.

## Atomic runtime publication

`BlueprintConfig` publishes all progression-related fields as one immutable,
volatile `BlueprintProgressionConfigSnapshot`. Initial load, server edits,
client edits, and both Fzzy synchronization callbacks replace the complete
snapshot. Runtime readers therefore cannot combine half of an old progression
policy with half of a new one.

Discovery reads the same publication. Disabling blueprints or discovery
tracking suppresses new discovery without deleting learned IDs, discovered IDs,
or Research Points.

## Policy composition

Policy resolution has two deliberate layers:

1. Phase 3 resolves the active datapack profile and its most-specific rule.
2. Phase 4 applies the synchronized coarse gates to that immutable result.

Coarse configuration can disable or restrict datapack behavior but cannot grant
behavior a datapack disabled. In particular:

- global blueprint disablement suppresses Journal, research, recycling, and new
  discovery behavior while preserving stored progression;
- learned entries remain `FULL` when the Journal is enabled by the underlying
  policy;
- unlearned visibility is capped at the configured ceiling;
- both configuration and the selected datapack policy must permit research,
  recycling, unlearned recycling, or creative bypass;
- missing configured profiles resolve to the existing disabled policy;
- research costs above the active point cap are not researchable or affordable;
- recycling is not eligible when its complete award would exceed the active
  point cap.

Lowering the point cap never truncates an existing saved balance. It prevents
additional credit until the balance is again below the cap, preserving the
Phase 0 no-silent-data-loss contract.

## Reload and diagnostics

Research datapack application now checks the configured active profile instead
of only checking the built-in default. A missing active profile is reported and
default policy evaluation fails closed. Same-ID datapack replacement and the
last-known-good snapshot behavior from Phase 3 are unchanged.

## Networking

The mod network protocol remains `4`. Phase 4 adds no new custom packet and no
client-authored authority. Fzzy Config already synchronizes the coarse values;
the later disclosure-filtered Journal snapshot will receive its own deliberately
versioned packet when the Journal presentation model is implemented.

## Verification

Phase 4 adds tests for:

- configuration defaults and hard point-cap validation;
- global fail-closed composition without mutation of the datapack policy;
- maximum undiscovered visibility;
- cost eligibility under a lower point cap;
- recycling overflow prevention;
- `KEEP` and unlearned-recycling gates;
- packaged presence of the immutable snapshot and duplicate-policy enum.

The full build, release-artifact verifier, publication-readiness verifier,
dedicated-server startup, and client mixin startup remain the release gates.

The completed Phase 4 tree passes 91 automated tests. The dedicated server
loaded the expanded config, applied research snapshot revision 1 with the
configured built-in profile, rebuilt 481 catalog entries, and reached `Done`.
The client loaded the same config schema, applied both gunsmith mixins, created
the normal texture atlases, and reached the render loop. Remaining malformed
recipe, language, sound-path, and Realms messages are the previously documented
third-party-pack or unauthenticated development-client warnings.

## Deferred to later phases

- disclosure-filtered Journal entries and completion summaries;
- Journal packet chunking and screen UI;
- manual recycling item/point commits;
- Research Bench registration and atomic item/point transactions;
- operator progression inspection and reset commands.
