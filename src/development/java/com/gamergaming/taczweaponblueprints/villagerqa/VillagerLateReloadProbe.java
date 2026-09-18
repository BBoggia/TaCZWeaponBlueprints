package com.gamergaming.taczweaponblueprints.villagerqa;

import java.util.*;
import java.nio.file.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Actual late failure, successful commit, and another late failure against that new commit. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerLateReloadProbe {
    private static int phase,ticks,round;
    private static boolean failNext;
    private static Throwable failure;
    private static TradeSnapshot before;
    private static final ResourceLocation TARGET=new ResourceLocation("taczweaponblueprints:gunsmith/buy_iron");
    private static final List<String> checks=new ArrayList<>();
    private static final class LastSyncFailure {
        @SubscribeEvent(priority=EventPriority.LOWEST) public void synced(OnDatapackSyncEvent event) {
            if(event.getPlayer()==null&&failNext){
                failNext=false;
                throw new IllegalStateException("Disposable QA: failure after datapack sync handlers applied their publications");
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(!Boolean.getBoolean("tacz.villagerQA.lateReload")||event.phase!=TickEvent.Phase.END||VillagerPrototypeStock.root()==null||phase==3)return;
        var server=event.getServer();
        try {
            if(phase==0){
                StockPublicationCoordinator.settle();if(StockPublicationCoordinator.pending()||!StockCatalog.current().coherent())return;
                MinecraftForge.EVENT_BUS.register(new LastSyncFailure());
                start(server);
            }else if(phase==2){
                StockPublicationCoordinator.settle();if(StockPublicationCoordinator.pending()&&ticks++<100)return;
                require(!StockPublicationCoordinator.pending(),"Round "+round+": recovery/commit releases trading after settling");
                var actual=TradeDataManager.INSTANCE.publication().snapshot();
                if(round==1){
                    require(failure==null,"Successful reload completes normally");
                    require(actual.definitions().get(TARGET).primary().count()==before.definitions().get(TARGET).primary().count()-1,
                            "Successful reload applies the new trade price");
                }else{
                    require(failure!=null&&!failNext,"Round "+round+": late listener rejects the native reload future");
                    require(actual.equals(before),"Round "+round+": failed late reload restores the complete last committed trade snapshot");
                }
                if(++round<3)start(server);else{checks.add("PASS");finish(server);}
            }else if(ticks++>400)throw new AssertionError("Reload timeout");
        }catch(Throwable error){checks.add("FAIL: "+error);TaCZWeaponBlueprints.LOGGER.error("Late reload QA",error);finish(server);}
    }
    private static void start(MinecraftServer server)throws Exception {
        before=TradeDataManager.INSTANCE.publication().snapshot();failure=null;failNext=round!=1;ticks=0;
        var pack=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("late-reload-qa");
        Files.createDirectories(pack);Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Disposable late reload QA\"}}");
        com.google.gson.JsonObject row;
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(TaCZWeaponBlueprints.class.getResourceAsStream("/data/taczweaponblueprints/taczweaponblueprints/villager_trades/gunsmith/buy_iron.json")))){row=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();}
        row.getAsJsonObject("price").addProperty("count",before.definitions().get(TARGET).primary().count()-1);
        var file=pack.resolve("data/taczweaponblueprints/taczweaponblueprints/villager_trades/gunsmith/buy_iron.json");Files.createDirectories(file.getParent());Files.writeString(file,row.toString());
        server.getPackRepository().reload();var packs=new ArrayList<>(server.getPackRepository().getSelectedIds());if(!packs.contains("file/late-reload-qa"))packs.add("file/late-reload-qa");
        phase=1;server.reloadResources(packs).whenCompleteAsync((unused,error)->{failure=error;phase=2;},server);
    }
    private static void require(boolean value,String text){if(!value)throw new AssertionError(text);checks.add(text);}
    private static void finish(MinecraftServer server){
        phase=3;try{Files.write(VillagerPrototypeStock.root().resolve("late-reload-result.txt"),checks);}catch(Exception e){throw new RuntimeException(e);}server.halt(false);
    }
}
