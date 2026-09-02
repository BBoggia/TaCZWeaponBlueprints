package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

/** One reproducible authoring recommendation and all evidence needed to review it. */
public record WeaponRatingSuggestion(
        TaCZGunStats stats,
        Double effectiveDamage,
        Double sustainedDamagePerSecond,
        int combatScore,
        int utilityScore,
        int appealScore,
        boolean appealReviewed,
        String appealReason,
        int mechanicalScore,
        int weightedScore,
        ResearchTechTreeContract.Tier suggestedTier,
        Map<String, Integer> metricPercentiles,
        List<String> warnings) {

    public WeaponRatingSuggestion {
        if (stats == null || suggestedTier == null) {
            throw new IllegalArgumentException("Rating suggestion evidence and tier cannot be null");
        }
        metricPercentiles = Map.copyOf(metricPercentiles);
        warnings = List.copyOf(warnings);
    }
}
