package com.gamergaming.taczweaponblueprints.block;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * Additive single-block workstation for physical RP turn-ins. Interaction is
 * activated only when the dedicated client screen is registered.
 */
public final class BlueprintRecyclerBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            box(1, 2, 1, 15, 11, 15),
            box(0, 11, 0, 16, 13, 16),
            box(2, 13, 10, 14, 16, 15),
            box(4, 7, 0, 12, 11, 3),
            box(3, 3, 0, 13, 6, 2),
            box(2, 0, 2, 5, 2, 5),
            box(11, 0, 2, 14, 2, 5),
            box(2, 0, 11, 14, 2, 14));
    private static final VoxelShape EAST_SHAPE = rotateClockwise(NORTH_SHAPE);
    private static final VoxelShape SOUTH_SHAPE = rotateClockwise(EAST_SHAPE);
    private static final VoxelShape WEST_SHAPE = rotateClockwise(SOUTH_SHAPE);
    private static final Component TITLE = Component.translatable(
            "container.taczweaponblueprints.blueprint_recycler");

    public BlueprintRecyclerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                FACING, context.getHorizontalDirection().getOpposite());
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
            NetworkHooks.openScreen(
                    serverPlayer,
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> BlueprintRecyclerMenu.server(
                                    containerId, inventory, level, pos),
                            TITLE),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                rotated[0] = Shapes.or(
                        rotated[0],
                        Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX)));
        return rotated[0];
    }
}
