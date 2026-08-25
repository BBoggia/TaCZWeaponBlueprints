# Research Tree Phase 5: Details and Material Preparation

Phase 5 connects the interactive tree to a clearer, safer material-preparation
workflow. Selecting a tree node still does not mutate inventory or progression.
The server-authored detail and Prepare views now provide the information and
actions needed to move from a selection to a valid research transaction.

## Selected-node details

The compact card below the tree shows:

- the selected weapon icon and name;
- its immediate, action-oriented status;
- RP cost and the player's current RP balance;
- up to two disclosed prerequisite icons.

Extra public prerequisites are summarized with a count. Hovering a disclosed
icon shows its name and current state. Hidden prerequisites do not contribute
to this client presentation.

## Material preview

Each material requirement now carries two overlap-safe counts:

- the amount already inserted into the Research Bench; and
- the additional amount available in the player's main inventory and hotbar.

These are not independent raw item totals. They come from one maximum-flow
allocation across the complete cost. If two requirements accept overlapping
items, one stack can contribute to only one requirement in the preview—the same
constraint applied when research consumes the materials.

Material tooltips use short player-facing labels: **In bench** and
**Ready to add**. The Research action remains disabled until the bench itself
contains a complete valid allocation (unless Creative bypass applies).

## Fill from Inventory

Prepare includes a **Fill** action. It is enabled only when the authoritative
preview knows at least one useful inventory item can fit into the bench.

The request carries only the open container ID, action, and currently selected
blueprint ID. The server then:

1. verifies the player still owns the open bench menu;
2. verifies the menu is still in Prepare mode;
3. resolves the current selected policy again;
4. calculates a new overlap-safe allocation from current bench and inventory
   stacks;
5. simulates merging the selected items into the six bench slots;
6. applies both sides together; and
7. publishes a fresh preview.

Fill moves the maximum useful amount available. It may partially prepare a cost
when the player does not own everything yet. It never moves armor or offhand
items, never replaces unrelated bench contents, never consumes RP, and never
performs the research transaction. If the selected item set cannot fit, the
operation makes no changes. Unexpected application failures restore both the
bench and inventory snapshots before returning.

## Canvas accessibility

Dedicated `+` and `−` buttons supplement mouse-wheel zoom. Their narration says
“Zoom in” and “Zoom out,” so zoom is no longer exclusively dependent on a
scroll wheel. Existing Center, Fit, search/Enter selection, text status, and
tooltips remain available.

## Network and validation

The preview wire format now includes the fillable state plus separate bench and
inventory allocation counts. Protocol version `11` prevents an older client
from interpreting the new shape as the previous preview format.

Validation rejects negative or oversized counts, counts larger than the
requirement, double-counted bench-plus-inventory summaries, inconsistent
completion state, and a Fill-enabled preview that has no usable inventory
materials.

Focused tests cover overlap rerouting, full and partial fills, exact inventory
decrements, already-complete benches, occupied destinations, packet round trips,
every menu action ordinal, and malformed preview summaries.
