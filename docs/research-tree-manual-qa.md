# Research Tree Manual QA Matrix

This matrix covers behavior that unit tests and artifact inspection cannot prove
without a real Minecraft client. Complete it on the exact release candidate and
record the game version, Forge version, TaCZ version, mod JAR hash, operating
system, and display resolution with the results.

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

Automated Phase 8 preflight is a JDK 17 `./gradlew cleanTest build`; the final
versioned candidate additionally runs `./gradlew certifyReleaseCandidate` after
the changelog release heading is finalized. Success certifies packaged
structure, data, protocol, and report metadata. It does not certify any
unchecked hands-on behavior below.

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
- [ ] With JEI only, EMI only, and both installed, the Bench, Analyzer,
  blueprint, and Research Data information pages are present and localized.
  They expose no hidden blueprint list and provide no research recipe transfer.
- [ ] With neither viewer installed, a client and dedicated server reach their
  normal startup markers without missing-class or optional-dependency errors.
- [ ] Search, zoom controls, tree, contextual tooltip, overlaid rail, and buttons do not overlap or clip.
- [ ] Long weapon names are clipped cleanly and remain readable in tooltips.
- [ ] Long translated button labels remain identifiable and clickable.
- [ ] Inventory and material slots are absent and inactive in Research.
- [ ] Only the dedicated Blueprint Analyzer exposes the contextual input,
  extract-only output, and player inventory.
- [ ] Fullscreen is a translucent world-backed overlay at every supported size and reserves no permanent right, bottom, or drawer details panel.
- [ ] Opening, resizing, and closing the permanent Research Tree preserve a valid focus without exposing inactive inventory slots.
- [ ] Search text and independent per-domain Tech Tree pan and zoom positions survive repeated domain changes.
- [ ] Fullscreen controls remain readable over bright and dark world backgrounds.
- [ ] The pinned context card fully covers every graph edge, label, badge, and item model behind it; only the card's own icons and widgets render above its background.
- [ ] Selected, focused, and hovered rail labels avoid the pinned context card; if space is exhausted, the conflicting label stays hidden and never claims clicks through the card.
- [ ] Changing Research Tree Settings spacing, wrapping, and pass settings while the bench is open refreshes Tech Tree geometry once without changing the server tree or entering a rebuild loop.
- [ ] A large content-pack catalog with many entries at the same progression
  level wraps into multiple Tech Tree rows without overlap or an empty first publication.
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

## Tree interaction and accessibility

- [ ] Dragging empty canvas space pans smoothly and remains bounded.
- [ ] Wheel, `+`, `-`, and Fit operate at both zoom limits in the permanent Research Tree.
- [ ] Search highlights matches, and committing the active result centers it.
- [ ] Typing a search query updates highlights and result count without panning, changing the focused node, or changing domains; Up/Down changes the active result and Enter or a result click navigates exactly once.
- [ ] Closing and reopening search preserves a valid query and active result without changing the authoritative research selection.
- [ ] Hovering an ordinary node shows only its published name and one concise status line; it never exposes selected-card cost, inventory, or readiness details.
- [ ] Clicking a node shows one clear next step plus direct requirement and immediate unlock counts; the pinned card exposes exact RP and inventory materials.
- [ ] Clicking a node pins the current selection and exposes the floating Research action only after its matching authoritative preview arrives.
- [ ] Single and double clicks only select; the Research button performs the action exactly once and remains disabled while its server result is pending.
- [ ] With Hold to Research enabled, holding an already-selected Ready node shows a smooth progress bar and submits exactly once at the configured duration.
- [ ] Releasing a hold early, dragging far enough to pan, selecting another node, closing the Bench, or disabling the shortcut cancels it without spending RP or materials.
- [ ] A first click on an unselected Ready node only selects it; it never inherits enough held time to research it.
- [ ] With a full inventory, successful research creates exactly one world-drop blueprint; if another mod cancels that drop, the transaction reports failure and restores every RP and material input.
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
  for research, spends RP or materials, or sends a transaction packet.
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
- [ ] While a goal is active, `Next` only recommends an available step on that
  goal's selected AND-of-OR route. An unsatisfied any-of group contributes one
  alternative rather than every member. Untracking restores the global recommendation.
