package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.progression.BlueprintDiscoveryService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlueprintDiscoveryEvents {
    private BlueprintDiscoveryEvents() {
    }

    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Forge fires this event after the picked-up stack has been inserted.
            // Unopened loot and failed/cancelled pickup attempts never reach it.
            BlueprintDiscoveryService.discoverInventoryBlueprint(serverPlayer, event.getStack());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer) {
            BlueprintProgressionSyncScheduler.flush(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            BlueprintProgressionSyncScheduler.clear(serverPlayer);
            NetworkHandler.clearPlayerSyncState(serverPlayer);
        }
    }
}
