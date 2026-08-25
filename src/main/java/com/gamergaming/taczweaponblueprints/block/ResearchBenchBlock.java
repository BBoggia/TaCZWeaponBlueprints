package com.gamergaming.taczweaponblueprints.block;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public final class ResearchBenchBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty EXTENSION = BooleanProperty.create("extension");
    private static final Component TITLE = Component.translatable(
            "container.taczweaponblueprints.research_bench");
    private static final VoxelShape BASE_SHAPE = Block.box(0, 0, 0, 16, 14, 16);
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            BASE_SHAPE, Block.box(0, 14, 0, 16, 27, 4));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            BASE_SHAPE, Block.box(0, 14, 12, 16, 27, 16));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            BASE_SHAPE, Block.box(12, 14, 0, 16, 27, 16));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            BASE_SHAPE, Block.box(0, 14, 0, 4, 27, 16));

    public ResearchBenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(EXTENSION, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The imported model's visual front points opposite vanilla's usual
        // horizontal-facing convention, so retain the player's look direction.
        Direction facing = context.getHorizontalDirection();
        BlockPos extensionPos = context.getClickedPos().relative(facing.getClockWise());
        if (!context.getLevel().getWorldBorder().isWithinBounds(extensionPos)
                || !context.getLevel().getBlockState(extensionPos).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && !state.getValue(EXTENSION)) {
            BlockPos extensionPos = counterpartPos(pos, state);
            level.setBlock(
                    extensionPos,
                    state.setValue(EXTENSION, true),
                    Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(EXTENSION)) {
            BlockPos rootPos = counterpartPos(pos, state);
            BlockState rootState = level.getBlockState(rootPos);
            if (isCounterpart(state, rootState)) {
                level.destroyBlock(rootPos, !player.isCreative(), player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockPos counterpartPos = counterpartPos(pos, state);
            BlockState counterpart = level.getBlockState(counterpartPos);
            if (isCounterpart(state, counterpart)) {
                if (state.getValue(EXTENSION)) {
                    // Environmental removal of the invisible half must not erase
                    // the only loot-bearing half through a shape update.
                    level.destroyBlock(counterpartPos, true);
                } else {
                    level.setBlock(counterpartPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            net.minecraft.world.level.LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        Direction counterpartDirection = state.getValue(EXTENSION)
                ? state.getValue(FACING).getCounterClockWise()
                : state.getValue(FACING).getClockWise();
        if (direction == counterpartDirection && !isCounterpart(state, neighborState)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
            BlockPos rootPos = state.getValue(EXTENSION) ? counterpartPos(pos, state) : pos;
            if (level.getBlockState(rootPos).is(this)) {
                NetworkHooks.openScreen(
                        serverPlayer,
                        menuProvider(level, rootPos),
                        buffer -> buffer.writeBlockPos(rootPos));
            }
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
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> BASE_SHAPE;
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
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EXTENSION);
    }

    private static BlockPos counterpartPos(BlockPos pos, BlockState state) {
        Direction widthDirection = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(EXTENSION) ? widthDirection.getOpposite() : widthDirection);
    }

    private static boolean isCounterpart(BlockState state, BlockState candidate) {
        return candidate.getBlock() == state.getBlock()
                && candidate.getValue(FACING) == state.getValue(FACING)
                && candidate.getValue(EXTENSION) != state.getValue(EXTENSION);
    }

    private static MenuProvider menuProvider(Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> ResearchBenchMenu.server(
                        containerId, inventory, level, pos),
                TITLE);
    }
}
