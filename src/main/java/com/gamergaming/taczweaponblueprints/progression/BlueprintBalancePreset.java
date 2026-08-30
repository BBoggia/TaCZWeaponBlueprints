package com.gamergaming.taczweaponblueprints.progression;

import java.util.Locale;

/**
 * Reversible discovery-pacing overlays for servers that do not want to tune
 * the individual loot and undiscovered-visibility settings by hand.
 */
public enum BlueprintBalancePreset {
    CUSTOM,
    ACCESSIBLE,
    BALANCED,
    SCARCE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BlueprintBalancePreset parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("balance preset cannot be null");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown balance preset " + value, exception);
        }
    }
}
