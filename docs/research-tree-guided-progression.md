# Research Tree Guided Progression

The first guided-progression slice answers the player's immediate question:
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

Silhouette, Name, Preview-only, redacted, learned, locked, unpublished, and
otherwise non-navigable nodes cannot become recommendations. Reloads, research
point updates, projection changes, and learned-state updates recompute the
answer from the current immutable publication rather than retaining a stale ID.

## Follow-up boundary

Future guided progression can build contextual milestone messages and optional
first-use prompts on this focus-only contract. Those additions should consume
the recommendation result rather than duplicating ranking logic, and they must
preserve the same server-authoritative transaction boundary.
