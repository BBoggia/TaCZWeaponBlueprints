package com.gamergaming.taczweaponblueprints.progression.workbench;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.server.level.ServerPlayer;

/**
 * Retained for the legacy packet discriminator after research and crafting
 * became separate physical workstations.
 */
public final class ResearchWorkbenchMenuTransitions {
    private ResearchWorkbenchMenuTransitions() {
    }

    public static boolean toCrafting(ServerPlayer player, ResearchBenchMenu menu) {
        return false;
    }

    public static boolean toResearch(ServerPlayer player, GunSmithTableMenu menu) {
        return false;
    }
}
