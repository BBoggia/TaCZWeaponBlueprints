# Villager trading

Craft a Gunsmith Station or Ballistics Desk and place it where an unemployed
adult villager can reach it during work hours. Trade with the villager to level
its profession; keeping its job site accessible lets it restock. These job
sites work in existing worlds and have no crafting or research screen.

The **Gunsmith** supplies physical equipment. The **Ballistics Researcher**
supplies Research Data and targeted analysis fragments for the Blueprint
Analyzer. Purchasing equipment does not learn its blueprint. Protected research
rewards require prerequisites; subsequent crafting still follows Workbench
requirements. A fully learned player can still buy shared stock, but already
learned study targets do not provide new knowledge.

## Getting started

- Gunsmith Station: 3 Iron Ingots across the top, a Copper Ingot between two
  planks in the middle, and a plank in each bottom corner (4 planks total).
- Ballistics Desk: the same layout with 3 Paper across the top and a glass pane
  in the middle. Both recipes appear in the recipe book after collecting the
  corresponding top-row material.
- Equipment arrives unloaded. Buy its matching ammo and, when required by the
  supported magazine addon, an empty basic magazine from the same villager.
- Hover a trade arrow for restrictions or an unavailable reason. Sold-out
  stock needs a normal restock; missing content or disabled trades need the
  server's content/settings to become usable again.
- Stock is per villager, shared between customers. Restocking keeps its chosen
  guns and study targets. Whole blueprints and Tier 3 gun sales are off by default.

The [stock and economy reference](villager-stock-and-economy.md) explains the
bundled candidates, prices, and tradeoffs. Pack-author examples cover
[a custom gun pool](../examples/villager-trading/custom-gun-pack/README.md),
[another physical currency](../examples/villager-trading/physical-currency/README.md),
and [a strict economy](../examples/villager-trading/strict/README.md).

## Registered content

| Profession and POI ID | Job block and item ID | Display name |
| --- | --- | --- |
| `taczweaponblueprints:gunsmith` | `taczweaponblueprints:gunsmith_station` | Gunsmith / Gunsmith Station |
| `taczweaponblueprints:ballistics_researcher` | `taczweaponblueprints:ballistics_desk` | Ballistics Researcher / Ballistics Desk |

Both job blocks occupy one block, have four horizontal facing states, and have
crafting recipes and block drops. Every facing belongs to its profession's POI.
The POIs are included in `minecraft:acquirable_job_site`. Registration uses
Forge's normal registry handling; there is no second manual POI state-map update.
The vise bench and measuring desk have distinct models and original pixel
textures. Both professions have distinct outfits and headgear, retaining biome
hems and boots, exposed faces, and vanilla level badges, including on zombie
villagers. Profession headgear replaces biome hats. Existing weaponsmith and
librarian work sounds accompany the corresponding professions.

## Owned offers

`VillagerTradeOffers.create` constructs a native `MerchantOffer` with copied
inputs and output. Ownership and pricing terms are stored under the offer-level
`taczweaponblueprints:trade` NBT key. Stock factories additionally attach their
chosen definition, target, pairing dependencies, and original price amounts
under `taczweaponblueprints:stock_offer`. These markers are separate from
merchandise NBT, including the strict schema of protected analysis fragments.

The current terms format is version 1: a bounded canonical namespaced slot ID,
minimum primary input count, availability flag, and nonnegative revision. Terms
survive native offer/entity save and load, zombification, and curing. Invalid
saved owned terms suspend the offer instead of removing its ownership marker.

Vanilla retains responsibility for payment, item movement, uses, XP, work,
restocking, and profession changes. An untraded novice can change professions
normally; creating or viewing offers does not lock it. An actual profession
change clears native offers and their attached terms.

## Prices and transfer checks

