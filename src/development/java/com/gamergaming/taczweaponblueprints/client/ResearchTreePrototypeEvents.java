package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Path;
import java.nio.file.Files;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Development-only entry point; no ordinary run or release registers these handlers. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT)
public final class ResearchTreePrototypeEvents {
    private static final boolean ENABLED = Boolean.getBoolean("tacz.treePrototype.enabled");
    private static boolean titleOpened, benchOpened;
    private static int[] originalWindow;
    private static int originalGuiScale;
    private ResearchTreePrototypeEvents() { }

    /** Bounded local commands for repeatable native QA; never evaluates code or sends research actions. */
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Path root = Path.of(System.getProperty("tacz.treePrototype.output"));
        Path command = root.resolve("control.json");
        if (!Files.isRegularFile(command)) return;
        try {
            if (Files.size(command) > 4096) throw new IllegalArgumentException("prototype command is too large");
            var json = com.google.gson.JsonParser.parseString(Files.readString(command)).getAsJsonObject();
            Files.delete(command);
            var minecraft = Minecraft.getInstance();
            String action = json.get("action").getAsString();
            if (action.equals("resize")) {
                if (!(minecraft.screen instanceof ResearchTreePrototypeScreen)) throw new IllegalArgumentException("comparison screen required");
                int width = json.get("width").getAsInt(), height = json.get("height").getAsInt(), scale = json.get("guiScale").getAsInt();
                if (width < 640 || width > 2000 || height < 360 || height > 1200 || scale < 0 || scale > 6)
                    throw new IllegalArgumentException("prototype resize out of range");
                if (originalWindow == null) {
                    int[] w = new int[1], h = new int[1]; GLFW.glfwGetWindowSize(minecraft.getWindow().getWindow(), w, h);
                    originalWindow = new int[] {w[0], h[0]}; originalGuiScale = minecraft.options.guiScale().get();
                }
                GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), width, height);
                minecraft.options.guiScale().set(scale); minecraft.resizeDisplay();
            } else if (action.equals("restore")) restoreWindow();
            else if (action.equals("close")) { restoreWindow(); minecraft.stop(); }
            else if (minecraft.screen instanceof ResearchTreePrototypeScreen screen) {
                screen.control(action, json.has("value") ? json.get("value").getAsString() : "");
            } else throw new IllegalArgumentException("comparison screen required");
            Files.writeString(root.resolve("control-result.json"), "{\"status\":\"accepted\"}\n");
        } catch (Exception failure) {
            try { Files.deleteIfExists(command); Files.writeString(root.resolve("control-result.json"),
                    ResearchTreeBaselineInput.JSON.toJson(java.util.Map.of("status", "failed", "error", failure.toString())) + "\n"); }
            catch (Exception ignored) { }
            System.err.println("[Tree prototype] Control failed: " + failure);
        }
    }

    static void restoreWindow() {
        if (originalWindow == null) return;
        var minecraft = Minecraft.getInstance();
        GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), originalWindow[0], originalWindow[1]);
        minecraft.options.guiScale().set(originalGuiScale); originalWindow = null; minecraft.resizeDisplay();
    }

    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (!ENABLED || Minecraft.getInstance().getOverlay() != null) return;
        try {
            if (!titleOpened && event.getScreen() instanceof TitleScreen && System.getProperty("tacz.treePrototype.input") != null) {
                titleOpened = true;
                var input = ResearchTreeBaselineInput.read(Path.of(System.getProperty("tacz.treePrototype.input")));
                Minecraft.getInstance().setScreen(new ResearchTreePrototypeScreen(event.getScreen(), input, true));
            } else if (!benchOpened && event.getScreen() instanceof ResearchBenchScreen && Boolean.getBoolean("tacz.treePrototype.captureBench")) {
                if (openBench()) benchOpened = true;
            }
        } catch (Exception failure) { System.err.println("[Tree prototype] Cannot open: " + failure); }
    }

    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (ENABLED && event.getKeyCode() == GLFW.GLFW_KEY_F8 && event.getScreen() instanceof ResearchBenchScreen) {
            if (openBench()) { benchOpened = true; event.setCanceled(true); }
        }
    }

    private static boolean openBench() {
        var publicState = ClientResearchState.publication();
        var catalog = ResearchTechTreeProjectionBuilder.build(new ResearchTreePublication(publicState.graph(), publicState.presentation(), publicState.techTree()));
        if (!catalog.available()) return false;
        var input = ResearchTreeBaselineInput.capture("live_bench_snapshot", Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                ResearchTechTreeLayoutPolicy.DEFAULT, catalog);
        // Closing the Bench's menu is intentional. Returning goes to the world, not an obsolete menu instance.
        Minecraft.getInstance().setScreen(new ResearchTreePrototypeScreen(null, input, true));
        return true;
    }

    @Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> ResearchTreePrototypeIcons.reloaded());
        }
    }
}
