package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchPreview;

import net.minecraft.resources.ResourceLocation;

/** Disclosure and authority gate for exact fullscreen Research Tree card content. */
public final class ResearchTreeContextCardPolicy {
    private ResearchTreeContextCardPolicy() {
    }

    /**
     * Exact costs and the Research action are safe only when all three client-visible
     * identities agree. The server still revalidates and performs the transaction.
     */
    public static boolean hasMatchingAuthoritativePreview(
            ResourceLocation pinnedNodeId,
            Optional<ResourceLocation> authoritativeSelection,
            ResearchBenchPreview preview) {
        if (authoritativeSelection == null || preview == null) {
            throw new IllegalArgumentException("invalid Research Tree context card authority state");
        }
        return pinnedNodeId != null
                && authoritativeSelection.filter(pinnedNodeId::equals).isPresent()
                && preview.blueprintId().filter(pinnedNodeId::equals).isPresent();
    }
}