The offer factory defaults to a 75% minimum primary charge and also accepts an
explicit validated floor. A
secondary material charge remains fully payable. Identical item/tag inputs are
combined when their total fits one stack. A 24-Iron primary charge plus 4 Iron
normalizes to 28 Iron with a minimum of 22. A 64+12 combination is rejected and
requires an alternate price; it is never truncated.

The floor is computed from live demand and the customer's original adjustment
whenever the price is read. It does not accumulate corrections across customers.
For marked offers, `getSpecialPriceDiff()` exposes the effective adjustment used
by vanilla offer synchronization; the original adjustment remains in native
storage. Integrations should use native adjustment/reset operations rather than
assuming that this accessor returns the unmodified stored adjustment.

An open owned merchant menu captures offer identity, terms, input costs, and
output. Server tick and input checks close stale quotes using native container
cleanup, which returns inputs once. Validation happens before output movement
for pickup, quick-move, number-key transfers, and dropping. Owned quick-move also
requires inventory capacity for the entire output, preventing partial delivery
at the full price. Normal drag handling remains vanilla.

Hooks leave unmarked offer pricing unchanged. Session checks apply to the two
registered professions when their menu includes marked offers. The current
session limit is 256 offers. Callers must modify offers on the server thread and
publish changed terms or replacement offers when their validity changes.

## Availability in the native merchant screen

Hover a trade's arrow to see why an owned offer is unavailable, whether its
research reward requires prerequisites, or the family, ammunition and capacity
of an empty magazine. Native prices, sold-out marks, levels and XP remain in
place. These hints contain only currently issued offers and grant no permission
to trade. Status from another menu, an older revision or a closed connection is
ignored. If a price or relevant dependency changes, the menu closes through
Minecraft's normal cleanup and returns its input stacks once.

## Datapack trade definitions

Definitions use format version 1 and live at
`data/<namespace>/taczweaponblueprints/villager_trades/<name>.json`.
The definition ID comes from `<namespace>:<name>`. To replace a bundled row,
use its exact resource path in a higher-priority datapack. Different files
cannot own the same slot, including disabled files. There is no implicit
merge or last-file-wins rule for slot ownership.

Example gun row:

```json
{
  "version": 1,
  "profession": "taczweaponblueprints:gunsmith",
  "level": 2,
  "slot": "example:gunsmith/starter",
  "role": "core",
  "enabled": true,
  "output": {
    "kind": "gun",
    "count": 1,
    "tier": 1,
    "selection": "starter",
    "candidates": [
      {"id": "tacz:m1911", "weight": 1},
      {"id": "example_pack:sidearm", "weight": 2}
    ]
  },
  "price": {"item": "currency", "count": 24},
  "secondary": {"item": "minecraft:iron_ingot", "count": 4},
  "uses": 2,
  "xp": 5,
  "demand_multiplier": 0.05,
  "minimum_basis_points": 7500,
  "required_mods": [],
  "fallbacks": []
}
```

`profession` is either `taczweaponblueprints:gunsmith` or
`taczweaponblueprints:ballistics_researcher`. `level` is 1–5. Slots are stable
namespaced IDs independent of a definition's resource path. Keep a slot's
product and identity stable across pack revisions.

`role` defaults to `core`. `support` describes conditional magazine supply;
`fallback` defines an alternative that never owns an independent slot. A
fallback reference is a definition ID and must point to a fallback at the same
profession and level. Fallbacks are tried in order and carry their own prices,
counts, stock, and XP. Cycles, missing references, and conflicting slots reject
the complete reload. Disabling a definition or profession does not activate
its fallbacks. A disabled intermediate fallback also stops traversal down that
branch; a shared descendant can still be reached through another enabled
branch. Disabling an output category can select an allowed alternative.

Output kinds:

| Kind | Candidate IDs |
| --- | --- |
| `currency` | None; receives the effective merchant currency |
| `research_data` | Exact item IDs with an active Research Data analysis policy |
| `gun`, `ammo`, `attachment` | Canonical recipe-backed TaCZ content IDs |
| `analysis_fragments`, `blueprint` | Canonical gun targets for protected, prerequisite-respecting knowledge stock |
| `magazine` | Stable magazine family IDs from the adapter; an empty list requests enabled families compatible with the authored gun pools |

