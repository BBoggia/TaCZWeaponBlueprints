package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Locale;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

/** Versioned vertical-progression model selected by an automatic-placement profile. */
public enum AutomaticWeaponScoringModel {
    MECHANICAL_V2(
            "mechanical_v2",
            ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
            ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION),
    CAPABILITY_V3(
            "capability_v3",
            ResearchTechTreeContract.CAPABILITY_FORMULA_VERSION,
            ResearchTechTreeContract.CAPABILITY_REFERENCE_VERSION);

    private final String serializedName;
    private final String formulaVersion;
    private final String referenceVersion;

    AutomaticWeaponScoringModel(
            String serializedName,
            String formulaVersion,
            String referenceVersion) {
        this.serializedName = serializedName;
        this.formulaVersion = formulaVersion;
        this.referenceVersion = referenceVersion;
    }

    public String serializedName() {
        return serializedName;
    }

    public String formulaVersion() {
        return formulaVersion;
    }

    public String referenceVersion() {
        return referenceVersion;
    }

    public static AutomaticWeaponScoringModel decode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Automatic weapon scoring model cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AutomaticWeaponScoringModel model : values()) {
            if (model.serializedName.equals(normalized)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown automatic weapon scoring model " + value);
    }
}
