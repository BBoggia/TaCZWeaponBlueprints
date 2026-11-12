package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.require;
import java.util.*;
import com.gamergaming.taczweaponblueprints.client.settings.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.settings.SettingsCatalog.QuickControl;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.network.chat.Component;

/** Drives the production screens without changing the server configuration. */
final class QuickSetupScreenVerification {
    private static int step, delay;
    private static QuickSetupScreen quick;
    private static SettingsEditSession draft;
    private static Map<String, String> baseline;
    static boolean tick() {
        var mc = Minecraft.getInstance();
        if (delay-- > 0) return false;
        switch (step) {
            case 0 -> {
                baseline = FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT);
                draft = ClientSettingsAccess.draft().orElseThrow();
                find(mc.screen, "settings.taczweaponblueprints.ui.world").onPress();
                require(mc.screen instanceof QuickSetupScreen, "World & Server opens Quick Setup by default");
                quick = (QuickSetupScreen) mc.screen;
            }
            case 1 -> {
                require(!draft.dirty(), "Quick Setup opens without changing any stored selection");
                screenshot("quick-setup");
                var ammo = choice(QuickControl.AMMO_RESEARCH);
                focus(quick, ammo); quick.keyPressed(257, 0, 0);
                require(draft.proposedChanges().keySet().equals(Set.of("ammoResearch")), "keyboard Quick Setup edit changes only ammo research");
                require(baseline.equals(FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT)), "Quick Setup edits stay pending until Apply");
                find(quick, "settings.taczweaponblueprints.ui.advanced").onPress();
            }
            case 2 -> {
                require(mc.screen instanceof AdvancedSettingsScreen && ClientSettingsAccess.draft().orElseThrow() == draft,
                        "Quick and Advanced share the exact same draft");
                find(mc.screen, "settings.taczweaponblueprints.ui.category.villagers").onPress();
            }
            case 3 -> {
                var widget = mc.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(w -> w.getX() == mc.screen.width - 166 && w.getY() == 72).findFirst().orElseThrow();
                mc.screen.setFocused(widget); mc.screen.keyPressed(257, 0, 0);
                require(draft.proposedChanges().containsKey("enableGunsmith"), "Advanced adds its change to the pending Quick Setup proposal");
                mc.screen.onClose();
            }
            case 4 -> {
                require(mc.screen == quick && ClientSettingsAccess.draft().orElseThrow() == draft,
                        "returning to Quick Setup does not discard or replace the draft");
                require(choice(QuickControl.GUNSMITH).getValue().equals(draft.preview().get("enableGunsmith")),
                        "Quick Setup reflects the edit made in Advanced");
                find(quick, "settings.taczweaponblueprints.ui.summary").onPress();
            }
            case 5 -> {
                screenshot("quick-summary-pending");
                find(quick, "settings.taczweaponblueprints.ui.pending_preview").onPress();
            }
            case 6 -> {
                require(find(quick, "settings.taczweaponblueprints.ui.applied_snapshot") != null,
                        "summary can show the applied snapshot separately from pending changes");
                screenshot("quick-summary-applied");
                quick.onClose();
                // Restore using the actual controls; no server-side apply is involved.
                restore(QuickControl.GUNSMITH); restore(QuickControl.AMMO_RESEARCH);
                require(!draft.dirty(), "restoring Quick controls to baseline removes pending changes");
                quick.resize(mc, 320, 240);
            }
            case 7 -> {
                require(quick.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .allMatch(w -> w.getX() >= 0 && w.getRight() <= 320 && w.getY() >= 0 && w.getBottom() <= 240),
                        "Quick Setup frame and actions fit a 320 by 240 GUI");
                var list = quick.children().stream().filter(AbstractSelectionList.class::isInstance).map(AbstractSelectionList.class::cast).findFirst().orElseThrow();
                list.mouseScrolled(100, 120, 0, -2);
                require(list.getScrollAmount() > 0, "small GUI supports ordinary mouse wheel scrolling");
            }
            case 8 -> {
                screenshot("quick-small-scrolled");
                quick.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                require(baseline.equals(FzzySettingsAdapter.capture(ModConfigs.BLUEPRINT)), "Quick/Advanced navigation and cancelled edits preserve live settings");
                var search = quick.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("Currency Item ID"); quick.setFocused(search); quick.keyPressed(257, 0, 0);
            }
            case 9 -> {
                require(mc.screen instanceof AdvancedSettingsScreen, "Enter in Quick Setup search opens Advanced results");
                find(mc.screen, "config.taczweaponblueprints.blueprint.researchCurrencyItem").onPress();
                require(ClientSettingsAccess.draft().orElseThrow() == draft && !draft.dirty(), "Quick search keeps the same unmodified draft");
                mc.screen.onClose();
                require(mc.screen == quick, "Advanced search returns to its originating Quick Setup screen");
                quick.onClose();
                return true;
            }
            default -> throw new IllegalStateException("Unknown Quick UI fixture step");
        }
        step++; delay = 4; return false;
    }
    private static void restore(QuickControl control) {
        var widget = choice(control);
        for (int i = 0; i < 5 && !widget.getValue().equals(baseline.get(control.presentationField())); i++) widget.onPress();
    }
    private static CycleButton<?> choice(QuickControl control) {
        String label = Component.translatable(QuickSetupSettings.labelKey(control)).getString() + ":";
        return descendants(quick).stream().filter(CycleButton.class::isInstance).map(CycleButton.class::cast)
                .filter(w -> w.getMessage().getString().startsWith(label)).findFirst().orElseThrow();
    }
    private static List<GuiEventListener> descendants(ContainerEventHandler parent) {
        List<GuiEventListener> result = new ArrayList<>();
        for (var child : parent.children()) { result.add(child); if (child instanceof ContainerEventHandler nested) result.addAll(descendants(nested)); }
        return result;
    }
    private static boolean focus(ContainerEventHandler parent, GuiEventListener target) {
        for (var child : parent.children()) {
            if (child == target || child instanceof ContainerEventHandler nested && focus(nested, target)) {
                parent.setFocused(child); return true;
            }
        }
        return false;
    }
    private static Button find(ContainerEventHandler parent, String key) {
        String label = Component.translatable(key).getString();
        return descendants(parent).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(w -> w.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new IllegalStateException("Missing " + label));
    }
    private static void screenshot(String name) {
        try { FeatureClientVerification.screenshot(Minecraft.getInstance(), name); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
