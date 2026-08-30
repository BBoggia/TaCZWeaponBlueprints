package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.LayoutDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.WidthMode;

class ResearchTechTreeLayerWidthResolverTest {
    private static final LayoutDefinition DYNAMIC =
            new LayoutDefinition(WidthMode.DYNAMIC, 9, 20);

    @Test
    void dynamicWidthUsesDeterministicFourByThreeThresholds() {
        assertEquals(9, resolve(0));
        assertEquals(9, resolve(60));
        assertEquals(10, resolve(61));
        assertEquals(10, resolve(75));
        assertEquals(11, resolve(76));
        assertEquals(11, resolve(90));
        assertEquals(12, resolve(91));
        assertEquals(12, resolve(108));
        assertEquals(13, resolve(109));
        assertEquals(14, resolve(145));
        assertEquals(15, resolve(148));
        assertEquals(16, resolve(169));
        assertEquals(17, resolve(193));
        assertEquals(18, resolve(217));
        assertEquals(19, resolve(244));
        assertEquals(19, resolve(270));
        assertEquals(20, resolve(271));
        assertEquals(20, resolve(287));
        assertEquals(20, resolve(ResearchTreeGraph.MAX_NODES));
    }

    @Test
    void dynamicWidthHonorsCustomBounds() {
        LayoutDefinition bounded = new LayoutDefinition(WidthMode.DYNAMIC, 10, 12);
        assertEquals(10, ResearchTechTreeLayerWidthResolver.resolve(bounded, 1));
        assertEquals(11, ResearchTechTreeLayerWidthResolver.resolve(bounded, 76));
        assertEquals(12, ResearchTechTreeLayerWidthResolver.resolve(bounded, 109));
    }

    @Test
    void widerAuthoringCeilingIsOptInAndPopulationDriven() {
        LayoutDefinition wider = new LayoutDefinition(WidthMode.DYNAMIC, 9, 28);

        assertEquals(20, ResearchTechTreeLayerWidthResolver.resolve(wider, 287));
        assertEquals(21, ResearchTechTreeLayerWidthResolver.resolve(wider, 301));
        assertEquals(28, ResearchTechTreeLayerWidthResolver.resolve(
                wider, ResearchTreeGraph.MAX_NODES));
    }

    @Test
    void fixedWidthIgnoresPopulationAndInvalidInputsFailClosed() {
        LayoutDefinition fixed = new LayoutDefinition(12);
        assertEquals(12, ResearchTechTreeLayerWidthResolver.resolve(fixed, 0));
        assertEquals(12, ResearchTechTreeLayerWidthResolver.resolve(
                fixed, ResearchTreeGraph.MAX_NODES));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeLayerWidthResolver.resolve(null, 10));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeLayerWidthResolver.resolve(DYNAMIC, -1));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeLayerWidthResolver.resolve(
                        DYNAMIC, ResearchTreeGraph.MAX_NODES + 1));
    }

    private static int resolve(int population) {
        return ResearchTechTreeLayerWidthResolver.resolve(DYNAMIC, population);
    }
}
