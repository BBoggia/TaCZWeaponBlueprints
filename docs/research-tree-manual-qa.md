# Research Tree Manual QA Matrix

This matrix covers behavior that unit tests and artifact inspection cannot prove
without a real Minecraft client. Complete it on the exact release candidate and
record the game version, Forge version, TaCZ version, mod JAR hash, operating
system, and display resolution with the results.

Run general research checks in the intended active currency. RP-cap and legacy
Data-redemption checks explicitly require `BANKED_RP` plus `LEGACY_CURRENCY`
in a disposable configuration. The [economy handoff](research-economy-release-handoff.md)
adds the default Scrap/Data-analysis and full mode matrix.

## Candidate record

Fill this in before checking any runtime box. Do not combine results from
different artifacts or environments.

| Evidence | Value |
| --- | --- |
| Mod version | |
| JAR SHA-256 | |
| Release-candidate report | |
| Minecraft / Forge | |
| TaCZ / Fzzy Config | |
| Client OS / Java | |
| Dedicated-server OS / Java | |
| Window sizes / GUI scales | |
| Currency / Data mode / datapacks | |

Automated research-guidance Phase 10 preflight is a JDK 17
`./gradlew verifyResearchGuidanceCandidateHandoff`; the final versioned
candidate additionally runs `./gradlew certifyReleaseCandidate` after the
changelog release heading is finalized. Success certifies packaged structure,
data, protocol, report metadata, and their binding to the exact JAR. Success
does not certify any unchecked hands-on behavior below.

## Display and localization

Repeat the Research Bench and Blueprint Analyzer checks separately at GUI
scales 1, 2, 3, 4, and Auto where the display supports them. Include 320x240,
854x480, and the normal release-test window size.

- [ ] The Research Bench opens directly into the edge-to-edge Research Tree; it exposes no compact intermediary, Research/Recycle tabs, fullscreen toggle, inventory, or recycling slot.
- [ ] On a fresh client preference file, the first synchronized publication
  shows one Journal-key hint and the Journal opens to Getting Started. Relogging
  does not repeat the hint.
- [ ] Escape closes Getting Started without dismissing it, **Got it** persists
  dismissal across restart, and the Journal's `?` button always reopens it.
- [ ] Getting Started remains readable and navigable at every required GUI
  scale, narration identifies the page, and hidden blueprints never gain a
  name, icon, ID, cost, prerequisite, or action through the guide.
- [ ] With JEI only, EMI only, and both installed, the Research Bench, crafting
  Workbench, Blueprint Analyzer, blueprint, and Research Data information pages
  are present and localized. They expose no hidden blueprint list and provide
  no research or crafting recipe transfer. The crafting page directs players
  to an active Workbench recipe list for current server-verified access.
- [ ] With neither viewer installed, a client and dedicated server reach their
  normal startup markers without missing-class or optional-dependency errors.
- [ ] Search, zoom controls, tree, contextual tooltip, overlaid rail, and buttons do not overlap or clip.
- [ ] Long weapon names are clipped cleanly and remain readable in tooltips.
- [ ] Long translated button labels remain identifiable and clickable.
- [ ] Inventory and material slots are absent and inactive in Research.
- [ ] Only the dedicated Blueprint Analyzer exposes the contextual input,
  extract-only output, and player inventory.
- [ ] The Tech Tree has a quiet opaque surface, a persistent header, and a compact inspector below the canvas. Details expands beside the canvas when space permits and overlays it on small screens.
- [ ] Opening, resizing, and closing the permanent Research Tree preserve a valid focus without exposing inactive inventory slots.
- [ ] Search text and independent per-domain Tech Tree pan and zoom positions survive repeated domain changes.
- [ ] Fullscreen controls remain readable over bright and dark world backgrounds.
- [ ] The pinned context card fully covers every graph edge, label, badge, and item model behind it; only the card's own icons and widgets render above its background.
- [ ] Selected, focused, and hovered rail labels avoid the pinned context card; if space is exhausted, the conflicting label stays hidden and never claims clicks through the card.
- [ ] Legacy spacing/preset keys remain compatible stored data and do not alter scene geometry. Reduce Motion, grid, minimap, and hold settings still apply.
- [ ] A large content-pack catalog with many entries at the same progression
  level fits into separate scene branches without node overlap or an empty first publication.
- [ ] If Tech Tree publication is deliberately fault-injected, the Bench stays
  in Tech Tree and reports that it is unavailable; it never opens Branches or
  All Weapons and the server log records the generation failure.
- [ ] Reduce Motion applies immediately in an open bench: any active transition
  completes once, and focus, Fit, wheel, search, and sidebar camera changes are
  immediate afterward without changing saved camera targets.
- [ ] Re-enabling camera motion restores short bounded easing without momentum,
  and both settings survive a client restart.
- [ ] The background grid is absent by default, toggles immediately in the
  permanent Research Tree, and never removes connectors, status glyphs, or hit targets.
- [ ] AUTO hides at Overview, appears when less than 80% of the layout area is
  visible, and remains visible until coverage reaches 92%. Small zoom changes
  between those thresholds do not flicker. Always and Hidden respond immediately.
- [ ] Wide and tall layouts fit the outer minimap panel to their proportions,
  retaining a usable hit area and avoiding persistent overlays.
- [ ] The minimap contains only compact status, tracked-route, target, and
  viewport marks. It never renders item icons, weapon names, or hidden IDs.

## Tree interaction and accessibility

- [ ] Dragging empty canvas space pans smoothly and remains bounded.
- [ ] Clicking and dragging the minimap moves the same bounded tree camera at
  every supported zoom level without selecting, researching, hovering, or
  showing a tooltip for a graph node underneath it.
- [ ] During a minimap drag, changing camera coverage does not hide or move the
  panel. Dragging beyond its edge stays bounded; release reevaluates AUTO.
  Focus loss, resize, and publication replacement cancel the gesture safely.
- [ ] Search results, Getting Started, the sidebar, and the selected-node card
  remain visually and interactively above the minimap when their bounds meet.
- [ ] Empty, one-node, ordinary, and maximum-size published trees keep the
  minimap inside the screen at GUI scales 1 through 4.
- [ ] Wheel, `+`, `-`, and Fit operate at both zoom limits in the permanent Research Tree.
- [ ] Search highlights matches, and committing the active result centers it.
- [ ] Typing a search query updates highlights and result count without panning, changing the focused node, or changing domains; Up/Down changes the active result and Enter or a result click navigates exactly once.
- [ ] Closing and reopening search preserves a valid query and active result without changing the authoritative research selection.
- [ ] Hovering a disclosed node shows its published name and status; at low zoom it also shows enlarged artwork and disclosed progression details. It never borrows a different selection’s inventory or readiness.
- [ ] Clicking a node shows one clear next step plus direct requirement and immediate unlock counts; the pinned card exposes exact currency and inventory materials.
- [ ] Clicking a node pins the current selection and exposes the floating Research action only after its matching authoritative preview arrives.
- [ ] Single and double clicks only select; the Research button performs the action exactly once and remains disabled while its server result is pending.
- [ ] With Hold to Research enabled, holding an already-selected Ready node shows a smooth progress bar and submits exactly once at the configured duration.
- [ ] Releasing a hold early, dragging far enough to pan, selecting another node, closing the Bench, or disabling the shortcut cancels it without spending currency or materials.
- [ ] A first click on an unselected Ready node only selects it; it never inherits enough held time to research it.
- [ ] With a full inventory, successful research creates exactly one world-drop blueprint; if another mod cancels that drop, the transaction reports failure and restores every currency and material input.
- [ ] Up/Down while search is focused cycles matches; Enter selects the current match.
- [ ] Arrow keys outside search traverse connected prerequisites/dependents and
  neighboring nodes without changing the authoritative selection; Enter
  explicitly selects the focused node and sends no duplicate selection.
