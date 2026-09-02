package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/** Deterministic UI-neutral operator diagnostics for award data. */
public final class ResearchPointAwardDiagnostics {
    private ResearchPointAwardDiagnostics() {
    }

    public static Summary summarize(ResearchPointAwardSnapshot snapshot) {
        ResearchPointAwardSnapshot stable = snapshot == null
                ? ResearchPointAwardSnapshot.EMPTY
                : snapshot;
        Set<ResourceLocation> groups = new LinkedHashSet<>();
        Map<ResearchPointAwardTrigger.Type, Integer> triggers =
                new EnumMap<>(ResearchPointAwardTrigger.Type.class);
        int targetBindings = 0;
        for (ResearchPointAwardDefinition definition : stable.definitions().values()) {
            groups.add(definition.awardGroup());
            if (definition.enabled()) {
                triggers.merge(definition.trigger().type(), 1, Integer::sum);
                ResearchPointAwardTarget target = definition.trigger().target().orElse(null);
                targetBindings += target == null || target.isGeneric()
                        ? 1
                        : target.indexBindingCount();
            }
        }
        return new Summary(
                stable.definitions().size(),
                stable.enabledDefinitionCount(),
                groups.size(),
                stable.budgets().size(),
                targetBindings,
                triggers);
    }

    public static Optional<Inspection> inspect(
            ResearchPointAwardSnapshot snapshot,
            ResourceLocation definitionId) {
        if (snapshot == null || definitionId == null) {
            return Optional.empty();
        }
        ResearchPointAwardDefinition definition = snapshot.definitions().get(definitionId);
        return definition == null
                ? Optional.empty()
                : Optional.of(new Inspection(
                        definitionId,
                        definition.enabled(),
                        definition.trigger().type(),
                        definition.awardGroup(),
                        definition.priority(),
                        definition.reward().points(),
                        definition.reward().overflow(),
                        definition.repeat().type(),
                        definition.profiles(),
                        definition.budget().map(ResearchPointAwardBudget::id),
                        definition.trigger().target()
                                .map(ResearchPointAwardTarget::termCount).orElse(0)));
    }

    /** Exact integration event IDs that command functions can invoke directly. */
    public static List<ResourceLocation> integrationSourceIds(
            ResearchPointAwardSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        return snapshot.definitions().values().stream()
                .filter(ResearchPointAwardDefinition::enabled)
                .filter(definition -> definition.trigger().type()
                        == ResearchPointAwardTrigger.Type.INTEGRATION)
                .flatMap(definition -> definition.trigger().target().stream())
                .flatMap(target -> target.ids().stream())
                .distinct()
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    public record Summary(
            int definitionCount,
            int enabledDefinitionCount,
            int awardGroupCount,
            int budgetCount,
            int targetBindingCount,
            Map<ResearchPointAwardTrigger.Type, Integer> triggerCounts) {
        public Summary {
            if (definitionCount < 0 || enabledDefinitionCount < 0
                    || enabledDefinitionCount > definitionCount || awardGroupCount < 0
                    || budgetCount < 0 || targetBindingCount < 0 || triggerCounts == null) {
                throw new IllegalArgumentException("invalid Research Point award summary");
            }
            Map<ResearchPointAwardTrigger.Type, Integer> copy =
                    new EnumMap<>(ResearchPointAwardTrigger.Type.class);
            copy.putAll(triggerCounts);
            triggerCounts = Collections.unmodifiableMap(copy);
        }
    }

    public record Inspection(
            ResourceLocation definitionId,
            boolean enabled,
            ResearchPointAwardTrigger.Type triggerType,
            ResourceLocation awardGroup,
            int priority,
            int points,
            ResearchPointAwardReward.Overflow overflow,
            ResearchPointAwardRepeat.Type repeat,
            java.util.List<ResourceLocation> profiles,
            Optional<ResourceLocation> budgetId,
            int targetTerms) {
        public Inspection {
            if (definitionId == null || triggerType == null || awardGroup == null || points <= 0
                    || overflow == null || repeat == null || profiles == null || budgetId == null
                    || targetTerms < 0) {
                throw new IllegalArgumentException("invalid Research Point award inspection");
            }
            profiles = java.util.List.copyOf(profiles);
        }
    }
}
