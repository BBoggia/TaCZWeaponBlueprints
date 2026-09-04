package com.gamergaming.taczweaponblueprints.progression.workbench;

import com.gamergaming.taczweaponblueprints.block.CraftingWorkbenchBlock;
import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Revalidates the physical source of a native TaCZ crafting menu. */
public final class CraftingWorkbenchAuthority {
    private CraftingWorkbenchAuthority() {
    }

    public static boolean valid(
            ServerPlayer player,
            GunSmithTableMenu menu,
            ResearchWorkbenchContext context) {
        if (player == null || menu == null || context == null || player.server == null
                || player.level().isClientSide || !player.server.isSameThread()
                || player.containerMenu != menu
                || context.interactionMode() != ResearchInteractionMode.CRAFTING
                || !context.hasSession()
                || context.sessionId() != (long) menu.containerId + 1L
                || !(menu instanceof TaCZWorkbenchMenuBridge bridge)
                || bridge.taczweaponblueprints$workbenchContext()
                        .filter(context::equals).isEmpty()
                || !player.level().dimension().location().equals(context.dimensionId())
                || !player.level().isLoaded(context.rootPosition())
                || player.distanceToSqr(
                        context.rootPosition().getX() + 0.5D,
                        context.rootPosition().getY() + 0.5D,
                        context.rootPosition().getZ() + 0.5D) > 64.0D) {
            return false;
        }

        ResourceLocation menuId = menu.getBlockId();
        if (menuId == null || !menuId.equals(context.workstationId())) {
            return false;
        }
        CraftingWorkbenchTierResolver.Resolution current =
                CraftingWorkbenchTierResolver.resolve(
                        menuId, ModConfigs.BLUEPRINT.researchFeatureSnapshot());
        if (current.tier() != context.tier()) {
            return false;
        }

        if (CraftingWorkbenchTierResolver.isNativeCraftingWorkbench(menuId)) {
            if (!CraftingWorkbenchBlock.isValidRoot(
                    player.level(), context.rootPosition(), context.tier())) {
                return false;
            }
            ResourceLocation liveId = ForgeRegistries.BLOCKS.getKey(
                    player.level().getBlockState(context.rootPosition()).getBlock());
            return menuId.equals(liveId);
        }

        BlockState state = player.level().getBlockState(context.rootPosition());
        if (!(state.getBlock() instanceof AbstractGunSmithTableBlock table)
                || !table.getRootPos(context.rootPosition(), state)
                        .equals(context.rootPosition())
                || !(player.level().getBlockEntity(context.rootPosition())
                        instanceof GunSmithTableBlockEntity blockEntity)) {
            return false;
        }
        ResourceLocation liveId = blockEntity.getId() == null
                ? DefaultAssets.DEFAULT_BLOCK_ID
                : blockEntity.getId();
        return menuId.equals(liveId);
    }

    public static boolean valid(ServerPlayer player, GunSmithTableMenu menu) {
        return menu instanceof TaCZWorkbenchMenuBridge bridge
                && bridge.taczweaponblueprints$workbenchContext()
                        .filter(context -> valid(player, menu, context)).isPresent();
    }
}
