package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

/** Selects which authored Research Tree cost channels are active at runtime. */
public enum ResearchCostMode implements EnumTranslatable {
    POINTS_AND_ITEMS(true, true),
    POINTS_ONLY(true, false),
    ITEMS_ONLY(false, true);

    private final boolean pointsEnabled;
    private final boolean itemsEnabled;

    ResearchCostMode(boolean pointsEnabled, boolean itemsEnabled) {
        this.pointsEnabled = pointsEnabled;
        this.itemsEnabled = itemsEnabled;
    }

    public boolean pointsEnabled() {
        return pointsEnabled;
    }

    public boolean itemsEnabled() {
        return itemsEnabled;
    }

    /**
     * Produces a non-destructive effective cost. The datapack-authored cost is
     * retained by the source policy and returns if the mode changes again.
     */
    public BlueprintResearchCost apply(BlueprintResearchCost authored) {
        if (authored == null) {
            throw new IllegalArgumentException("authored research cost cannot be null");
        }
        return new BlueprintResearchCost(
                pointsEnabled ? authored.points() : 0,
                itemsEnabled ? authored.ingredients() : java.util.List.of());
    }

    @Override
    public String prefix() {
        return "config.taczweaponblueprints.blueprint.researchCostMode";
    }
}
