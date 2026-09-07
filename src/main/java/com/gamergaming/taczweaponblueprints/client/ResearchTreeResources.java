package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ResearchTreeResources {
    private ResearchTreeResources() { }
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ResearchTreeNodeArt.reloaded());
    }
}
