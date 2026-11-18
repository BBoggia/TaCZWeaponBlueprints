package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Opens the Research Bench in the Tech Tree view this player chose: the lane-and-tier view unless
 * they switched to the classic one. Development probes that drive the classic screen can force it
 * with {@code -Dtacz.techTree.classic=true}.
 */
public final class TechTreeScreens {
    public static final String FORCE_CLASSIC = "tacz.techTree.classic";

    private TechTreeScreens() { }

    public static AbstractContainerScreen<ResearchBenchMenu> create(ResearchBenchMenu menu, Inventory inventory,
            Component title) {
        boolean classic = Boolean.getBoolean(FORCE_CLASSIC)
                || com.gamergaming.taczweaponblueprints.init.ModConfigs.RESEARCH_TREE_CLIENT.techTreeView.get()
                        == TechTreeViewMode.CLASSIC;
        return classic
                ? new ResearchBenchScreen(menu, inventory, title)
                : new TechTreeSheetScreen(menu, inventory, title);
    }
}
