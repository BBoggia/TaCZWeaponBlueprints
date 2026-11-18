# TaCZ compatibility

## Forge 1.20.1 target

Version 1.3.11 uses the official TaCZ 1.2.0-hotfix for Minecraft 1.20.1
Forge, Modrinth file `4yAbMXbz`. The required TaCZ range remains `[1.2.0,)`.
[Official Forge hotfix and release notes](https://modrinth.com/mod/timeless-and-classics-zero/version/4yAbMXbz)

The workbench screen, menu, block entity, recipe, and loot-injection classes
the addon integrates with are unchanged from 1.2.0. Blueprint research and
crafting use the same hooks with either TaCZ file.

## Integration changes

- Workbench tab membership now targets `lambda$getRecipe$3`, and crafted-output
  tracking targets `lambda$doCraft$4`. The native server crafting entry point
  still enforces blueprint access and physical workstation permissions.
- TaCZ copies recipe results through `getResultItem` before splitting them
  into legal output stacks. The addon preserves that split and its remainder
  reduction, then stamps each emitted weapon. Forge recipes still supply their
  own IDs through `getId`; no NeoForge recipe-holder or data-component code is
  used in this integration.
- All three Crafting Workbenches expose `tacz:dmr`. Marksman blueprints appear
  in the Rifle Blueprints creative tab and use gun artwork sizing and naming.
  Subgroup settings offer `dmr`, and automatic placement uses the sniper
  fallback score band when weapon evidence is unavailable.
- Free factory attachment choices no longer count as removable attachments
  during physical-item inspection. Both TaCZ's virtual-factory marker and
  membership in the gun's declared factory choices are required. An unmarked
  physical copy of the same attachment still blocks consumption.

The inspected blueprint catalog, item identity, ammo recovery, weapon evidence,
artwork, native block-entity menu, and loot-injector APIs retain the signatures
used by the addon. The new JEI recipe selection path still reaches the addon's
selection guard and native crafting authorization. These are static findings,
not runtime acceptance results.

## Existing worlds and packs

No addon configuration or saved player knowledge is rewritten by these changes.
Existing 1.1.8 research reference catalogs remain historical scoring baselines.
New recipe-backed weapons continue to enter the server's blueprint catalog.

TaCZ moved some weapons, including the MK14, into the `dmr` category. Review
pack rules or configured exemptions that select only `rifle` or `sniper` if
they should also apply to those weapons. Custom crafting workbench definitions
with explicit tab lists need `tacz:dmr` too.

Install the same addon build on the client and server. For hotfix testing, use
the 1.3.11 Forge build with the Forge TaCZ file on both sides.

## Release validation

The packaged 1.3.11 addon ran on a Minecraft 1.20.1 Forge 47.4.10 client and
dedicated server with the official TaCZ hotfix. The client selected a blueprint
in the Research Bench, confirmed that an unlearned recipe was hidden in the
Tier 3 Crafting Workbench, learned its physical blueprint, and found the recipe
in the native TaCZ screen. The server retained player progression through a
datapack reload and shut down cleanly.

The release checks used a fresh world. Existing-world upgrades, third-party
gun packs, TaCZ Magazines with the hotfix, and Forge recipe-viewer combinations
remain outside this runtime check.
