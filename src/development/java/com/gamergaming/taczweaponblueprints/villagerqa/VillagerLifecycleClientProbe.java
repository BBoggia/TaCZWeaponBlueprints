package com.gamergaming.taczweaponblueprints.villagerqa;

import java.nio.file.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.client.ClientVillagerTradeStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.inventory.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real Minecraft client sends native selection/click/close packets; no server shortcut. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,value=Dist.CLIENT)
public final class VillagerLifecycleClientProbe {
    private static boolean opened;
    private static int ticks,lastAction=-1,selectTick;
    private static JsonObject pending;
    private static String name(){return System.getProperty("tacz.villagerQA.client","");}
    private static Path root(){return Path.of(System.getProperty("tacz.villagerQA.control"));}
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post event){
        if(name().isEmpty()||opened||!(event.getScreen() instanceof TitleScreen)||VillagerPrototypeStock.root()==null)return;
        opened=true;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(name().equals("client-integrated"))mc.createWorldOpenFlows().loadLevel(event.getScreen(),"lifecycle-integrated");
        else ConnectScreen.startConnecting(event.getScreen(),mc,ServerAddress.parseString("127.0.0.1:25610"),new ServerData("Villager lifecycle QA","127.0.0.1:25610",false),false);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(name().isEmpty()||event.phase!=TickEvent.Phase.END||VillagerPrototypeStock.root()==null)return;
        ticks++;if(ticks%5!=0)return;var mc=Minecraft.getInstance();
        try{
            var state=new JsonObject();state.addProperty("ticks",ticks);state.addProperty("screen",mc.screen==null?"none":mc.screen.getClass().getSimpleName());state.addProperty("connected",mc.player!=null);state.addProperty("last_action",lastAction);
            if(mc.player!=null){state.addProperty("container",mc.player.containerMenu.containerId);
                if(mc.player.containerMenu instanceof MerchantMenu menu){state.addProperty("offers",menu.getOffers().size());state.addProperty("status",java.util.stream.IntStream.range(0,menu.getOffers().size()).anyMatch(i->ClientVillagerTradeStatus.row(menu,i).isPresent()));}}
            Files.writeString(root().resolve(name()+"-state.json"),state.toString());
            var file=root().resolve(name()+"-action.json");if(!Files.exists(file))return;
            var action=JsonParser.parseString(Files.readString(file)).getAsJsonObject();int id=action.get("id").getAsInt();if(id<=lastAction)return;
            if(action.get("kind").getAsString().equals("stop")){lastAction=id;mc.stop();return;}
            if(mc.player==null||!(mc.player.containerMenu instanceof MerchantMenu menu)||menu.containerId!=action.get("container").getAsInt())return;
            if(action.get("kind").getAsString().equals("close")){mc.player.closeContainer();lastAction=id;return;}
            if(pending==null||pending.get("id").getAsInt()!=id){
                pending=action;selectTick=ticks;int index=action.get("index").getAsInt();menu.setSelectionHint(index);menu.tryMoveItems(index);mc.getConnection().send(new ServerboundSelectTradePacket(index));return;
            }
            if(ticks-selectTick<20)return;
            if(action.get("kind").getAsString().equals("take"))mc.gameMode.handleInventoryMouseClick(menu.containerId,2,0,ClickType.PICKUP,mc.player);
            lastAction=id;pending=null;
        }catch(Exception e){TaCZWeaponBlueprints.LOGGER.error("Lifecycle client QA failed",e);try{Files.writeString(root().resolve(name()+"-error.txt"),e.toString());}catch(Exception ignored){}}
    }
}
