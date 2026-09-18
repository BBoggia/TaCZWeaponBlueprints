package com.gamergaming.taczweaponblueprints.villagerqa;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.server.level.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind;

/** Accelerated native purchases; measures material/trade/restock effort, never real-world gathering time. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerBalanceProbe {
 private static ServerLevel level;private static int serial;private static JsonArray runs=new JsonArray();
 @SubscribeEvent public static void start(ServerStartedEvent e){
  var root=VillagerPrototypeStock.root();if(root==null||!Boolean.getBoolean("tacz.villagerQA.balance"))return;
  e.getServer().execute(()->{try{
   level=e.getServer().overworld();net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.OnDatapackSyncEvent(e.getServer().getPlayerList(),null));StockPublicationCoordinator.settle();
   var out=TradeDiagnostics.json(TradeRuntime.inspect());var catalog=new JsonArray();
   var context=ProgressionPolicyAccessService.acquire(ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElseThrow();
   for(var data:context.catalog().blueprints().entrySet()){
    var j=new JsonObject();j.addProperty("id",data.getKey().toString());j.addProperty("kind",data.getValue().getKind().name());j.addProperty("recipe",data.getValue().getRecipe().getId().toString());
    var ingredients=new JsonArray();for(var i:data.getValue().getRecipe().getInputs()){var input=new JsonObject();input.add("ingredient",i.getIngredient().toJson());input.addProperty("count",i.getCount());ingredients.add(input);}j.add("ingredients",ingredients);
    context.policyFor(data.getKey()).ifPresent(policy->{j.addProperty("research_tier",policy.researchWorkbenchTier().level());j.addProperty("research_cost",BlueprintResearchDataManager.INSTANCE.policyFor(data.getKey(),null).researchCost().toString());});
    if(data.getValue().getKind()==com.gamergaming.taczweaponblueprints.item.BlueprintKind.GUN)j.addProperty("fragment_threshold",AnalysisReconstructionResolver.resolve(com.gamergaming.taczweaponblueprints.progression.BlueprintWeaponAnalysisService.context(),context.profileId(),data.getKey(),AnalyzerTier.TIER_3).policy().threshold());j.addProperty("policy",context.policyFor(data.getKey()).map(Object::toString).orElse("none"));catalog.add(j);
   }out.add("catalog",catalog);
   for(boolean strict:new boolean[]{false,true})for(boolean discount:new boolean[]{false,true})for(String cohort:List.of("new","established","fully_learned"))for(var prof:List.of(ModVillagers.GUNSMITH.get(),ModVillagers.BALLISTICS_RESEARCHER.get()))run(strict,discount,cohort,prof);
   var halls=new JsonArray();for(boolean strict:new boolean[]{false,true})for(boolean discount:new boolean[]{false,true})for(int count:new int[]{1,10})halls.add(hall(strict,discount,count));out.add("halls",halls);
   ModConfigs.BLUEPRINT.villagerMaterialPurchases.validateAndSet(true);ModConfigs.BLUEPRINT.onSyncServer();
   out.add("native_careers",runs);out.addProperty("method","Supplied exact inputs; native MerchantMenu pickup, native XP thresholds; accelerated native level-up/restock. No gathering or day-length timing claim.");
   Files.writeString(root.resolve("balance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));Files.writeString(root.resolve("balance-result.txt"),"PASS: 24 native novice-to-master career scenarios; default/strict, normal/floor prices, new/established/fully learned customers.\n");
  }catch(Throwable ex){TaCZWeaponBlueprints.LOGGER.error("Balance QA failed",ex);try{Files.writeString(root.resolve("balance-result.txt"),"FAIL: "+ex);}catch(Exception ignored){}}
  finally{e.getServer().halt(false);}});
 }
 private static void run(boolean strict,boolean discount,String cohort,VillagerProfession prof)throws Exception{
  ModConfigs.BLUEPRINT.villagerMaterialPurchases.validateAndSet(!strict);ModConfigs.BLUEPRINT.onSyncServer();
  var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes((cohort+serial).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Balance"+serial++));p.moveTo(.5,85,2.5);p.setHealth(20);p.getInventory().clearContent();
  var known=p.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();int n=0;
  for(var id:new TreeSet<>(BlueprintDataManager.SERVER.getBlueprintDataMap().keySet()))if(cohort.equals("fully_learned")||cohort.equals("established")&&n++%2==0)known.addBlueprint(id.toString());
  var v=new Villager(EntityType.VILLAGER,level);v.setUUID(UUID.nameUUIDFromBytes(("career"+prof.name()+cohort).getBytes(java.nio.charset.StandardCharsets.UTF_8)));v.setNoAi(true);v.setNoGravity(true);v.moveTo(.5,85,.5);v.setVillagerData(v.getVillagerData().setProfession(prof));level.addFreshEntity(v);v.getOffers();
  var j=new JsonObject();j.addProperty("profession",prof.name());j.addProperty("strict",strict);j.addProperty("discount",discount);j.addProperty("cohort",cohort);var purchases=new JsonArray();int restocks=0;
  while(v.getVillagerData().getLevel()<5){
   if(purchases.size()>400)throw new AssertionError("Career not reachable");
   if(v.getVillagerXp()>=VillagerData.getMaxXpPerLevel(v.getVillagerData().getLevel())){
    net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(Villager.class,"m_35528_").invoke(v);
    if(v.getVillagerData().getLevel()==2&&prof==ModVillagers.GUNSMITH.get()){
     var g=v.getOffers().stream().filter(o->binding(o).kind()==Kind.GUN).findFirst().orElseThrow();
     j.addProperty("trades_before_first_gun",purchases.size());j.addProperty("restocks_before_first_gun",restocks);j.add("starter_purchase",buy(p,v,g,discount));
     var supply=new JsonArray();for(var dependency:binding(g).dependencies()){
      var o=v.getOffers().stream().filter(x->binding(x).slot().equals(dependency)).findFirst().orElseThrow();
      if(o.isOutOfStock()){v.restock();restocks++;}supply.add(buy(p,v,o,discount));
     }j.add("starter_supplies",supply);
    }continue;
   }
   var offer=v.getOffers().stream().filter(o->!o.isOutOfStock()&&o.getXp()>0).max(Comparator.comparingInt(MerchantOffer::getXp)).orElse(null);
   if(offer==null){v.restock();if(++restocks>80)throw new AssertionError("Restock failed");continue;}
   purchases.add(buy(p,v,offer,discount));
  }
  var gun=v.getOffers().stream().filter(o->binding(o).kind()==Kind.GUN).findFirst();
  if(prof==ModVillagers.GUNSMITH.get()&&gun.isEmpty())throw new AssertionError("No starter gun");
  if(gun.isPresent()){
   var g=gun.orElseThrow();if(g.isOutOfStock())v.restock();j.add("first_gun_quote",quote(g,discount));j.addProperty("ammo",StockCatalog.current().ammo(binding(g).target()).orElseThrow().toString());j.addProperty("magazine",StockCatalog.current().magazine(binding(g).target()).toString());
  }
  j.addProperty("level",v.getVillagerData().getLevel());j.addProperty("xp",v.getVillagerXp());j.addProperty("restocks",restocks);j.add("purchases",purchases);var stock=new JsonArray();
  for(var o:v.getOffers()){var row=quote(o,discount);row.addProperty("uses",o.getMaxUses());row.addProperty("level",binding(o).level());stock.add(row);}j.add("stock",stock);runs.add(j);p.closeContainer();v.discard();
 }
 private static JsonObject hall(boolean strict,boolean discount,int count)throws Exception {
  ModConfigs.BLUEPRINT.villagerMaterialPurchases.validateAndSet(!strict);ModConfigs.BLUEPRINT.onSyncServer();var out=new JsonObject();out.addProperty("villagers_per_profession",count);out.addProperty("strict",strict);out.addProperty("discount",discount);int payout=0;var inputs=new TreeMap<String,Integer>();
  for(var profession:List.of(ModVillagers.GUNSMITH.get(),ModVillagers.BALLISTICS_RESEARCHER.get()))for(int i=0;i<count;i++){
   var v=new Villager(EntityType.VILLAGER,level);v.setNoAi(true);v.setNoGravity(true);v.moveTo(.5,85,.5);v.setVillagerData(v.getVillagerData().setProfession(profession));level.addFreshEntity(v);v.getOffers();for(int at=1;at<5;at++)net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(Villager.class,"m_35528_").invoke(v);
   for(var o:v.getOffers())if(binding(o).kind()==Kind.CURRENCY){if(discount)o.setSpecialPriceDiff(-999);payout+=o.getResult().getCount()*o.getMaxUses();inputs.merge(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(o.getCostA().getItem()).toString(),o.getCostA().getCount()*o.getMaxUses(),Integer::sum);}v.discard();
  }out.addProperty("combined_currency_per_stock",payout);out.add("inputs",new Gson().toJsonTree(inputs));if(strict&&payout!=0)throw new AssertionError("Strict hall still buys materials");return out;
 }
 private static StockPlan.Entry binding(MerchantOffer o){return ((OwnedVillagerOffer)o).taczweaponblueprints$binding().entry();}
 private static JsonObject quote(MerchantOffer o,boolean discount){if(discount)o.setSpecialPriceDiff(-999);var j=new JsonObject();j.addProperty("target",binding(o).target().toString());j.addProperty("kind",binding(o).kind().name());j.addProperty("input_a",o.getCostA().toString());j.addProperty("input_a_count",o.getCostA().getCount());j.addProperty("input_b",o.getCostB().toString());j.addProperty("output",o.getResult().toString());j.addProperty("output_count",o.getResult().getCount());return j;}
 private static JsonObject buy(ServerPlayer p,Villager v,MerchantOffer o,boolean discount){
  p.closeContainer();v.setTradingPlayer(p);var j=quote(o,discount);var m=new MerchantMenu(1,p.getInventory(),v);p.containerMenu=m;m.setSelectionHint(v.getOffers().indexOf(o));m.getSlot(0).set(o.getCostA().copy());m.getSlot(1).set(o.getCostB().copy());int uses=o.getUses();
  m.clicked(2,0,ClickType.PICKUP,p);if(m.getCarried().isEmpty()||o.getUses()!=uses+1||!m.getSlot(0).getItem().isEmpty()||!m.getSlot(1).getItem().isEmpty())throw new AssertionError("Native purchase failed");
  j.addProperty("level",v.getVillagerData().getLevel());m.setCarried(ItemStack.EMPTY);p.closeContainer();p.getInventory().clearContent();return j;
 }
}
