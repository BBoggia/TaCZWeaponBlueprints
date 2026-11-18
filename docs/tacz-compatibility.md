# TaCZ compatibility

## Current NeoForge target

Version 1.3.11 uses the official TaCZ 1.2.0-hotfix for Minecraft 1.21.1
NeoForge, Modrinth project `SzzJttH8`, file `tJiZL0He`. The required TaCZ
range remains `[1.2.0,)`. [Official NeoForge hotfix and release notes](https://modrinth.com/mod/timeless-and-classics-zero/version/tJiZL0He)

The hotfix adds rotation to the native gun smith table. Its root-position
lookup and the classes the addon hooks for opening the menu, filtering recipes,
crafting, and marking loot are unchanged from 1.2.0. The addon continues to use
the recipes TaCZ loads on the server, including those from packs TaCZ now tries
to load without a 1.21.1 declaration.

## Integration changes

- The workbench's tab-membership callback changed from `lambda$getRecipe$2`
  to `lambda$getRecipe$3`. The magazine integration still preserves native
  recipe filters and the server's crafting permission checks.
- The crafting callback changed from `lambda$doCraft$3` to `lambda$doCraft$4`.
  TaCZ now copies the recipe result through `getResultItem`, then splits it
  into stacks that respect item limits. Crafted-origin tracking applies to
  each split output and preserves the native remainder reduction.
- TaCZ introduced the `dmr` item type and `tacz:dmr` recipe tab. All three
  Crafting Workbenches include that tab. Marksman blueprints appear in the
  Rifle Blueprints creative tab, use gun-sized artwork and names, and are
  offered in subgroup settings. When weapon evidence is unavailable, their
  automatic-placement fallback uses the sniper score band.
- TaCZ can store free factory attachment choices as installed attachment
  stacks. The physical-item resolver ignores one only when it carries TaCZ's
  virtual-factory marker and belongs to that gun's declared factory choices.
  An unmarked physical copy still blocks consumption, even if its ID matches
  a factory choice.

The inspected public API calls for blueprint catalog loading, item identity,
ammo recovery, weapon evidence, and blueprint artwork retain their signatures.
The native block-entity menu hook and loot-injector hook also retain their
targets. TaCZ's new JEI selection flow still reaches the addon's selection and
crafting guards.

## Updating an installation

Replace the unofficial TaCZ jar with the official **NeoForge** jar on the
client and server; both use the `tacz` mod ID and must not be installed together.
For hotfix testing, install the 1.3.11 NeoForge addon build on both the
client and server. Published addon 1.3.9 builds target the unofficial port.

This change does not rewrite addon configuration files or stored player
knowledge. TaCZ recategorized some existing weapons as `dmr`; pack-authored
rules or configured exemptions that intentionally select `rifle` or `sniper`
should be reviewed if they are meant to include those weapons. Existing custom
workbench definitions with explicit tab lists need `tacz:dmr` as well.

TaCZ 1.2.0 ships its default gun pack as a shared base with a 1.21.1 overlay.
The hotfix attempts to load third-party packs without a 1.21.1 declaration.
Packs with incompatible resources can still require TaCZ's conversion command. The addon's blueprint catalog
continues to use the server's loaded recipes rather than parsing exported pack
folders. Existing 1.1.8 research reference catalogs remain historical scoring
baselines, not a declaration that new TaCZ weapons are absent or unsupported.

## Release validation

The packaged 1.3.11 addon ran on a Minecraft 1.21.1 NeoForge 21.1.233 client
and dedicated server with the official TaCZ hotfix. Checks covered learning
physical blueprints, hiding locked recipes, crafting on addon and native
workbenches, marking crafted weapons, reconnecting, and buying a quoted path in
the Research Bench. The feature suite also exercised the Blueprint Analyzer,
both villager professions and the Designer.

The release checks used disposable worlds. An existing unofficial-port world,
third-party gun packs, and every combination of custom attachments and bulk
recipe outputs remain outside this runtime check.
