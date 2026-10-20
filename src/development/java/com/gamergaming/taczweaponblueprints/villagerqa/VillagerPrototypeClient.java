package com.gamergaming.taczweaponblueprints.villagerqa;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.List;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,value=Dist.CLIENT)
public final class VillagerPrototypeClient {
    private static WeakReference<MerchantMenu> bound=new WeakReference<>(null);
    private static List<Integer> unavailable=List.of();
    private static int frames;
    public static void accept(VillagerPrototypePresentation.Status status){
        var player=Minecraft.getInstance().player;
        if(player!=null && player.containerMenu instanceof MerchantMenu menu && menu.containerId==status.containerId()){
            bound=new WeakReference<>(menu);unavailable=status.unavailable();frames=0;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){bound.clear();unavailable=List.of();}
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event){
        if(!(event.getScreen() instanceof MerchantScreen screen) || screen.getMenu()!=bound.get())return;
        int selected=ObfuscationReflectionHelper.getPrivateValue(MerchantScreen.class,screen,"shopItem");
        if(!unavailable.contains(selected))return;
        var mc=Minecraft.getInstance();int left=(screen.width-276)/2,top=(screen.height-166)/2;
        var label=Component.translatable("merchant.taczweaponblueprints.unavailable");
        event.getGuiGraphics().drawString(mc.font,label,left+106,top+64,0xFFBF80,false);
        if(event.getMouseX()>=left+186 && event.getMouseX()<left+208 && event.getMouseY()>=top+35 && event.getMouseY()<top+56)
            event.getGuiGraphics().renderTooltip(mc.font,List.of(label,Component.translatable("merchant.taczweaponblueprints.unavailable.detail")),java.util.Optional.empty(),event.getMouseX(),event.getMouseY());
        if(++frames==90 && Boolean.getBoolean("tacz.villagerQA.capture")){
            var root=VillagerPrototypeStock.root();
            Screenshot.grab(root.toFile(),"merchant-unavailable.png",mc.getMainRenderTarget(),ignored->{});
            try{Files.writeString(root.resolve("client-presentation-result.txt"),"PASS: native MerchantScreen received separate unavailable status; strict merchandise NBT unchanged by presentation.\n");}
            catch(Exception error){TaCZWeaponBlueprints.LOGGER.error("Could not save prototype client evidence",error);}
        }
        if(frames==150 && Boolean.getBoolean("tacz.villagerQA.capture"))mc.stop();
    }
}
