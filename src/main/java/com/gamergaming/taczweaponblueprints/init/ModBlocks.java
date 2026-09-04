package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.block.CraftingWorkbenchBlock;
import com.gamergaming.taczweaponblueprints.block.ResearchBenchBlock;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, TaCZWeaponBlueprints.MODID);

    public static final RegistryObject<ResearchBenchBlock> RESEARCH_BENCH = BLOCKS.register(
            "research_bench",
            () -> researchBench(ResearchWorkbenchTier.TIER_1));

    public static final RegistryObject<ResearchBenchBlock> ADVANCED_RESEARCH_BENCH = BLOCKS.register(
            "advanced_research_bench",
            () -> researchBench(ResearchWorkbenchTier.TIER_2));

    public static final RegistryObject<ResearchBenchBlock> EXPERIMENTAL_RESEARCH_BENCH = BLOCKS.register(
            "experimental_research_bench",
            () -> researchBench(ResearchWorkbenchTier.TIER_3));

    public static final RegistryObject<CraftingWorkbenchBlock> WORKBENCH_LVL1 = BLOCKS.register(
            "workbench_lvl1",
            () -> craftingWorkbench(ResearchWorkbenchTier.TIER_1, 31.25D));

    public static final RegistryObject<CraftingWorkbenchBlock> WORKBENCH_LVL2 = BLOCKS.register(
            "workbench_lvl2",
            () -> craftingWorkbench(ResearchWorkbenchTier.TIER_2, 28.25D));

    public static final RegistryObject<CraftingWorkbenchBlock> WORKBENCH_LVL3 = BLOCKS.register(
            "workbench_lvl3",
            () -> craftingWorkbench(ResearchWorkbenchTier.TIER_3, 28.0D));

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

    public static final RegistryObject<Block> BLUEPRINT_RECYCLER = BLOCKS.register(
            "blueprint_recycler",
            () -> new BlueprintRecyclerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.METAL)));

    private ModBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
