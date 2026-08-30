package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchPolicyDefinition(
        boolean journalEnabled,
        boolean treeEnabled,
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
        boolean visibilityRestricted,
        BlueprintReverseEngineeringPolicy reverseEngineering) {

    public BlueprintResearchPolicyDefinition {
        if (visibility == null || researchCost == null || specificity == null
                || reverseEngineering == null) {
            throw new IllegalArgumentException(
                    "policy visibility, costs, specificity, and reverse-engineering policy cannot be null");
        }
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        ruleId = ruleId == null ? Optional.empty() : ruleId;
        if (recyclingValue < 0 || recyclingValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("policy recycling value is outside the supported range");
        }
        validateEconomy(researchEnabled, recyclingValue, researchCost);
        validateReverseEngineeringEconomy(
                recyclingEnabled,
                recyclingValue,
                reverseEngineering);
    }

    public static BlueprintResearchPolicyDefinition fromProfile(BlueprintResearchProfile profile) {
        return new BlueprintResearchPolicyDefinition(
                profile.journalEnabled(),
                profile.treeEnabled(),
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
                false,
                profile.reverseEngineering());
    }

    public BlueprintResearchPolicyDefinition apply(
            ResourceLocation selectedRuleId,
            BlueprintResearchRule rule,
            BlueprintResearchTarget.MatchSpecificity selectedSpecificity) {
        return new BlueprintResearchPolicyDefinition(
                journalEnabled,
                rule.treeEnabled().orElse(treeEnabled),
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
                        .orElse(false),
                rule.reverseEngineering()
                        .map(reverseEngineering::apply)
                        .orElse(reverseEngineering));
    }

    public BlueprintResearchPolicyDefinition withPrerequisites(List<ResourceLocation> effectivePrerequisites) {
        return new BlueprintResearchPolicyDefinition(
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                effectivePrerequisites,
                creativeBypassesCost,
                ruleId,
                specificity,
                visibilityRestricted,
                reverseEngineering);
    }

    /** Applies the profile's non-overridable final gate for this blueprint domain. */
    public BlueprintResearchPolicyDefinition applyDomainPolicy(
            BlueprintResearchProfile.DomainPolicy domainPolicy) {
        if (domainPolicy == null) {
            throw new IllegalArgumentException("research domain policy cannot be null");
        }
        return new BlueprintResearchPolicyDefinition(
                journalEnabled,
                treeEnabled && domainPolicy.treeEnabled(),
                visibility,
                researchEnabled && domainPolicy.researchEnabled(),
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                prerequisites,
                creativeBypassesCost,
                ruleId,
                specificity,
                visibilityRestricted,
                reverseEngineering);
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

    private static void validateReverseEngineeringEconomy(
            boolean recyclingEnabled,
            int recyclingValue,
            BlueprintReverseEngineeringPolicy policy) {
        boolean directLoop = policy.enabled()
                && policy.allowKnown()
                && policy.outputRecyclable()
                && recyclingEnabled
                && recyclingValue > 0;
        if (directLoop && !policy.expertAllowEconomyLoop()) {
            throw new IllegalArgumentException(
                    "reverse engineering known items into recyclable blueprints creates a direct RP loop; "
                            + "disable known-item reverse engineering, disable recyclable output, or set "
                            + "expert_allow_economy_loop explicitly");
        }
    }
}
