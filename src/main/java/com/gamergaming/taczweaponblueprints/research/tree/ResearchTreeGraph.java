package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.network.BlueprintSyncLimits;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/** Bounded, deterministic and disclosure-filtered research graph for one player. */
public final class ResearchTreeGraph {
    public static final int MAX_NODES = BlueprintDataManager.MAX_CATALOG_ENTRIES;
    public static final int MAX_EDGES = BlueprintResearchSnapshot.MAX_TOTAL_PREREQUISITES;
    public static final ResearchTreeGraph EMPTY = new ResearchTreeGraph(List.of(), List.of());
    public static final String REDACTED_NAME_KEY =
            "gui.taczweaponblueprints.research_bench.tree.undisclosed";
    public static final String REDACTED_ITEM_TYPE = "undisclosed";
    public static final ResourceLocation REDACTED_DISPLAY_SLOT =
            new ResourceLocation("minecraft", "paper");

    private static final Comparator<Edge> EDGE_ORDER = Comparator
            .comparing((Edge edge) -> edge.dependentId().toString())
            .thenComparing(edge -> edge.prerequisiteId().toString());

    private final List<Node> nodes;
    private final List<Edge> edges;
    private final Map<ResourceLocation, Node> nodesById;
    private final Map<ResourceLocation, List<ResourceLocation>> prerequisitesById;

