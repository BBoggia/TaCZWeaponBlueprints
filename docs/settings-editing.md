# Settings editing contract

The settings foundation lets Quick Setup and Advanced Settings share a draft
without creating another gameplay configuration. The mod settings entry opens a landing page separating Personal Settings from
World & Server Settings. Connected worlds open Quick Setup, with everyday choices
and a Gameplay Summary. Advanced Settings offers seven categories with search and
paged native Fzzy editors. Personal Settings exposes the active client preferences
through a separate local draft. Main-menu Local Defaults retains the native editor.

## Storage and compatibility

`BlueprintConfig` remains registered as `taczweaponblueprints:blueprint`, with
server schema 5 and flat fields in `config/taczweaponblueprints/blueprint.toml`.
`ResearchTreeClientConfig` remains client-only, with schema 3 in
`config/taczweaponblueprints/research_tree_client.toml`. Existing lane, hold, and notification preferences carry over. The retired
`techTreeView` key is ignored on read. `GeneralConfig`
is not registered.

The [field inventory](settings-field-inventory.csv) lists the values, groups, and actions on each settings page. It includes each field's label, scope, permissions, current
group/condition, destination, and persistence status. `ConfigGroup` and
`ConfigAction` are presentation entries, not saved sections. Six personal layout
fields were already deprecated and excluded by Fzzy; seven other legacy layout
values remain serialized. The four active personal controls remain separate
from server settings.

The fixed compatibility fixtures under `src/test/resources/settings/` capture
1.3.9 defaults and effective behavior from the unchanged config classes, plus
custom current configurations, banked RP, physical currency, authored profiles,
missing-addon selectors, exact overrides, workstation mappings, dormant values,
legacy schemas 0/2/3, and personal preferences. Current valid values are preserved;
the existing migration/normalization behavior still governs old or invalid files.
Unknown TOML fields do not acquire a new preservation guarantee.

## Shared model

`SettingsCatalog` assigns canonical server fields to Advanced destinations and
identifies the Quick Setup controls. `SettingsCondition` describes the existing
native UI conditions without changing gameplay or storage. `SettingsSnapshot`
contains immutable stored values, authoritative profile context, permission state,
a connection session token, and a revision. Values use single-key TOML documents,
including maps and lists, so no second set of setting types needs to be invented.

`SettingsPresentation` uses existing preset, station, and domain resolvers. It
separates stored selections from effective results and identifies inactive,
unsupported, read-only, and unavailable states. A missing or changing profile is
unavailable, never a guessed local default. Domain summaries describe the category
gate; authored item exclusions, exact overrides, and progression gates can still
affect individual entries. Currency identity is retained alongside the selected
cost channels. Renderers translate the existing field/condition keys and new
result keys; they should cache a presentation until the draft or snapshot changes.

`SettingsEditSession` owns the pending map. Both views must use that same instance.
Preview and discard operate only on the draft. Applying freezes the submitted
proposal so edits cannot disappear behind an arriving reply. A failed or stale
proposal remains available for comparison, but must not be silently rebased onto
a newer snapshot. Independent crafting-tier changes copy the old preset-resolved
research flag into Custom before changing crafting. Explicit research access,
item overrides, and dormant values are retained.

## Fzzy integration

The adapter targets the pinned Fzzy Config 0.7.7+fix1 binary. Its public screen
provider supplies navigation, but its native screen update managers and synced
registry are internal. This foundation therefore uses a narrow server-validated
patch adapter. It does not reflect into Fzzy internals, add GUI mixins, register a
duplicate config, or nest existing fields into `ConfigSection` objects.

`FzzySettingsAdapter` accesses this mod's public fields and Fzzy's public
serialization, validation, and copy methods. It prepares a detached candidate,
validates the entire requested change, rejects corrected or inconsistent values,
and checks that no unrelated field would change through normalization. Stored
conditional values are read unconditionally so dormant custom values survive.
It installs accepted values into the original registered wrappers.

`SettingsTransaction` checks permission and the session revision before validation
and again checks the revision before saving. The revision covers current stored
configuration, the actual config file, and catalog/research/policy publications.
Another operator's edit, a file change not yet picked up by Fzzy, or a datapack
reload requires a fresh read. These are optimistic conflict checks; they do not
lock an external text editor across a filesystem write.

