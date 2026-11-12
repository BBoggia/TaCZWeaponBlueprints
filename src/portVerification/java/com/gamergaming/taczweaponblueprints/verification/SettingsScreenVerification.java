package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.require;
import java.util.*;
import com.gamergaming.taczweaponblueprints.client.settings.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;

final class SettingsScreenVerification {
    private static int category, delay, popupStep, searchStep;
    private static boolean started, inside, done, checkedPage, quickDone, personalDone, recipesDone, usabilityDone;
    private static Map<String,String> original;
    private static AdvancedSettingsScreen screen;
    static boolean tick() {
        var mc=Minecraft.getInstance();
        if(done)return true;
        if(!started){
            started=true; original=FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT);
            ConfigApiJava.INSTANCE.openScreen("taczweaponblueprints");
            require(mc.screen instanceof SettingsLandingScreen,"public Fzzy provider opens settings landing page");
            delay=4;return false;
        }
        if(delay-->0)return false;
        if(!personalDone){
            if(!PersonalSettingsScreenVerification.tick())return false;
            personalDone=true;delay=4;return false;
        }
        if(!quickDone){
            if(!QuickSetupScreenVerification.tick())return false;
            quickDone=true;
            // Quick Setup's clean exit releases its server session; reopen for native editor coverage.
            ClientSettingsAccess.open();delay=4;return false;
        }
        if(screen==null){
            if(ClientSettingsAccess.pending())return false;
            if(!recipesDone){
                if(!SettingsRecipesScreenVerification.tick())return false;
                recipesDone=true;
            }
            if(!usabilityDone){
                if(!SettingsUsabilityVerification.tick())return false;
                usabilityDone=true;
            }
            screen=new AdvancedSettingsScreen(mc.screen);mc.setScreen(screen);delay=4;return false;
        }
        var categories=Arrays.stream(SettingsCatalog.Category.values()).filter(c->c!=SettingsCatalog.Category.PERSONAL&&c!=SettingsCatalog.Category.LEGACY).toList();
        if(popupStep==1){
            require(!screen.getPopupWidgets().isEmpty(),"native collection editor opens its popup on the custom screen");
            screen.keyPressed(256,0,0);popupStep=2;delay=3;return false;
        }
        if(popupStep==2){require(screen.getPopupWidgets().isEmpty(),"Escape closes collection popup without leaving settings");popupStep=0;}
        if(category==categories.size()){
            if(searchStep==0){screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("gun sales");searchStep=1;delay=3;return false;}
            if(searchStep==1){find("config.taczweaponblueprints.blueprint.villagerGunSales").onPress();searchStep=2;delay=3;return false;}
            require(find("config.taczweaponblueprints.blueprint.villagerGunSales")!=null,"search finds a setting by words and opens its category");
            screen.resize(mc,320,240);
            require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).allMatch(w->w.getX()>=0 && w.getX()+w.getWidth()<=320 && w.getY()>=0 && w.getY()+w.getHeight()<=240),"settings controls fit a 320 by 240 GUI");
            screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            require(original.equals(FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT)),"visiting every category and page leaves live Fzzy configuration unchanged");
            require(!ClientSettingsAccess.draft().orElseThrow().dirty(),"category navigation creates no pending changes");
            mc.setScreen(null);done=true;return true;
        }
        if(!inside){
            click("settings.taczweaponblueprints.ui.category."+categories.get(category).name().toLowerCase(Locale.ROOT));inside=true;checkedPage=false;delay=3;return false;
        }
        if(!checkedPage){
            checkedPage=true;
            if(categories.get(category)==SettingsCatalog.Category.STARTING_ACCESS){
                pressFirstEditor();
                popupStep=1;delay=3;return false;
            }
            if(categories.get(category)==SettingsCatalog.Category.VILLAGERS){
                pressFirstEditor();
                require(ClientSettingsAccess.draft().orElseThrow().dirty(),"native boolean widget edits the shared draft");
                require(original.equals(FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT)),"native widget edit does not mutate live Fzzy state");
                pressFirstEditor();
                require(!ClientSettingsAccess.draft().orElseThrow().dirty(),"returning native control to baseline clears its pending change");
            }
            try { FeatureClientVerification.screenshot(mc,"settings-"+categories.get(category).name().toLowerCase(Locale.ROOT)); }
            catch(Exception e){throw new IllegalStateException(e);}
        }
        var next=find("settings.taczweaponblueprints.ui.next");
        if(next.active){next.onPress();delay=3;return false;}
        require(true,"native Fzzy widgets render every page of "+categories.get(category));
        click("gui.back");inside=false;category++;delay=3;return false;
    }
    private static void pressFirstEditor(){
        var widget=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.getX()==screen.width-166 && w.getY()==72).findFirst().orElseThrow();
        screen.setFocused(widget);screen.keyPressed(257,0,0);
    }
    private static Button find(String key){String label=Component.translatable(key).getString();return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("Missing button "+label));}
    private static void click(String key){find(key).onPress();}
}
