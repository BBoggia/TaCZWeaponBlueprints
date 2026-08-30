package com.gamergaming.taczweaponblueprints.progression;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

/** Server-wide result of a successful Research Tree transaction. */
public enum TreeResearchResultMode implements EnumTranslatable {
    DIRECT_LEARN(true, false),
    CREATE_BLUEPRINT(false, true);

    private final boolean learnsDirectly;
    private final boolean createsPhysicalBlueprint;

    TreeResearchResultMode(
            boolean learnsDirectly,
            boolean createsPhysicalBlueprint) {
        this.learnsDirectly = learnsDirectly;
        this.createsPhysicalBlueprint = createsPhysicalBlueprint;
    }

    public boolean learnsDirectly() {
        return learnsDirectly;
    }

    public boolean createsPhysicalBlueprint() {
        return createsPhysicalBlueprint;
    }

    @Override
    public String prefix() {
        return "config.taczweaponblueprints.blueprint.treeResearchResultMode";
    }
}