- [ ] Plan tooltips show the exact total of published RP and material requirement
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
- [ ] Fullscreen Help reopens the same non-blocking first-visit coachmark after it has been dismissed.
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
- [ ] Selected and relationship context still distinguishes insufficient-RP, undiscovered, prerequisite-locked, disabled, over-cap, unavailable, Preview, and anonymous states after the graph collapses them into four families.
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
- [ ] Clicking a requirement or unlock card focuses the expected node; relationships remain available in the contextual tooltip and highlighted path.
- [ ] The focused card shows a useful next action for learned, ready, insufficient-RP, discovery-locked, prerequisite-locked, disabled, over-cap, unavailable, Preview, and anonymous nodes.
- [ ] Requirement cards show completion status, unlock cards are visually distinct, and their counts remain truthful when more relationships exist than compact slots.
- [ ] Anonymous mystery requirement cards show no identity and do nothing when clicked.
- [ ] Escape closes search, first-visit guidance, and a pinned node card in that order; the next Escape closes the Research Bench rather than revealing a compact screen.
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
  RP/material cost, or Research Point award.
- [ ] Repeated relog and `/reload` do not duplicate starter knowledge or awards;
  removing a starter from configuration does not revoke knowledge already
  granted.
- [ ] Missing/blocked starter IDs and unmatched subgroup selectors do not crash,
  expose phantom recipes, or partially corrupt progression, and `/gg research
  status` reports actionable diagnostics.

### Blueprint Analyzer lifecycle

- [ ] The Recycler uses its worn-steel final model rather than an iron-block
  placeholder in-world, in the inventory, as a dropped item, and in JEI/REI if
  installed.
- [ ] North/east/south/west placement keeps the paper intake on the interaction
  side, does not mirror its controls, and uses an outline/collision shape that
  follows the cabinet, deck, console, intake, drawer, and feet.
- [ ] The shaped model does not hide adjacent block faces, produce opaque-cube
  lighting seams, or let the player target empty corner space as solid machine.
- [ ] Shift-clicking the Analyzer input or output returns its complete stack to the player inventory; shift-clicking any player slot targets only the Analyzer input and never its output or another player slot.
- [ ] The output rejects manual insertion but permits extraction and shift-click extraction.
- [ ] Closing the Analyzer with unconsumed input or unclaimed output returns both to inventory, or drops each exactly once when the inventory is full.
- [ ] Walking out of range, breaking the Analyzer, disconnecting, and a server-forced menu replacement each return or drop both server-owned slots exactly once.
- [ ] Replacing the input immediately after a result never leaves the old success or failure text attached to the new item.
- [ ] A configured but capped Research Data item remains identified and untouched; an ordinary unsupported item remains Invalid even while player award data is unavailable.
- [ ] Redeem All reports the next award without promising every remaining item will be accepted, stops at policy/cap boundaries, and leaves every rejected item intact.
- [ ] With narration enabled, empty, invalid, ready, blocked, processing, success, and failure states announce the item type, status, reward or balance, and available action.
- [ ] At every supported GUI scale and with a deliberately long translation, hovering the detail area exposes the full text hidden by wrapping or visual clipping.
- [ ] An eligible unloaded and attachment-free gun resolves its logical TaCZ ID,
  shows the exact server RP/material cost, and creates one protected blueprint
  only after Analyze is pressed.
- [ ] An attachment consumes exactly one physical item; ammunition consumes the
  canonical output batch or its explicit rule override and leaves any remainder
  in the input.
- [ ] Loaded guns, guns with removable attachments, insufficient physical count,
  missing RP/materials, occupied output, known/blocked/exempt content, and stale
  state-token requests consume nothing.
- [ ] A permitted customized item warns that customization will be lost; a rule
  that rejects modified items leaves it intact.
- [ ] A completed analysis marks discovery only, does not learn the recipe, and
  cannot recycle the protected output for RP under the packaged policy. Using
  that physical blueprint later learns the recipe according to its provenance.

