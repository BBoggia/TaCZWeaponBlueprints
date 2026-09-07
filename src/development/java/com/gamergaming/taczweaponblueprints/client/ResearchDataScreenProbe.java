package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.google.gson.*;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs after the marked currency world's transaction assertions, never on an ordinary world. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,value=Dist.CLIENT)
public final class ResearchDataScreenProbe {
    private static boolean active,dispatch,done,clicked,captured;
    private static int step,ticks,total,oldScale,oldWidth,oldHeight,checks;
    private static Path output; private static BlueprintRecyclerPreview expected;
    private static BlueprintResearchSnapshot original;
    private static ValidatedEnum<ResearchCurrencyPolicy.Kind> oldKind;
    private static ValidatedEnum<ResearchDataPolicy.Behavior> oldMode;
    private static final AtomicInteger saved=new AtomicInteger();
    private static final JsonArray evidence=new JsonArray();
    static void start(Path root) {
        output=root;var mc=Minecraft.getInstance();oldScale=mc.options.guiScale().get();int[] logicalWidth=new int[1],logicalHeight=new int[1];
        org.lwjgl.glfw.GLFW.glfwGetWindowSize(mc.getWindow().getWindow(),logicalWidth,logicalHeight);oldWidth=logicalWidth[0];oldHeight=logicalHeight[0];
        original=BlueprintResearchDataManager.INSTANCE.snapshot();oldKind=ModConfigs.BLUEPRINT.researchCurrencyKind;oldMode=ModConfigs.BLUEPRINT.researchDataBehavior;
        active=true;
    }
    private static boolean analysis(){return (step/2)%2==0;}
    private static boolean physical(){return step>=4;}
    private static String name(){return (physical()?"scrap":"banked")+"-"+(analysis()?"analysis":"legacy")+(step%2==1?"-narrow":"-normal");}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(!active||done||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(++total>1800)throw new IllegalStateException("Data screen QA timed out");
            if(step==8){if(saved.get()==16)finish(null);return;}
            if(expected!=null){ticks++;return;}
            if(dispatch)return;dispatch=true;
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            server.execute(()->{
                try {
                    var p=server.getPlayerList().getPlayer(uuid);var preview=setup(p);
                    mc.execute(()->{
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,980);
                        int desiredWidth=step%2==1?320:426;
                        mc.options.guiScale().set(Math.max(1,mc.getWindow().getWidth()/desiredWidth));mc.resizeDisplay();
                        expected=preview;ticks=0;clicked=false;captured=false;
                        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),2,2);
                    });
                }catch(Exception error){mc.execute(()->finish(error));}
            });
        }catch(Exception error){finish(error);}
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post e) {
        if(!active||done||expected==null||ticks<20||!(e.getScreen() instanceof BlueprintRecyclerScreen screen))return;
        try {
            if(!captured){
                check(screen.getMenu().preview().equals(expected),"authoritative preview arrived");
                check(screen.width>=BlueprintRecyclerMenu.Layout.PANEL_WIDTH&&screen.height>=BlueprintRecyclerMenu.Layout.PANEL_HEIGHT,"panel fits viewport");
                var model=BlueprintRecyclerScreenModel.from(expected,false);check(model.controlsEnabled(),"action enabled");
                if(analysis()) {
                    var lines=AnalyzerWorkbenchProbeSupport.facts(screen);
                    check(lines.stream().noneMatch(c->c.getString().contains("tacz:ak47")||c.getString().contains("qa:data")||c.getString().startsWith("gui.")),"aggregate details contain no hidden IDs or missing keys");
                    check(lines.stream().anyMatch(c->c.getString().contains("2%")),"aggregate blueprint odds displayed");
                    check(lines.stream().anyMatch(c->c.getString().contains("Already learned targets can drop")),"duplicates explained");
                    check(lines.stream().anyMatch(c->c.getString().contains("T2:")),"other stations compared");
                }
                check(ClientResearchCurrency.policy().kind()==(physical()?ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM:ResearchCurrencyPolicy.Kind.BANKED_RP),"server denomination displayed");
                e.getGuiGraphics().flush();AnalyzerNativeCapture.grab(output,name()+".png",saved::incrementAndGet,ResearchDataScreenProbe::finish);
                var row=new JsonObject();row.addProperty("case",name());row.addProperty("width",screen.width);row.addProperty("height",screen.height);evidence.add(row);
                captured=true;return;
            }
            if(!clicked){
                var actionButton=AnalyzerWorkbenchProbeSupport.button(screen,"primaryButton");
                check(screen.mouseClicked(actionButton.getX()+2,actionButton.getY()+2,0),"native action clicked");clicked=true;ticks=0;return;
            }
            if(ticks<20)return;
            var field=BlueprintRecyclerScreen.class.getDeclaredField("feedback");field.setAccessible(true);var feedback=(BlueprintRecyclerFeedbackState)field.get(screen);
            check(feedback.visibleCode().filter(c->c==BlueprintRecyclerActionContract.ResultCode.SUCCESS).isPresent(),"native result received");
            check(screen.getMenu().inputStack().getCount()==2,"one item processed");
            e.getGuiGraphics().flush();AnalyzerNativeCapture.grab(output,name()+"-result.png",saved::incrementAndGet,ResearchDataScreenProbe::finish);
            step++;expected=null;dispatch=false;
        }catch(Exception error){finish(error);}
    }
    private static BlueprintRecyclerPreview setup(ServerPlayer player)throws Exception {
        ModConfigs.BLUEPRINT.researchCurrencyKind=new ValidatedEnum<>(physical()?ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM:ResearchCurrencyPolicy.Kind.BANKED_RP);
        ModConfigs.BLUEPRINT.researchDataBehavior=new ValidatedEnum<>(analysis()?ResearchDataPolicy.Behavior.ANALYSIS:ResearchDataPolicy.Behavior.LEGACY_CURRENCY);
        ResearchDataTransactionProbe.publishConfig();
        if(analysis()){
            ResearchDataTransactionProbe.install(original,new net.minecraft.resources.ResourceLocation("tacz:ak47"),true);
            var snapshot=BlueprintResearchDataManager.INSTANCE.snapshot();var old=snapshot.researchData().policies().values().iterator().next();
            var rows=new EnumMap<AnalyzerTier,ResearchDataPolicy.StationRow>(AnalyzerTier.class);
            for(var tier:AnalyzerTier.values()) rows.put(tier,new ResearchDataPolicy.StationRow(new AnalyzerOutcomeTable(tier.level()*2,100-tier.level()*2,0,tier.level(),tier.level()+2),Map.of()));
            var policy=new ResearchDataPolicy(old.format(),old.enabled(),old.priority(),old.input(),old.profile(),old.behavior(),old.minimumAnalyzerTier(),
                    new BlueprintResearchCost(3,List.of()),old.pool(),old.allowLearned(),old.allowUndiscovered(),old.progressBonus(),old.rewardClass(),old.reconstruction(),rows);
            ResearchDataTransactionProbe.apply(snapshot.withResearchData(new ResearchDataSnapshot(Map.of(new net.minecraft.resources.ResourceLocation("qa:data"),policy),snapshot.researchData().reconstruction())));
        }else ResearchDataTransactionProbe.apply(original);
        var menu=ResearchCurrencyProbe.open(player,player.blockPosition().offset(3,0,0));
        player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(ModItems.SCRAP.get(),16));
        var data=player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();data.replaceProgression(Set.of(),Set.of("tacz:ak47"),137);data.addBlueprint("tacz:ak47");data.clearArchivedAnalysisFragments();data.getResearchPointAwardLedger().clear();
        menu.getSlot(0).set(new ItemStack(ModItems.RESEARCH_REPORT.get(),3));menu.refreshAuthoritativePreview(player);menu.broadcastChanges();
        if(physical()&&analysis()) {
            player.getInventory().setItem(0,new ItemStack(ModItems.SCRAP.get(),1));menu.broadcastChanges();
            check(menu.preview().pointBalance()==1&&!menu.preview().actionable(),"carried Scrap shortage refreshes readiness");
            player.getInventory().setItem(0,new ItemStack(ModItems.SCRAP.get(),16));menu.broadcastChanges();
            check(menu.preview().pointBalance()==16&&menu.preview().actionable(),"carried Scrap restoration refreshes readiness");
        }
        return menu.preview();
    }
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks++;}
    private static void finish(Exception error){
        if(done)return;done=true;var mc=Minecraft.getInstance();
        mc.getSingleplayerServer().execute(()->{
            Exception failure=error;
            try{ModConfigs.BLUEPRINT.researchCurrencyKind=oldKind;ModConfigs.BLUEPRINT.researchDataBehavior=oldMode;ResearchDataTransactionProbe.publishConfig();ResearchDataTransactionProbe.apply(original);}catch(Exception e){if(failure==null)failure=e;else failure.addSuppressed(e);}
            final Exception result=failure;
            mc.execute(()->{
                try{
                    mc.options.guiScale().set(oldScale);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),oldWidth,oldHeight);mc.resizeDisplay();
                    if(result!=null){Files.writeString(output.resolve("data-screen-error.txt"),result.toString());result.printStackTrace();}
                    else {var summary=new JsonObject();summary.addProperty("passed",true);summary.addProperty("assertions",checks);summary.addProperty("captures",saved.get());summary.add("cases",evidence);Files.writeString(output.resolve("data-screen-complete.json"),new GsonBuilder().setPrettyPrinting().create().toJson(summary));}
                }catch(Exception e){e.printStackTrace();}finally{mc.stop();}
            });
        });
    }
}
