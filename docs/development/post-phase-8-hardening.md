# Post-Phase-8 Hardening

Date: 2026-08-24

The post-certification adversarial review found no authority or duplication
exploit, but it exposed migration, payload, reload, presentation, and release
edge cases not covered by the original 43 tests.

Progression now uses blueprint output IDs as the durable identity. The legacy
`Recipes` list is still read and written for rollback compatibility. On login,
reload, respawn, and dimension synchronization, every current recipe alias is
resolved to its output blueprint and the selected canonical recipe is sent to
the client. This preserves existing aliases without making cheaper duplicate
recipes craftable.

Catalog and learned-recipe synchronization now use protocol 3. Payloads are
sorted, split below a 900,000-byte budget, accumulated by synchronization ID,
and published only when every chunk is present. Catalog publication validates
the same text and resource-ID limits used by the wire format.

Additional hardening includes:

- constructor and inherited-weight underflow rejection;
- a visible, rate-limited fallback for removed blueprint items;
- live refresh of an open TaCZ gun-smithing screen;
- component-based translated tooltip substitution;
- configuration-independent legacy data generation;
- minimum-dependency smoke runtimes without Packet Fixer by default;
- precise targeted-disabled versus global-disabled diagnostics;
- exact Minecraft 1.20.1 and Forge/FML 47 compatibility ranges;
- explicit JUnit platform launcher resolution for Gradle 9 migration;
- artifact checks for every declared runtime range; and
- a publication-readiness gate for the unresolved project license.

The regression suite contains 47 tests after this pass. Maximum supported
catalogs and learned-recipe sets are exercised at their longest permitted field
lengths to prove every produced chunk remains under its byte budget.