Candidates are explicit public pools with positive finite weights. A missing
optional item or gun pack excludes a candidate; it does not make the JSON
invalid. `required_mods` is an optional list of loader mod IDs. TaCZ gun packs
are content packs and do not necessarily have a loader mod ID.

`tier` defaults to 0 (no exact tier filter). Equipment uses the greater of its
current research and crafting tiers; gun equipment is also limited by the
server's maximum gun tier. Studies use the existing Analyzer classification,
falling back to the shared reconstruction research tier. Data pools retain
their own configured target tiers. All pools exclude missing recipes, blocked
content, and globally disabled crafting. Knowledge pools also require usable
blueprint progression. Data sales require a usable analysis pool with protected
prerequisite-respecting rewards; disabled/legacy redemption and unprotected
reward definitions do not qualify for these sales.

`selection` defaults to `none`. `starter` and `expert` identify planned gun/ammo
pairs; `stocked` requires compatibility with the villager's chosen equipment;
`study_t1` and `study_t2` identify persistent study targets. These are planning
constraints, not customer-specific knowledge checks. The current resolver
reports global candidates before stock pairing. The stock planner applies
pairing, supply requirements, and ordered fallbacks before native factories
receive a saved choice. A ready global pool alone is not authorization to
construct an arbitrary trade.

## Server settings

Settings are in the existing `blueprint.toml` and Fzzy Config server controls.
They use the same synchronized immutable snapshot initialization for new,
migrated, and already-current-version files. Adding the settings does not
require a configuration-version migration.

| Setting | Default | Effect |
| --- | --- | --- |
| `enableGunsmith`, `enableBallisticsResearcher` | `true` | Enable that profession's resolved table |
| `villagerCurrencyMode` | `FOLLOW_RESEARCH_ITEM` | Follow physical research currency; banked RP uses Emeralds |
| `villagerFixedCurrency` | `taczweaponblueprints:scrap` | Item used by `FIXED_ITEM` mode |
| `villagerPricePercent` | 100 | Scale primary charges, rounded upward |
| `villagerStockPercent` | 100 | Scale capacity, rounded upward |
| `villagerMinimumPricePercent` | 75 | Minimum primary charge after discounts |
| `villagerMaterialPurchases` | `true` | Permit material-for-currency offers |
| `villagerMaximumGunTier` | 2 | Maximum equipment gun tier |
| `villagerGunSales`, `villagerAmmoSales`, `villagerAttachmentSales` | `true` | Equipment category controls |
| `villagerDataSales`, `villagerFragmentSales` | `true` | Knowledge category controls |
| `villagerBlueprintSales` | `false` | Permit explicitly authored whole-blueprint rows |
| `villagerMagazineSales` | `true` | Permit magazine rows when the addon is available |
| `villagerExtendedMagazines` | `false` | Include extended families in enabled magazine rows |

Price/stock percentages range from 1 to 1,000. The minimum percentage ranges
from 0 to 100, but the charged amount always remains at least one item. A
row's `minimum_basis_points` can strengthen the server minimum, not weaken it.
Secondary material counts and output quantities do not scale with the price
modifier. Unknown/air currencies and prices that exceed an item's actual stack
limit make the row unavailable. Material purchases whose input is the currency
are suppressed. No banked balance is debited or converted.

Configuration and datapack inspection does not require a restart. Profession
and POI registrations remain stable. Existing owned stock follows current
settings when the server accesses offers, including before a trade. Disabling a
profession suspends its marked offers and blocks new native AI assignments.
Existing saved/traded professions and registered job sites are retained.
Professions with no viable stock/XP path also cannot be newly assigned.
Rejected assignments release their workstation reservation so it can be
claimed again when the profession becomes available.

## Publication and inspection

