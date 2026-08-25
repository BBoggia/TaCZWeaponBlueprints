# Research Visibility Hardening

Date: 2026-08-25

The Journal and Research Bench now enforce the same five-tier disclosure
contract. `HIDDEN` publishes no entry or node. `SILHOUETTE` publishes an
anonymous node, `NAME` adds only the translated name, `PREVIEW` adds identity,
icon, category, and an aggregate research summary, and `FULL` adds exact policy
state and recycling detail.

The tree uses validated per-publication opaque resource keys for silhouette and
name-only nodes. Their real blueprint IDs, item types, icon sources, player
state, costs, and server action targets are not synchronized. These nodes can
be focused and navigated locally but cannot be submitted as Research Bench
selection actions.

Preview nodes disclose an identity and aggregate point, ingredient-type, and
prerequisite counts, but their learned, discovered, eligibility, affordability,
and recycling fields remain redacted until full visibility. Deliberately
selecting a preview node can still request the open Bench's authoritative
preparation view; all transactions remain server validated.

The packaged exact TaCZ tree requests `FULL` visibility. The server's
`maximumUndiscoveredVisibility` remains a ceiling, so it can now produce every
tier for the packaged tree. The fallback profile remains `SILHOUETTE` for
otherwise unmatched add-on content.

This changes the research-tree wire shape and advances the network protocol to
`12`. It does not change persisted learned blueprints, discoveries, Research
Points, or datapack visibility strings.
