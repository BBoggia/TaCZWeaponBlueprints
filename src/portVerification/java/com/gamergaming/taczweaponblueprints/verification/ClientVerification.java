package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.client.ClientCraftingAccessState;
import com.gamergaming.taczweaponblueprints.init.ModAttachments;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.message.ClientMessageCraft;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=CoreVerification.ID,value=Dist.CLIENT)
public final class ClientVerification {
    static int expected,wait,attempts;
    static boolean connecting,done;
    static final List<String> checks=new ArrayList<>();
    static void expect(int stage){expected=stage;wait=0;if(stage==99||stage<0){done=true;write(stage==99);Minecraft.getInstance().stop();}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done||ReviewVerification.enabled())return;var mc=Minecraft.getInstance();
        try {
            RuntimeIdentity.verify();
            if(mc.player==null){
                if(mc.getOverlay()!=null)return;
                if(FeatureVerification.enabled()) {
                    com.gamergaming.taczweaponblueprints.client.ResearchTreeGuidancePreference.client().dismiss();
                    com.gamergaming.taczweaponblueprints.client.ResearchTreeGuidancePreference.client().dismissOnboarding();
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                }
                if(mc.screen instanceof TitleScreen&&!connecting || mc.screen instanceof DisconnectedScreen && ++wait>80){
                    if(++attempts>3)throw new IllegalStateException("Too many connection attempts");
                    connecting=true;wait=0;
                    if (Boolean.getBoolean("wrb.verifyIntegrated")) {
                        mc.createWorldOpenFlows().openWorld("packaged-integrated-world", () -> mc.setScreen(new TitleScreen()));
                    } else {
                        var address="127.0.0.1:25594";
                        ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("WRB Core QA",address,ServerData.Type.OTHER),false,null);
                    }
                }
                return;
            }
            if(expected==0)return;
            if(++wait>600)throw new IllegalStateException("Client timed out waiting for stage "+expected);
            if (LifecycleVerification.enabled()) { LifecycleClientVerification.tick(mc); return; }
            if (FeatureVerification.enabled()) { FeatureClientVerification.tick(mc); return; }
            if(!(mc.screen instanceof GunSmithTableScreen screen)||!(mc.player.containerMenu instanceof GunSmithTableMenu menu))return;
            var access=ClientCraftingAccessState.snapshot(menu.containerId,menu);
            if(!access.receivedForMode(ModConfigs.BLUEPRINT.enableBlueprints.get()))return;
            var bp=BlueprintDataManager.CLIENT.getBlueprintData(CoreVerification.TARGET.toString());
            if(bp==null)return;
            var id=bp.getRecipeId();boolean shouldKnow=expected!=1&&expected!=5;
            if(shouldKnow&&!access.allowedRecipeIds().contains(id.toString()))return;
            var field=GunSmithTableScreen.class.getDeclaredField("recipes");field.setAccessible(true);
            @SuppressWarnings("unchecked") var recipes=(Map<ResourceLocation,List<ResourceLocation>>)field.get(screen);
            boolean visible=recipes.values().stream().anyMatch(ids->ids.contains(id));
            // A received packet queues one widget rebuild, so allow a frame before asserting.
            if(visible!=shouldKnow&&wait<100)return;
            require(visible==shouldKnow,"native recipe screen visibility at stage "+expected);
            require(access.allowedRecipeIds().contains(id.toString())==shouldKnow,"server allow-list at stage "+expected);
            if(shouldKnow)require(ModAttachments.recipeData(mc.player).orElseThrow().getLearnedBlueprints().contains(CoreVerification.TARGET.toString()),"client learned knowledge at stage "+expected);
            int stage=expected;expected=0;
            if(stage==1){
                mc.player.closeContainer();
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            } else if(stage==3||stage==9)PacketDistributor.sendToServer(new ClientMessageCraft(id,menu.containerId));
            PacketDistributor.sendToServer(new CoreVerification.Probe(stage));
        }catch(Throwable error){CoreVerification.LOG.error("WRB_CORE CLIENT FAILURE",error);done=true;write(false);if(mc.player!=null)PacketDistributor.sendToServer(new CoreVerification.Probe(-1));mc.stop();}
    }
    static void require(boolean condition,String name){if(!condition)throw new IllegalStateException(name);checks.add(name);CoreVerification.LOG.info("WRB_CORE CLIENT PASS {}",name);}
    static void write(boolean passed){try{var obj=new com.google.gson.JsonObject();RuntimeIdentity.addTo(obj);obj.addProperty("passed",passed);obj.addProperty("suite",CoreVerification.suite());obj.add("checks",new com.google.gson.Gson().toJsonTree(checks));java.nio.file.Files.writeString(java.nio.file.Path.of("core-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj));}catch(Exception error){throw new IllegalStateException(error);}}
}
