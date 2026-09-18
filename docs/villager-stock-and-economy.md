# Villager stock and economy

The bundled market offers selected equipment and research supplies alongside
exploration and crafting. These values were checked against TaCZ 1.1.8-hotfix
with the bundled research profile. Other packs can change recipes, tiers,
research costs, source availability, and magazine requirements.

## Equipment pools

| Level | Curated gun pool | Base price | Lowest primary price | Uses per stock |
| --- | --- | --- | --- | --- |
| Apprentice | M1911, CZ75, P320, M9A4, DB Long | 24 currency + 4 Iron | 18 currency + 4 Iron | 2 |
| Expert | M4A1, AK47, M16A4, M16A1, G36K, QBZ-95, SKS Tactical | 48 currency + 8 Iron | 36 currency + 8 Iron | 1 |
| Master | HK416D, AUG, SCAR-L | 64 currency + 12 Iron | 48 currency + 12 Iron | 1 |

The market still checks effective research/crafting tiers. Apprentice guns must
be Tier 1; Expert and Master guns must be Tier 2. Master stock uses an already
stocked ammo type and a different selected gun. If no compatible choice exists,
the authored ammo fallback can replace the new gun offer. Installed addon guns
are not automatically added or priced. The custom-pool example requires an
explicit candidate list, ammo pool, and reviewed price.

The original recipe spread informed the pool split. For example, M1911 needs
16 Iron and 5 logs; CZ75 needs 17 Iron; DB Long needs 14 Iron and 10 logs. M4A1
needs 38 Iron, 6 Lapis and 8 logs, while HK416D needs 64 Iron, 16 Gold and 8
Quartz. The bundled research costs also differ: M1911 costs 4 Scrap, 2 Paper and
1 Iron for its own node; DB Long costs 6 Scrap, 3 Paper and 2 Iron; HK416D
costs 8 Scrap, 4 Paper and 3 Iron. Prerequisite routes add their own costs.
Buying is an alternate acquisition path, not a replacement recipe valuation. Factory attachments stay attached to purchased guns. Crafting remains
useful for making repeat copies without another currency purchase or villager
stock wait, after learning the recipe and meeting its Workbench requirements.

## Bundled trade table

Currency means the active physical research item (normally Scrap), the fixed
villager currency when configured, or Emeralds in banked RP mode. Primary-input
discounts stop at 75% of the authored amount, rounded up. Secondary Iron costs
are not discounted. Native demand can raise prices.

| Profession / level | Slot | Output | Primary input | Secondary | Uses |
| --- | --- | --- | --- | --- | --- |
| Ballistics Researcher / 3 | bulk notes | 4 research data | 14 currency | — | 2 |
| Ballistics Researcher / 4 | bulk reports | 2 research data | 22 currency | — | 2 |
| Ballistics Researcher / 2 | buy lapis | 1 currency | 12 lapis lazuli | — | 4 |
| Ballistics Researcher / 1 | buy paper | 1 currency | 24 paper | — | 4 |
| Ballistics Researcher / 5 | dossier | 1 research data | 24 currency | — | 1 |
| Ballistics Researcher / 2 | fragments 2 | 2 analysis fragments | 8 currency | — | 3 |
| Ballistics Researcher / 4 | fragments 4 | 2 analysis fragments | 16 currency | — | 2 |
| Ballistics Researcher / 5 | fragments 5 | 5 analysis fragments | 36 currency | — | 1 |
| Ballistics Researcher / 1 | note | 1 research data | 4 currency | — | 4 |
| Ballistics Researcher / 3 | report | 1 research data | 12 currency | — | 3 |
| Gunsmith / 3 | basic attachment | 1 attachment | 12 currency | 2 iron ingot | 3 |
| Gunsmith / 3 | bulk starter ammo | 32 ammo | 7 currency | — | 8 |
| Gunsmith / 2 | buy copper | 1 currency | 12 copper ingot | — | 4 |
| Gunsmith / 1 | buy iron | 1 currency | 8 iron ingot | — | 4 |
| Gunsmith / 4 | expert ammo | 16 ammo | 8 currency | — | 8 |
| Gunsmith / 4 | expert gun | 1 gun | 48 currency | 8 iron ingot | 1 |
| Gunsmith / 5 | premium gun | 1 gun | 64 currency | 12 iron ingot | 1 |
| Gunsmith / 5 | specialist attachment | 1 attachment | 24 currency | 4 iron ingot | 2 |
| Gunsmith / 1 | starter ammo | 16 ammo | 4 currency | — | 12 |
| Gunsmith / 2 | starter gun | 1 gun | 24 currency | 4 iron ingot | 2 |

