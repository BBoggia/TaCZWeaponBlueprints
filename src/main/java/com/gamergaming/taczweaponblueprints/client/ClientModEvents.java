package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModMenus;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
        modid = TaCZWeaponBlueprints.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.RESEARCH_BENCH.get(), ResearchBenchScreen::new);
            MenuScreens.register(ModMenus.BLUEPRINT_RECYCLER.get(), BlueprintRecyclerScreen::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RESEARCH_BENCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    ModBlocks.ADVANCED_RESEARCH_BENCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    ModBlocks.EXPERIMENTAL_RESEARCH_BENCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    ModBlocks.WORKBENCH_LVL1.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    ModBlocks.WORKBENCH_LVL2.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    ModBlocks.WORKBENCH_LVL3.get(), RenderType.cutout());
        });
    }
}
