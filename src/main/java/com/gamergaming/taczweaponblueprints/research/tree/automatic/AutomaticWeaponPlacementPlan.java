package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

/** Immutable result of one pure automatic placement proposal pass. */
public record AutomaticWeaponPlacementPlan(
        String placementVersion,
        int levelsPerTier,
        int reviewConfidenceThreshold,
        int candidateCount,
        Map<String, AutomaticWeaponPlacementProposal> proposals,
        Map<String, String> rejectedCandidates) {
    public static final AutomaticWeaponPlacementPlan EMPTY =
            new AutomaticWeaponPlacementPlan(
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    ResearchTechTreeContract.DEFAULT_LEVELS_PER_TIER,
                    AutomaticWeaponPlacementPolicy.DEFAULT_REVIEW_CONFIDENCE_THRESHOLD,
                    0,
                    Map.of(),
                    Map.of());

    public AutomaticWeaponPlacementPlan {
        if (placementVersion == null || placementVersion.isBlank()
                || levelsPerTier < ResearchTechTreeContract.MIN_LEVELS_PER_TIER
                || levelsPerTier > ResearchTechTreeContract.MAX_LEVELS_PER_TIER
                || reviewConfidenceThreshold < 0
                || reviewConfidenceThreshold > ResearchTechTreeContract.SCORE_MAX
                || candidateCount < 0
                || candidateCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || proposals == null || rejectedCandidates == null
                || candidateCount != proposals.size() + rejectedCandidates.size()) {
            throw new IllegalArgumentException("Automatic weapon placement plan is invalid");
        }
        proposals = immutableMap(proposals);
        rejectedCandidates = immutableMap(rejectedCandidates);
        if (!Collections.disjoint(proposals.keySet(), rejectedCandidates.keySet())
                || proposals.entrySet().stream().anyMatch(entry ->
                        !entry.getKey().equals(entry.getValue().blueprintId())
                                || !placementVersion.equals(entry.getValue().placementVersion())
                                || levelsPerTier != entry.getValue().levelsPerTier())) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement plan candidate maps are inconsistent");
        }
    }

    public int readyCount() {
        return (int) proposals.values().stream()
                .filter(proposal -> !proposal.reviewRequired())
                .count();
    }

    public int reviewRequiredCount() {
        return proposals.size() - readyCount();
    }

    public int count(Tier tier, int level) {
        if (tier == null || level < 0 || level >= levelsPerTier) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement distribution coordinate is invalid");
        }
        return (int) proposals.values().stream()
                .filter(proposal -> proposal.position().tier() == tier
                        && proposal.position().level() == level)
                .count();
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        LinkedHashMap<String, T> copy = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Automatic weapon placement plan map is invalid");
            }
            copy.put(entry.getKey(), entry.getValue());
        });
        return Collections.unmodifiableMap(copy);
    }
}
