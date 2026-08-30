package com.gamergaming.taczweaponblueprints.client;

/** Immutable client-owned Research Tree motion and decoration preferences. */
public record ResearchTreeDisplayPolicy(
        boolean reduceMotion,
        boolean showBackgroundGrid) {
    /** Clean ordinary-player defaults; both choices remain configurable. */
    public static final ResearchTreeDisplayPolicy DEFAULT =
            new ResearchTreeDisplayPolicy(false, false);

    public boolean cameraAnimationEnabled() {
        return !reduceMotion;
    }
}
