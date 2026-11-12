package com.gamergaming.taczweaponblueprints.client.settings;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Public Fzzy navigation hook. Local server defaults retain the native editor. */
public final class SettingsScreens {
    private SettingsScreens() {}
    public static void register() {
        ConfigApiJava.registerScreenProvider("taczweaponblueprints", (namespace, scope) -> {
            var parent = Minecraft.getInstance().screen;
            if (scope.equals(namespace)) return new SettingsLandingScreen(parent);
            if (scope.equals(namespace + ".research_tree_client")) return new PersonalSettingsScreen(parent);
            return null;
        });
    }
    public static void nativeScreen(String config) {
        var minecraft = Minecraft.getInstance();
        // Fzzy's generic open helper clears the current screen before invoking its provider.
        // Preserve the explicit landing-page parent for our own personal navigation.
        if (config.equals("research_tree_client")) minecraft.setScreen(new PersonalSettingsScreen(minecraft.screen));
        else ConfigApiJava.INSTANCE.openScreen("taczweaponblueprints." + config);
    }
}