- [ ] A fresh player sees all 53 recipe-backed default TaCZ weapons in one connected weakest-to-strongest Tech Tree without a legacy view selector.
- [ ] Glock 17 is the normal shared entry; in a test datapack that removes or blacklists it, M9A4 becomes the selectable root and every class starter points to it instead of remaining locked behind Glock.
- [ ] Every ordinary overview connector corresponds to a prerequisite actually enforced by the server.
- [ ] A weapon learned before the connected default-tree update remains learned even when its newly authored prerequisite is not present in that save.
- [ ] Two simultaneous players see different learned/available states without sharing client state.
- [ ] Locked nodes explain points, discovery, or prerequisite requirements correctly.
- [ ] Research automatically consumes only the exact required amounts from the main inventory and hotbar and preserves unrelated stacks.
- [ ] The Research button and configured hold shortcut produce identical server-authoritative results.
- [ ] While a request is pending, repeated mouse or keyboard activation cannot submit a duplicate transaction.
- [ ] Successful research provides visible card feedback and an audible cue; a rejected request leaves its reason visible near the selected weapon instead of relying only on the action bar.
- [ ] The Research Bench contains no duplicate input; closing the dedicated Analyzer returns unused input and unclaimed output, dropping them only when inventory insertion is impossible.
- [ ] Insufficient points, ingredients, or prerequisites consume nothing.
- [ ] In packaged `DIRECT_LEARN` mode, successful research charges the exact
  current RP and inventory cost, learns immediately, and produces no item even
  when inventory is full. In temporary `CREATE_BLUEPRINT` compatibility mode,
  it produces one physical blueprint without learning until that item is used.
- [ ] Successful recycling consumes one learned duplicate and awards the exact configured RP.
- [ ] Creative bypass behaves exactly as the synchronized server setting specifies.
- [ ] `/gg progression points give <targets> <amount>` updates every successful target immediately, skips over-cap targets without changing them, and cannot exceed the configured RP cap.
- [ ] `/gg progression reset <targets> awards` clears only RP award history; `learned`, `discovered`, and `points` preserve it, while `all` clears it with the rest of progression.
- [ ] On a fresh player, the first valid blueprint discovery grants exactly 1 RP; repeated inventory ticks, dropping and repicking the same blueprint, relogging, and restarting grant nothing more for that blueprint.
- [ ] An existing player receives each eligible retroactive advancement and 10/25-discovery or 5/15/30-learned milestone once, in bounded batches, without duplicate notifications after relog or `/reload`.
- [ ] Acquire Hardware, Diamonds!, A Terrible Fortress, Into Fire, The End?, and Free the End grant 2/4/4/4/6/8 RP respectively, while ordinary combat grants no RP with the packaged defaults.
- [ ] Near the RP cap, a finite discovery, milestone, or advancement reward remains unclaimed instead of partially paying; after enough RP is spent, reconciliation grants its complete value exactly once.
- [ ] Research Notes, Reports, and Dossiers appear only through the eight documented exploration-chest modifiers and redeem for exactly 1/3/6 RP in the Blueprint Analyzer.
- [ ] Near the RP cap, each Research Data item is rejected intact when its full 1/3/6-point value will not fit; bulk redemption stops without consuming the rejected stack.
- [ ] Replacing or removing one packaged award or Research Data loot modifier with a higher-priority datapack changes only that definition after `/reload`; malformed award data preserves the previous award revision.
- [ ] Disabling `enableResearchPointAwards` stops datapack awards and built-in Research Data loot injection without deleting balances or claim history; reenabling it does not replay already claimed finite awards.

## Tech Tree follow-up

These checks cover the optional third view from its original acceptance
contract through the current unified-domain implementation. Leave a box
unchecked until that behavior has been confirmed in a real client/server run.

