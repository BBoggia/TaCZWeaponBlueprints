# TaCZ compatibility

## Current NeoForge target

[TaCZ] Weapon Research & Blueprints 1.3.10 targets the official
Timeless and Classics Zero (TaCZ) 1.2.0 release for Minecraft 1.21.1 NeoForge.
`gradle.properties` pins Modrinth project `SzzJttH8`, version `O2Pfsh6w`, and
sets the required TaCZ range to `[1.2.0,)`. Minecraft, NeoForge, Java, and Fzzy
Config requirements are unchanged.

The [official release notes](https://github.com/MCModderAnchor/TACZ/releases/tag/1.2.0-neoforge-1.21.1)
credit the unofficial port and describe the shared implementation. The
[published NeoForge artifact](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9454361)
is marked beta on CurseForge. The dependency pin selects the NeoForge artifact
by version ID rather than a version string shared with the Forge artifact.

The 1.3.10 build passes compilation, automated tests, and packaged-artifact
checks against the pinned official dependency. No Minecraft client or server
was launched for this update. Earlier runtime acceptance results used the
unofficial 1.1.8-hotfix-r7 port and do not certify this update. The separately
prepared Forge 1.3.10 build has the same runtime-validation limitation.

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
crafting guards. Runtime behavior remains to be checked.

## Updating an installation

Replace the unofficial TaCZ jar with the official **NeoForge** jar on the
client and server; both use the `tacz` mod ID and must not be installed together.
Use matching addon builds. Published addon 1.3.9 builds still target the
unofficial port; changing TaCZ alone does not apply these source fixes.

This change does not rewrite addon configuration files or stored player
knowledge. TaCZ recategorized some existing weapons as `dmr`; pack-authored
rules or configured exemptions that intentionally select `rifle` or `sniper`
should be reviewed if they are meant to include those weapons. Existing custom
workbench definitions with explicit tab lists need `tacz:dmr` as well.

TaCZ 1.2.0 ships its default gun pack as a shared base with a 1.21.1 overlay.
Third-party gun packs must provide the appropriate 1.21.1 resources; TaCZ's
release notes describe its conversion command. The addon's blueprint catalog
continues to use the server's loaded recipes rather than parsing exported pack
folders. Existing 1.1.8 research reference catalogs remain historical scoring
baselines, not a declaration that new TaCZ weapons are absent or unsupported.

## Validation still required

Compilation, automated workbench contract checks, and packaged-artifact checks
pass. Client and dedicated-server startup with required mixins remain unchecked.
In disposable worlds, verify learned and locked recipes, all three crafting
tiers, bulk output stacks and crafted-origin tracking, Marksman recipes,
factory versus physical attachments in the Blueprint Analyzer, and JEI recipe
selection. Repeat login and datapack reload checks with TaCZ's chunked gun-pack
synchronization, including an existing unofficial-port world backed up first.

The previously recorded release validation must be regenerated for the new
dependency before certifying this update for publication. Packaging alone is not runtime
certification.
