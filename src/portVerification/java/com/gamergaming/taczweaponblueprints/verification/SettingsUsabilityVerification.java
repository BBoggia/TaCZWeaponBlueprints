package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.require;
import java.util.*;
import com.gamergaming.taczweaponblueprints.client.settings.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;

final class SettingsUsabilityVerification {
    private static int step, delay, oldScale;
    private static Language language;
    private static QuickSetupScreen quick;
    static boolean tick() {
        var mc = Minecraft.getInstance(); if (delay-- > 0) return false;
        switch (step) {
            case 0 -> {
                mc.getToasts().clear();
                oldScale = mc.options.guiScale().get(); language = Language.getInstance();
                mc.options.guiScale().set(0); mc.resizeDisplay();
                quick = new QuickSetupScreen(null); mc.setScreen(quick);
            }
            case 1 -> {
                bounds();
                require(quick.width == mc.getWindow().getGuiScaledWidth() && quick.height == mc.getWindow().getGuiScaledHeight(), "settings tested at actual automatic GUI scale");
                Set<String> reached = new HashSet<>();
                for (int i = 0; i < 90; i++) {
                    quick.keyPressed(258, 0, 0);
                    descendants(quick).stream().filter(CycleButton.class::isInstance).map(CycleButton.class::cast)
                            .filter(AbstractWidget::isFocused).forEach(w -> reached.add(w.getMessage().getString()));
                }
                require(reached.size() == SettingsCatalog.QuickControl.values().length, "keyboard Tab reaches every Quick Setup control at high GUI scale");
                screenshot("settings-auto-scale");
                mc.setScreen(new PersonalSettingsScreen(quick));
            }
            case 2 -> {
                bounds(); screenshot("settings-personal-auto-scale");
                var widget = mc.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(w -> w.getX() == mc.screen.width - 160 && w.getY() == 54).findFirst().orElseThrow();
                var narration = new ScreenNarrationCollector(); narration.update(widget::updateNarration);
                require(narration.collectNarrationText(true).contains(Component.translatable("config.taczweaponblueprints.research_tree_client.holdToResearch").getString()), "native preference narration includes its full setting name");
                mc.screen.onClose();
                Language.inject(new Language() {
                    public String getOrDefault(String key, String fallback) {
                        String value = language.getOrDefault(key, fallback);
                        return key.startsWith("settings.taczweaponblueprints.choice.") || key.startsWith("settings.taczweaponblueprints.quick.")
                                ? value + " — extended translation for layout verification" : value;
                    }
                    public boolean has(String key) { return language.has(key); }
                    public boolean isDefaultRightToLeft() { return language.isDefaultRightToLeft(); }
                    public FormattedCharSequence getVisualOrder(FormattedText value) { return language.getVisualOrder(value); }
                });
                mc.setScreen(new QuickSetupScreen(null));
            }
            case 3 -> {
                bounds(); screenshot("settings-long-translations");
                Language.inject(language); mc.options.guiScale().set(oldScale); mc.resizeDisplay();
                mc.setScreen(new AdvancedSettingsScreen(null));
            }
            case 4 -> {
                var search = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("cost mode");
                search = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                mc.screen.setFocused(search); search.setCursorPosition(1); search.setHighlightPos(1); mc.screen.charTyped('x', 0);
                search = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                require(search.getValue().equals("cxost mode") && search.getCursorPosition() == 2, "search edits in the middle preserve cursor position");
                require(!ClientSettingsAccess.draft().orElseThrow().dirty(), "keyboard, narration and translation checks create no edits");
                mc.setScreen(null); return true;
            }
        }
        step++; delay = 5; return false;
    }
    static List<GuiEventListener> descendants(ContainerEventHandler parent) {
        List<GuiEventListener> result = new ArrayList<>();
        for (var child : parent.children()) { result.add(child); if (child instanceof ContainerEventHandler nested) result.addAll(descendants(nested)); }
        return result;
    }
    private static void bounds() {
        var screen = Minecraft.getInstance().screen;
        require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .allMatch(w -> w.getX() >= 0 && w.getRight() <= screen.width && w.getY() >= 0 && w.getBottom() <= screen.height), "settings frame fits actual GUI scale: " + screen.getClass().getSimpleName());
    }
    private static void screenshot(String name) {
        try { FeatureClientVerification.screenshot(Minecraft.getInstance(), name); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