- [ ] Tech Tree is the sole player-facing choice; the dormant Branches and All Weapons projections never appear in compact or fullscreen navigation.
- [ ] With the packaged profile, Tech Tree opens Weapons and shows Attachment and Ammo domain slots as disabled/empty without publishing their nodes.
- [ ] Compact Tech Tree mode shows three direct domain icons in stable Weapons, Attachments, Ammo order; hover/narration identifies the domain and visible blueprint count, and a domain with no visible publication is disabled rather than opening a stale tree.
- [ ] Compact and fullscreen show the same three stable domain slots after removing one domain during `/reload`; the missing slot is disabled, Page Up/Page Down skips it in both directions, and restoring the domain makes the same slot usable again.
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
- [ ] With the automatic-placement profile set to `distributed`, eligible add-on guns move to stable stat-sorted ranks after `/reload`, no generated rank exceeds the tree's `layout.max_nodes_per_layer`, and RP costs, materials, availability, and prerequisite connectors remain unchanged.
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
- [ ] A client or server still using protocol 37 is rejected cleanly. Matching protocol-38 peers reconstruct identical group ordinals, alternatives, hidden counts, disclosure state, and aggregate path-purchase previews even when chunks arrive out of order.
- [ ] `/gg research status` reports the selected automatic mode and prerequisite strategy, tree, eligible/excluded counts, planned references/groups/alternate-route groups, omissions, branch boundaries, same/cross-family edges and merges, maximum fan-out, the current catalog/research revision pair, and complete candidate/branch-coordinate/decision/finalized-rank counts; a legacy tree may show the explicitly diagnostic Phase-0 counterfactual, while an authoritative grouped tree instead reports live effective-alternative, ancestry, route-cost, chain, branch-entry, per-phase fan-out/family-density, terminal-affordability, warning evidence, and the Phase-6 retain/prototype/insufficient motif decision with its review limits. A stale publication reports `awaiting_rebuild`, a failed rebuild reports its exact stage and bounded reason, and a later revision clears that stale failure.
- [ ] `/gg research inspect <blueprint>` explains an add-on gun's automatic state, score, confidence, generated rank, optional band, stable sibling order, planned prerequisite or omission, exact branch strategy, parent-family counts, terminal/depth flags, and any grouped route-review outcome/cost bounds/ancestry diversity without adding a line for attachment/ammo entries.
- [ ] `/gg research export` writes format 18 deterministically, preserves canonical prerequisite-group boundaries plus the legacy prerequisite union, records the automatic strategy, explicit generated relationship shapes and hybrid aggregate counts, planned generated groups, and exact/bounded alternative-route review evidence, includes the complete `grouped_route_quality` distributions/warnings/terminal bounds plus `grouped_route_motif_assessment` signals/recommendations/visual-evidence boundary, and its tree-owned band policy, configured/effective layer width, topology population, branch-prerequisite and publication-completeness summaries, planned and published ranks, topology/economy sections, second-parent quotas, strategy-specific rejection evidence, and per-weapon decision/parent relationships agree with status/inspect for representative authored, automatic, excluded, and unplaced weapons.
- [ ] For the Phase 10 default-rollout check, confirm the packaged profile reports `grouped_routes_v1`; a temporary format-3 profile with `prerequisite_strategy: legacy_and` restores mandatory generated pairs without changing authored prerequisites or learned blueprints, and restoring the packaged profile returns to grouped routes without revoking progress.
- [ ] At normal zoom and maximum zoom-out, select targets behind singleton, grouped, and mixed requirements. Confirm route highlighting chooses one viable OR route, grouped junctions remain legible, learning an alternative updates satisfaction without resetting the camera, Fit still frames the full tree, and no new top-heavy or excessive-width regression is visible.
- [ ] In `DIRECT_LEARN`, select a higher unlearned target. Confirm the action reads `Research Path (N)`, RP is the aggregate distinct-node cost, additional material types are disclosed when the six-row preview is truncated, and success learns the prerequisite-first shortest closure plus target. Missing RP/materials and an injected late learning rejection must leave all inventory, points, blueprint/discovery knowledge, and legacy recipe aliases unchanged. In `CREATE_BLUEPRINT`, the same locked target must retain ordinary unmet-prerequisite behavior.
- [ ] For the Phase 12 visual-refinement check, use a large catalog with several mature weapon families and force at least one semantic rank to wrap. Include sharply uneven family sizes and a rank where authored nodes share space with only one mature automatic family. The client should keep the minimum required row count, prefer wrap boundaries between families when that does not leave a row below half its balanced target, keep each fitting family coherent even with ordering sweeps set to zero, retain a dense shared base, and avoid both a tall/thin column and a wide top-heavy slab.
- [ ] Inspect a target with two or more drawable any-of groups. Its junction diamonds and outgoing approaches must occupy distinct small vertical lanes, remain associated with the correct alternatives, and neither overlap nor add extra height to unrelated ranks.
- [ ] At the first mature branch split, confirm the branches have a small but noticeable seam that grows gradually toward the top. The seam must remain much smaller than a full empty branch corridor, and similarly scored terminal weapons may still end as a two- or three-node cohort on one layer.
- [ ] Changing the undiscovered-visibility preset does not redact or change the operator export's weapon costs, parent sets, topology metrics, or economy totals; no real player progress changes while exporting.
- [ ] The packaged weapon economy reports 418 RP total, 10–54 RP single-parent leaf paths, 10–88 RP AND-aware leaf closures, 218 finite configured RP, 16 AND merges, and `research_policy` cost authority with no automatic cost curve.
- [ ] A format-2 tree with no `bands` renders a rank-only tree with no empty tier gutters; adding three custom bands labels only bands that contain nodes, and removing the middle band's members leaves no vertical gap.
- [ ] Switching the same format-2 tree between `none`, `dynamic`, and `configured` leaves every prerequisite, RP/material cost, availability state, and research result unchanged.
- [ ] A representative third-party pack produces the same automatic positions
  across two restarts and across different resource discovery orders; no
  weapon disappears when the active candidate count approaches the practical
  pack maximum.
