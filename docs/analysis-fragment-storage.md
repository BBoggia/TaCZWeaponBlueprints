# Analysis fragment storage and reconstruction

Analysis fragments carry target-specific progress toward a physical blueprint. They preserve the recycling and learning permissions of the earned reward. They cannot supply Research Bench discounts or direct currency returns, even after the player learns the target.

The optional [Analyzer presets and migration guide](analyzer-presets-and-balancing.md)
explains how to enable reconstruction, adjust thresholds, and return to legacy
analysis while preserving saved counts and fragment identities.

**Research Data and tiered weapon analysis produce marked fragments; the Analyzer archives and reconstructs them.** Matching target and reward classes combine across both sources. Legacy weapon policies remain deterministic, and ordinary fragments keep their current preset. This document defines the storage and transaction API; the [Analyzer behavior contract](analyzer-behavior-contract.md) covers the workstation.

## Item identity

The existing `taczweaponblueprints:blueprint_fragment` item supports two identities. Ordinary fragments contain only the canonical string `BlueprintTarget`. Analysis fragments also contain the compound `taczweaponblueprints:analysis`:

```snbt
{
  BlueprintTarget: "tacz:ak47",
  "taczweaponblueprints:analysis": {
    format: 1,
    reward_class: "protected_prerequisites"
  }
}
```

The root must contain exactly those two fields. The marker must contain exactly an integer `format: 1` and a recognized string `reward_class`. Unknown versions, extra fields, missing values, and incorrect types invalidate the marked item. A present malformed marker never falls back to ordinary fragments.

| Saved class | Reconstructed blueprint | Learning mode |
| --- | --- | --- |
| `protected_prerequisites` | Cannot be recycled | Requires Tech Tree prerequisites |
| `protected_bypass` | Cannot be recycled | Bypasses Tech Tree prerequisites |
| `recyclable_prerequisites` | Can be recycled when normal policy permits | Requires Tech Tree prerequisites |
| `recyclable_bypass` | Can be recycled when normal policy permits | Bypasses Tech Tree prerequisites |

The marker records resolved reward permissions at creation. It does not store the station tier, holder, original weapon origin, odds, policy ID, policy revision, or random seed. Matching targets and classes can stack across stations and trades. Different targets or classes cannot stack. Item copies, splits, drops, and normal Minecraft item serialization retain the marker. Tooltip text identifies reconstruction-only use and the recorded reward class.

`BlueprintFragmentItem.identity` validates either identity. `getTarget` returns the target of either valid kind for discovery and presentation. Ordinary fragment consumers must use `getOrdinaryTarget`; it rejects every analysis-marked item. `createAnalysis` creates the marked variant. `AnalysisRewardClass.provenance` supplies the same permission definition for a full analysis reward or a reconstructed blueprint. Reconstruction records the existing `fragment_reconstruction` provenance source, retaining compatibility with the physical blueprint reader.

Current reconstruction policy can restrict recycling or require prerequisites. It cannot promote protected fragments into recyclable output or promote prerequisite-respecting fragments into bypass output. Disabled learning blocks reconstruction. The permissions are never recomputed from the current holder's weapon inventory or loot-origin status.

## Player-data version 5

The ordinary `ArchivedBlueprintFragments` list is unchanged. The additional `ArchivedAnalysisFragments` list contains one entry per target and positive class counts:

```snbt
{
  DataVersion: 5,
  ArchivedAnalysisFragments: [
    {
      Id: "tacz:ak47",
      Counts: {
        protected_prerequisites: 8,
        recyclable_bypass: 2
      }
    }
  ]
}
```

Missing class keys mean zero. Counts use integer tags and stable names, not enum ordinals. An empty target entry is omitted. Unknown classes, nonpositive values, incorrect types, invalid IDs, and overflowing totals are rejected. Repeated valid target entries are discarded rather than merged into additional progress.

The hard bounds are:

- 4,096 distinct targets across the union of ordinary and analysis archives.
- 1,000,000,000 total ordinary plus analysis fragments for one target.
- Four analysis reward classes per target.
- At most 8,192 serialized analysis entries inspected on load; a larger analysis list is ignored as a unit.

