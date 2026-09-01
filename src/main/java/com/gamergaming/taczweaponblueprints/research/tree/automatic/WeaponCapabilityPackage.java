package com.gamergaming.taczweaponblueprints.research.tree.automatic;

/** Explainable v3 capability packages used for progression and branch identity. */
public enum WeaponCapabilityPackage {
    LETHALITY("lethality", true),
    SUSTAINED_PRESSURE("sustained_pressure", true),
    PRECISION_REACH("precision_reach", true),
    AREA_CONTROL("area_control", true),
    HANDLING("handling", false),
    VERSATILITY("versatility", false);

    private final String serializedName;
    private final boolean combat;

    WeaponCapabilityPackage(String serializedName, boolean combat) {
        this.serializedName = serializedName;
        this.combat = combat;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean combat() {
        return combat;
    }
}
