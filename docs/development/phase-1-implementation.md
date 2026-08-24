# Phase 1 Implementation

Date: 2026-08-24

Phase 1 updates the blueprint catalog and gun-smithing screen integration for TaCZ 1.1.5. It also separates structure-mod data-generation dependencies from the default development runtime.

## Blueprint catalog

`BlueprintDataManager` now derives each blueprint from the recipe's actual output stack instead of parsing the recipe ID path.

- Guns use `IGun.getIGunOrNull` and `getGunId`.
- Ammo uses `IAmmo.getIAmmoOrNull` and `getAmmoId`.
- Attachments use `IAttachment.getIAttachmentOrNull` and `getAttachmentId`.
- TaCZ's gun, ammo, and attachment indexes provide the translation key, category, and display slot.
- Recipes are sorted by ID before catalog construction, making duplicate selection deterministic.
- The first recipe for a unique output wins; later recipes for the same output are treated as aliases.
- Catalog rebuilds are assembled separately and published as immutable snapshots, so readers cannot observe a partially rebuilt map.
- Invalid blueprint IDs supplied through item NBT now return no match instead of throwing from `ResourceLocation` construction.
- Assertions and per-recipe failure spam were replaced with explicit validation and aggregate diagnostics.

The current TaCZ pack set produces:

| Result | Count |
| --- | ---: |
| Input gun-smithing recipes | 694 |
| Unique registered blueprints | 452 |
| Guns | 179 |
| Ammo types | 43 |
| Attachments | 230 |
| Duplicate recipe aliases ignored | 235 |
| Invalid unresolved recipe outputs skipped | 7 |

The seven invalid recipe entries represent five underlying unresolved pack outputs; two are duplicate `zugzwang` aliases:

- `atea:attachments/grip_lb1`
- `atea:attachments/muzzle_ultra5`
- `ccrp:attachments/stock_haenel_buttstock`
- `classicr:attachments/ammo_mod_rubber`
- `suffuse:gun/ags30`
- `zugzwang:atea/attachments/grip_lb1`
- `zugzwang:atea/attachments/muzzle_ultra5`

These entries are skipped safely and listed in one warning. Duplicate aliases are reported separately at INFO level.

## Gun-smithing screen compatibility

The old mixin cancelled TaCZ's `classifyRecipes` method at its start, before TaCZ populated its recipe tabs. It then attempted to replace two TaCZ `final` fields and reimplemented grouping by parsing recipe paths.

The mixin now lets TaCZ 1.1.5 perform its native classification and filters the resulting recipe lists at method return using the player's learned recipe IDs. This preserves TaCZ's block tabs, pack filters, search behavior, ordering, and result grouping while enforcing blueprint unlocks.

The redundant constructor redirect was also removed because TaCZ 1.1.5 already guards its initial recipe selection against null and empty lists.

## Development dependency separation

The structure-mod suite is no longer loaded by default. This keeps the normal client and dedicated-server classpaths limited to the core runtime and prevents the Library Ferret and Quark/Zeta dedicated-server crashes observed in Phase 0.

Structure mods remain available for isolated loot-support generation with:

```shell
./gradlew runData -PincludeStructureDataMods=true
```

## Validation

- Java 17 `compileJava`: successful.
- Java 17 `build`: successful; the project still has no unit-test source (`NO-SOURCE`).
- Default `runServer`: reached `Done (1.504s)` and shut down with all three dimensions saved.
- Server catalog: 452 unique blueprints; no recipe-ID parsing failures or `Unknown item type` messages.
- Default `runClient`: reached the normal render loop with OpenAL and texture atlases initialized.
- Client log: the four illegal `GunSmithTableScreenMixin` final-field writes are gone. The only two ERROR records are pre-existing invalid Suffuse asset filenames.
- Isolated opt-in `runData`: `BUILD SUCCESSFUL in 20s`; 486 cached outputs, zero files written.
- Isolated generated-resource comparison: zero differences, excluding `.cache` and `.DS_Store` metadata.
- All 518 main/generated JSON files parse successfully.
- The 485 global loot-modifier entries exactly match the 485 modifier JSON files.

The runtime server and client checks provide integration coverage for the Forge/TaCZ lifecycle. Focused JVM unit tests remain unavailable because the repository has no test harness and catalog construction depends on live TaCZ registries and pack indexes.

## Remaining external pack diagnostics

Phase 1 does not alter third-party TaCZ content packs. The smoke tests still report:

- A malformed `ccrp:lang/en_us.json` file.
- Two invalid Suffuse asset paths: one uppercase `.WAV` extension and one filename containing a space and parentheses.
- Five underlying recipe outputs that resolve to an empty item stack, listed above.

These no longer prevent catalog construction, client startup, or dedicated-server startup.
