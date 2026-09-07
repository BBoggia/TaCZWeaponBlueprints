# Settings guide

**Personal Settings** changes your own controls and Tech Tree display.
**Server Settings** changes progression for the server; multiplayer changes
require operator permission. A disabled control explains which mode or switch
must be selected before you can edit it.

In multiplayer, Server Settings displays the server’s values. Fzzy Config also
saves these shared settings locally; Personal Settings is a separate file.

## Find the right setting

| Section | What it controls |
| --- | --- |
| General Progression | Enable blueprints, discovery, the Journal, and research. |
| Discovery and Loot | Choose a preset or customize blueprint loot chances and quantities. |
| Research Purchases | Choose currency/material costs and whether research learns a recipe or creates a blueprint. |
| Currency and Rewards | Choose physical Scrap or banked RP, loot supply, and gameplay rewards. |
| Research Data | Choose Data results and whether learned or undiscovered weapons can be targets. |
| Research Progression | Choose Research Bench tier rules and the result of ordinary fragment sets. |
| Crafting Access | Choose Workbench level rules for ammo and attachments. |
| Blueprint Analyzer | Choose duplicate-blueprint recycling and the actions offered for found weapons. |
| Starting Access | Grant starting blueprints or allow selected recipes without learning them. |
| Datapacks and Exclusions | Select a profile and exclude specific blueprint IDs. |
| Advanced Tier Settings | Tune automatic tier boundaries, custom tier switches, and other workstations. |
| Advanced Crafting Rules | Allow or block crafting for categories, subgroups, or individual items. |
| Ordinary Loot Fragments | Customize ordinary fragment set sizes and research discounts. |

The advanced sections start collapsed. Personal Settings keeps legacy layout
controls in their own collapsed section because they do not affect the current
Tech Tree. Restore Tree Appearance resets camera motion, the grid, the minimap,
and saved legacy layout values; it does not reset confirmation controls.

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
The Ordinary Fragment Result preset and Ordinary Loot Fragments section affect
ordinary loot fragments. Fragments earned from weapon or Research Data analysis
use the shared reconstruction rules in datapacks. See
[Research Data policies](research-data-policies.md) and
[analysis fragment storage](analysis-fragment-storage.md).

## Currency and Research Data

Physical Item currency uses Scrap by default. Banked Research Points saves a
balance on each player. Changing currency mode or item ID does not convert
existing items or saved balances. The banked cap only applies to banked RP.

Currency loot, gameplay rewards, and Research Data loot have separate switches.
Disabling Data processing does not automatically remove Data from loot.
In Data analysis mode, turning off already-learned targets excludes those
weapons; it blocks processing only when no eligible target remains. Per-item
Data policies can override the default target options.
