package com.gamergaming.taczweaponblueprints.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Narrow packet boundary implemented by the dedicated Blueprint Analyzer menu. */
public interface BlueprintRecyclerMenuBridge {
    boolean isRecyclerMenuValid(Player player);

    BlueprintRecyclerActionContract.ActionResult handleRecyclerAction(
            ServerPlayer player,
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation expectedInputId,
            int expectedInputCount,
            long expectedStateToken);

    void acceptRecyclerPreview(BlueprintRecyclerPreview preview);
}
