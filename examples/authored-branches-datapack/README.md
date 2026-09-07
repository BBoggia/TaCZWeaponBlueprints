# Nested Tech Tree example

This pack demonstrates five named regions around eight TaCZ blueprints. It uses
real prerequisite rules separately from visual membership:

- Foundation owns Glock 17 and the later M4A1 milestone.
- Submachine guns and Rifles are sibling specialties inside Foundation.
- Controlled fire and Rapid fire are separate children of Submachine guns.
- SKS accepts Uzi **or** AK-47 across the two specialties.
- M4A1 requires HK MP5A5 **or** Vector, plus HK416D.

Copy this directory into a test world's `datapacks` folder, set
`activeResearchProfile = "example:nested"` in the mod's blueprint configuration,
and reload the configuration and datapacks (or restart the server). Use the
Research Bench to view the tree. `/gg research status` and `/gg research export`
show the loaded structure. The eight nodes cost 2 Research Points each; this is
a small authoring example, not a balanced replacement progression.

The pack requires the corresponding TaCZ blueprint IDs to exist in the live
catalog. Unlisted blueprints are omitted from this example's research tree.
Remove the pack and restore your previous active profile to return to your
regular progression. Use a test world when comparing profiles.

Read the [branch authoring contract](../../docs/research-tree-authored-branches.md)
for format versions, limits, disclosure rules, and validation errors. Tree and
entry files use format 3; research profiles use format 1 and rules use format 2.
This is a datapack; keep `pack.mcmeta` at the root when copying or zipping it.
