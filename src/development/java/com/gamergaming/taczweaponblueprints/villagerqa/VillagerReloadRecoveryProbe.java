package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.villager.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

/** Failed/overlapping native reload acceptance, confined to explicitly marked disposable worlds. */
@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerReloadRecoveryProbe {
 private static boolean armed,nest,nestedFinished,done;
 private static int tick,phase,finishedAt,initialPrice;
 private static MinecraftServer server;
 private static net.minecraft.server.level.ServerPlayer player;
 private static MerchantMenu menu;
 private static net.minecraft.world.item.trading.MerchantOffer offer;
 private static ItemStack payment;
 private static List<String> attemptedPacks;
 private static final List<String> checks=new ArrayList<>();
 private static final net.minecraft.resources.ResourceLocation AMMO=net.minecraft.resources.ResourceLocation.parse("taczweaponblueprints:gunsmith/starter_ammo");
 private static boolean enabled(){return Boolean.getBoolean("tacz.villagerQA.reloadRecovery")&&VillagerPrototypeStock.root()!=null;}
 @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void listeners(AddReloadListenerEvent event){
  if(!enabled())return;
  event.addListener(new SimplePreparableReloadListener<Boolean>(){
   protected Boolean prepare(ResourceManager r,ProfilerFiller p){return armed;}
   protected void apply(Boolean fail,ResourceManager r,ProfilerFiller p){
    if(!fail)return;
    require(TradeDataManager.INSTANCE.publication().snapshot().definitions().get(AMMO).primary().count()==9,"Failed attempt really published changed trade data before aborting");
    if(nest){nest=false;server.reloadResources(attemptedPacks).whenComplete((unused,error)->{
      require(error!=null&&StockPublicationCoordinator.pending(),"Nested failure cannot unlock its still-running parent reload");nestedFinished=true;
    });}
    throw new IllegalStateException("Intentional QA foreign listener failure");
   }
  });
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(!enabled()||done)return;
  server=event.getServer();tick++;
  try{
   if(tick>1000)throw new AssertionError("Reload recovery probe timed out");
   if(tick==20){
    NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(),null));StockPublicationCoordinator.settle();
    var level=server.overworld();var v=new Villager(EntityType.VILLAGER,level);v.moveTo(.5,85,.5);v.setNoAi(true);v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GUNSMITH.get()));level.addFreshEntity(v);
    player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ReloadRecovery"));player.moveTo(.5,85,2.5);player.setHealth(20);v.setTradingPlayer(player);
    offer=v.getOffers().stream().filter(o->((OwnedVillagerOffer)o).taczweaponblueprints$binding().entry().kind()==TradeDefinition.Kind.AMMO).findFirst().orElseThrow();
    menu=new MerchantMenu(81,player.getInventory(),v);player.containerMenu=menu;menu.setSelectionHint(v.getOffers().indexOf(offer));payment=offer.getCostA().copy();menu.getSlot(0).set(payment.copy());
    require(((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player)&&!menu.getSlot(2).getItem().isEmpty(),"Initial funded native quote is usable");
    initialPrice=TradeDataManager.INSTANCE.publication().snapshot().definitions().get(AMMO).primary().count();
    var pack=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("reload-recovery-qa");Files.createDirectories(pack);
    Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Disposable failed reload data\"}}");
    String relative="data/taczweaponblueprints/taczweaponblueprints/villager_trades/gunsmith/starter_ammo.json";
    try(var stream=TaCZWeaponBlueprints.class.getResourceAsStream("/"+relative)){
     var json=com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();json.getAsJsonObject("price").addProperty("count",9);
     var file=pack.resolve(relative);Files.createDirectories(file.getParent());Files.writeString(file,json.toString());
    }
    server.getPackRepository().reload();attemptedPacks=new ArrayList<>(server.getPackRepository().getSelectedIds());attemptedPacks.add("file/reload-recovery-qa");
    armed=true;phase=1;attempt(2);
   }
   if(phase==2&&tick-finishedAt>=10){checkRecovered();nest=true;phase=3;attempt(4);}
   if(phase==4&&tick-finishedAt>=10){
    require(nestedFinished,"Nested native reload callback completed");checkRecovered();int uses=offer.getUses();menu.clicked(2,0,ClickType.PICKUP,player);
    require(ItemStack.matches(menu.getCarried(),offer.getResult())&&offer.getUses()==uses+1&&menu.getSlot(0).getItem().isEmpty(),"Recovered funded trade delivers exactly one output and charges once");
    armed=false;phase=5;server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused,error)->{
      if(error!=null)fail(error);else{finishedAt=tick;phase=6;}
    });
   }
   if(phase==6&&tick-finishedAt>=10){require(!StockPublicationCoordinator.pending(),"Subsequent successful native reload settles normally");checks.add("PASS");finish();}
  }catch(Throwable e){fail(e);}
 }
 private static void attempt(int next){server.reloadResources(attemptedPacks).whenComplete((unused,error)->{
   if(error==null)fail(new AssertionError("QA failure did not abort reload"));else{TaCZWeaponBlueprints.LOGGER.info("QA observed reload failure",error);finishedAt=tick;phase=next;}
 });}
 private static void checkRecovered(){
  require(!StockPublicationCoordinator.pending(),"Aborted reload barrier clears after recovery");
  require(TradeDataManager.INSTANCE.publication().snapshot().definitions().get(AMMO).primary().count()==initialPrice,"Recovery restores active-resource prices instead of partially applied data");
  require(player.containerMenu==menu&&((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player)&&ItemStack.matches(payment,menu.getSlot(0).getItem())&&menu.getCarried().isEmpty(),"Recovery preserves unchanged quote and exact held payment");
 }
 private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);}
 private static void fail(Throwable e){checks.add("FAIL: "+e);TaCZWeaponBlueprints.LOGGER.error("Reload recovery QA failed",e);finish();}
 private static void finish(){done=true;try{Files.write(VillagerPrototypeStock.root().resolve("reload-recovery-result.txt"),checks);}catch(Exception e){throw new RuntimeException(e);}server.halt(false);}
}
