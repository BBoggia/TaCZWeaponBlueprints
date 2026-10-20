package com.gamergaming.taczweaponblueprints.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A single-block job site. Its four facing states all belong to the same POI. */
public final class VillagerJobSiteBlock extends HorizontalDirectionalBlock {
    public static final com.mojang.serialization.MapCodec<VillagerJobSiteBlock> CODEC = simpleCodec(VillagerJobSiteBlock::new);
    @Override public com.mojang.serialization.MapCodec<VillagerJobSiteBlock> codec() { return CODEC; }

    public VillagerJobSiteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.item.Item.TooltipContext level, java.util.List<net.minecraft.network.chat.Component> lines,
            net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable(getDescriptionId()+".hint")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.or(Block.box(0,0,0,16,13,16),Block.box(2,13,2,14,16,14));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
