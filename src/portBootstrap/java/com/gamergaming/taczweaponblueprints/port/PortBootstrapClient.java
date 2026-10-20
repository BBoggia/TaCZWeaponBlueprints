package com.gamergaming.taczweaponblueprints.port;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Reports readiness after resources have loaded and the title screen can tick. */
@EventBusSubscriber(modid = PortBootstrap.MOD_ID, value = Dist.CLIENT)
public final class PortBootstrapClient {
    private static boolean reported;

    private PortBootstrapClient() {}

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!reported && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            reported = true;
            LogUtils.getLogger().info("WRB_PORT_BOOTSTRAP client title screen ready");
            if (Boolean.getBoolean("wrb.portBootstrap.exitAfterReady")) {
                minecraft.stop();
            }
        }
    }
}
