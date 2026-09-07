package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.resource.research.*;

/** Published independently of both currency and gameplay currency-award enablement. */
public record ResearchDataSettings(ResearchDataPolicy.Behavior behavior, ResearchDataPoolResolver.Defaults defaults) {
    public ResearchDataPolicy.Behavior behaviorFor(java.util.Optional<ResearchDataPolicy> owner) {
        if (behavior != ResearchDataPolicy.Behavior.ANALYSIS) return behavior;
        return owner.filter(ResearchDataPolicy::enabled).map(ResearchDataPolicy::behavior).orElse(ResearchDataPolicy.Behavior.DISABLED);
    }
    public ResearchDataSettings { java.util.Objects.requireNonNull(behavior); java.util.Objects.requireNonNull(defaults); }
}
