package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.function.Predicate;
import java.util.List;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

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
                ignored -> false);
    }

    public static BlueprintResearchPolicy apply(
            BlueprintResearchPolicy policy,
            AutomaticWeaponPrerequisitePlan plan,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            boolean unlearnedPrerequisiteSelectable,
            Predicate<ResourceLocation> knownPrerequisite,
            Predicate<ResourceLocation> accessibleWithoutLearning) {
        if (policy == null) {
            throw new IllegalArgumentException(
                    "automatic prerequisite policy cannot be null");
        }
        if (plan == null || !plan.profileId().equals(policy.profileId())) {
            return policy;
        }
        List<ResourceLocation> prerequisites = plan.prerequisitesFor(policy.blueprintId());
        if (prerequisites.isEmpty() || !policy.prerequisites().isEmpty()) {
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
        List<ResourceLocation> safe = prerequisites.stream()
                .filter(known)
                .filter(prerequisite -> !blocked.test(prerequisite.toString()))
                .filter(prerequisite -> !accessible.test(prerequisite))
                .filter(prerequisite -> unlearnedPrerequisiteSelectable
                        || playerData != null
                                && playerData.hasBlueprint(prerequisite.toString()))
                .toList();
        // Fail open when every proposed anchor is blocked or hidden by the active
        // selection ceiling. A partial safe set remains useful and reachable.
        if (safe.isEmpty()) {
            return policy;
        }
        BlueprintResearchPolicy result = policy;
        for (ResourceLocation prerequisite : safe) {
            boolean satisfied = playerData != null
                    && (playerData.hasBlueprint(prerequisite.toString())
                            || accessible.test(prerequisite));
            result = result.withAdditionalPrerequisite(prerequisite, satisfied);
        }
        return result;
    }
}
