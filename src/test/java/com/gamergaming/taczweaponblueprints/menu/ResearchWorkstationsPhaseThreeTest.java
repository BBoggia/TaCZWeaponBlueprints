package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.client.BlueprintRecyclerActionResultListener;
import com.gamergaming.taczweaponblueprints.client.BlueprintRecyclerScreen;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Structural gates for the usable client/server Recycler introduced in Phase 3. */
class ResearchWorkstationsPhaseThreeTest {
    @Test
    void dedicatedScreenConsumesCorrelatedActionResults() {
        assertTrue(BlueprintRecyclerActionResultListener.class.isAssignableFrom(
                BlueprintRecyclerScreen.class));
    }

    @Test
    void recyclerNowOwnsItsInteractionWithoutChangingProtocol()
            throws ReflectiveOperationException {
        assertEquals(
                BlueprintRecyclerBlock.class,
                BlueprintRecyclerBlock.class.getMethod(
                        "use",
                        BlockState.class,
                        Level.class,
                        BlockPos.class,
                        Player.class,
                        InteractionHand.class,
                        BlockHitResult.class).getDeclaringClass());
        assertEquals("40", NetworkHandler.PROTOCOL_VERSION);
    }

    @Test
    void expandedConventionalLayoutKeepsEverySlotInsideTheScreen() {
        assertTrue(BlueprintRecyclerMenu.Layout.INPUT_X >= 0);
        assertTrue(BlueprintRecyclerMenu.Layout.INPUT_Y >= 0);
        assertTrue(BlueprintRecyclerMenu.Layout.OUTPUT_X + 16
                < BlueprintRecyclerMenu.Layout.DETAIL_X);
        assertTrue(BlueprintRecyclerMenu.Layout.DETAIL_X
                + BlueprintRecyclerMenu.Layout.DETAIL_WIDTH
                <= BlueprintRecyclerMenu.Layout.PANEL_WIDTH
                        - BlueprintRecyclerMenu.Layout.SECTION_X * 2);
        assertTrue(BlueprintRecyclerMenu.Layout.ACTION_Y
                + BlueprintRecyclerMenu.Layout.ACTION_HEIGHT
                < BlueprintRecyclerMenu.Layout.PLAYER_LABEL_Y);
        assertTrue(BlueprintRecyclerMenu.Layout.PLAYER_X + 8 * 18 + 16
                <= BlueprintRecyclerMenu.Layout.PANEL_WIDTH);
        assertTrue(BlueprintRecyclerMenu.Layout.HOTBAR_Y + 16
                <= BlueprintRecyclerMenu.Layout.PANEL_HEIGHT);
    }
}
