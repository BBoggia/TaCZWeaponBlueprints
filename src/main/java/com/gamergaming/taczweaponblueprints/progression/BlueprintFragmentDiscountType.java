package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;

/** Config-facing active discount choices; NONE is represented by a disabled/reconstruction mode. */
public enum BlueprintFragmentDiscountType {
    FIXED,
    PERCENTAGE;

    public BlueprintFragmentDiscount create(int value) {
        return this == FIXED
                ? BlueprintFragmentDiscount.fixed(value)
                : BlueprintFragmentDiscount.percentage(value);
    }
}
