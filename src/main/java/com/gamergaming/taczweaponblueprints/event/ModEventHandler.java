package com.gamergaming.taczweaponblueprints.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService;
import com.gamergaming.taczweaponblueprints.progression.StartingBlueprintGrantService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModEventHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Fzzy Config completes its NeoForge configuration task before play begins.
            StartingBlueprintGrantService.applyConfiguredGrants(serverPlayer);
            NetworkHandler.syncAllPlayerData(serverPlayer);
            ResearchPointPresentationService.syncHelp(serverPlayer);
            ResearchPointAwardReconciliationScheduler.schedule(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            NetworkHandler.syncPlayerRecipeData(serverPlayer);
            ResearchPointPresentationService.syncHelp(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            NetworkHandler.syncPlayerRecipeData(serverPlayer);
            ResearchPointPresentationService.syncHelp(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // This event precedes the actual mode change. The end-of-tick
            // progression flush evaluates Creative bypasses from the new mode.
            BlueprintProgressionSyncScheduler.markDirty(serverPlayer);
        }
    }
}
