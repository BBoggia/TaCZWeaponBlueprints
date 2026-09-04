package com.gamergaming.taczweaponblueprints.progression.gate;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;

/** Selects which workstation action a Progression Gate restricts. */
public enum ProgressionGateScope {
    RESEARCH,
    CRAFTING,
    BOTH;

    public boolean appliesTo(ResearchInteractionMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("research interaction mode cannot be null");
        }
        return this == BOTH
                || this == RESEARCH && mode == ResearchInteractionMode.RESEARCH
                || this == CRAFTING && mode == ResearchInteractionMode.CRAFTING;
    }
}