The configured analysis retention cap applies to the sum of the four analysis classes for a target. It does not apply separately to each class. The hard combined ordinary/analysis bound applies in addition to that configured cap.

Versions 0–4 load with an empty analysis archive. Valid ordinary progress loads first and is never displaced by analysis data. Other knowledge, RP, award history, criteria, and recent unlock data retain their existing migration behavior. Analysis entries that cannot fit are skipped without reducing ordinary counts. Version-4 readers cannot retain the new analysis archive.

Counts are raw progress. They do not store thresholds or completed-set totals. Changing a threshold or lowering a retention cap does not erase saved counts or create a blueprint on reload. Removed targets and disabled policies retain their saved entries. The existing capability clone path copies this data on both death and non-death clones.

## Shared target settings

Pack authors can define reconstruction independently of weapon-attempt eligibility through [shared reconstruction settings](research-data-policies.md#shared-reconstruction-definition). Explicit target and profile settings take precedence over existing Analyzer reconstruction; an enabled shared fallback supports owned fragments when weapon analysis uses legacy mode. Existing packs without shared settings retain their current rules. Threshold changes preserve archived counts, and current restrictions cannot grant an acquired fragment additional permissions.

## Mutation and reconstruction API

`IPlayerRecipeData.getArchivedAnalysisFragments` returns a detached immutable map. `AnalysisFragmentMutation` compares and replaces all four classes for one target through preflight, commit, and rollback operations. Ordinary mutations also enforce the combined limits. The three-map `replaceSupplementalProgression` overload replaces ordinary fragments, criteria, and analysis fragments together after validating every channel. The older two-map overload preserves existing analysis state.

External capability implementations retain empty defaults. Their default analysis mutation returns `UNSUPPORTED`, including when a reconstruction would consume a complete input set without changing the final archived count. Implementations must support the new mutation and replacement boundaries before accepting analysis fragments.

`AnalysisFragmentReconstructionService` evaluates a current target policy and a matching marked input. It accepts only the fragments that fit the remaining shared capacity. It then reconstructs at most one set from that input's class. Other classes cannot contribute to that set. Any excess input and archived progress remain available for subsequent actions.

An already archived complete set can reconstruct when no new fragments fit. Matching fragment input still identifies the target, but zero input items are consumed. An occupied output blocks a completed reconstruction before any deposit or set consumption. Partial deposits can proceed without an empty output when they create no item.

Commit re-evaluates the quoted policy and current input/archive state, checks output identity and provenance before mutation, and verifies the resulting input, output, and archive afterward. Failure restores the workstation and the affected target's class counts. A failed restoration reports `ROLLBACK_FAILED`. The service never credits RP, applies a Research Bench discount, or learns a target; already-known targets can reconstruct duplicates with their recorded protections.

The service takes a server-supplied policy with an explicit target and revision. Callers must acquire current catalog/policy eligibility, disable missing targets, validate the actual workstation and request token, and serialize transactions on the server thread. This API is not a client action authorization mechanism. Menu routing, request deduplication, and new weapon outcome production are integrated separately.

## Synchronization, inspection, and reset

Protocol 62 retains the supplemental analysis-count channel and adds Analyzer preview and result details. Clients and servers must use matching protocol versions. Only targets whose identity is already disclosed by the current Blueprint Journal or Tech Tree are included; analysis progress does not disclose a hidden target. Ordinary discount progress and public criteria remain separate fields.

Each analysis wire entry contains a bounded canonical target ID followed by four nonnegative VarInts in this fixed order: protected/prerequisites, protected/bypass, recyclable/prerequisites, recyclable/bypass. This order is explicit in the serializer and does not use enum ordinals. Empty entries, invalid totals, duplicate IDs, byte-budget overflow, and combined archive-limit overflow are rejected. Reordered chunks publish only after a complete valid snapshot arrives. Empty snapshots clear previously synchronized analysis counts; disconnect clears the accumulator.

`/gg progression inspect <player>` reports analysis target and fragment totals separately. Its per-blueprint inspection reports all four class counts. Fragment and full-progression resets clear both archives. Knowledge and RP resets preserve analysis progress. Capability NBT serialization includes analysis counts for saves, clones, and NBT-based exports. Research and ordinary fragment transaction rollback snapshots include the new channel.
