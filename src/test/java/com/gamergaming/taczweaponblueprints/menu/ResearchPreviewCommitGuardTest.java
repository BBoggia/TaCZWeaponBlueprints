package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;

import net.minecraft.resources.ResourceLocation;

class ResearchPreviewCommitGuardTest {
    private static final ResearchRouteFingerprint CURRENT =
            new ResearchRouteFingerprint(10L, 20L);

    @Test
    void directResearchRequiresTheFreshAuthoritativeFingerprint() {
        ResearchSelectionPreview preview = preview(CURRENT);

        assertTrue(ResearchPreviewCommitGuard.accepts(
                true, preview, Optional.of(CURRENT)));
        assertFalse(ResearchPreviewCommitGuard.accepts(
                true, preview, Optional.empty()));
        assertFalse(ResearchPreviewCommitGuard.accepts(
                true,
                preview,
                Optional.of(new ResearchRouteFingerprint(10L, 21L))));
        assertFalse(ResearchPreviewCommitGuard.accepts(
                true, ResearchSelectionPreview.EMPTY, Optional.of(CURRENT)));
    }

    @Test
    void legacySingleBlueprintResearchDoesNotRequireAPathFingerprint() {
        assertTrue(ResearchPreviewCommitGuard.accepts(
                false, ResearchSelectionPreview.EMPTY, Optional.empty()));
        assertFalse(ResearchPreviewCommitGuard.accepts(
                false, ResearchSelectionPreview.EMPTY, Optional.of(CURRENT)));
    }

    private static ResearchSelectionPreview preview(ResearchRouteFingerprint fingerprint) {
        return new ResearchSelectionPreview(
                Optional.of(new ResourceLocation("test:target")),
                4,
                10,
                true,
                true,
                true,
                true,
                false,
                List.of(),
                2,
                0,
                ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.POINTS_AND_ITEMS,
                Optional.of(fingerprint));
    }
}
