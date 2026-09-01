package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

/** One auditable v2-versus-v3 shadow result. */
public record WeaponCapabilityComparison(
        String blueprintId,
        int mechanicalV2Score,
        int capabilityV3Score,
        int scoreDelta,
        Tier mechanicalV2Tier,
        Tier capabilityV3Tier,
        int tierDelta,
        int capabilityConfidence) {
    public WeaponCapabilityComparison {
        if (blueprintId == null || blueprintId.isBlank()
                || !score(mechanicalV2Score) || !score(capabilityV3Score)
                || scoreDelta != capabilityV3Score - mechanicalV2Score
                || mechanicalV2Tier == null || capabilityV3Tier == null
                || tierDelta != capabilityV3Tier.ordinal() - mechanicalV2Tier.ordinal()
                || !score(capabilityConfidence)) {
            throw new IllegalArgumentException("Weapon capability comparison is invalid");
        }
    }

    public static WeaponCapabilityComparison compare(
            WeaponMechanicalScore mechanical,
            WeaponCapabilityScore capability) {
        if (mechanical == null || capability == null
                || !mechanical.evidence().blueprintId().equals(
                        capability.evidence().blueprintId())) {
            throw new IllegalArgumentException("Capability comparison inputs are invalid");
        }
        Tier mechanicalTier = mechanical.rating().suggestedTier();
        Tier capabilityTier = capability.suggestedTier();
        return new WeaponCapabilityComparison(
                mechanical.evidence().blueprintId(),
                mechanical.score(),
                capability.progressionScore(),
                capability.progressionScore() - mechanical.score(),
                mechanicalTier,
                capabilityTier,
                capabilityTier.ordinal() - mechanicalTier.ordinal(),
                capability.confidence());
    }

    private static boolean score(int value) {
        return value >= 0 && value <= 100;
    }
}
