package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchModel;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchAnalyzer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponRoleSignature;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable pre-topology partition of every gun in one catalog.
 *
 * <p>Eligible proposals retain their score-derived legacy coordinates here.
 * Semantic layer assignment belongs to {@link AutomaticWeaponCandidatePositioner}.
 */
public record AutomaticWeaponCandidateClassification(
        ResourceLocation treeId,
        AutomaticPlacementMode mode,
        AutomaticWeaponPlacementPolicy basePolicy,
        long catalogRevision,
        long researchRevision,
        int catalogWeaponCount,
        Map<String, AutomaticWeaponPlacementProposal> eligibleProposals,
        Map<String, AutomaticWeaponRoleSignature> roleSignatures,
        Map<String, AutomaticWeaponRoleSignature> authoredRoleSignatures,
        AutomaticWeaponBranchModel branchModel,
        Map<String, String> excludedAutomaticCandidates,
        Set<String> authoredBlueprintIds,
        Set<String> unplacedBlueprintIds) {
    public AutomaticWeaponCandidateClassification {
        if (treeId == null || mode == null || basePolicy == null
                || catalogRevision < 0L || researchRevision < 0L
                || catalogWeaponCount < 0
                || catalogWeaponCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || eligibleProposals == null || roleSignatures == null
                || authoredRoleSignatures == null || branchModel == null
                || excludedAutomaticCandidates == null
                || authoredBlueprintIds == null || unplacedBlueprintIds == null) {
            throw new IllegalArgumentException("Automatic weapon candidate classification is invalid");
        }
        eligibleProposals = immutableMap(eligibleProposals);
        roleSignatures = immutableMap(roleSignatures);
        authoredRoleSignatures = immutableMap(authoredRoleSignatures);
        excludedAutomaticCandidates = immutableMap(excludedAutomaticCandidates);
        authoredBlueprintIds = immutableSet(authoredBlueprintIds);
        unplacedBlueprintIds = immutableSet(unplacedBlueprintIds);

        Set<String> all = new LinkedHashSet<>();
        addDisjoint(all, eligibleProposals.keySet());
        addDisjoint(all, excludedAutomaticCandidates.keySet());
        addDisjoint(all, authoredBlueprintIds);
        addDisjoint(all, unplacedBlueprintIds);
        if (all.size() != catalogWeaponCount
                || !roleSignatures.keySet().equals(eligibleProposals.keySet())
                || !authoredRoleSignatures.keySet().equals(authoredBlueprintIds)
                || !branchModel.matches(roleSignatures, authoredRoleSignatures)
                || !canonicalBranchModel(
                        roleSignatures, authoredRoleSignatures, branchModel)
                || (!mode.assignsPlacement() && !eligibleProposals.isEmpty())
                || excludedAutomaticCandidates.values().stream().anyMatch(String::isBlank)
                || eligibleProposals.entrySet().stream().anyMatch(entry ->
                        !entry.getKey().equals(entry.getValue().blueprintId())
                                || (entry.getValue().reviewRequired()
                                        && !basePolicy.reviewHandling().assignsPlacement())
                                || entry.getValue().levelsPerTier() != basePolicy.levelsPerTier()
                                || !ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION.equals(
                                        entry.getValue().placementVersion())
                                || !entry.getValue().progressionCoordinate().equals(
                                        ResearchTechTreeContract.legacyProgressionCoordinate(
                                                entry.getValue().position())))
                || !validRoleSignatures(eligibleProposals, roleSignatures)) {
            throw new IllegalArgumentException(
                    "Automatic weapon candidate classification is inconsistent");
        }
    }

    public int automaticCandidateCount() {
        return eligibleProposals.size() + excludedAutomaticCandidates.size();
    }

    public Optional<AutomaticWeaponPlacementProposal> eligibleProposal(
            ResourceLocation blueprintId) {
        return blueprintId == null
                ? Optional.empty()
                : Optional.ofNullable(eligibleProposals.get(blueprintId.toString()));
    }

    public Optional<AutomaticWeaponRoleSignature> roleSignature(
            ResourceLocation blueprintId) {
        return blueprintId == null
                ? Optional.empty()
                : Optional.ofNullable(roleSignatures.get(blueprintId.toString()));
    }

    public Optional<AutomaticWeaponRoleSignature> authoredRoleSignature(
            ResourceLocation blueprintId) {
        return blueprintId == null
                ? Optional.empty()
                : Optional.ofNullable(authoredRoleSignatures.get(blueprintId.toString()));
    }

    private static boolean canonicalBranchModel(
            Map<String, AutomaticWeaponRoleSignature> signatures,
            Map<String, AutomaticWeaponRoleSignature> authoredSignatures,
            AutomaticWeaponBranchModel model) {
        if (signatures.isEmpty()) {
            return model.equals(AutomaticWeaponBranchModel.EMPTY);
        }
        return model.equals(new AutomaticWeaponBranchAnalyzer().discover(
                signatures, authoredSignatures, model.branchLimit()));
    }

    private static void addDisjoint(Set<String> all, Set<String> values) {
        for (String value : values) {
            if (!all.add(value)) {
                throw new IllegalArgumentException(
                        "Automatic weapon candidate categories overlap at " + value);
            }
        }
    }

    private static boolean validRoleSignatures(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, AutomaticWeaponRoleSignature> signatures) {
        for (Map.Entry<String, AutomaticWeaponRoleSignature> entry : signatures.entrySet()) {
            AutomaticWeaponPlacementProposal proposal = proposals.get(entry.getKey());
            AutomaticWeaponRoleSignature signature = entry.getValue();
            if (proposal == null
                    || !entry.getKey().equals(signature.blueprintId())
                    || proposal.mechanicalScore() != signature.mechanicalScore()
                    || proposal.confidence() != signature.confidence()) {
                return false;
            }
        }
        return true;
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        Map<String, T> copy = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            validateId(entry.getKey());
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Automatic weapon candidate map contains null");
            }
            copy.put(entry.getKey(), entry.getValue());
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableSet(Set<String> source) {
        Set<String> copy = new LinkedHashSet<>();
        source.stream().sorted().forEach(value -> {
            validateId(value);
            copy.add(value);
        });
        return Collections.unmodifiableSet(copy);
    }

    private static void validateId(String value) {
        if (value == null || ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException("Automatic weapon candidate ID is invalid");
        }
    }
}
