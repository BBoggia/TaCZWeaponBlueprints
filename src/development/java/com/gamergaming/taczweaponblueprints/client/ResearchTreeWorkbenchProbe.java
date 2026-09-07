package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in native integration probe, restricted to explicitly allowed disposable QA world copies. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT)
public final class ResearchTreeWorkbenchProbe {
    private static final String OUTPUT = System.getProperty("tacz.treeWorkbenchQA.output");
    private static boolean opened;
    private static final String WORLD = System.getProperty("tacz.treeWorkbenchQA.world", "M5 UI QA");
    private static Long originalDayTime;
    private static String snapshot;
    private static Measurement measurement;
    private static long openRequested;
    private static boolean firstMeasured;
    private static final class Measurement {
        final String name;
        String motion = "none";
        ResearchTreeViewport.Snapshot camera;
        final List<Long> screen = new ArrayList<>(), canvas = new ArrayList<>(), interval = new ArrayList<>(), allocated = new ArrayList<>();
        int frames; long start, canvasNanos, previous, allocationStart, firstScreen, firstCanvas, firstUseful;
        Measurement(String name) { this.name=name; }
    }
    @SubscribeEvent public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (OUTPUT == null || !(event.getScreen() instanceof ResearchBenchScreen screen)) return;
        if (!firstMeasured) { firstMeasured=true;measurement=new Measurement("opening"); }
        if (measurement == null) return;
        try {
            var canvas = (ResearchTreeCanvas)field(screen,"treeCanvas");
            if(measurement.camera==null)measurement.camera=canvas.viewport().snapshot();
            if(measurement.motion.equals("pan"))canvas.viewport().panByScreenDelta(Math.sin(measurement.frames*.08)*5,Math.cos(measurement.frames*.08)*4);
            if(measurement.motion.equals("zoom"))canvas.viewport().zoomProportional(Math.sin(measurement.frames*.08)*.15,canvas.bounds().width()/2.0,canvas.bounds().height()/2.0);
            var renderer = (ResearchTreeSceneRenderer)field(canvas,"sceneRenderer");
            if (renderer != null) renderer.timingObserver = nanos -> { if(measurement!=null)measurement.canvasNanos=nanos; };
            var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
            measurement.allocationStart=bean.getThreadAllocatedBytes(Thread.currentThread().getId());
            measurement.start=System.nanoTime();
        } catch(Exception error) { measurement=null;System.err.println("[Workbench QA] measurement stopped: "+error); }
    }
    private static void measured(ResearchBenchScreen screen) throws Exception {
        var m=measurement; if(m==null)return;
        long elapsed=System.nanoTime()-m.start;
        if(m.frames==0){m.firstScreen=elapsed;m.firstCanvas=m.canvasNanos;m.firstUseful=openRequested==0||!m.name.equals("opening")?0:System.nanoTime()-openRequested;}
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        if(m.frames++>=30) {
            m.screen.add(elapsed);m.canvas.add(m.canvasNanos);m.interval.add(m.start-m.previous);
            m.allocated.add(bean.getThreadAllocatedBytes(Thread.currentThread().getId())-m.allocationStart);
        }
        m.previous=m.start;
        if(m.frames<150)return;
        var result=evidence(screen);
        result.addProperty("first_screen_cpu_ns",m.firstScreen);result.addProperty("first_canvas_cpu_ns",m.firstCanvas);
        result.addProperty("first_useful_including_open_request_ns",m.firstUseful);
        result.add("screen_cpu",ResearchTreeBaselineTool.statistics(m.screen));
        result.add("canvas_cpu",ResearchTreeBaselineTool.statistics(m.canvas));
        result.add("frame_interval_including_pacing",ResearchTreeBaselineTool.statistics(m.interval));
        m.allocated.sort(Long::compare);result.addProperty("median_screen_allocated_bytes",m.allocated.get(m.allocated.size()/2));
        Files.writeString(Path.of(OUTPUT).resolve(m.name+"-timing.json"),ResearchTreeBaselineInput.JSON.toJson(result)+"\n");
        snapshot=m.name;measurement=null;
        ((ResearchTreeCanvas)field(screen,"treeCanvas")).viewport().restore(m.camera);
        var renderer=(ResearchTreeSceneRenderer)field(field(screen,"treeCanvas"),"sceneRenderer");
        if(renderer!=null)renderer.timingObserver=null;
    }
    private static int[] originalWindow;
    private static int originalGuiScale;
    private static ClientResearchState.Publication savedPublication;
    private static ResearchTreeDisplayPolicy originalDisplay;
    private static ResearchTreeMinimapMode originalMinimap;
    private ResearchTreeWorkbenchProbe() { }
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (OUTPUT == null) return;
        var mc = Minecraft.getInstance();
        if (!opened && event.getScreen() instanceof TitleScreen && mc.getOverlay() == null) {
            opened = true; mc.createWorldOpenFlows().loadLevel(event.getScreen(), WORLD); return;
        }
        if (event.getScreen() instanceof ResearchBenchScreen active) {
            try { measured(active); } catch(Exception error) { measurement=null;System.err.println("[Workbench QA] measure failed: "+error); }
        }
        if (snapshot == null || !(event.getScreen() instanceof ResearchBenchScreen screen)) return;
        try {
            requireCopy(); Path root = Path.of(OUTPUT); Files.createDirectories(root);
            var data = evidence(screen);
            Files.writeString(root.resolve(snapshot + ".json"), ResearchTreeBaselineInput.JSON.toJson(data) + "\n");
            event.getGuiGraphics().flush();
            Screenshot.grab(root.toFile(), snapshot + ".png", mc.getMainRenderTarget(), message -> System.out.println("[Workbench QA] " + message.getString()));
        } catch (Exception e) { System.err.println("[Workbench QA] " + e); }
        snapshot = null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || event.phase != TickEvent.Phase.END) return;
        Path root = Path.of(OUTPUT), command = root.resolve("control.json");
        if (!Files.isRegularFile(command)) return;
        try {
            if (Files.size(command) > 4096) throw new IllegalArgumentException("oversized QA command");
            var data = JsonParser.parseString(Files.readString(command)).getAsJsonObject(); Files.delete(command);
            var mc = Minecraft.getInstance(); String action = data.get("action").getAsString(); requireCopy();
            if (action.equals("close")) {
                restoreWindow();
                if(originalDayTime!=null)mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(originalDayTime));
                mc.stop(); return;
            }
            if (action.equals("open-bench")) {
                openRequested=System.nanoTime();
                var hit = mc.player.pick(5, 0, false);
                if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)) throw new IllegalStateException("look at the Research Bench");
                mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, block);
            } else {
                if (!(mc.screen instanceof ResearchBenchScreen screen)) throw new IllegalStateException("Research Bench must be open");
                var canvas = (ResearchTreeCanvas) field(screen, "treeCanvas");
                switch (action) {
                    case "world-time" -> {
                        var server=mc.getSingleplayerServer();
                        if(originalDayTime==null)originalDayTime=server.overworld().getDayTime();
                        long value=data.get("value").getAsLong();
                        if(value<0 || value>24000)throw new IllegalArgumentException("invalid time");
                        server.execute(() -> server.overworld().setDayTime(value));
                    }
                    case "measure" -> {
                        String name=data.get("value").getAsString();
                        if(!name.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("invalid measure name");
                        measurement=new Measurement(name);
                        if(data.has("motion"))measurement.motion=data.get("motion").getAsString();
                    }
                    case "replay" -> {
                        if(savedPublication==null)savedPublication=ClientResearchState.publication();
                        var replay=ResearchTreeBaselineInput.read(Path.of(data.get("value").getAsString()));
                        if(replay.projections().size()!=1)throw new IllegalArgumentException("QA replay requires one domain");
                        ClientResearchState.publishTreeOnly(ResearchTreePerformanceTool.publication(replay.projections().get(0)));
                    }
                    case "stress" -> {
                        if(savedPublication==null)savedPublication=ClientResearchState.publication();
                        var replay=switch(data.get("value").getAsString()) {
                            case "nodes" -> ResearchTreeBaselineFixtures.maximumNodes().input();
                            case "edges" -> ResearchTreeBaselineFixtures.maximumEdges().input();
                            default -> throw new IllegalArgumentException("unknown bounded stress fixture");
                        };
                        ClientResearchState.publishTreeOnly(ResearchTreePerformanceTool.publication(replay.projections().get(0)));
                    }
                    case "resize" -> {
                        int w=data.get("width").getAsInt(), h=data.get("height").getAsInt(), scale=data.get("guiScale").getAsInt();
                        if (w<640 || w>2000 || h<360 || h>1200 || scale<0 || scale>6) throw new IllegalArgumentException("resize outside QA limits");
                        if (originalWindow == null) {
                            int[] width=new int[1], height=new int[1]; org.lwjgl.glfw.GLFW.glfwGetWindowSize(mc.getWindow().getWindow(),width,height);
                            originalWindow=new int[]{width[0],height[0]}; originalGuiScale=mc.options.guiScale().get();
                        }
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),w,h); mc.options.guiScale().set(scale); mc.resizeDisplay();
                    }
                    case "restore" -> restoreWindow();
                    case "settings" -> {
                        var config=com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT;
                        if(originalDisplay==null){originalDisplay=config.displayPolicy();originalMinimap=config.minimapMode();}
                        config.reduceMotion.validateAndSet(data.get("reduceMotion").getAsBoolean());
                        config.showBackgroundGrid.validateAndSet(data.get("grid").getAsBoolean());
                        config.minimap.validateAndSet(ResearchTreeMinimapMode.valueOf(data.get("minimap").getAsString()));config.onUpdateClient();
                    }
                    case "search" -> {
                        invoke(screen,"openFullscreenSearch",boolean.class,true);
                        ((net.minecraft.client.gui.components.EditBox)field(screen,"searchBox")).setValue(data.get("value").getAsString());
                    }
                    case "next" -> invoke(screen,"focusRecommendedBlueprint",null,null);
                    case "click-zoom" -> {
                        int direction=data.get("value").getAsInt();
                        if(direction!=1 && direction!=-1)throw new IllegalArgumentException("zoom direction must be -1 or 1");
                        var button=(net.minecraft.client.gui.components.Button)field(screen,direction<0?"zoomOutButton":"zoomInButton");
                        if(!button.visible || !button.active)throw new IllegalStateException("zoom control is disabled");
                        screen.mouseClicked(button.getX()+3,button.getY()+3,0);
                        screen.mouseReleased(button.getX()+3,button.getY()+3,0);
                    }
                    case "track" -> invoke(screen,"toggleTrackedResearch",null,null);
                    case "affordable" -> invoke(screen,"toggleAffordableNow",null,null);
                    case "help" -> invoke(screen,"showGuidance",null,null);
                    case "domain" -> invoke(screen,"selectTechTreeDomain",com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.class,
                            com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.valueOf(data.get("value").getAsString()));
                    case "escape" -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
                    case "enter" -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0);
                    case "down" -> screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN,0,0);
                    case "reload-resources" -> mc.reloadResourcePacks();
                    case "point-sync" -> mc.getSingleplayerServer().execute(() ->
                            com.gamergaming.taczweaponblueprints.network.NetworkHandler.syncPlayerPointBalance(
                                    mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
                    case "scroll-help" -> {
                        var layout=(ResearchTreeWorkbenchLayout)field(screen,"workbenchLayout");var b=layout.help().panel();
                        screen.mouseScrolled(b.x()+12,b.y()+36,data.get("value").getAsDouble());
                    }
                    case "click-search" -> {
                        var buttons=(java.util.List<net.minecraft.client.gui.components.AbstractWidget>)field(screen,"searchResultButtons");
                        var b=buttons.get(data.get("value").getAsInt());
                        if(!b.visible)throw new IllegalStateException("search result is not visible");
                        screen.mouseClicked(b.getX()+4,b.getY()+4,0);screen.mouseReleased(b.getX()+4,b.getY()+4,0);
                    }
                    case "reject-view" -> {
                        var rejected=ResearchBenchScreen.class.getDeclaredField("researchTreePublicationRejected");rejected.setAccessible(true);rejected.setBoolean(screen,true);
                        invoke(screen,"clearRejectedPresentation",null,null);
                    }
                    case "retry-view" -> invoke(screen,"reloadResearchTree",boolean.class,false);
                    case "withdraw" -> {
                        if(savedPublication==null)savedPublication=ClientResearchState.publication();
                        ClientResearchState.publishTreeOnly(com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication.EMPTY);
                    }
                    case "restore-publication" -> {
                        if(savedPublication==null)throw new IllegalStateException("no saved QA publication");
                        ClientResearchState.publishTreeOnly(new com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication(
                                savedPublication.graph(),savedPublication.presentation(),savedPublication.techTree()));savedPublication=null;
                    }
                    case "snapshot" -> {
                        String name = data.get("value").getAsString();
                        if (!name.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid snapshot name");
                        snapshot = name;
                    }
                    case "select" -> {
                        var id = new ResourceLocation(data.get("value").getAsString());
                        if (canvas.graph().node(id).isEmpty()) throw new IllegalArgumentException("node is not disclosed");
                        invoke(screen, "selectTreeNode", ResourceLocation.class, id); canvas.focusTarget(id);
                    }
                    case "click-node" -> {
                        var id = new ResourceLocation(data.get("value").getAsString()); canvas.focusTarget(id); canvas.viewport().finishAnimation();
                        var b = canvas.nodeBounds(id).orElseThrow();
                        double x=canvas.bounds().x()+canvas.viewport().viewportX(b.centerX()), y=canvas.bounds().y()+canvas.viewport().viewportY(b.y()+b.height()/2);
                        screen.mouseClicked(x,y,0); screen.mouseReleased(x,y,0);
                    }
                    case "drag-node" -> {
                        var b=canvas.nodeBounds(canvas.focusedId().orElseThrow()).orElseThrow();
                        double x=canvas.bounds().x()+canvas.viewport().viewportX(b.centerX()), y=canvas.bounds().y()+canvas.viewport().viewportY(b.y()+b.height()/2);
                        screen.mouseClicked(x,y,0); screen.mouseDragged(x+20,y+12,0,20,12); screen.mouseReleased(x+20,y+12,0);
                    }
                    case "click-inspector" -> {
                        var layout=(ResearchTreeWorkbenchLayout)field(screen,"workbenchLayout"); var b=layout.inspectorContent();
                        screen.mouseClicked(b.x()+2,b.y()+2,0); screen.mouseReleased(b.x()+2,b.y()+2,0);
                    }
                    case "research" -> {
                        var button = (net.minecraft.client.gui.components.Button) field(screen, "primaryResearchButton");
                        if (!button.active || !button.visible) throw new IllegalStateException("research action is not available");
                        screen.mouseClicked(button.getX() + 3, button.getY() + 3, 0);
                        screen.mouseReleased(button.getX() + 3, button.getY() + 3, 0);
                    }
                    case "overview" -> canvas.fit();
                    case "zoom" -> screen.mouseScrolled(canvas.bounds().x()+canvas.bounds().width()/2,
                            canvas.bounds().y()+canvas.bounds().height()/2,data.get("value").getAsDouble());
                    case "click-minimap" -> {
                        var map=(ResearchTreeMinimap)field(screen,"treeMinimap"); var b=map.panelBounds();
                        if (!map.visible() || b==null) throw new IllegalStateException("minimap is not visible");
                        screen.mouseClicked(b.x()+b.width()/2,b.y()+b.height()/2,0);
                        screen.mouseReleased(b.x()+b.width()/2,b.y()+b.height()/2,0);
                    }
                    case "branch" -> canvas.focusedId().ifPresent(canvas::focusBranch);
                    case "scroll-inspector" -> {
                        var layout = (ResearchTreeWorkbenchLayout) field(screen, "workbenchLayout"); var b = layout.inspectorContent();
                        screen.mouseScrolled(b.x() + 2, b.y() + 2, data.get("value").getAsDouble());
                    }
                    case "dismiss-help" -> invoke(screen, "dismissGuidance", null, null);
                    default -> throw new IllegalArgumentException("unknown QA action");
                }
            }
            Files.writeString(root.resolve("control-result.json"), "{\"status\":\"accepted\"}\n");
        } catch (Exception e) {
            try { Files.deleteIfExists(command); Files.writeString(root.resolve("control-result.json"),
                    ResearchTreeBaselineInput.JSON.toJson(Map.of("status", "failed", "error", e.toString()))); } catch (Exception ignored) { }
        }
    }
    private static void restoreWindow() {
        if(originalDisplay!=null){
            var config=com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT;
            config.reduceMotion.validateAndSet(originalDisplay.reduceMotion());config.showBackgroundGrid.validateAndSet(originalDisplay.showBackgroundGrid());
            config.minimap.validateAndSet(originalMinimap);config.onUpdateClient();originalDisplay=null;
        }
        if (originalWindow == null) return;
        var mc=Minecraft.getInstance(); org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),originalWindow[0],originalWindow[1]);
        mc.options.guiScale().set(originalGuiScale); originalWindow=null; mc.resizeDisplay();
    }
    private static void requireCopy() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (!List.of("M5 UI QA", "M8 Authored QA").contains(WORLD) || server == null || !server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(WORLD))
            throw new IllegalStateException("QA requires the disposable world copy");
    }
    private static Object field(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
    private static void invoke(Object instance, String name, Class<?> type, Object value) throws Exception {
        var method = ResearchBenchScreen.class.getDeclaredMethod(name, type == null ? new Class<?>[0] : new Class<?>[]{type});
        method.setAccessible(true); method.invoke(instance, type == null ? new Object[0] : new Object[]{value});
    }
    private static JsonObject evidence(ResearchBenchScreen screen) throws Exception {
        var mc = Minecraft.getInstance(); var canvas = (ResearchTreeCanvas) field(screen, "treeCanvas");
        var result = new JsonObject();
        result.addProperty("gui_width", screen.width); result.addProperty("gui_height", screen.height);
        result.addProperty("framebuffer_width", mc.getWindow().getWidth()); result.addProperty("framebuffer_height", mc.getWindow().getHeight());
        result.addProperty("selected", screen.getMenu().selectedBlueprint().map(Object::toString).orElse(""));
        result.addProperty("focused",canvas.focusedId().map(Object::toString).orElse(""));
        result.addProperty("pinned",((ResearchTreeFullscreenOverlayState)field(screen,"fullscreenOverlayState")).pinnedNodeId().map(Object::toString).orElse(""));
        result.add("search",ResearchTreeBaselineInput.JSON.toJsonTree(field(screen,"treeSearch")));
        result.add("affordability",ResearchTreeBaselineInput.JSON.toJsonTree(ClientResearchAffordabilityState.snapshot()));
        var decoration=new TreeMap<String,ResearchTreeCanvas.SceneDecoration>();
        for(var node:canvas.graph().nodes())decoration.put(node.blueprintId().toString(),canvas.sceneDecoration(node.blueprintId()));
        result.add("decoration",ResearchTreeBaselineInput.JSON.toJsonTree(decoration));
        result.add("selection_feedback",ResearchTreeBaselineInput.JSON.toJsonTree(field(screen,"selectionFeedback")));
        result.addProperty("publication_rejected",(Boolean)field(screen,"researchTreePublicationRejected"));
        result.addProperty("next_request",((Number)field(screen,"nextActionRequestId")).intValue());
        result.addProperty("help_visible",(Boolean)field(screen,"guidanceVisible"));
        result.addProperty("resource_generation",ResearchTreeNodeArt.reloadGeneration());
        result.addProperty("zoom_out_active",((net.minecraft.client.gui.components.Button)field(screen,"zoomOutButton")).active);
        result.addProperty("zoom_in_active",((net.minecraft.client.gui.components.Button)field(screen,"zoomInButton")).active);
        result.addProperty("publication_generation",ClientResearchState.publication().generation());
        result.addProperty("preview_identity",System.identityHashCode(screen.getMenu().preview()));
        result.addProperty("help_scroll",((Number)field(screen,"helpScroll")).intValue());
        var previewMethod=ResearchBenchScreen.class.getDeclaredMethod("currentPreview");previewMethod.setAccessible(true);
        result.add("current_preview",ResearchTreeBaselineInput.JSON.toJsonTree(previewMethod.invoke(screen)));
        result.addProperty("research_points", ((Number) field(screen, "researchPoints")).longValue());
        result.addProperty("bench_tier", screen.getMenu().workbenchTier().level());
        var inventory = new TreeMap<String,Integer>();
        for (var stack : mc.player.getInventory().items) if (!stack.isEmpty()) inventory.merge(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        result.add("inventory", ResearchTreeBaselineInput.JSON.toJsonTree(inventory));
        var playerId = mc.player.getUUID(); var server = mc.getSingleplayerServer();
        result.add("server", server.submit(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            var authoritative = new JsonObject(); var items = new TreeMap<String,Integer>();
            for (var stack : player.getInventory().items) if (!stack.isEmpty()) items.merge(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
            authoritative.add("inventory",ResearchTreeBaselineInput.JSON.toJsonTree(items));
            player.getCapability(com.gamergaming.taczweaponblueprints.init.ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(data -> {
                authoritative.addProperty("research_points",data.getResearchPoints());
                authoritative.add("awards",ResearchTreeBaselineInput.JSON.toJsonTree(data.getResearchPointAwardLedger()));
            });
            return authoritative;
        }).get(2,java.util.concurrent.TimeUnit.SECONDS));
        result.addProperty("inspector_scroll", ((Number) field(screen, "inspectorScroll")).intValue());
        result.addProperty("inspector_maximum_scroll", ((Number) field(screen, "inspectorMaximumScroll")).intValue());
        result.add("minimap", ResearchTreeBaselineInput.JSON.toJsonTree(((ResearchTreeMinimap)field(screen,"treeMinimap")).snapshot()));
        var publication = ClientResearchState.publication();
        result.addProperty("public_nodes", publication.graph().nodes().size());
        var sceneResult = (ResearchTreeSceneCache.Result) field(screen, "activeScene");
        if (sceneResult != null) {
            result.addProperty("scene_status", sceneResult.status().name());
            result.add("scene_diagnostics", ResearchTreeBaselineInput.JSON.toJsonTree(sceneResult.diagnostics()));
        }
        var projections = (ResearchTreeProjectionCache) field(screen, "treeProjections");
        result.addProperty("catalog_available", projections.techTreeProjections().available());
        result.addProperty("catalog_domains", projections.techTreeProjections().domains().toString());
        result.add("preview", ResearchTreeBaselineInput.JSON.toJsonTree(screen.getMenu().preview()));
        result.add("camera", ResearchTreeBaselineInput.JSON.toJsonTree(canvas.viewport().snapshot()));
        result.add("display_policy",ResearchTreeBaselineInput.JSON.toJsonTree(canvas.displayPolicy()));
        result.addProperty("minimap_mode",com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT.minimapMode().name());
        result.add("layout", ResearchTreeBaselineInput.JSON.toJsonTree(field(screen, "workbenchLayout")));
        result.add("nodes", ResearchTreeBaselineInput.JSON.toJsonTree(canvas.graph().nodes()));
        result.add("geometry", ResearchTreeBaselineInput.JSON.toJsonTree(canvas.scene().orElse(null)));
        result.add("feedback", ResearchTreeBaselineInput.JSON.toJsonTree(field(screen, "researchFeedback")));
        return result;
    }
}