- [ ] Keyboard traversal keeps the focused node inside the unobscured canvas
  with a small margin, does not recenter nodes that are already visible, and
  remains smooth under rapid repeated arrows while the rail or card changes.
- [ ] Left/Right prefers a neighbor on the same graph tier and still reaches a
  sensible directional node when an irregular or disconnected layout has no
  same-tier neighbor.
- [ ] Tab and Shift+Tab reach every visible button and search field.
- [ ] `Next` focuses one published Available blueprint in the permanent tree,
  explains the recommendation on hover/narration, and never selects it
  for research, spends currency or materials, or sends a transaction packet.
- [ ] `Next` prefers an affordable choice, then the current tree, then a choice
  that opens more direct paths; repeating it without a state change focuses the
  same node, while learning/reloading content recomputes a valid recommendation.
- [ ] When no published Available blueprint is navigable, `Next` is disabled and
  exposes a concise empty-state tooltip without changing focus or selection.
- [ ] `Track` in the selected-node card marks one revealed blueprint as the
  current session goal.
- [ ] A tracked goal survives closing and reopening the Research Bench during the
  same connection, clears on logout, and clears safely if a reload removes or
  redacts the target.
- [ ] The tracked prerequisite route remains highlighted when switching Tech
  Tree domains; only route nodes present in the active domain are drawn.
- [ ] For an any-of requirement whose server-selected route differs from the
  client's initial public estimate, the tracked highlight settles on exactly
  the server-selected alternative and never highlights every alternative.
- [ ] Reloading research data or changing the tracked target while route guidance
  is in flight ignores the stale response; it must not replace the route for the
  newer publication or target.
- [ ] A route containing redacted topology or exceeding the bounded guidance
  payload leaves authoritative guidance unavailable without revealing hidden
  identifiers or displaying a partial route as authoritative.
- [ ] While a goal is active, `Next` only recommends an available step on that
  goal's selected AND-of-OR route. An unsatisfied any-of group contributes one
  alternative rather than every member. Untracking restores the global recommendation.
- [ ] Plan tooltips show the exact total of published currency and material requirement
  entries, label totals as partial when Preview or anonymous steps withhold exact
  policy, and never reveal hidden item IDs, quantities, names, or availability.
- [ ] A fresh client sees exactly three short tree-help instructions; `Got it` survives a restart, and the rail `?` button reopens help.
- [ ] The help panel blocks underlying node clicks, dragging, scrolling, hover highlighting, and tooltips only inside its own bounds.
- [ ] The compact browse-view selector is hidden and no keyboard, search,
  recommendation, portal, or restored-state path can enter Branches or All Weapons.
- [ ] The fullscreen Research Trees sidebar begins with the first Tech Tree
  domain; it contains no Branches/All Weapons view action or dead leading slot.
- [ ] After the sidebar has been used, it collapses to a narrow edge affordance; moving onto that edge reliably reopens it without stealing clicks elsewhere on the graph.
- [ ] Pinning the fullscreen sidebar prevents auto-hide, survives closing and reopening Minecraft, and unpinning restores normal auto-hide behavior.
- [ ] Fullscreen Help reopens after dismissal. Tab and Shift+Tab stay on its dismiss control; tree shortcuts and research actions are blocked until Help closes. Enter, Space, and Escape dismiss Help without purchasing research.
- [ ] Selecting an available domain opens its Tech Tree projection and disabled Ammo or Attachment domains cannot be selected.
- [ ] Fit keeps the complete visible Tech Tree domain at a readable scale and remains pannable when the whole graph cannot fit readably.
- [ ] An empty or failed Tech Tree publication shows a stable, explicit unavailable state without crashing or revealing a legacy projection.
- [ ] Forks and merges are centered around their connected nodes, avoid avoidable crossings, and preserve bottom-to-top direction at every supported GUI scale.
- [ ] Anonymous nodes appear only in the disclosure-safe Undisclosed branch and never reveal their actual type or identity.
- [ ] Node status remains understandable without relying on color.
- [ ] The untouched graph uses only the four major Learned, Available, Locked, and Hidden/unavailable families; exact lock reasons appear on hover or selection instead of adding another graph-level state.
- [ ] Help shows recognizable Learned, Available, Locked, and Hidden badges with plain-text labels, and its narration names the same four families.
- [ ] A node is never labeled Ready to research until its focused/pinned ID, authoritative selected ID, and exact server preview ID match and that preview is researchable; before the preview arrives it says Checking your inventory.
- [ ] Every graph node shows a recognizable family badge, while focused and relationship context may replace it with the more specific status symbol and explanation.
- [ ] Selected and relationship context still distinguishes insufficient-currency, undiscovered, prerequisite-locked, disabled, over-cap, unavailable, Preview, and anonymous states after the graph collapses them into four families.
- [ ] Local focus, hover, search highlighting, and the chosen-for-research corner marker remain distinguishable when they overlap.
- [ ] Focusing a Silhouette or Name node leaves the previous valid research-selection marker on the correct Preview/Full node.
- [ ] With Minecraft narration enabled, search and buttons announce meaningful labels; selected-node text is readable through the normal screen narration pass.
- [ ] Narration announces active search-result counts, selected sidebar entries, the current projection, and the same authoritative selected-card next step, cost, material count, and relationship counts shown visually without exposing redacted details.
- [ ] At minimum zoom, the visually reduced nodes and cross-group portals remain practical to click and overlapping expanded targets choose the nearest visible item.
- [ ] Arrowheads make bottom-to-top dependency direction clear without hovering.
- [ ] Unified forks share a clear centered source trunk and unified merges share a centered target approach without obscuring which nodes are connected.
- [ ] Isolated Branch projections retain visibly separate source and target ports.
- [ ] Connectors do not pass through node cards when a category tier wraps across several rows.
- [ ] Cross-group connectors remain readable while panning and at Fit scale.
- [ ] A maximum practical third-party publication remains responsive while
  panning, zooming, searching, changing branches, and opening the selected card;
  offscreen nodes and connectors do not cause visible frame stalls.
- [ ] Requirement and unlock portals are distinct, name only disclosed destination groups, and open the expected branch without changing the server research selection.
- [ ] Focusing a node distinguishes direct requirements, earlier requirements, direct unlocks, later unlocks, and unrelated branches.
- [ ] Following a disclosed requirement inspects it and adds a Back entry; Back restores the prior target, domain, rendered camera, details state, and scroll. Inspection never replaces the tracked goal.
- [ ] With a delayed selection preview, press Back and then pan, zoom, or scroll Details. The later preview must preserve the newer input. Repeat without intervening input and verify normal Back restoration.
- [ ] The focused card shows a useful next action for learned, ready, insufficient-currency, discovery-locked, prerequisite-locked, disabled, over-cap, unavailable, Preview, and anonymous nodes.
- [ ] Requirement cards show completion status, unlock cards are visually distinct, and their counts remain truthful when more relationships exist than compact slots.
- [ ] Anonymous mystery requirement cards show no identity and do nothing when clicked.
- [ ] Escape closes search or help first, collapses expanded details when they overlay a small canvas, then clears inspection and closes the Research Bench.
- [ ] Silhouette and Name nodes never gain an icon, category, real ID, cost, readiness, or server-selectable action through the redesigned UI.

## Progression and transactions

### Progression exemptions and starting knowledge

- [ ] An exact progression exemption makes its canonical gun-smith recipe
  immediately available while leaving Learned and Discovered counts unchanged;
  removing it restores the lock unless that player independently learned it.
- [ ] Exempting `gun`, `ammo`, or `attachment` affects every current catalog
  member of that kind, while an item-subgroup exemption affects only matching
  content and updates correctly after a content-pack reload.
- [ ] Exempt content is absent from Research Trees, the Journal, blueprint loot,
  and physical reverse engineering, and an exempt prerequisite no longer
  blocks its dependent research.
