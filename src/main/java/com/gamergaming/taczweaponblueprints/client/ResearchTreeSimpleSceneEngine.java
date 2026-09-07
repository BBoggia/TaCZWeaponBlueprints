package com.gamergaming.taczweaponblueprints.client;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Independent emergency layout: one node per row, compacted ranks, and separate route tracks. */
public final class ResearchTreeSimpleSceneEngine implements ResearchTreeSceneEngine {
    public static final String REVISION = "simple-v1";
    @Override public String revision() { return REVISION; }

    @Override public ResearchTreeScene layout(ResearchTreeSceneInput input) {
        var metrics = input.policy().nodeMetrics();
        var sorted = input.topology().nodes().stream().sorted(Comparator
                .comparingInt(ResearchTechTreeProjection.Placement::rank)
                .thenComparingInt(ResearchTechTreeProjection.Placement::laneOrder)
                .thenComparingLong(ResearchTechTreeProjection.Placement::siblingOrder)
                .thenComparing(p -> p.nodeId().toString())).toList();
        double padding = Math.max(12, input.policy().spacing().canvasPadding()) + metrics.paintPadding();
        double step = metrics.height() + 2 * metrics.paintPadding()
                + Math.max(16, input.policy().spacing().sameTierStepGap());
        Map<ResourceLocation, ResearchTreeScene.Node> positioned = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            var p = sorted.get(i);
            positioned.put(p.nodeId(), ResearchTreeSceneGeometry.node(p.nodeId(), padding,
                    padding + (sorted.size() - i - 1) * step, metrics));
        }
        var nodes = input.topology().nodes().stream().map(p -> positioned.get(p.nodeId())).toList();
        var groups = input.topology().groups().stream().map(g -> new ResearchTreeScene.Requirement(g, Optional.empty())).toList();
        return ResearchTreeSceneGeometry.assemble(input, revision(), nodes, groups,
                ResearchTreeSceneGeometry.outsideRoutes(input, nodes),
                ResearchTreeSceneGeometry.outsideBoundaries(input, nodes));
    }
}
