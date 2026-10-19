package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy;

/** An operator override of a profile's category gate, not of individual research rules. */
public enum ResearchDomainMode {
    PROFILE,
    ENABLED,
    DISABLED;

    public DomainPolicy apply(DomainPolicy profile) {
        return switch (this) {
            case PROFILE -> java.util.Objects.requireNonNull(profile);
            case ENABLED -> new DomainPolicy(true, true);
            case DISABLED -> new DomainPolicy(false, false);
        };
    }
}
