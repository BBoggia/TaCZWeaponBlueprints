package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.require;
import java.util.*;
import com.gamergaming.taczweaponblueprints.client.settings.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.PersonalSettingsSession;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.settings.*;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import com.gamergaming.taczweaponblueprints.client.TechTreeLaneMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class PersonalSettingsScreenVerification {
    private static int step, delay;
    private static Map<String, String> server, personal;
    static boolean tick() {
        var mc = Minecraft.getInstance(); var live = ModConfigs.RESEARCH_TREE_CLIENT;
        if (delay-- > 0) return false;
        switch (step) {
            case 0 -> {
                server = FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT);
                live.nodeGap.validateAndSet(77); live.holdToResearch.validateAndSet(false);
                live.holdDurationMillis.validateAndSet(1200); live.techTreeLanes.validateAndSet(TechTreeLaneMode.BRANCHES);
                live.onUpdateClient(); live.save(); personal = PersonalSettingsSession.capture(live);
                button("settings.taczweaponblueprints.ui.personal").onPress();
                require(mc.screen instanceof PersonalSettingsScreen, "personal scope opens the focused client-only editor");
            }
            case 1 -> {
                screenshot("personal-settings");
                require(personal.equals(PersonalSettingsSession.capture(live)), "opening Personal Settings preserves hidden and visible values");
                var duration = editor("holdDurationMillis");
                require(!duration.active, "hold duration is inactive while hold-to-confirm is off");
                press("techTreeLanes");
                require(live.techTreeLanes.get() == TechTreeLaneMode.BRANCHES, "personal widget edit stays detached until Apply");
                button("settings.taczweaponblueprints.ui.apply").onPress();
            }
            case 2 -> {
                require(live.techTreeLanes.get() == TechTreeLaneMode.TYPES, "personal Apply updates the same preference read by the Tech Tree");
                require(live.nodeGap.getUnconditional() == 77 && live.holdDurationMillis() == 1200, "personal Apply preserves hidden layout and dormant hold duration");
                mc.screen.onClose();
                require(mc.screen instanceof SettingsLandingScreen, "Personal Settings Back returns to the landing page");
                button("settings.taczweaponblueprints.ui.personal").onPress();
                live.chooseTechTreeLanes(TechTreeLaneMode.BRANCHES);
                press("holdToResearch"); button("settings.taczweaponblueprints.ui.apply").onPress();
            }
            case 3 -> {
                require(live.techTreeLanes.get() == TechTreeLaneMode.BRANCHES && !live.holdToResearchEnabled(), "stale personal draft cannot overwrite a newer in-tree toggle");
                button("settings.taczweaponblueprints.ui.discard").onPress();
                require(mc.screen instanceof ConfirmScreen, "discarding personal changes asks before dropping the proposal");
                button("settings.taczweaponblueprints.ui.discard").onPress();
            }
            case 4 -> {
                button("config.taczweaponblueprints.research_tree_client.resetTreeAppearance.button").onPress();
                require(mc.screen instanceof ConfirmScreen, "appearance reset explains its scope before queuing");
                button("gui.cancel").onPress();
                require(live.nodeGap.getUnconditional() == 77 && live.techTreeLanes.get() == TechTreeLaneMode.BRANCHES, "cancelled appearance reset changes nothing");
                button("config.taczweaponblueprints.research_tree_client.resetTreeAppearance.button").onPress();
                button("settings.taczweaponblueprints.ui.queue_reset").onPress();
            }
            case 5 -> {
                require(live.nodeGap.getUnconditional() == 77 && live.techTreeLanes.get() == TechTreeLaneMode.BRANCHES, "queued appearance reset waits for Apply");
                button("settings.taczweaponblueprints.ui.apply").onPress();
            }
            case 6 -> {
                require(live.techTreeLanes.get() == TechTreeLaneMode.BRANCHES && live.nodeGap.getUnconditional() != 77, "appearance reset retains its visual and legacy layout scope");
                require(!live.holdToResearchEnabled() && live.holdDurationMillis() == 1200 && live.showResearchPointNotifications.get(), "appearance reset keeps interaction and reward-message preferences");
                require(server.equals(FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT)) && !ClientSettingsAccess.draft().orElseThrow().dirty(), "personal edits never mutate server gameplay or its pending draft");
                mc.screen.resize(mc, 320, 240);
                require(mc.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .allMatch(w -> w.getX() >= 0 && w.getRight() <= 320 && w.getY() >= 0 && w.getBottom() <= 240), "personal settings fit a 320 by 240 GUI");
            }
            case 7 -> {
                screenshot("personal-small");
                mc.screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                mc.screen.onClose();
                var landing = mc.screen;
                ConfigApiJava.INSTANCE.openScreen("taczweaponblueprints.research_tree_client");
                require(mc.screen instanceof PersonalSettingsScreen, "direct Fzzy personal scope also hides inactive layout editors");
                mc.setScreen(landing); return true;
            }
        }
        step++; delay = 4; return false;
    }
    private static AbstractWidget editor(String field) {
        var mc = Minecraft.getInstance(); int y = button("config.taczweaponblueprints.research_tree_client." + field).getY();
        return mc.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(w -> w.getX() == mc.screen.width - 160 && w.getY() == y).findFirst().orElseThrow();
    }
    private static void press(String field) { var mc = Minecraft.getInstance(); mc.screen.setFocused(editor(field)); mc.screen.keyPressed(257, 0, 0); }
    private static Button button(String key) {
        String label = Component.translatable(key).getString();
        return Minecraft.getInstance().screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new IllegalStateException("Missing " + label));
    }
    private static void screenshot(String name) {
        try { FeatureClientVerification.screenshot(Minecraft.getInstance(), name); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
