package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;

/** Scripted camera/view comparison. Serialization and framebuffer capture are outside timed rendering. */
final class ResearchTreePrototypeCapture {
    private static final List<String> STAGES = List.of("tile-opening", "tile-overview", "tile-target", "tile-branch",
            "card-target", "card-overview", "current-overview", "current-target", "world-card-target",
            "nested-overview", "unknown-target", "dense-overview", "tile-return");
    private final ResearchTreePrototypeScreen screen;
    private final List<Long> samples = new ArrayList<>();
    private final JsonArray stages = new JsonArray();
    private Path directory;
    private int stage, frame, width, height, guiWidth, guiHeight;
    private boolean stopped, started;

    ResearchTreePrototypeCapture(ResearchTreePrototypeScreen screen) { this.screen = screen; }

    void afterRender(GuiGraphics graphics, long nanos) {
        if (stopped) return;
        try {
            var window = Minecraft.getInstance().getWindow();
            if (!started) {
                directory = Path.of(System.getProperty("tacz.treePrototype.output", "build/reports/research-tree/prototype"))
                        .resolve(Instant.now().toString().replace(':', '-'));
                Files.createDirectories(directory);
                width = window.getWidth(); height = window.getHeight(); guiWidth = screen.width; guiHeight = screen.height;
                started = true; screen.captureStage(0); write("sampling"); return;
            }
            if (width != window.getWidth() || height != window.getHeight() || guiWidth != screen.width || guiHeight != screen.height) {
                stopped = true; write("view_changed"); return;
            }
            if (frame++ >= 30) samples.add(nanos);
            if (frame < 90) return;
            var state = screen.evidence();
            state.addProperty("stage", STAGES.get(stage));
            state.add("render_cpu", ResearchTreeBaselineTool.statistics(samples));
            stages.add(state);
            graphics.flush();
            Screenshot.grab(directory.toFile(), String.format("%02d-%s.png", stage, STAGES.get(stage)),
                    Minecraft.getInstance().getMainRenderTarget(), message -> System.out.println("[Tree prototype] " + message.getString()));
            samples.clear(); frame = 0;
            if (++stage == STAGES.size()) { stopped = true; write("complete"); }
            else { write("sampling"); screen.captureStage(stage); }
        } catch (Exception failure) {
            stopped = true;
            System.err.println("[Tree prototype] Capture failed: " + failure);
            try { write("failed: " + failure); } catch (Exception ignored) { }
        }
    }

    void close() {
        if (stopped || !started) return;
        stopped = true;
        try { write("interrupted"); } catch (Exception failure) { System.err.println("[Tree prototype] " + failure); }
    }

    private void write(String status) throws Exception {
        var result = ResearchTreeBaselineTool.environment();
        result.addProperty("status", status);
        result.addProperty("measurement", "Prototype Screen.render CPU including final GUI flush; 30 warmup and 60 sampled frames per state; excludes capture, world render, frame wait and GPU completion");
        result.add("stages", stages);
        Files.writeString(directory.resolve("runtime.json"), ResearchTreeBaselineInput.JSON.toJson(result) + "\n");
        System.out.println("[Tree prototype] " + status + ": " + directory);
    }
}
