package com.gamergaming.taczweaponblueprints.client;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in native observer, compiled into the development run only. Never sends research actions. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT)
public final class ResearchTreeBaselineCapture {
    private static final boolean ENABLED = System.getProperty("tacz.treeBaseline.output") != null;
    private static final boolean MOTION = Boolean.getBoolean("tacz.treeBaseline.motion");
    private static ResearchBenchScreen screen;
    private static ResearchTreeCanvas canvas;
    private static long openingStarted;
    private static long renderStarted;
    private static long publicationGeneration;
    private static double firstFrameMs;
    private static Path directory;
    private static JsonObject record;
    private static ResearchTreeViewport.Snapshot originalCamera;
    private static Context context;
    private static int frame;
    private static boolean completed;
    private static final Map<String, List<Long>> samples = new LinkedHashMap<>();

    private ResearchTreeBaselineCapture() { }

    /** Identity is deliberate: replacement views must not inherit measurements or camera restoration. */
    record Context(Object canvas, Object graph, Object layout, ResearchTreeScreenLayout.Rect bounds,
            int screenWidth, int screenHeight, int framebufferWidth, int framebufferHeight,
            double guiScale, int frameRateLimit, boolean vsync) {
        boolean matches(Context other) {
            return other != null && canvas == other.canvas && graph == other.graph && layout == other.layout
                    && bounds.equals(other.bounds) && screenWidth == other.screenWidth && screenHeight == other.screenHeight
                    && framebufferWidth == other.framebufferWidth && framebufferHeight == other.framebufferHeight
                    && guiScale == other.guiScale && frameRateLimit == other.frameRateLimit && vsync == other.vsync;
        }
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        if (!ENABLED || !(event.getNewScreen() instanceof ResearchBenchScreen bench)) return;
        screen = bench;
        canvas = null;
        record = null;
        directory = null;
        originalCamera = null;
        context = null;
        completed = false;
        renderStarted = 0;
        openingStarted = System.nanoTime();
        frame = 0;
        samples.clear();
    }

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!ENABLED || completed || event.getScreen() != screen) return;
        try {
            if (!checkContext()) return;
            if (canvas != null && MOTION) {
                // Repeatable camera-only workload; never select, click, or research a node.
                var camera = canvas.viewport();
                if (frame == 180 || frame == 300) camera.restore(originalCamera);
                if (frame >= 180 && frame < 300) camera.panByScreenDelta(
                        Math.sin((frame - 180) * 0.12) * 9, Math.cos((frame - 180) * 0.12) * 6);
                if (frame >= 300 && (frame - 300) % 15 == 0) {
                    camera.zoomAt(frame < 360 ? 1 : -1,
                            canvas.bounds().width() / 2.0, canvas.bounds().height() / 2.0);
                }
            }
            renderStarted = System.nanoTime();
        } catch (Exception failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public static void afterRender(ScreenEvent.Render.Post event) {
        if (!ENABLED || completed || event.getScreen() != screen || renderStarted == 0) return;
        long finished = System.nanoTime();
        long renderNanos = finished - renderStarted;
        renderStarted = 0;
        try {
            if (record == null) {
                firstFrameMs = (finished - openingStarted) / 1_000_000.0;
                capture(event);
                return;
            }
            // Screen.render can rebuild its canvas/layout after the pre-render guard.
            if (!checkContext()) return;
            String phase = frame < 60 ? "warmup" : frame < 180 ? "stationary"
                    : frame < 300 ? "pan" : "zoom";
            if (!MOTION && frame >= 180) phase = "manual_interaction";
            samples.computeIfAbsent(phase, ignored -> new ArrayList<>()).add(renderNanos);
            if (frame == 179 || frame == 299 || frame == 419) {
                record.add(phase + "_camera", ResearchTreeBaselineInput.JSON.toJsonTree(canvas.viewport().snapshot()));
                event.getGuiGraphics().flush();
                screenshot(phase + ".png");
            }
            if (++frame >= 420) finish("complete");
        } catch (Exception failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (!ENABLED || completed || event.getScreen() != screen) return;
        try {
            finish("screen_closed_early");
        } catch (Exception failure) {
            fail(failure);
        }
    }

    private static void capture(ScreenEvent.Render.Post event) throws Exception {
        var minecraft = Minecraft.getInstance();
        var candidateCanvas = readCanvas();
        if (candidateCanvas == null || candidateCanvas.graph().nodes().isEmpty()) return;
        var publication = ClientResearchState.publication();
        var catalog = ResearchTechTreeProjectionBuilder.build(new ResearchTreePublication(
                publication.graph(), publication.presentation(), publication.techTree()));
        var active = catalog.projections().stream().filter(p -> p.graph().equals(candidateCanvas.graph()))
                .findFirst().orElse(null);
        if (active == null) return; // Wait for a fully matching visible publication.
        canvas = candidateCanvas;
        publicationGeneration = publication.generation();
        originalCamera = canvas.viewport().snapshot();
        context = context(canvas);
        var policy = ResearchTechTreeLayoutPolicy.fromShared(ModConfigs.RESEARCH_TREE_CLIENT.layoutPolicy());
        var input = ResearchTreeBaselineInput.capture("live_client_projection",
                canvas.bounds().width(), policy, catalog);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(Instant.now());
        directory = Path.of(System.getProperty("tacz.treeBaseline.output")).resolve(stamp);
        input.write(directory.resolve("projection.json"));
        record = ResearchTreeBaselineTool.environment();
        record.addProperty("status", "sampling");
        record.addProperty("input_sha256", input.sha256());
        record.addProperty("measurement", "ScreenEvent render CPU interval, entire Research Bench; excludes world rendering, frame wait and GPU completion");
        record.addProperty("scripted_camera_motion", MOTION);
        record.addProperty("opening_to_first_nonempty_frame_ms", firstFrameMs);
        record.addProperty("screen_width_gui", screen.width);
        record.addProperty("screen_height_gui", screen.height);
        record.addProperty("framebuffer_width", minecraft.getWindow().getWidth());
        record.addProperty("framebuffer_height", minecraft.getWindow().getHeight());
        record.addProperty("gui_scale_setting", minecraft.options.guiScale().get());
        record.addProperty("effective_gui_scale", minecraft.getWindow().getGuiScale());
        record.addProperty("frame_rate_limit", minecraft.options.framerateLimit().get());
        record.addProperty("vsync", minecraft.options.enableVsync().get());
        record.addProperty("active_domain", active.domain().name());
        record.add("active_projection", ResearchTreeBaselineTool.summary(active));
        record.add("initial_camera", ResearchTreeBaselineInput.JSON.toJsonTree(originalCamera));
        canvas.focusedId().ifPresent(id -> record.addProperty("focused_node", id.toString()));
        canvas.authoritativeSelectedId().ifPresent(id -> record.addProperty("selected_node", id.toString()));
        record.add("canvas_bounds", ResearchTreeBaselineInput.JSON.toJsonTree(canvas.bounds()));
        record.add("layout_policy", ResearchTreeBaselineInput.JSON.toJsonTree(policy));
        var versions = new JsonObject();
        for (String id : List.of("minecraft", "forge", "tacz", "fzzy_config", TaCZWeaponBlueprints.MODID)) {
            ModList.get().getModContainerById(id).ifPresent(mod -> versions.addProperty(id,
                    mod.getModInfo().getVersion().toString()));
        }
        record.add("versions", versions);
        writeRecord();
        event.getGuiGraphics().flush();
        screenshot("opening.png");
        System.out.println("[Research baseline] Captured matching client projection: " + directory);
    }

    private static void finish(String status) throws Exception {
        completed = true;
        try {
            restoreCamera();
            if (record != null) {
                var timings = new JsonObject();
                samples.forEach((phase, values) -> timings.add(phase, ResearchTreeBaselineTool.statistics(values)));
                record.add("screen_render_cpu", timings);
                record.addProperty("status", status);
                writeRecord();
                System.out.println("[Research baseline] Native sampling " + status + ": " + directory);
            }
        } finally {
            releaseView();
        }
    }

    private static ResearchTreeCanvas readCanvas() throws ReflectiveOperationException {
        // Reflection stays in the development observer; production APIs need no debug accessors.
        Field field = ResearchBenchScreen.class.getDeclaredField("treeCanvas");
        field.setAccessible(true);
        return (ResearchTreeCanvas) field.get(screen);
    }

    private static Context context(ResearchTreeCanvas candidate) {
        if (candidate == null) return null;
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        return new Context(candidate, candidate.graph(), candidate.layout(), candidate.bounds(), screen.width, screen.height,
                window.getWidth(), window.getHeight(), window.getGuiScale(), minecraft.options.framerateLimit().get(),
                minecraft.options.enableVsync().get());
    }

    private static boolean checkContext() throws Exception {
        if (record == null) return true;
        if (ClientResearchState.publication().generation() != publicationGeneration) {
            finish("publication_changed");
            return false;
        }
        if (!context.matches(context(readCanvas()))) {
            finish("view_changed");
            return false;
        }
        return true;
    }

    private static void restoreCamera() throws ReflectiveOperationException {
        if (MOTION && canvas != null && originalCamera != null && context != null
                && ClientResearchState.publication().generation() == publicationGeneration
                && context.matches(context(readCanvas()))) canvas.viewport().restore(originalCamera);
    }

    private static void releaseView() {
        screen = null;
        canvas = null;
        originalCamera = null;
        context = null;
        renderStarted = 0;
    }

    private static void writeRecord() throws Exception {
        Files.writeString(directory.resolve("runtime.json"), ResearchTreeBaselineInput.JSON.toJson(record) + "\n");
    }

    private static void screenshot(String name) {
        Screenshot.grab(directory.toFile(), name, Minecraft.getInstance().getMainRenderTarget(),
                result -> System.out.println("[Research baseline] " + result.getString()));
    }

    private static void fail(Exception failure) {
        completed = true;
        try { restoreCamera(); } catch (Exception restoreFailure) { failure.addSuppressed(restoreFailure); }
        finally { releaseView(); }
        if (record != null) {
            record.addProperty("status", "failed");
            record.addProperty("failure", failure.toString());
            try { writeRecord(); } catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        }
        System.err.println("[Research baseline] Capture failed: " + failure);
        failure.printStackTrace();
    }
}