- [ ] A fresh and an existing player each receive every valid configured
  starting blueprint exactly once on login/config update/reload, with no item,
  currency/material cost, or Research Point award.
- [ ] Repeated relog and `/reload` do not duplicate starter knowledge or awards;
  removing a starter from configuration does not revoke knowledge already
  granted.
- [ ] Missing/blocked starter IDs and unmatched subgroup selectors do not crash,
  expose phantom recipes, or partially corrupt progression, and `/gg research
  status` reports actionable diagnostics.

### Blueprint Analyzer lifecycle

- [ ] All three Blueprint Analyzers use their manual, microscope, and electronic
  station models in-world, in inventory, as drops, and in JEI/EMI when installed.
- [ ] North/east/south/west placement preserves the front-facing controls and
  complete two-block outline/collision shape. Both halves open the same menu,
  and breaking either removes the station with at most one normal item drop.
- [ ] The shaped model does not hide adjacent block faces, produce opaque-cube
  lighting seams, or let the player target empty corner space as solid machine.
- [ ] Shift-clicking the Analyzer input or output returns its complete stack to the player inventory; shift-clicking any player slot targets only the Analyzer input and never its output or another player slot.
- [ ] The output rejects manual insertion but permits extraction and shift-click extraction.
- [ ] Closing the Analyzer with unconsumed input or unclaimed output returns both to inventory, or drops each exactly once when the inventory is full.
- [ ] Walking out of range, breaking the Analyzer, disconnecting, and a server-forced menu replacement each return or drop both server-owned slots exactly once.
- [ ] Replacing the input immediately after a result never leaves the old success or failure text attached to the new item.
- [ ] In banked/legacy mode, a configured but capped Research Data item remains identified and untouched; an ordinary unsupported item remains Invalid even while player award data is unavailable.
- [ ] In legacy Data mode, Redeem Stack reports the next award without promising every remaining item will be accepted, stops at policy/cap boundaries, and leaves every rejected item intact.
- [ ] With narration enabled, empty, invalid, ready, blocked, processing, success, and failure states announce the item type, status, reward or balance, and available action.
- [ ] At every supported GUI scale and with a deliberately long translation, hovering the detail area exposes the full text hidden by wrapping or visual clipping.
- [ ] An eligible unloaded and attachment-free gun resolves its logical TaCZ ID,
  shows the exact server currency/material cost, and creates one protected blueprint
  only after Analyze is pressed.
- [ ] Loaded weapon analysis and direct recovery return exactly the magazine and
  valid chambered rounds, including a chamber-only weapon. Matching ammo stacks
  fill first; any remainder drops when the main inventory is full. Repeat with
  Scrap and banked RP, and confirm a repeated request cannot return ammo twice.
- [ ] A blocked loaded-weapon action preserves weapon NBT, payment, and inventory.
  Open-bolt chamber flags and virtual/fuel ammunition produce no extra rounds.
- [ ] An attachment consumes exactly one physical item; ammunition consumes the
  canonical output batch or its explicit rule override and leaves any remainder
  in the input.
- [ ] Guns with invalid ammunition metadata, guns with removable attachments, insufficient physical count,
  missing currency/materials, occupied output, known/blocked/exempt content, and stale
  state-token requests consume nothing.
- [ ] A permitted customized item warns that customization will be lost; a rule
  that rejects modified items leaves it intact.
- [ ] A completed analysis marks discovery only, does not learn the recipe, and
  cannot recycle the protected output for currency under the packaged policy. Using
  that physical blueprint later learns the recipe according to its provenance.

- [ ] A fresh player sees all 53 recipe-backed default TaCZ weapons in one connected weakest-to-strongest Tech Tree without a legacy view selector.
- [ ] Glock 17 is the normal shared entry; in a test datapack that removes or blacklists it, M9A4 becomes the selectable root and every class starter points to it instead of remaining locked behind Glock.
- [ ] Every ordinary overview connector corresponds to a prerequisite actually enforced by the server.
- [ ] A weapon learned before the connected default-tree update remains learned even when its newly authored prerequisite is not present in that save.
- [ ] Two simultaneous players see different learned/available states without sharing client state.
- [ ] Locked nodes explain points, discovery, or prerequisite requirements correctly.
- [ ] Research automatically consumes only the exact required amounts from the main inventory and hotbar and preserves unrelated stacks.
- [ ] For an any-of prerequisite with a cheaper Tier 3 or gated alternative and
  a more expensive accessible alternative, a lower-tier Bench selects and
  prices the accessible route. It reports the blocker only when every complete
  route is inaccessible.
- [ ] Revoking an advancement criterion used by a Progression Gate refreshes an
  already-open Research Bench and disables the affected route without requiring
  the player to close the screen. Regranting it refreshes the route once.
- [ ] The Research button and configured hold shortcut produce identical server-authoritative results.
- [ ] While a request is pending, repeated mouse or keyboard activation cannot submit a duplicate transaction.
- [ ] A successful whole-path Tech Tree purchase adds exactly one Recent batch
  to the Blueprint Journal. Its target is the selected weapon, its members are
  only the newly learned path nodes, and its displayed total remains exact if
  a very large path stores only a bounded representative member list.
- [ ] Learning from a physical blueprint and an administrator grant each add a
  Recent entry, while starting grants, migrations, discovery-only changes,
  RP-only conversions, failed research, and rolled-back research add none.
- [ ] Recent and Unavailable remain separate Journal views. Recent is newest
  first and searchable by disclosed name, ID, source, and retained path member;
  a progression `all` reset clears it without relabeling removed content as
  recent activity.
- [ ] Successful research provides visible card feedback and an audible cue; a rejected request leaves its reason visible near the selected weapon instead of relying only on the action bar.
- [ ] The Research Bench contains no duplicate input; closing the dedicated Analyzer returns unused input and unclaimed output, dropping them only when inventory insertion is impossible.
- [ ] Insufficient currency, ingredients, or prerequisites consume nothing.
- [ ] In packaged `DIRECT_LEARN` mode, successful research charges the exact
  current currency and inventory cost, learns immediately, and produces no item even
  when inventory is full. In temporary `CREATE_BLUEPRINT` compatibility mode,
  it produces one physical blueprint without learning until that item is used.
- [ ] Successful recycling consumes one learned duplicate and delivers the exact configured active currency.
- [ ] Creative bypass behaves exactly as the synchronized server setting specifies.
- [ ] `/gg progression points give <targets> <amount>` updates every successful target immediately, skips over-cap targets without changing them, and cannot exceed the configured RP cap.
- [ ] In banked mode, a fresh server shows a 10,000 RP cap, the settings control cannot exceed 100,000, and lowering the cap below a player's stored balance leaves that balance intact while blocking further gains.
- [ ] `/gg progression reset <targets> awards` clears only RP award history; `learned`, `discovered`, and `points` preserve it, while `all` clears it with the rest of progression.
- [ ] On a fresh player, the first valid blueprint discovery grants exactly 1 active-currency unit; repeated inventory ticks, dropping and repicking the same blueprint, relogging, and restarting grant nothing more for that blueprint.
- [ ] An existing player receives each eligible retroactive advancement and 10/25-discovery or 5/15/30-learned milestone once, in bounded batches, without duplicate notifications after relog or `/reload`.
- [ ] Acquire Hardware, Diamonds!, A Terrible Fortress, Into Fire, The End?, and Free the End grant 2/4/4/4/6/8 active-currency units respectively. No built-in combat award definition pays, while player-kill Scrap loot remains independently enabled.
- [ ] In banked mode near the RP cap, a finite discovery, milestone, or advancement reward remains unclaimed instead of partially paying; after enough RP is spent, reconciliation grants its complete value exactly once.
- [ ] The eight bundled exploration-chest modifiers supply Notes, Reports, and Dossiers at their documented rates. In legacy Data mode, they redeem for exactly 1/3/6 active-currency units at the Analyzer; default analysis instead creates fragments or blueprints.
- [ ] In banked/legacy mode near the RP cap, each Research Data item is rejected intact when its full 1/3/6-point value will not fit; bulk redemption stops without consuming the rejected stack.
- [ ] Replacing or removing one packaged award or Research Data loot modifier with a higher-priority datapack changes only that definition after `/reload`; malformed award data preserves the previous award revision.
- [ ] Disabling `enableResearchPointAwards` stops gameplay datapack awards without deleting balances or claims. Data loot and Scrap loot follow their own switches. Reenabling awards does not replay already claimed finite rewards.

