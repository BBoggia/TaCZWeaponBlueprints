package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchPolicyDefinition(
        boolean journalEnabled,
        JournalVisibility visibility,
        boolean researchEnabled,
        boolean recyclingEnabled,
        boolean allowUnlearnedRecycling,
        int recyclingValue,
        BlueprintResearchCost researchCost,
        boolean requiresDiscovery,
        List<ResourceLocation> prerequisites,
        boolean creativeBypassesCost,
        Optional<ResourceLocation> ruleId,
        BlueprintResearchTarget.MatchSpecificity specificity,
        boolean visibilityRestricted) {

    public BlueprintResearchPolicyDefinition {
        if (visibility == null || researchCost == null || specificity == null) {
            throw new IllegalArgumentException("policy visibility, cost, and specificity cannot be null");
        }
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        ruleId = ruleId == null ? Optional.empty() : ruleId;
        if (recyclingValue < 0 || recyclingValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("policy recycling value is outside the supported range");
        }
        validateEconomy(researchEnabled, recyclingValue, researchCost);
    }

    public static BlueprintResearchPolicyDefinition fromProfile(BlueprintResearchProfile profile) {
        return new BlueprintResearchPolicyDefinition(
                profile.journalEnabled(),
                profile.visibility(),
                profile.researchEnabled(),
                profile.recyclingEnabled(),
                profile.allowUnlearnedRecycling(),
                profile.recyclingValue(),
                profile.researchCost(),
                profile.requiresDiscovery(),
                List.of(),
                profile.creativeBypassesCost(),
                Optional.empty(),
                BlueprintResearchTarget.MatchSpecificity.NONE,
                false);
    }

    public BlueprintResearchPolicyDefinition apply(
            ResourceLocation selectedRuleId,
            BlueprintResearchRule rule,
            BlueprintResearchTarget.MatchSpecificity selectedSpecificity) {
        return new BlueprintResearchPolicyDefinition(
                journalEnabled,
                rule.visibility().orElse(visibility),
                rule.researchEnabled().orElse(researchEnabled),
                rule.recyclingEnabled().orElse(recyclingEnabled),
                rule.allowUnlearnedRecycling().orElse(allowUnlearnedRecycling),
                rule.recyclingValue().orElse(recyclingValue),
                rule.researchCost().orElse(researchCost),
                rule.requiresDiscovery().orElse(requiresDiscovery),
                rule.prerequisites().orElse(prerequisites),
                rule.creativeBypassesCost().orElse(creativeBypassesCost),
                Optional.of(selectedRuleId),
                selectedSpecificity,
                rule.visibility()
                        .map(value -> !value.revealsIdentity())
                        .orElse(false));
    }

    private static void validateEconomy(
            boolean researchEnabled,
            int recyclingValue,
            BlueprintResearchCost cost) {
        if (researchEnabled && cost.points() <= recyclingValue) {
            throw new IllegalArgumentException(
                    "research point cost must exceed the recycling value while research is enabled");
        }
    }
}
