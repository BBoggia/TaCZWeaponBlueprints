package com.gamergaming.taczweaponblueprints.progression.workbench;

import com.gamergaming.taczweaponblueprints.block.ResearchBenchBlock;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Revalidates a menu-derived workstation context against live server world state. */
public final class ResearchWorkbenchAuthority {
    private ResearchWorkbenchAuthority() {
    }

    public static boolean validForResearch(
            ServerPlayer player,
            ResearchWorkbenchContext context) {
        if (player == null || context == null || player.server == null
                || player.level().isClientSide || !player.server.isSameThread()
                || context.interactionMode() != ResearchInteractionMode.RESEARCH
                || !context.hasSession()
                || !(player.containerMenu instanceof ResearchBenchMenu menu)
                || !menu.authorizesResearchContext(player, context)
                || !player.level().dimension().location().equals(context.dimensionId())
                || !player.level().isLoaded(context.rootPosition())
                || player.distanceToSqr(
                        context.rootPosition().getX() + 0.5D,
                        context.rootPosition().getY() + 0.5D,
                        context.rootPosition().getZ() + 0.5D) > 64.0D
                || !ResearchBenchBlock.isValidRoot(
                        player.level(), context.rootPosition(), context.tier())) {
            return false;
        }
        BlockState root = player.level().getBlockState(context.rootPosition());
        ResourceLocation liveId = ForgeRegistries.BLOCKS.getKey(root.getBlock());
        return context.workstationId().equals(liveId);
    }
}
