# Research Designer help and troubleshooting

For a new custom Tech Tree using installed weapons, join a test world as an
operator, open Research Designer, and choose **Empty tree with server weapons**.
Choose **Customize server tree** to edit a copy of the current layout instead.
Use the **Add** catalog, **Add next**, and **Connect** to build it, then **Test** to
check a simulated player before exporting.

A blank local project has no captured weapon catalog. It can store a draft, but
capturing the server first is the easiest way to author with installed content.

| What you see | What to do |
| --- | --- |
| Capture is unavailable | Join a server with the matching mod build and operator permission level 2. Local projects remain available without that permission. |
| Capture expired or the connection closed | Reconnect if needed and capture again. Incomplete transfers are discarded. |
| Preview is checking a large draft | Continue working, or open **Issues → Cancel**. Use **Retry** to request a new preview. Replaced or cancelled previews cannot overwrite current results. |
| Preview is unavailable | Open **Issues** and resolve the reported issue. The Build canvas remains available. A valid draft can exceed the gameplay renderer's routing capacity; simplify its connections before testing Player view again. |
| A weapon or dependency is missing | Restore the required mod or gun pack, or explicitly map the node to another blueprint. Unresolved references block export. |
| The server behaves differently from the simulation | Check the active profile, settings, pack priority and dependency versions. A saved capture describes its source at capture time, not the current server. |
| Export name already exists | Choose a new export name. Previous exports are retained. |
| An imported ZIP is rejected | Use the editable project bundle, not the installable datapack. Check its version, contents and size. Do not rename a datapack to make it look like a project. |
| A save conflicts with another edit | Preserve your draft with `Ctrl+Shift+S` (Command on macOS), then reopen the current saved project. Do not replace the other edit blindly. |
| An image is missing or a title is too long | The node remains selectable by blueprint ID. Use search and the full-text tooltip. Restore the resource pack for artwork or translations. |

Use **Save** for an explicit project save. Recovery checkpoints keep supported
pending field input as well as the draft. On reopening, **Open saved** and
**Recover draft** let you choose deliberately. Unsupported future versions are
not rewritten as the current format.

Installing a different tree does not rename blueprint IDs or erase learned
knowledge and fragment archives. Omitted blueprints may stop appearing in that
tree. Rules can change whether existing progress is useful. Back up the world
and check existing-player scenarios before deploying a change.

Drafts support up to 4,096 nodes and 128 branches, with branch nesting limited
to 16 levels. Runtime prerequisite chains have a depth limit of 64. Dense edge
routing can make a preview unavailable before a document reaches its node limit.

This release exports local files. Installation, pack priority and active-profile
selection are explicit server steps; there is no Designer Apply button yet.
Simulation does not certify external progression providers, survival resource
supply, or every possible inventory/output condition. Test the exported datapack
in a disposable world before installing it in a main world.

See [workspace controls](research-designer-workspace.md),
[player simulation](research-designer-simulation.md), and
[export and installation](research-designer-export.md).
