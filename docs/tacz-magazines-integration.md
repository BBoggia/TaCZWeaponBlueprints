# TaCZ Magazines integration

On the Minecraft 1.21.1 NeoForge development branch, no compatible TaCZ
Magazines dependency is configured or validated. The adapter remains optional
and rejects unrecognized versions. The existing stored-magazine protection
still prevents an Analyzer from consuming a gun with a stored magazine. The
integration contract below describes the preserved Forge release.

The optional integration targets **TaCZ Magazines 0.2.0-Beta** on Minecraft
1.20.1. With that version installed, empty magazines are available in the
Magazines tab at all three crafting Workbench tiers and TaCZ's original ammo
Workbench by default. Crafting uses the add-on's material costs. Servers can
optionally link access to learned weapons and require higher Workbench tiers.
There are no separate magazine blueprints. Existing gun, ammo, and attachment progression
requirements still apply. Before analyzing a gun or recovering its value at a
Blueprint Analyzer, eject any inserted magazine, including an empty one.

## Access settings

Server Settings includes a **TaCZ Magazines** section. Existing configurations
receive the defaults below without changing saved gun knowledge.

| Control | TOML field | Default |
| --- | --- | --- |
| Magazine Access | `magazineProgressionMode` | `COMPATIBILITY` (Materials Only) |
| Keep Basic Magazines Available | `magazineKeepBasicCrafting` | `true` |
| Basic Magazine Workbench Tier | `magazineBasicWorkbenchTier` | `TIER_1` |
| Extended Magazine Workbench Tier | `magazineExtendedWorkbenchTier` | `TIER_1` |

**Materials Only** permits empty-magazine crafting without learned weapons.
**Linked to Weapons** (`LINKED_WEAPON`) requires any compatible gun in the
current blueprint catalog to be learned or progression-exempt. A blacklisted
gun does not qualify. Retained knowledge for a removed gun cannot grant access
until that gun returns to the catalog. Shared families use the add-on's actual
compatible-gun set; knowing another gun with the same caliber is insufficient.

Keep Basic Magazines Available lets players supply found or traded guns without
learning them. Turn it off to apply weapon-linked access to basic magazines as
well. Its saved value is retained when Materials Only is selected. There is no
separate magazine knowledge record to migrate or reset: access follows current
gun knowledge, progression exemptions, and family definitions.

Both modes apply the chosen minimum tiers. Basic means extension level zero;
all three extended variants use the Extended Magazine Workbench Tier. Higher
Workbench tiers satisfy lower requirements. TaCZ's original ammo Workbench
counts as Tier 1 unless `externalWorkstationTiers` explicitly maps
`tacz:ammo_workbench` to another tier. The generic unknown-workstation fallback
does not raise its magazine tier. The existing Creative-mode tier bypass applies
to these minimums, but does not grant weapon knowledge.

These settings apply while `enableBlueprints` is enabled. Disabling blueprint
progression restores native unrestricted crafting access, including magazines.
Normal recipe costs still apply. Server setting changes and knowledge updates
refresh open Workbench access, and the server rechecks access when crafting.
These controls do not change the add-on's magazine fitting, loading, ammunition,
or extended-magazine attachment requirements.

## Optional dependency boundary

No TaCZ Magazines classes or assets are bundled, and the add-on is not required
to start the game. Its public methods are resolved only when that exact version
is installed. Other versions report `UNSUPPORTED_VERSION`; a missing or changed
API reports `UNAVAILABLE` without loading optional client classes directly.

`MagazineCatalog.INSTANCE.publication()` exposes the current revision and
capture. Captures distinguish an absent add-on, waiting for generated recipes,
an unsupported version, an unavailable integration, and completed discovery.
Recipe consumers must require `READY` before using the data. A ready catalog can be
empty when no enabled magazine recipes exist.

## Discovery and identity

The adapter captures the resolved upstream magazine families after recipe
generation. Its `OnDatapackSyncEvent` listener uses `LOWEST` priority, following
the supported add-on's `NORMAL` handler on both login and reload. Publications
replace the previous maps and are cleared between server worlds. Failed
captures publish an unavailable, empty result so stale recipes cannot remain
eligible from an earlier publication.

Each descriptor contains:

- the upstream family ID and a `taczmagazines:family/<family>` identity;
- the resolved ammo ID, capacity, extension level, and compatible gun IDs;
- the current generated recipe ID and a defensive copy of its empty output;
- ingredient JSON and counts from that recipe, retaining item/tag ingredients.

The numeric ordering prefix in an upstream recipe ID is never part of the
family identity. A reload can replace recipe mappings without changing the
family key. These keys are stable against recipe reordering, not arbitrary
upstream family renames or gun-pack/config changes. Persistent progression
using them will need an explicit migration policy for such changes.

