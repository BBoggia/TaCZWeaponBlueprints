# TaCZ 1.1.8 Compatibility Assessment

## Target

The compatibility target is **TaCZ 1.1.8-hotfix** for Minecraft 1.20.1 and
Forge, not the superseded `1.1.8-release` artifact.

- Upstream release: <https://github.com/MCModderAnchor/TACZ/releases/tag/1.1.8-hotfix>
- Modrinth version: <https://modrinth.com/mod/timeless-and-classics-zero/version/yOVIzIJR>
- Modrinth Maven repository: `https://api.modrinth.com/maven`
- Tested Maven coordinate: `maven.modrinth:timeless-and-classics-zero:1.1.8-hotfix`
- Binary SHA-1: `bddafeea4c9c1132ed720c30fbaedfe5ab25e846`
- Sources SHA-1: `b73081734e9f51ce0976515cee2547586c6a78a7`

This assessment was performed from the published `v1.0.3-beta7` commit on the
`codex/tacz-1.1.8-support` branch. Compatibility probes first used an isolated
copy; the changes were then implemented and validated on the support branch.

## Result

The current add-on is source- and startup-compatible with TaCZ 1.1.8-hotfix.
No catalog, progression, networking, crafting-enforcement, or loot-policy
rewrite was required. The dependency migration, redundant client-mixin removal,
focused filter tests, renderer hardening, and documentation updates described
below are implemented on the support branch.

## Upstream changes relevant to this add-on

### Stable integration points

The following TaCZ classes and methods used by the add-on are unchanged between
1.1.5 and 1.1.8-hotfix:

- `GunSmithTableMenu`, including the server-authoritative `doCraft` injection
  target;
- `GunSmithTableRecipe` and `GunSmithTableResult`;
- `ModRecipe.GUN_SMITH_TABLE_CRAFTING`;
- `TabConfig` and `ResultButton`;
- `IAmmo` and `IAttachment`;
- `CommonGunIndex` and `CommonAttachmentIndex`.

Every field and method descriptor targeted by `GunSmithTableScreenMixin` also
still exists. Both the client screen mixin and server menu mixin applied at
runtime without an injection error.

### Gunsmith screen behavior

TaCZ 1.1.8 adds an optional automatic "filter by held item" selection when the
gunsmith screen is initialized. It also reconciles `selectedRecipe` and clears
the ingredient-count state after filter changes. The blueprint filter runs at
the return of TaCZ's native `classifyRecipes`, so it composes with the new held
item, pack, search, tab, and ordering behavior instead of replacing it.

Two existing blueprint injections duplicate guards already present in TaCZ:

- the cancellable replacement of `addIndexButtons` duplicates TaCZ's null/empty
  list and null-recipe handling while directly editing Minecraft screen widget
  internals;
- the cancellable `getPlayerIngredientCount` injection duplicates TaCZ's null
  recipe guard.

They compiled and applied on 1.1.8, but were removed to reduce coupling. The
`getSelectedRecipe` null guard remains because TaCZ can call that method with a
null ID during empty-screen initialization.

### Lazy client resources

TaCZ 1.1.8 lazily loads gun, ammunition, and attachment models and animations.
The blueprint renderer only asks the client indexes for slot textures. Ammo and
attachment slot textures remain eagerly populated, and gun displays are now
created on demand through TaCZ's own `ClientIndexManager`. The existing fallback
to the catalog's synchronized slot texture remains valid.

The gun-display lookup is now evaluated once per render instead of calling
`getDefaultDisplay()` repeatedly. This is a small hardening/clarity change, not
a correction for a known rendering failure.

### Common resources and loot

`CommonAssetsManager` retains the recipe and gun/ammo/attachment index APIs used
by `BlueprintDataManager`. TaCZ adds its own loot-table injection manager and an
ammo sort value, neither of which changes blueprint catalog construction.

The default 1.1.8 pack injects a Model 943 revolver and ammunition into the
optional bonus chest. That coexists technically with blueprint loot injection,
but it is a progression-design consideration: obtaining a weapon as loot does
not teach its recipe. No interception should be added unless the project
explicitly decides that all direct TaCZ weapon loot must be suppressed.

## Compatibility probe evidence

An isolated build replaced the old CurseMaven dependency with the official
TaCZ 1.1.8-hotfix Modrinth artifact.

- `compileJava`: passed;
- automated tests: 47 passed, 0 failed, 0 skipped;
- dedicated server: reached `Done (7.240s)`;
- server mixin: `GunSmithTableMenuMixin` applied successfully;
- catalog: 172 valid blueprints from 173 default-pack recipes (53 guns, 24
  ammunition entries, and 95 attachments);
- expected validation: `tacz:misc/blood_strike_1` was skipped because its result
  is a hanging-entity item, not a supported gun, ammo, or attachment;
