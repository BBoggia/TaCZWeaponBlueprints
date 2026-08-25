# Research Tree Manual QA Matrix

This matrix covers behavior that unit tests and artifact inspection cannot prove
without a real Minecraft client. Complete it on the exact release candidate and
record the game version, Forge version, TaCZ version, mod JAR hash, operating
system, and display resolution with the results.

## Display and localization

Repeat the Research and Recycle checks at GUI scales 1, 2, 3, 4, and
Auto where the display supports them. Include 320x240, 854x480, and the normal
release-test window size.

- [ ] Title, tabs, search, zoom controls, tree, contextual tooltip, and buttons do not overlap or clip.
- [ ] Long weapon names are clipped cleanly and remain readable in tooltips.
- [ ] Long translated button labels remain identifiable and clickable.
- [ ] Inventory and material slots are absent and inactive in Research.
- [ ] Only the duplicate and player-inventory slots needed for Recycle are active.
- [ ] Fullscreen is a translucent world-backed overlay at every supported size and reserves no permanent right, bottom, or drawer details panel.
- [ ] Entering, leaving, and resizing fullscreen preserve a valid focus without exposing inactive inventory slots.
- [ ] Search text and the independent compact, All Weapons, and per-branch pan and zoom positions survive repeated view/fullscreen toggles.
- [ ] Fullscreen controls remain readable over bright and dark world backgrounds.

## Tree interaction and accessibility

- [ ] Dragging empty canvas space pans smoothly and remains bounded.
- [ ] Wheel, `+`, `-`, and Show All operate at both zoom limits in compact and fullscreen views.
- [ ] Search highlights matches and centers the current match.
- [ ] Hovering a node shows contextual status, relationship, cost, and prerequisite information without reserving canvas space.
- [ ] Clicking a node pins the current selection and exposes the floating Research action when its complete inventory cost is available.
- [ ] Double-clicking a ready node performs the same action exactly once; slower clicks only change selection.
- [ ] Up/Down while search is focused cycles matches; Enter selects the current match.
- [ ] Arrow keys outside search traverse connected prerequisites/dependents and neighboring nodes.
- [ ] Tab and Shift+Tab reach every visible button and search field.
- [ ] A fresh client sees exactly three short tree-help instructions; `Got it` survives a restart, and the compact `?` button reopens help.
- [ ] The help panel blocks underlying node clicks, dragging, scrolling, hover highlighting, and tooltips only inside its own bounds.
- [ ] Compact branch selection cycles every published branch without clipping or overlapping the search field.
- [ ] The fullscreen Weapon Trees sidebar begins with All Weapons, scrolls independently, and keeps the selected branch visible.
- [ ] Selecting a sidebar branch swaps the Branches projection but only fits that branch's region in All Weapons.
- [ ] Anonymous nodes appear only in the disclosure-safe Undisclosed branch and never reveal their actual type or identity.
- [ ] Node status remains understandable without relying on color.
- [ ] Every node shows a recognizable status badge, and the same symbol appears beside the focused status text.
- [ ] Learned, available, insufficient-RP, undiscovered, prerequisite-locked, disabled, over-cap, unavailable, Preview, and anonymous nodes use visibly different glyphs.
- [ ] Local focus, hover, search highlighting, and the chosen-for-research corner marker remain distinguishable when they overlap.
- [ ] Focusing a Silhouette or Name node leaves the previous valid research-selection marker on the correct Preview/Full node.
- [ ] With Minecraft narration enabled, search and buttons announce meaningful labels; selected-node text is readable through the normal screen narration pass.
- [ ] Arrowheads make bottom-to-top dependency direction clear without hovering.
- [ ] Branches leave through visibly separate source ports and merges enter through separate target ports.
- [ ] Connectors do not pass through node cards when a category tier wraps across several rows.
- [ ] Cross-group connectors remain readable while panning and at Fit scale.
- [ ] Requirement and unlock portals are distinct, name only disclosed destination groups, and open the expected branch without changing the server research selection.
- [ ] Focusing a node distinguishes direct requirements, earlier requirements, direct unlocks, later unlocks, and unrelated branches.
- [ ] Clicking a compact requirement or unlock card focuses the expected node; fullscreen relationships remain available in the contextual tooltip and highlighted path.
- [ ] The focused card shows a useful next action for learned, ready, insufficient-RP, discovery-locked, prerequisite-locked, disabled, over-cap, unavailable, Preview, and anonymous nodes.
- [ ] Requirement cards show completion status, unlock cards are visually distinct, and their counts remain truthful when more relationships exist than compact slots.
- [ ] Anonymous mystery requirement cards show no identity and do nothing when clicked.
- [ ] Escape exits fullscreen first; a second Escape closes the Research Bench.
- [ ] Silhouette and Name nodes never gain an icon, category, real ID, cost, readiness, or server-selectable action through the redesigned UI.

## Progression and transactions

- [ ] A fresh player sees the seven default TaCZ weapon branches.
- [ ] Two simultaneous players see different learned/available states without sharing client state.
- [ ] Locked nodes explain points, discovery, or prerequisite requirements correctly.
- [ ] Research automatically consumes only the exact required amounts from the main inventory and hotbar and preserves unrelated stacks.
- [ ] The Research button and double-click shortcut produce identical server-authoritative results.
- [ ] Closing the menu returns the unused duplicate recycling input, dropping only when inventory insertion is impossible.
- [ ] Insufficient points, ingredients, or prerequisites consume nothing.
- [ ] Successful research charges the exact current RP and inventory cost and produces one blueprint; if the inventory remains full, the output is safely dropped at the player.
- [ ] Successful recycling consumes one learned duplicate and awards the exact configured RP.
- [ ] Creative bypass behaves exactly as the synchronized server setting specifies.

## Reloads, content packs, and networking

- [ ] `/reload` while the bench is open replaces the tree and preview atomically.
- [ ] A stale selected node returns safely to Browse after its rule or content is removed.
- [ ] Removing a TaCZ content pack leaves no broken connector or crash; restoring it makes persisted unlocks usable again.
- [ ] An unmatched add-on gun remains discoverable and independently researchable.
- [ ] Dedicated server rejects a client with any protocol other than `15`.
- [ ] Reconnect, dimension change, and respawn do not combine old and new tree chunks.

## Bench model and blocks

- [ ] Item, first-person, third-person, and inventory icon face forward.
- [ ] North, east, south, and west placements face the player correctly.
- [ ] Both halves open the same menu and cannot be separated by pistons.
- [ ] Breaking either half removes the complete bench and produces exactly one drop.

## Release screenshots

Capture clean, HUD-appropriate PNGs at native resolution:

1. Fullscreen All Weapons view with the Weapon Trees sidebar, grouped regions, a selected node, visible arrowheads, and several available and locked nodes.
2. Selected-node contextual tooltip showing prerequisites, unlocks, RP cost, inventory material counts, and readiness.
3. Fullscreen overlay with the world visible behind the tree and the floating Research action enabled.
4. Recycle view showing a duplicate and RP reward.
5. Research Bench model placed in a representative TaCZ workshop.

Do not use a development build watermark or a screenshot from a different JAR
than the recorded release hash.
