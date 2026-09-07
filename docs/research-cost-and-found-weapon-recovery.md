# Research cost and found-weapon recovery

Research costs use physical Scrap by default or banked Research Points (RP)
when explicitly selected. `researchCostMode` chooses Bench cost channels;
`foundWeaponRecoveryMode` independently controls positively identified loot guns.
Both are synchronized server policies. See [currency configuration](research-currency-configuration.md).

## Research cost modes

`researchCostMode` selects the effective cost channels used by Research Tree
transactions:

| Mode | Currency charged | Materials charged |
| --- | --- | --- |
| `POINTS_AND_ITEMS` | Yes | Yes |
| `POINTS_ONLY` | Yes | No |
| `ITEMS_ONLY` | No | Yes |

`POINTS_AND_ITEMS` is the default. The runtime derives a masked effective cost
from each profile/rule cost. It never mutates or replaces the datapack-authored
cost, so switching modes is reversible. The effective cost is shared by
single-node research, automatic shortest-path purchases, server affordability
checks, inventory planning, Research Bench previews, status output, and the
research catalog's economy report.

Creative cost bypass continues to waive every active channel. A policy with no
cost in its active channels is free; the Research Bench reports that state
instead of presenting a misleading zero-currency requirement.

## Physical weapon origin

New survival-crafted TaCZ gun-smithing outputs receive a versioned
`CRAFTED_SURVIVAL` marker with their recipe ID. Guns observed in TaCZ's global
loot-generation pass receive a `LOOT_GENERATED` marker with the queried loot
table ID. Crafting overwrites an existing origin with the crafted marker; loot
stamping never overwrites an existing marker.

The server accepts direct recovery only from a structurally valid,
server-recognized `LOOT_GENERATED` marker. Missing, malformed, unknown-version,
legacy, and third-party acquisition paths are treated as unknown and fail
closed. They may still use ordinary reverse engineering when its policy allows
it, but they cannot receive the found-weapon economy exception.

The marker is positive server workflow evidence, not a cryptographic signature.
Operators who use commands or other mods to rewrite arbitrary item NBT are
outside the normal survival trust model and should protect those capabilities.

## Recovery modes

`foundWeaponRecoveryMode` controls only positively verified found weapons:

| Mode | Blueprint action | Direct currency action |
| --- | --- | --- |
| `PROTECTED_BLUEPRINT_ONLY` | Protected blueprint | No |
| `RECYCLABLE_BLUEPRINT` | Recyclable blueprint | No |
| `DIRECT_RP_ONLY` | No | Yes |
| `PLAYER_CHOICE` | Recyclable blueprint | Yes |

`PROTECTED_BLUEPRINT_ONLY` is the compatibility default. Crafted and unknown
weapons always follow protected ordinary reverse-engineering behavior, even if
the configured found-weapon mode is more permissive.

Direct recovery is server-authoritative and re-evaluated at click time. It:

1. resolves the physical gun and its current reverse-engineering rule;
2. requires a gun whose ammunition can be recovered safely, with permitted attachments/modification state;
3. pays the rule's configured reverse-engineering currency and material cost;
4. consumes the physical gun exactly once;
5. records discovery without teaching the blueprint;
6. credits the same active-currency value used by blueprint recycling; and
7. commits atomically or restores input, inventory, output, knowledge, and currency.

`physical_blueprint_learning` governs whether a produced physical blueprint may
teach a recipe. It does not disable a non-learning result: direct currency recovery
remains eligible because it creates no blueprint, while a rule whose output is
explicitly recyclable may still create that recycle-only output. A protected,
non-recyclable blueprint remains disabled when physical blueprint learning is
disabled. This separation does not bypass blocked-content, known-item,
attachment/modification, cost, recycling, delivery-capacity, or rollback checks.

In banked mode, the RP-cap check is applied after the reverse-engineering cost
is paid; no output item is created. In physical mode, the complete reward fills
compatible Analyzer Output first and then main inventory. An incompatible output
or insufficient delivery space blocks the action before any payment or input
consumption. The reward cannot fund its own gross cost. When the blueprint is
not learned, the client requires a second in-screen confirmation; the server still treats the action itself as
an intentional sacrifice that does not learn the weapon.

Per-target recycling enablement/value and the global manual duplicate-recycling
policy remain authoritative. For example, selecting `DIRECT_RP_ONLY` while
globally disabling recycling produces an explicit disabled recovery state
instead of creating a second, ungoverned currency source.

## Economy and migration

The direct path deliberately reuses both the reverse-engineering cost and the
existing recycling value. It therefore does not introduce a separate reward
curve. Discovery awards are dispatched through the same post-commit transition
used by ordinary reverse engineering. Delivery capacity, transaction bounds, and
rollback behavior remain in force.

Found-weapon recovery was introduced without changing the player-save version
or rewriting existing items. Current builds require matching protocol-62
clients and servers. Existing guns have no origin marker and remain protected
unless a trusted server workflow later creates a newly marked gun. No automatic
backfill guesses whether an old gun was crafted or looted.

## Operator checks

After changing either mode:

1. reconnect a client so the synchronized configuration is unquestionably
   current;
2. run `/gg research status` and confirm the reported research cost mode;
3. inspect one item-heavy and one currency-heavy Research Tree path;
4. test a newly crafted gun, a newly generated loot gun, and an unmarked legacy
   gun in the Blueprint Analyzer; and
5. test physical recovery with compatible and incompatible Output and a full
   inventory; in banked mode, test near the RP cap.

Do not use an NBT-edited or operator-spawned item as evidence that ordinary loot
stamping works. For a runtime release gate, generate the loot through a real
loot table and craft the comparison gun through TaCZ's gun-smithing menu.
