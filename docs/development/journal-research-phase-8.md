# Journal and Research Phase 8: Research Bench Integration

## Scope

Phase 8 completes the initial Journal and research feature line with one
server-authoritative Research Bench. It integrates the Phase 7 recycling
transaction, adds atomic blueprint research, exposes exact authorized costs in
an open menu, and provides explicit operator progression recovery commands.

The bench is intentionally an instant menu. It has no block entity, ticking
state, persistent queue, energy storage, chunk ticket, or automation surface.
Unused inputs exist only in the open menu and are returned through Minecraft's
safe container-return path when the menu closes.

## Registered gameplay surface

Phase 8 registers:

- the `taczweaponblueprints:research_bench` block and block item;
- a shaped, datapack-replaceable crafting recipe and self-drop loot table;
- an axe mineability tag, blockstate, block model, and item model;
- a common Research Bench menu and client-only screen registration;
- six research ingredient slots, one recycling slot, and one non-takeable
  virtual output slot.

The normal `stillValid` distance and block-presence check guards every menu
action. Breaking or moving away from the bench invalidates the authority
surface. Shift-click explicitly excludes the virtual output, routes physical
blueprints to recycling, routes other items to research inputs, and returns
bench inputs to the player inventory.

## Bounded network contract

The protocol is deliberately advanced from `5` to `6`. The client may send
only:

1. the open container ID;
2. one of `SELECT`, `RESEARCH`, or `RECYCLE`;
3. an optional resource ID bounded by the shared 256-character ceiling.

The server verifies the sender, matching open menu, container ID, bench
usability, selected ID, and physical recycling stack. Point values, ingredient
costs, eligibility, duplicate state, and output contents never come from the
client.

Exact research-cost previews are sent only for a matching open menu and a
blueprint whose current visibility is at least `PREVIEW`. Preview packets are
bounded to six ingredient types and 64 item alternatives per type. Standard
menu synchronization carries the physical slots and virtual output. A preview
is informational: every research or recycling action resolves current policy
again immediately before commit.

Datapack, configuration, and catalog synchronization refreshes an open bench.
Even if a stale preview is visible during a reload boundary, the transaction
service rejects or applies only the newly published authoritative state.

## Atomic research transaction

`BlueprintResearchService` validates, in order:

- a living server player, bounded output ID, and six-slot bench input;
- the player's progression capability and any valid legacy migration;
- current catalog, research snapshot, configuration, blacklist, and player
  policy;
- policy identity and point-balance freshness;
- availability, administrative permission, global and detailed research
  enablement, learned state, discovery requirements, and prerequisites;
- the complete point cost and a feasible complete ingredient allocation;
- a free inventory slot for the normal physical blueprint output.

Only after every check succeeds does the service spend the complete point cost,
consume the planned ingredient quantities, and insert exactly one standard
blueprint. The produced item does not directly learn the recipe; normal
blueprint use remains the explicit learning action. Entering the inventory can
record discovery under the existing discovery policy.

If both synchronized server configuration and the selected datapack policy
allow Creative cost bypass, both point and item costs are waived. This is
separate from recycling: Phase 7 still consumes exactly one physical recycling
input in Creative.

Every failed research action reports a typed, localized outcome and preserves
points, ingredients, and output state.

## Overlap-safe ingredient allocation

Research ingredients are configurable alternatives or item tags, so a greedy
slot scan is not correct. For example, one cost may accept `paper or iron` while
a second cost accepts only `paper`. Spending the paper on the flexible cost
would incorrectly reject a valid paper-plus-iron input.

`ResearchIngredientPlanner` models the maximum six ingredient types and six
input slots as a bounded flow network. A complete maximum flow is an exact
consumption plan; an incomplete flow means the cost cannot be paid. This handles
overlapping explicit alternatives and tags deterministically without assigning
one physical item to multiple costs.

## Operator recovery commands

Permission-level-two operators receive:

- `/gg progression inspect <player>` for learned, discovered, legacy, and point
  counts;
- `/gg progression reset <targets> learned`;
- `/gg progression reset <targets> discovered`;
- `/gg progression reset <targets> points`;
- `/gg progression reset <targets> all`.

Learned reset also clears rollback-compatible legacy recipes so catalog
migration cannot immediately restore the reset. Discovery reset retains learned
IDs because the invariant `learned` is a subset of `discovered` must remain
true. Every successful reset republishes recipe, progression, and Journal state.

## Verification gates

Phase 8 adds focused coverage for:

- overlap-safe alternative allocation;
- exact point and item consumption;
- creative joint-gate bypass;
- no partial mutation on point, ingredient, output, or policy failure;
- bounded action and exact-preview packet round trips;
- invalid action, ID, and ingredient-count rejection;
- operator command registration.

The release-artifact verifier requires the bench block, menu, screen, packets,
transaction services, planner, commands, recipe, and block loot table in the
packaged JAR. Full clean build, publication readiness, server startup, client
startup, and JSON/resource validation are the automated phase gates. The
hands-on Research Bench interaction matrix is recorded in the release checklist
for final pre-release gameplay QA.

Automated and runtime verification completed on August 24, 2026:

- all 121 tests passed with no failures or skips;
- the clean build, release-artifact verifier, and publication-readiness gate
  passed;
- the dedicated server applied the research snapshot, rebuilt 481 blueprints,
  loaded the bench data, and reached `Done`;
- the client registered the bench screen and completed registry freeze, model
  baking, sound initialization, and texture-atlas creation without a bench
  resource error.

The development content set still reports its pre-existing malformed `ccrp`
language JSON and invalid third-party sound filenames. These warnings do not
originate in TaCZ Weapon Blueprints and did not prevent either runtime startup.

## Deliberately deferred extensions

- ticking or queued research and a Research Bench block entity;
- automation, energy, physical research currencies, and team-shared state;
- reverse engineering guns or attachments;
- automatic recycling, personal rerolls, and blueprint fragments;
- public third-party research transaction APIs.
