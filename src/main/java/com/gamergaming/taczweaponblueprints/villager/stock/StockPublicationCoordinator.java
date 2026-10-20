package com.gamergaming.taczweaponblueprints.villager.stock;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.villager.trade.TradeDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.bus.api.SubscribeEvent;

/** Transfers wait for complete server reload attempts, including failure recovery. */
@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class StockPublicationCoordinator {
    private static final StockReloadBarrier BARRIER=new StockReloadBarrier();
    private record Committed(TradeDataManager.Publication trades,
            com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootSnapshot loot,
            com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot research,
            com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot awards,
            java.util.Optional<ResearchPointAwardDataManager.Failure> awardFailure) {}
    private static Committed committed;
    private static void captureCommitted() {
        committed=new Committed(TradeDataManager.INSTANCE.publication(),BlueprintLootDataManager.INSTANCE.snapshot(),
                BlueprintResearchDataManager.INSTANCE.snapshot(),ResearchPointAwardDataManager.INSTANCE.snapshot(),
                ResearchPointAwardDataManager.INSTANCE.lastFailure());
    }
    private StockPublicationCoordinator() {}
    public static long beginReload(){return BARRIER.begin();}
    public static boolean pending(){return BARRIER.pending();}
    public static void finished(MinecraftServer server,long attempt,Throwable failure){
        var completion=BARRIER.finish(attempt,failure!=null);
        if(completion==StockReloadBarrier.Completion.COMMIT){captureCommitted();return;}
        if(completion!=StockReloadBarrier.Completion.RECOVER)return;
        long generation=BARRIER.generation();
        try {
            // Read immutable committed snapshots, not files: datapack files may
            // already have changed on disk, and invalid active definitions may
            // deliberately retain a prior valid snapshot. Never rerun foreign listeners.
            var previous=java.util.Objects.requireNonNull(committed,"No committed resource snapshot");
            TradeDataManager.INSTANCE.restoreAfterAbortedReload(previous.trades());
            BlueprintLootDataManager.INSTANCE.restoreAfterAbortedReload(previous.loot());
            BlueprintResearchDataManager.INSTANCE.restoreAfterAbortedReload(previous.research());
            ResearchPointAwardDataManager.INSTANCE.restoreAfterAbortedReload(previous.awards(),previous.awardFailure());
            // TaCZ replaces its indexes before a reload completes. A failed
            // attempt may leave those indexes empty: keep our last complete
            // catalog (and magazine capture), reconciling only derived policy.
            if(!com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER
                    .rebuildDerivedPublicationsFromRetainedCatalog())
                throw new IllegalStateException("Recovery could not rebuild retained catalog policy");
            if(com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService.acquire(
                    com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService.Mode.CURRENT_ONLY).isEmpty())
                throw new IllegalStateException("Recovery could not reconcile progression data");
            StockCatalog.clear();
            for(var player:server.getPlayerList().getPlayers()) {
                com.gamergaming.taczweaponblueprints.network.NetworkHandler.syncAllPlayerData(player);
                com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.syncHelp(player);
                com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler.schedule(player);
            }
            BARRIER.recovered(generation);
            TaCZWeaponBlueprints.LOGGER.info("Recovered villager trades from the active resources after an aborted reload");
        }catch(RuntimeException error){
            TaCZWeaponBlueprints.LOGGER.error("Could not reconcile active villager resources; trades remain suspended until a successful reload",error);
            for(var player:server.getPlayerList().getPlayers())
                if(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)player.closeContainer();
        }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void synced(OnDatapackSyncEvent event){
        // Later sync handlers can still fail this reload. Its complete future,
        // not this event, decides when these publications become committed.
        if(event.getPlayer()==null)BARRIER.initialized();
    }
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event){BARRIER.reset(true);committed=null;}
    @SubscribeEvent public static void started(ServerStartedEvent event){captureCommitted();BARRIER.initialized();}
    /** Called at server tick END, after every completion/resource/recipe handler has returned. */
    public static void settle(){BARRIER.settle();}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){BARRIER.reset(false);committed=null;StockCatalog.clear();}
}
