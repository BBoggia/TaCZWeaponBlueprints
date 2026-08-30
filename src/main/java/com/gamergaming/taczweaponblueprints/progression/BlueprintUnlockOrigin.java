package com.gamergaming.taczweaponblueprints.progression;

/**
 * Trusted server-side source of one blueprint-learning request.
 *
 * <p>The origin controls policy and award behavior. It must never be accepted
 * directly from an untrusted client payload.</p>
 */
public enum BlueprintUnlockOrigin {
    TREE_RESEARCH(true),
    PHYSICAL_BLUEPRINT(true),
    STARTING_GRANT(false),
    ADMINISTRATOR(false),
    MIGRATION(false);

    private final boolean liveAwardsEligible;

    BlueprintUnlockOrigin(boolean liveAwardsEligible) {
        this.liveAwardsEligible = liveAwardsEligible;
    }

    /**
     * Whether a successful state transition from this origin may dispatch live
     * discovery and learning awards. Actual awards still require a new state
     * transition and the normal award-service checks.
     */
    public boolean liveAwardsEligible() {
        return liveAwardsEligible;
    }
}
