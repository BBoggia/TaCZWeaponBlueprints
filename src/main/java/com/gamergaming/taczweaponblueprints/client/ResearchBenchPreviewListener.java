package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;

/** Preview delivery shared by Research Bench views, scoped to the open menu. */
public interface ResearchBenchPreviewListener {
    ResearchBenchMenu getMenu();
    void acceptPreview(ResearchSelectionPreview preview);
}