- dynamic loot snapshot: 6 pools, 6 rules, and 748 exact bindings loaded;
- client: OpenGL, OpenAL, the sound engine, and texture atlases initialized;
- client mixin: `GunSmithTableScreenMixin` applied successfully;
- no mixin application, injection, add-on exception, or client asset-loading
  error was recorded before the smoke client was intentionally terminated.

The startup probes validate class loading and mixin application. They do not
replace an interactive gunsmith-screen and blueprint-rendering acceptance pass.

## Implemented branch validation

The support branch now resolves the official Modrinth
`1.1.8-hotfix_mapped_official_1.20.1` artifact in normal development builds and
declares the bounded runtime range `[1.1.8,1.2)` in the packaged metadata.

- automated tests: 50 passed, 0 failed, 0 skipped;
- `cleanTest test build`: passed;
- `verifyReleaseArtifact`: passed, including the expected reduced client mixin
  surface and packaged dependency range;
- `verifyPublicationReadiness`: passed;
- dedicated server: reached `Done (2.024s)` with the server menu mixin applied;
- current development content set: 481 registered blueprints from 724 TaCZ
  recipes, with 235 duplicates ignored and 8 invalid results skipped;
- dynamic loot: 6 pools, 6 rules, and 748 exact bindings loaded;
- client with lazy loading enabled: reached the render loop with the gunsmith
  screen mixin applied;
- client with lazy loading disabled: reached the render loop with the gunsmith
  screen mixin applied.

The eager client probe revealed malformed filenames and missing animation
runtimes in the installed third-party Suffuse content pack. They are upstream
pack-data problems and occur independently of this add-on. No add-on exception,
mixin application error, or injection error occurred in either asset mode.

An interactive world pass is still required to visually confirm representative
gun, ammunition, and attachment blueprint icons, the held-item/search/tab/pack
filter combinations, live unlock/revocation behavior, crafting, and migration
of a real existing player save.

## Implementation plan

### 1. Pin the supported development artifact

- Add a Modrinth Maven repository scoped to the `maven.modrinth` group.
- Replace the CurseMaven TaCZ artifact declaration with the verified
  `1.1.8-hotfix` artifact.
- Remove the obsolete `tacz_curse_artifact` property.
- Set `tacz_version=1.1.8-hotfix`.
- Raise the declared runtime minimum to `[1.1.8,1.2)`.
- Keep Minecraft 1.20.1, Forge 47.x, and Fzzy Config 0.5.9 unchanged; all were
  proven in the isolated runtime.

Supporting both 1.1.5 and 1.1.8 in one release should only be claimed after the
final hardened code is tested against both versions. The safer initial contract
is 1.1.8 through the next breaking TaCZ line.

### 2. Reduce client mixin coupling

- Delete the `addIndexButtons` replacement and let TaCZ own widget registration.
- Delete the redundant `getPlayerIngredientCount` null injection.
- Remove shadows/imports/accessor methods that become unused.
- Retain the learned-recipe filter at `classifyRecipes` return.
- Retain the null-ID shield around `getSelectedRecipe`.
- Retain live refresh when configuration or learned recipes change.
- Retain the empty-state message.

### 3. Harden the lazy-resource renderer path

- Resolve a gun's `GunDisplayInstance` once per render and use the synchronized
  catalog texture when no live client display is available.
- Verify blueprint icons with TaCZ lazy loading both enabled and disabled.
- Verify a cold third-party gun-pack icon, not only the eagerly loaded default
  pack.

### 4. Add focused compatibility coverage

- Add structural tests that assert the intended mixin surface stays minimal.
- Add/update tests for the new dependency metadata and release verifier.
- Exercise an empty learned-recipe set while TaCZ's held-item filter is active.
- Exercise unlock/revocation while the gunsmith screen is open.
- Exercise tab, pack, search, pagination, and filter changes where no learned
  recipe remains selected.
- Confirm a locked recipe packet is still rejected by the server mixin.

### 5. Update operator and release documentation

- Update the README and operations guide from TaCZ 1.1.5 to 1.1.8-hotfix.
- Update current changelog compatibility wording without rewriting historical
  phase reports.
- Remove the obsolete TaCZ 1.1.5 JavaDoc reference.
- Record the native TaCZ bonus-chest behavior as a progression-design note.
- Choose the add-on's next version only when the compatibility changes are ready
  for release.

### 6. Run the full release gates

- Run `cleanTest test build` and the artifact/publication verifiers.
- Repeat isolated dedicated-server and client startup smokes.
- Complete an interactive gunsmith workbench pass and render representative gun,
  ammo, and attachment blueprints.
- Test at least one existing player save to confirm learned recipe/blueprint data
  remains unchanged across the TaCZ upgrade.

## Acceptance criteria

TaCZ 1.1.8 support is ready when the verified artifact is used by normal builds,
all automated/release gates pass, both runtime environments start without an
add-on or mixin error, the new native filters compose with blueprint filtering,
lazy and eager asset modes render blueprint icons, server crafting remains fail
closed for locked recipes, and existing learned progression survives the
upgrade.
