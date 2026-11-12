# Settings guide

This guide covers the NeoForge 1.3.10 settings screens. Forge 1.20.1 retains
its existing Fzzy Config interface and section names.

Open the mod's settings from the Mods screen. **Personal Settings** changes
your own controls and Tech Tree display. While connected to a world,
**World & Server Settings** opens **Quick Setup**. Shared settings affect everyone
in that world; editing them requires operator permission level 2.

At the main menu, **Local Defaults** edits local configuration through the native
Fzzy editor. It does not change a connected remote server.

## Start with Quick Setup

Quick Setup covers blueprint progression, research, ammo and attachment
research, Research Bench access, crafting tiers, discovery pacing, research
costs, and the two villager professions. Use **Advanced Settings** whenever you
need a more specific rule. Both views share the same pending changes.

To enable the bundled ammo and attachment Tech Trees, set **Ammo research** and
**Attachment research** to **Enabled**, keep **Research** enabled, and choose
**Apply**. No additional datapack or reload is needed. **Use Profile** follows
your active datapack; the bundled profile enables weapon research only.

**Gameplay Summary** separates the applied snapshot from your pending preview.
Select a setting in the summary to open its Advanced category. Switching screens
does not save changes: **Apply** asks the server to save the draft, and **Discard**
returns it to the last snapshot. If another operator or a reload changes the
server while you edit, review the fresh values before applying your proposal.

**Recommended setup** and **Simpler Stations** show exactly which values they
would change before adding them to your draft. Simpler Stations selects a single
Research Bench and a single Analyzer; it does not change crafting Workbench tiers.
Neither recipe automatically applies itself or resets unrelated custom settings.

## Find a detailed setting

Advanced Settings has seven categories and search across them:

| Category | What it controls |
| --- | --- |
| Blueprint loot and discovery | Loot chance, quantities, discovery pacing and visibility. |
| Research, stations and crafting | Research access, category research, purchases, tiers and crafting rules. |
| Currency, rewards and Research Data | Physical Scrap or banked RP, rewards and Data behavior. |
| Blueprint Analyzer and fragments | Analyzer setup, recovery, recycling and ordinary loot fragments. |
| Villager trading | Professions, stock, prices, currency and trade categories. |
| Starting unlocks and exceptions | Starting knowledge and recipes that do not require blueprints. |
| Datapacks and compatibility | Profiles, exclusions and external workstation mappings. |

Inactive controls explain which mode or switch enables them. Unsupported
TaCZ Magazines controls remain saved but are unavailable on NeoForge.

## Personal preferences

Personal Settings exposes research confirmation, confirmation hold time,
currency notifications, reduced motion, grid visibility, minimap mode and
context labels. It has its own Apply and Discard controls. Appearance resets
show their scope before updating your draft; hidden legacy layout values remain
saved. Personal choices never change server progression.

## Enter values in familiar units

- Blueprint Loot Chance uses percentages: enter `20` for a 20% chance.
- Confirmation Hold Time uses seconds: enter `0.6` for a short hold.
- Research Discount Amount uses percentages when its type is Percentage:
  enter `25` for 25%. With Currency Units selected, enter a whole number of
  Scrap or RP. Changing the type changes how the existing stored value is used;
  review the amount after switching types.
- Large counts, such as banked RP caps and fragment set sizes, use number boxes.
- Bounded whole-number shares and small counts use sliders.
- Presets and multi-option crafting rules open a list of choices, so you can
  compare them without cycling through intermediate settings.
- Blueprint lists support searching by name or resource ID. Resource IDs such
  as `tacz:ak47` identify pack content; the shared backing gun item is not a
  weapon blueprint ID.

Saved TOML values remain compatible with existing files. Blueprint chance is
still stored as a fraction (`0.2`), hold time as milliseconds (`600`), and a
percentage discount as basis points (`2500` for 25%). The screen converts these
units for editing. Do not copy the screen's number directly into a differently
scaled TOML field.

## Analyzer and fragment settings

The found-weapon action setting selects analysis, direct currency recycling,
or player choice. It does **not** set analysis odds or guarantee a blueprint.
The bundled tier matrix can produce blueprints, fragments, or no reward;
no-reward chances apply only when the weapon is above the Analyzer's tier.

Datapacks configure each Analyzer/weapon tier pair, blueprint/fragment/no-reward
weights, fragment quantity ranges, costs, eligibility, and reconstruction
requirements. See the [default tier matrix](analyzer-presets-and-balancing.md)
and [policy fields](analyzer-policy-authoring.md). A guaranteed legacy mode
remains available as a profile override.

Ordinary loot fragments and analysis fragments are different item types.
The Ordinary Fragment Result preset and detailed fragment controls affect
ordinary loot fragments. Fragments earned from weapon or Research Data analysis
use the shared reconstruction rules in datapacks. See
[Research Data policies](research-data-policies.md) and
[analysis fragment storage](analysis-fragment-storage.md).

## Optional magazine crafting

TaCZ Magazines integration is available only on the Forge branch. It is not
enabled by the NeoForge settings screens. The [magazine integration guide](tacz-magazines-integration.md)
describes the existing Forge controls; compatibility with TaCZ 1.2.0 requires
separate validation.

## Currency and Research Data

Physical Item currency uses Scrap by default. Banked Research Points saves a
balance on each player. Changing currency mode or item ID does not convert
existing items or saved balances. The banked cap only applies to banked RP.

Currency loot, gameplay rewards, and Research Data loot have separate switches.
Disabling Data processing does not automatically remove Data from loot.
In Data analysis mode, turning off already-learned targets excludes those
weapons; it blocks processing only when no eligible target remains. Per-item
Data policies can override the default target options.