Use operator permission level 2:

- `/gg villager_trades status` reports definition/catalog revisions, currency,
  ready pool counts, and whether each profession has an XP route from Novice.
- `/gg villager_trades inspect <slot>` reports one resolved row, including its
  candidates, exclusions, fallback, normalized price, uses, and XP.
- `/gg villager_trades export` writes `<world>/reports/villager-trades.json`.
  It includes definition, catalog, research, policy, and magazine publication
  identities and effective settings. Repeated exports replace that report.

Reload with `/reload`. Parsing/structural validation is independent of loaded
content. The committed resource snapshots update only after the complete native
reload succeeds. Late synchronization-handler failures restore the previous
committed definitions before trading resumes. Overlapping attempts remain
blocked until they have all finished. Invalid files retain the whole last valid definition snapshot and
record the failure; no successful subset publishes. Inspection always resolves
that snapshot against **current** catalogs and settings. A catalog still
loading returns `PENDING` instead of choosing an empty-pool fallback.

`Resolution.retains(slot, definition, target)` checks the globally preferred
row. Saved-stock validation uses the ordered direct alternatives instead, so
an established fallback can remain available when its primary becomes usable
again. The saved definition, target, output count, category, selection, level,
and role must still match a currently usable alternative. Neither a newly
useful fallback nor a newly preferred primary replaces existing merchandise.

Every nonterminal level needs at least one usable positive-XP core offer at or
below it. Restocking lets an earlier trade continue advancing the villager.
An Expert-only trade cannot make a fresh Novice viable. The diagnostic marks
professions with no route unavailable for acquisition by the stock planner;
it does not erase saved villagers or their XP.

## Native stock and persistence

Each profession adds two core factories at each of the five native levels.
The bundled tables produce ten core offers per career when their content and
settings are usable. A datapack with more than two core slots at a level is
reported as unreachable instead of silently dropping some of its slots.

A villager chooses a bounded whole-career plan before either factory runs.
The plan uses sorted, weighted candidate identities and a saved seed. It pairs
starter and Expert guns with the correct ammo, limits premium gun choices to
already planned ammo, and checks attachment compatibility. The Researcher's
repeated study rows share their tier's selected target, including when that
target comes from a fallback. Compatible alternatives are considered in
definition order, with slot IDs breaking ties between conflicting preferences.
A non-study preference only excludes its study fallback when that preference
can actually be stocked with the chosen equipment and supplies. Customer knowledge does
not affect merchandise selection. Missing equipment uses the first compatible
explicit fallback; a pending required catalog waits instead of choosing one.

Gun outputs copy the real recipe output, preserving factory attachments while
clearing ammunition, chamber contents, dummy ammunition, and any inserted
magazine. Output item identity, count, and current recipe NBT are checked before
an established offer can remain available. Unexpected saved output data
suspends the offer.

With supported TaCZ Magazines installed, a gun requires its actual basic
magazine family. Sharing ammo does not imply sharing a magazine. Required
support offers append after vanilla selects a gun, at its unlock level, and a
family already stocked at an earlier level is reused. Automatic family discovery includes guns in enabled, reachable fallback
branches; orphan and disabled fallback branches do not supply candidates. An installed but
unsupported adapter, disabled family, or missing required recipe cannot be
treated as addon absence. Extended magazines never satisfy this basic supply
requirement.

`taczweaponblueprints:stock` stores format version 1, profession, selection seed,
chosen entries, excluded slots, requested native factory positions, and issued
slot identities. It is saved once beside the native `MerchantOffers` recipe
list so vanilla zombification and curing carry future choices too. Entity NBT
also retains an empty pending plan. Lists have strict bounds and malformed
stock suspends owned offers. Native offer serialization retains uses, demand,
customer adjustment, and XP; the mod does not keep a second stock-use counter.

