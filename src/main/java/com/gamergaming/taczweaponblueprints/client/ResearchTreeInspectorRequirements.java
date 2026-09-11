package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import static com.gamergaming.taczweaponblueprints.client.ResearchTreeInspectorRow.Kind.*;

/** Requirement links come from the current public graph and reciprocal domain index. */
final class ResearchTreeInspectorRequirements {
    private static final String PREFIX = "gui.taczweaponblueprints.research_bench.tree.";
    static boolean mayFollow(ResearchTreeGraph graph, ResearchTechTreeProjectionCatalog catalog,
            ResourceLocation from, ResourceLocation to) {
        if (from == null || to == null || from.equals(to)
                || graph.node(to).filter(n -> n.visibility().allowsServerSelection()).isEmpty()) return false;
        Domain local = catalog.domainOf(from).orElse(null), remote = catalog.domainOf(to).orElse(null);
        if (local == null || remote == null || graph.requirementGroups().stream()
                .noneMatch(g -> g.dependentId().equals(from) && g.visibleAlternativeIds().contains(to))) return false;
        return local == remote || catalog.relationships().navigationTo(local, from, remote, to)
                .filter(n -> n.direction() == ResearchTechTreeProjection.Direction.REQUIREMENT).isPresent();
    }
    static Component groupHeading(ResearchTreeGraph.RequirementGroup group) {
        int alternatives = group.visibleAlternativeIds().size() + group.hiddenAlternativeCount() + group.externalAlternativeCount();
        return Component.translatable(PREFIX + "inspector.requirement." + (alternatives == 1 ? "single" : "any"), group.ordinal() + 1);
    }
    static List<ResearchTreeInspectorRow> rows(ResearchTreeGraph graph, ResearchTechTreeProjectionCatalog catalog,
            ResourceLocation target, Function<ResearchTreeGraph.Node, Component> name,
            Function<Domain, Component> domainName, int text, int accent, int muted, int learned) {
        var result = new ArrayList<ResearchTreeInspectorRow>();
        var groups = graph.requirementGroups().stream().filter(g -> g.dependentId().equals(target)).toList();
        if (groups.size() > 1) result.add(ResearchTreeInspectorRow.text(GROUP, Component.translatable(PREFIX + "scene.all_groups"), text));
        for (var group : groups) {
            result.add(ResearchTreeInspectorRow.text(GROUP, groupHeading(group), accent));
            for (var id : group.visibleAlternativeIds()) graph.node(id).ifPresent(node -> {
                boolean external = !catalog.domainOf(id).equals(catalog.domainOf(target));
                Component label = name.apply(node);
                if (external && node.visibility().revealsIdentity()) label = Component.translatable(PREFIX + "inspector.external", label,
                        catalog.domainOf(id).map(domainName).orElse(Component.empty()));
                if (node.learned()) label = Component.translatable(PREFIX + "inspector.learned", label);
                result.add(new ResearchTreeInspectorRow(external ? EXTERNAL_LINK : PREREQUISITE, label,
                        node.learned() ? learned : text, mayFollow(graph, catalog, target, id) ? Optional.of(id) : Optional.empty()));
            });
            if (group.hiddenAlternativeCount() > 0) result.add(ResearchTreeInspectorRow.text(EXPLANATION,
                    Component.translatable(PREFIX + "requirement.undiscovered", group.hiddenAlternativeCount()), muted));
            if (group.externalAlternativeCount() > 0) result.add(ResearchTreeInspectorRow.text(EXPLANATION,
                    Component.translatable(PREFIX + "requirement.outside_view", group.externalAlternativeCount()), muted));
        }
        if (groups.isEmpty()) result.add(ResearchTreeInspectorRow.text(EXPLANATION,
                Component.translatable(PREFIX + "inspector.requirement.none"), muted));
        return List.copyOf(result);
    }
}