    public ResearchTreeGraph(List<Node> nodes, List<Edge> edges) {
        if ((nodes != null && nodes.stream().anyMatch(Objects::isNull))
                || (edges != null && edges.stream().anyMatch(Objects::isNull))) {
            throw new IllegalArgumentException("research tree cannot contain null nodes or edges");
        }
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        List<Edge> sortedEdges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
        sortedEdges.sort(EDGE_ORDER);
        this.edges = List.copyOf(sortedEdges);
        validate(this.nodes, this.edges);

        Map<ResourceLocation, Node> nodeIndex = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisiteIndex = new LinkedHashMap<>();
        for (Node node : this.nodes) {
            nodeIndex.put(node.blueprintId(), node);
            prerequisiteIndex.put(node.blueprintId(), new ArrayList<>());
        }
        for (Edge edge : this.edges) {
            prerequisiteIndex.get(edge.dependentId()).add(edge.prerequisiteId());
        }
        prerequisiteIndex.replaceAll((ignored, ids) -> List.copyOf(ids));
        nodesById = Map.copyOf(nodeIndex);
        prerequisitesById = Map.copyOf(prerequisiteIndex);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public Optional<Node> node(ResourceLocation blueprintId) {
        return blueprintId == null ? Optional.empty() : Optional.ofNullable(nodesById.get(blueprintId));
    }

    public List<ResourceLocation> prerequisitesOf(ResourceLocation blueprintId) {
        return blueprintId == null
                ? List.of()
                : prerequisitesById.getOrDefault(blueprintId, List.of());
    }

    /** Opaque per-publication key for a node whose real blueprint ID is not disclosed. */
    public static ResourceLocation redactedNodeId(int ordinal) {
        return redactedNodeId(ordinal, 0);
    }

    public static ResourceLocation redactedNodeId(int ordinal, int disambiguator) {
        if (ordinal < 0 || ordinal >= MAX_NODES) {
            throw new IllegalArgumentException("redacted research tree ordinal is invalid");
        }
        if (disambiguator < 0 || disambiguator > MAX_NODES) {
            throw new IllegalArgumentException("redacted research tree disambiguator is invalid");
        }
        return new ResourceLocation(
                "taczweaponblueprints",
                "undisclosed/" + ordinal + (disambiguator == 0 ? "" : "/" + disambiguator));
    }

    private static boolean isRedactedNodeId(ResourceLocation id, int ordinal) {
        if (!id.getNamespace().equals("taczweaponblueprints")) {
            return false;
        }
        String base = "undisclosed/" + ordinal;
        String path = id.getPath();
        if (path.equals(base)) {
            return true;
        }
        if (!path.startsWith(base + "/")) {
            return false;
        }
        String suffix = path.substring(base.length() + 1);
        try {
            int disambiguator = Integer.parseInt(suffix);
            return disambiguator > 0
                    && disambiguator <= MAX_NODES
                    && suffix.equals(Integer.toString(disambiguator));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /** True when a new player-state snapshot can safely reuse the existing layout. */
    public boolean hasSameLayoutTopology(ResearchTreeGraph other) {
        if (other == null || nodes.size() != other.nodes.size() || !edges.equals(other.edges)) {
            return false;
        }
        for (int index = 0; index < nodes.size(); index++) {
            Node left = nodes.get(index);
            Node right = other.nodes.get(index);
            if (!left.blueprintId().equals(right.blueprintId())
                    || !left.itemType().equals(right.itemType())
                    || left.hiddenPrerequisiteCount() != right.hiddenPrerequisiteCount()) {
                return false;
            }
        }
        return true;
    }

    private static void validate(List<Node> nodes, List<Edge> edges) {
        if (nodes.size() > MAX_NODES || edges.size() > MAX_EDGES) {
            throw new IllegalArgumentException("research tree exceeds its node or edge limit");
        }
        Map<ResourceLocation, Node> byId = new LinkedHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node.ordinal() != index) {
                throw new IllegalArgumentException("research tree node ordinals must be contiguous");
            }
            if (byId.put(node.blueprintId(), node) != null) {
                throw new IllegalArgumentException("research tree contains a duplicate blueprint ID");
            }
        }

        Set<Edge> uniqueEdges = new HashSet<>();
        Map<ResourceLocation, Integer> visiblePrerequisites = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisitesByNode = new LinkedHashMap<>();
        for (Node node : nodes) {
            visiblePrerequisites.put(node.blueprintId(), 0);
            prerequisitesByNode.put(node.blueprintId(), new ArrayList<>());
        }
        for (Edge edge : edges) {
            if (!uniqueEdges.add(edge)) {
                throw new IllegalArgumentException("research tree contains a duplicate edge");
            }
            if (!byId.containsKey(edge.prerequisiteId()) || !byId.containsKey(edge.dependentId())) {
                throw new IllegalArgumentException("research tree edge references an unknown node");
            }
            if (edge.prerequisiteId().equals(edge.dependentId())) {
                throw new IllegalArgumentException("research tree cannot contain a self edge");
            }
            visiblePrerequisites.compute(edge.dependentId(), (ignored, count) -> count == null ? 1 : count + 1);
            prerequisitesByNode.get(edge.dependentId()).add(edge.prerequisiteId());
        }
        for (Node node : nodes) {
            if (visiblePrerequisites.getOrDefault(node.blueprintId(), 0)
                    != node.prerequisiteCount()) {
                throw new IllegalArgumentException("research tree prerequisite counts do not match its edges");
            }
        }

        Set<ResourceLocation> complete = new LinkedHashSet<>();
        for (ResourceLocation blueprintId : byId.keySet()) {
            visit(blueprintId, prerequisitesByNode, complete, new LinkedHashSet<>());
        }
    }

    private static void visit(
            ResourceLocation blueprintId,
            Map<ResourceLocation, List<ResourceLocation>> prerequisitesByNode,
            Set<ResourceLocation> complete,
            LinkedHashSet<ResourceLocation> visiting) {
        if (complete.contains(blueprintId)) {
            return;
        }
        if (!visiting.add(blueprintId)) {
            throw new IllegalArgumentException("research tree contains a prerequisite cycle");
        }
        if (visiting.size() > BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH) {
            throw new IllegalArgumentException("research tree exceeds the prerequisite depth limit");
        }
        try {
            for (ResourceLocation prerequisite : prerequisitesByNode.getOrDefault(blueprintId, List.of())) {
                visit(prerequisite, prerequisitesByNode, complete, visiting);
            }
            complete.add(blueprintId);
        } finally {
            visiting.remove(blueprintId);
        }
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof ResearchTreeGraph other
                && nodes.equals(other.nodes) && edges.equals(other.edges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, edges);
    }

    @Override
    public String toString() {
        return "ResearchTreeGraph[nodes=" + nodes + ", edges=" + edges + "]";
    }

    public record Node(
            int ordinal,
            ResourceLocation blueprintId,
            String nameKey,
            String itemType,
            ResourceLocation displaySlotId,
            JournalVisibility visibility,
            boolean learned,
            boolean discovered,
            boolean policyEligible,
            int pointCost,
            int ingredientTypeCount,
            int prerequisiteCount,
            int hiddenPrerequisiteCount,
            Availability availability) {
        public Node {
            if (ordinal < 0 || ordinal >= MAX_NODES
                    || blueprintId == null
                    || displaySlotId == null
                    || visibility == null
                    || availability == null
                    || !visibility.appearsInTree()) {
                throw new IllegalArgumentException("invalid research tree node identity");
            }
            validateId(blueprintId, "blueprint");
            validateId(displaySlotId, "display slot");
            validateText(nameKey, BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH, "name key");
            validateText(itemType, BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH, "item type");
            if (pointCost < 0 || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || ingredientTypeCount < 0
                    || ingredientTypeCount > BlueprintResearchCost.MAX_INGREDIENT_TYPES
                    || prerequisiteCount < 0
                    || prerequisiteCount > BlueprintResearchRule.MAX_PREREQUISITES
                    || hiddenPrerequisiteCount != 0
                    || learned != (availability == Availability.LEARNED)
                    || policyEligible != (availability == Availability.AVAILABLE)
                    || (availability == Availability.RESEARCH_DISABLED
                    && (pointCost != 0 || ingredientTypeCount != 0
                    || prerequisiteCount != 0 || hiddenPrerequisiteCount != 0))) {
                throw new IllegalArgumentException("invalid research tree node state");
            }
            if (!visibility.revealsIdentity()) {
                if (!isRedactedNodeId(blueprintId, ordinal)
                        || !itemType.equals(REDACTED_ITEM_TYPE)
                        || !displaySlotId.equals(REDACTED_DISPLAY_SLOT)
                        || availability != Availability.REDACTED
                        || learned || discovered || policyEligible
                        || pointCost != 0 || ingredientTypeCount != 0
                        || (!visibility.revealsName() && !nameKey.equals(REDACTED_NAME_KEY))) {
                    throw new IllegalArgumentException("redacted research tree node leaks restricted metadata");
                }
            } else if (!visibility.revealsExactPolicy()
                    && (availability != Availability.PREVIEW
                    || learned || discovered || policyEligible)) {
                throw new IllegalArgumentException("preview research tree node leaks full policy state");
            }
        }

        public State state() {
            if (availability == Availability.LEARNED) {
                return State.LEARNED;
            }
            return availability == Availability.AVAILABLE ? State.AVAILABLE : State.LOCKED;
        }
    }

    public record Edge(ResourceLocation prerequisiteId, ResourceLocation dependentId) {
        public Edge {
            if (prerequisiteId == null || dependentId == null) {
                throw new IllegalArgumentException("research tree edge IDs cannot be null");
            }
            validateId(prerequisiteId, "edge prerequisite");
            validateId(dependentId, "edge dependent");
        }
    }

    public enum State {
        LOCKED,
        AVAILABLE,
        LEARNED
    }

    /** Disclosure-safe reason a visible node is not currently actionable. */
    public enum Availability {
        REDACTED,
        PREVIEW,
        LEARNED,
        AVAILABLE,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        RESEARCH_DISABLED,
        COST_ABOVE_CAP,
        CONTENT_UNAVAILABLE
    }

    private static void validateId(ResourceLocation id, String field) {
        if (id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("research tree " + field + " ID is oversized");
        }
    }

    private static void validateText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException("research tree " + field + " is blank or oversized");
        }
    }
}
