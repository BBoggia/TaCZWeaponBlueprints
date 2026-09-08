package com.gamergaming.taczweaponblueprints.progression;

import java.util.Map;
import java.util.WeakHashMap;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Detects changes even when an integrated-server configuration callback is missed. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class StationSetupRuntime {
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final class State {
        StationSetup applied; boolean reloading;
        State(StationSetup value) { applied = value; }
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        STATES.put(event.getServer(), new State(ModConfigs.BLUEPRINT.stationSetup()));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STATES.remove(event.getServer()); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        var current = ModConfigs.BLUEPRINT.stationSetup();
        var state = STATES.get(server);
        if (state == null || state.reloading || current.equals(state.applied)) return;
        state.applied = current;
        state.reloading = true;
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof ResearchBenchMenu || player.containerMenu instanceof BlueprintRecyclerMenu)
                player.closeContainer();
        }
        // Publish immutable settings before rebuilding recipes and dependent research policy.
        ModConfigs.BLUEPRINT.onSyncServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, error) ->
                server.execute(() -> {
                    state.reloading = false;
                    if (error != null) TaCZWeaponBlueprints.LOGGER.error("Could not reload station recipes; run /reload", error);
                }));
    }
}
