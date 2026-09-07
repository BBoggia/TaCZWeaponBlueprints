package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.DisplayUnitNumber;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in screen checks, isolated by the launcher's disposable game directory. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,value=Dist.CLIENT)
public final class SettingsReviewProbe {
    private static final String OUTPUT=System.getProperty("tacz.settingsReviewQA.output","");
    private static final AtomicInteger SAVED=new AtomicInteger();
    private static int ticks,step,captures,joinedTicks; private static boolean done, connecting;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(OUTPUT.isEmpty()||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(mc.getOverlay()!=null)return;
            if(++ticks<30)return;
            if(step==0 && Boolean.getBoolean("tacz.settingsReviewQA.server") && mc.player==null) {
                if(!connecting && mc.screen instanceof TitleScreen) {
                    connecting=true;
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(),mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25577"),
                        new net.minecraft.client.multiplayer.ServerData("Settings QA","127.0.0.1:25577",false),false);
                }
                return;
            }
            if(step==0 && Boolean.getBoolean("tacz.settingsReviewQA.server") && ++joinedTicks<120)return;
            if(step==0 && (mc.screen instanceof TitleScreen || mc.player!=null)) {
                Files.createDirectories(Path.of(OUTPUT));
                Files.writeString(Path.of(OUTPUT,"observed-config.txt"),"balance="+com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.balancePreset.get()+"\nfragment="+com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.fragmentPreset.get());
                if (Boolean.getBoolean("tacz.settingsReviewQA.server")) {
                    check(com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.balancePreset.get() == com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset.CUSTOM,"server balance setting replaces local default");
                    check(com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.fragmentPreset.get() == com.gamergaming.taczweaponblueprints.progression.BlueprintFragmentPreset.CUSTOM,"server fragment setting replaces local default");
                }
                mc.options.guiScale().set(3);mc.resizeDisplay();
                ConfigApi.INSTANCE.openScreen("taczweaponblueprints.blueprint");step=1;ticks=0;
            } else if(step==7) {mc.setScreen(new UnitScreen());step=8;ticks=0;}
            else if(step==8 && ticks>40) {
                var s=(UnitScreen)mc.screen;s.chanceBox.setValue("37.5");s.discountBox.setValue("12.75");s.secondsBox.setValue("1.25");step=9;ticks=0;
            } else if(step==9 && ticks>30) {
                var s=(UnitScreen)mc.screen;
                check(Math.abs(s.chance.get()-.375)<1e-12,"percent applies as fraction");
                check(s.discount.get()==1275,"percent applies as basis points");
                check(s.seconds.get()==1250,"seconds apply as milliseconds");step=10;ticks=0;
            } else if(step==11 && ticks>30) {
                var s=(UnitScreen)mc.screen;s.chanceBox.setValue("101");step=12;ticks=0;
            } else if(step==12 && ticks>30) {
                var s=(UnitScreen)mc.screen;check(Math.abs(s.chance.get()-.375)<1e-12,"invalid percentage preserves value");
                s.chance.accept(.42);step=13;ticks=0;
            } else if(step==13 && ticks>30) {
                var s=(UnitScreen)mc.screen;check(s.chanceBox.getValue().equals("42"),"external reset refreshes visible units");step=14;ticks=0;
            } else if(step==15 && SAVED.get()==captures) {
                Files.writeString(Path.of(OUTPUT,"complete.json"),"{\"passed\":true,\"inputAssertions\":5,\"captures\":"+captures+"}");done=true;mc.stop();
            }
        }catch(Exception e){fail(e);}
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if(OUTPUT.isEmpty()||done||ticks<30)return;
        var mc=Minecraft.getInstance();
        try {
            if(step>=1&&step<=6 || step==10 || step==14) {
                event.getGuiGraphics().flush();captures++;
                AnalyzerNativeCapture.grab(Path.of(OUTPUT),"settings-"+step+".png",SAVED::incrementAndGet,SettingsReviewProbe::fail);
                if(step<4) mc.screen.mouseScrolled(mc.screen.width/2d,mc.screen.height/2d,-35);
                else if(step==4) {mc.setScreen(new TitleScreen());ConfigApi.INSTANCE.openScreen("taczweaponblueprints.research_tree_client");}
                else if(step==5) mc.screen.mouseScrolled(mc.screen.width/2d,mc.screen.height/2d,-35);
                step++;ticks=0;
            }
        }catch(Exception e){fail(e);}
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void fail(Exception e){try{Files.writeString(Path.of(OUTPUT,"error.txt"),e+"\n"+java.util.Arrays.toString(e.getStackTrace()));}catch(Exception ignored){}done=true;Minecraft.getInstance().stop();}
    private static final class UnitScreen extends Screen {
        final DisplayUnitNumber<Double> chance=DisplayUnitNumber.percent(.2);
        final DisplayUnitNumber<Integer> discount=DisplayUnitNumber.integer(2500,10000,1,()->100);
        final DisplayUnitNumber<Integer> seconds=DisplayUnitNumber.integer(600,3000,100,()->1000);
        EditBox chanceBox,discountBox,secondsBox;
        UnitScreen(){super(Component.literal("Settings input validation"));}
        @Override protected void init(){
            chanceBox=addRenderableWidget((EditBox)chance.widgetEntry(ChoiceValidator.Companion.any()));chanceBox.setPosition(width/2,60);
            discountBox=addRenderableWidget((EditBox)discount.widgetEntry(ChoiceValidator.Companion.any()));discountBox.setPosition(width/2,100);
            secondsBox=addRenderableWidget((EditBox)seconds.widgetEntry(ChoiceValidator.Companion.any()));secondsBox.setPosition(width/2,140);
        }
        @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);super.render(g,x,y,partial);
            g.drawString(font,"Chance (%)",width/2-130,66,0xffffff);g.drawString(font,"Discount (%)",width/2-130,106,0xffffff);g.drawString(font,"Hold (seconds)",width/2-130,146,0xffffff);}
    }
}