## Tech Tree follow-up

These checks cover the optional third view from its original acceptance
contract through the current unified-domain implementation. Leave a box
unchecked until that behavior has been confirmed in a real client/server run.

- [ ] Tech Tree is the sole player-facing choice; the dormant Branches and All Weapons projections never appear in compact or fullscreen navigation.
- [ ] With the packaged profile, Tech Tree opens Weapons and shows Attachment and Ammo domain slots as disabled/empty without publishing their nodes.
- [ ] Compact Tech Tree mode shows three direct domain icons in stable Weapons, Attachments, Ammo order; hover/narration identifies the domain and visible blueprint count, and a domain with no visible publication is disabled rather than opening a stale tree.
- [ ] Compact and fullscreen show the same three stable domain slots after removing one domain during `/reload`; the missing slot is disabled, Page Up/Page Down skips it in both directions, and restoring the domain makes the same slot usable again.
- [ ] Switching among Compact, Balanced, and Spacious changes both horizontal and ordinary row spacing without hiding the Tech Tree; Custom maximum spacing values remain bounded and a rejected visual layout falls back to Balanced.
- [ ] With narration enabled and no widget focused, Tech Tree announces the current domain, its stable position, visible blueprint count, and the Page Up/Page Down shortcut without naming hidden or unpublished nodes.
- [ ] Only the selected domain is expanded, and search switches to and centers a result in another domain only after the player commits that result.
- [ ] The Weapons domain is one connected bottom-to-top progression with Starter at the bottom and Apex at the top rather than separate weapon-class columns.
- [ ] Tier shading, semantic zoom, and connector routing remain readable at every supported GUI scale without requiring Fit to make nodes unusably small; authored lanes never appear as columns, boxes, or headers.
- [ ] A same-domain prerequisite is an ordinary edge; a cross-domain prerequisite is a truthful boundary portal that opens the correct domain.
- [ ] Switching among domains preserves an independent camera, focus, search, and pinned-card state when the referenced node remains public.
- [ ] Removing the selected domain during `/reload` retains it when available, otherwise falls back to Weapons, then the first non-empty domain, and finally a stable empty state.
- [ ] Redacted nodes reveal no actual kind, domain, lane, score, or authored placement through layout, labels, search, narration, or portals.
- [ ] Every 53-gun, 24-ammo, and 95-attachment TaCZ 1.1.8 recipe-backed blueprint resolves to exactly one curated Tech Tree placement.
- [ ] With a disposable format-2 profile that enables every domain, Weapons, Attachments, and Ammo each show one cohesive tree with Glock 17, RK-6, and 9mm as their respective bottom entry nodes.
- [ ] Changing Research Tree spacing or crossing/compaction passes reflows every enabled Tech domain atomically, while a state-only research update preserves their cameras and geometry.
- [ ] Following connectors from each entry can reach every node in that domain, never moves down an authored tier, and does not require switching domains for default content.
- [ ] Under the packaged profile, removing or blacklisting RK-6 or 9mm does not activate a fallback or publish either disabled domain; after enabling the domain in a disposable profile, the next configured Starter is promoted normally.
- [ ] Under the packaged profile, all 95 default attachments and 24 ammunition types are absent from Tech Tree research, while representative physical blueprints still learn successfully.
- [ ] After explicitly enabling both domains in a disposable format-2 profile, all 95 attachments and 24 ammunition types can be selected and their Starter, Basic, and Apex entries retain the authored 2, 4, and 12 RP costs.
- [ ] With `review_handling` omitted or set to `exclude`, warning-bearing or unscoreable add-on content remains in its safe fallback area, remains governed by its existing research rule, and creates no automatic prerequisite.
- [ ] With bundled format-2 `connected` + `place_connected`, warning-bearing add-on guns occupy append-stable deterministic ranks no wider than the population-resolved 9–20-node capacity and connect through the authored foundation instead of collecting in one narrow trunk or a separate component.
- [ ] With an opt-in 9–28 dynamic layout and more than 300 tree-visible weapons, status resolves a capacity above 20, Fit still frames the full canvas, and normal zoom/pan/hit testing remain responsive. Restoring the bundled 9–20 policy restores its prior geometry.
- [ ] Test catalogs immediately below and above dynamic-width thresholds (60/61, 75/76, 108/109, and 270/271 topology weapons). `/gg research status` and export must report widths 9/10, 10/11, 12/13, and 19/20 respectively; restarting or changing catalog discovery order must not change a result.
- [ ] In a mixed 53-authored-plus-add-on catalog, the topology audit's `widest rank` never exceeds `resolved layer width`; authored occupancy must reduce the automatic slots available in that same rank rather than allowing the two populations to exceed the cap when combined.
- [ ] Mouse wheel and zoom-out controls reach 15% while preserving cursor anchoring and usable hit targets; Fit may still frame exceptionally large canvases below 15%, and zooming in returns cleanly to the interactive range.
- [ ] An add-on gun with no usable TaCZ runtime evidence receives a deterministic conservative, review-marked placement; restart and catalog discovery order do not move it, and export identifies `unscored_fallback` plus the evidence failure.
- [ ] `place_independent` publishes the same reviewed placement without a generated prerequisite; returning to `exclude` restores its legacy fallback position without changing learned or discovered state.
- [ ] With the automatic-placement profile set to `distributed`, eligible add-on guns move to stable stat-sorted ranks after `/reload`, no generated rank exceeds the tree's `layout.max_nodes_per_layer`, and currency costs, materials, availability, and prerequisite connectors remain unchanged.
- [ ] The packaged format-4 automatic profile reports `scoring model=capability_v3`; inspecting the M320 reports formula `tacz-gun-capability-v3`, reference `tacz-1.1.8-capability-v3`, and an Advanced suggestion matching its authored rank-5 placement behind the AUG and M870.
- [ ] Override the same profile with `scoring_model: mechanical_v2`, reload, and confirm generated positions revert without changing learned blueprints, RP, authored placements, costs, or prerequisite authority. Restore `capability_v3` and confirm the prior v3 topology returns deterministically.
- [ ] Returning the profile to `independent` restores the legacy fallback position after `/reload`; learned/discovered state and the current research graph remain unchanged.
- [ ] If one automatic proposal would appear before an authored prerequisite, the publication uses the complete authored/legacy layout rather than hiding the Tech Tree or applying only some proposals.
- [ ] With the profile set to `connected`, the lower automatic ranks form a dense shared box, the transition retains a deterministic taper of cross-family and then same-family simultaneous parents, and the upper paths become family-local without forcing every apex to a single node; no edge remains within a rank, and the tree, Journal, preview, and Research action agree.
- [ ] In a deep connected tree, family separation begins around the lower third while merge density tapers through roughly the lower three quarters; periodic merges do not reconnect mature specialization ranks across families.
- [ ] At full-tree Fit on a large add-on catalog, sparse outer branches remain within two ordinary node gaps of the dense tree envelope, lower shared ranks retain normal compact spacing, and small branch-family gutters appear at the first family rank and widen toward the top. High-fan-out or crossing-heavy first splits should receive visibly more initial clearance without reaching a huge empty corridor.
- [ ] With `prerequisite_strategy: grouped_routes_v1`, a connected add-on gun with two generated alternatives is blocked while neither route is learned, becomes researchable after either route is learned, and still shows both conservative layout connectors. A one-parent selection remains a singleton requirement and a generated root remains directly available.
- [ ] With a temporary format-3 `prerequisite_strategy: hybrid_routes_v1` profile using `max_prerequisites: 3`, confirm the lower body remains densely connected, branch separation is gradual, and occasional mixed `[[A,B],[C]]` nodes require one of A/B plus C. No mandatory gateway should first appear in specialization or a terminal cohort, and no eligible rank should contain more than one scheduled gateway.
- [ ] Inspect representative pure-OR, mandatory, and mixed hybrid targets. `/gg research inspect` must report the exact relationship shape, learning either OR alternative must satisfy only that group, and the mandatory gateway must remain required. Restore the packaged grouped profile afterward and confirm learned knowledge is unchanged.
- [ ] Learning a connected add-on gun's only generated anchor immediately makes the gun researchable, while blocking or removing that anchor suppresses the generated lock instead of stranding the gun.
- [ ] A datapack-authored prerequisite remains the complete effective prerequisite list for that gun and is never replaced or extended by the connected-mode heuristic.
- [ ] A legacy rule with `prerequisites: [A, B]` still requires both A and B. A format-2 rule with one `prerequisite_groups` entry containing `any_of: [A, B]` becomes researchable after learning either A or B, while a second singleton group remains independently mandatory.
- [ ] A partially disclosed any-of group shows its visible alternatives and a bounded hidden-choice count without exposing hidden IDs. Preview/name/silhouette visibility must not reveal whether the group is satisfied; full visibility may show the current satisfaction count.
- [ ] A client or server still using an older protocol is rejected cleanly. Matching protocol-66 peers reconstruct identical group ordinals, alternatives, hidden counts, disclosure state, aggregate path-purchase previews, typed route-authority failures, route fingerprints, correlated authoritative guidance, bounded Affordable Now batches, Recent unlock history, disclosure-filtered Blueprint Fragment, crafting-access, and Progression Gate state, and the exact native crafting allow-list with its policy identity even when updates arrive out of order.
- [ ] With a crafting Workbench open, simulate or pause an incomplete crafting-access response for at least five seconds. Confirm the client retries the original request once, accepts the replacement snapshot, and does not issue additional retries while the same menu remains open.
- [ ] Open each Research Bench tier and confirm it exposes only the Tech Tree. Open each crafting Workbench level and confirm it exposes only TaCZ crafting. Neither screen should offer a mode-switch button.
- [ ] Break or replace either half of a crafting Workbench while its menu is open. Moving away or changing dimension must also invalidate the old crafting session.
- [ ] Learn representative Tier 1, Tier 2, and Tier 3 weapons. Confirm a lower Workbench hides and rejects higher-tier recipes, the appropriate Workbench exposes and crafts them, and Creative bypass follows the server setting without bypassing Progression Gates unless its separate setting is enabled.
- [ ] In an authored-only profile, omit a recipe-backed weapon from the Tech Tree but give it an explicit format-4 crafting rule. After learning it outside the tree, confirm the correct Workbench shows or rejects it from the crafting policy alone. A missing research assignment must not make crafting policy unavailable.
- [ ] Give one weapon a broad research-tier or research-gate rule and a more specific crafting-only rule, then reverse the arrangement. Confirm each action selects its own most-specific applicable rule and an override scoped only to the other action does not erase profile-level gates.
- [ ] Starting from a version-3 server config, upgrade without editing the new crafting controls. Confirm representative guns, ammo, and attachments retain their profile-defined crafting results and existing Blueprint-Free selectors still bypass knowledge only.
- [ ] Change the ammo and attachment crafting strategies through every fixed-level, no-level, disabled, and profile option. Confirm the matching Workbench recipes update without adding either category to the Tech Tree; for ammo, also verify Match Earliest Gun and its unlinked fallback.
- [ ] Put one category or subgroup in both the no-level and disabled lists, then add an exact override for one matching blueprint. Disabled must win the broad conflict, the exact override must win for its one target, and changing these settings with a Workbench open must replace the old allow-list before another craft can complete.
- [ ] Confirm an existing `tacz:gun_smith_table`, an exactly mapped gun-pack workstation, an unknown workstation using the fallback tier, and an unknown unrestricted workstation each follow their configured policy. No TaCZ crafting screen should offer a Research button.
- [ ] Open a crafting Workbench on a delayed or throttled connection. While the recipe response is pending, confirm TaCZ tabs can initialize but no recipe or previous-menu selection is selectable; the screen should show that it is checking access.
- [ ] Keep a crafting Workbench open while changing the active profile, tier enforcement, Creative bypass, or a relevant Progression Gate, and while reloading datapacks. Confirm the old selection disappears as soon as the replacement response begins and cannot be crafted while its chunks are still arriving.
- [ ] Disable blueprint progression and reopen every crafting Workbench level. Confirm the server-approved native TaCZ view includes recipes that are not represented in the blueprint catalog; re-enable progression and confirm recipes remain hidden until a matching restricted response arrives.
- [ ] Confirm the ordinary `tacz:gun_smith_table` recipe is absent after startup and `/reload`, while an existing table still opens. A higher-priority world datapack may deliberately restore that recipe without restoring any other removed content.
- [ ] `/gg research status` reports the selected automatic mode and prerequisite strategy, tree, eligible/excluded counts, planned references/groups/alternate-route groups, omissions, branch boundaries, same/cross-family edges and merges, maximum fan-out, the current catalog/research revision pair, and complete candidate/branch-coordinate/decision/finalized-rank counts; a legacy tree may show the explicitly diagnostic Phase-0 counterfactual, while an authoritative grouped tree instead reports live effective-alternative, ancestry, route-cost, chain, branch-entry, per-phase fan-out/family-density, terminal-affordability, warning evidence, and the Phase-6 retain/prototype/insufficient motif decision with its review limits. A stale publication reports `awaiting_rebuild`, a failed rebuild reports its exact stage and bounded reason, and a later revision clears that stale failure.
- [ ] `/gg research inspect <blueprint>` explains an add-on gun's automatic state, score, confidence, generated rank, optional band, stable sibling order, planned prerequisite or omission, exact branch strategy, parent-family counts, terminal/depth flags, and any grouped route-review outcome/cost bounds/ancestry diversity without adding a line for attachment/ammo entries.
- [ ] `/gg research export` writes format 22 deterministically, preserves canonical prerequisite-group boundaries plus the legacy prerequisite union, records the automatic strategy, explicit generated relationship shapes and hybrid aggregate counts, planned generated groups, and exact/bounded alternative-route review evidence, includes complete independent crafting-policy coverage, dispositions, Workbench levels, assignment sources, selected rules, reasons, and warnings, includes the complete `grouped_route_quality` distributions/warnings/terminal bounds plus `grouped_route_motif_assessment` signals/recommendations/visual-evidence boundary, includes revision-matched research-workstation, Blueprint Fragment, Progression Gate, external-workstation mapping, review-fallback, and omission diagnostics, and its tree-owned band policy, configured/effective layer width, topology population, branch-prerequisite and publication-completeness summaries, planned and published ranks, topology/economy sections, second-parent quotas, strategy-specific rejection evidence, and per-weapon decision/parent relationships agree with status/inspect for representative authored, automatic, excluded, and unplaced weapons.
- [ ] For the Phase 10 default-rollout check, confirm the packaged profile reports `grouped_routes_v1`; a temporary format-3 profile with `prerequisite_strategy: legacy_and` restores mandatory generated pairs without changing authored prerequisites or learned blueprints, and restoring the packaged profile returns to grouped routes without revoking progress.
- [ ] At normal zoom and maximum zoom-out, select targets behind singleton, grouped, and mixed requirements. Confirm route highlighting chooses one viable OR route, grouped junctions remain legible, learning an alternative updates satisfaction without resetting the camera, Fit still frames the full tree, and no new top-heavy or excessive-width regression is visible.
- [ ] In `DIRECT_LEARN`, select a higher unlearned target. Confirm the action reads `Research N blueprints`, RP is the aggregate distinct-node cost, additional material types are disclosed when the bounded preview is truncated, and success learns the prerequisite-first shortest closure plus target. Missing currency/materials and an injected late learning rejection must leave all inventory, points, blueprint/discovery knowledge, and legacy recipe aliases unchanged. In `CREATE_BLUEPRINT`, the same locked target must retain ordinary unmet-prerequisite behavior.
- [ ] In `DIRECT_LEARN`, change the selected route, Tech Tree publication, relevant configuration, or player knowledge after receiving a preview but before submitting its Research action. Confirm the server rejects the stale fingerprint, consumes no RP or items, and returns a fresh preview. Repeated clicks while one selection is pending and rapid selection changes must not create duplicate purchases or unbounded planning work.
- [ ] Track an unlearned goal behind at least one any-of requirement. The compact bottom-edge goal chip must show the exact enabled currency/material progress, the highlighted edge must match the server-selected alternative, and pressing the chip must focus and select the goal without resetting its domain camera unnecessarily.
- [ ] While that goal remains tracked, add, remove, split, and combine matching inventory stacks. The exact route and camera must remain stable, the goal chip must briefly report Checking, and its material totals must refresh after the short debounce without double-counting an item shared by two requirements.
- [ ] Close the Research Bench while a tracked-goal request is pending, then reopen it without changing the target or inventory. The reopened Bench must issue a replacement request and must not remain in Checking indefinitely. A terminal route rejection must read as unavailable rather than Checking.
- [ ] Repeat the tracked-goal check in `POINTS_ONLY`, `ITEMS_ONLY`, `POINTS_AND_ITEMS`, and Creative mode. Disabled channels must be absent, Creative must say costs are waived rather than inventing progress, a progression-capacity failure must remain distinct from missing resources, and a completed goal must remain visible until deliberately untracked.
- [ ] Open tree help while a goal is tracked and test keyboard navigation plus GUI scales 1 through 4. The help coachmark and goal chip must never overlap, the chip must stay on-screen with long translated weapon names, narration must expose its status and next step, and focused nodes must not center behind it.
- [ ] In fullscreen, enable Affordable Now beside search. The compact tooltip must report progressive checked/total and affordable counts; unchecked nodes remain visually neutral, learned nodes remain legible context, and unaffordable or blocked nodes dim without disappearing, moving, or resetting the camera.
- [ ] While Affordable Now is checking a large tree, use search, hover relationships, select a node, and keep a goal route tracked. Those higher-priority states must remain readable above the affordability dimming, and the filter control must own its click area instead of selecting a covered graph node.
- [ ] Add and remove RP or matching inventory materials with Affordable Now enabled. Results must return to neutral Checking after the short stability debounce and then refresh in bounded batches. Changing the tree generation, closing the Bench, reconnecting, or receiving a stale/out-of-order result must not retain or apply stale classifications.
- [ ] Open the same large tree for two players and enable Affordable Now together. Both sweeps must make round-robin progress without a render-thread stall, unbounded packet, or one player monopolizing the shared one-target-per-tick planning fuse.
- [ ] In `CREATE_BLUEPRINT`, confirm the tracked-goal price chip and Affordable Now control are hidden. Switch back to `DIRECT_LEARN` and confirm both become available after the server menu-data update without reopening the Bench.
- [ ] For the Phase 12 visual-refinement check, use a large catalog with several mature weapon families and force at least one semantic rank to wrap. Include sharply uneven family sizes and a rank where authored nodes share space with only one mature automatic family. The client should keep the minimum required row count, prefer wrap boundaries between families when that does not leave a row below half its balanced target, keep each fitting family coherent even with ordering sweeps set to zero, retain a dense shared base, and avoid both a tall/thin column and a wide top-heavy slab.
- [ ] Inspect a target with two or more drawable any-of groups. Its junction diamonds and outgoing approaches must occupy distinct small vertical lanes, remain associated with the correct alternatives, and neither overlap nor add extra height to unrelated ranks.
- [ ] At the first mature branch split, confirm the branches have a small but noticeable seam that grows gradually toward the top. The seam must remain much smaller than a full empty branch corridor, and similarly scored terminal weapons may still end as a two- or three-node cohort on one layer.
- [ ] Changing the undiscovered-visibility preset does not redact or change the operator export's weapon costs, parent sets, topology metrics, or economy totals; no real player progress changes while exporting.
- [ ] A fresh live export reports the installed catalog's resolved costs and current automatic cost authority. Historical authored-policy fixtures with 418 total currency units and 218 finite units remain reference data, not expected prices for the current automatic tree.
- [ ] A format-2 tree with no `bands` renders a rank-only tree with no empty tier gutters; adding three custom bands labels only bands that contain nodes, and removing the middle band's members leaves no vertical gap.
- [ ] Switching the same format-2 tree between `none`, `dynamic`, and `configured` leaves every prerequisite, currency/material cost, availability state, and research result unchanged.
- [ ] A representative third-party pack produces the same automatic positions
  across two restarts and across different resource discovery orders; no
  weapon disappears when the active candidate count approaches the practical
  pack maximum.