Catalog/configuration changes revalidate saved merchandise and supply links.
Repricing preserves spent uses. A reduced capacity applies immediately; an
increased capacity waits for native restock. Only native restock resets uses.
An established target that becomes invalid suspends until the same definition
and target become valid again. It does not reroll. A pending or unreachable
plan that has issued no owned offers can retry on a later publication. A real
native profession change clears its previous plan.

Other mods can still modify these professions' factory lists. The mod logs
interference, preserves their offers and vanilla's actual selection, and
suspends owned guns whose required supplies were not selected or were removed.
Previously issued slots are not recreated when another integration deletes
them. Required magazine support appends only for actually selected owned guns.

After pairing and fallbacks, each nonterminal level must have a positive-XP,
restockable core offer at or below it. An unreachable plan issues no owned
stock and logs excluded slots once per profession/publication. The global
operator report remains a candidate-pool report; it does not claim that an
individual villager has every candidate or that another mod left its factories
unchanged.

## Validation limits

| Value | Limit |
| --- | --- |
| Definition documents | 256 |
| Independently owned slots per profession | 128 |
| Active/saved owned-offer budget used by merchant guards | 256 |
| Candidates per authored or resolved output | 64 |
| Aggregate authored / resolved candidate IDs | 4,096 |
| JSON characters per document / whole reload | 65,536 / 4,194,304 |
| JSON nesting | 16 |
| Fallback path including the root | 8 definitions |
| Required mods per definition | 16 |
| Namespaced ID length | 256 characters |
| Input/output counts | 1–64, further constrained by actual stack limits |
| Uses / XP | 1–1,024 / 0–1,000 |
| Candidate weights | Finite, greater than 0, at most 1,000,000 |
| Demand multiplier | Finite, 0–1 |

The 4,096 candidate budget matches the blueprint catalog budget. Slot and
owned-offer budgets stay within the existing 256-offer merchant guard and
availability-index budget, leaving space for dependent magazine support.
Counts use vanilla's 64-item upper bound and the registered item's smaller
limit where applicable. Duplicate keys, unknown fields, coercions, fractional
integers, unknown enum values, and oversized/deep documents are rejected.
Exceeding the aggregate resolved-candidate budget suspends the whole resolved
table rather than selecting rows by file order. Fallback resolution memoizes each definition so a shared fallback graph cannot
multiply work exponentially. Resolution is performed for explicit inspections
and shared stock publication refreshes. Villager interactions reuse that
snapshot and bounded compatibility/output caches; they do not rescan the global
catalog each tick.

## Purchased research items

New Gunsmith weapons carry `traded` origin, which survives transfers and saves.
They cannot use verified-found currency recovery. Existing player-owned weapons
are not restamped. Paid analysis follows the effective profile and weapon rule.
Both tiered analysis and legacy reverse engineering produce nonrecyclable,
prerequisite-bound rewards by default. An explicit `expert_allow_economy_loop`
override in the effective analysis attempt retains its authored permissions;
pack authors enabling it must review their complete economy.

`allowed_origins` now accepts `traded`. An omitted list includes every origin;
an explicitly authored old list keeps its previous meaning and must add
`traded` to permit purchased weapons. Disabled blueprint learning stays disabled.
Found and crafted provenance retain their existing meanings. Item provenance
still uses format 1 with an additional named origin; client/server protocol 69
is required for the new Analyzer preview value.

Researcher fragments use the existing protected analysis-fragment schema and
shared archive thresholds. Trading, holding, transferring, or archiving them
never grants knowledge automatically. Reconstructed blueprints use normal
learning, prerequisites, discovery, and crafting access checks.

Whole-blueprint rows require both an authored `blueprint` output and
`villagerBlueprintSales = true`. Their `traded` blueprint provenance prevents
recycling and requires normal prerequisites. The default table does not vend
whole blueprints. Tier 3 guns likewise need `villagerMaximumGunTier = 3` and
an authored tier-3 pool with compatible ammunition and any required magazines.
Increasing the setting does not turn existing tier-2 rows into tier-3 stock.

