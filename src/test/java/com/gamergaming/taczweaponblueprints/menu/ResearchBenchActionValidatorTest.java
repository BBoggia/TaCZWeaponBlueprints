package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchBenchActionValidatorTest {
    private static final ResourceLocation SELECTED = new ResourceLocation("test:selected");
    private static final ResourceLocation OTHER = new ResourceLocation("test:other");

    @Test
    void selectionIsAcceptedOnlyWhileBrowsing() {
        assertTrue(accepts(ResearchBenchMenu.Mode.BROWSE, ResearchBenchMenu.Action.SELECT,
                Optional.of(SELECTED), Optional.empty()));
        assertTrue(accepts(ResearchBenchMenu.Mode.BROWSE, ResearchBenchMenu.Action.SELECT,
                Optional.of(SELECTED), Optional.of(SELECTED)));
        assertFalse(accepts(ResearchBenchMenu.Mode.RECYCLE, ResearchBenchMenu.Action.SELECT,
                Optional.of(SELECTED), Optional.of(SELECTED)));
    }

    @Test
    void researchRequiresTheCurrentBrowseSelection() {
        assertTrue(accepts(ResearchBenchMenu.Mode.BROWSE, ResearchBenchMenu.Action.RESEARCH,
                Optional.of(SELECTED), Optional.of(SELECTED)));
        assertFalse(accepts(ResearchBenchMenu.Mode.RECYCLE, ResearchBenchMenu.Action.RESEARCH,
                Optional.of(SELECTED), Optional.of(SELECTED)));
        assertFalse(accepts(ResearchBenchMenu.Mode.BROWSE, ResearchBenchMenu.Action.RESEARCH,
                Optional.of(SELECTED), Optional.of(OTHER)));
        assertFalse(accepts(ResearchBenchMenu.Mode.BROWSE, ResearchBenchMenu.Action.RESEARCH,
                Optional.empty(), Optional.of(SELECTED)));
        assertFalse(accepts(ResearchBenchMenu.Mode.BROWSE, ResearchBenchMenu.Action.RESEARCH,
                Optional.of(SELECTED), Optional.empty()));
    }

    @Test
    void recyclingRequiresThePhysicalInputIdInRecycleMode() {
        assertTrue(ResearchBenchActionValidator.accepts(
                ResearchBenchMenu.Mode.RECYCLE,
                ResearchBenchMenu.Action.RECYCLE,
                Optional.empty(),
                Optional.of(SELECTED),
                Optional.of(SELECTED)));
        assertFalse(ResearchBenchActionValidator.accepts(
                ResearchBenchMenu.Mode.RECYCLE,
                ResearchBenchMenu.Action.RECYCLE,
                Optional.empty(),
                Optional.of(OTHER),
                Optional.of(SELECTED)));
        assertFalse(ResearchBenchActionValidator.accepts(
                ResearchBenchMenu.Mode.BROWSE,
                ResearchBenchMenu.Action.RECYCLE,
                Optional.empty(),
                Optional.of(SELECTED),
                Optional.of(SELECTED)));
    }

    @Test
    void modeChangesRejectSmuggledBlueprintIdsAndNullState() {
        for (ResearchBenchMenu.Action action : new ResearchBenchMenu.Action[] {
                ResearchBenchMenu.Action.SHOW_BROWSE,
                ResearchBenchMenu.Action.SHOW_RECYCLE}) {
            assertTrue(accepts(ResearchBenchMenu.Mode.BROWSE, action,
                    Optional.empty(), Optional.empty()));
            assertFalse(accepts(ResearchBenchMenu.Mode.BROWSE, action,
                    Optional.empty(), Optional.of(SELECTED)));
        }
        assertFalse(ResearchBenchActionValidator.accepts(
                null, ResearchBenchMenu.Action.SELECT, Optional.empty(), Optional.empty(), Optional.empty()));
        assertFalse(ResearchBenchActionValidator.accepts(
                ResearchBenchMenu.Mode.BROWSE, null, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static boolean accepts(
            ResearchBenchMenu.Mode mode,
            ResearchBenchMenu.Action action,
            Optional<ResourceLocation> selected,
            Optional<ResourceLocation> requested) {
        return ResearchBenchActionValidator.accepts(
                mode, action, selected, requested, Optional.empty());
    }
}