Persistence uses Fzzy's public `save()` on the detached candidate. This preserves
its file-watcher suppression and normal file format. Because `save()` returns
void and can report errors only in logs, the adapter reads the file back and
compares the entire parsed document before installing live values. A failed save
does not publish gameplay changes. Fzzy does not expose an atomic file-write
transaction: after a failed or interrupted save, the operator must inspect the
file/logs and reopen before retrying. No success is claimed without verification.

A successful batch calls `BlueprintConfig.onUpdateServer(ServerUpdateContext)`
once. Dedicated servers then use Fzzy's public `network().syncConfig`.
Integrated hosts share the registered config with their client; replaying a full
sync into that shared object can restore an older batch. They instead retain the
already-applied values and send a bounded complete `SettingsConfigSyncPacket`
only to remote LAN guests. Guests validate a detached copy before installing it
through the same public field wrappers, run `onSyncClient`, and save Fzzy's local
cache. The receiver also checks the connection and ignores local-host delivery.
The existing publication rebuilds,
open-workbench handling, player synchronization, starting-grant reconciliation,
and station recipe reload path remain responsible for runtime changes. No-op,
read, discard, denied, invalid, stale, and failed-save paths skip this callback.
If saving succeeds but refresh or the subsequent read fails, the result reports
saved changes with an unconfirmed refresh; clients must not replay that batch.

## Transport and lifecycle

NeoForge protocol `neoforge-2` adds mandatory settings request/reply and LAN sync payloads.
These development builds require matching client/server versions. Apply requests
contain only intended fields, with a 24,000-byte budget; replies have a
196,608-byte budget. Field counts, UTF lengths, duplicates, and total encoded
sizes are bounded. Oversized settings require the existing editor or direct
server-file workflow; values are never truncated or reset to make them fit.

The server binds each session to the actual `ServerPlayer` connection, expires
it after 30 minutes, rechecks operator level 2 on every apply, and consumes the
session after an apply attempt. Replies are request-ID-bound. Logout/server stop
clear sessions. Client timeout, rejection, and save failure require a fresh read;
disconnect clears the draft, including when switching servers. A submitted apply
cannot be cancelled as if it had never reached the server. Its authoritative
outcome must be read or checked after reconnecting.

Fzzy retains its existing synchronized-config caching on clients. This adapter
does not treat a local client save as a server update. Local Defaults and Personal
Settings are explicit destinations; pending server drafts are never reused there.

## Verification

Focused settings tests cover fixed stored/effective baselines, field coverage,
flat paths, dormant values, migrations, independent crafting changes, discard,
no-op, coordinated validation, stale revisions, permission loss, failed saves,
failed refresh, translated result keys, and transfer limits.

After `./gradlew preparePackagedVerification`, run
`python3 scripts/verify-neoforge-runtime.py --suite settings` with Java 21.
The disposable real client/server fixture checks read-only access, permission
revocation, simple and coordinated apply, normal Fzzy synchronization,
save/reload, concurrent operators, datapack and file conflicts, reconnect cleanup,
and unchanged player progression, inventory, and saved villager offers.
It also exercises Quick Setup, draft handoff to Advanced, native editors,
summary navigation, keyboard controls, actual automatic GUI scaling, narration
labels, and expanded translation strings. The `settings-integrated` suite repeats
the supported editing flow in singleplayer; run `core` first to supply its disposable
world. These focused checks do not replace the full packaged release matrix.

## Quick Setup and effective summary

Quick Setup edits the existing progression, research, category access, station
access, crafting levels, discovery, cost-channel, and villager fields. Category
choices retain Follow modpack, On, and Off. Existing PRESET station access appears
as Follow existing rules. Custom discovery stays Custom, and changing its preset
does not erase the dormant custom values. The independent Workbench levels
control uses the coordinated research-preserving edit described above.

The scrollable cards use vanilla focus, keyboard navigation, and narration.
Each card includes a gameplay explanation and its inactive or unavailable reason;
the full text is also available in its tooltip. The common search field opens
Advanced search results without saving. Returning from Advanced refreshes the
visible choices from the same draft, retaining both views' pending changes.

Gameplay Summary distinguishes the last applied server snapshot from the pending
preview. Neither is a live subscription to other operators' changes: Refresh
fetches current authoritative state and retains any proposal for explicit review.
The summary shows weapons' profile-derived category access, inherited ammo and
attachment access, Research Bench tiers, Workbench levels, discovery visibility
and loot, and the actual currency name. Its cost row links to the Currency
category in Advanced. Category summaries explicitly retain item exclusions and
authored gates; missing or newly selected profiles display Unavailable until
authoritative context is obtained. Custom crafting exceptions are disclosed.

