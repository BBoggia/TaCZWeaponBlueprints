package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy;

/** Immutable category choices shared by runtime evaluation and captured Designer projects. */
public record ResearchDomainSettings(ResearchDomainMode ammo, ResearchDomainMode attachments) {
    public static final ResearchDomainSettings PROFILE_DEFAULTS = new ResearchDomainSettings(
            ResearchDomainMode.PROFILE, ResearchDomainMode.PROFILE);

    public ResearchDomainSettings {
        java.util.Objects.requireNonNull(ammo);
        java.util.Objects.requireNonNull(attachments);
    }

    public ResearchDomainMode mode(Domain domain) {
        return switch (domain) {
            case WEAPONS -> ResearchDomainMode.PROFILE;
            case AMMO -> ammo;
            case ATTACHMENTS -> attachments;
        };
    }

    public DomainPolicy apply(Domain domain, DomainPolicy profile) {
        return mode(domain).apply(profile);
    }
}
