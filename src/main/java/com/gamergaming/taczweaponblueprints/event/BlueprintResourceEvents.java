package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService;
import com.gamergaming.taczweaponblueprints.progression.StartingBlueprintGrantService;

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
        event.addListener(ResearchPointAwardDataManager.INSTANCE);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // A null player indicates a server-wide datapack reload. TaCZ has finished
        // applying its resources by this point, so rebuild and publish one complete
        // catalog snapshot before synchronizing clients.
        if (event.getPlayer() != null) {
            return;
        }

        boolean catalogUpdated = BlueprintDataManager.SERVER.initialize(event.getPlayerList().getServer());
        BlueprintResearchDataManager.INSTANCE.logActiveProfileAudit();
        if (catalogUpdated) {
            event.getPlayers().forEach(player -> {
                StartingBlueprintGrantService.applyConfiguredGrants(player);
                NetworkHandler.syncAllPlayerData(player);
                ResearchPointPresentationService.syncHelp(player);
                ResearchPointAwardReconciliationScheduler.schedule(player);
            });
        } else {
            // Catalog initialization publishes only a complete catalog map, but a
            // later evidence, automatic-placement, or policy stage may fail. Rebuild
            // every revision-coupled publication against the current complete map
            // before synchronizing players.
            boolean recovered = BlueprintDataManager.SERVER
                    .rebuildDerivedPublicationsFromRetainedCatalog();
            if (!recovered) {
                TaCZWeaponBlueprints.LOGGER.error(
                        "Unable to reconcile progression policy with the retained blueprint "
                                + "catalog; research remains fail-closed until a successful reload");
            }
            event.getPlayers().forEach(player -> {
                var grants = StartingBlueprintGrantService.applyConfiguredGrants(player);
                if (grants.changed()) {
                    NetworkHandler.syncPlayerRecipeData(player);
                } else {
                    NetworkHandler.syncJournalData(player);
                }
                ResearchPointPresentationService.syncHelp(player);
                ResearchPointAwardReconciliationScheduler.schedule(player);
            });
        }
    }
}
