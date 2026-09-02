package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeLayerWidthResolver;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchAnalyzer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchLayerPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;

/** Converts a complete candidate classification into a ranked topology input. */
public final class AutomaticWeaponCandidatePositioner {
    private AutomaticWeaponCandidatePositioner() {
    }

    public static AutomaticWeaponPlacementCandidateSnapshot position(
            AutomaticWeaponCandidateClassification classification,
            ResearchTechTreeDefinition tree) {
        if (classification == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon candidate positioner requires a classification");
        }
        int topologyWeaponCount = Math.addExact(
                classification.authoredBlueprintIds().size(),
                classification.eligibleProposals().size());
        AutomaticWeaponPlacementPolicy placementPolicy = resolvePolicy(
                classification.basePolicy(), topologyWeaponCount, tree);
        int expectedBranchLimit = classification.eligibleProposals().isEmpty()
                ? 0
                : AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                        placementPolicy.maxNodesPerRank());
        if (classification.branchModel().branchLimit() != expectedBranchLimit) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch model does not match its resolved layer width");
        }
        Map<String, AutomaticWeaponPlacementProposal> positioned =
                new AutomaticWeaponBranchLayerPlanner().assign(
                        classification.eligibleProposals(),
                        classification.roleSignatures(),
                        classification.authoredRoleSignatures(),
                        classification.branchModel(),
                        placementPolicy);
        return new AutomaticWeaponPlacementCandidateSnapshot(
                classification.treeId(),
                classification.mode(),
                placementPolicy,
                classification.catalogRevision(),
                classification.researchRevision(),
                classification.catalogWeaponCount(),
                positioned,
                classification.excludedAutomaticCandidates(),
                classification.authoredBlueprintIds(),
                classification.unplacedBlueprintIds());
    }

    static AutomaticWeaponPlacementPolicy resolvePolicy(
            AutomaticWeaponPlacementPolicy basePolicy,
            int topologyWeaponCount,
            ResearchTechTreeDefinition tree) {
        if (basePolicy == null || topologyWeaponCount < 0) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement-policy inputs are invalid");
        }
        return tree != null
                && tree.format() >= ResearchTechTreeDefinition.CURRENT_FORMAT
                        ? basePolicy.withMaxNodesPerRank(
                                ResearchTechTreeLayerWidthResolver.resolve(
                                        tree.layout(), topologyWeaponCount))
                        : basePolicy;
    }
}
