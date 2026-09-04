package com.gamergaming.taczweaponblueprints.item;

import java.util.List;

import com.gamergaming.taczweaponblueprints.block.ResearchBenchBlock;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Block item that communicates a Research Bench's non-color tier identity. */
public final class ResearchBenchItem extends BlockItem {
    private final ResearchWorkbenchTier tier;

    public ResearchBenchItem(Block block, ResearchWorkbenchTier tier, Properties properties) {
        super(block, properties);
        if (tier == null) {
            throw new IllegalArgumentException("Research Bench tier cannot be null");
        }
        this.tier = tier;
    }

    public ResearchWorkbenchTier tier() {
        return tier;
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return state.getBlock() instanceof ResearchBenchBlock
                && ResearchBenchBlock.placeCompleteStructure(
                        context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "item.taczweaponblueprints.research_bench.tooltip.tier",
                tier.level()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "item.taczweaponblueprints.research_bench.tooltip.footprint")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
