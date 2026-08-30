package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/** Shared Phase 0 publications modeled after the reported large-catalog failure. */
final class ResearchTreeRedesignFixture {
    static final int ACTIVE_CATALOG_NODES = 481;
    static final int CURATED_DEFAULT_NODES = 54;
    static final int GENERATED_NODES = ACTIVE_CATALOG_NODES - CURATED_DEFAULT_NODES;
    static final ResourceLocation PISTOL_GROUP_ID =
            new ResourceLocation("taczweaponblueprints", "pistols");

    private static final int[] AUTHORED_GROUP_SIZES = {4, 14, 17, 6, 5, 6, 2};
    private static final List<String> DISCLOSED_LANES = List.of(
            "ammo",
            "grip",
            "machine_gun",
            "muzzle",
            "pistol",
            "rifle",
            "launcher",
            "scope",
            "shotgun",
            "submachine_gun",
            "sniper");

    private ResearchTreeRedesignFixture() {
    }

    static ResearchTreePublication denseGeneratedCatalog() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(ACTIVE_CATALOG_NODES);
        List<ResearchTreePresentation.Group> groups = new ArrayList<>();
        int ordinal = 0;
        for (int groupOrder = 0; groupOrder < AUTHORED_GROUP_SIZES.length; groupOrder++) {
            List<ResearchTreePresentation.Member> members = new ArrayList<>();
            ResourceLocation icon = null;
            for (int memberOrder = 0;
                    memberOrder < AUTHORED_GROUP_SIZES[groupOrder];
                    memberOrder++) {
                ResourceLocation id = id("authored_" + ordinal);
                if (icon == null) {
                    icon = id;
                }
                nodes.add(disclosedNode(
                        ordinal,
                        id,
                        DISCLOSED_LANES.get(ordinal % DISCLOSED_LANES.size()),
                        0));
                members.add(new ResearchTreePresentation.Member(id, 0, memberOrder));
                ordinal++;
            }
            groups.add(new ResearchTreePresentation.Group(
                    id("group_" + groupOrder),
                    "Group " + groupOrder,
                    Optional.empty(),
                    Optional.of(icon),
                    groupOrder,
                    ResearchTreePresentation.Kind.AUTHORED,
                    members));
        }

