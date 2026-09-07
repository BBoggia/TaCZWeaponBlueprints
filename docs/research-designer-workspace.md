# Research Designer workspace

Open **Research Designer** from the title or pause menu, use
`/researchdesigner`, or assign **Open Research Designer** in Controls.
You can open and edit saved local projects without joining a world.

## Start with installed weapons

Join a world as an operator and choose one of these starting points:

- **Customize server tree** copies the current tree and its saved weapon catalog.
- **Empty tree with server weapons** keeps that catalog and starts an empty tree.

Both require operator permission level 2 and a server with the matching mod
build. **Blank** creates a blank local draft without a catalog; it is mainly useful
for advanced document work. **Import project** opens a shared editable bundle.
Opening or saving a project leaves the active server tree unchanged.

## Build a first branch

1. Open the **Add** tab, search for a weapon by name, and click its catalog entry.
   It becomes a starting node without prerequisites.
2. Select that node and choose **Add next**. Click another weapon to add it below
   the selected weapon and connect it in one action.
3. Keep adding weapons to make a split from that same parent. Press Escape when
   finished, or select a different parent and choose **Add next** again.
4. Drag a node to arrange it. The outlined destination shows where it will land.
   Drop into a named branch to change its membership.
5. Choose **Test** to check a simulated player, then **Project → Export** when ready to
   create a datapack.

Catalog entries already in the tree say **In tree** and select that node instead
of creating another copy. The category button switches between weapons, ammo,
and attachments. The Bench filter uses the saved catalog's research tier.

The catalog scrolls independently of the canvas. Use its scrollbar or mouse wheel;
Page Up/Down moves through results, and Up/Down moves between focused weapon rows.
Search stays visible. A checkmark means the weapon is already in the tree; hover
or keyboard-focus a row for its full name and details. Fit and zoom controls sit
inside the canvas. The top **Project** menu holds Back, Export, Advanced and help.
The project name carries an asterisk while changes are unsaved.

## Connections and groups

**Connect** makes the selected weapon a prerequisite: select the parent, choose
Connect, then click the weapon that should come after it. Escape cancels the
connection. Duplicate required links, self-links and cycles are rejected.

Select a node and open **Unlock after…** to see and edit its requirements. Each
listed group is required. A group containing alternatives means that any one
of those weapons is sufficient. **Add prerequisite** lets you choose whether
all selected weapons are required or any one will do. Adding an ordinary
connection preserves existing alternatives.

Shift-click nodes to select several, then choose **Group selection** and name the
branch. The **Groups** tab lists branches; clicking a branch header also opens
its controls. Branches have separate canvas regions. Collapse hides a branch's
own nodes in this editing view without changing the document. Nested branch
settings and broader arrangement tools remain under **Project → Advanced**.

Moving a node changes placement and branch membership. It preserves prerequisites,
costs and Research Bench requirements. Dependent nodes can move to later rows to
keep the prerequisite order valid. Routine additions, connections and moves apply
immediately and can be undone. Broad arrangement and deletion that leaves broken
references open a review first. Deleting a prerequisite retains surviving
references so research cannot silently become easier.

## Edit research rules

The selected node's inspector contains cost, materials, Research Bench tier and
prerequisites. Use a whole number for cost, then **Apply**. Escape while editing
that field restores the previous cost. Costs use the currency from the saved
server settings. **Materials** offers item search and quantity controls.

The Research Bench button cycles through tiers and the inherited default.
**More research rules** opens detailed properties, including visibility,
discovery, availability, and inherited/effective values. **Project → Advanced** retains
mapping, nested branches, templates and other document tools.

On wide windows the inspector appears beside the canvas. Clicking empty canvas
space clears the selection and returns that space to the tree. On narrower
windows the **Node** tab uses the left panel. Very small windows use the detailed
forms for costs and prerequisite editing to keep controls reachable.

## Navigate and repair drafts

Drag empty canvas space to pan, scroll to zoom, or choose **Fit**. Arrow keys pan
and `F` fits the view outside text inputs. `E` opens Advanced. Use **Project → How to build** for help. Tab and Enter navigate buttons, catalog entries and forms; advanced tools
also provide selection and arrangement forms for keyboard use.

The Build canvas keeps incomplete nodes visible while you repair them. An
unassigned node offers **Assign a weapon**. **Issues** lists validation messages
and can select the affected node. It also provides Cancel/Retry for background
checks. Missing local artwork uses a blueprint item fallback.

Build shows editable branch regions and placement order. **Test → Player view**
uses the gameplay renderer and disclosure rules, so its spacing and visible
content can differ. Draft nodes are never inserted as fabricated gameplay nodes.
Checks use saved dependency facts, not the connected player's inventory or
current server state. A failed check leaves the draft available for editing.

## Save, recover and export

**Save** or `Ctrl+S` saves the project. `Ctrl+Shift+S` saves a separate project
identity. Undo/Redo buttons and `Ctrl+Z`, `Ctrl+Y`, or `Ctrl+Shift+Z` operate on
project history outside text inputs. On macOS, use Command instead of Ctrl.
Camera changes do not add Undo entries.

Changed sessions receive recovery checkpoints at most once every five seconds.
Back checkpoints before returning to the chooser. **Open saved** opens the last
explicit save; **Recover draft** opens its recovery checkpoint, including supported
pending input. The camera, category and selection are saved separately. A
recovered draft stays marked as recovered until explicitly saved.

Projects live beneath the game directory in
`taczweaponblueprints/designer/projects/<project UUID>/`; saved captures are in
`taczweaponblueprints/designer/captures/`. Hover the project name in the top toolbar for save status, the project
location and capture date. The date describes saved context, not a live server check.

A conflicting disk edit is not overwritten. Save a separate copy or reopen the
externally changed project. Abrupt termination can lose changes newer than the
last completed checkpoint. Unsupported documents are not silently rewritten.

**Project → Export** creates an installable datapack and a separate editable project bundle
after review. Installing that datapack, choosing pack priority and activating
its profile remain explicit server steps. See [export and installation](research-designer-export.md),
[player simulation](research-designer-simulation.md), and
[troubleshooting](research-designer-troubleshooting.md).
