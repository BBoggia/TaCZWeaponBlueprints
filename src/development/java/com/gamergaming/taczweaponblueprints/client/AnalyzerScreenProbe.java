package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.BlueprintWeaponAnalysisService;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.google.gson.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in screen acceptance; never loads an unmarked player world or saves GUI preferences. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,value=Dist.CLIENT)
public final class AnalyzerScreenProbe {
    private static final boolean ENABLED=Boolean.getBoolean("tacz.analyzerScreenQA.enabled");
    private static final Path OUTPUT=Path.of(System.getProperty("tacz.analyzerStationsQA.output","."));
    private static final String WORLD=System.getProperty("tacz.analyzerScreenQA.world","Analyzer A5 QA");
    private static final int CASES=28;
    private static me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode> originalRecovery;
    private static final AtomicInteger SAVED=new AtomicInteger();
    private static boolean opened,finished,dispatched;
    private static volatile BlueprintRecyclerPreview expected;
    private static int step,ticks,total,checks,warmup,oldScale;
    private static final JsonArray evidence=new JsonArray();
    private static final List<ResourceLocation> targets=new ArrayList<>();
    private AnalyzerScreenProbe() { }
    private static void marker(Path path) { check(Files.isRegularFile(path.resolve(".analyzer-screen-copy")),"marked disposable world"); }
    private static void check(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); checks++; }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (!ENABLED || finished) return;
        var mc=Minecraft.getInstance();
        try {
            if (!opened && event.getScreen() instanceof TitleScreen && mc.getOverlay()==null) {
                marker(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD));
                Files.createDirectories(OUTPUT); oldScale=mc.options.guiScale().get(); opened=true;
                mc.createWorldOpenFlows().loadLevel(event.getScreen(),WORLD); return;
            }
            if (expected==null || ticks<30 || !(event.getScreen() instanceof BlueprintRecyclerScreen screen) || mc.getOverlay()!=null) return;
            if (step<15 || step>18 || ticks<=30) check(screen.getMenu().preview().withStateToken(0).equals(expected.withStateToken(0)),
                    "client preview agrees with server, case "+step+" expected="+expected+" actual="+screen.getMenu().preview());
            check(screen.width>=BlueprintRecyclerMenu.Layout.PANEL_WIDTH && screen.height>=BlueprintRecyclerMenu.Layout.PANEL_HEIGHT,"layout fits native GUI");
            if (step<9) {
                var w=expected.presentation().weapon().orElseThrow();
                check(w.tier()==step%3+1 && expected.presentation().stationTier()==step/3+1,"nine tier pair presentation");
            }
            if (step==13) check(screen.getTitle().getString().length()>100,"long title fixture");
            if (step==21 || step==22) {
                var model=BlueprintRecyclerScreenModel.from(screen.getMenu().preview(),false);
                check(model.statusKey().endsWith("reverse.status.ingredients_required"),"direct RP blockage explains missing materials");
                var lines=AnalyzerWorkbenchProbeSupport.facts(screen);
                check(lines.stream().anyMatch(c->c.getString().contains("Paper") && c.getString().contains("0/4")),"blocked recovery lists unpaid materials even with zero computed award");
                check(lines.stream().noneMatch(c->c.getString().contains("Iron Ingot") || c.getString().contains("Blueprint:")
                        || c.getString().contains("fragments:")),"direct-only screen omits inactive analysis materials and odds");
            }
            if (step==24) {
                check(screen.getMenu().preview().actionable(),"recycle-only blueprint is still a valid configured action");
                var lines=AnalyzerWorkbenchProbeSupport.facts(screen);
                check(lines.stream().anyMatch(c->c.getString().equals("Blueprint learning disabled.")),"screen explains disabled blueprint learning");
                check(lines.stream().noneMatch(c->c.getString().contains("Archive total:") || c.getString().contains("Saved:")),"no matching fragment progress without a learning reward class");
            }
            if (step>=25) {
                var model=BlueprintRecyclerScreenModel.from(screen.getMenu().preview(),false);
                check(model.statusKey().endsWith("reverse.status.direct_ready"),"available recovery owns status in choice mode");
                check(BlueprintRecyclerScreenModel.focusesRecovery(screen.getMenu().preview()),"summary follows the only ready action");
                check(AnalyzerWorkbenchContent.cost(screen.getMenu().preview(),model.primaryAction()).currency()==7,"overview follows recovery cost");
                var lines=AnalyzerWorkbenchProbeSupport.facts(screen);
                check(lines.stream().anyMatch(c->c.getString().contains(step==25 ? "already learned" : step==27 ? "higher-tier Blueprint Analyzer" : "missing required materials")),"blocked analysis reason remains visible");
                check(lines.stream().anyMatch(c->c.getString().contains("Paper") && c.getString().contains("4/4")),"active recovery materials remain visible");
                check(lines.stream().noneMatch(c->c.getString().startsWith("Blueprint:") || c.getString().startsWith("Fragments (")),"inactive odds do not describe the recovery action");
            }
            if (step>=15 && step<=18 && ticks==30) {
                // Exercise the actual button request, codec, server action, response and feedback.
                var actionButton=AnalyzerWorkbenchProbeSupport.button(screen,"primaryButton");
                var x=actionButton.getX()+2;
                var y=actionButton.getY()+2;
                check(screen.mouseClicked(x,y,0),"click Analyzer action"); ticks++; return;
            }
            if (step>=15 && step<=18 && ticks<45) return;
            if (step>=15 && step<=18) {
                var field=BlueprintRecyclerScreen.class.getDeclaredField("feedback");field.setAccessible(true);
                var feedback=(BlueprintRecyclerFeedbackState)field.get(screen);
                check(feedback.visibleCode().filter(c->c==BlueprintRecyclerActionContract.ResultCode.SUCCESS).isPresent(),"committed response displayed");
                if(step<18) check(feedback.visibleOutcome().orElseThrow().kind()==(step==15?AnalyzerOutcomeTable.Kind.FRAGMENTS:step==16?AnalyzerOutcomeTable.Kind.BLUEPRINT:AnalyzerOutcomeTable.Kind.NO_RESULT),"exact committed outcome reaches screen");
                else check(feedback.visibleFragments().orElseThrow().blueprintCreated(),"reconstruction result reaches screen");
            }
            event.getGuiGraphics().flush();
            String name=String.format(java.util.Locale.ROOT,"%02d-%s.png",step,caseName());
            AnalyzerNativeCapture.grab(OUTPUT,name,()->SAVED.incrementAndGet(),AnalyzerScreenProbe::fail);
            var row=new JsonObject(); row.addProperty("case",caseName());row.addProperty("width",screen.width);row.addProperty("height",screen.height);
            row.addProperty("input",expected.inputKind().name());row.addProperty("station",expected.presentation().stationTier());
            evidence.add(row); expected=null;dispatched=false;ticks=0;step++;
        } catch(Exception error) { fail(error); }
    }
    private static String caseName() {
        if (step<9) return "analyzer-"+(step/3+1)+"-weapon-"+(step%3+1);
        return switch(step) { case 9->"reconstruct";case 10->"retained-input";case 11->"occupied-output";case 12->"missing-resources";
            case 13->"small-long-title";case 14->"small-scrolled-details";case 15->"fragments-result";case 16->"blueprint-result";
            case 17->"no-result";case 18->"reconstructed-result";case 19->"research-data";case 20->"ordinary-fragments";
            case 21->"direct-rp-missing-materials";case 22->"direct-rp-scrolled-materials";case 23->"direct-rp-ready";case 24->"blueprint-learning-disabled";case 25->"choice-known-recovery";case 26->"choice-missing-analysis-material";default->"choice-analysis-tier-blocked"; };
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || !opened || event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        try {
            if (++total>3000) throw new IllegalStateException("Analyzer screen QA timed out");
            var server=mc.getSingleplayerServer();
            if (mc.player==null || server==null) return;
            if (warmup++<60) return;
            if (step==CASES) {
                if (SAVED.get()!=CASES) return;
                var result=new JsonObject();result.addProperty("passed",true);result.addProperty("assertions",checks);result.addProperty("screenshots",SAVED.get());result.add("cases",evidence);
                Files.writeString(OUTPUT.resolve("complete.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                restore();finished=true;mc.stop();return;
            }
            if (expected!=null) { ticks++;return; }
            if (dispatched) return;
            dispatched=true;
            int scenario=step;var uuid=mc.player.getUUID();
            server.execute(()-> {
                try {
                    marker(server.getWorldPath(LevelResource.ROOT));
                    var player=server.getPlayerList().getPlayer(uuid);
                    var preview=setup(player,scenario);
                    mc.execute(()-> {
                        try {
                            mc.options.guiScale().set(scenario==13 || scenario==14 ? 0 : 3);mc.resizeDisplay();
                            expected=preview;
                            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),2,2);
                        } catch(Exception error) {fail(error);}
                    });
                } catch(Exception error) {mc.execute(()->fail(error));}
            });
        } catch(Exception error) {fail(error);}
    }
    @SubscribeEvent public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!ENABLED || finished || expected==null || !(event.getScreen() instanceof BlueprintRecyclerScreen screen)) return;
        if (ticks==10 && step==13) {
            Minecraft.getInstance().setScreen(new BlueprintRecyclerScreen(screen.getMenu(),Minecraft.getInstance().player.getInventory(),
                    Component.literal("Experimental Blueprint Analyzer — A deliberately very long translated workstation title with additional words for narrow display testing")));
        }
        if (ticks==10 && (step==14 || step==22 || step==24)) {
            try { AnalyzerWorkbenchProbeSupport.openDetails(screen); screen.keyPressed(267,0,0); }
            catch (ReflectiveOperationException error) { fail(error); }
        }
    }
    private static BlueprintRecyclerPreview setup(ServerPlayer player,int scenario) {
        if (scenario==21 || scenario==24 || scenario==25) {
            // Replace only this process's field: the registered persisted config object stays untouched.
            if(originalRecovery==null)originalRecovery=ModConfigs.BLUEPRINT.foundWeaponRecoveryMode;
            var mode=scenario==21 ? com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode.DIRECT_RP_ONLY
                    : scenario==25 ? com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode.PLAYER_CHOICE
                    : com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode.RECYCLABLE_BLUEPRINT;
            ModConfigs.BLUEPRINT.foundWeaponRecoveryMode=new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition<>(
                    new me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum<>(mode),()->mode);
            republishConfig();
        }
        var context=BlueprintWeaponAnalysisService.context();var config=ModConfigs.BLUEPRINT.progressionSnapshot();
        if (targets.isEmpty()) for(int tier=1;tier<=3;tier++) {
            final int wanted=tier;
            targets.add(context.catalog().keySet().stream().sorted().filter(id-> {
                var q=BlueprintAnalyzerPolicyResolver.resolve(context,config.activeProfileId(),id,AnalyzerTier.TIER_1,BlueprintAnalyzerPolicy.Origin.FOUND,config.foundWeaponRecoveryMode());
                return q.status()==AnalyzerPolicyQuote.Status.CONFIGURED && q.weaponTier().orElseThrow().level()==wanted;
            }).findFirst().orElseThrow());
        }
        player.closeContainer();player.getInventory().clearContent();
        var data=player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
        var target=scenario==27?new ResourceLocation("tacz:cz75"):targets.get(scenario<9?scenario%3:scenario==24?2:1);
        data.clearRecipes();
        data.replaceProgression(Set.of(),Set.of(target.toString()),20);data.clearArchivedBlueprintFragments();data.clearArchivedAnalysisFragments();
        player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,8));
        int tier=scenario<9?scenario/3+1:scenario==16?2:scenario==17 || scenario==24?3:1;
        var block=switch(tier) {case 1->(BlueprintRecyclerBlock)ModBlocks.BLUEPRINT_RECYCLER.get();case 2->ModBlocks.ADVANCED_BLUEPRINT_ANALYZER.get();default->ModBlocks.EXPERIMENTAL_BLUEPRINT_ANALYZER.get();};
        var pos=player.blockPosition().offset(2,0,0);player.serverLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        player.serverLevel().removeBlock(pos, false);
        if (!com.gamergaming.taczweaponblueprints.block.BlueprintRecyclerBlock.placeCompleteStructure(
                player.serverLevel(), pos, block.defaultBlockState())) throw new IllegalStateException("Analyzer fixture needs two blocks");
        block.use(player.serverLevel().getBlockState(pos),player.serverLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
        var menu=(BlueprintRecyclerMenu)player.containerMenu;
        var item=ForgeRegistries.ITEMS.getValues().stream().filter(i->i instanceof IGun).findFirst().orElseThrow();
        var input=new ItemStack(item);((IGun)item).setGunId(input,target);PhysicalWeaponProvenance.stampLootGenerated(input,new ResourceLocation("minecraft:chests/simple_dungeon"));
        if (scenario==9 || scenario==10 || scenario==18) {
            input=BlueprintFragmentItem.createAnalysis(target,AnalysisRewardClass.PROTECTED_PREREQUISITES);input.setCount(3);
            data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,target.toString(),AnalysisFragmentCounts.EMPTY,new AnalysisFragmentCounts(scenario==10?10:2,0,0,0)));
        }
        if(scenario==11)menu.getSlot(1).set(new ItemStack(Items.STONE));
        if(scenario==12){data.setResearchPoints(0);player.getInventory().clearContent();}
        if(scenario==19)input=new ItemStack(ModItems.RESEARCH_NOTE.get(),3);
        if(scenario==20){input=new ItemStack(ModItems.BLUEPRINT_FRAGMENT.get());input.getOrCreateTag().putString("BlueprintTarget",target.toString());input.setCount(3);}
        if(scenario==23 || scenario>=25)player.getInventory().setItem(1,new ItemStack(Items.PAPER,4));
        if(scenario==25)data.addBlueprint(target.toString());
        if(scenario==26)player.getInventory().setItem(0,ItemStack.EMPTY);
        menu.getSlot(0).set(input);menu.broadcastChanges();menu.refreshAuthoritativePreview(player);
        var preview=menu.preview();
        if(scenario<9){check(preview.presentation().weapon().orElseThrow().outcomes().isPresent(),"server supplies odds");check(preview.pointCost()==3,"effective analysis cost");}
        if(scenario==10){check(preview.fragmentEvaluation().orElseThrow().accepted()==0,"retained input preview");check(preview.fragmentEvaluation().orElseThrow().createsBlueprint(),"saved set reconstructs");}
        if(scenario==21 || scenario==22){check(preview.recoveryPointValue()==0,"blocked direct RP award not yet computed");check(preview.presentation().recoveryCost().orElseThrow().points()==7,"direct costs independent of tiered attempt");}
        if(scenario==23)check(preview.recoveryStatus().orElseThrow()==com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryService.Status.READY,"direct RP becomes ready with its own materials");
        return preview;
    }
    private static void republishConfig(){
        try{var method=ModConfigs.BLUEPRINT.getClass().getDeclaredMethod("publishProgressionSnapshot");method.setAccessible(true);method.invoke(ModConfigs.BLUEPRINT);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Unable to republish temporary QA config",error);}
    }
    private static void restore(){if(originalRecovery!=null){ModConfigs.BLUEPRINT.foundWeaponRecoveryMode=originalRecovery;originalRecovery=null;republishConfig();}var mc=Minecraft.getInstance();mc.options.guiScale().set(oldScale);mc.resizeDisplay();}
    private static void fail(Exception error){
        try{Files.createDirectories(OUTPUT);Files.writeString(OUTPUT.resolve("error.txt"),error.toString()+"\n"+Arrays.toString(error.getStackTrace()));}catch(Exception ignored){}
        restore();finished=true;Minecraft.getInstance().stop();
    }
}
