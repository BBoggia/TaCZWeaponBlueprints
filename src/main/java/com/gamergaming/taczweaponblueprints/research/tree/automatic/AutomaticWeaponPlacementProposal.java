package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;

/** Auditable tier/level/order suggestion for one scored weapon. */
public record AutomaticWeaponPlacementProposal(
        String blueprintId,
        int mechanicalScore,
        int confidence,
        ProgressionPosition position,
        ProgressionCoordinate progressionCoordinate,
        int levelsPerTier,
        String formulaVersion,
        String referenceVersion,
        String placementVersion,
        List<String> reviewReasons) {
    public AutomaticWeaponPlacementProposal {
        if (!validText(blueprintId)
                || mechanicalScore < 0 || mechanicalScore > ResearchTechTreeContract.SCORE_MAX
                || confidence < 0 || confidence > ResearchTechTreeContract.SCORE_MAX
                || position == null
                || progressionCoordinate == null
                || levelsPerTier < ResearchTechTreeContract.MIN_LEVELS_PER_TIER
                || levelsPerTier > ResearchTechTreeContract.MAX_LEVELS_PER_TIER
                || !validText(formulaVersion)
                || !validText(referenceVersion)
                || !validText(placementVersion)
                || reviewReasons == null
                || reviewReasons.stream().anyMatch(reason -> !validText(reason))) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement proposal is invalid");
        }
        if (position.tier()
                        != ResearchTechTreeContract.Tier.forScore(mechanicalScore)
                || position.level()
                        != ResearchTechTreeContract.levelForScore(
                                mechanicalScore, levelsPerTier)) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement proposal does not match its mechanical score");
        }
        if (progressionCoordinate.siblingOrder() != position.siblingOrder()) {
            throw new IllegalArgumentException(
                    "Automatic weapon progression coordinates disagree on sibling order");
        }
        reviewReasons = reviewReasons.stream().distinct().sorted().toList();
    }

    /** Compatibility constructor for score-bucket proposals authored before Phase 5. */
    public AutomaticWeaponPlacementProposal(
            String blueprintId,
            int mechanicalScore,
            int confidence,
            ProgressionPosition position,
            int levelsPerTier,
            String formulaVersion,
            String referenceVersion,
            String placementVersion,
            List<String> reviewReasons) {
        this(
                blueprintId,
                mechanicalScore,
                confidence,
                position,
                ResearchTechTreeContract.legacyProgressionCoordinate(position),
                levelsPerTier,
                formulaVersion,
                referenceVersion,
                placementVersion,
                reviewReasons);
    }

    public AutomaticWeaponPlacementProposal withProgressionCoordinate(
            ProgressionCoordinate coordinate) {
        return new AutomaticWeaponPlacementProposal(
                blueprintId,
                mechanicalScore,
                confidence,
                position,
                coordinate,
                levelsPerTier,
                formulaVersion,
                referenceVersion,
                placementVersion,
                reviewReasons);
    }

    public boolean reviewRequired() {
        return !reviewReasons.isEmpty();
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }
}
