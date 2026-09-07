# Research properties and player simulation

Choose **Test** for a simulated player, or select a node and open **More research
rules** for detailed properties. On small windows, use **Project → Advanced → Properties &
simulation**. A saved server capture supplies
catalog, item, tag and settings facts for offline evaluation.

## Research properties

The property page shows the captured source rule, the local override, the value
that would be inherited without that override, and the effective value after
server settings are applied. **Change value** opens a typed form or a choice
list. **Remove override** restores inheritance for that property. Both actions
use the same review and Undo history as structural edits.

You can edit:

- Currency cost and up to six materials. Costs use whole currency units. Each
  material has a count from 1 to 64 and either item alternatives or an item tag.
- Research Bench tier, research availability, and inclusion in the Tech Tree.
- Discovery requirements, blueprint visibility, and permission for Creative
  cost bypass.
- Tree and research availability for weapons, ammo and attachments.
- Branch label disclosure and its anchor blueprint.

Editing one property preserves the rest of the selected rule, including
crafting, Analyzer and fragment overrides. Removing a Research Bench override
preserves other progression fields. Invalid combinations remain draft errors;
for example, a zero research cost can conflict with enabled recycling.

**Resolved settings & stations** shows the currency, result mode, server
visibility limits, Creative permissions, crafting requirements and Blueprint
Analyzer outcomes. Crafting and Analyzer settings have their own rules; they
are not directly edited by these forms. A research-tier change can affect an
Analyzer policy that derives its weapon tier from research progression, so the
resolved details are recalculated.

Currency identity, global enablement, result mode and global Creative permissions
come from captured server settings. A local rule cannot grant a bypass that the
server disables. The forms show these required settings rather than changing the
connected server's configuration. Captured settings are retained with the
project's existing dependency context and settings assumptions.

## Synthetic scenarios

The Test screen offers **New player**, **Player with some research learned**, and
**Player with everything learned**. These change knowledge while retaining the
current bench and supplies. **Add test supplies** replaces the simulated inventory
with up to 256 stacks, prioritizing the captured currency and authored materials.
It also fills the simulated banked RP balance. It is a convenience sample, not a
promise that every route is affordable. The summary shows learned blueprints and
supply-stack counts. **Check selected weapon** evaluates the node selected in Build;
without a selection it checks the overall view. **Player view** opens the gameplay
presentation from the result page.

Use **Advanced test settings** for the full scenario controls described below.

**Player simulation** lets you set a target, learned and discovered blueprints,
additional progression exemptions, Research Bench tier, Creative mode, currency
and material inventory, and direct-learning or physical-blueprint results.
Knowledge presets provide new, partial, full and out-of-order starting points;
custom ID lists let you reproduce an exact case. Preset membership is based on
sorted project IDs, not a promise that half the research routes are complete.

Inventory slots use captured item IDs and stack limits. Tag membership comes
from the capture, rather than user-entered claims. Physical currency is placed
in these inventory slots. The separate banked RP field applies only when the
captured server uses banked RP.

**Evaluate scenario** reports the chosen route and aggregate payment. Direct
learning uses the runtime route planner, including shared prerequisites,
inclusive alternatives and missing ancestry behind learned support. Currency
and materials use one shared allocation, so the same items cannot pay twice.
Research Bench and Creative checks use the captured feature settings.

Physical-blueprint mode checks the target's prerequisites and individual cost;
it does not purchase its ancestors. A successful simulation records a synthetic
blueprint output while leaving learned knowledge unchanged. Direct learning
updates synthetic knowledge and deducts the complete route payment.

**Simulate research** operates only on that synthetic state. **Reset scenario**
clears it. Scenario state lasts for the open workspace and is separate from
project save/recovery and Undo. Closing the project discards the scenario. The
synthetic output list has a 256-entry limit; world inventory delivery, item
stacking and ground drops are not assessed.

## Player view and validation boundaries

**Player view** renders the actual runtime disclosure projection through the
shared Tech Tree canvas. The authoring library, property summaries and route
report are absent from this view. Hidden identities, artwork, branch labels,
edges and counts follow the same publication rules as the player's Tech Tree.
Return to the results page to inspect author-only diagnostics.

These results have distinct meanings:

- **Valid graph**: the current draft compiled and passed structural validation.
- **Runtime preview passed**: the assessed research checks passed for this
  particular synthetic scenario and saved dependency context.
- **Survival supply unassessed**: the simulator has not established whether
  players can obtain the required items or workstations in the world.

External progression gates are reported as unassessed and block a successful
simulation unless the captured Creative bypass applies. The simulator never
calls world gate providers, purchase packets, award dispatchers or player-data
storage. Capture age remains relevant: offline checks do not establish that a
server still has the same settings, mods, tags or content. Export and activation
remain separate operations.
