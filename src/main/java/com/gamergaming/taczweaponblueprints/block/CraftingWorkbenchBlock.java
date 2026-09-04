package com.gamergaming.taczweaponblueprints.block;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/** A two-block TaCZ crafting workstation with an authenticated progression tier. */
public final class CraftingWorkbenchBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty EXTENSION = BooleanProperty.create("extension");

    private static final int TRANSACTION_FLAGS =
            Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final ThreadLocal<Integer> STRUCTURE_REPLACEMENT_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private final ResearchWorkbenchTier tier;
    private final VoxelShape shape;

    public CraftingWorkbenchBlock(
            ResearchWorkbenchTier tier,
            double height,
            Properties properties) {
        super(properties);
        if (tier == null || !Double.isFinite(height) || height <= 0.0D || height > 32.0D) {
            throw new IllegalArgumentException("invalid crafting Workbench definition");
        }
        this.tier = tier;
        this.shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, height, 16.0D);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(EXTENSION, false));
    }

    public ResearchWorkbenchTier tier() {
        return tier;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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
            if (!isCounterpart(state, level.getBlockState(extensionPos))
                    && !level.setBlock(
                            extensionPos,
                            state.setValue(EXTENSION, true),
                            Block.UPDATE_ALL)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Places both halves before BlockItem consumes the held Workbench item. */
    public static boolean placeCompleteStructure(
            Level level,
            BlockPos rootPos,
            BlockState targetRoot) {
        if (level == null || rootPos == null || targetRoot == null
                || !(targetRoot.getBlock() instanceof CraftingWorkbenchBlock)
                || targetRoot.getValue(EXTENSION)) {
            return false;
        }
        BlockPos extensionPos = counterpartPos(rootPos, targetRoot);
        if (!level.getWorldBorder().isWithinBounds(rootPos)
                || !level.getWorldBorder().isWithinBounds(extensionPos)) {
            return false;
        }

        BlockState sourceRoot = level.getBlockState(rootPos);
        BlockState sourceExtension = level.getBlockState(extensionPos);
        BlockState targetExtension = targetRoot.setValue(EXTENSION, true);
        AtomicTwoPartReplacement.Outcome outcome;
        beginStructureReplacement();
        try {
            outcome = AtomicTwoPartReplacement.replace(
                    sourceRoot,
                    sourceExtension,
                    targetRoot,
                    targetExtension,
                    new AtomicTwoPartReplacement.Access<>() {
                        @Override
                        public void write(
                                AtomicTwoPartReplacement.Part part,
                                BlockState state) {
                            level.setBlock(
                                    part == AtomicTwoPartReplacement.Part.FIRST
                                            ? rootPos
                                            : extensionPos,
                                    state,
                                    TRANSACTION_FLAGS);
                        }

                        @Override
                        public BlockState read(AtomicTwoPartReplacement.Part part) {
                            return level.getBlockState(
                                    part == AtomicTwoPartReplacement.Part.FIRST
                                            ? rootPos
                                            : extensionPos);
                        }
                    });
        } finally {
            endStructureReplacement();
        }

        if (outcome != AtomicTwoPartReplacement.Outcome.SUCCESS) {
            publishCurrentStates(
                    level, rootPos, extensionPos, sourceRoot, sourceExtension);
            return false;
        }
        publishReplacement(
                level,
                rootPos,
                extensionPos,
                sourceRoot,
                sourceExtension,
                targetRoot,
                targetExtension);
        return true;
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
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean moving) {
        if (!level.isClientSide
                && !structureReplacementInProgress()
                && state.getBlock() != newState.getBlock()) {
            BlockPos counterpartPos = counterpartPos(pos, state);
            BlockState counterpart = level.getBlockState(counterpartPos);
            if (isCounterpart(state, counterpart)) {
                if (state.getValue(EXTENSION)) {
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
            LevelAccessor level,
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
            BlockPos rootPos = rootPosition(pos, state);
            if (isValidRoot(level, rootPos, tier)) {
                openCrafting(serverPlayer, level, rootPos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openCrafting(ServerPlayer player, Level level, BlockPos rootPos) {
        ResourceLocation workstationId = ForgeRegistries.BLOCKS.getKey(this);
        if (workstationId == null) {
            return;
        }
        NetworkHooks.openScreen(
                player,
                new net.minecraft.world.SimpleMenuProvider(
                        (containerId, inventory, ignoredPlayer) -> {
                            GunSmithTableMenu menu = new GunSmithTableMenu(
                                    containerId, inventory, workstationId);
                            ((TaCZWorkbenchMenuBridge) menu)
                                    .taczweaponblueprints$attachWorkbenchContext(
                                            new ResearchWorkbenchContext(
                                                    rootPos,
                                                    level.dimension().location(),
                                                    workstationId,
                                                    tier,
                                                    ResearchInteractionMode.CRAFTING,
                                                    (long) containerId + 1L));
                            return menu;
                        },
                        Component.translatable(getDescriptionId())),
                buffer -> buffer.writeResourceLocation(workstationId));
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return shape;
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

    public static BlockPos rootPosition(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CraftingWorkbenchBlock)) {
            throw new IllegalArgumentException("state is not a crafting Workbench");
        }
        return state.getValue(EXTENSION) ? counterpartPos(pos, state) : pos;
    }

    public static boolean isValidRoot(
            BlockGetter level,
            BlockPos rootPos,
            ResearchWorkbenchTier expectedTier) {
        return expectedTier != null
                && tierAtValidRoot(level, rootPos).filter(expectedTier::equals).isPresent();
    }

    public static Optional<ResearchWorkbenchTier> tierAtValidRoot(
            BlockGetter level,
            BlockPos rootPos) {
        if (level == null || rootPos == null) {
            return Optional.empty();
        }
        BlockState root = level.getBlockState(rootPos);
        if (!(root.getBlock() instanceof CraftingWorkbenchBlock workbench)
                || root.getValue(EXTENSION)
                || !isCounterpart(
                        root,
                        level.getBlockState(counterpartPos(rootPos, root)))) {
            return Optional.empty();
        }
        return Optional.of(workbench.tier);
    }

    private static BlockPos counterpartPos(BlockPos pos, BlockState state) {
        Direction widthDirection = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(EXTENSION)
                ? widthDirection.getOpposite()
                : widthDirection);
    }

    private static boolean isCounterpart(BlockState state, BlockState candidate) {
        return candidate.getBlock() == state.getBlock()
                && candidate.getValue(FACING) == state.getValue(FACING)
                && candidate.getValue(EXTENSION) != state.getValue(EXTENSION);
    }

    private static void publishReplacement(
            Level level,
            BlockPos rootPos,
            BlockPos extensionPos,
            BlockState sourceRoot,
            BlockState sourceExtension,
            BlockState targetRoot,
            BlockState targetExtension) {
        level.sendBlockUpdated(rootPos, sourceRoot, targetRoot, Block.UPDATE_ALL);
        level.sendBlockUpdated(extensionPos, sourceExtension, targetExtension, Block.UPDATE_ALL);
        level.updateNeighborsAt(rootPos, targetRoot.getBlock());
        level.updateNeighborsAt(extensionPos, targetExtension.getBlock());
    }

    private static void publishCurrentStates(
            Level level,
            BlockPos rootPos,
            BlockPos extensionPos,
            BlockState sourceRoot,
            BlockState sourceExtension) {
        BlockState currentRoot = level.getBlockState(rootPos);
        BlockState currentExtension = level.getBlockState(extensionPos);
        level.sendBlockUpdated(rootPos, sourceRoot, currentRoot, Block.UPDATE_ALL);
        level.sendBlockUpdated(
                extensionPos, sourceExtension, currentExtension, Block.UPDATE_ALL);
        level.updateNeighborsAt(rootPos, currentRoot.getBlock());
        level.updateNeighborsAt(extensionPos, currentExtension.getBlock());
    }

    private static void beginStructureReplacement() {
        STRUCTURE_REPLACEMENT_DEPTH.set(STRUCTURE_REPLACEMENT_DEPTH.get() + 1);
    }

    private static void endStructureReplacement() {
        int depth = STRUCTURE_REPLACEMENT_DEPTH.get() - 1;
        if (depth <= 0) {
            STRUCTURE_REPLACEMENT_DEPTH.remove();
        } else {
            STRUCTURE_REPLACEMENT_DEPTH.set(depth);
        }
    }

    private static boolean structureReplacementInProgress() {
        return STRUCTURE_REPLACEMENT_DEPTH.get() > 0;
    }

}
