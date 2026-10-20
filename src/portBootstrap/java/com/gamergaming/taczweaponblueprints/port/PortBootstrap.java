package com.gamergaming.taczweaponblueprints.port;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/** Development-only dependency probe; does not load the unported addon. */
@Mod(PortBootstrap.MOD_ID)
public final class PortBootstrap {
    public static final String MOD_ID = "wrb_port_bootstrap";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PortBootstrap(IEventBus modBus) {
        modBus.addListener(this::loaded);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void loaded(FMLLoadCompleteEvent event) {
        for (String id : new String[]{"minecraft", "neoforge", "tacz", "fzzy_config", "kotlinforforge"}) {
            var info = ModList.get().getModContainerById(id).orElseThrow(
                    () -> new IllegalStateException("Missing bootstrap dependency: " + id)).getModInfo();
            LOGGER.info("WRB_PORT_BOOTSTRAP dependency {}={}", id, info.getVersion());
        }
        LOGGER.info("WRB_PORT_BOOTSTRAP load complete; Java {}", Runtime.version());
    }

    private void serverStarted(ServerStartedEvent event) {
        LOGGER.info("WRB_PORT_BOOTSTRAP dedicated server ready");
        if (Boolean.getBoolean("wrb.portBootstrap.exitAfterReady")) {
            event.getServer().halt(false);
        }
    }
}
