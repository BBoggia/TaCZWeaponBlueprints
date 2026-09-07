package com.gamergaming.taczweaponblueprints.progression.analyzer;

import java.util.Locale;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;

/** Stable item/save identities, independent of station, owner and current odds. */
public enum AnalysisRewardClass {
    PROTECTED_PREREQUISITES(false, false),
    PROTECTED_BYPASS(false, true),
    RECYCLABLE_PREREQUISITES(true, false),
    RECYCLABLE_BYPASS(true, true);

    private final boolean recyclable;
    private final boolean bypass;
    AnalysisRewardClass(boolean recyclable, boolean bypass) {
        this.recyclable = recyclable;
        this.bypass = bypass;
    }
    public String serializedName() { return name().toLowerCase(Locale.ROOT); }
    public boolean recyclable() { return recyclable; }
    public PhysicalBlueprintLearningMode learningMode() {
        return bypass ? PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES
                : PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES;
    }
    public static Optional<AnalysisRewardClass> parse(String name) {
        for (var value : values()) if (value.serializedName().equals(name)) return Optional.of(value);
        return Optional.empty();
    }
    public static AnalysisRewardClass of(boolean recyclable, PhysicalBlueprintLearningMode learning) {
        if (learning == null || !learning.learningPermitted()) throw new IllegalArgumentException("analysis rewards must permit learning");
        return recyclable ? (learning == PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES ? RECYCLABLE_BYPASS : RECYCLABLE_PREREQUISITES)
                : (learning == PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES ? PROTECTED_BYPASS : PROTECTED_PREREQUISITES);
    }
    /** Current policy can restrict an acquired reward, never grant it new permissions. */
    public AnalysisRewardClass restrict(boolean allowRecycling, PhysicalBlueprintLearningMode learning) {
        if (learning == null || !learning.learningPermitted()) throw new IllegalArgumentException("analysis reward learning is disabled");
        return of(recyclable && allowRecycling,
                bypass && learning == PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES
                        ? learning : PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES);
    }
    public BlueprintProvenance provenance(BlueprintProvenance.Source source) {
        return new BlueprintProvenance(BlueprintProvenance.CURRENT_FORMAT, source, recyclable, learningMode());
    }
}
