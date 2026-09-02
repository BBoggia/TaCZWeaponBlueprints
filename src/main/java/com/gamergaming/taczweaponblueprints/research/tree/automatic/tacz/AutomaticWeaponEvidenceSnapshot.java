package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScore;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityComparison;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityScore;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence;

/** Atomic, non-authoritative result of one successful TaCZ runtime evidence capture. */
public record AutomaticWeaponEvidenceSnapshot(
        long catalogRevision,
        String referenceVersion,
        String sourceVersion,
        int candidateCount,
        int referenceWeaponCount,
        int referenceMatches,
        Set<String> referenceBlueprintIds,
        Map<String, WeaponStatEvidence> evidenceByBlueprint,
        Map<String, WeaponMechanicalScore> scoresByBlueprint,
        Map<String, WeaponCapabilityScore> capabilityScoresByBlueprint,
        Map<String, String> rejectedBlueprints,
        AutomaticWeaponPlacementPlan placementPlan,
        AutomaticWeaponPlacementPlan capabilityPlacementPlan) {
    /** Construction path retained for callers that only supply the v2 plan. */
    public AutomaticWeaponEvidenceSnapshot(
            long catalogRevision, String referenceVersion, String sourceVersion,
            int candidateCount, int referenceWeaponCount, int referenceMatches,
            Set<String> referenceBlueprintIds,
            Map<String, WeaponStatEvidence> evidenceByBlueprint,
            Map<String, WeaponMechanicalScore> scoresByBlueprint,
            Map<String, WeaponCapabilityScore> capabilityScoresByBlueprint,
            Map<String, String> rejectedBlueprints,
            AutomaticWeaponPlacementPlan placementPlan) {
        this(
                catalogRevision, referenceVersion, sourceVersion, candidateCount,
                referenceWeaponCount, referenceMatches, referenceBlueprintIds,
                evidenceByBlueprint, scoresByBlueprint, capabilityScoresByBlueprint,
                rejectedBlueprints, placementPlan,
                defaultCapabilityPlan(capabilityScoresByBlueprint, referenceBlueprintIds));
    }

    /** Legacy construction path retained for focused v2 fixtures. */
    public AutomaticWeaponEvidenceSnapshot(
            long catalogRevision,
            String referenceVersion,
            String sourceVersion,
            int candidateCount,
            int referenceWeaponCount,
            int referenceMatches,
            Set<String> referenceBlueprintIds,
            Map<String, WeaponStatEvidence> evidenceByBlueprint,
            Map<String, WeaponMechanicalScore> scoresByBlueprint,
            Map<String, String> rejectedBlueprints,
            AutomaticWeaponPlacementPlan placementPlan) {
        this(
                catalogRevision, referenceVersion, sourceVersion, candidateCount,
                referenceWeaponCount, referenceMatches, referenceBlueprintIds,
                evidenceByBlueprint, scoresByBlueprint, Map.of(), rejectedBlueprints,
                placementPlan, AutomaticWeaponPlacementPlan.EMPTY);
    }

    public static final AutomaticWeaponEvidenceSnapshot EMPTY =
            new AutomaticWeaponEvidenceSnapshot(
                    0L, "none", "none", 0, 0, 0, Set.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), AutomaticWeaponPlacementPlan.EMPTY,
                    AutomaticWeaponPlacementPlan.EMPTY);

    public static AutomaticWeaponEvidenceSnapshot emptyForCatalog(long catalogRevision) {
        return new AutomaticWeaponEvidenceSnapshot(
                catalogRevision, "none", "none", 0, 0, 0, Set.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), AutomaticWeaponPlacementPlan.EMPTY,
                AutomaticWeaponPlacementPlan.EMPTY);
    }

    public AutomaticWeaponEvidenceSnapshot {
        if (catalogRevision < 0 || !validText(referenceVersion) || !validText(sourceVersion)
                || candidateCount < 0 || referenceWeaponCount < 0
                || candidateCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || referenceWeaponCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || referenceMatches < 0 || referenceMatches > referenceWeaponCount
                || referenceBlueprintIds == null
                || evidenceByBlueprint == null || scoresByBlueprint == null
                || capabilityScoresByBlueprint == null
                || rejectedBlueprints == null || placementPlan == null
                || capabilityPlacementPlan == null
                || referenceMatches != referenceBlueprintIds.size()
                || !evidenceByBlueprint.keySet().containsAll(referenceBlueprintIds)
                || candidateCount != evidenceByBlueprint.size() + rejectedBlueprints.size()
                || !evidenceByBlueprint.keySet().equals(scoresByBlueprint.keySet())
                || !evidenceByBlueprint.keySet().containsAll(
                        capabilityScoresByBlueprint.keySet())
                || !Collections.disjoint(
                        evidenceByBlueprint.keySet(), rejectedBlueprints.keySet())
                || !ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION.equals(
                        placementPlan.placementVersion())
                || !ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION.equals(
                        capabilityPlacementPlan.placementVersion())) {
            throw new IllegalArgumentException(
                    "Automatic weapon evidence snapshot is invalid");
        }
        Set<String> addOns = new java.util.LinkedHashSet<>(evidenceByBlueprint.keySet());
        addOns.removeAll(referenceBlueprintIds);
        Set<String> planned = new java.util.LinkedHashSet<>(placementPlan.proposals().keySet());
        planned.addAll(placementPlan.rejectedCandidates().keySet());
        Set<String> capabilityAddOns = new java.util.LinkedHashSet<>(
                capabilityScoresByBlueprint.keySet());
        capabilityAddOns.removeAll(referenceBlueprintIds);
        Set<String> capabilityPlanned = new java.util.LinkedHashSet<>(
                capabilityPlacementPlan.proposals().keySet());
        capabilityPlanned.addAll(capabilityPlacementPlan.rejectedCandidates().keySet());
        boolean emptyState = candidateCount == 0 && evidenceByBlueprint.isEmpty();
        Map<String, WeaponStatEvidence> suppliedEvidence = evidenceByBlueprint;
        if (!addOns.equals(planned)
                || !capabilityAddOns.equals(capabilityPlanned)
                || (!emptyState && (!ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION.equals(
                        referenceVersion) || catalogRevision == 0))
                || evidenceByBlueprint.entrySet().stream().anyMatch(entry ->
                        !entry.getKey().equals(entry.getValue().blueprintId()))
                || scoresByBlueprint.entrySet().stream().anyMatch(entry ->
                        !entry.getKey().equals(entry.getValue().evidence().blueprintId())
                                || !entry.getValue().evidence().equals(
                                        suppliedEvidence.get(entry.getKey()))
                                || !ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION.equals(
                                        entry.getValue().rating().formulaVersion())
                                || !referenceVersion.equals(
                                        entry.getValue().rating().referenceVersion()))
                || capabilityScoresByBlueprint.entrySet().stream().anyMatch(entry ->
                        !entry.getKey().equals(entry.getValue().evidence().blueprintId())
                                || !entry.getValue().evidence().equals(
                                        suppliedEvidence.get(entry.getKey()))
                                || !ResearchTechTreeContract.CAPABILITY_FORMULA_VERSION.equals(
                                        entry.getValue().formulaVersion())
                                || !ResearchTechTreeContract.CAPABILITY_REFERENCE_VERSION.equals(
                                        entry.getValue().referenceVersion()))
                || placementPlan.proposals().values().stream().anyMatch(proposal ->
                        !ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION.equals(
                                proposal.formulaVersion())
                                || !referenceVersion.equals(proposal.referenceVersion())
                                || !placementPlan.placementVersion().equals(
                                        proposal.placementVersion()))
                || capabilityPlacementPlan.proposals().values().stream().anyMatch(proposal ->
                        !ResearchTechTreeContract.CAPABILITY_FORMULA_VERSION.equals(
                                proposal.formulaVersion())
                                || !ResearchTechTreeContract.CAPABILITY_REFERENCE_VERSION.equals(
                                        proposal.referenceVersion())
                                || !capabilityPlacementPlan.placementVersion().equals(
                                        proposal.placementVersion()))) {
            throw new IllegalArgumentException(
                    "Automatic weapon evidence snapshot versions or candidates are inconsistent");
        }
        referenceBlueprintIds = immutableSet(referenceBlueprintIds);
        evidenceByBlueprint = immutableMap(evidenceByBlueprint);
        scoresByBlueprint = immutableMap(scoresByBlueprint);
        capabilityScoresByBlueprint = immutableMap(capabilityScoresByBlueprint);
        rejectedBlueprints = immutableMap(rejectedBlueprints);
    }

    public int acceptedCount() {
        return evidenceByBlueprint.size();
    }

    public int addOnCount() {
        return placementPlan.candidateCount();
    }

    public int capabilityAddOnCount() {
        return capabilityPlacementPlan.candidateCount();
    }

    public boolean matchesCatalogRevision(long revision) {
        return catalogRevision == revision;
    }

    public Map<String, WeaponCapabilityComparison> capabilityComparisons() {
        Map<String, WeaponCapabilityComparison> result = new LinkedHashMap<>();
        capabilityScoresByBlueprint.forEach((id, capability) -> result.put(
                id,
                WeaponCapabilityComparison.compare(scoresByBlueprint.get(id), capability)));
        return Collections.unmodifiableMap(result);
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank();
    }

    private static AutomaticWeaponPlacementPlan defaultCapabilityPlan(
            Map<String, WeaponCapabilityScore> capabilityScores,
            Set<String> referenceBlueprintIds) {
        if (capabilityScores == null || capabilityScores.isEmpty()) {
            return AutomaticWeaponPlacementPlan.EMPTY;
        }
        Set<String> references = referenceBlueprintIds == null
                ? Set.of()
                : referenceBlueprintIds;
        var candidates = capabilityScores.keySet().stream()
                .filter(id -> !references.contains(id))
                .toList();
        return new AutomaticWeaponPlacementPlanner().planCapabilities(
                capabilityScores, candidates, AutomaticWeaponPlacementPolicy.DEFAULT);
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        Map<String, T> copy = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!validText(entry.getKey()) || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Automatic weapon evidence snapshot map is invalid");
            }
            copy.put(entry.getKey(), entry.getValue());
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableSet(Set<String> source) {
        java.util.LinkedHashSet<String> copy = new java.util.LinkedHashSet<>();
        source.stream().sorted().forEach(value -> {
            if (!validText(value)) {
                throw new IllegalArgumentException(
                        "Automatic weapon evidence snapshot set is invalid");
            }
            copy.add(value);
        });
        return Collections.unmodifiableSet(copy);
    }
}
