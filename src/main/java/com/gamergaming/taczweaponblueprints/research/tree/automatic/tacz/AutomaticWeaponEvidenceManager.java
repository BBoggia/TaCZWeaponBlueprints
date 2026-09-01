package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.resource.PublicationRevision;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScore;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScorer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityScore;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityScorer;
import com.tacz.guns.resource.CommonAssetsManager;

import net.minecraft.resources.ResourceLocation;

/** Owns the latest revision-matched non-authoritative runtime evidence snapshot. */
public final class AutomaticWeaponEvidenceManager {
    public static final AutomaticWeaponEvidenceManager INSTANCE =
            new AutomaticWeaponEvidenceManager();

    private final TaCZRuntimeWeaponEvidenceAdapter adapter =
            new TaCZRuntimeWeaponEvidenceAdapter();
    private volatile Publication publication =
            new Publication(AutomaticWeaponEvidenceSnapshot.EMPTY, 0L);

    AutomaticWeaponEvidenceManager() {
    }

    public boolean rebuild(
            CommonAssetsManager assetsManager,
            Map<ResourceLocation, BlueprintData> recipeBackedCatalog,
            long catalogRevision) {
        if (catalogRevision <= 0) {
            throw new IllegalArgumentException("Blueprint catalog revision must be positive");
        }
        try {
            return publish(
                    adapter.capture(recipeBackedCatalog, assetsManager),
                    WeaponMechanicalReferenceCatalog.bundled(),
                    catalogRevision);
        } catch (RuntimeException | LinkageError exception) {
            invalidateForCatalogRevision(catalogRevision);
            TaCZWeaponBlueprints.LOGGER.error(
                    "Unable to rebuild automatic weapon evidence; invalidated publication at revision {}",
                    publication.revision(),
                    exception);
            return false;
        }
    }

    boolean publish(
            TaCZRuntimeWeaponEvidenceAdapter.Capture capture,
            WeaponMechanicalReferenceCatalog catalog) {
        return publish(capture, catalog, 1L);
    }

