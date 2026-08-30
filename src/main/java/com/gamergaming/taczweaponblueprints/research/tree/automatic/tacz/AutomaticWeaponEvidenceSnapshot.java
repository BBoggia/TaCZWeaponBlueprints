package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScore;
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
        Map<String, String> rejectedBlueprints,
        AutomaticWeaponPlacementPlan placementPlan) {
    public static final AutomaticWeaponEvidenceSnapshot EMPTY =
            new AutomaticWeaponEvidenceSnapshot(
                    0L, "none", "none", 0, 0, 0, Set.of(), Map.of(), Map.of(), Map.of(),
                    AutomaticWeaponPlacementPlan.EMPTY);

    public static AutomaticWeaponEvidenceSnapshot emptyForCatalog(long catalogRevision) {
        return new AutomaticWeaponEvidenceSnapshot(
                catalogRevision, "none", "none", 0, 0, 0, Set.of(), Map.of(), Map.of(),
                Map.of(), AutomaticWeaponPlacementPlan.EMPTY);
    }

    public AutomaticWeaponEvidenceSnapshot {
        if (catalogRevision < 0 || !validText(referenceVersion) || !validText(sourceVersion)
                || candidateCount < 0 || referenceWeaponCount < 0
                || candidateCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || referenceWeaponCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || referenceMatches < 0 || referenceMatches > referenceWeaponCount
                || referenceBlueprintIds == null
                || evidenceByBlueprint == null || scoresByBlueprint == null
                || rejectedBlueprints == null || placementPlan == null
                || referenceMatches != referenceBlueprintIds.size()
                || !evidenceByBlueprint.keySet().containsAll(referenceBlueprintIds)
                || candidateCount != evidenceByBlueprint.size() + rejectedBlueprints.size()
                || !evidenceByBlueprint.keySet().equals(scoresByBlueprint.keySet())
                || !Collections.disjoint(
                        evidenceByBlueprint.keySet(), rejectedBlueprints.keySet())
                || !ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION.equals(
                        placementPlan.placementVersion())) {
            throw new IllegalArgumentException(
                    "Automatic weapon evidence snapshot is invalid");
        }
        Set<String> addOns = new java.util.LinkedHashSet<>(evidenceByBlueprint.keySet());
        addOns.removeAll(referenceBlueprintIds);
        Set<String> planned = new java.util.LinkedHashSet<>(placementPlan.proposals().keySet());
        planned.addAll(placementPlan.rejectedCandidates().keySet());
        boolean emptyState = candidateCount == 0 && evidenceByBlueprint.isEmpty();
        Map<String, WeaponStatEvidence> suppliedEvidence = evidenceByBlueprint;
        if (!addOns.equals(planned)
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
                || placementPlan.proposals().values().stream().anyMatch(proposal ->
                        !ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION.equals(
                                proposal.formulaVersion())
                                || !referenceVersion.equals(proposal.referenceVersion())
                                || !placementPlan.placementVersion().equals(
                                        proposal.placementVersion()))) {
            throw new IllegalArgumentException(
                    "Automatic weapon evidence snapshot versions or candidates are inconsistent");
        }
        referenceBlueprintIds = immutableSet(referenceBlueprintIds);
        evidenceByBlueprint = immutableMap(evidenceByBlueprint);
        scoresByBlueprint = immutableMap(scoresByBlueprint);
        rejectedBlueprints = immutableMap(rejectedBlueprints);
    }

    public int acceptedCount() {
        return evidenceByBlueprint.size();
    }

    public int addOnCount() {
        return placementPlan.candidateCount();
    }

    public boolean matchesCatalogRevision(long revision) {
        return catalogRevision == revision;
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank();
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