A required basic empty magazine adds a same-level supply offer at 6 currency
plus 2 Iron, with 4 uses; its discount floor is 5 currency plus 2 Iron. Integral
magazine guns such as DB Long do not require a detachable-magazine purchase.

## Early progression and research value

In scripted native purchases, a new Gunsmith reached Apprentice after four
Iron exchanges and two ammo purchases (6 trades), without restocking. With
material purchases disabled, ten ammo purchases reached the same level. Both
paths supplied ammo before the first gun. When magazines were required, the
matching basic magazine was available alongside the gun.

For the default path, those first six trades cost 32 Iron and a net 4 Scrap
before the gun purchase; maximum discounts reduce that to 24 Iron and a net
2 Scrap. The first gun then costs 24 Scrap + 4 Iron (18 + 4 at the floor),
plus a magazine if required. Strict stock needs 40 Scrap of Novice ammo purchases
before the gun, or 30 at the discount floor. These totals exclude the job-block
recipe and reuse the ammo already purchased. Gathering travel, waiting for work
hours, and building costs were not timed.

Bundled study targets require 5 Tier 1 or 10 Tier 2 analysis fragments. Three
Apprentice bundles supply 6 Tier 1 fragments for 24 currency (18 at the floor),
enough for one reconstruction with progress left over. Two Master bundles
supply 10 Tier 2 fragments for 72 currency (54 at the floor), requiring a
restock because that offer has one use. Expert bundles can contribute toward
the same Tier 2 target. Prerequisites still govern learning the reconstructed
blueprint; the physical route replaces that target's ordinary research purchase.

New, partly learned, and fully learned customer scenarios all reached Master
through native purchases in default and strict economies. Knowledge does not
reroll shared merchandise. A fully learned player can still buy equipment, but
Data and study rewards can duplicate known targets. No guarantee of fresh
research is made for those customers.

## Trading halls and strict stock

| Mature traders | Default income per stocked set | With material purchases disabled |
| --- | --- | --- |
| 1 Gunsmith | 8 currency | 0 |
| 10 Gunsmiths | 80 currency | 0 |
| 1 Researcher | 8 currency | 0 |
| 10 Researchers | 80 currency | 0 |

One of each profession pays 16 currency for 32 Iron, 48 Copper, 96 Paper, and
48 Lapis per stocked set. At maximum discounts, the input totals fall to
24 Iron, 36 Copper, 72 Paper, and 36 Lapis; payout stays 16. Ten of each
multiply every total by ten. Restocking makes this renewable, so modest per-
villager stock is not a global farm limit. Use the strict example to remove
these four bundled purchase rows, or disable all owned material purchases in
server settings. Normal work-site/restock rules still apply.

## Validation scope

Native accelerated scenarios covered all five career levels for three customer
knowledge states, ordinary and maximum discounts, and default/strict settings.
The same checks ran with TaCZ alone, TaCZ Magazines 0.2.0-Beta, and a synthetic
120-gun extension (292 catalog entries total). Inputs were supplied and
restocking/level-up waits were accelerated; these are material and transaction
measurements, not survival-hour estimates or third-party pack balance approval.

Custom packs should export their own resolved table and compare actual recipes
and research costs. Recheck after changing the global catalog because automatic
tier placement can change. New selections exclude invalid candidates; established
stock follows the suspension rules in [villager trading](villager-trading.md).
