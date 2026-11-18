package com.gamergaming.taczweaponblueprints.client;

/** How the new Tech Tree groups weapons into lanes for this player. */
public enum TechTreeLaneMode implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    BRANCHES,
    TYPES;
    @Override public String prefix() { return "config.taczweaponblueprints.research_tree_client.techTreeLanes"; }
}
