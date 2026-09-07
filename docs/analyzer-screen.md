# Blueprint Analyzer screen and synchronization

The Blueprint Analyzer keeps its Input and Output slots. Its details show the
inserted weapon, Analyzer and weapon tiers, full-blueprint probability, fragment
quantity and probability, and any no-result probability. The cost and matching
saved progress stay beside the slots while the details scroll.

## Reading the screen

Scroll over the details panel, or press Page Up / Page Down, to read required
materials, archive capacity, weapon source, and blueprint learning/recycling
rules. Hover a shortened line or button for its full text. Keyboard narration
includes the complete details and action names. Long labels do not push slots or
buttons outside the panel.

A probability is displayed from the server's integer weights. Exact percentages
use no approximation mark; rounded percentages use `≈`. A positive chance below
0.01% appears as `<0.01%`. A non-guaranteed chance above 99.99% appears as
`>99.99%`. Only impossible outcomes show 0%, and only guaranteed outcomes show
100%. Fragment quantity ranges include both endpoints.

Analyze Weapon consumes the configured weapon count, RP, and required materials
for every accepted attempt, including an explicitly configured no-result. RP
recovery has its own input count, cost, material list, and RP award; its cost does
not inherit the tiered analysis cost. The action tooltip explains the applicable
consumption. Weapon analysis and direct RP recovery remain distinct actions.
When direct RP recovery is blocked, its requirements remain visible even if the
server cannot yet calculate the award. Direct-only mode explains the missing
requirement instead of replacing it with a generic mode message.
Direct-only mode omits the disabled weapon-analysis odds, materials, and saved
analysis progress. Configured blueprint outputs with disabled learning explicitly
state that the blueprint cannot teach its target.
When RP recovery is the only available action in player-choice mode, its cost
and status also take precedence. The details still explain why weapon analysis
is blocked, such as a known target, missing materials, or a station requirement.

Marked analysis fragments show matching saved progress, the amount to archive,
the amount retained in Input, and saved progress after the action. A complete
saved set can reconstruct while retaining every identifying input fragment.
Other reward classes remain separate, and the shared archive total is shown in
the details. Ordinary fragments continue to show their configured reconstruction,
discount, or RP behavior.

Completed weapon actions report a blueprint, an exact fragment quantity, or a
paid no-result. Fragment actions report the amount archived, retained, and saved,
and whether a blueprint was created. Results remain briefly visible after Input
becomes empty and clear when another input is inserted. A pending request disables
submission. Unmatched, expired, or already-handled responses cannot install new
feedback.

## Wire and integration contract

Protocol **59** is required on both client and server. The preview appends an
`AnalyzerPresentation` to the existing bounded input decision. It contains:

- A server-resolved station tier; tier zero is reserved for source-compatible
  constructors without extended presentation.
- Optional weapon policy status, tier, five outcome-table integers, reward
  class, matching saved count, total count, reconstruction threshold, and cap.
- Optional marked-fragment class, restricted output class, archive total, and cap.
- Optional direct-recovery input count, RP cost, material allocations, and the
  underlying physical eligibility status.

Record validation limits tiers, progress, quantities, weights, costs, and enum
values. Ingredient and alternative counts are checked before allocating packet
lists. Presentation kind must match the input kind. Previews contain no selected
random outcome. Changing saved progress or policy can refresh a weapon preview
without changing its physical input.

`ActionResult` appends optional weapon and fragment results. Weapon results are
legal only for successful reverse-engineering actions; fragment results are
legal only for successful fragment actions. Failed transactions contain neither.
The server action retains the existing request ID, input identity, menu session,
and state-token checks. Results also carry the server's post-action preview
token. The client accepts only its matching pending request and matching preview
token, so a delayed receipt cannot attach to a replacement stack with the same
target and count. A later state change clears feedback attached to a nonempty
input. Successful feedback already attached to an empty input remains visible
until expiry or insertion of another item. Source-compatible result constructors
use token zero when no authoritative state is supplied; network menu actions
always attach their current preview token.

`reverseEngineerDetailed` returns the committed outcome alongside the existing
transaction result. `reverseEngineer` remains a compatibility wrapper returning
the original result. Existing preview/result constructors remain available for
source callers, with empty presentation extensions. Player-data version 5 and
catalog export format 22 remain unchanged by the screen update.

The bundled research profile remains legacy. Pack authors explicitly opt into
tiered policies; see [Analyzer policy authoring](analyzer-policy-authoring.md).
