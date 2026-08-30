package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;

import net.minecraft.resources.ResourceLocation;

/**
 * Atomic positioned partition of every gun in one catalog for one Research Tech Tree.
 * Only {@link #eligibleProposals()} may replace a legacy fallback placement or
 * supply a placement for an otherwise unplaced, non-authored gun.
 */
public record AutomaticWeaponPlacementCandidateSnapshot(
        ResourceLocation treeId,
        AutomaticPlacementMode mode,
        AutomaticWeaponPlacementPolicy policy,
        long catalogRevision,
        long researchRevision,
        int catalogWeaponCount,
        Map<String, AutomaticWeaponPlacementProposal> eligibleProposals,
        Map<String, String> excludedAutomaticCandidates,
        Set<String> authoredBlueprintIds,
        Set<String> unplacedBlueprintIds) {
    public AutomaticWeaponPlacementCandidateSnapshot {
        if (treeId == null || mode == null || policy == null
                || catalogRevision < 0L || researchRevision < 0L
                || catalogWeaponCount < 0
                || catalogWeaponCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || eligibleProposals == null || excludedAutomaticCandidates == null
                || authoredBlueprintIds == null || unplacedBlueprintIds == null) {
            throw new IllegalArgumentException("Automatic placement candidate snapshot is invalid");
        }
        eligibleProposals = immutableMap(eligibleProposals);
        excludedAutomaticCandidates = immutableMap(excludedAutomaticCandidates);
        authoredBlueprintIds = immutableSet(authoredBlueprintIds);
        unplacedBlueprintIds = immutableSet(unplacedBlueprintIds);

        Set<String> all = new LinkedHashSet<>();
        addDisjoint(all, eligibleProposals.keySet());
        addDisjoint(all, excludedAutomaticCandidates.keySet());
        addDisjoint(all, authoredBlueprintIds);
        addDisjoint(all, unplacedBlueprintIds);
        if (all.size() != catalogWeaponCount
                || (!mode.assignsPlacement() && !eligibleProposals.isEmpty())
                || excludedAutomaticCandidates.values().stream().anyMatch(String::isBlank)
                || eligibleProposals.entrySet().stream().anyMatch(entry ->
                        !entry.getKey().equals(entry.getValue().blueprintId())
                                || (entry.getValue().reviewRequired()
                                        && !policy.reviewHandling().assignsPlacement())
                                || entry.getValue().levelsPerTier() != policy.levelsPerTier()
                                || !ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION.equals(
                                        entry.getValue().placementVersion()))
                || !validLayering(eligibleProposals, policy)) {
            throw new IllegalArgumentException("Automatic placement candidate partition is inconsistent");
        }
    }

    private static boolean validLayering(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            AutomaticWeaponPlacementPolicy policy) {
        if (!policy.usesDynamicLayers()) {
            return proposals.values().stream().allMatch(proposal ->
                    proposal.progressionCoordinate().equals(
                            ResearchTechTreeContract.legacyProgressionCoordinate(
                                    proposal.position())));
        }
        Map<Integer, Long> widths = proposals.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        proposal -> proposal.progressionCoordinate().rank(),
                        java.util.stream.Collectors.counting()));
        return widths.values().stream().allMatch(width -> width <= policy.maxNodesPerRank())
                && proposals.values().stream().allMatch(proposal ->
                        proposal.progressionCoordinate().bandId().equals(
                                policy.bandForScore(proposal.mechanicalScore())
                                        .map(com.gamergaming.taczweaponblueprints.research.tree
                                                .automatic.AutomaticWeaponProgressionBand::id)));
    }

    public int automaticCandidateCount() {
        return eligibleProposals.size() + excludedAutomaticCandidates.size();
    }

    /** Compatibility alias retained for existing diagnostic integrations. */
    @Deprecated(forRemoval = false)
    public int fallbackCandidateCount() {
        return automaticCandidateCount();
    }

    /** Compatibility alias; the map now covers every excluded non-authored gun. */
    @Deprecated(forRemoval = false)
    public Map<String, String> excludedFallbackCandidates() {
        return excludedAutomaticCandidates;
    }

    public boolean matches(ResourceLocation tree, long catalog, long research) {
        return treeId.equals(tree) && catalogRevision == catalog && researchRevision == research;
    }

    public Optional<AutomaticWeaponPlacementProposal> eligibleProposal(ResourceLocation blueprintId) {
        return blueprintId == null
                ? Optional.empty()
                : Optional.ofNullable(eligibleProposals.get(blueprintId.toString()));
    }

    /** Replaces provisional coordinates while retaining the classified partition. */
    public AutomaticWeaponPlacementCandidateSnapshot withEligibleProposals(
            Map<String, AutomaticWeaponPlacementProposal> proposals) {
        return new AutomaticWeaponPlacementCandidateSnapshot(
                treeId,
                mode,
                policy,
                catalogRevision,
                researchRevision,
                catalogWeaponCount,
                proposals,
                excludedAutomaticCandidates,
                authoredBlueprintIds,
                unplacedBlueprintIds);
    }

    private static void addDisjoint(Set<String> all, Set<String> values) {
        for (String value : values) {
            if (!all.add(value)) {
                throw new IllegalArgumentException(
                        "Automatic placement candidate categories overlap at " + value);
            }
        }
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        Map<String, T> copy = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            validateId(entry.getKey());
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Automatic placement candidate map contains null");
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
            throw new IllegalArgumentException("Automatic placement candidate ID is invalid");
        }
    }
}
