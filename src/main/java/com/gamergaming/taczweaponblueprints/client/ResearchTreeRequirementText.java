package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Disclosure-safe selected-node copy for canonical AND-of-OR requirements. */
public final class ResearchTreeRequirementText {
    static final int MAX_DETAILED_GROUPS = 4;
    static final int MAX_VISIBLE_ALTERNATIVES_PER_GROUP = 3;

    private ResearchTreeRequirementText() {
    }

    public static Optional<Component> summary(
            ResearchTreeGraph graph,
            ResourceLocation dependentId) {
        List<ResearchTreeGraph.RequirementGroup> groups = groups(graph, dependentId);
        if (groups.isEmpty() || groups.stream().noneMatch(
                ResearchTreeRequirementText::requiresExplanation)) {
            return Optional.empty();
        }
        int visible = groups.stream()
                .mapToInt(group -> group.visibleAlternativeIds().size()).sum();
        int hidden = groups.stream()
                .mapToInt(ResearchTreeGraph.RequirementGroup::hiddenAlternativeCount).sum();
        int external = groups.stream()
                .mapToInt(ResearchTreeGraph.RequirementGroup::externalAlternativeCount).sum();
        int satisfied = (int) groups.stream()
                .filter(ResearchTreeGraph.RequirementGroup::satisfied).count();
        boolean satisfactionDisclosed = groups.stream().allMatch(
                ResearchTreeGraph.RequirementGroup::satisfactionDisclosed);
        boolean hasAlternativeGroup = groups.stream().anyMatch(
                ResearchTreeRequirementText::isAlternativeGroup);
        if (groups.size() == 1 && hasAlternativeGroup) {
            return Optional.of(satisfactionDisclosed
                    ? Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.card.requirement_any_of",
                            visible, hidden, external, satisfied)
                    : Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.card.requirement_any_of.undisclosed",
                            visible, hidden, external));
        }
        if (!hasAlternativeGroup) {
            return Optional.of(satisfactionDisclosed
                    ? Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.card.requirement_opaque",
                            groups.size(), hidden, external, satisfied)
                    : Component.translatable(
                            "gui.taczweaponblueprints.research_bench.tree.card.requirement_opaque.undisclosed",
                            groups.size(), hidden, external));
        }
        return Optional.of(satisfactionDisclosed
                ? Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.card.requirement_group_set",
                        groups.size(), visible, hidden, external, satisfied)
                : Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.card.requirement_group_set.undisclosed",
                        groups.size(), visible, hidden, external));
    }

    /**
     * Names only published alternatives. Undisclosed and projection-external
     * members remain anonymous counts, even when a group is partially visible.
     */
    public static List<Component> details(
            ResearchTreeGraph graph,
            ResourceLocation dependentId,
            Function<ResearchTreeGraph.Node, Component> nodeName) {
        if (nodeName == null) {
            throw new IllegalArgumentException(
                    "Research Tree requirement name resolver cannot be null");
        }
        List<ResearchTreeGraph.RequirementGroup> groups = groups(graph, dependentId);
        if (groups.stream().noneMatch(ResearchTreeRequirementText::requiresExplanation)) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        List<ResearchTreeGraph.RequirementGroup> detailedGroups = java.util.stream.Stream.concat(
                        groups.stream().filter(ResearchTreeRequirementText::requiresExplanation),
                        groups.stream().filter(group -> !requiresExplanation(group)))
                .limit(MAX_DETAILED_GROUPS)
                .toList();
        int groupLimit = detailedGroups.size();
        for (int groupIndex = 0; groupIndex < groupLimit; groupIndex++) {
            ResearchTreeGraph.RequirementGroup group = detailedGroups.get(groupIndex);
            lines.add(groupHeader(group));
            int visibleLimit = Math.min(
                    group.visibleAlternativeIds().size(),
                    MAX_VISIBLE_ALTERNATIVES_PER_GROUP);
            for (int alternativeIndex = 0;
                    alternativeIndex < visibleLimit;
                    alternativeIndex++) {
                ResourceLocation alternativeId =
                        group.visibleAlternativeIds().get(alternativeIndex);
                ResearchTreeGraph.Node alternative = graph.node(alternativeId)
                        .orElseThrow(() -> new IllegalStateException(
                                "published requirement alternative is absent"));
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.requirement.alternative",
                        nodeName.apply(alternative)));
            }
            int omittedVisible = group.visibleAlternativeIds().size() - visibleLimit;
            if (omittedVisible > 0) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.requirement.more_visible",
                        omittedVisible));
            }
            if (group.hiddenAlternativeCount() > 0) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.requirement.undiscovered",
                        group.hiddenAlternativeCount()));
            }
            if (group.externalAlternativeCount() > 0) {
                lines.add(Component.translatable(
                        "gui.taczweaponblueprints.research_bench.tree.requirement.outside_view",
                        group.externalAlternativeCount()));
            }
        }
        int omittedGroups = groups.size() - groupLimit;
        if (omittedGroups > 0) {
            lines.add(Component.translatable(
                    "gui.taczweaponblueprints.research_bench.tree.requirement.more_groups",
                    omittedGroups));
        }
        return List.copyOf(lines);
    }

    private static Component groupHeader(ResearchTreeGraph.RequirementGroup group) {
        String kind = isAlternativeGroup(group) ? "any_of" : "mandatory";
        String state = !group.satisfactionDisclosed()
                ? "undisclosed"
                : group.satisfied() ? "satisfied" : "unsatisfied";
        return Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.requirement."
                        + kind + "." + state);
    }

    private static boolean isAlternativeGroup(
            ResearchTreeGraph.RequirementGroup group) {
        return group.visibleAlternativeIds().size()
                + group.hiddenAlternativeCount()
                + group.externalAlternativeCount() > 1;
    }

    private static boolean requiresExplanation(
            ResearchTreeGraph.RequirementGroup group) {
        return isAlternativeGroup(group)
                || group.hiddenAlternativeCount() > 0
                || group.externalAlternativeCount() > 0;
    }

    private static List<ResearchTreeGraph.RequirementGroup> groups(
            ResearchTreeGraph graph,
            ResourceLocation dependentId) {
        if (graph == null || dependentId == null) {
            throw new IllegalArgumentException(
                    "Research Tree requirement text inputs cannot be null");
        }
        if (graph.node(dependentId).isEmpty()) {
            return List.of();
        }
        return graph.requirementGroupsOf(dependentId);
    }
}
