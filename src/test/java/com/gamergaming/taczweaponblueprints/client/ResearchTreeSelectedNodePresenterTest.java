package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
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
                        0));
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
                node, canAfford, selection, preview, 2, 3);
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
}