## Research Data and conversion limits

Data sales follow the effective Data behavior and policy. Analysis-mode sales
require a usable protected reward pool. Legacy currency exchange is excluded
unless that row's `research_data` output explicitly sets
`"allow_legacy_exchange": true`. This optional format-1 field defaults to false
and may only be enabled for Research Data outputs.

For an opted-in legacy row, redemption and payment must use the **same physical
item**. The minimum discounted primary charge must exceed the maximum authored
redemption reward for the entire output bundle. The bound includes independently
stacking award groups and ignores player cooldowns or exhaustion. A disabled,
missing, equal-value, or more profitable reward makes the row unavailable.
Banked RP and Emerald counts are different units and are never compared.
Changing award data or currency republishes eligibility. External integrations
that add rewards still need their own economy review.

Material-purchase payouts follow the current configured currency on subsequent
stock validation. This updates the denomination without replenishing uses or
rerolling equipment. Invalid denominations and self-currency purchases suspend
or exclude the affected offer. Existing open menus must reopen after changes.

## Strict economy

Set `villagerMaterialPurchases = false` to remove renewable material-to-currency
trades. Leave ammunition and Research Data sales enabled so both professions
retain a Novice XP path. Keep protected fragment rewards, whole-blueprint sales
off, a maximum gun tier of 2, and the default discount floor. A configuration
example is in [the strict economy example](../examples/villager-trading/strict/README.md).

Default stock is bounded **per villager per restock**, not per player or world.
Trading halls multiply renewable supply and material income. There is no global
scarcity guarantee. Disabling material purchases removes that direct income
source; authors must still review other mods' trades and conversion recipes.

## Reload and saved-world behavior

During a resource reload, output transfers wait until the core data and optional
magazine recipe handlers have completed. At the end of the server tick, only
open merchant sessions are checked; other villagers validate lazily on access.
Unchanged reloads and unchanged recipe regeneration on another player's login
preserve the menu. Changed relevant prices, outputs or supplies close stale
quotes. Inputs return through normal native container cleanup.

An aborted reload restores this mod's last committed definitions and retains
the last complete equipment catalog. Recovery uses saved snapshots, so later
file edits and partially applied data cannot replace the committed state. Trading resumes
only after all overlapping reload attempts finish and recovery settles. If
recovery itself fails, open merchant menus close to return inputs and trades
remain suspended until a successful reload. This does not repair failed
reload state inside other mods.

Changed configuration never rerolls established equipment or refills spent
stock. Raising stock capacity takes effect at native restock. Removing content
suspends affected slots while retaining the selected identities. Unknown saved
item payloads in owned offers are retained while their addon is absent, so
restoring it restores merchandise and uses after a subsequent load. This does
not restore items that another tool already deleted from the save.

Changing a datapack candidate pool does **not** authorize replacement of an
established product. Removed choices remain dormant until restored. There is no
automatic fallback reroll or product-reset command. An integration explicitly
replacing an owned offer must retain its native uses and maximum capacity,
update its saved slot binding/plan consistently, and invalidate the old quote;
creating a fresh unused offer would be a stock refill.

Status packets use protocol 69, carry a merchant container/session and monotonic
revision, and are limited to 256 issued offers. Item/price fingerprints prevent
stale hints from being displayed beside changed native offers. No future stock
or other player's blueprint knowledge is sent.

## Client presentation compatibility

The native MerchantScreen remains in use. Trade-arrow hints wrap to the screen,
and magazine hints use the item and ammo display names. JEI 15.12.2.50 and
EMI 1.1.24 were checked separately. EMI 1.1.24 requires JEI 15.20 or newer when
both are installed; follow the viewers' dependency requirements. Their combined
installation with a newer JEI version has not been tested here.

Default and Unicode fonts, a Unicode font resource-pack override, small screens,
and GUI scales 1–3 are included in presentation checks. These checks do not
establish compatibility with every custom font or replacement merchant screen.
