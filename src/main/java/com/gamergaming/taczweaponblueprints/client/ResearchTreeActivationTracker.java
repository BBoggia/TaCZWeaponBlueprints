package com.gamergaming.taczweaponblueprints.client;

import net.minecraft.resources.ResourceLocation;

/** Pure same-node double-click recognition for optional direct research. */
final class ResearchTreeActivationTracker {
    private final long windowMillis;
    private ResourceLocation previousNode;
    private long previousClickMillis = -1L;

    ResearchTreeActivationTracker(long windowMillis) {
        if (windowMillis <= 0L) {
            throw new IllegalArgumentException("Research Tree activation window must be positive");
        }
        this.windowMillis = windowMillis;
    }

    boolean click(ResourceLocation node, long nowMillis) {
        if (node == null || nowMillis < 0L) {
            throw new IllegalArgumentException("invalid Research Tree node click");
        }
        boolean activate = node.equals(previousNode)
                && previousClickMillis >= 0L
                && nowMillis >= previousClickMillis
                && nowMillis - previousClickMillis <= windowMillis;
        if (activate) {
            reset();
        } else {
            previousNode = node;
            previousClickMillis = nowMillis;
        }
        return activate;
    }

    void reset() {
        previousNode = null;
        previousClickMillis = -1L;
    }
}