`QuickSetupSettings` maps each choice to its canonical field or coordinated
crafting edit. `QuickSetupText` translates resolved values on the client. Screens
cache `SettingsPresentation` summaries until their draft or snapshot changes,
instead of rebuilding policies during rendering.

## Advanced navigation

The custom screens use public `ConfigScreenProvider`, `PopupWidgetScreen`, and
validated field widgets. A detached BlueprintConfig supplies the existing units,
validation, autocomplete, conditions, and list/map popups. Listeners update the
shared draft, never the registered config. A wrapper enforces permission and
request-state checks even for widgets that recompute their active state while
rendering. Labels expose descriptions and inactive-setting reasons in tooltips.

Search includes translated labels/descriptions, category names, and canonical
field names. Clicking a search-result label opens its category at the corresponding
page. Category positions and the last edited field are retained within the screen.
Search preserves its cursor when results rebuild. Native controls narrate their
setting name along with their value. Truncated status messages expose their full
text on hover at smaller GUI sizes.
Unsupported magazine editors are omitted; their stored values remain untouched.

Apply submits the shared draft. Leaving with pending changes offers Discard or
Cancel; category navigation does neither. Refresh retains a stale proposal, and
Review compares each proposed field with the freshly fetched server value before
an explicit handoff. Keeping a reviewed proposal does not apply it automatically.


## Personal preferences

Personal Settings shows lane grouping, hold-to-confirm, hold duration, and reward messages. The same public
Fzzy field widgets retain their validation and units. The public screen provider
routes both the landing-page button and the exact personal scope to this view.
Inactive layout fields are omitted from navigation, not marked deprecated or
removed from persistence. The config remains schema 3 with unchanged defaults.

`PersonalSettingsSession` owns a detached copy of the client configuration.
Apply checks that both live values and the file still match the opening baseline,
saves the candidate through Fzzy, verifies the parsed file, then updates the
original field wrappers and calls `onUpdateClient`. No server settings request or
server gameplay callback is involved. A conflicting in-tree branch-label toggle
or file edit produces a stale result instead of overwriting newer preferences.
Discard abandons the draft and reloads current values. Failed saves do not install
the draft; saved changes whose refresh fails are reported separately.

Reset tree appearance invokes the existing reset on the detached copy after a
scope confirmation. It restores branch grouping and resets saved legacy layout values. Hold-to-confirm, hold duration, and reward
messages are retained. Reset remains pending until Apply. Ordinary edits keep
all hidden compatibility values; already deprecated fields retain the existing
serializer's omission behavior.

## Setup recipes

Quick Setup's Setup recipes button offers two explicit proposals. Previewing or
cancelling a recipe changes nothing. The review lists every changed field's
current draft value and proposed value, marking fields that replace pending
edits. Queue changes adds the reviewed proposal to the shared draft; Apply is
still the only server update. A changed, replaced, frozen, or read-only draft
cannot queue an outdated proposal.

Recommended setup sets only these canonical values:

| Fields | Values |
| --- | --- |
| enableBlueprints, enableResearch | true |
| researchAccess, analyzerSetup | TIERED |
| progressionPreset | TIERED_RESEARCH_AND_CRAFTING |
| ammoResearch, attachmentResearch | PROFILE |
| balancePreset | BALANCED |
| researchCostMode | POINTS_AND_ITEMS |
| enableGunsmith, enableBallisticsResearcher | true |

Simpler Stations sets only `researchAccess=SINGLE` and `analyzerSetup=SINGLE`.
It leaves disabled research disabled, preserves general Workbench levels, and
retains `singleAnalyzerDataTier`. The preview explains that single-Analyzer
weapon rewards follow the weapon tier while Research Data keeps its configured
reward tier.

Neither recipe edits the currency, active profile, starting unlocks, exact item
rules, blacklists, reward amounts, villager economy, or stored fragment progress.
Dormant Custom values are retained. The screen identifies the retained currency
and profile and warns about configured crafting exceptions and authored rules.

Setup match is derived from each recipe's allowlist; Custom means neither list
matches. It is not a saved setting and does not claim that unrelated Advanced
values match defaults. A repeated queue is a no-op. `SettingsRecipes` owns the
allowlists and immutable preview; `SettingsEditSession.setAll` checks every
field before changing the pending map. Normal server validation still applies
to the combined draft on Apply.
