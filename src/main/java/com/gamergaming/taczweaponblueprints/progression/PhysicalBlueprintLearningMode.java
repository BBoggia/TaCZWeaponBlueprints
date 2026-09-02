package com.gamergaming.taczweaponblueprints.progression;

/** Server policy for consuming a physical blueprint to learn its recipe. */
public enum PhysicalBlueprintLearningMode {
    BYPASS_TREE_PREREQUISITES(true, false),
    REQUIRE_TREE_PREREQUISITES(true, true),
    DISABLED(false, false);

    private final boolean learningPermitted;
    private final boolean prerequisitesRequired;

    PhysicalBlueprintLearningMode(
            boolean learningPermitted,
            boolean prerequisitesRequired) {
        this.learningPermitted = learningPermitted;
        this.prerequisitesRequired = prerequisitesRequired;
    }

    public boolean learningPermitted() {
        return learningPermitted;
    }

    public boolean prerequisitesRequired() {
        return prerequisitesRequired;
    }
}
