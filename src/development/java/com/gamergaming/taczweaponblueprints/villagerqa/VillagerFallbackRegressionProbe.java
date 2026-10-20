package com.gamergaming.taczweaponblueprints.villagerqa;

import java.util.*;
import java.nio.file.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid="taczweaponblueprints")
public final class VillagerFallbackRegressionProbe {
    private static boolean done;
    private static final List<String> evidence=new ArrayList<>();
    private static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    private static TradeDefinition copy(TradeDefinition d, ResourceLocation name, Role role, Output output, List<ResourceLocation> fallbacks) {
        return new TradeDefinition(name,d.profession(),d.level(),d.slot(),role,true,output,d.primary(),d.secondary(),d.uses(),d.xp(),d.demandMultiplier(),d.minimumBasisPoints(),d.requiredMods(),fallbacks);
    }
    private static void publish(Map<ResourceLocation,TradeDefinition> definitions) {
        TradeDataManager.INSTANCE.restoreAfterAbortedReload(new TradeDataManager.Publication(new TradeSnapshot(definitions),0,Optional.empty()));
        StockCatalog.clear();
    }
    private static void check(boolean condition,String text) {
        if(!condition)throw new AssertionError(text);
        evidence.add(text);
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if(done||!Boolean.getBoolean("tacz.villagerQA.fallbackRegression")||VillagerPrototypeStock.root()==null)return;
        StockPublicationCoordinator.settle();
        if(StockPublicationCoordinator.pending()||!StockCatalog.current().coherent())return;
        done=true;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.OnDatapackSyncEvent(event.getServer().getPlayerList(),null));
        StockPublicationCoordinator.settle();
        var previous=TradeDataManager.INSTANCE.publication();
        try {
            var defaults=previous.snapshot().definitions();
            var starter=defaults.get(id("taczweaponblueprints:gunsmith/starter_gun"));
            var mag=defaults.get(id("taczweaponblueprints:gunsmith/magazine_support_2"));
            evidence.add("Magazine catalog: "+com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCatalog.INSTANCE.publication().catalog().status());
            var target=starter.output().candidates().stream().map(Choice::id).filter(g->StockCatalog.current().magazine(g).status()==StockPlanner.SupplyStatus.READY).findFirst().orElseThrow();var supply=StockCatalog.current().magazine(target);
            evidence.add("Selected known starter gun requiring supply: "+target);
            check(supply.status()==StockPlanner.SupplyStatus.READY,"Precondition: Starter gun has a ready required magazine family");
            var fallbackId=id("review:starter_fallback");
            var fallback=copy(starter,fallbackId,Role.FALLBACK,new Output(Kind.GUN,1,1,Selection.STARTER,List.of(new Choice(target,1))),List.of());
            var root=copy(starter,starter.id(),Role.CORE,new Output(Kind.GUN,1,1,Selection.STARTER,List.of(new Choice(id("missing:gun"),1))),List.of(fallbackId));
            var defs=new TreeMap<>(defaults);defs.put(root.id(),root);defs.put(fallbackId,fallback);publish(defs);
            var catalog=StockCatalog.current();
            check(catalog.alternatives.get(root.slot()).stream().anyMatch(r->r.definition().equals(fallbackId)&&r.ready()),"Precondition: fallback gun passes current content, tier, price and configuration validation");
            check(catalog.inspection.resolution().slots().get(root.slot()).ready(),"Operator candidate report says starter fallback gun is ready");
            var plan=StockPlanner.create(Profession.GUNSMITH,catalog.alternatives,catalog,0).plan();
            check(plan.entries().get(root.slot()).target().equals(target),"Automatic magazine discovery permits the valid fallback gun");
            issued(event.getServer().overworld(),ModVillagers.GUNSMITH.get(),Kind.GUN,target);
            check(catalog.inspection.resolution().slots().get(mag.slot()).ready(),"Automatic support includes the matching fallback-gun family");
            defs.put(mag.id(),copy(mag,mag.id(),Role.SUPPORT,new Output(Kind.MAGAZINE,1,0,Selection.STARTER,List.of(new Choice(supply.family().orElseThrow(),1))),mag.fallbacks()));publish(defs);
            catalog=StockCatalog.current();plan=StockPlanner.create(Profession.GUNSMITH,catalog.alternatives,catalog,0).plan();
            check(plan.entries().get(root.slot()).target().equals(target),"CONTROL: explicit support candidate allows the identical fallback gun and ammo to be planned");

            // A valid study fallback behind a globally ready but incompatible attachment.
            var fragments=defaults.get(id("taczweaponblueprints:ballistics_researcher/fragments_2"));
            var attachment=defaults.get(id("taczweaponblueprints:gunsmith/basic_attachment"));
            var studyId=id("review:study_fallback");
            var study=copy(fragments,studyId,Role.FALLBACK,fragments.output(),List.of());
            var studyRoot=copy(fragments,fragments.id(),Role.CORE,attachment.output(),List.of(studyId));
            var note=defaults.get(id("taczweaponblueprints:ballistics_researcher/note"));
            defs=new TreeMap<>();defs.put(note.id(),note);defs.put(studyRoot.id(),studyRoot);defs.put(studyId,study);publish(defs);
            catalog=StockCatalog.current();var rows=catalog.alternatives.get(studyRoot.slot());
            check(rows.get(0).ready()&&rows.get(1).ready(),"Precondition: attachment and study fallback both have globally valid current candidates");
            plan=StockPlanner.create(Profession.BALLISTICS_RESEARCHER,catalog.alternatives,catalog,0).plan();
            check(plan.reachable()&&plan.entries().get(studyRoot.slot()).kind()==Kind.ANALYSIS_FRAGMENTS,"Incompatible attachment uses its valid study fallback");
            issued(event.getServer().overworld(),ModVillagers.BALLISTICS_RESEARCHER.get(),Kind.ANALYSIS_FRAGMENTS,null);
            defs.put(studyRoot.id(),copy(studyRoot,studyRoot.id(),Role.CORE,study.output(),List.of()));publish(defs);
            catalog=StockCatalog.current();plan=StockPlanner.create(Profession.BALLISTICS_RESEARCHER,catalog.alternatives,catalog,0).plan();
            check(plan.entries().containsKey(studyRoot.slot()),"CONTROL: same study pool works when selected directly");
            evidence.add("PASS");
        } catch(Throwable e) {
            evidence.add("FAIL: "+e);
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.error("Final review probe",e);
        } finally {
            TradeDataManager.INSTANCE.restoreAfterAbortedReload(previous);
            try{Files.write(VillagerPrototypeStock.root().resolve("fallback-regression-result.txt"),evidence);}catch(Exception e){throw new RuntimeException(e);}
            event.getServer().halt(false);
        }
    }
    private static void issued(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.npc.VillagerProfession profession,Kind kind,ResourceLocation target)throws Exception {
        var villager=new Villager(EntityType.VILLAGER,level);villager.setNoAi(true);villager.moveTo(.5,85,.5);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));level.addFreshEntity(villager);
        try{
            villager.getOffers();
            net.neoforged.fml.util.ObfuscationReflectionHelper.findMethod(Villager.class,"increaseMerchantCareer").invoke(villager);
            check(villager.getOffers().stream().anyMatch(offer->{
                var owned=(OwnedVillagerOffer)offer;var binding=owned.taczweaponblueprints$binding();
                return binding!=null&&binding.entry().kind()==kind&&(target==null||binding.entry().target().equals(target))
                        &&owned.taczweaponblueprints$terms().available();
            }),"Native factories issue an available "+kind+" fallback with its required dependencies");
        }finally{villager.discard();}
    }
}
