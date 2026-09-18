package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in native reload/config checks without spawning or opening a villager. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerTradeDefinitionProbe {
    private static final List<String> results=new ArrayList<>();
    private static Path root,override; private static String original; private static long revision;
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        root=VillagerPrototypeStock.root();if(root==null||Boolean.getBoolean("tacz.villagerQA.stock")||!Boolean.getBoolean("tacz.villagerQA.trades"))return;
        var server=event.getServer();server.execute(()->{
            try {
                MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));
                var report=TradeRuntime.inspect();save("baseline",report);
                require(report.definitionsRevision()>0,"Bundled definitions published");
                require(report.resolution().slots().size()==23,"Twenty core and three conditional support slots inspect without villagers");
                require(report.resolution().slots().values().stream().filter(r->r.role()==TradeDefinition.Role.CORE).allMatch(TradeResolver.Row::ready),"All bundled core pools resolve on reference content");
                if (report.magazineStatus().equals("READY")) require(report.resolution().slots().values().stream()
                        .filter(r -> r.role() == TradeDefinition.Role.SUPPORT).allMatch(TradeResolver.Row::ready),
                        "Magazine support pools include compatible enabled families within the candidate budget");
                require(report.resolution().acquisition().values().stream().allMatch("ready"::equals),"Both professions retain novice progression");
                var cfg=ModConfigs.BLUEPRINT;cfg.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.BANKED_RP);cfg.onSyncServer();
                require(TradeRuntime.inspect().resolution().currency().toString().equals("minecraft:emerald"),"Banked RP selects Emeralds without a bank transaction");
                cfg.researchCurrencyKind.validateAndSet(ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM);cfg.researchCurrencyItem.validateAndSet("minecraft:iron_ingot");cfg.onSyncServer();
                report=TradeRuntime.inspect();save("iron",report);
                var gun=report.resolution().slots().get(id("gunsmith/starter_gun"));
                require(gun.price().orElseThrow().amounts().charged().primary()==28&&gun.price().orElseThrow().amounts().charged().minimumPrimary()==22,"Iron currency normalizes 24+4 to 28, minimum 22");
                require(report.resolution().slots().get(id("gunsmith/premium_gun")).reason().startsWith("fallback:"),"Unrepresentable 64+12 uses an explicit alternate price");
                cfg.researchCurrencyItem.validateAndSet("taczweaponblueprints:scrap");cfg.onSyncServer();
                cfg.villagerMaterialPurchases.validateAndSet(false);cfg.villagerDataSales.validateAndSet(false);cfg.onSyncServer();
                require(TradeRuntime.inspect().resolution().acquisition().get(TradeDefinition.Profession.BALLISTICS_RESEARCHER).equals("no_positive_xp_at_level_1"),"Unavailable Novice progression is diagnosed");
                cfg.villagerMaterialPurchases.validateAndSet(true);cfg.villagerDataSales.validateAndSet(true);cfg.onSyncServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gg villager_trades status");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gg villager_trades export");
                require(Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("reports/villager-trades.json")),"Operator export writes the live table");
                var pack=server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("trade-definition-qa");Files.createDirectories(pack);
                Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Disposable trade definition QA\"}}");
                override=pack.resolve("data/taczweaponblueprints/"+TradeDataManager.DIRECTORY+"/gunsmith/starter_ammo.json");Files.createDirectories(override.getParent());
                try(var input=TaCZWeaponBlueprints.class.getResourceAsStream("/data/taczweaponblueprints/"+TradeDataManager.DIRECTORY+"/gunsmith/starter_ammo.json")){original=new String(Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
                var json=com.google.gson.JsonParser.parseString(original).getAsJsonObject();json.getAsJsonObject("price").addProperty("count",5);Files.writeString(override,json.toString());
                server.getPackRepository().reload();var selected=new ArrayList<>(server.getPackRepository().getSelectedIds());if(!selected.contains("file/trade-definition-qa"))selected.add("file/trade-definition-qa");
                server.reloadResources(selected).whenCompleteAsync((ignored,error)->checkOverride(server,error),server);
            } catch(Throwable failure){finish(server,failure);}
        });
    }
    private static void checkOverride(MinecraftServer server,Throwable failure){
        if(failure!=null){finish(server,failure);return;}
        try{
            var report=TradeRuntime.inspect();save("override",report);revision=report.definitionsRevision();
            require(report.resolution().slots().get(id("gunsmith/starter_ammo")).price().orElseThrow().amounts().originalPrimary()==5,"Higher-priority same-path datapack overrides the bundled definition");
            Files.writeString(override,"{\"version\":1,\"version\":1}");
            ModConfigs.BLUEPRINT.gunBlacklist.validateAndSet(Set.of("tacz:m1911"));ModConfigs.BLUEPRINT.onSyncServer();
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenCompleteAsync((ignored,error)->checkRejection(server,error),server);
        }catch(Throwable error){finish(server,error);}
    }
    private static void checkRejection(MinecraftServer server,Throwable failure){
        if(failure!=null){finish(server,failure);return;}
        try{
            var report=TradeRuntime.inspect();save("rejected",report);
            require(report.definitionsRevision()==revision&&report.reloadFailure().isPresent(),"Malformed reload retains the exact definition revision");
            require(report.resolution().slots().values().stream().flatMap(r->r.candidates().stream()).noneMatch(c->c.id().toString().equals("tacz:m1911")),"Retained definitions cannot sell a newly blocked gun");
            require(report.resolution().slots().get(id("gunsmith/starter_ammo")).price().orElseThrow().amounts().originalPrimary()==5,"Rejected reload cannot partially replace a valid price");
            Files.writeString(override,original);ModConfigs.BLUEPRINT.gunBlacklist.validateAndSet(Set.of());ModConfigs.BLUEPRINT.onSyncServer();
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenCompleteAsync((ignored,error)->checkRecovery(server,error),server);
        }catch(Throwable error){finish(server,error);}
    }
    private static void checkRecovery(MinecraftServer server,Throwable failure){
        if(failure!=null){finish(server,failure);return;}
        try{
            var report=TradeRuntime.inspect();save("recovered",report);
            require(report.definitionsRevision()>revision&&report.reloadFailure().isEmpty(),"Corrected reload publishes and clears the error");
            require(report.resolution().slots().get(id("gunsmith/starter_ammo")).price().orElseThrow().amounts().originalPrimary()==4,"Recovered price is authored default");
            finish(server,null);
        }catch(Throwable error){finish(server,error);}
    }
    private static ResourceLocation id(String value){return new ResourceLocation("taczweaponblueprints",value);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);results.add(message);}
    private static void save(String name,TradeRuntime.Inspection report)throws Exception{Files.writeString(root.resolve("trades-"+name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(TradeDiagnostics.json(report)));}
    private static void finish(MinecraftServer server,Throwable failure){
        if(failure==null)results.add("PASS");else{results.add("FAIL: "+failure);TaCZWeaponBlueprints.LOGGER.error("Trade definition QA failed",failure);}
        try{Files.write(root.resolve("trade-definition-result.txt"),results);}catch(Exception e){TaCZWeaponBlueprints.LOGGER.error("Could not write QA result",e);}server.halt(false);
    }
}
