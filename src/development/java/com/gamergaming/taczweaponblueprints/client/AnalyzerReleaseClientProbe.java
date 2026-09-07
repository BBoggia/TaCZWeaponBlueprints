package com.gamergaming.taczweaponblueprints.client;

import static com.gamergaming.taczweaponblueprints.acceptance.AnalyzerReleaseExchange.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.menu.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real multiplayer connection, authoritative previews, button requests, result feedback and reconnect. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT)
public final class AnalyzerReleaseClientProbe {
    private static boolean opened, connected, finished, disconnected, clicked, confirmed;
    private static int total, ticks, scale, assertions, captures;
    private static String active = "";
    private static final AtomicInteger SAVED = new AtomicInteger();
    private AnalyzerReleaseClientProbe() { }
    private static void verify(boolean condition, String message) { check(condition, message); assertions++; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (++total > 12000) throw new IllegalStateException("Analyzer multiplayer QA timeout");
            String command = read("command");
            if (opened && mc.player != null && mc.getConnection() != null) connected = true;
            if ((command.equals("connect") && !opened || command.equals("reconnect") && disconnected)
                    && mc.getOverlay() == null && mc.player == null && !(mc.screen instanceof ConnectScreen)) {
                if (!opened) { scale = mc.options.guiScale().get(); opened = true; mc.options.guiScale().set(3); mc.resizeDisplay(); }
                disconnected = false;
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("127.0.0.1:25577"),
                        new ServerData("Disposable Analyzer acceptance", "127.0.0.1:25577", false), false);
            }
            if (command.equals("disconnect") && mc.player != null) {
                mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); disconnected = true;
            }
            if (command.equals("failed")) throw new IllegalStateException("Dedicated probe failed: " + read("server-error.txt"));
            if (command.equals("complete") && SAVED.get() == captures) {
                requireConnectedCompletion(opened, connected);
                var result = new JsonObject(); result.addProperty("passed", true); result.addProperty("assertions", assertions);
                result.addProperty("screenshots", captures); result.addProperty("phase", PHASE);
                write("client-complete.json", JSON.toJson(result)); stop(); return;
            }
            ticks++;
        } catch (Exception e) { fail(e); }
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (!ENABLED || finished || !opened || !PHASE.equals("gameplay")) return;
        var mc = Minecraft.getInstance();
        try {
            var text = read("request.json");
            if (text.isEmpty() || !(event.getScreen() instanceof BlueprintRecyclerScreen screen) || mc.getOverlay() != null) return;
            var request = JsonParser.parseString(text).getAsJsonObject();
            int step = request.get("step").getAsInt(), attempt = request.get("attempt").getAsInt();
            String identity = step + ":" + attempt;
            if (read("ack").equals(identity)) return;
            if (!identity.equals(active)) { active = identity; ticks = 0; clicked = false; confirmed = false;
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2, 2); }
            if (ticks < 30) return;
            if (!clicked) {
                verify(screen.getMenu().preview().withStateToken(0).toString().equals(read("preview")), "network preview matches dedicated server: " + identity
                        + "\nExpected: " + read("preview") + "\nActual: " + screen.getMenu().preview().withStateToken(0));
                verify(screen.width >= BlueprintRecyclerMenu.Layout.PANEL_WIDTH && screen.height >= BlueprintRecyclerMenu.Layout.PANEL_HEIGHT, "native layout fits");
                if (Boolean.getBoolean("tacz.analyzerReleaseQA.loadedAmmo") && (step == 9 || step == 10 || step == 19)) {
                    var method = BlueprintRecyclerScreen.class.getDeclaredMethod("ammunitionReturn", com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview.class);
                    method.setAccessible(true);
                    verify(((Optional<?>) method.invoke(screen, screen.getMenu().preview())).isPresent(), "loaded weapon preview shows ammunition return");
                }
                if (request.get("action").getAsBoolean()) {
                    var actionButton = AnalyzerWorkbenchProbeSupport.button(screen, "primaryButton");
                    var x = actionButton.getX() + 2;
                    var y = actionButton.getY() + 2;
                    verify(screen.mouseClicked(x, y, 0), "native action button clicked"); clicked = true; ticks = 0; return;
                }
            }
            if (clicked) {
                if (step == 19 && !confirmed) {
                    var confirmation = BlueprintRecyclerScreen.class.getDeclaredField("confirmationAction"); confirmation.setAccessible(true);
                    verify(((Optional<?>)confirmation.get(screen)).filter(a -> a == BlueprintRecyclerActionContract.Action.RECOVER_POINTS).isPresent(), "unknown-weapon recovery requires confirmation");
                    verify(screen.getMenu().preview().withStateToken(0).toString().equals(read("preview")), "first recovery click does not consume input or RP");
                    var actionButton = AnalyzerWorkbenchProbeSupport.button(screen, "primaryButton");
                    int x = actionButton.getX() + 2;
                    int y = actionButton.getY() + 2;
                    verify(screen.mouseClicked(x, y, 0), "confirm direct RP recovery"); confirmed = true; ticks = 0; return;
                }
                var field = BlueprintRecyclerScreen.class.getDeclaredField("feedback"); field.setAccessible(true);
                var feedback = (BlueprintRecyclerFeedbackState)field.get(screen);
                verify(feedback.visibleCode().filter(c -> c == BlueprintRecyclerActionContract.ResultCode.SUCCESS).isPresent(), "committed response reaches real multiplayer screen: " + identity);
                if (step == 9 || step == 10) verify(feedback.visibleOutcome().isPresent(), "server outcome feedback");
                if (step == 11) verify(feedback.visibleFragments().orElseThrow().blueprintCreated(), "reconstruction feedback");
            }
            event.getGuiGraphics().flush(); captures++;
            String filename = String.format(Locale.ROOT, "%02d-%s-%d.png", step, request.get("name").getAsString(), attempt);
            AnalyzerNativeCapture.grab(ROOT, filename, () -> SAVED.incrementAndGet(), AnalyzerReleaseClientProbe::fail);
            write("ack", identity);
        } catch (Exception e) { fail(e); }
    }
    private static void stop() { finished = true; var mc = Minecraft.getInstance(); if (opened) { mc.options.guiScale().set(scale); mc.resizeDisplay(); } mc.stop(); }
    private static void fail(Exception e) { try { write("client-error.txt", e + "\n" + Arrays.toString(e.getStackTrace())); } catch (Exception ignored) { } stop(); }
}