    boolean publish(
            TaCZRuntimeWeaponEvidenceAdapter.Capture capture,
            WeaponMechanicalReferenceCatalog catalog,
            long catalogRevision) {
        if (capture == null || catalog == null) {
            return false;
        }
        if (catalogRevision <= 0) {
            throw new IllegalArgumentException("Blueprint catalog revision must be positive");
        }
        WeaponMechanicalScorer scorer = new WeaponMechanicalScorer();
        Map<String, WeaponMechanicalScore> scores = new LinkedHashMap<>();
        capture.evidenceByBlueprint().forEach((id, evidence) ->
                scores.put(id, scorer.score(evidence, catalog.reference())));
        Map<String, WeaponCapabilityScore> capabilityScores = new LinkedHashMap<>();
        Map<String, String> capabilityFailures = new LinkedHashMap<>();
        try {
            WeaponCapabilityReferenceCatalog capabilityCatalog =
                    WeaponCapabilityReferenceCatalog.bundled();
            WeaponCapabilityScorer capabilityScorer = new WeaponCapabilityScorer();
            capture.evidenceByBlueprint().forEach((id, evidence) -> {
                try {
                    capabilityScores.put(
                            id,
                            capabilityScorer.score(evidence, capabilityCatalog.reference()));
                } catch (RuntimeException | LinkageError exception) {
                    capabilityFailures.put(
                            id,
                            "capability_score_failure:"
                                    + exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException | LinkageError exception) {
            capture.evidenceByBlueprint().keySet().forEach(id -> capabilityFailures.put(
                    id,
                    "capability_reference_failure:"
                            + exception.getClass().getSimpleName()));
        }
        Set<String> referenceBlueprintIds = capture.evidenceByBlueprint().keySet().stream()
                .filter(catalog.blueprintIds()::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        var addOnCandidates = scores.keySet().stream()
                .filter(id -> !catalog.blueprintIds().contains(id))
                .toList();
        var placementPlan = new AutomaticWeaponPlacementPlanner().plan(
                scores,
                addOnCandidates,
                AutomaticWeaponPlacementPolicy.DEFAULT);
        var capabilityAddOnCandidates = capabilityScores.keySet().stream()
                .filter(id -> !catalog.blueprintIds().contains(id))
                .toList();
        var capabilityPlacementPlan = new AutomaticWeaponPlacementPlanner()
                .planCapabilities(
                        capabilityScores,
                        capabilityAddOnCandidates,
                        AutomaticWeaponPlacementPolicy.DEFAULT);
        AutomaticWeaponEvidenceSnapshot snapshot = new AutomaticWeaponEvidenceSnapshot(
                catalogRevision,
                catalog.referenceVersion(),
                catalog.sourceVersion(),
                capture.candidateCount(),
                catalog.blueprintIds().size(),
                referenceBlueprintIds.size(),
                referenceBlueprintIds,
                capture.evidenceByBlueprint(),
                scores,
                capabilityScores,
                capture.rejectedBlueprints(),
                placementPlan,
                capabilityPlacementPlan);
        Publication previous = publication;
        publication = new Publication(
                snapshot, PublicationRevision.next(previous.revision()));
        TaCZWeaponBlueprints.LOGGER.info(
                "Captured automatic weapon evidence revision {}: {}/{} accepted, {} default "
                        + "reference matches, {} add-on candidates, and {} rejected",
                publication.revision(),
                snapshot.acceptedCount(),
                snapshot.candidateCount(),
                snapshot.referenceMatches(),
                snapshot.addOnCount(),
                snapshot.rejectedBlueprints().size());
        long changedBands = snapshot.capabilityComparisons().values().stream()
                .filter(comparison -> comparison.tierDelta() != 0)
                .count();
        int maximumDelta = snapshot.capabilityComparisons().values().stream()
                .mapToInt(comparison -> Math.abs(comparison.scoreDelta()))
                .max()
                .orElse(0);
        TaCZWeaponBlueprints.LOGGER.info(
                "Capability v3 shadow comparison: {} scored, {} changed suggested bands, "
                        + "maximum absolute score delta {}, and {} isolated failures",
                capabilityScores.size(), changedBands, maximumDelta,
                capabilityFailures.size());
        if (!capabilityFailures.isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Capability v3 evidence was unavailable for {} weapons; mechanical v2 "
                            + "publication remains valid. Failures: {}",
                    capabilityFailures.size(), capabilityFailures);
        }
        if (placementPlan.reviewRequiredCount() > 0
                || !placementPlan.rejectedCandidates().isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Automatic weapon placement proposals require review: {} flagged and {} rejected",
                    placementPlan.reviewRequiredCount(),
                    placementPlan.rejectedCandidates().size());
        }
        if (capabilityPlacementPlan.reviewRequiredCount() > 0
                || !capabilityPlacementPlan.rejectedCandidates().isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Capability v3 automatic placement proposals require review: {} flagged "
                            + "and {} rejected",
                    capabilityPlacementPlan.reviewRequiredCount(),
                    capabilityPlacementPlan.rejectedCandidates().size());
        }
        return true;
    }

    public AutomaticWeaponEvidenceSnapshot snapshot() {
        return publication.snapshot();
    }

    public AutomaticWeaponEvidenceSnapshot snapshotForCatalogRevision(long catalogRevision) {
        AutomaticWeaponEvidenceSnapshot snapshot = publication.snapshot();
        return snapshot.matchesCatalogRevision(catalogRevision)
                ? snapshot
                : AutomaticWeaponEvidenceSnapshot.emptyForCatalog(catalogRevision);
    }

    public long revision() {
        return publication.revision();
    }

    public void clear() {
        publication = new Publication(AutomaticWeaponEvidenceSnapshot.EMPTY, 0L);
    }

    public void invalidateForCatalogRevision(long catalogRevision) {
        if (catalogRevision < 0) {
            throw new IllegalArgumentException("Blueprint catalog revision cannot be negative");
        }
        Publication previous = publication;
        publication = new Publication(
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(catalogRevision),
                PublicationRevision.next(previous.revision()));
    }

    private record Publication(
            AutomaticWeaponEvidenceSnapshot snapshot,
            long revision) {
    }
}