        List<ResearchTreePresentation.Member> generated = new ArrayList<>(GENERATED_NODES);
        for (int memberOrder = 0; ordinal < ACTIVE_CATALOG_NODES; ordinal++, memberOrder++) {
            ResourceLocation publicId = ResearchTreeGraph.redactedNodeId(ordinal);
            nodes.add(redactedNode(ordinal, publicId, 0));
            generated.add(new ResearchTreePresentation.Member(publicId, 0, memberOrder));
        }
        groups.add(new ResearchTreePresentation.Group(
                ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                ResearchTreePresentation.UNDISCLOSED_TITLE,
                Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                Optional.empty(),
                groups.size(),
                ResearchTreePresentation.Kind.UNDISCLOSED,
                generated));
        return new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(groups));
    }

    static ResearchTreePublication connectedProgression() {
        List<ResearchTreeGraph.Node> nodes = List.of(
                disclosedNode(0, id("root"), "pistol", 0),
                disclosedNode(1, id("left"), "pistol", 1),
                disclosedNode(2, id("right"), "shotgun", 1),
                disclosedNode(3, id("left_leaf"), "rifle", 1),
                disclosedNode(4, id("right_leaf"), "sniper", 1),
                disclosedNode(5, id("merge"), "machine_gun", 2),
                disclosedNode(6, id("top"), "launcher", 1));
        List<ResearchTreeGraph.Edge> edges = List.of(
                edge("root", "left"),
                edge("root", "right"),
                edge("left", "left_leaf"),
                edge("right", "right_leaf"),
                edge("left_leaf", "merge"),
                edge("right_leaf", "merge"),
                edge("merge", "top"));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                group(0, "starter", List.of(
                        member("root", 0, 0),
                        member("left", 1, 0),
                        member("left_leaf", 2, 0))),
                group(1, "branch", List.of(
                        member("right", 1, 0),
                        member("right_leaf", 2, 0))),
                group(2, "elite", List.of(
                        member("merge", 3, 0),
                        member("top", 4, 0)))));
        return new ResearchTreePublication(new ResearchTreeGraph(nodes, edges), presentation);
    }

    /** Exact public topology and authored ranks of the packaged TaCZ 1.1.8 pistol branch. */
    static ResearchTreePublication defaultPistolProgression() {
        List<List<String>> rankPaths = List.of(
                List.of("taurus943"),
                List.of("glock_17", "m9a4"),
                List.of("m1911", "cz75", "p320", "hk_mk23", "rhino357"),
                List.of("lonetrail", "b93r", "deagle", "deagle_golden", "timeless50"),
                List.of("taurus500"));
        List<ResearchTreeGraph.Edge> edges = List.of(
                taczEdge("taurus943", "glock_17"),
                taczEdge("taurus943", "m9a4"),
                taczEdge("glock_17", "m1911"),
                taczEdge("glock_17", "cz75"),
                taczEdge("glock_17", "p320"),
                taczEdge("glock_17", "hk_mk23"),
                taczEdge("m9a4", "rhino357"),
                taczEdge("m1911", "lonetrail"),
                taczEdge("cz75", "b93r"),
                taczEdge("p320", "deagle"),
                taczEdge("p320", "deagle_golden"),
                taczEdge("p320", "timeless50"),
                taczEdge("rhino357", "taurus500"));
        Map<ResourceLocation, Integer> prerequisiteCounts = new LinkedHashMap<>();
        edges.forEach(edge -> prerequisiteCounts.merge(edge.dependentId(), 1, Integer::sum));

        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTreePresentation.Member> members = new ArrayList<>();
        for (int rank = 0; rank < rankPaths.size(); rank++) {
            List<String> paths = rankPaths.get(rank);
            for (int order = 0; order < paths.size(); order++) {
                ResourceLocation nodeId = taczId(paths.get(order));
                nodes.add(disclosedNode(
                        nodes.size(),
                        nodeId,
                        "pistol",
                        prerequisiteCounts.getOrDefault(nodeId, 0)));
                members.add(new ResearchTreePresentation.Member(nodeId, rank, order));
            }
        }
        ResearchTreePresentation.Group pistols = new ResearchTreePresentation.Group(
                PISTOL_GROUP_ID,
                "Pistols",
                Optional.of("gui.taczweaponblueprints.research_group.pistols"),
                Optional.of(taczId("taurus943")),
                0,
                ResearchTreePresentation.Kind.AUTHORED,
                members);
        return new ResearchTreePublication(
                new ResearchTreeGraph(nodes, edges),
                new ResearchTreePresentation(List.of(pistols)));
    }

    /**
     * A node-level DAG whose group-level relationship is A -> B -> A. Layout
     * composition must not assume that collapsing groups also produces a DAG.
     */
    static ResearchTreePublication alternatingGroupDependencies() {
        List<ResearchTreeGraph.Node> nodes = List.of(
                disclosedNode(0, id("group_a_root"), "rifle", 0),
                disclosedNode(1, id("group_b_entry"), "rifle", 1),
                disclosedNode(2, id("group_b_exit"), "rifle", 1),
                disclosedNode(3, id("group_a_top"), "rifle", 1));
        List<ResearchTreeGraph.Edge> edges = List.of(
                edge("group_a_root", "group_b_entry"),
                edge("group_b_entry", "group_b_exit"),
                edge("group_b_exit", "group_a_top"));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                group(0, "group_a", List.of(
                        member("group_a_root", 0, 0),
                        member("group_a_top", 3, 0))),
                group(1, "group_b", List.of(
                        member("group_b_entry", 1, 0),
                        member("group_b_exit", 2, 0)))));
        return new ResearchTreePublication(new ResearchTreeGraph(nodes, edges), presentation);
    }

    /** Included public nodes with one truthful edge crossing into an excluded redacted group. */
    static ResearchTreePublication disclosureBoundary() {
        ResourceLocation root = id("public_root");
        ResourceLocation child = id("public_child");
        ResourceLocation undisclosed = ResearchTreeGraph.redactedNodeId(2);
        List<ResearchTreeGraph.Node> nodes = List.of(
                disclosedNode(0, root, "rifle", 0),
                disclosedNode(1, child, "rifle", 1),
                redactedNode(2, undisclosed, 1));
        List<ResearchTreeGraph.Edge> edges = List.of(
                new ResearchTreeGraph.Edge(root, child),
                new ResearchTreeGraph.Edge(child, undisclosed));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("public_group"),
                        "Public",
                        Optional.empty(),
                        Optional.of(root),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(root, 0, 0),
                                new ResearchTreePresentation.Member(child, 1, 0))),
                new ResearchTreePresentation.Group(
                        ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                        ResearchTreePresentation.UNDISCLOSED_TITLE,
                        Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                        Optional.empty(),
                        1,
                        ResearchTreePresentation.Kind.UNDISCLOSED,
                        List.of(new ResearchTreePresentation.Member(undisclosed, 2, 0)))));
        return new ResearchTreePublication(new ResearchTreeGraph(nodes, edges), presentation);
    }

    /** Longest valid prerequisite chain accepted by the graph boundary. */
    static ResearchTreePublication maximumDepthProgression() {
        int size = BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH;
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(size);
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>(size - 1);
        List<ResearchTreePresentation.Member> members = new ArrayList<>(size);
        for (int ordinal = 0; ordinal < size; ordinal++) {
            ResourceLocation nodeId = id("depth_" + ordinal);
            nodes.add(disclosedNode(ordinal, nodeId, "rifle", ordinal == 0 ? 0 : 1));
            members.add(new ResearchTreePresentation.Member(nodeId, ordinal, 0));
            if (ordinal > 0) {
                edges.add(new ResearchTreeGraph.Edge(id("depth_" + (ordinal - 1)), nodeId));
            }
        }
        return new ResearchTreePublication(
                new ResearchTreeGraph(nodes, edges),
                new ResearchTreePresentation(List.of(new ResearchTreePresentation.Group(
                        id("maximum_depth"),
                        "Maximum depth",
                        Optional.empty(),
                        Optional.of(id("depth_0")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        members))));
    }

    private static ResearchTreePresentation.Group group(
            int order,
            String name,
            List<ResearchTreePresentation.Member> members) {
        return new ResearchTreePresentation.Group(
                id(name),
                name,
                Optional.empty(),
                Optional.of(members.get(0).nodeId()),
                order,
                ResearchTreePresentation.Kind.AUTHORED,
                members);
    }

    private static ResearchTreePresentation.Member member(
            String name,
            int rank,
            int order) {
        return new ResearchTreePresentation.Member(id(name), rank, order);
    }

    private static ResearchTreeGraph.Edge edge(String prerequisite, String dependent) {
        return new ResearchTreeGraph.Edge(id(prerequisite), id(dependent));
    }

    private static ResearchTreeGraph.Edge taczEdge(String prerequisite, String dependent) {
        return new ResearchTreeGraph.Edge(taczId(prerequisite), taczId(dependent));
    }

    private static ResearchTreeGraph.Node disclosedNode(
            int ordinal,
            ResourceLocation id,
            String itemType,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "fixture." + id.getPath(),
                itemType,
                new ResourceLocation("minecraft", "paper"),
                JournalVisibility.FULL,
                false,
                false,
                prerequisiteCount == 0,
                8,
                0,
                prerequisiteCount,
                0,
                prerequisiteCount == 0
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeGraph.Node redactedNode(
            int ordinal,
            ResourceLocation publicId,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                publicId,
                ResearchTreeGraph.REDACTED_NAME_KEY,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                JournalVisibility.SILHOUETTE,
                false,
                false,
                false,
                0,
                0,
                prerequisiteCount,
                0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResourceLocation taczId(String path) {
        return new ResourceLocation("tacz", path);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("phase_zero", path);
    }
}
