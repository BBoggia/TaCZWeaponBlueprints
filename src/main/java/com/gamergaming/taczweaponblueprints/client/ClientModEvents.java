package com.gamergaming.taczweaponblueprints.client;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModMenus;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = TaCZWeaponBlueprints.MODID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(ModMenus.RESEARCH_BENCH.get(), ResearchBenchScreen::new);
        event.register(ModMenus.BLUEPRINT_RECYCLER.get(), BlueprintRecyclerScreen::new);
    }

    @SubscribeEvent
    public static void registerItemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new com.gamergaming.taczweaponblueprints.client.renderer.item.BlueprintItemClientExtensions(),
                com.gamergaming.taczweaponblueprints.init.ModItems.BLUEPRINT_ITEM.get());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
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
