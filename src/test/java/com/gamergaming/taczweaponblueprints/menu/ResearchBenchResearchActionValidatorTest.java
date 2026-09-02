package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchBenchResearchActionValidatorTest {
    private static final ResourceLocation SELECTED = new ResourceLocation("test:selected");
    private static final ResourceLocation OTHER = new ResourceLocation("test:other");

    @Test
    void selectionAcceptsPresentEmptyAndNullRequests() {
        assertTrue(accepts(ResearchBenchResearchAction.SELECT, Optional.empty(), Optional.empty()));
        assertTrue(accepts(
                ResearchBenchResearchAction.SELECT,
                Optional.of(SELECTED),
                Optional.of(OTHER)));
        assertTrue(ResearchBenchResearchActionValidator.accepts(
                ResearchBenchResearchAction.SELECT, null, null));
    }

    @Test
    void researchRequiresTheCurrentAuthoritativeSelection() {
        assertTrue(accepts(
                ResearchBenchResearchAction.RESEARCH,
                Optional.of(SELECTED),
                Optional.of(SELECTED)));
        assertFalse(accepts(
                ResearchBenchResearchAction.RESEARCH,
                Optional.of(SELECTED),
                Optional.of(OTHER)));
        assertFalse(accepts(
                ResearchBenchResearchAction.RESEARCH,
                Optional.of(SELECTED),
                Optional.empty()));
        assertFalse(accepts(
                ResearchBenchResearchAction.RESEARCH,
                Optional.empty(),
                Optional.of(SELECTED)));
    }

    @Test
    void nullActionsAreRejected() {
        assertFalse(accepts(null, Optional.empty(), Optional.empty()));
    }

    private static boolean accepts(
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> selected,
            Optional<ResourceLocation> requested) {
        return ResearchBenchResearchActionValidator.accepts(action, selected, requested);
    }
}