Only generated magazine recipes with the expected tab, registered magazine
item, and a known enabled family are accepted. The adapter reads upstream
family relationships, including isolated guns and overrides; it does not infer
compatibility from matching ammo alone. Loaded outputs, inconsistent capacity,
invalid data, and multiple recipes claiming the same family are rejected.
Discovery has limits of 8,192 families/recipes and 65,536 compatibility links.

## Recipe overrides and ownership

The add-on remains responsible for generating recipes and applying
`data/<namespace>/taczmagazines/magazine_recipes/*.json` overrides. The adapter
does not regenerate recipes or write the recipe manager. Disabled or removed
recipes disappear from the next publication. Custom ingredient costs are read
from the generated recipe rather than replaced with assumed iron costs.

The captured ingredient JSON is descriptive data. Actual crafting resolves the
current server recipe, verifies its output, tab, and costs against the captured
descriptor, and retains TaCZ's material handling. An old recipe ID cannot
authorize a replaced, removed, or changed recipe. Magazine permission is added
to the same server access snapshot used by ordinary Workbench recipes, according
to the current access settings. The physical open Workbench must
still be valid.

The client decorates the existing tab list only at the four supported
workstation IDs. It preserves authored tabs, avoids duplicates, and does not
mutate shared gun-pack block data. The original ammo Workbench retains the
add-on's held-gun magazine filter. Empty tabs disappear through TaCZ's normal
recipe classification. Installing the add-on does not make magazines available
at arbitrary other workstation IDs.

TaCZ also checks tab membership during server recipe lookup. Validated magazine
recipes participate in that check while the native recipe filter and our
physical Workbench authorization remain in force. Client access filtering runs
after all native and add-on classification callbacks, before selection and
button creation. Recipe groups without tab metadata are removed in every access
mode, including the temporary waiting state during reload.
The pack-filter widget receives a separate namespace scaffold so its Magazines
checkbox exists even when the initial held-gun view is empty. This scaffold is
used only to construct namespace choices; it never supplies a craftable recipe
or changes the player's later filter selections.

After reload, open Workbenches receive refreshed access after the native recipe
synchronization. If upstream login generation changes the shared recipe set,
existing clients also receive the new recipes before their access refresh.
This includes recovery from unavailable discovery during a later login.
Recipe updates invalidate the screen's selected output and queue a rebuild.

The existing Workbench access protocol holds at most 4,096 recipe IDs. With
blueprint progression enabled, magazine crafting is disabled with a server log warning if the complete blueprint catalog
plus discovered magazines exceeds that limit; ordinary blueprint crafting
remains available. Reserving space for the full blueprint catalog avoids
evicting magazine recipes as players learn more blueprints.

When `enableBlueprints=false`, the access snapshot grants the native recipe view
and crafting bypasses the progression adapter and its collection limit. The
physical workstation is still authenticated. TaCZ still resolves the live recipe,
applies its native filters, and consumes the native ingredient cost; an
unrestricted progression grant does not manufacture a recipe or waive payment.
An unavailable adapter cannot add tab membership, but it no longer vetoes
recipes already supported by the native workstation.

## Blueprint Analyzer protection

An inserted magazine blocks blueprint extraction, tiered weapon analysis, and
found-weapon currency recovery. Both the preview and the final server action
report that the player must eject the magazine first. Rejected actions leave
the gun, magazine data, ammunition, materials, currency, and progression intact
and do not select an analysis outcome. After ejection, normal analysis and
loose-round/chamber recovery apply.

The read-only guard checks `TaCZMag_StoredMagazine` on a TaCZ `IGun`. Upstream
uses the presence of that key to identify an inserted magazine. Empty, loaded,
malformed, and unresolved stored items all remain protected. The check neither
deserializes the magazine nor guesses its current ammunition count. It also
works when recipes are disabled, discovery is unavailable, or the add-on has
been removed from a saved game. Ordinary guns without that marker and non-gun
items are unaffected. For a save retaining the marker without the add-on,
restore the add-on and eject the magazine before using the Analyzer.

The Analyzer does not automatically eject, return, or analyze magazines.
The new preview/action status requires matching protocol-69 clients and servers;
player save formats are unchanged.

## Scope

Full magazine blueprint learning is a separate catalog/domain feature. Magazines
are not classified as loose ammo or TaCZ extended-mag attachments by this adapter.

Upstream references: [published version](https://www.curseforge.com/minecraft/mc-mods/tacz-magazines/files/8536879)
and [reviewed source](https://github.com/Raiiiden/TaCZMagazines/tree/1acc1d87bd1e71f425bcd3d6ee87eb4c6b2413e8).
