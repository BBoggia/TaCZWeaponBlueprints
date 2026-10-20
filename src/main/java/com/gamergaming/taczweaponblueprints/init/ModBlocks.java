package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.block.CraftingWorkbenchBlock;
import com.gamergaming.taczweaponblueprints.block.ResearchBenchBlock;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, TaCZWeaponBlueprints.MODID);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, com.gamergaming.taczweaponblueprints.block.VillagerJobSiteBlock> GUNSMITH_STATION =
            BLOCKS.register("gunsmith_station", () -> new com.gamergaming.taczweaponblueprints.block.VillagerJobSiteBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, com.gamergaming.taczweaponblueprints.block.VillagerJobSiteBlock> BALLISTICS_DESK =
            BLOCKS.register("ballistics_desk", () -> new com.gamergaming.taczweaponblueprints.block.VillagerJobSiteBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, ResearchBenchBlock> RESEARCH_BENCH = BLOCKS.register(
            "research_bench",
            () -> researchBench(ResearchWorkbenchTier.TIER_1));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, ResearchBenchBlock> ADVANCED_RESEARCH_BENCH = BLOCKS.register(
            "advanced_research_bench",
            () -> researchBench(ResearchWorkbenchTier.TIER_2));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, ResearchBenchBlock> EXPERIMENTAL_RESEARCH_BENCH = BLOCKS.register(
            "experimental_research_bench",
            () -> researchBench(ResearchWorkbenchTier.TIER_3));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, CraftingWorkbenchBlock> WORKBENCH_LVL1 = BLOCKS.register(
            "workbench_lvl1",
            () -> craftingWorkbench(ResearchWorkbenchTier.TIER_1, 31.25D));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, CraftingWorkbenchBlock> WORKBENCH_LVL2 = BLOCKS.register(
            "workbench_lvl2",
            () -> craftingWorkbench(ResearchWorkbenchTier.TIER_2, 31.25D));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, CraftingWorkbenchBlock> WORKBENCH_LVL3 = BLOCKS.register(
            "workbench_lvl3",
            () -> craftingWorkbench(ResearchWorkbenchTier.TIER_3, 31.25D));

    private static ResearchBenchBlock researchBench(ResearchWorkbenchTier tier) {
        return new ResearchBenchBlock(tier, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .noOcclusion()
                    .sound(SoundType.WOOD));
    }

    private static CraftingWorkbenchBlock craftingWorkbench(
            ResearchWorkbenchTier tier,
            double height) {
        return new CraftingWorkbenchBlock(tier, height, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .sound(SoundType.METAL));
    }

    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> BLUEPRINT_RECYCLER = BLOCKS.register(
            "blueprint_recycler",
            () -> analyzer(AnalyzerTier.TIER_1));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, BlueprintRecyclerBlock> ADVANCED_BLUEPRINT_ANALYZER = BLOCKS.register(
            "advanced_blueprint_analyzer", () -> analyzer(AnalyzerTier.TIER_2));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, BlueprintRecyclerBlock> EXPERIMENTAL_BLUEPRINT_ANALYZER = BLOCKS.register(
            "experimental_blueprint_analyzer", () -> analyzer(AnalyzerTier.TIER_3));

    private static BlueprintRecyclerBlock analyzer(AnalyzerTier tier) {
        return new BlueprintRecyclerBlock(tier, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.METAL));
    }

    private ModBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
