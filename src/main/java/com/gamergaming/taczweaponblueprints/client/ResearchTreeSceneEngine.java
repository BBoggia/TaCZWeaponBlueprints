package com.gamergaming.taczweaponblueprints.client;

/** A synchronous, bounded scene candidate producer. Validation is owned by the caller. */
public interface ResearchTreeSceneEngine {
    String revision();
    default boolean requiresGroupedRouting() { return false; }
    ResearchTreeScene layout(ResearchTreeSceneInput input);
}
