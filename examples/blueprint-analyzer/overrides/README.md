# Blueprint Analyzer rule examples

Enable alongside a complete tiered policy for
`taczweaponblueprints:duplicate_recovery`, then reload or restart. Enabling
these partial rule overlays with the bundled legacy profile fails validation.
TaCZ AK-47 and M4A1 blueprints must exist in the catalog.

The rifle tag makes T1 analysis produce 2–4 fragments for a T3 target. The exact
AK-47 rule requires a T2 Analyzer and guarantees a blueprint at T2. Exact matches
win over tag matches even though this tag rule has higher numeric priority.
Only one Analyzer rule applies; its fields do not accumulate with lower matches.

Disable this pack before returning to a legacy Analyzer profile.
