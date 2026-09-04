# Blueprint Fragment Supply

Blueprint Fragments are weapon-specific loot items that use the same server
rules as full blueprints. They replace part of the existing blueprint supply
instead of adding an unrestricted second loot stream.

This document covers fragment identity, loot generation, Blueprint Analyzer
archiving, and the results of completing a set.

## Item identity

The mod registers one `taczweaponblueprints:blueprint_fragment` item. Each valid
stack stores exactly one canonical target blueprint ID. Fragments stack to 64,
but only stacks with the same target data combine.

The item name and tooltip show the target's current catalog name. A malformed
stack is labeled as invalid. A valid fragment whose target is no longer in the
loaded catalog is labeled as unknown without exposing a removed or hidden
identity through fallback text. Carrying a valid fragment records the same
bounded blueprint discovery as carrying its full blueprint.

## Loot replacement

`fragmentLootReplacementPercent` sets the share of successful blueprint rolls
that can become fragments. The default is 20 percent. A replacement consumes
the same per-table roll and the same shared 64-item research-loot budget as a
full blueprint, so enabling fragments does not increase the number of research
items added by the loot modifier.

A fragment can replace a roll only when all of these conditions hold:

- the active blueprint loot rule produced at least one valid candidate;
- the candidate passed the existing gun, ammo, or attachment blacklist and
  selector rules;
- the active research profile includes a current fragment-enabled policy for
  the target; and
- the catalog, research tree, progression policy, and synchronized server
  settings describe the same loaded revision.

If fragments are disabled, the candidate pool is empty, or the publications do
not match, the original full-blueprint roll remains in effect. Invalid fragment
state never makes an otherwise valid loot table fail.

Both current datapack loot rules and legacy generated modifier data follow this
replacement policy. When a datapack owns a loot table, its current rule remains
authoritative and the legacy modifier stays inactive as before.

## Target weighting

Fragment selection begins with the effective weights from the blueprint loot
rule. Pre-generated containers do not identify a player, so they retain those
catalog weights exactly and produce deterministic candidate ordering.

When the loot context identifies a server player, unlearned targets receive a
strong preference and learned targets remain possible at a reduced weight.
Keeping a small learned-target share supports the configured RP result for
completed learned sets without letting it dominate progression loot. The
selection never treats a nearby player as the owner of a pre-generated
container.

Repeated candidate IDs are combined before selection. Non-finite, non-positive,
missing, excluded, and fragment-disabled candidates cannot enter the resolved
pool.

## Thresholds and diagnostics

Every resolved candidate retains its effective Research Bench tier, fragment
threshold, and whether that threshold came from an exact override. Automatic
targets therefore use the same tie-aware workstation-tier assignment as the
rest of the progression policy. An exact rule or
`exactFragmentThresholds` setting remains visible as exact diagnostic evidence.

Use `/gg loot preview <loot_table>` to inspect:

- whether a revision-matched fragment policy is available;
- the configured replacement share;
- the number of fragment targets;
- whether the preview has player-aware weighting;
- expected fragment drops for that rule;
- target counts grouped by threshold; and
- the number of exact threshold overrides.

Use `/gg research inspect <blueprint_id>` or `/gg research export` for the
resolved tier, threshold source, and complete per-target progression policy.

## Archiving fragments

Put one target's fragments into the Blueprint Analyzer to preview the current
archive, how many fragments the stack can add, the completed-set threshold, and
the result that will be applied. The server accepts only the portion that fits
the configured per-target retention cap; rejected fragments remain in the
input slot.

Confirming the action is one server-authoritative transaction. The Analyzer
rechecks the item target, stack count, output slot, RP balance, archive value,
and current progression policy before it changes anything. If item consumption,
archive storage, RP credit, or reconstructed-blueprint output fails, it restores
the input, output, RP, and fragment state together.

Changing a threshold does not rewrite stored progress. The mod retains the raw
fragment count and evaluates complete sets against the current threshold. This
avoids rounding loss when a datapack or server setting changes, and a completed
set is consumed only when its matching result is successfully applied.

## Completed-set results

The active preset or resolved datapack policy decides what a completed set does:

- **Targeted Research Boost:** a complete set discounts the matching weapon's
  RP cost when that weapon appears in a purchased Tech Tree route. Route choice
  and material requirements do not change. A set is consumed only after its
  matching node is learned successfully, including when one purchase learns
  several nodes with their own completed sets.
- **Reconstruct Blueprint:** a complete set creates a protected physical
  blueprint in the Analyzer output. The reconstructed item cannot be recycled
  for RP and requires its normal Tech Tree prerequisites when used.
- **Learned target:** when the target is already learned, one completed set can
  return the configured RP amount. The Analyzer requires the full award to fit
  under the RP cap; otherwise the fragments remain untouched.

Fragment discounts are included in the server's research quote and transaction
fingerprint. A fragment deposit, threshold change, reload, or another research
purchase therefore invalidates an older preview instead of allowing it to spend
against stale progress.

## Pack-author guidance

Do not add a second fragment-only roll merely to enable the built-in system.
Configure ordinary blueprint loot pools, select a fragment preset, and set the
replacement share. Profile and rule fragment settings decide which of those
blueprint candidates can become fragments.

Test loot both with a player-caused context and in a pre-generated chest. Also
test a profile with no fragment-enabled targets: the table should continue to
produce its normal full blueprints without warnings or crashes. When changing
thresholds or completion modes on an existing world, test a player below, at,
and above the new threshold and verify the Analyzer preview before reopening
progression to all players.
