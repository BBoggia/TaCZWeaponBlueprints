package com.gamergaming.taczweaponblueprints.client;

/** Which Tech Tree the Research Bench opens for this player. */
public enum TechTreeViewMode implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    NEW,
    CLASSIC;
    @Override public String prefix() { return "config.taczweaponblueprints.research_tree_client.techTreeView"; }
}
