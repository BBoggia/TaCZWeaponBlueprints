package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;

import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

/** Reversible visual-layout presets for the client-owned Research Tree. */
public enum ResearchTreeLayoutPreset implements EnumTranslatable {
    CUSTOM(null),
    COMPACT(new ResearchTreeLayoutPolicy(
            12,
            16,
            34,
            16,
            16,
            32,
            8,
            14,
            2,
            960,
            6,
            6)),
    BALANCED(ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW),
    SPACIOUS(new ResearchTreeLayoutPolicy(
            28,
            30,
            56,
            32,
            32,
            64,
            16,
            22,
            2,
            960,
            6,
            6));

    private final ResearchTreeLayoutPolicy policy;

    ResearchTreeLayoutPreset(ResearchTreeLayoutPolicy policy) {
        this.policy = policy;
    }

    public ResearchTreeLayoutPolicy resolve(ResearchTreeLayoutPolicy customPolicy) {
        if (customPolicy == null) {
            throw new IllegalArgumentException("custom Research Tree layout cannot be null");
        }
        return policy == null ? customPolicy : policy;
    }

    @Override
    public String prefix() {
        return "config.taczweaponblueprints.research_tree_client.layoutPreset";
    }
}