- [ ] Switching `connected` back to `independent` and reloading removes every
  generated prerequisite without changing learned/discovered state; removing
  the custom profile entirely produces the same compatibility-first behavior.
- [ ] Existing learned blueprints remain learned after enabling or rearranging Tech Tree presentation data.

## Reloads, content packs, and networking

- [ ] `/reload` while the bench is open replaces the tree and preview atomically.
- [ ] A stale selected node returns safely to Browse after its rule or content is removed.
- [ ] Removing a TaCZ content pack leaves no broken connector or crash; restoring it makes persisted unlocks usable again.
- [ ] An unmatched add-on gun receives a named/icon preview node and follows the active automatic-placement/review policy without first finding its physical blueprint.
- [ ] Under the packaged profile, unmatched ammo and attachments retain their authored 4 RP Preview fallback rules but remain non-researchable and unpublished; enabling their domains makes the fallbacks selectable without fabricating prerequisites.
- [ ] Large generated add-on weapon families wrap across Tech Tree rows instead of one ultra-wide strip, and repeated reloads produce the same placement.
- [ ] Changing dormant legacy group metadata during `/reload` does not change the visible Tech Tree or reintroduce a hidden view selector.
- [ ] Dedicated server rejects a client with any protocol other than `36`.
- [ ] Reconnect, dimension change, and respawn do not combine old and new tree chunks.

## Bench model and blocks

- [ ] Item, first-person, third-person, and inventory icon face forward.
- [ ] North, east, south, and west placements face the player correctly.
- [ ] Both halves open the same menu and cannot be separated by pistons.
- [ ] Breaking either half removes the complete bench and produces exactly one drop.

## Release screenshots

Capture clean, HUD-appropriate PNGs at native resolution:

1. Fullscreen Tech Tree with the Research Trees domain sidebar, one connected prerequisite tree, a selected node, visible arrowheads, and several available and locked nodes.
2. Selected-node contextual tooltip showing prerequisites, unlocks, RP cost, inventory material counts, and readiness.
3. Fullscreen overlay with the world visible behind the tree and the floating Research action enabled.
4. Blueprint Analyzer screen showing a physical TaCZ item, its exact reverse-engineering cost, and the extract-only blueprint output.
5. Research Bench model placed in a representative TaCZ workshop.
6. Blueprint Recycler model placed beside the Research Bench with its paper
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
