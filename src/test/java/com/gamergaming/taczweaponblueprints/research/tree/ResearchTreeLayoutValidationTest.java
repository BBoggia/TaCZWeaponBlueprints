package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeLayoutValidationTest {
    @Test
    void overlapDiagnosticsIdentifyBothNodesAndTheirCoordinates() {
        ResourceLocation firstId = new ResourceLocation("test", "first");
        ResourceLocation secondId = new ResourceLocation("test", "second");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchTreeLayout(
                        64,
                        64,
                        1,
                        List.of(
                                new ResearchTreeLayout.PositionedNode(
                                        0, firstId, 0, 0, 0, 4, 4),
                                new ResearchTreeLayout.PositionedNode(
                                        1, secondId, 1, 0, 1, 16, 16))));

        assertTrue(failure.getMessage().contains("test:first at (4, 4)"));
        assertTrue(failure.getMessage().contains("test:second at (16, 16)"));
        assertTrue(failure.getMessage().contains("tier=0"));
        assertTrue(failure.getMessage().contains("component="));
    }
}
