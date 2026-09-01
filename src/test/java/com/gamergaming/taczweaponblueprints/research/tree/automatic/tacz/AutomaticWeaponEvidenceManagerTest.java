package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponFireModeEvidence;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchAutomaticPlacementProfile;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeEntryBundle;

import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponEvidenceManagerTest {
    @Test
    void publicationIsAtomicVersionedAndClearable() {
        AutomaticWeaponEvidenceManager manager = new AutomaticWeaponEvidenceManager();
        WeaponStatEvidence builtIn = weapon("tacz:glock_17", 6.0);
        WeaponStatEvidence addOn = weapon("addon:service_pistol", 8.0);
        var capture = new TaCZRuntimeWeaponEvidenceAdapter.Capture(
                3,
                Map.of(
                        builtIn.blueprintId(), builtIn,
                        addOn.blueprintId(), addOn),
                Map.of("addon:broken", "missing_tacz_gun_index"));

        assertTrue(manager.publish(
                capture, WeaponMechanicalReferenceCatalog.bundled()));

        AutomaticWeaponEvidenceSnapshot snapshot = manager.snapshot();
        assertEquals(1, manager.revision());
        assertEquals(3, snapshot.candidateCount());
        assertEquals(2, snapshot.acceptedCount());
        assertEquals(1, snapshot.referenceMatches());
        assertEquals(java.util.Set.of("tacz:glock_17"), snapshot.referenceBlueprintIds());
        assertEquals(1L, snapshot.catalogRevision());
        assertEquals(1, snapshot.addOnCount());
        assertEquals(1, snapshot.placementPlan().candidateCount());
        assertEquals(List.of("addon:service_pistol"),
                snapshot.placementPlan().proposals().keySet().stream().toList());
        assertFalse(snapshot.placementPlan().proposals()
                .containsKey("tacz:glock_17"));
        assertEquals(3, snapshot.placementPlan().levelsPerTier());
        assertEquals(1, snapshot.capabilityPlacementPlan().candidateCount());
        assertEquals(List.of("addon:service_pistol"),
                snapshot.capabilityPlacementPlan().proposals().keySet().stream().toList());
        assertEquals(snapshot.evidenceByBlueprint().keySet(),
                snapshot.scoresByBlueprint().keySet());
        assertEquals(snapshot.evidenceByBlueprint().keySet(),
                snapshot.capabilityScoresByBlueprint().keySet());
        assertEquals(snapshot.evidenceByBlueprint().keySet(),
                snapshot.capabilityComparisons().keySet());
        assertTrue(snapshot.scoresByBlueprint().values().stream().allMatch(score ->
                score.rating().referenceVersion().equals(snapshot.referenceVersion())));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomaticWeaponEvidenceSnapshot(
                        snapshot.catalogRevision(),
                        snapshot.referenceVersion(),
                        snapshot.sourceVersion(),
                        snapshot.candidateCount(),
                        snapshot.referenceWeaponCount(),
                        snapshot.referenceMatches(),
                        java.util.Set.of("addon:service_pistol"),
                        snapshot.evidenceByBlueprint(),
                        snapshot.scoresByBlueprint(),
                        snapshot.rejectedBlueprints(),
                        snapshot.placementPlan()));

        assertFalse(manager.publish(null, WeaponMechanicalReferenceCatalog.bundled()));
        assertEquals(1, manager.revision());
        assertEquals(snapshot, manager.snapshot());

        manager.invalidateForCatalogRevision(2L);
        assertEquals(2, manager.revision());
        assertEquals(0, manager.snapshot().acceptedCount());
        assertTrue(manager.snapshot().matchesCatalogRevision(2L));
        assertEquals(manager.snapshot(), manager.snapshotForCatalogRevision(2L));
        assertEquals(0, manager.snapshotForCatalogRevision(3L).acceptedCount());

        manager.clear();
        assertEquals(0, manager.revision());
        assertEquals(AutomaticWeaponEvidenceSnapshot.EMPTY, manager.snapshot());
    }

    @Test
    void capabilityFailureDoesNotInvalidateMechanicalRollbackEvidence() {
        AutomaticWeaponEvidenceManager manager = new AutomaticWeaponEvidenceManager();
        WeaponStatEvidence normal = weapon("tacz:glock_17", 6.0);
        WeaponStatEvidence capabilityOverflow = capabilityOverflowWeapon();
        var capture = new TaCZRuntimeWeaponEvidenceAdapter.Capture(
                2,
                Map.of(
                        normal.blueprintId(), normal,
                        capabilityOverflow.blueprintId(), capabilityOverflow),
                Map.of());

        assertTrue(manager.publish(capture, WeaponMechanicalReferenceCatalog.bundled()));

        AutomaticWeaponEvidenceSnapshot snapshot = manager.snapshot();
        assertEquals(2, snapshot.acceptedCount());
        assertEquals(2, snapshot.scoresByBlueprint().size());
        assertEquals(java.util.Set.of("tacz:glock_17"),
                snapshot.capabilityScoresByBlueprint().keySet());
        assertEquals(1, snapshot.placementPlan().candidateCount());
        assertEquals(0, snapshot.capabilityPlacementPlan().candidateCount());
    }

    @Test
    void candidatePublicationIsRevisionCoupledAndInvalidatesAtomically() {
        AutomaticWeaponPlacementCandidateManager manager =
                new AutomaticWeaponPlacementCandidateManager();
        assertTrue(manager.rebuild(
                BlueprintResearchSnapshot.EMPTY,
                4L,
                Map.of(),
                9L,
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(9L)));
        assertEquals(1L, manager.publication().revision());
        assertEquals(9L, manager.publication().catalogRevision());
        assertEquals(4L, manager.publication().researchRevision());
        assertTrue(manager.publication().snapshotsByTree().isEmpty());
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.PublicationState.READY,
                manager.publication().health().state());
        assertTrue(manager.publication().health().failure().isEmpty());

        manager.invalidateForRevisions(10L, 5L);
        assertEquals(2L, manager.publication().revision());
        assertEquals(10L, manager.publication().catalogRevision());
        assertEquals(5L, manager.publication().researchRevision());
        assertTrue(manager.publication().snapshotsByTree().isEmpty());
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.PublicationState.INVALIDATED,
                manager.publication().health().state());
        assertTrue(manager.publication().health().failure().isEmpty());

        manager.clear();
        assertEquals(0L, manager.publication().revision());
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.PublicationState.EMPTY,
                manager.publication().health().state());
    }

    @Test
    void candidatePublicationRetainsBoundedFailureStageUntilRecoveryStarts() {
        AutomaticWeaponPlacementCandidateManager manager =
                new AutomaticWeaponPlacementCandidateManager();
        manager.invalidateForFailure(
                11L,
                6L,
                AutomaticWeaponPlacementCandidateManager.RebuildStage.RANK_FINALIZATION,
                new IllegalStateException("  unable\n to   fit rank  "));

        assertEquals(
                AutomaticWeaponPlacementCandidateManager.PublicationState.FAILED,
                manager.publication().health().state());
        var failure = manager.publication().health().failure().orElseThrow();
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.RebuildStage.RANK_FINALIZATION,
                failure.stage());
        assertEquals("unable to fit rank", failure.message());
        assertEquals(11L, manager.publication().catalogRevision());
        assertEquals(6L, manager.publication().researchRevision());
        assertTrue(manager.publication().snapshotsByTree().isEmpty());

        manager.invalidateForFailure(
                12L,
                7L,
                AutomaticWeaponPlacementCandidateManager.RebuildStage.PUBLICATION,
                new IllegalArgumentException("x".repeat(600)));
        assertEquals(512,
                manager.publication().health().failure().orElseThrow()
                        .message().length());
        assertTrue(manager.publication().health().failure().orElseThrow()
                .message().endsWith("..."));

        manager.invalidateForRevisions(13L, 8L);
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.PublicationState.INVALIDATED,
                manager.publication().health().state());
        assertTrue(manager.publication().health().failure().isEmpty());
    }

    @Test
    void candidateRebuildFailuresAreAtomicAndRecoverAcrossEveryDeclaredStage() {
        for (AutomaticWeaponPlacementCandidateManager.RebuildStage failedStage
                : AutomaticWeaponPlacementCandidateManager.RebuildStage.values()) {
            AtomicBoolean faultPending = new AtomicBoolean(true);
            AutomaticWeaponPlacementCandidateManager manager =
                    new AutomaticWeaponPlacementCandidateManager(stage -> {
                        if (stage == failedStage
                                && faultPending.compareAndSet(true, false)) {
                            throw new IllegalStateException(
                                    "forced " + stage.serializedName() + " failure");
                        }
                    });

            assertFalse(manager.rebuild(
                    BlueprintResearchSnapshot.EMPTY,
                    1L,
                    Map.of(),
                    1L,
                    AutomaticWeaponEvidenceSnapshot.emptyForCatalog(1L)));
            assertEquals(
                    AutomaticWeaponPlacementCandidateManager.PublicationState.FAILED,
                    manager.publication().health().state());
            assertEquals(
                    failedStage,
                    manager.publication().health().failure().orElseThrow().stage());
            assertTrue(manager.publication().classificationsByTree().isEmpty());
            assertTrue(manager.publication().snapshotsByTree().isEmpty());
            assertTrue(manager.publication().prerequisitePlansByProfile().isEmpty());

            manager.invalidateForRevisions(2L, 2L);
            assertEquals(
                    AutomaticWeaponPlacementCandidateManager.PublicationState.INVALIDATED,
                    manager.publication().health().state());
            assertTrue(manager.publication().health().failure().isEmpty());

            assertTrue(manager.rebuild(
                    BlueprintResearchSnapshot.EMPTY,
                    2L,
                    Map.of(),
                    2L,
                    AutomaticWeaponEvidenceSnapshot.emptyForCatalog(2L)));
            assertEquals(
                    AutomaticWeaponPlacementCandidateManager.PublicationState.READY,
                    manager.publication().health().state());
            assertTrue(manager.publication().health().failure().isEmpty());
        }
    }

    @Test
    void nonemptyCandidatePublicationFailsClosedWithoutLeakingPartialStages() {
        ResourceLocation weaponId = id("addon:service_pistol");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                weaponId, blueprint(weaponId));
        AutomaticWeaponEvidenceManager evidenceManager =
                new AutomaticWeaponEvidenceManager();
        assertTrue(evidenceManager.publish(
                new TaCZRuntimeWeaponEvidenceAdapter.Capture(
                        1,
                        Map.of(weaponId.toString(), weapon(weaponId.toString(), 8.0)),
                        Map.of()),
                WeaponMechanicalReferenceCatalog.bundled()));

        AtomicBoolean failPositioning = new AtomicBoolean(false);
        AutomaticWeaponPlacementCandidateManager manager =
                new AutomaticWeaponPlacementCandidateManager(stage -> {
                    if (failPositioning.get()
                            && stage == AutomaticWeaponPlacementCandidateManager
                                    .RebuildStage.POSITIONING) {
                        throw new IllegalStateException(
                                "forced nonempty positioning failure");
                    }
                });
        BlueprintResearchSnapshot research = nonemptyResearch();
        assertTrue(manager.rebuild(
                research, 1L, catalog, 1L, evidenceManager.snapshot()));
        assertFalse(manager.publication().classificationsByTree().isEmpty());
        assertFalse(manager.publication().snapshotsByTree().isEmpty());
        assertFalse(manager.publication().prerequisitePlansByProfile().isEmpty());
        long readyRevision = manager.publication().revision();

        failPositioning.set(true);
        assertFalse(manager.rebuild(
                research, 2L, catalog, 1L, evidenceManager.snapshot()));
        assertEquals(readyRevision + 1L, manager.publication().revision());
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.PublicationState.FAILED,
                manager.publication().health().state());
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.RebuildStage.POSITIONING,
                manager.publication().health().failure().orElseThrow().stage());
        assertTrue(manager.publication().classificationsByTree().isEmpty());
        assertTrue(manager.publication().snapshotsByTree().isEmpty());
        assertTrue(manager.publication().prerequisitePlansByProfile().isEmpty());

        failPositioning.set(false);
        assertTrue(manager.rebuild(
                research, 2L, catalog, 1L, evidenceManager.snapshot()));
        assertFalse(manager.publication().snapshotsByTree().isEmpty());
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.PublicationState.READY,
                manager.publication().health().state());
    }

    @Test
    void candidatePublicationRevisionRolloverRemainsPositive() {
        assertEquals(1L,
                AutomaticWeaponPlacementCandidateManager.nextPublicationRevision(0L));
        assertEquals(Long.MAX_VALUE,
                AutomaticWeaponPlacementCandidateManager.nextPublicationRevision(
                        Long.MAX_VALUE - 1L));
        assertEquals(1L,
                AutomaticWeaponPlacementCandidateManager.nextPublicationRevision(
                        Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () ->
                AutomaticWeaponPlacementCandidateManager.nextPublicationRevision(-1L));
    }

    @Test
    void evidenceResultsRejectOversizedDirectConstruction() {
        Map<String, String> rejected = new LinkedHashMap<>();
        java.util.stream.IntStream.range(0, 4097).forEach(index ->
                rejected.put("large_pack:weapon_" + index, "missing_tacz_gun_index"));

        assertThrows(IllegalArgumentException.class, () ->
                new TaCZRuntimeWeaponEvidenceAdapter.Capture(
                        4097,
                        Map.of(),
                        rejected));
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponEvidenceSnapshot(
                        1L,
                        ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                        "test-source",
                        4097,
                        0,
                        0,
                        java.util.Set.of(),
                        Map.of(),
                        Map.of(),
                        rejected,
                        AutomaticWeaponPlacementPlan.EMPTY));
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponEvidenceSnapshot(
                        1L,
                        "none",
                        "none",
                        0,
                        4097,
                        0,
                        java.util.Set.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        AutomaticWeaponPlacementPlan.EMPTY));
    }

    private static WeaponStatEvidence weapon(String id, double damage) {
        return new WeaponStatEvidence(
                id,
                "pistol",
                damage,
                0.0,
                500.0,
                15,
                2.0,
                100.0,
                50.0,
                0.1,
                1.5,
                1,
                0.2,
                0.3,
                2.0,
                0.2,
                0.4,
                -0.2,
                1,
                2,
                null,
                "magazine",
                false,
                false,
                List.of());
    }

    private static WeaponStatEvidence capabilityOverflowWeapon() {
        return new WeaponStatEvidence(
                "addon:overflow", "pistol", 8.0, 0.0, 500.0, 15, 2.0,
                100.0, 50.0, 0.1, 1.5, 1, 0.2, 0.3, 2.0, 0.2, 0.4,
                -0.2, 1, 2, null, "magazine", false, false, 1, 1.0,
                null, null, false, false, null, 0.0, 2.0, 1, null, null,
                0.0,
                List.of(new WeaponFireModeEvidence(
                        "semi", Double.MAX_VALUE, Double.MAX_VALUE, 1, null, null,
                        0.0, 0.0, true, 100.0, 0.1, 1.5, 0.2)),
                null, null, null, null, null, List.of());
    }

    private static BlueprintResearchSnapshot nonemptyResearch() {
        ResourceLocation profileId = id("test:profile");
        ResourceLocation treeId = id("test:tree");
        ResourceLocation laneId = id("test:weapons");
        BlueprintResearchProfile profile = new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                List.of(),
                Optional.of(treeId));
        ResearchTechTreeDefinition tree = new ResearchTechTreeDefinition(
                ResearchTechTreeDefinition.CURRENT_FORMAT,
                "Test",
                Optional.empty(),
                Optional.empty(),
                ResearchTechTreeDefinition.WeaponPlacementMode.AUTOMATIC,
                new ResearchTechTreeDefinition.LayoutDefinition(9),
                ResearchTechTreeDefinition.BandPolicyDefinition.NONE,
                Arrays.stream(Tier.values())
                        .map(tier -> new ResearchTechTreeDefinition.TierDefinition(
                                tier, tier.name(), Optional.empty()))
                        .toList(),
                List.of(new ResearchTechTreeDefinition.DomainDefinition(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        laneId,
                        Tier.STARTER,
                        List.of(new ResearchTechTreeDefinition.LaneDefinition(
                                laneId,
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0)))));
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                treeId,
                0,
                List.of(new ResearchTechTreeEntryBundle.Entry(
                        new BlueprintResearchTarget(
                                List.of(),
                                List.of(),
                                Optional.of(new BlueprintCatalogSelector(
                                        List.of("addon"),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(BlueprintKind.GUN),
                                        1.0F))),
                        Domain.WEAPONS,
                        laneId,
                        Tier.BASIC,
                        10,
                        Optional.empty(),
                        Optional.empty(),
                        true)));
        ResearchAutomaticPlacementProfile automatic =
                new ResearchAutomaticPlacementProfile(
                        2,
                        treeId,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of());
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId, profile),
                Map.of(),
                Map.of(),
                Map.of(treeId, tree),
                Map.of(id("test:entries"), entries),
                Map.of(id("test:automatic"), automatic));
    }

    private static BlueprintData blueprint(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip",
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                "pistol",
                new ResourceLocation(id.getNamespace(), "display/" + id.getPath()),
                BlueprintKind.GUN);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
