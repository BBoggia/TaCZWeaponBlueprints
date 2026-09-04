package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateGroup;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;

/** Shared pure helpers for the staged crafting-policy resolvers. */
final class BlueprintCraftingPolicyResolutionSupport {
    private BlueprintCraftingPolicyResolutionSupport() {
    }

    static ProgressionGateRequirements craftingGates(
            ProgressionGateRequirements requirements) {
        return gatesFor(requirements, ResearchInteractionMode.CRAFTING);
    }

    static ProgressionGateRequirements researchGates(
            ProgressionGateRequirements requirements) {
        return gatesFor(requirements, ResearchInteractionMode.RESEARCH);
    }

    static ProgressionGateRequirements researchGatesOrElse(
            Optional<ProgressionGateRequirements> override,
            ProgressionGateRequirements fallback) {
        return gatesOrElse(override, fallback, ResearchInteractionMode.RESEARCH);
    }

    static ProgressionGateRequirements craftingGatesOrElse(
            Optional<ProgressionGateRequirements> override,
            ProgressionGateRequirements fallback) {
        return gatesOrElse(override, fallback, ResearchInteractionMode.CRAFTING);
    }

    private static ProgressionGateRequirements gatesOrElse(
            Optional<ProgressionGateRequirements> override,
            ProgressionGateRequirements fallback,
            ResearchInteractionMode mode) {
        if (override == null || fallback == null) {
            throw new IllegalArgumentException("progression gate selection cannot be null");
        }
        if (override.isEmpty()) {
            return gatesFor(fallback, mode);
        }
        ProgressionGateRequirements applicable = gatesFor(override.orElseThrow(), mode);
        return applicable.conditionCount() > 0
                ? applicable
                : gatesFor(fallback, mode);
    }

    private static ProgressionGateRequirements gatesFor(
            ProgressionGateRequirements requirements,
            ResearchInteractionMode mode) {
        if (requirements == null || requirements.allOf().isEmpty()) {
            return ProgressionGateRequirements.EMPTY;
        }
        List<ProgressionGateGroup> groups = requirements.allOf().stream()
                .map(group -> group.anyOf().stream()
                        .filter(condition -> condition.scope().appliesTo(mode))
                        .toList())
                .filter(alternatives -> !alternatives.isEmpty())
                .map(ProgressionGateGroup::new)
                .toList();
        return groups.isEmpty()
                ? ProgressionGateRequirements.EMPTY
                : new ProgressionGateRequirements(groups);
    }
}
