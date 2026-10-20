package com.gamergaming.taczweaponblueprints.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.progression.BlueprintDiscoveryService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BlueprintDiscoveryEvents {
    private BlueprintDiscoveryEvents() {
    }

    @SubscribeEvent
    public static void onItemPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
            // NeoForge fires this event after the picked-up stack has been inserted.
            // Unopened loot and failed/cancelled pickup attempts never reach it.
            BlueprintDiscoveryService.discoverInventoryBlueprint(serverPlayer, event.getOriginalStack());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencySyncTracker.tick(serverPlayer);
            ResearchPointAwardReconciliationScheduler.process(serverPlayer);
            BlueprintProgressionSyncScheduler.flush(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencySyncTracker.clear(serverPlayer);
            BlueprintProgressionSyncScheduler.clear(serverPlayer);
            com.gamergaming.taczweaponblueprints.command.sub.ResearchCurrencyMigrationCommand.PENDING.clear(serverPlayer.getUUID());
            ResearchPointAwardReconciliationScheduler.clear(serverPlayer);
            NetworkHandler.clearPlayerSyncState(serverPlayer);
        }
    }
}
