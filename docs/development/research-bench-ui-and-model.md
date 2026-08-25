# Research Bench UI and Model Revision

Date: 2026-08-24

## UI revision

The Research Bench now has two player-facing interaction states: Research and
Recycle. Research is a clean, non-inventory tree view with disclosure-safe
search, contextual node tooltips, exact server-authored material counts, a
direct Research action, and an optional double-click shortcut. The transaction
pulls the exact cost from the player's main inventory and hotbar atomically, so
there is no physical Prepare or Fill step. Recycle retains its single-purpose
input and live point preview. Only the duplicate and player slots needed by
Recycle are exposed through the menu.

Fullscreen Research uses a translucent, world-backed canvas inspired by quest
overlays. Search and navigation controls float over the tree, while RP,
requirements, inventory counts, readiness, and relationship details live in
the node tooltip instead of a permanent right, bottom, or drawer panel.

The research and recycling tabs remain visually enabled. A dedicated accent
outline marks the selected tab, avoiding the misleading bright-unselected and
dark-selected state produced by disabled vanilla buttons. Player-facing copy is
short and action-oriented; server validation remains an implementation detail.

Recycling preview and commit share `BlueprintRecyclingService.evaluate`. The
preview never mutates progression or items, while commit consumes the exact
evaluation immediately before crediting points. The preview packet remains
bounded and protocol version 13 rejects clients using an older action, state,
material-summary, or research-tree wire shape.

## Phase 0 GUI verification matrix

Before the visual tree work begins, verify the interaction foundation at GUI
scales 1, 2, 3, 4, and Auto where the display supports them:

- Browse with no entries, no search results, no selection, an eligible
  selection, and a locked selection.
- Research with zero, partial, exact, and excess materials in the player
  inventory; insufficient points; a full inventory; and Creative cost bypass.
- Use both the Research button and double-click shortcut, then switch to
  Recycle and confirm unrelated inventory stacks are unchanged.
- Recycle with an empty input, invalid blueprint, unlearned blueprint, learned
  duplicate, point-cap failure, and successful award.
- Repeat both states at 320x240 and 854x480 window sizes, then with long
  translated blueprint names and long localized button labels.

## Source model assessment

The supplied `research_bench.gltf` is a valid glTF 2.0 export containing one
freeform mesh with 6,291 vertices and one embedded 4096 by 4096 texture. Its
bounds are approximately 2.01 by 1.69 by 1.13 Minecraft blocks.

TaCZ 1.1.8 does not render glTF mesh data. Its `GltfManager` reads `.gltf` only
from the animation directory into animation structures. TaCZ block geometry is
loaded as Bedrock/Blockbench cuboids into `BedrockModel`. The supplied mesh
therefore cannot use TaCZ's geometry renderer without being remodeled and
losing its freeform surfaces.

Because the bench is static, Forge's baked OBJ model loader is a better fit
than a per-frame block-entity renderer. The imported mesh is chunk-batched,
retains its topology and UVs, and adds no new runtime dependency. Four
pre-rotated block meshes keep the two-block anchor deterministic. The block
places an invisible collision extension perpendicular to its facing, opens the
same root menu from either half, removes both halves together, and blocks piston
movement. Only the root half has loot.

The texture was resized to 1024 by 1024 for a packaged size of about 1.3 MB;
the original embedded PNG was approximately 19 MB. The bench uses the cutout
render layer and disables full-cube occlusion.

## Verification

- Gradle tests, reobfuscation, packaging, and release-artifact verification
  pass.
- Every project JSON resource parses successfully.
- The development client completed resource reload, model baking, and texture
  atlas creation with no Research Bench or OBJ-loader error.
- The existing malformed third-party `ccrp` language file and Suffuse sound
  filenames remain unrelated development-content warnings.

The interactive placement, orientation, break/drop, item-transform, slot-flow,
and hover matrix remains a hands-on release-checklist requirement because the
desktop automation layer cannot attach to Minecraft's LWJGL window.
