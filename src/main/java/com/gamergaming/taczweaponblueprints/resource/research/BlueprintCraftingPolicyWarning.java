package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Locale;

/** Bounded diagnostic conditions retained with a crafting assignment. */
public enum BlueprintCraftingPolicyWarning {
    AMMO_WITHOUT_LINKED_WEAPON,
    AMMO_WITHOUT_TIERED_LINKED_WEAPON,
    AMBIGUOUS_AMMO_LINK,
    UNKNOWN_ATTACHMENT_TYPE,
    AUTOMATIC_REVIEW_FALLBACK,
    AUTHORED_OMITTED_FALLBACK,
    MIGRATED_COMPATIBILITY;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
