# Research Tree Navigation Phase 6

Date: 2026-08-25

Phase 6 completes the built-in TaCZ 1.1.8-hotfix progression. It changes only
packaged datapack content and its regression contract; research remains
server-authoritative, existing learned blueprint IDs remain learned, and
modpacks can replace any of these definitions through datapacks.

## Balance method

The 54 default weapons were checked against the gun definitions in the pinned
`maven.modrinth:timeless-and-classics-zero:1.1.8-hotfix` artifact. Ranking is a
manual, role-normalized judgment rather than a runtime score. The review
considered:

- close and sustained damage, fire rate, burst/automatic modes, and capacity;
- reload behavior, range falloff, armor ignore, and head-shot behavior;
- aim time, weight, attachment breadth, and special gun scripts; and
- role-defining constraints such as single-shot launchers, shell reloads,
  inventory-fed ammunition, and overheating.

This avoids pretending that one formula can directly compare a revolver, a
shotgun, a battle rifle, and a launcher. Comparable sidegrades intentionally
share a rank.

## Shipped branches

Ranks are listed from bottom to top, matching the in-game canvas.

| Branch | Rank 0 entry | Middle progression | Highest rank |
| --- | --- | --- | --- |
| Pistols | Taurus 943 | Glock 17/M9A4, then service and specialty pistols | Taurus 500 |
| SMGs | Uzi | MP5A5, then UMP45/P90 | Vector .45 |
| Shotguns | Long Double Barrel | Short Double Barrel/M870, then M1014 | AA-12/SPAS-12 |
| Rifles | M16A4 | Four role families leading through assault, battle, and marksman rifles | SPR15 HB/SCAR-H |
| Snipers | Springfield 1873 | Kar98/M700, AWP, then M95 | M107 |
| Machine Guns | RPK | M249, then FN Evolys | Minigun |
| Special Weapons | M320 | -- | RPG-7 |

Every branch has one independent entry root. Every non-root weapon has at
least one prerequisite in the same branch and a lower authored rank. The
default graph therefore has seven components and never forces a player to
research an unrelated category.

The M1911 and M16A1 are no longer entry roots. TaCZ's current definitions give
them substantially better practical output than weaker choices in their
categories. The SMG and shotgun branches also use an additional distinct step
where their capacity, automatic fire, or special scripts justify it.

## Economy

The established 4, 6, 8, 10, and 12 Research Point tiers remain intact.
Material requirements rise with progression, and every dependent weapon costs
strictly more Research Points than its prerequisite. Exceptionally powerful
short branches may skip an intermediate price tier without inventing filler
nodes.

## Validation

`DefaultTaCZResearchTreeTest` now pins the complete authored rank list for all
seven groups and verifies:

- exact one-time coverage of all 54 TaCZ 1.1.8-hotfix weapons;
- exactly seven groups, roots, and independent components;
- one root per group and a root icon that matches it;
- a prerequisite on every non-root node;
- lower-rank, same-group prerequisites only;
- strictly increasing costs along every edge; and
- use of every intended 4/6/8/10/12 RP tier.

These checks catch catalog drift, accidental duplicate membership, reversed
tiers, cross-category gates, orphaned upgrades, and unintended balance edits
before a release is built.

## Remaining work

Phase 7 owns adversarial scale, reload, fallback, and disclosure testing.
Phase 8 owns in-game playtesting and final release documentation. The authored
ordering is intentionally easy to rebalance after real play sessions without
changing Java code, the network protocol, or saved player progression.