- [ ] Switching `connected` back to `independent` and reloading removes every
  generated prerequisite without changing learned/discovered state; removing
  the custom profile entirely produces the same compatibility-first behavior.
- [ ] Existing learned blueprints remain learned after enabling or rearranging Tech Tree presentation data.

## Blueprint Fragment supply

- [ ] Enable a fragment preset and set Fragment Loot Share to 0, 20, and 100 percent. With a fixed loot seed, 0 preserves full-blueprint output, 100 replaces every valid opportunity, and 20 replaces a measured share without increasing the total number of research items.
- [ ] Generate loot from a player-caused context with learned and unlearned targets. Unlearned targets should appear more often, while learned targets remain possible. Generate a fresh chest without player context and confirm it follows the configured catalog weights instead.
- [ ] Confirm gun, ammo, and attachment blacklists, rule selectors, disabled kinds, progression exemptions, and fragment-disabled profile entries exclude the same targets from fragment supply that they exclude from the originating blueprint pool.
- [ ] Put matching fragments together and confirm they stack to 64. Fragments for different targets must remain separate. A valid target must use the current weapon name and record discovery when carried.
- [ ] Test a malformed target tag and a canonical target removed by a content-pack reload. The item must read Invalid or Unknown respectively, must not crash inventory rendering, and must not disclose a hidden fallback ID.
- [ ] Run `/gg loot preview <loot_table>` for player-aware and generated-container cases. Confirm the replacement share, target count, expected fragments, thresholds, and exact-override count match `/gg research inspect` and `/gg research export`.
- [ ] Disable every fragment policy, empty the eligible blueprint pool, then force a stale progression-policy publication during reload testing. Each case must retain normal full-blueprint behavior and must not fail the loot table.
- [ ] Use loot tables that resolve 8, 256, and at least 4,096 fragment candidates. Confirm generation remains responsive, repeated reloads retain canonical ordering, and the measured replacement rate stays within one percentage point of its configured value over 100,000 successful opportunities.

