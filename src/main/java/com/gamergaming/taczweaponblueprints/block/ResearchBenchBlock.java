package com.gamergaming.taczweaponblueprints.block;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class ResearchBenchBlock extends Block {
    private static final Component TITLE = Component.translatable(
            "container.taczweaponblueprints.research_bench");

    public ResearchBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, menuProvider(level, pos), buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static MenuProvider menuProvider(Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> ResearchBenchMenu.server(
                        containerId, inventory, level, pos),
                TITLE);
    }
}
