package com.gamergaming.taczweaponblueprints.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * Pins the current two-block structure before the implementation is generalized
 * for workstation tiers. Source assertions are intentional here: constructing
 * an unregistered Block after Minecraft's test bootstrap freezes registries is
 * invalid, while the geometry helpers are private and world-free.
 */
class ResearchBenchCompatibilityBaselineTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));
    private static final Path BLOCK_SOURCE = PROJECT.resolve(
            "src/main/java/com/gamergaming/taczweaponblueprints/block/ResearchBenchBlock.java");
    private static final Path MENU_SOURCE = PROJECT.resolve(
            "src/main/java/com/gamergaming/taczweaponblueprints/menu/ResearchBenchMenu.java");

    @Test
    void rootIsTheOnlyDefaultAuthoritativeHalf() throws IOException {
        String source = compact(Files.readString(BLOCK_SOURCE));

        assertTrue(source.contains("setValue(FACING, Direction.NORTH)"));
        assertTrue(source.contains("setValue(EXTENSION, false)"));
        assertTrue(source.contains("if (!level.isClientSide && !state.getValue(EXTENSION))"));
    }

    @Test
    void rootAndExtensionNormalizeAcrossTheClockwiseWidthAxis() throws IOException {
        String source = compact(Files.readString(BLOCK_SOURCE));

        assertTrue(source.contains("Direction widthDirection = state.getValue(FACING).getClockWise()"));
        assertTrue(source.contains(
                "return pos.relative(state.getValue(EXTENSION) ? "
                        + "widthDirection.getOpposite() : widthDirection)"));
        assertTrue(source.contains("candidate.getValue(FACING) == state.getValue(FACING)"));
        assertTrue(source.contains(
                "candidate.getValue(EXTENSION) != state.getValue(EXTENSION)"));
    }

    @Test
    void eitherClickedHalfOpensTheMenuAtTheNormalizedRoot() throws IOException {
        String source = compact(Files.readString(BLOCK_SOURCE));

        assertTrue(source.contains(
                "BlockPos rootPos = rootPosition(pos, state)"));
        assertTrue(source.contains("isValidRoot(level, rootPos, tier)"));
        assertTrue(source.contains("menuProvider(level, rootPos)"));
        assertTrue(source.contains("buffer.writeBlockPos(rootPos)"));
        assertTrue(source.contains("buffer.writeVarInt(tier.level())"));
    }

    @Test
    void menuValidityUsesTheSharedExactTierStructureValidator() throws IOException {
        String source = compact(Files.readString(MENU_SOURCE));

        assertTrue(source.contains(
                "ResearchBenchBlock.isValidRoot(level, pos, workbenchTier)"));
        assertTrue(source.contains("player.distanceToSqr("));
        assertTrue(source.contains("<= 64.0D"));
    }

    @Test
    void onlyTheRootHalfCanProduceBlockLoot() throws IOException {
        var loot = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/data/taczweaponblueprints/loot_tables/blocks/"
                        + "research_bench.json")))
                .getAsJsonObject();
        String serialized = loot.toString();

        assertTrue(serialized.contains("minecraft:block_state_property"));
        assertTrue(serialized.contains("taczweaponblueprints:research_bench"));
        assertTrue(serialized.contains("\"extension\":\"false\""));
    }

    @Test
    void generalizedBlockRetainsEveryTwoBlockSafetyBoundary() throws IOException {
        String source = compact(Files.readString(BLOCK_SOURCE));

        assertTrue(source.contains("getWorldBorder().isWithinBounds(extensionPos)"));
        assertTrue(source.contains("getBlockState(extensionPos).canBeReplaced(context)"));
        assertTrue(source.contains("void playerWillDestroy("));
        assertTrue(source.contains("void onRemove("));
        assertTrue(source.contains("BlockState updateShape("));
        assertTrue(source.contains("BlockState rotate("));
        assertTrue(source.contains("BlockState mirror("));
        assertTrue(source.contains("PushReaction.BLOCK"));
        assertTrue(source.contains("candidate.getBlock() == state.getBlock()"));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
    }
}
