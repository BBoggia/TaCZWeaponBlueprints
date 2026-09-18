package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.villager.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native renderer and MerchantScreen acceptance, only in marked disposable QA clients. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,value=Dist.CLIENT)
public final class VillagerPresentationProbe {
 private static int ticks,step,waitUntil;private static boolean opened,queued,reloaded;private static Villager trader;
 private static Path root(){return VillagerPrototypeStock.root();}
 private static boolean enabled(){return Boolean.getBoolean("tacz.villagerQA.presentation")&&root()!=null;}
 @SubscribeEvent public static void screen(ScreenEvent.Init.Post e){
  if(!enabled()||opened||!(e.getScreen() instanceof TitleScreen))return;opened=true;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
  org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();
  mc.createWorldOpenFlows().loadLevel(e.getScreen(),"lifecycle-integrated");
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
  if(!enabled()||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;ticks++;
  try{
   if(ticks>2200)throw new AssertionError("Presentation timeout step "+step);
   if(ticks<50||ticks<waitUntil)return;
   if(Boolean.getBoolean("tacz.villagerQA.skins")){
    if(step==4){Files.writeString(root().resolve("skin-result.txt"),"PASS: both native profession overlays rendered across seven biomes, normal/zombie models, and front/side/back views. Visual inspection required.\n");mc.stop();return;}
    if(!(mc.screen instanceof Gallery)){mc.setScreen(new Gallery(step<2?step:step+1));waitUntil=ticks+35;return;}
    capture(step<2?"biomes-"+step:"outfit-"+(step-2));step++;mc.setScreen(null);return;
   }
   if(step<3){if(!(mc.screen instanceof Gallery)){mc.setScreen(new Gallery(step));waitUntil=ticks+35;return;}capture("gallery-"+step);step++;mc.setScreen(null);return;}
   if(step>=3&&step<=10){
    if(!queued){queued=true;int at=step;
     mc.options.guiScale().set(at%3+1);mc.options.forceUnicodeFont().set(at>=6);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),at>=6?1024:1280,at>=6?768:800);mc.resizeDisplay();
     mc.getSingleplayerServer().execute(()->openMerchant(at));waitUntil=ticks+40;return;
    }
    if(!(mc.screen instanceof MerchantScreen m))return;
    capture("merchant-"+step+"-scale-"+mc.options.guiScale().get()+"-unicode-"+mc.options.forceUnicodeFont().get());
    mc.player.closeContainer();queued=false;step++;waitUntil=ticks+15;return;
   }
   if(step==11&&!reloaded){reloaded=true;mc.reloadResourcePacks().thenRun(()->{mc.setScreen(new Gallery(0));waitUntil=ticks+40;step=12;});return;}
   if(step==12){capture("resource-reload");
    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),720,480);mc.options.guiScale().set(4);mc.resizeDisplay();
    var journal=new com.gamergaming.taczweaponblueprints.client.BlueprintJournalScreen();mc.setScreen(journal);
    var field=journal.getClass().getDeclaredField("onboardingView");field.setAccessible(true);
    if(!field.getBoolean(journal)){var method=journal.getClass().getDeclaredMethod("toggleOnboarding");method.setAccessible(true);method.invoke(journal);}
    step=13;waitUntil=ticks+30;return;
   }
   if(step==13){capture("onboarding-top");mc.screen.keyPressed(269,0,0);step=14;waitUntil=ticks+30;return;}
   if(step==14){capture("onboarding-bottom");var field=mc.screen.getClass().getDeclaredField("onboardingScroll");field.setAccessible(true);if(field.getInt(mc.screen)<=0)throw new AssertionError("Small-screen guide did not scroll");mc.screen.keyPressed(268,0,0);if(field.getInt(mc.screen)!=0)throw new AssertionError("Home did not reset guide");Files.writeString(root().resolve("presentation-result.txt"),"PASS: native biome/zombie/level render galleries, both job blocks, merchant GUI scales 1–3, alternate fonts, suspended/sold-out/empty states, magazine names, long tooltips, resource reload, and keyboard-scrollable small-screen onboarding. Visual inspection required.\n");step=15;mc.stop();}

  }catch(Throwable ex){try{Files.writeString(root().resolve("presentation-result.txt"),"FAIL: "+ex);}catch(Exception ignored){}TaCZWeaponBlueprints.LOGGER.error("Presentation QA failed",ex);mc.stop();}
 }
 private static void openMerchant(int at){
  var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);p.closeContainer();
  if(trader!=null)trader.discard();trader=new Villager(EntityType.VILLAGER,p.serverLevel());trader.setNoAi(true);trader.setNoGravity(true);trader.moveTo(p.getX()+1,p.getY(),p.getZ());trader.setVillagerData(trader.getVillagerData().setProfession(at%2==0?ModVillagers.BALLISTICS_RESEARCHER.get():ModVillagers.GUNSMITH.get()));p.serverLevel().addFreshEntity(trader);
  trader.getOffers();try{for(int i=1;i<5;i++)net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(Villager.class,"m_35528_").invoke(trader);}catch(Exception ex){throw new RuntimeException(ex);}
  if(at==7){trader.getOffers().clear();}
  if(at==8){var o=trader.getOffers().get(0);o.setToOutOfStock();}
  if(at==6){var o=trader.getOffers().get(0);o.getResult().setHoverName(Component.literal("An unusually long custom-pack weapon and research supply display name for wrapping checks"));}
  trader.setTradingPlayer(p);trader.openTradingScreen(p,trader.getDisplayName(),trader.getVillagerData().getLevel());
 }
 @SubscribeEvent public static void render(ScreenEvent.Render.Post e){
  if(!enabled()||!(e.getScreen() instanceof MerchantScreen m))return;
  // Exercise the production arrow tooltip using native GUI coordinates.
  var mc=Minecraft.getInstance();int left=(m.width-276)/2,top=(m.height-166)/2;
  int selected=0;
  if(step==9)for(int i=0;i<m.getMenu().getOffers().size();i++)if(com.gamergaming.taczweaponblueprints.client.ClientVillagerTradeStatus.row(m.getMenu(),i).map(r->(r.flags()&4)!=0).orElse(false)){selected=i;break;}
  if(step==10&&!m.getMenu().getOffers().isEmpty()){
   var item=m.getMenu().getOffers().get(0).getResult().copy();item.setHoverName(Component.literal("An unusually long custom-pack weapon display name with additional descriptive text to check native tooltip wrapping"));e.getGuiGraphics().renderTooltip(mc.font,item,left+220,top+48);
  }else com.gamergaming.taczweaponblueprints.client.ClientVillagerTradeStatus.render(e.getGuiGraphics(),m.getMenu(),mc.font,left,top,0,selected,left+197,top+45);
 }
 private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(root().toFile(),name+".png",mc.getMainRenderTarget(),ignored->{});}
 private static final class Gallery extends Screen {
  private final int page;private final List<LivingEntity> entities=new ArrayList<>();
  Gallery(int page){super(Component.literal("Villager presentation QA"));this.page=page;var mc=Minecraft.getInstance();
   if(page!=2)for(boolean zombie:new boolean[]{false,true})for(var type:page>2?List.of(VillagerType.PLAINS,VillagerType.PLAINS,VillagerType.PLAINS):List.of(VillagerType.PLAINS,VillagerType.DESERT,VillagerType.JUNGLE,VillagerType.SAVANNA,VillagerType.SNOW,VillagerType.SWAMP,VillagerType.TAIGA)){
    var data=new VillagerData(type,page==0||page==3?ModVillagers.GUNSMITH.get():ModVillagers.BALLISTICS_RESEARCHER.get(),entities.size()%5+1);
    if(zombie){var v=new ZombieVillager(EntityType.ZOMBIE_VILLAGER,mc.level);v.setVillagerData(data);entities.add(v);}else{var v=new Villager(EntityType.VILLAGER,mc.level);v.setVillagerData(data);entities.add(v);}
   }
  }
  public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float delta){
   g.fill(0,0,width,height,0xff16232b);g.drawCenteredString(font,page==0||page==3?"GUNSMITH · biome clothing / zombie clothing":page==1||page==4?"BALLISTICS RESEARCHER · biome clothing / zombie clothing":"JOB SITES · inventory models",width/2,12,0xe9dec0);
   if(page>2){
    for(int i=0;i<entities.size();i++){
     int column=i%3,x=width*(column*2+1)/6,y=i<3?height/2-6:height-16;
     var entity=entities.get(i);float yaw=column==0?180:column==1?240:360;
     entity.yBodyRot=entity.yHeadRot=entity.yHeadRotO=yaw;entity.setYRot(yaw);
     InventoryScreen.renderEntityInInventory(g,x,y-4,Math.min(150,(height-80)/5),new org.joml.Quaternionf().rotateZ((float)Math.PI),new org.joml.Quaternionf(),entity);
     g.drawCenteredString(font,(i<3?"Villager":"Zombie")+" · "+new String[]{"Front","Side","Back"}[column],x,y+3,0xc8d6da);
    }
    return;
   }
   if(page<2){String[] names={"Plains","Desert","Jungle","Savanna","Snow","Swamp","Taiga"};
    for(int i=0;i<entities.size();i++){int x=(i%7)*width/7+width/14,y=i<7?height/2-12:height-18;g.drawCenteredString(font,names[i%7]+" · "+(i%5+1),x,y+3,0xc8d6da);InventoryScreen.renderEntityInInventoryFollowsMouse(g,x,y-5,Math.min(width/14,(height-80)/5),15,0,entities.get(i));}
   }else{var items=List.of(ModItems.GUNSMITH_STATION.get(),ModItems.BALLISTICS_DESK.get());for(int i=0;i<2;i++){int x=width/4+i*width/2;g.pose().pushPose();g.pose().translate(x-48,height/2-48,0);g.pose().scale(6,6,6);g.renderItem(new ItemStack(items.get(i)),0,0);g.pose().popPose();g.drawCenteredString(font,items.get(i).getDescription(),x,height/2+64,0xe9dec0);}}
  }
 }
}
