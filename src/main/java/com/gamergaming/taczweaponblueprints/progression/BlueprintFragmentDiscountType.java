package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;

/** Config-facing active discount choices; NONE is represented by a disabled/reconstruction mode. */
public enum BlueprintFragmentDiscountType implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    FIXED,
    PERCENTAGE;

    public BlueprintFragmentDiscount create(int value) {
        return this == FIXED
                ? BlueprintFragmentDiscount.fixed(value)
                : BlueprintFragmentDiscount.percentage(value);
    }
    @Override public String prefix() { return "config.taczweaponblueprints.blueprint.fragmentDiscountType"; }
}
