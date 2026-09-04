package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Locale;

/** Explainable authority that supplied a resolved crafting assignment. */
public enum BlueprintCraftingPolicySource {
    EXACT_RULE,
    AUTHORED_RULE,
    AUTHORED_BAND,
    AUTOMATIC_PERCENTILE,
    AUTOMATIC_REVIEW_FALLBACK,
    LINKED_WEAPON,
    CONFIG_OVERRIDE,
    CATEGORY_DEFAULT,
    PROFILE_FALLBACK,
    MIGRATED_COMPATIBILITY;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
