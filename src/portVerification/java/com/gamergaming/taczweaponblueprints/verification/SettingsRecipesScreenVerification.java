package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.require;
import java.util.*;
import com.gamergaming.taczweaponblueprints.client.settings.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.progression.StationSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.network.chat.Component;

final class SettingsRecipesScreenVerification {
    private static int step, delay;
    private static SettingsEditSession draft;
    private static Map<String, String> original;
    private static QuickSetupScreen quick;
    static boolean tick() {
        var mc = Minecraft.getInstance(); if (delay-- > 0) return false;
        switch (step) {
            case 0 -> {
                draft = ClientSettingsAccess.draft().orElseThrow();
                draft.setChoice("researchAccess", StationSetup.ResearchAccess.PORTABLE);
                draft.setBoolean("enableResearch", false); draft.setBoolean("enableJournal", false);
                original = draft.proposedChanges(); quick = new QuickSetupScreen(mc.screen); mc.setScreen(quick);
                button("settings.taczweaponblueprints.ui.recipes").onPress();
            }
            case 1 -> {
                require(mc.screen instanceof SettingsRecipesScreen, "Quick Setup opens explicit setup recipes");
                button(SettingsRecipes.Recipe.SIMPLER_STATIONS.key()).onPress();
            }
            case 2 -> {
                screenshot("recipe-simpler-preview");
                require(original.equals(draft.proposedChanges()), "viewing a recipe leaves pending changes untouched");
                mc.screen.onClose();
                require(original.equals(draft.proposedChanges()), "cancelling a recipe preview is a no-op");
                button(SettingsRecipes.Recipe.SIMPLER_STATIONS.key()).onPress();
                button("settings.taczweaponblueprints.ui.queue_recipe").onPress();
            }
            case 3 -> {
                require(mc.screen == quick, "queueing a recipe returns to Quick Setup without applying");
                require(draft.proposedChanges().keySet().equals(Set.of("researchAccess", "analyzerSetup", "enableResearch", "enableJournal")), "Simpler Stations queues only its two fields alongside existing edits");
                require(!SettingsValues.booleanValue(draft.preview().get("enableResearch")), "Simpler Stations retains disabled research");
                button("settings.taczweaponblueprints.ui.recipes").onPress();
                button(SettingsRecipes.Recipe.RECOMMENDED.key()).onPress();
            }
            case 4 -> {
                screenshot("recipe-recommended-preview");
                require(SettingsRecipes.preview(draft, SettingsRecipes.Recipe.RECOMMENDED).changes().stream().anyMatch(SettingsRecipes.Change::replacesPending), "Recommended preview identifies conflicting pending edits");
                button("settings.taczweaponblueprints.ui.queue_recipe").onPress();
            }
            case 5 -> {
                require(SettingsRecipes.matching(draft.preview()).orElseThrow() == SettingsRecipes.Recipe.RECOMMENDED, "Recommended recipe produces its derived scoped match");
                require(draft.proposedChanges().equals(Map.of("enableJournal", SettingsValues.bool(false))), "Recommended preserves unrelated pending edits");
                draft.setBoolean("enableJournal", true);
                require(!draft.dirty(), "recipe navigation never creates unrelated pending changes");
                mc.setScreen(null); return true;
            }
        }
        step++; delay = 4; return false;
    }
    private static List<GuiEventListener> descendants(ContainerEventHandler parent) {
        List<GuiEventListener> result = new ArrayList<>();
        for (var child : parent.children()) { result.add(child); if (child instanceof ContainerEventHandler nested) result.addAll(descendants(nested)); }
        return result;
    }
    private static Button button(String key) {
        String label = Component.translatable(key).getString();
        return descendants(Minecraft.getInstance().screen).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new IllegalStateException("Missing " + label));
    }
    private static void screenshot(String name) {
        try { FeatureClientVerification.screenshot(Minecraft.getInstance(), name); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
