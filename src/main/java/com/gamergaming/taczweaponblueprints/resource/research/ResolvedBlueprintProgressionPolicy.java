package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

/** Immutable, explainable research policy for one included blueprint. */
public record ResolvedBlueprintProgressionPolicy(
        ResourceLocation profileId,
        ResourceLocation blueprintId,
        ResearchWorkbenchTier researchWorkbenchTier,
        BlueprintFragmentPolicy fragments,
        ProgressionGateRequirements gates,
        TierSource tierSource,
        Optional<ResourceLocation> selectedProgressionRuleId,
        MatchSpecificity ruleSpecificity,
        Optional<Integer> automaticScore,
        Optional<Integer> automaticPercentileBasisPoints,
        boolean reviewRequired,
        boolean exactFragmentThreshold) {
    public ResolvedBlueprintProgressionPolicy {
        if (profileId == null || blueprintId == null || researchWorkbenchTier == null
                || fragments == null || gates == null || tierSource == null
                || ruleSpecificity == null) {
            throw new IllegalArgumentException("resolved blueprint progression policy is invalid");
        }
        selectedProgressionRuleId = selectedProgressionRuleId == null
                ? Optional.empty()
                : selectedProgressionRuleId;
        automaticScore = automaticScore == null ? Optional.empty() : automaticScore;
        automaticPercentileBasisPoints = automaticPercentileBasisPoints == null
                ? Optional.empty()
                : automaticPercentileBasisPoints;
        automaticScore.ifPresent(score -> {
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("automatic score is out of bounds");
            }
        });
        automaticPercentileBasisPoints.ifPresent(percentile -> {
            if (percentile < 0 || percentile > AutomaticWorkbenchTierPercentiles.BASIS_POINTS) {
                throw new IllegalArgumentException("automatic percentile is out of bounds");
            }
        });
        if (automaticPercentileBasisPoints.isPresent() && automaticScore.isEmpty()) {
            throw new IllegalArgumentException("automatic percentile requires a score");
        }
        if (tierSource == TierSource.AUTOMATIC_PERCENTILE
                && automaticPercentileBasisPoints.isEmpty()) {
            throw new IllegalArgumentException("automatic tier source requires percentile evidence");
        }
        if (tierSource == TierSource.REVIEW_FALLBACK && !reviewRequired) {
            throw new IllegalArgumentException("review fallback must remain visible in diagnostics");
        }
        if (selectedProgressionRuleId.isPresent()
                != (ruleSpecificity != MatchSpecificity.NONE)) {
            throw new IllegalArgumentException(
                    "progression-rule identity and specificity must be present together");
        }
    }

    public enum TierSource {
        EXACT_RULE,
        AUTHORED_RULE,
        AUTHORED_BAND,
        AUTOMATIC_PERCENTILE,
        FALLBACK,
        REVIEW_FALLBACK
    }
}
