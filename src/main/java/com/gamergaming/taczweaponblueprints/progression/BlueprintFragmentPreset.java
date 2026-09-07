package com.gamergaming.taczweaponblueprints.progression;

/** High-level Blueprint Fragment behavior presented in normal settings. */
public enum BlueprintFragmentPreset implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    DISABLED,
    TARGETED_RESEARCH_BOOST,
    RECONSTRUCT_BLUEPRINT,
    CUSTOM;
    @Override public String prefix() { return "config.taczweaponblueprints.blueprint.fragmentPreset"; }
}
