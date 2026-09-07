package com.gamergaming.taczweaponblueprints.client;

/** Personal visibility policy for the fullscreen Tech Tree minimap. */
public enum ResearchTreeMinimapMode implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
    AUTOMATIC,
    ALWAYS,
    HIDDEN;
    @Override public String prefix() { return "config.taczweaponblueprints.research_tree_client.minimap"; }
}
