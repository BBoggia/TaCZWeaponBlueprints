package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Semantic detail content; only an explicitly disclosed target can be activated. */
record ResearchTreeInspectorRow(Kind kind, Component text, int color, Optional<ResourceLocation> target) {
    enum Kind { EXPLANATION, MATERIAL, GROUP, PREREQUISITE, EXTERNAL_LINK }
    ResearchTreeInspectorRow {
        if (kind == null || text == null || target == null
                || target.isPresent() && kind != Kind.PREREQUISITE && kind != Kind.EXTERNAL_LINK)
            throw new IllegalArgumentException("invalid inspector row");
    }
    static ResearchTreeInspectorRow text(Kind kind, Component text, int color) {
        return new ResearchTreeInspectorRow(kind, text, color, Optional.empty());
    }
}
