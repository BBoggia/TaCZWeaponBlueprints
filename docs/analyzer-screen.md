# Blueprint Analyzer screen and synchronization

The Blueprint Analyzer uses a horizontal work area: Input on the left, possible
results in the center, and the real Output slot on the right. A dark metal panel
separates this area from the cost/action strip and the player inventory. The
station tier appears once in the header. Possible results use blueprint,
fragment, and active-currency artwork; they never populate the real Output slot.

## Reading the screen

The overview shows facts needed for the current operation: rewards and odds,
weapon tier or Research Data target tiers, and fragment progress where applicable.
One notice line prioritizes processing, the latest result, a recovery confirmation,
or a blocking requirement. Generic ready messages are omitted.

Open **Details** to read required materials, archive capacity, weapon source,
other station tiers, and blueprint learning/recycling rules. Scroll within this
view or press Page Up / Page Down. Back or Escape returns to the overview;
Escape from the overview closes the workstation. Details covers the workstation
slots and blocks clicks, drops, and hotbar swaps on those hidden slots. The
inventory and action strip remain usable.

Hover a shortened line for its full text. Hover the cost strip or action button
for input consumption, currency balance, and every required material. Keyboard
narration includes the overview and action costs, plus the explanation when
Details is open. Long labels remain within their allocated area.

A probability is displayed from the server's integer weights. Exact percentages
use no approximation mark; rounded percentages use `≈`. A positive chance below
0.01% appears as `<0.01%`. A non-guaranteed chance above 99.99% appears as
`>99.99%`. Only guaranteed outcomes show 100%; impossible outcome rows are
omitted from the overview. Fragment quantity ranges include both endpoints.

Analyze Weapon consumes the configured weapon count, currency, and required materials
for every accepted attempt, including an explicitly configured no-result. Currency
recovery has its own input count, cost, material list, and currency award; its cost does
not inherit the tiered analysis cost. The action tooltip explains the applicable
consumption. When both weapon analysis and direct currency recovery are available,
the header selector changes the operation shown in the overview. The gold action
button submits only that operation. Switching the selector spends nothing and
updates the displayed reward and cost together. Unknown-weapon recovery still
requires a second confirmation click. Legacy Research Data uses the same selector
to choose one-item or stack redemption; random Data analysis processes one item.
In direct-only mode, recovery requirements remain visible even if the server
cannot yet calculate the award. The screen explains the missing requirement
instead of replacing it with a generic mode message.
Direct-only mode omits the disabled weapon-analysis odds, materials, and saved
analysis progress. Configured blueprint outputs with disabled learning explicitly
state that the blueprint cannot teach its target.
When currency recovery is the only available action in player-choice mode, its cost
and status also take precedence. The details still explain why weapon analysis
is blocked, such as a known target, missing materials, or a station requirement.

Marked analysis fragments show matching saved progress, the amount to archive,
the amount retained in Input, and saved progress after the action. A complete
saved set can reconstruct while retaining every identifying input fragment.
Other reward classes remain separate, and the shared archive total is shown in
the details. Ordinary fragments continue to show their configured reconstruction,
discount, or currency-return behavior.

Completed weapon actions report a blueprint, an exact fragment quantity, or a
paid no-result. Fragment actions report the amount archived, retained, and saved,
and whether a blueprint was created. Results remain briefly visible after Input
becomes empty and clear when another input is inserted. A pending request disables
submission. Unmatched, expired, or already-handled responses cannot install new
feedback.

## Wire and integration contract

Protocol **62** is required on both client and server. The preview appends an
`AnalyzerPresentation` to the existing bounded input decision. It contains:

- A server-resolved station tier; tier zero is reserved for source-compatible
  constructors without extended presentation.
- Optional weapon policy status, tier, five outcome-table integers, reward
  class, matching saved count, total count, reconstruction threshold, and cap.
- Optional marked-fragment class, restricted output class, archive total, and cap.
- Optional direct-recovery input count, active-currency cost, material allocations, and the
  underlying physical eligibility status.

Research Data adds an optional bounded aggregate summary of configured tiers,
three station outcome rows, and eligibility rules. It contains no target IDs.
See [Research Data presentation](research-data-policies.md#analyzer-presentation).

Record validation limits tiers, progress, quantities, weights, costs, and enum
values. Ingredient and alternative counts are checked before allocating packet
lists. Presentation kind must match the input kind. Previews contain no selected
random outcome. Changing saved progress or policy can refresh a weapon preview
without changing its physical input.

`ActionResult` carries an optional analysis outcome for successful
`REVERSE_ENGINEER` or `ANALYZE_DATA` actions, and optional fragment-transfer
feedback only for successful `ARCHIVE_FRAGMENTS`. Data uses the same outcome
shape as weapon analysis. Failed transactions contain neither result.
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

The bundled weapon-analysis profile is tiered; Research Data analysis is enabled by default. Pack authors can override the tier matrix or select legacy analysis; see [Analyzer policy authoring](analyzer-policy-authoring.md).

Loaded physical weapons show an ammunition return summary when the overview has
no higher-priority notice. Details includes the return quantity and explains that
inventory overflow drops at the player’s feet. Previewing never unloads a weapon.
