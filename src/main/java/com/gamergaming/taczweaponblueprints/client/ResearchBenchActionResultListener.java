package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

/** A Research Bench screen that wants the server's answer to its select and research requests. */
public interface ResearchBenchActionResultListener {
    void acceptActionResult(int requestId, ResearchBenchMenu.ActionResult result);
}
