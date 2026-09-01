package com.gamergaming.taczweaponblueprints.progression;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

/** Server policy for verified loot-generated weapons in the Blueprint Analyzer. */
public enum FoundWeaponRecoveryMode implements EnumTranslatable {
    /** Preserve the existing protected reverse-engineered blueprint behavior. */
    PROTECTED_BLUEPRINT_ONLY(true, false, false),
    /** Allow a verified found weapon to create a blueprint that can be recycled. */
    RECYCLABLE_BLUEPRINT(true, true, false),
    /** Offer only direct conversion of a verified found weapon into Research Points. */
    DIRECT_RP_ONLY(false, false, true),
    /** Let the player choose between a recyclable blueprint and direct RP. */
    PLAYER_CHOICE(true, true, true);

    private final boolean blueprintExtractionEnabled;
    private final boolean foundBlueprintRecyclable;
    private final boolean directPointsEnabled;

    FoundWeaponRecoveryMode(
            boolean blueprintExtractionEnabled,
            boolean foundBlueprintRecyclable,
            boolean directPointsEnabled) {
        this.blueprintExtractionEnabled = blueprintExtractionEnabled;
        this.foundBlueprintRecyclable = foundBlueprintRecyclable;
        this.directPointsEnabled = directPointsEnabled;
    }

    public boolean blueprintExtractionEnabled() {
        return blueprintExtractionEnabled;
    }

    public boolean foundBlueprintRecyclable() {
        return foundBlueprintRecyclable;
    }

    public boolean directPointsEnabled() {
        return directPointsEnabled;
    }

    @Override
    public String prefix() {
        return "config.taczweaponblueprints.blueprint.foundWeaponRecoveryMode";
    }
}
