package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlueprintResourceEvents {
    private BlueprintResourceEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(BlueprintLootDataManager.INSTANCE);
        event.addListener(BlueprintResearchDataManager.INSTANCE);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // A null player indicates a server-wide datapack reload. TaCZ has finished
        // applying its resources by this point, so rebuild and publish one complete
        // catalog snapshot before synchronizing clients.
        if (event.getPlayer() != null) {
            return;
        }

        if (BlueprintDataManager.SERVER.initialize(event.getPlayerList().getServer())) {
            event.getPlayers().forEach(NetworkHandler::syncAllPlayerData);
        } else {
            // The catalog retains its last-known-good snapshot. Research data may
            // still have changed, so republish Journals against that stable catalog.
            event.getPlayers().forEach(NetworkHandler::syncJournalData);
        }
    }
}