## Reloads, content packs, and networking

- [ ] `/reload` while the bench is open replaces the tree and preview atomically.
- [ ] Have two players use the same Research Bench and submit research requests
  on the same server tick. Each accepted purchase uses that player's own
  progression, and a request prepared from superseded shared state is rejected
  without duplicate item or RP consumption.
- [ ] Have two players open the same Blueprint Analyzer and try to archive the
  same fragment stack. Only the first valid transaction commits; the second
  receives a stale-state result without duplicating output, fragment progress,
  or RP.
- [ ] Die, disconnect, reconnect, change dimension, and stop/restart the server
  with a research preview or deferred update pending. Old sessions and packet
  chunks must not combine with the new player or server session, while saved
  knowledge, RP, fragments, and Progression Gate criteria remain intact.
- [ ] If a reload fault is injected after catalog publication but before
  automatic-placement or progression-policy publication, the server either
  rebuilds the complete revision-matched set from the current catalog or keeps
  research fail-closed with one actionable error; it never advertises a stale
  route as researchable.
- [ ] A stale selected node returns safely to Browse after its rule or content is removed.
- [ ] Removing a TaCZ content pack leaves no broken connector or crash; restoring it makes persisted unlocks usable again.
- [ ] An unmatched add-on gun receives a named/icon preview node and follows the active automatic-placement/review policy without first finding its physical blueprint.
- [ ] Under the packaged profile, unmatched ammo and attachments retain their authored 4 RP Preview fallback rules but remain non-researchable and unpublished; enabling their domains makes the fallbacks selectable without fabricating prerequisites.
- [ ] Large generated add-on weapon families wrap across Tech Tree rows instead of one ultra-wide strip, and repeated reloads produce the same placement.
- [ ] Changing dormant legacy group metadata during `/reload` does not change the visible Tech Tree or reintroduce a hidden view selector.
- [ ] Dedicated server rejects a client with any protocol other than `66`.
- [ ] With the Blueprint Journal disabled, identities still disclosed by the Tech Tree receive their Blueprint Fragment and public Progression Gate state. Silhouette, hidden, omitted, and unrelated identities receive no supplemental state.
- [ ] Select a node with a Bench-tier requirement, a Progression Gate, and archived Blueprint Fragments. Its details and narration list the current/required tier first, the active blocker, then fragment progress; only the selected node receives the small progress/blocker marks.
- [ ] Open the Blueprint Journal and confirm full-detail entries show their Workbench level, no-level access, or unavailable state. Disclosed fragment targets show progress in details and a compact row suffix only after at least one fragment is archived. Preview, name-only, and silhouette entries reveal no crafting policy, and name-only and silhouette entries reveal neither the target ID nor fragment metadata.
- [ ] Track a target blocked by the current Bench tier or a Progression Gate. The goal explains the blocked route and `Next` does not recommend a step from that inaccessible route. Affordable Now keeps the target non-affordable even when its RP and materials are sufficient.
- [ ] Use mouse, keyboard, and narration to inspect the same selected target at a Research Bench and craft it at a Workbench. Tooltips and spoken state identify the action and station tier without requiring an internal ID.
- [ ] Run `/gg progression inspect <player> <blueprint_id>`, `/gg research inspect <blueprint_id>`, and `/gg progression workstation <workstation_id>`; compare the independent research and crafting results, mapped levels, assignment source/rule/reason/warnings, fragment threshold/progress, and gate groups with the active configuration. Repeat with an entry omitted from the authored Research Tree and confirm its complete crafting assignment is still reported.
- [ ] Reconnect, dimension change, and respawn do not combine old and new tree chunks.

