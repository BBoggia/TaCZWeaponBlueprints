# Journal and Research Phase 6: Journal Presentation

## Scope

Phase 6 turns the Phase 5 client snapshot into a usable Blueprint Journal. It
adds a rebindable in-game control, a read-only native Minecraft screen,
deterministic client-side browsing, progress summaries, and unavailable-content
history. The screen never sends a packet and never treats a preview as
authorization.

This phase does not add recycling transactions, the Research Bench, ingredient
consumption, direct learning, or administrator progression commands.

## Access and lifecycle

`Open Blueprint Journal` is registered in Minecraft's Controls menu under the
`TaCZ Weapon Blueprints` category and defaults to `J`. Its conflict context is
in-game, so typing `J` in another GUI cannot queue an unexpected Journal open.
The client consumes every click and opens the screen only when a player and
level exist and no other screen is open.

The screen is non-pausing and read-only. It follows the atomic
`ClientBlueprintJournal` publication while open, rebuilding its categories,
results, page bounds, and controls when the server sends a newer snapshot.
Logout continues to clear the publication before another connection can use it.

## Disclosure-safe presentation

The query and screen consume `BlueprintJournalEntry` rather than the client TaCZ
catalog. Consequently, they cannot recover fields that Phase 5 redacted:

- `SILHOUETTE` entries show only their synchronized anonymous ordinal;
- `NAME` entries resolve only the synchronized translation key;
- `PREVIEW` and `FULL` entries may show the synchronized blueprint icon source,
  category, ID, state flags, and bounded policy summary;
- unavailable history shows only the normalized ID and durable learned flag
  supplied by the server.

Search indexes only the translated disclosed name, disclosed blueprint ID, and
disclosed category. Anonymous entries are not passed through the name resolver.
Category choices are derived only from entries that include an `itemType`.
Filtering and sorting similarly operate only on synchronized view-model fields.

The Journal remains a presentation surface. It has no action packet, and the
future Research Bench must still recompute all eligibility and costs on the
server at commit time.

## Browsing model

Current entries support:

- bounded search with an 80-character client input limit;
- `ALL`, `LEARNED`, `DISCOVERED`, `RESEARCHABLE`, `RECYCLABLE`, and
  `UNREVEALED` status filters;
- dynamic category filtering;
- catalog, localized-name, category, and progression sorting;
- resolution-dependent pagination and mouse-wheel page navigation.

Every sort has the synchronized ordinal as its final tie-breaker. Requested
pages are clamped after search, filter, snapshot, or window-size changes, so an
empty or stale page cannot survive a result-set change.

Unavailable history has its own searchable, paginated view. Learned historical
entries sort before discovered-only entries, then by normalized ID. Removed
content never contributes to current completion.

## Screen behavior and accessibility

The header displays Research Points and visible learned completion. Each row is
a normal narrated Minecraft button, the search box has a narration label, and
all controls participate in keyboard focus navigation. Status, filter, sort,
category, visibility, and detail text use localization keys.

At normal widths, a detail pane sits beside the entry list. At narrow GUI
scales, selecting a row opens the same detail content as an in-screen compact
view with a Back control; Escape returns to the list before it closes the
Journal. Long names, resource IDs, and history explanations wrap inside a
scissored detail region rather than drawing outside the panel.

The detail pane shows only policy summaries that Phase 5 deliberately permits:
point cost, ingredient-type count, prerequisite count, current point
affordability, and recycling value. Exact ingredients, prerequisite identities,
rule identities, and selector internals remain absent.

## Verification

Phase 6 adds focused coverage for:

- search over disclosed fields and rejection of anonymous hidden-name matches;
- status and category filters;
- deterministic progression/name/category ordering;
- page clamping;
- disclosed-category derivation;
- unavailable-history search and ordering;
- invalid page-size rejection.

The release-artifact verifier now requires the Journal query, screen, and key
mapping classes in the packaged JAR and continues to parse every packaged JSON
resource. Full test, clean build, dedicated-server startup, and client startup
remain the phase verification gates.

The completed Phase 6 tree passes 102 automated tests. The dedicated server
applied research revision 1, rebuilt the normal 481-entry blueprint catalog,
and reached `Done`. The client registered the client-only event surfaces,
applied the existing gunsmith mixin, created its texture atlases, and reached
the render loop. The malformed third-party gun-pack resources and unauthenticated
Realms warning are unchanged from earlier phases; no exception originated from
the Journal query, key mapping, or screen.

## Deferred to later phases

- manual duplicate-recycling transactions;
- Research Bench block, menu, preview, and atomic server commit;
- exact research-ingredient presentation within an authorized bench menu;
- administrator progression inspection and reset commands.
