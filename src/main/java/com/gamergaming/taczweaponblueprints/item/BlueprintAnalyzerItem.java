package com.gamergaming.taczweaponblueprints.item;

import java.util.List;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

/** Reads tier identity from its block so inventory and placed station cannot disagree. */
public final class BlueprintAnalyzerItem extends BlockItem {
    public BlueprintAnalyzerItem(Block block, Properties properties) {
        super(block, properties);
        if (!(block instanceof BlueprintRecyclerBlock)) {
            throw new IllegalArgumentException("Blueprint Analyzer item requires an Analyzer block");
        }
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return BlueprintRecyclerBlock.placeCompleteStructure(
                context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        if (com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.stationSetup().analyzer()
                == com.gamergaming.taczweaponblueprints.progression.StationSetup.AnalyzerMode.SINGLE)
            tooltip.add(Component.translatable("item.taczweaponblueprints.station.single").withStyle(ChatFormatting.GRAY));
        else tooltip.add(Component.translatable("item.taczweaponblueprints.blueprint_analyzer.tooltip.tier",
                ((BlueprintRecyclerBlock) getBlock()).tier().level()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.taczweaponblueprints.blueprint_analyzer.tooltip.footprint")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
