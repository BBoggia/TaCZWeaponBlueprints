package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeContextCardPolicyTest {
    private static final ResourceLocation NODE = new ResourceLocation("test:node");
    private static final ResourceLocation OTHER = new ResourceLocation("test:other");
    private static final ResearchSelectionPreview MATCHING = new ResearchSelectionPreview(
            Optional.of(NODE), 4, 8, true, true, true, true, false,
            List.of());

    @Test
    void exactContentRequiresPinnedSelectionAndPreviewToAgree() {
        assertTrue(ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                NODE, Optional.of(NODE), MATCHING));
        assertFalse(ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                OTHER, Optional.of(NODE), MATCHING));
        assertFalse(ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                NODE, Optional.of(OTHER), MATCHING));
        assertFalse(ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                NODE, Optional.of(NODE), ResearchSelectionPreview.EMPTY));
    }

    @Test
    void malformedAuthorityStateIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(NODE, null, MATCHING));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                        NODE, Optional.of(NODE), null));
    }
}
