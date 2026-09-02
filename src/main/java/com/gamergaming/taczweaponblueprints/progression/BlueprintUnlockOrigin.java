package com.gamergaming.taczweaponblueprints.progression;

/**
 * Trusted server-side source of one blueprint-learning request.
 *
 * <p>The origin controls policy and award behavior. It must never be accepted
 * directly from an untrusted client payload.</p>
 */
public enum BlueprintUnlockOrigin {
    TREE_RESEARCH(true, true),
    PHYSICAL_BLUEPRINT(true, true),
    STARTING_GRANT(false, false),
    ADMINISTRATOR(false, true),
    MIGRATION(false, false);

    private final boolean liveAwardsEligible;
    private final boolean recentHistoryEligible;

    BlueprintUnlockOrigin(boolean liveAwardsEligible, boolean recentHistoryEligible) {
        this.liveAwardsEligible = liveAwardsEligible;
        this.recentHistoryEligible = recentHistoryEligible;
    }

    /**
     * Whether a successful state transition from this origin may dispatch live
     * discovery and learning awards. Actual awards still require a new state
     * transition and the normal award-service checks.
     */
    public boolean liveAwardsEligible() {
        return liveAwardsEligible;
    }

    /** Whether successful learning from this trusted origin appears in Recent. */
    public boolean recentHistoryEligible() {
        return recentHistoryEligible;
    }
}
