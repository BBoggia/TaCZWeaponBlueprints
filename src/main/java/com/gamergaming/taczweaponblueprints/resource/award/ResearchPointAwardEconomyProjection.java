package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Deterministic upper-bound projection for configured finite Research Point
 * sources. Unlimited, cooldown, and windowed sources are reported separately
 * because they do not have a meaningful lifetime total.
 */
public final class ResearchPointAwardEconomyProjection {
    private ResearchPointAwardEconomyProjection() {
    }

    public static Projection project(
            ResearchPointAwardSnapshot snapshot,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts,
            ResourceLocation profileId) {
        ResearchPointAwardSnapshot stable = snapshot == null
                ? ResearchPointAwardSnapshot.EMPTY : snapshot;
        Map<ResourceLocation, ResearchPointAwardBlueprintFacts> stableFacts = facts == null
                ? Map.of() : Map.copyOf(facts);
        if (profileId == null) {
            return Projection.EMPTY;
        }
        EnumMap<ResearchPointAwardTrigger.Type, Integer> pointsByTrigger =
                new EnumMap<>(ResearchPointAwardTrigger.Type.class);
        int finiteDefinitions = 0;
        int renewableDefinitions = 0;
        int maximumFinitePoints = 0;
        for (ResearchPointAwardDefinition definition : stable.definitions().values()) {
            if (!definition.enabled() || !definition.appliesToProfile(profileId)) {
                continue;
            }
            if (!definition.repeat().finite()) {
                renewableDefinitions++;
                continue;
            }
            finiteDefinitions++;
            int claims = definition.repeat().type() == ResearchPointAwardRepeat.Type.ONCE
                    ? 1
                    : matchingTargetCount(definition.trigger(), stableFacts);
            int points = Math.multiplyExact(claims, definition.reward().points());
            maximumFinitePoints = Math.addExact(maximumFinitePoints, points);
            pointsByTrigger.merge(definition.trigger().type(), points, Math::addExact);
        }
        return new Projection(
                finiteDefinitions,
                renewableDefinitions,
                maximumFinitePoints,
                pointsByTrigger);
    }

    private static int matchingTargetCount(
            ResearchPointAwardTrigger trigger,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {
        ResearchPointAwardTarget target = trigger.target().orElse(null);
        if (trigger.type() == ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED
                || trigger.type() == ResearchPointAwardTrigger.Type.BLUEPRINT_LEARNED) {
            return (int) facts.values().stream()
                    .filter(value -> target == null || target.isGeneric()
                            || target.match(value) != ResearchPointAwardTarget.Specificity.NONE)
                    .count();
        }
        if (target == null || target.isGeneric()) {
            return 1;
        }
        int explicitTargets = target.ids().size() + target.namespaces().size()
                + target.tags().size() + (target.catalogSelector().isPresent() ? 1 : 0);
        return Math.max(1, explicitTargets);
    }

    public record Projection(
            int finiteDefinitionCount,
            int renewableDefinitionCount,
            int maximumFinitePoints,
            Map<ResearchPointAwardTrigger.Type, Integer> finitePointsByTrigger) {
        public static final Projection EMPTY = new Projection(0, 0, 0, Map.of());

        public Projection {
            boolean invalidEntries = finitePointsByTrigger == null
                    || finitePointsByTrigger.entrySet().stream().anyMatch(entry ->
                            entry.getKey() == null || entry.getValue() == null
                                    || entry.getValue() < 0);
            int projectedTotal = invalidEntries ? -1
                    : finitePointsByTrigger.values().stream()
                            .reduce(0, Math::addExact);
            if (finiteDefinitionCount < 0 || renewableDefinitionCount < 0
                    || maximumFinitePoints < 0 || invalidEntries
                    || projectedTotal != maximumFinitePoints) {
                throw new IllegalArgumentException("Invalid Research Point economy projection");
            }
            EnumMap<ResearchPointAwardTrigger.Type, Integer> copy =
                    new EnumMap<>(ResearchPointAwardTrigger.Type.class);
            copy.putAll(finitePointsByTrigger);
            finitePointsByTrigger = Collections.unmodifiableMap(copy);
        }
    }
}
