package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeSelectedNodePresenterTest {
    @Test
    void matchingServerPreviewOwnsTheExactNextStep() {
        assertAuthoritative(
                ResearchTreeUxPhaseZeroFixture.readyPreview(),
                ResearchTreeSelectedNodePresenter.Message.READY,
                true);
        assertAuthoritative(
                ResearchTreeUxPhaseZeroFixture.insufficientPointsPreview(),
                ResearchTreeSelectedNodePresenter.Message.POINTS_REQUIRED,
                false);
        assertAuthoritative(
                ResearchTreeUxPhaseZeroFixture.missingMaterialsPreview(),
                ResearchTreeSelectedNodePresenter.Message.MATERIALS_REQUIRED,
                false);
        assertAuthoritative(
                ResearchTreeUxPhaseZeroFixture.outputFullPreview(),
                ResearchTreeSelectedNodePresenter.Message.PROGRESSION_CAPACITY_EXHAUSTED,
                false);
        assertAuthoritative(
                ResearchTreeUxPhaseZeroFixture.lockedPolicyPreview(),
                ResearchTreeSelectedNodePresenter.Message.LOCKED,
                false);
    }

    @Test
    void publicAvailabilityNeverClaimsThatResearchIsReady() {
        ResearchSelectionPreview preview = ResearchTreeUxPhaseZeroFixture.readyPreview();
        ResourceLocation nodeId = preview.blueprintId().orElseThrow();
        ResearchTreeGraph.Node node = availableNode(nodeId);

        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(input(
                        node,
                        true,
                        Optional.empty(),
                        preview));

        assertEquals(
                ResearchTreeSelectedNodePresenter.Message.CHECKING_REQUIREMENTS,
                presentation.message());
        assertFalse(presentation.exactPreview());
        assertFalse(presentation.actionVisible());
        assertFalse(presentation.actionEnabled());
        assertTrue(presentation.ingredients().isEmpty());
        assertEquals(2, presentation.directRequirementCount());
        assertEquals(3, presentation.immediateUnlockCount());
    }

    @Test
    void mismatchedPreviewCannotLeakInventoryOrEnableResearch() {
        ResearchSelectionPreview preview = ResearchTreeUxPhaseZeroFixture.missingMaterialsPreview();
        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(input(
                        availableNode(new ResourceLocation("phase_zero:other")),
                        true,
                        preview.blueprintId(),
                        preview));

        assertEquals(
                ResearchTreeSelectedNodePresenter.Message.CHECKING_REQUIREMENTS,
                presentation.message());
        assertFalse(presentation.exactPreview());
        assertTrue(presentation.ingredients().isEmpty());
        assertEquals(0, presentation.pointBalance());
        assertFalse(presentation.actionVisible());
    }

    @Test
    void matchingPreviewDoesNotReplaceAPublishedExactLockReason() {
        ResearchSelectionPreview preview = ResearchTreeUxPhaseZeroFixture.lockedPolicyPreview();
        ResourceLocation nodeId = preview.blueprintId().orElseThrow();
        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(input(
                        node(nodeId, ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED),
                        true,
                        Optional.of(nodeId),
                        preview));

        assertEquals(
                ResearchTreeSelectedNodePresenter.Message.PREREQUISITES_REQUIRED,
                presentation.message());
        assertTrue(presentation.exactPreview());
        assertFalse(presentation.actionEnabled());
    }

    @Test
    void creativeBypassIsAnEffectiveSatisfiedCostState() {
        ResearchSelectionPreview preview = ResearchTreeUxPhaseZeroFixture.creativeBypassPreview();
        ResourceLocation nodeId = preview.blueprintId().orElseThrow();

        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(input(
                        availableNode(nodeId),
                        false,
                        Optional.of(nodeId),
                        preview));

        assertEquals(ResearchTreeSelectedNodePresenter.Message.READY, presentation.message());
        assertTrue(presentation.costBypassed());
        assertTrue(presentation.pointsSatisfied());
        assertTrue(presentation.materialsSatisfied());
        assertTrue(presentation.actionEnabled());
    }

    @Test
    void authoritativePathPreviewCanMakeAHigherLockedNodeActionable() {
        ResourceLocation nodeId = new ResourceLocation("test:path_target");
        ResearchSelectionPreview preview = new ResearchSelectionPreview(
                Optional.of(nodeId),
                24,
                30,
                true,
                true,
                true,
                true,
                false,
                List.of(),
                4,
                0);

        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(input(
                        node(nodeId, ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED),
                        false,
                        Optional.of(nodeId),
                        preview));

        assertEquals(ResearchTreeSelectedNodePresenter.Message.READY, presentation.message());
        assertTrue(presentation.pathPurchase());
        assertEquals(4, presentation.unlockCount());
        assertTrue(presentation.actionEnabled());
    }

    @Test
    void authoritativeAccessBlockerTakesPriorityOverAnAffordablePath() {
        ResourceLocation nodeId = new ResourceLocation("test:tier_three_target");
        for (var blocked : List.of(
                new AccessFixture(
                        ResearchAccessSummary.workbench(
                                ResearchWorkbenchTier.TIER_1,
                                ResearchWorkbenchTier.TIER_3),
                        ResearchTreeSelectedNodePresenter.Message.WORKBENCH_TIER_REQUIRED),
                new AccessFixture(
                        ResearchAccessSummary.gate("gate.test.complete_trial"),
                        ResearchTreeSelectedNodePresenter.Message.PROGRESSION_GATE_REQUIRED),
                new AccessFixture(
                        ResearchAccessSummary.POLICY_UNAVAILABLE,
                        ResearchTreeSelectedNodePresenter.Message.REQUIREMENTS_UNAVAILABLE))) {
            ResearchSelectionPreview preview = new ResearchSelectionPreview(
                    Optional.of(nodeId),
                    24,
                    30,
                    false,
                    true,
                    true,
                    false,
                    false,
                    List.of(),
                    4,
                    0,
                    ResearchSelectionPreview.PathPlanningState.NONE,
                    ResearchCostMode.POINTS_AND_ITEMS,
                    Optional.empty(),
                    blocked.summary());

            ResearchTreeSelectedNodePresenter.Presentation presentation =
                    ResearchTreeSelectedNodePresenter.present(input(
                            node(nodeId, ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED),
                            true,
                            Optional.of(nodeId),
                            preview));

            assertEquals(blocked.message(), presentation.message());
            assertTrue(presentation.actionVisible());
            assertFalse(presentation.actionEnabled());
        }
    }

    @Test
    void authoritativePreviewExplainsBoundedPlannerFailures() {
        ResourceLocation nodeId = new ResourceLocation("test:complex_path_target");
        for (ResearchSelectionPreview.PathPlanningState state : List.of(
                ResearchSelectionPreview.PathPlanningState.PATH_TOO_LARGE,
                ResearchSelectionPreview.PathPlanningState.ROUTE_TOO_COMPLEX,
                ResearchSelectionPreview.PathPlanningState.TECH_TREE_UNAVAILABLE,
                ResearchSelectionPreview.PathPlanningState.UNSATISFIABLE)) {
            ResearchSelectionPreview preview = new ResearchSelectionPreview(
                    Optional.of(nodeId),
                    0,
                    30,
                    false,
                    true,
                    true,
                    false,
                    false,
                    List.of(),
                    1,
                    0,
                    state);

            ResearchTreeSelectedNodePresenter.Presentation presentation =
                    ResearchTreeSelectedNodePresenter.present(input(
                            node(
                                    nodeId,
                                    ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED),
                            true,
                            Optional.of(nodeId),
                            preview));

            assertEquals(
                    switch (state) {
                        case PATH_TOO_LARGE ->
                                ResearchTreeSelectedNodePresenter.Message.PATH_TOO_LARGE;
                        case ROUTE_TOO_COMPLEX ->
                                ResearchTreeSelectedNodePresenter.Message.ROUTE_TOO_COMPLEX;
                        case TECH_TREE_UNAVAILABLE ->
                                ResearchTreeSelectedNodePresenter.Message.TECH_TREE_UNAVAILABLE;
                        case UNSATISFIABLE ->
                                ResearchTreeSelectedNodePresenter.Message.UNSATISFIABLE;
                        case NONE -> throw new AssertionError("unexpected successful state");
                    },
                    presentation.message());
            assertTrue(presentation.actionVisible());
            assertFalse(presentation.actionEnabled());
            assertFalse(presentation.pointsSatisfied());
            assertFalse(presentation.materialsSatisfied());
            assertTrue(presentation.pathPlanningFailed());
        }
    }

    @Test
    void authoritativePreviewCarriesTheEffectiveCostChannels() {
        ResourceLocation nodeId = new ResourceLocation("test:materials_only");
        ResearchSelectionPreview preview = new ResearchSelectionPreview(
                Optional.of(nodeId),
                0,
                0,
                true,
                true,
                true,
                true,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(new ResourceLocation("minecraft:paper")),
                        Optional.empty(),
                        2,
                        2)),
                1,
                1,
                ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.ITEMS_ONLY);

        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(input(
                        availableNode(nodeId), true, Optional.of(nodeId), preview));

        assertFalse(presentation.pointsEnabled());
        assertTrue(presentation.materialsEnabled());
        assertEquals(ResearchCostMode.ITEMS_ONLY, presentation.costMode());
        assertEquals(ResearchTreeSelectedNodePresenter.Message.READY, presentation.message());
    }

    @Test
    void publishedPresentationCarriesMaterialsOnlyModeBeforeExactPreviewArrives() {
        ResourceLocation nodeId = new ResourceLocation("test:published_materials_only");
        ResearchTreeGraph.Node node = new ResearchTreeGraph.Node(
                0,
                nodeId,
                "fixture.materials_only",
                "rifle",
                new ResourceLocation("minecraft:paper"),
                com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility.FULL,
                false,
                true,
                true,
                0,
                2,
                0,
                0,
                ResearchTreeGraph.Availability.AVAILABLE);

        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(
                        new ResearchTreeSelectedNodePresenter.Input(
                                node,
                                false,
                                Optional.empty(),
                                ResearchTreeUxPhaseZeroFixture.readyPreview(),
                                1,
                                2,
                                ResearchCostMode.ITEMS_ONLY));

        assertFalse(presentation.exactPreview());
        assertEquals(ResearchCostMode.ITEMS_ONLY, presentation.costMode());
        assertEquals(0, presentation.pointCost());
        assertEquals(2, presentation.ingredientTypeCount());
        assertTrue(presentation.pointsSatisfied());
        assertEquals(
                ResearchTreeSelectedNodePresenter.Message.CHECKING_REQUIREMENTS,
                presentation.message());
    }

    @Test
    void invalidInputsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ResearchTreeSelectedNodePresenter.present(null));
        ResearchSelectionPreview preview = ResearchTreeUxPhaseZeroFixture.readyPreview();
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchTreeSelectedNodePresenter.Input(
                        availableNode(preview.blueprintId().orElseThrow()),
                        true,
                        Optional.empty(),
                        preview,
                        -1,
                        0,
                        ResearchCostMode.POINTS_AND_ITEMS));
    }

    private static void assertAuthoritative(
            ResearchSelectionPreview preview,
            ResearchTreeSelectedNodePresenter.Message message,
            boolean enabled) {
        ResourceLocation nodeId = preview.blueprintId().orElseThrow();
        ResearchTreeSelectedNodePresenter.Presentation presentation =
                ResearchTreeSelectedNodePresenter.present(input(
                        availableNode(nodeId),
                        true,
                        Optional.of(nodeId),
                        preview));
        assertEquals(message, presentation.message());
        assertTrue(presentation.exactPreview());
        assertTrue(presentation.actionVisible());
        assertEquals(enabled, presentation.actionEnabled());
        assertEquals(preview.ingredients(), presentation.ingredients());
        assertEquals(preview.pointCost(), presentation.pointCost());
        assertEquals(preview.pointBalance(), presentation.pointBalance());
    }

    private static ResearchTreeSelectedNodePresenter.Input input(
            ResearchTreeGraph.Node node,
            boolean canAfford,
            Optional<ResourceLocation> selection,
            ResearchSelectionPreview preview) {
        return new ResearchTreeSelectedNodePresenter.Input(
                node,
                canAfford,
                selection,
                preview,
                2,
                3,
                ResearchCostMode.POINTS_AND_ITEMS);
    }

    private static ResearchTreeGraph.Node availableNode(ResourceLocation id) {
        return node(id, ResearchTreeGraph.Availability.AVAILABLE);
    }

    private static ResearchTreeGraph.Node node(
            ResourceLocation id,
            ResearchTreeGraph.Availability availability) {
        return new ResearchTreeGraph.Node(
                0,
                id,
                "fixture.phase_zero.available",
                "rifle",
                new ResourceLocation("minecraft:paper"),
                com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility.FULL,
                availability == ResearchTreeGraph.Availability.LEARNED,
                true,
                availability == ResearchTreeGraph.Availability.AVAILABLE,
                6,
                1,
                0,
                0,
                availability);
    }

    private record AccessFixture(
            ResearchAccessSummary summary,
            ResearchTreeSelectedNodePresenter.Message message) {
    }
}
