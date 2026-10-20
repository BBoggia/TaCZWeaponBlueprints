package com.gamergaming.taczweaponblueprints.block;

import java.util.Objects;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block Blueprint Analyzer. Both halves share one transient two-slot menu.
 */
public final class BlueprintRecyclerBlock extends HorizontalDirectionalBlock {
    public static final com.mojang.serialization.MapCodec<BlueprintRecyclerBlock> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
            AnalyzerTier.CODEC.fieldOf("tier").forGetter(BlueprintRecyclerBlock::tier), propertiesCodec()).apply(instance, BlueprintRecyclerBlock::new));
    @Override public com.mojang.serialization.MapCodec<BlueprintRecyclerBlock> codec() { return CODEC; }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty EXTENSION = BooleanProperty.create("extension");
    private static final int TRANSACTION_FLAGS = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final ThreadLocal<Integer> STRUCTURE_REPLACEMENT_DEPTH = ThreadLocal.withInitial(() -> 0);
    private final VoxelShape[] shapes;
    private final AnalyzerTier tier;

    public BlueprintRecyclerBlock(Properties properties) {
        this(AnalyzerTier.TIER_1, properties);
    }

    public BlueprintRecyclerBlock(AnalyzerTier tier, Properties properties) {
        super(properties);
        this.tier = Objects.requireNonNull(tier, "Analyzer tier");
        double equipmentHeight = switch (tier) {
            case TIER_1 -> 24.4D;
            case TIER_2 -> 28.3D;
            case TIER_3 -> 31.0D;
        };
        shapes = orientations(Shapes.or(box(0, 0, 0, 16, 14.5, 16),
                box(0, 14.5, 11, 16, equipmentHeight, 16),
                box(0, 14.5, 4, 16, tier == AnalyzerTier.TIER_1 ? 19 : 25, 11)));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EXTENSION, false));
    }

    public AnalyzerTier tier() {
        return tier;
    }

    public Component menuTitle() {
        return Component.translatable(switch (tier) {
            case TIER_1 -> "container.taczweaponblueprints.blueprint_recycler";
            case TIER_2 -> "container.taczweaponblueprints.advanced_blueprint_analyzer";
            case TIER_3 -> "container.taczweaponblueprints.experimental_blueprint_analyzer";
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockPos rootPos = rootPosition(pos, state);
            for (Player player : level.players()) {
                if (player.containerMenu instanceof BlueprintRecyclerMenu menu) {
                    menu.invalidateStation(level, rootPos);
                }
            }
        }
        if (!level.isClientSide
                && !structureReplacementInProgress()
                && state.getBlock() != newState.getBlock()) {
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos extensionPos = context.getClickedPos().relative(facing.getClockWise());
        if (!context.getLevel().getWorldBorder().isWithinBounds(extensionPos)
                || !context.getLevel().getBlockState(extensionPos).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos rootPos = rootPosition(pos, state);
            // Older saves have only a root. Claim empty space on first use without
            // replacing neighboring builds, fluids, or plants.
            BlockState root = level.getBlockState(rootPos);
            if (!isComplete(level, rootPos) && root.is(this) && !root.getValue(EXTENSION)
                    && level.getBlockState(counterpartPos(rootPos, root)).isAir()) {
                placeCompleteStructure(level, rootPos, root);
            }
            if (!isComplete(level, rootPos)) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.taczweaponblueprints.blueprint_analyzer.incomplete"), true);
                return InteractionResult.CONSUME;
            }
            serverPlayer.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> BlueprintRecyclerMenu.server(
                                    containerId, inventory, level, rootPos),
                            menuTitle()),
                    buffer -> buffer.writeBlockPos(rootPos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
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
        builder.add(FACING, EXTENSION);
    }

    private static VoxelShape[] orientations(VoxelShape north) {
        VoxelShape east = rotateClockwise(north);
        VoxelShape south = rotateClockwise(east);
        return new VoxelShape[]{north, east, south, rotateClockwise(south)};
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                rotated[0] = Shapes.or(
                        rotated[0],
                        Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX)));
        return rotated[0];
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
                    && (!level.getBlockState(extensionPos).canBeReplaced() || !level.setBlock(
                            extensionPos,
                            state.setValue(EXTENSION, true),
                            Block.UPDATE_ALL))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    public static boolean placeCompleteStructure(
            Level level,
            BlockPos rootPos,
            BlockState targetRoot) {
        if (level == null || rootPos == null || targetRoot == null
                || !(targetRoot.getBlock() instanceof BlueprintRecyclerBlock)
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
        if (!sourceExtension.canBeReplaced()) {
            return false;
        }
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
        if (outcome == AtomicTwoPartReplacement.Outcome.SUCCESS) {
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
        publishCurrentStates(level, rootPos, extensionPos, sourceRoot, sourceExtension);
        return false;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(EXTENSION)) {
            BlockPos rootPos = counterpartPos(pos, state);
            BlockState rootState = level.getBlockState(rootPos);
            if (isCounterpart(state, rootState)) {
                level.destroyBlock(rootPos, !player.isCreative() && rootState.canHarvestBlock(level, rootPos, player), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    public static BlockPos rootPosition(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BlueprintRecyclerBlock)) {
            throw new IllegalArgumentException("state is not a Blueprint Analyzer");
        }
        return state.getValue(EXTENSION) ? counterpartPos(pos, state) : pos;
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

    private static BlockPos counterpartPos(BlockPos pos, BlockState state) {
        Direction widthDirection = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(EXTENSION) ? widthDirection.getOpposite() : widthDirection);
    }

    private static boolean isCounterpart(BlockState state, BlockState candidate) {
        return candidate.getBlock() == state.getBlock()
                && candidate.getValue(FACING) == state.getValue(FACING)
                && candidate.getValue(EXTENSION) != state.getValue(EXTENSION);
    }

    /** Checks both halves; incomplete legacy placements remain available for conversion on use. */
    public static boolean isComplete(BlockGetter level, BlockPos rootPos) {
        BlockState root = level.getBlockState(rootPos);
        return root.getBlock() instanceof BlueprintRecyclerBlock
                && !root.getValue(EXTENSION)
                && isCounterpart(root, level.getBlockState(counterpartPos(rootPos, root)));
    }
}
