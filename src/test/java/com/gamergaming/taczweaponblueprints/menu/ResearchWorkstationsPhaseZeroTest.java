package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

/** Keeps the original workstation registry and layout anchors stable across the split. */
class ResearchWorkstationsPhaseZeroTest {
    private static final ResourceLocation RESEARCH_BENCH =
            new ResourceLocation("taczweaponblueprints:research_bench");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void researchBenchRegistryAnchorsRemainStableAfterTheContractSplit() {
        assertEquals(RESEARCH_BENCH, ModBlocks.RESEARCH_BENCH.getId());
        assertEquals(RESEARCH_BENCH, ModItems.RESEARCH_BENCH_ITEM.getId());
        assertEquals(RESEARCH_BENCH, ModMenus.RESEARCH_BENCH.getId());
        assertEquals("42", NetworkHandler.PROTOCOL_VERSION);
    }

    @Test
    void originalCompactAndFullscreenGeometryRemainAvailableToLayoutCallers() {
        ResearchTreeScreenLayout.Layout compact = ResearchTreeScreenLayout.compact();
        assertEquals(310, compact.screenWidth());
        assertEquals(240, compact.screenHeight());
        assertEquals(new ResearchTreeScreenLayout.Rect(8, 64, 294, 116), compact.canvas());
        assertEquals(new ResearchTreeScreenLayout.Rect(8, 183, 294, 44), compact.details());

        ResearchTreeScreenLayout.Layout fullscreen =
                ResearchTreeScreenLayout.fullscreen(854, 480, true);
        assertEquals(new ResearchTreeScreenLayout.Rect(0, 0, 854, 480), fullscreen.canvas());
        assertEquals(
                ResearchTreeScreenLayout.DetailsPlacement.OVERLAY,
                fullscreen.detailsPlacement());
    }
}
