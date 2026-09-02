package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;

/** Structural gates for the additive server-side Recycler introduced in Phase 2. */
class ResearchWorkstationsPhaseTwoTest {
    private static final ResourceLocation RECYCLER =
            new ResourceLocation("taczweaponblueprints:blueprint_recycler");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void additiveRegistryIdsAndProtocolRemainLocked() {
        assertEquals(RECYCLER, ModBlocks.BLUEPRINT_RECYCLER.getId());
        assertEquals(RECYCLER, ModItems.BLUEPRINT_RECYCLER_ITEM.getId());
        assertEquals(RECYCLER, ModMenus.BLUEPRINT_RECYCLER.getId());
        assertEquals("47", NetworkHandler.PROTOCOL_VERSION);
    }

    @Test
    void menuOwnsInputAndExtractOnlyOutputBeforeTheVanillaPlayerInventory() {
        assertEquals(0, BlueprintRecyclerMenu.INPUT_SLOT);
        assertEquals(1, BlueprintRecyclerMenu.OUTPUT_SLOT);
        assertEquals(2, BlueprintRecyclerMenu.FIRST_PLAYER_SLOT);
        assertTrue(BlueprintRecyclerMenuBridge.class.isAssignableFrom(
                BlueprintRecyclerMenu.class));
    }

    @Test
    void menuExplicitlyReturnsLocalInputWhenClosed() throws ReflectiveOperationException {
        assertEquals(
                BlueprintRecyclerMenu.class,
                BlueprintRecyclerMenu.class
                        .getMethod("removed", Player.class)
                        .getDeclaringClass());
    }

    @Test
    void shiftClickRoutesInputToPlayerAndPlayerStacksToTheSingleInput() {
        int slotCount = 37;
        BlueprintRecyclerMenu.TransferPlan fromInput =
                BlueprintRecyclerMenu.transferPlan(0, slotCount).orElseThrow();
        BlueprintRecyclerMenu.TransferPlan fromPlayer =
                BlueprintRecyclerMenu.transferPlan(18, slotCount).orElseThrow();

        assertEquals(2, fromInput.startInclusive());
        assertEquals(slotCount, fromInput.endExclusive());
        assertTrue(fromInput.reverse());
        assertEquals(0, fromPlayer.startInclusive());
        assertEquals(1, fromPlayer.endExclusive());
        assertFalse(fromPlayer.reverse());
        assertTrue(BlueprintRecyclerMenu.transferPlan(-1, slotCount).isEmpty());
        assertTrue(BlueprintRecyclerMenu.transferPlan(slotCount, slotCount).isEmpty());
    }

    @Test
    void preservedPhysicalIdentityDoesNotMasqueradeAsResearchData() {
        ResourceLocation paper = new ResourceLocation("minecraft:paper");
        ResearchDataRedemptionService.Evaluation unavailable =
                new ResearchDataRedemptionService.Evaluation(
                        ResearchDataRedemptionService.Status.PLAYER_DATA_UNAVAILABLE,
                        Optional.of(paper), 1, 0, 0, 0);
        ResearchDataRedemptionService.Evaluation configuredButBlocked =
                new ResearchDataRedemptionService.Evaluation(
                        ResearchDataRedemptionService.Status.NO_ELIGIBLE_AWARD,
                        Optional.of(paper), 1, 0, 0, 100);

        assertEquals(
                BlueprintRecyclerPreview.InputKind.INVALID,
                BlueprintRecyclerMenu.classifyInput(Optional.empty(), unavailable));
        assertEquals(
                BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                BlueprintRecyclerMenu.classifyInput(Optional.empty(), configuredButBlocked));
    }
}
