# Research economy supply and balance

Physical Scrap and Research Data analysis are the defaults. Explicit banked RP
and deterministic redemption settings remain supported. Existing saved RP is
preserved; see [migration](research-currency-migration.md) before withdrawing it.

## First Scrap and renewable supply

The selected physical currency drops from early exploration and player kills.
The bundled supply modifier targets these vanilla tables:

| Source | Chance | Quantity | Expected units per eligible roll |
| --- | --- | --- | --- |
| Toolsmith/weaponsmith village chests, mineshafts, dungeons, shipwreck supplies, outposts | 100% | 8–16 | 12 |
| Stronghold libraries, mansions, bastion treasure, ancient cities, End city treasure | 100% | 16–32 | 24 |
| Player kills of zombies, husks, skeletons, strays and pillagers | 25% | 1–2 | 0.375 |

These sources require no blueprint or workstation. They supplement existing
physical gameplay awards and found-blueprint recycling. Loot is supplied only
in physical mode, using the configured currency item. Changing that item changes
future supply; it does not exchange old Scrap.

`enableScrapLoot` enables this modifier independently of `enableResearchDataLoot`
and gameplay award settings. `scrapLootBudget` defaults to 32 and accepts 0–64.
Currency already present in the generated loot counts toward that limit; the
modifier never removes existing loot. This is a per-roll limit, not a per-player
cooldown. Other mods or later modifiers can add their own loot independently.

Pack authors can replace
`data/taczweaponblueprints/loot_modifiers/scrap_supply.json`. Its `tables` map
uses canonical loot-table IDs and rows containing `chance`, `minimum`, `maximum`,
and `player_kill`. Chances must be finite and in (0,1], quantities 1–64, and at
most 1,024 tables are accepted. Standard loot `conditions` remain available for further
filtering. A table absent from the map receives no injection. The generated file
is reproduced by the normal loot-modifier data provider.

## Data outcomes

Each action consumes one Data item and has no currency or material cost. It
produces a physical blueprint or matching fragments for one eligible weapon.

| Data item | Target tiers | Full blueprint chance at Analyzer T1 / T2 / T3 |
| --- | --- | --- |
| Research Note | T1 | 0% / 0% / 0% |
| Research Report | T1–T2 | 2% / 4% / 6% |
| Research Dossier | T1–T3 | 4% / 8% / 12% |

Fragment outcomes give 1–2, 2–3, or 3–4 fragments at Analyzer T1, T2, or T3.
Tier weights are equal; eligible weapons within a tier are equally weighted by
default. Empty tiers are removed and the remaining tier weights renormalize.
Learned and undiscovered targets are allowed by default. The independent global
eligibility settings and per-item overrides can change that behavior.

The shared fallback reconstructs T1/T2/T3 blueprints from 5/10/15 matching
fragments. Existing explicit target, profile, or legacy Analyzer thresholds take
precedence. Archives retain up to 1,000 fragments per target. Rewards respect
prerequisites and are protected from currency recycling. Known-target results
can still be traded to another player; protection and prerequisites travel with
the item. They do not turn into currency automatically.

Notes provide steady T1 fragments, while rarer Reports and Dossiers broaden the
pool and sometimes bypass fragment collection. Improved fragment quantities
make higher stations useful even when the player analyzes a Note. These are
initial balance values: broad catalogs spread progress across more targets, so
playtesting should include actual installed gun packs.

## Reports and examples

Run `/gg research export`, then:

```text
./gradlew generateAnalyzerBalanceReport -PanalyzerCatalog=/path/to/research-catalog.json
```

The format-22 catalog adds a versioned `research_data_economy` section with
resolved per-target probabilities, target tiers, outcome tables, thresholds,
policy costs, and fresh, 90%-known, and Tier-1-only catalog scenarios. This is an operator export, never
a client preview. The scenarios use standard eligibility defaults without
server blacklist/exemption overlays, and state that scope explicitly.

The balance report adds base research currency totals, 64-item stack counts,
the largest node cost, per-weapon recycling returns and reverse-engineering costs, supply expectations, and seeded Data simulations. It
compares 100 Data items per player used alone or pooled by four players, over
100 trials. It reports direct blueprints, completed fragment sets, duplicate
blueprints, and per-player outputs. The known scenario marks 90% of sorted catalog IDs as learned; the report gives
the resulting weighted duplicate probability for each weapon pool. Known sets remain fixed, so these are supply
estimates rather than a simulation of learning or prerequisite progression.
Initial archives and material bottlenecks are excluded. Empty pools are reported
without drawing rewards. Target rows and simulation work have explicit limits.

Supply estimates use the bundled modifier and budget 32 unless overridden:

```text
./gradlew generateAnalyzerBalanceReport -PanalyzerCatalog=/path/to/research-catalog.json -PeconomySupply=/path/to/scrap_supply.json -PeconomySupplyBudget=16
```

The report estimates eligible rolls with no preexisting currency. Source chance
and quantity, currency budget, Data costs, fragments, and protected duplicate
utility should be considered together. A full tree need not fit in one inventory;
players can carry and spend successive stacks. A single purchase still needs
all its currency and materials available together.

[Research economy examples](../examples/research-economy/README.md) cover an
alternate item, legacy settings, unlearned-only pools, custom tier/weapon weights,
and recyclable Data rewards. Build their ZIPs with
`packageResearchEconomyDatapacks`.

## Recycling and custom economies

Default Data outputs cannot be recycled, and analyzing Data cannot mint Scrap.
Ordinary blueprint recycling consumes its input. Default weapon reverse
engineering produces protected blueprints; direct found-weapon recovery retains
its found-origin checks, so crafting a weapon does not create a recovery source.
These controls prevent the default craft–analyze–recycle loop.

Pack authors enabling recyclable Data, changing weapon output permissions,
adding Data recipes, or reducing costs must compare those outputs with their
recipes and sinks. The tool reports resolved weapon Analyzer quotes as well as
Data outcomes, but cannot prove arbitrary mod recipes unprofitable. Renewable
mob supply is intentional and can be disabled or rebalanced separately.