## Bench model and blocks

- [ ] Item, first-person, third-person, and inventory icon face forward.
- [ ] North, east, south, and west placements face the player correctly.
- [ ] Obstruct or fault-inject the extension-half write during placement. The
  root is rolled back and the held Bench item is not consumed; a successful
  retry places both halves.
- [ ] Both halves open the same menu and cannot be separated by pistons.
- [ ] Breaking either half removes the complete bench and produces exactly one drop.

## Release screenshots

Capture clean, HUD-appropriate PNGs at native resolution:

1. Fullscreen Tech Tree with the Research Trees domain sidebar, one connected prerequisite tree, a selected node, visible arrowheads, and several available and locked nodes.
2. Selected-node contextual tooltip showing prerequisites, unlocks, currency cost, inventory material counts, and readiness.
3. Fullscreen overlay with the world visible behind the tree and the floating Research action enabled.
4. Blueprint Analyzer screen showing a physical TaCZ item, its exact reverse-engineering cost, and the extract-only blueprint output.
5. Research Bench model placed in a representative TaCZ workshop.
6. Blueprint Analyzer model placed beside the Research Bench with its paper
   intake, top blueprint inset, output drawer, and control face visible.

For redesign comparisons, capture the same state and camera at 320 x 240,
854 x 480, and the normal release-test resolution. Record GUI scale and JAR
hash, and compare each candidate against the Phase 0 evidence manifest rather
than mixing captures from different builds.

For grouped-prerequisite Phase 12, also capture a matched before/after pair from
the same catalog, viewport, GUI scale, Fit state, and selected node. Include the
dense lower trunk, the first mature family split, the upper terminal cohorts,
and one multi-group any-of junction. Record the resolved node capacity and
confirm the after image shows clearer small branch seams without a top-heavy,
excessively wide, or tall/thin regression.

Do not use a development build watermark or a screenshot from a different JAR
than the recorded release hash.

## Scene migration regression cases

- [ ] Search results remain readable and clickable when they overlap the narrow
  inspector. Enter selects the active result; Escape closes search before help
  and inspected selection. Closing the inspector does not repin it on a refresh.
- [ ] Scroll help with the wheel or arrow/Page keys. The inspector, item art, and
  graph never draw over it. Its dismiss action remains accessible at GUI 320×240.
- [ ] Focus, hover, inspected selection, active search, tracked target, and route
  marks can coexist without changing geometry. The affordability lens preserves
  these contexts and dims only other blocked/learned choices.
- [ ] A cross-domain portal is validated against the displayed scene, opens the
  correct public domain, and never selects a hidden or withdrawn target.
- [ ] F3+T and language changes refresh names, search results, and artwork while
  preserving geometry/camera when measured dimensions remain unchanged.
- [ ] Withdraw the current domain/publication while search, tracking, and an
  inspector are open. Old nodes, results, tooltips, pins, and art disappear before
  the next render/input. Restore valid data and verify current selection/preview.
- [ ] Inject a rejected replacement. The screen shows unavailable with no old
  graph or active research action; subsequent valid input or reopening recovers.
- [ ] Close/disconnect and reconnect with another profile or server. No camera,
  query, pin, preview, or tracked route from the prior menu becomes active.


## Authored branch layouts

- [ ] Load the [nested example](../examples/authored-branches-datapack/README.md)
  on a dedicated server and confirm five branches, eight visible nodes, and the
  shared M4A1 requirement in status/export output.
- [ ] Connect with a matching protocol-66 client. Check nested containment,
  sibling spacing, translated labels, and the cross-branch OR requirement.
- [ ] Change only a branch title and reload; check that the label refreshes
  without moving nodes. Change membership and verify that geometry refreshes.
- [ ] Test `anchor_revealed` with hidden, anonymous, and revealed anchors. Hidden
  labels and translation keys must not appear until the anchor is revealed.
- [ ] Remove the example and restore the prior profile. Confirm that formats
  1 and 2 still use the inferred layout and preserve research requirements.

## Performance and final artifact checks

Use the same frozen public input, camera, window/framebuffer size, GUI scale,
resource packs, and graphics settings for before/after comparisons. Keep builds
and offline profiling stopped during native timing. Record canvas CPU separately
from the full Research Bench and paced frame interval. Include the first useful
frame, opening, stationary overview, target detail, pan, zoom, resource reload,
and publication withdrawal/restoration. Preserve server inventory and authority.

Exercise the authored-branches example in an isolated world with its selected
profile. Verify nested region labels, the shared later milestone, and cross-branch
OR alternatives at normal and minimum size. Repeat with long translated labels.
Check GUI scales 1–4 and Auto, 320×240 GUI, 854×480 window, and bright/dark worlds.
Stress checks must record explicit unavailable results and verify that a later
valid publication recovers. Do not treat a written report as proof of valid geometry.

