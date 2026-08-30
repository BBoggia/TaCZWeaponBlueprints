package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

/** Complete immutable and indexed award datapack publication. */
public record ResearchPointAwardSnapshot(
        Map<ResourceLocation, ResearchPointAwardDefinition> definitions,
        Map<ResearchPointAwardTrigger.Type, List<Binding>> bindingsByTrigger,
        Map<TargetIndexKey, List<Binding>> exactBindings,
        Map<TargetIndexKey, List<Binding>> tagBindings,
        Map<NamespaceIndexKey, List<Binding>> namespaceBindings,
        Map<ResearchPointAwardTrigger.Type, List<Binding>> selectorBindings,
        Map<ResearchPointAwardTrigger.Type, List<Binding>> genericBindings,
        Map<ResourceLocation, ResearchPointAwardBudget> budgets) {
    public static final ResearchPointAwardSnapshot EMPTY = new ResearchPointAwardSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public ResearchPointAwardSnapshot {
        definitions = immutableMap(definitions);
        bindingsByTrigger = immutableEnumLists(bindingsByTrigger);
        exactBindings = immutableLists(exactBindings);
        tagBindings = immutableLists(tagBindings);
        namespaceBindings = immutableLists(namespaceBindings);
        selectorBindings = immutableEnumLists(selectorBindings);
        genericBindings = immutableEnumLists(genericBindings);
        budgets = immutableMap(budgets);
        if (definitions.size() > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_DEFINITIONS) {
            throw new IllegalArgumentException("too many Research Point award definitions");
        }
        if (bindingsByTrigger.getOrDefault(
                ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE, List.of()).size()
                > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_MILESTONE_DEFINITIONS) {
            throw new IllegalArgumentException("too many enabled blueprint milestone award definitions");
        }
    }

    public static ResearchPointAwardSnapshot create(
            Map<ResourceLocation, ResearchPointAwardDefinition> definitions) {
        Map<ResourceLocation, ResearchPointAwardDefinition> sorted = sortedDefinitions(definitions);
        if (sorted.size() > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_DEFINITIONS) {
            throw new IllegalArgumentException("Research Point award snapshot cannot contain more than "
                    + PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_DEFINITIONS + " definitions");
        }

        Map<ResearchPointAwardTrigger.Type, List<Binding>> byTrigger =
                new EnumMap<>(ResearchPointAwardTrigger.Type.class);
        Map<TargetIndexKey, List<Binding>> exact = new LinkedHashMap<>();
        Map<TargetIndexKey, List<Binding>> tags = new LinkedHashMap<>();
        Map<NamespaceIndexKey, List<Binding>> namespaces = new LinkedHashMap<>();
        Map<ResearchPointAwardTrigger.Type, List<Binding>> selectors =
                new EnumMap<>(ResearchPointAwardTrigger.Type.class);
        Map<ResearchPointAwardTrigger.Type, List<Binding>> generic =
                new EnumMap<>(ResearchPointAwardTrigger.Type.class);
        Map<ResourceLocation, ResearchPointAwardBudget> budgets = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, ResearchPointAwardDefinition> entry : sorted.entrySet()) {
            entry.getValue().validateForSnapshot();
            Binding binding = new Binding(entry.getKey(), entry.getValue());
            entry.getValue().budget().ifPresent(budget -> {
                ResearchPointAwardBudget previous = budgets.putIfAbsent(budget.id(), budget);
                if (previous != null && !previous.equals(budget)) {
                    throw new IllegalArgumentException(
                            "conflicting Research Point shared budget " + budget.id());
                }
            });
            if (!entry.getValue().enabled()) {
                continue;
            }

            ResearchPointAwardTrigger.Type type = entry.getValue().trigger().type();
            byTrigger.computeIfAbsent(type, ignored -> new ArrayList<>()).add(binding);
            ResearchPointAwardTarget target = entry.getValue().trigger().target().orElse(null);
            if (target == null || target.isGeneric()) {
                generic.computeIfAbsent(type, ignored -> new ArrayList<>()).add(binding);
                continue;
            }
            target.ids().forEach(id -> exact
                    .computeIfAbsent(new TargetIndexKey(type, id), ignored -> new ArrayList<>())
                    .add(binding));
            target.tags().forEach(id -> tags
                    .computeIfAbsent(new TargetIndexKey(type, id), ignored -> new ArrayList<>())
                    .add(binding));
            target.namespaces().forEach(namespace -> namespaces
                    .computeIfAbsent(new NamespaceIndexKey(type, namespace), ignored -> new ArrayList<>())
                    .add(binding));
            if (target.catalogSelector().isPresent()) {
                selectors.computeIfAbsent(type, ignored -> new ArrayList<>()).add(binding);
            }
        }

        int milestoneDefinitions = byTrigger.getOrDefault(
                ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE, List.of()).size();
        if (milestoneDefinitions
                > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_MILESTONE_DEFINITIONS) {
            throw new IllegalArgumentException("Research Point award snapshot cannot contain more than "
                    + PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_MILESTONE_DEFINITIONS
                    + " enabled blueprint milestone definitions");
        }

        return new ResearchPointAwardSnapshot(
                sorted, byTrigger, exact, tags, namespaces, selectors, generic, budgets);
    }

    /** Indexed candidate retrieval; no trigger scans all definitions. */
    public List<Binding> candidatesFor(ResearchPointAwardContext context) {
        if (context == null) {
            return List.of();
        }
        ResearchPointAwardTrigger.Type type = context.triggerType();
        Map<ResourceLocation, Binding> candidates = new LinkedHashMap<>();
        context.targetId().ifPresent(targetId -> {
            add(candidates, exactBindings.get(new TargetIndexKey(type, targetId)));
            add(candidates, namespaceBindings.get(
                    new NamespaceIndexKey(type, targetId.getNamespace())));
        });
        context.targetTags().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(tag -> add(candidates, tagBindings.get(new TargetIndexKey(type, tag))));
        add(candidates, selectorBindings.get(type));
        add(candidates, genericBindings.get(type));
        return List.copyOf(candidates.values());
    }

    public int enabledDefinitionCount() {
        return bindingsByTrigger.values().stream().mapToInt(List::size).sum();
    }

    private static void add(Map<ResourceLocation, Binding> destination, List<Binding> bindings) {
        if (bindings != null) {
            bindings.forEach(binding -> destination.putIfAbsent(binding.definitionId(), binding));
        }
    }

    private static Map<ResourceLocation, ResearchPointAwardDefinition> sortedDefinitions(
            Map<ResourceLocation, ResearchPointAwardDefinition> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, ResearchPointAwardDefinition> sorted = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IllegalArgumentException("award definitions cannot contain null entries");
                    }
                    sorted.put(entry.getKey(), entry.getValue());
                });
        return Collections.unmodifiableMap(sorted);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return values == null || values.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static <K> Map<K, List<Binding>> immutableLists(Map<K, List<Binding>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<K, List<Binding>> copy = new LinkedHashMap<>();
        values.forEach((key, bindings) -> copy.put(key, List.copyOf(bindings)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResearchPointAwardTrigger.Type, List<Binding>> immutableEnumLists(
            Map<ResearchPointAwardTrigger.Type, List<Binding>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResearchPointAwardTrigger.Type, List<Binding>> copy =
                new EnumMap<>(ResearchPointAwardTrigger.Type.class);
        values.forEach((key, bindings) -> copy.put(key, List.copyOf(bindings)));
        return Collections.unmodifiableMap(copy);
    }

    public record Binding(
            ResourceLocation definitionId,
            ResearchPointAwardDefinition definition) {
        public Binding {
            if (definitionId == null || definition == null
                    || definitionId.toString().length()
                    > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                throw new IllegalArgumentException("invalid Research Point award binding");
            }
        }
    }

    public record TargetIndexKey(ResearchPointAwardTrigger.Type triggerType, ResourceLocation targetId) {
        public TargetIndexKey {
            if (triggerType == null || targetId == null) {
                throw new IllegalArgumentException("invalid award target index key");
            }
        }
    }

    public record NamespaceIndexKey(ResearchPointAwardTrigger.Type triggerType, String namespace) {
        public NamespaceIndexKey {
            if (triggerType == null || namespace == null || namespace.isBlank()) {
                throw new IllegalArgumentException("invalid award namespace index key");
            }
        }
    }
}
