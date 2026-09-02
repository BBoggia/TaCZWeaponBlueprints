package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.function.Predicate;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

/** Applies one validated connected-mode prerequisite set to a player policy. */
public final class AutomaticWeaponPrerequisiteOverlay {
    private AutomaticWeaponPrerequisiteOverlay() {
    }

    public static BlueprintResearchPolicy apply(
            BlueprintResearchPolicy policy,
            AutomaticWeaponPrerequisitePlan plan,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate) {
        return apply(policy, plan, playerData, blockedPredicate, true);
    }

    public static BlueprintResearchPolicy apply(
            BlueprintResearchPolicy policy,
            AutomaticWeaponPrerequisitePlan plan,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            boolean unlearnedPrerequisiteSelectable) {
        return apply(
                policy,
                plan,
                playerData,
                blockedPredicate,
                unlearnedPrerequisiteSelectable,
                ignored -> true);
    }

    public static BlueprintResearchPolicy apply(
            BlueprintResearchPolicy policy,
            AutomaticWeaponPrerequisitePlan plan,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            boolean unlearnedPrerequisiteSelectable,
            Predicate<ResourceLocation> knownPrerequisite) {
        return apply(
                policy,
                plan,
                playerData,
                blockedPredicate,
                unlearnedPrerequisiteSelectable,
                knownPrerequisite,
                ignored -> false,
                Map.of());
    }

    public static BlueprintResearchPolicy apply(
            BlueprintResearchPolicy policy,
            AutomaticWeaponPrerequisitePlan plan,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            boolean unlearnedPrerequisiteSelectable,
            Predicate<ResourceLocation> knownPrerequisite,
            Predicate<ResourceLocation> accessibleWithoutLearning) {
        return apply(
                policy,
                plan,
                playerData,
                blockedPredicate,
                unlearnedPrerequisiteSelectable,
                knownPrerequisite,
                accessibleWithoutLearning,
                Map.of());
    }

    public static BlueprintResearchPolicy apply(
            BlueprintResearchPolicy policy,
            AutomaticWeaponPrerequisitePlan plan,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            boolean unlearnedPrerequisiteSelectable,
            Predicate<ResourceLocation> knownPrerequisite,
            Predicate<ResourceLocation> accessibleWithoutLearning,
            Map<ResourceLocation, ResourceLocation> prerequisiteReplacements) {
        return apply(
                policy,
                plan,
                playerData,
                blockedPredicate,
                unlearnedPrerequisiteSelectable,
                knownPrerequisite,
                accessibleWithoutLearning,
                prerequisiteReplacements,
                false);
    }

    public static BlueprintResearchPolicy apply(
            BlueprintResearchPolicy policy,
            AutomaticWeaponPrerequisitePlan plan,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            boolean unlearnedPrerequisiteSelectable,
            Predicate<ResourceLocation> knownPrerequisite,
            Predicate<ResourceLocation> accessibleWithoutLearning,
            Map<ResourceLocation, ResourceLocation> prerequisiteReplacements,
            boolean replaceAuthoredRequirements) {
        if (policy == null) {
            throw new IllegalArgumentException(
                    "automatic prerequisite policy cannot be null");
        }
        if (plan == null || !plan.profileId().equals(policy.profileId())) {
            return replaceAuthoredRequirements
                    ? policy.withRequirements(ResearchRequirements.EMPTY, true)
                    : policy;
        }
        ResearchRequirements requirements = plan.requirementsFor(policy.blueprintId());
        if (!replaceAuthoredRequirements
                && (!policy.automaticPrerequisitesAllowed()
                        || requirements.allOf().isEmpty()
                        || !policy.requirements().allOf().isEmpty())) {
            return policy;
        }
        Predicate<String> blocked = blockedPredicate == null
                ? ignored -> false
                : blockedPredicate;
        Predicate<ResourceLocation> known = knownPrerequisite == null
                ? ignored -> false
                : knownPrerequisite;
        Predicate<ResourceLocation> accessible = accessibleWithoutLearning == null
                ? ignored -> false
                : accessibleWithoutLearning;
        Map<ResourceLocation, ResourceLocation> replacements;
        try {
            replacements = prerequisiteReplacements == null
                    ? Map.of()
                    : Map.copyOf(prerequisiteReplacements);
        } catch (NullPointerException exception) {
            throw new IllegalArgumentException(
                    "automatic prerequisite replacements cannot contain nulls",
                    exception);
        }
        BlueprintResearchPolicy result = replaceAuthoredRequirements
                ? policy.withRequirements(ResearchRequirements.EMPTY, true)
                : policy;
        for (ResearchPrerequisiteGroup group : requirements.allOf()) {
            List<ResourceLocation> rebased = group.anyOf().stream()
                    .map(prerequisite -> replacements.getOrDefault(
                            prerequisite, prerequisite))
                    .filter(prerequisite -> !prerequisite.equals(policy.blueprintId()))
                    .distinct()
                    .toList();
            if (rebased.stream().anyMatch(accessible)) {
                continue;
            }
            List<ResourceLocation> safe = rebased.stream()
                    .filter(known)
                    .filter(prerequisite -> !blocked.test(prerequisite.toString()))
                    .filter(prerequisite -> unlearnedPrerequisiteSelectable
                            || playerData != null
                                    && playerData.hasBlueprint(prerequisite.toString()))
                    .toList();
            // Generated groups fail open independently. Removing one unsafe
            // alternative must not discard another safe route in the same group.
            if (safe.isEmpty()) {
                continue;
            }
            ResearchPrerequisiteGroup filtered = new ResearchPrerequisiteGroup(safe);
            boolean satisfied = playerData != null && filtered.anyOf().stream()
                    .anyMatch(prerequisite ->
                            playerData.hasBlueprint(prerequisite.toString())
                                    || accessible.test(prerequisite));
            result = result.withAdditionalRequirementGroup(filtered, satisfied);
        }
        return result;
    }
}