Tie the final client/server checks to a source digest and tested JAR hash. Run a
fresh full test/build and artifact verification. Release certification follows
version and release-note finalization; implementation validation does not publish
or certify an unfinished release.

## Station access modes

- [ ] At normal and minimum GUI sizes, Portable Research opens through the
  Journal / Research key, and its J button opens the Journal without overlapping
  persistent controls or the requirements inspector.
- [ ] Compare all six combinations of Analyzer setup and research access on a
  client connected to a dedicated server. Change modes with previews open;
  confirm old previews cannot spend currency and recipe lists refresh.
- [ ] Confirm single-station tooltips and screen headings are clear, existing
  higher-tier stations remain usable, and crafting tiers remain independent.
- [ ] Verify a real research purchase, whole-path purchase, physical blueprint
  output, death, dimension change and reconnect in Portable Research mode.

## Purchase-bound route review

- [ ] Track A, inspect B, and choose Review research path. A remains tracked.
- [ ] Check new blueprints, learned support, totals, and chosen alternatives with
  insufficient currency and materials. Review availability does not enable Research.
- [ ] Follow a disclosed reviewed step and use Back. Restore B and its view.
- [ ] Resize to 320×240 GUI pixels. Keep the review open with at least four detail
  rows, fixed purchase information, and distinct Review/Track/Research controls.
- [ ] Change inventory while the route fingerprint stays the same. Withdraw the
  old allocation and refresh it from the current selection preview.
- [ ] Replace selection, publication, configuration, fragments, or learned state
  with a response in flight. Never label the obsolete response Current purchase.
- [ ] Reject malformed or undisclosed review content and oversized payloads.
  A failed review must not block an otherwise valid research purchase.
- [ ] Verify protocol-66 peers; older peers must be rejected by channel negotiation.

## Zoom readability and route emphasis

- [ ] At whole-tree and distant overview, identify Shared foundations and branch
  titles. Click a title to frame its region, then drag a title to pan without
  activating it. Click a node at overview to inspect that node.
- [ ] Cross artwork, name, status, and junction thresholds in both directions.
  Check stable artwork size, readable priority labels, and separate focus/pin marks.
- [ ] Track A and review B. Confirm thicker gold review lines, direct requirement
  emphasis, quieter ancestry/context, and A's cyan goal cross. Withdraw or change
  the review and check that its emphasis disappears immediately.
- [ ] Check crossing gaps and AND-of-OR junctions with highlighted routes at
  close, overview, and minimum GUI sizes. Pan regions partly off-screen.
- [ ] Check long authored/translated branch titles, overlapping labels, and
  search focus. Confirm labels stay inside the canvas and full titles have tooltips.
- [ ] Reopen a compatible saved view without recentering. For a fresh view,
  confirm tracked-target/frontier framing without assuming exact affordability.

## Artwork preparation and navigation performance

- [ ] First-seen disclosed artwork replaces its temporary blueprint fallback
  without blocking navigation. Hidden nodes never request undisclosed artwork.
- [ ] Warm panning and zooming neither rebuild scene geometry nor decode already
  cached artwork. Record screen CPU timings and render-thread allocations
  separately from paced frame intervals and GPU/whole-game performance.
- [ ] Repeat resource reload while browsing and while preparation is pending.
  Updated art replaces old art, failed art uses the fallback, and queue/cache
  sizes settle without retaining earlier resource generations.

### Automated native input regression check

The development Workbench probe accepts `verify-input-regressions` in its disposable QA world. Select a blueprint with a current preview and expand Details before running it. It checks Help focus containment, blocked shortcuts and research requests, scrolling and dismissal, and deferred Back restoration with and without newer pointer or keyboard input. Results are written to `input-regressions.json` in the configured probe output directory. The Back check holds a pending restoration until a widget refresh; also run the delayed-network scenario above.

### Playtest refinements

- [ ] A single node click opens expanded details at 320×240, 512×282, and 854×480 GUI sizes. After collapsing, selecting the same node reopens details.
- [ ] The Path button stays in the title row without covering a long weapon name. Cost, materials, prerequisite, and progression sections remain readable and scroll independently of the action row.
- [ ] Branch labels shows On or Off and toggles branch titles and their hit targets. Zoomed-out branches omit titles that do not fit; displayed titles avoid tiles, connectors, junctions, portals, and other labels.
- [ ] Tiles without internal names use the full padded interior for weapon artwork. Names appear only when readable, with aspect ratio preserved in both layouts.
- [ ] Section gaps and indented prerequisite links have correct pointer hit tests. Follow a link with mouse and keyboard, then use Back; scroll position and the tracked goal remain correct.

### Inspector cost table and prerequisite wording

- [ ] One purchase-scope heading precedes a Resource / Have / Required table. Physical currency names match the server denomination. Material Have values match the server quote's allocated amounts.
- [ ] Check points-only, items-only, creative bypass, free research, stale/missing previews, planning failure, learned nodes, and quotes with additional material types beyond the preview limit. Disabled channels must not appear as shortages.
- [ ] At 320×240, 512×282, and 854×480, resource names can wrap without moving numeric columns. Large numbers and translated headers fit their measured columns. Clicking a cost row narrates the resource and both amounts.
- [ ] One alternative reads Required. Multiple alternatives read Any one satisfies this requirement, including when some alternatives are undiscovered or outside the current view. Clicking a linked name only inspects it. Path is the only source of verified route-choice information.

### Saved labels and contextual focus

- [ ] Toggle Branch labels, reopen the Research Bench, then restart the client. The state and button text agree. Tree Appearance exposes the same preference; its reset restores On.
- [ ] Toggle while panning or zooming. Selection, tracking, camera, topology, and purchase quote remain unchanged. No invisible label click targets remain before or after the next frame.
- [ ] Pan and zoom slowly around label-fit thresholds. A valid label stays at its branch-relative anchor. It moves or disappears only when necessary, and needs additional clearance before reappearing.
- [ ] Open View at 854×480, 512×282, and 320×240 GUI sizes. Focus blueprint and Focus branch frame the inspected target without choosing a purchase route. If details cover the canvas, focusing reveals the canvas.
- [ ] View contains mouse, wheel, drag, character input, and camera/research shortcuts. Tab and arrows cycle its actions; Enter/Space activate. Escape restores the View button's focus. An outside click only dismisses. Help, search, resizing, and selection changes close the menu.
- [ ] The development probe's `verify-view-controls` checks menu input containment, Escape, outside clicks, Help, and immediate removal of hidden-label hits in the disposable QA world.

### Unified node presentation

- [ ] Slowly zoom through overview, artwork, one-line names, and two-line names in both directions. Tiny movements near a threshold keep the current presentation. Direct zoom jumps settle immediately.
- [ ] At the same camera scale, inspect or keyboard-focus a visible node and move search focus to it. Its artwork rectangle and in-card name stay unchanged. External names appear only when the in-card name is absent and a safe location fits.
- [ ] Compare short and long names at maximum zoom. A short name reserves one line. Longer names can use two; remaining overflow has an ellipsis and the hover tooltip exposes the full published name.
- [ ] Track A and inspect B. Each has separate corner marks; A has exactly one cyan goal cross. Check the overview marker too. Status, fragments, and blocker marks stay readable against weapon artwork.
- [ ] At 854×480, 512×282, and 320×240 GUI sizes, repeat node selection, cost inspection, View focus, label toggling, Help, and Back. Small-screen overlays fully occlude node art and names underneath them.
- [ ] Use custom scenes with different tile sizes, unavailable textures, restricted nodes, and resource reload. Each node's detail follows its own projected dimensions; missing art uses its blueprint fallback, and restricted identity stays undisclosed.

Development probe snapshots include `node_presentations`: each visible node's
actual art/name rectangles, name lines, channels, and decoration flags. Compare
these with screenshots when checking selection stability and zoom thresholds.
