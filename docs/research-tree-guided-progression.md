# Research Tree Guided Progression

Guided progression answers the player's immediate question:
“What can I work on next?” The Research Bench now exposes a small `Next` action
in compact mode and an arrow action in the fullscreen rail.

## Recommendation contract

The recommendation engine is pure, deterministic, and presentation-only. It
considers only nodes that the server has published as exact-policy,
identity-revealing, `Available`, and navigable. It ranks those candidates by:

1. affordable with the player's synchronized RP balance;
2. present in the currently viewed tree or Tech Tree domain;
3. number of disclosed direct paths leaving the node;
4. lower RP cost, then fewer ingredient types;
5. stable source order and blueprint ID tie-breakers.

The button's tooltip and narration explain whether the choice opens paths, fits
the current RP balance, or is the lowest-cost available fallback. If no valid
candidate exists, the disabled action explains that there is currently no
available recommendation.

## Authority and disclosure boundary

Activating `Next` only navigates to and focuses the recommended public node. It
does not change the authoritative selected blueprint, request a preview,
research a blueprint, consume RP or materials, or send a transaction packet.
The existing explicit node selection and server-confirmed Research action remain
the only route into a transaction.

Tracked routes wait for the same server-authoritative eligibility result used by
the Research action. A route blocked by its current Research Bench tier or a
Progression Gate does not advertise an inaccessible next step. The Affordable
Now filter likewise classifies only a complete server-evaluated route as
affordable; RP and materials alone cannot make a tier- or gate-blocked target
appear ready.

Silhouette, Name, Preview-only, redacted, learned, locked, unpublished, and
otherwise non-navigable nodes cannot become recommendations. Reloads, research
point updates, projection changes, and learned-state updates recompute the
answer from the current immutable publication rather than retaining a stale ID.

## Inspection, tracking, and purchase review

Clicking a disclosed blueprint opens its compact inspector and requests the
matching server preview. Details expands costs, prerequisite groups, crafting
requirements, and fragment information. Currency and materials describe the
whole quoted purchase. Disabled cost channels do not appear as shortages;
Research Bench access and Progression Gates remain separate blockers.

Following a disclosed requirement inspects that blueprint and adds a Back entry.
Back restores its previous domain, rendered camera, details state, and scroll.
Ordinary selection starts a new inspection chain. These actions do not replace
the tracked goal; its cyan marker and route remain visible independently.

Review research path displays the server-selected prerequisite route for the
current purchase, including learned steps. It remains useful when the player
cannot yet afford the purchase. The review is optional and does not change the
route, require another purchase confirmation, or replace the tracked goal.
The action reads Research blueprint for one unlock and Research N blueprints for
a whole-path purchase; the count includes the target and unlearned prerequisites.

A review is bound to the open menu, publication, target, route fingerprint, and
current quote. Selection, resource allocation, capacity, or publication changes
invalidate obsolete results. Delayed responses cannot overwrite another target.
The Research action always uses the current server-authoritative preview,
whether or not the review is open. Review highlights disappear when their quote
is no longer current. Timeouts expose a bounded retry rather than repeatedly
requesting work.

## Follow-up boundary

Future guided progression can build contextual milestone messages and optional
first-use prompts on this focus-only contract. Those additions should consume
the recommendation result rather than duplicating ranking logic, and they must
preserve the same server-authoritative transaction boundary.
