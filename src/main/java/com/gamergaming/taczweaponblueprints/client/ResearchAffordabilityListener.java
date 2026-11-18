package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

/** Advances the bounded affordability sweep in the active Research Bench view. */
public interface ResearchAffordabilityListener {
    ResearchBenchMenu getMenu();
    void refreshAuthoritativeAffordability();
    void scheduleAffordabilityRetry();
}
