package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Objects;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import net.minecraft.resources.ResourceLocation;

/** Policy evidence only: CONFIGURED does not assert player/inventory eligibility. */
public record AnalyzerPolicyQuote(ResourceLocation profileId, ResourceLocation blueprintId,
        AnalyzerTier analyzerTier, BlueprintAnalyzerPolicy.Origin origin, Status status,
        Optional<AnalyzerTier> weaponTier, String tierSource, boolean reviewRequired,
        Optional<ResourceLocation> selectedRuleId, BlueprintResearchTarget.MatchSpecificity specificity,
        Optional<BlueprintReverseEngineeringPolicy> attempt, Optional<AnalyzerOutcomeTable> outcomes,
        int fragmentThreshold, int retainedProgressCap) {
    public AnalyzerPolicyQuote {
        Objects.requireNonNull(profileId); Objects.requireNonNull(blueprintId); Objects.requireNonNull(analyzerTier);
        Objects.requireNonNull(origin); Objects.requireNonNull(status); Objects.requireNonNull(weaponTier);
        Objects.requireNonNull(tierSource); Objects.requireNonNull(selectedRuleId); Objects.requireNonNull(specificity);
        Objects.requireNonNull(attempt); Objects.requireNonNull(outcomes);
        if (fragmentThreshold < 0 || retainedProgressCap < fragmentThreshold) throw new IllegalArgumentException("invalid analysis progress bounds");
        if (status == Status.CONFIGURED && (weaponTier.isEmpty() || attempt.isEmpty() || outcomes.isEmpty())) {
            throw new IllegalArgumentException("configured analysis quote is incomplete");
        }
    }
    public enum Status {
        LEGACY, CONFIGURED, DISABLED, CONTENT_UNAVAILABLE, TIER_UNAVAILABLE,
        STALE_PROGRESSION, STATION_TIER_REQUIRED, ORIGIN_DISABLED, DIRECT_RECOVERY_ONLY
    }
}
