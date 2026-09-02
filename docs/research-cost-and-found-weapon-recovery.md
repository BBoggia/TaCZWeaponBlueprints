# Research cost and found-weapon recovery

This feature adds two synchronized server policies without changing authored
research resources or persisted player progression.

## Research cost modes

`researchCostMode` selects the effective cost channels used by Research Tree
transactions:

| Mode | RP charged | Items charged |
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
instead of presenting a misleading zero-RP requirement.

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

| Mode | Blueprint action | Direct RP action |
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
2. requires an unloaded gun with permitted attachments/modification state;
3. pays the rule's configured reverse-engineering RP and material cost;
4. consumes the physical gun exactly once;
5. records discovery without teaching the blueprint;
6. credits the same RP value used by blueprint recycling; and
7. commits atomically or restores input, inventory, output, knowledge, and RP.

`physical_blueprint_learning` governs whether a produced physical blueprint may
teach a recipe. It does not disable a non-learning result: direct RP recovery
remains eligible because it creates no blueprint, while a rule whose output is
explicitly recyclable may still create that recycle-only output. A protected,
non-recyclable blueprint remains disabled when physical blueprint learning is
disabled. This separation does not bypass blocked-content, known-item,
attachment/modification, cost, recycling, RP-cap, or rollback checks.

The RP-cap check is applied after the reverse-engineering RP cost is paid. The
direct action does not require the Analyzer output slot to be empty because it
does not create an item. When the blueprint is not learned, the client requires
a second in-screen confirmation; the server still treats the action itself as
an intentional sacrifice that does not learn the weapon.

Per-target recycling enablement/value and the global manual duplicate-recycling
policy remain authoritative. For example, selecting `DIRECT_RP_ONLY` while
globally disabling recycling produces an explicit disabled recovery state
instead of creating a second, ungoverned RP source.

## Economy and migration

The direct path deliberately reuses both the reverse-engineering cost and the
existing recycling value. It therefore does not introduce a separate reward
curve. Discovery awards are dispatched through the same post-commit transition
used by ordinary reverse engineering. RP caps, maximum transaction bounds, and
rollback behavior remain in force.

This change requires matching protocol-47 clients and servers. It adds no new
player-save version and does not rewrite existing items. Existing guns have no
origin marker and remain protected unless a trusted server workflow later
creates a newly marked gun. No automatic backfill guesses whether an old gun
was crafted or looted.

## Operator checks

After changing either mode:

1. reconnect a client so the synchronized configuration is unquestionably
   current;
2. run `/gg research status` and confirm the reported research cost mode;
3. inspect one item-heavy and one RP-heavy Research Tree path;
4. test a newly crafted gun, a newly generated loot gun, and an unmarked legacy
   gun in the Blueprint Analyzer; and
5. test the direct action near the RP cap and with a non-empty output slot.

Do not use an NBT-edited or operator-spawned item as evidence that ordinary loot
stamping works. For a runtime release gate, generate the loot through a real
loot table and craft the comparison gun through TaCZ's gun-smithing menu.
