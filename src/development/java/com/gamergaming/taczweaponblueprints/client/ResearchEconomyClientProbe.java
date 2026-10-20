package com.gamergaming.taczweaponblueprints.client;

import static com.gamergaming.taczweaponblueprints.acceptance.ResearchEconomyAcceptance.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.menu.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Two independent real clients exercise the normal screen request and result packets. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, value = Dist.CLIENT)
public final class ResearchEconomyClientProbe {
    private static boolean opened, connected, finished, clicked;
    private static int ticks, age, failedConnections;
    private static BlueprintRecyclerScreen activeScreen;
    private static final AtomicInteger SAVED = new AtomicInteger();
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (++age > 12000) throw new IllegalStateException("Economy client timeout");
            if (mc.player != null && mc.getConnection() != null) connected = true;
            if (mc.player != null && !mc.player.isAlive()) mc.player.respawn();
            var command = read("command");
            if (command.equals("connect") && opened && mc.player == null && mc.screen instanceof DisconnectedScreen) {
                check(++failedConnections <= 3, "repeated client login failure"); opened = false;
            }
            if (command.equals("connect") && !opened && mc.getOverlay() == null && mc.player == null) {
                artifactOrigin(); opened = true;
                mc.options.guiScale().set(3); mc.resizeDisplay();
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("127.0.0.1:25578"),
                        new ServerData("Disposable economy acceptance", "127.0.0.1:25578", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            }
            if (command.equals("disconnect") && mc.player != null) {
                mc.level.disconnect(); mc.disconnect(); mc.setScreen(new TitleScreen()); opened = false;
            }
            if (command.equals("failed")) throw new IllegalStateException(read("server-error.txt"));
            if (command.equals("complete") && (!PHASE.equals("gameplay") || SAVED.get() == 1)) {
                check(connected, "completion needs a real connection");
                write(ROLE + "-complete.json", JSON.toJson(Map.of("passed", true, "connected", true,
                        "screenshots", SAVED.get(), "networkAction", clicked, "loginRetries", failedConnections)));
                finished = true; mc.stop();
            }
            ticks++;
        } catch (Exception e) { fail(e); }
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (!ENABLED || finished || !PHASE.equals("gameplay")) return;
        try {
            if (!read("action").equals("go") || !read(ROLE + "-ack").isEmpty()
                    || !(event.getScreen() instanceof BlueprintRecyclerScreen screen)) return;
            if (activeScreen != screen) { activeScreen = screen; ticks = 0; }
            if (!clicked && ticks < 40) return;
            if (!clicked) {
                check(screen.getMenu().preview().withStateToken(0).toString().equals(read(ROLE + "-preview")), "dedicated preview matches wire");
                var buttonField = BlueprintRecyclerScreen.class.getDeclaredField("primaryButton"); buttonField.setAccessible(true);
                var button = (net.minecraft.client.gui.components.Button)buttonField.get(screen);
                check(button.active && button.visible, "Data action button is ready");
                var detailField = BlueprintRecyclerScreen.class.getDeclaredField("detailsButton"); detailField.setAccessible(true);
                var details = (net.minecraft.client.gui.components.Button) detailField.get(screen);
                check(screen.mouseClicked(details.getX() + 2, details.getY() + 2, 0), "Details opens");
                screen.mouseReleased(details.getX() + 2, details.getY() + 2, 0);
                check(!screen.getMenu().getSlot(0).isActive() && !screen.getMenu().getSlot(1).isActive(), "Details hides workstation slots");
                check(screen.keyPressed(256, 0, 0), "Escape closes Details");
                check(Minecraft.getInstance().screen == screen && screen.getMenu().getSlot(0).isActive()
                        && screen.getMenu().getSlot(1).isActive(), "Escape preserves open workstation and restores slots");
                check(screen.mouseClicked(details.getX() + 2, details.getY() + 2, 0), "Details reopens");
                screen.mouseReleased(details.getX() + 2, details.getY() + 2, 0);
                int x = button.getX() + 2;
                int y = button.getY() + 2;
                check(screen.mouseClicked(x, y, 0), "Data action button");
                var requestField = BlueprintRecyclerScreen.class.getDeclaredField("requests"); requestField.setAccessible(true);
                check(((BlueprintRecyclerRequestTracker)requestField.get(screen)).pending(), "button sends a real network request");
                clicked = true; ticks = 0; return;
            }
            var field = BlueprintRecyclerScreen.class.getDeclaredField("feedback"); field.setAccessible(true);
            var feedback = (BlueprintRecyclerFeedbackState)field.get(screen);
            if (feedback.visibleCode().isEmpty() && ticks < 100) return;
            check(feedback.visibleCode().filter(c -> c == BlueprintRecyclerActionContract.ResultCode.SUCCESS).isPresent(),
                    "Data result crosses connection: " + feedback.visibleCode() + ", preview=" + screen.getMenu().preview());
            event.getGuiGraphics().flush();
            AnalyzerNativeCapture.grab(ROOT, ROLE + "-data.png", SAVED::incrementAndGet, ResearchEconomyClientProbe::fail);
            write(ROLE + "-ack", "done");
        } catch (Exception e) { fail(e); }
    }
    private static void fail(Exception e) {
        try { write(ROLE + "-error.txt", e + "\n" + Arrays.toString(e.getStackTrace())); } catch (Exception ignored) { }
        finished = true; Minecraft.getInstance().stop();
    }
}
