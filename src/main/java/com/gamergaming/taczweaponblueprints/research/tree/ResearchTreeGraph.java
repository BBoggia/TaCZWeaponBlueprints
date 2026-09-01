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
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

/** Bounded, deterministic and disclosure-filtered research graph for one player. */
public final class ResearchTreeGraph {
    public static final int MAX_NODES = BlueprintDataManager.MAX_CATALOG_ENTRIES;
    public static final int MAX_EDGES = BlueprintResearchSnapshot.MAX_TOTAL_PREREQUISITES;
    public static final int MAX_REQUIREMENT_GROUPS =
            BlueprintResearchSnapshot.MAX_TOTAL_PREREQUISITES;
    public static final ResearchTreeGraph EMPTY = new ResearchTreeGraph(
            List.of(), List.of(), List.of());
    public static final String REDACTED_NAME_KEY =
            "gui.taczweaponblueprints.research_bench.tree.undisclosed";
    public static final String REDACTED_ITEM_TYPE = "undisclosed";
    public static final ResourceLocation REDACTED_DISPLAY_SLOT =
            new ResourceLocation("minecraft", "paper");

    private static final Comparator<Edge> EDGE_ORDER = Comparator
            .comparing((Edge edge) -> edge.dependentId().toString())
            .thenComparing(edge -> edge.prerequisiteId().toString());
    private static final Comparator<RequirementGroup> REQUIREMENT_GROUP_ORDER = Comparator
            .comparing((RequirementGroup group) -> group.dependentId().toString())
            .thenComparingInt(RequirementGroup::ordinal);

    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<RequirementGroup> requirementGroups;
    private final Map<ResourceLocation, Node> nodesById;
    private final Map<ResourceLocation, List<ResourceLocation>> prerequisitesById;
    private final Map<ResourceLocation, List<RequirementGroup>> requirementGroupsById;

    public ResearchTreeGraph(List<Node> nodes, List<Edge> edges) {
        this(nodes, edges, singletonGroups(nodes, edges));
    }

    public ResearchTreeGraph(
            List<Node> nodes,
            List<Edge> edges,
            List<RequirementGroup> requirementGroups) {
        if ((nodes != null && nodes.stream().anyMatch(Objects::isNull))
                || (edges != null && edges.stream().anyMatch(Objects::isNull))
                || (requirementGroups != null
                        && requirementGroups.stream().anyMatch(Objects::isNull))) {
            throw new IllegalArgumentException(
                    "research tree cannot contain null nodes, edges, or requirement groups");
        }
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        List<Edge> sortedEdges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
        sortedEdges.sort(EDGE_ORDER);
        this.edges = List.copyOf(sortedEdges);
        List<RequirementGroup> sortedGroups = requirementGroups == null
                ? new ArrayList<>()
                : new ArrayList<>(requirementGroups);
        sortedGroups.sort(REQUIREMENT_GROUP_ORDER);
        this.requirementGroups = List.copyOf(sortedGroups);
        validate(this.nodes, this.edges, this.requirementGroups);

        Map<ResourceLocation, Node> nodeIndex = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisiteIndex = new LinkedHashMap<>();
        Map<ResourceLocation, List<RequirementGroup>> requirementGroupIndex =
                new LinkedHashMap<>();
        for (Node node : this.nodes) {
            nodeIndex.put(node.blueprintId(), node);
            prerequisiteIndex.put(node.blueprintId(), new ArrayList<>());
            requirementGroupIndex.put(node.blueprintId(), new ArrayList<>());
        }
        for (Edge edge : this.edges) {
            prerequisiteIndex.get(edge.dependentId()).add(edge.prerequisiteId());
        }
        for (RequirementGroup group : this.requirementGroups) {
            requirementGroupIndex.get(group.dependentId()).add(group);
        }
        prerequisiteIndex.replaceAll((ignored, ids) -> List.copyOf(ids));
        requirementGroupIndex.replaceAll((ignored, groups) -> List.copyOf(groups));
        nodesById = Map.copyOf(nodeIndex);
        prerequisitesById = Map.copyOf(prerequisiteIndex);
        requirementGroupsById = Map.copyOf(requirementGroupIndex);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<RequirementGroup> requirementGroups() {
        return requirementGroups;
    }

    public List<RequirementGroup> requirementGroupsOf(ResourceLocation blueprintId) {
        return blueprintId == null
                ? List.of()
                : requirementGroupsById.getOrDefault(blueprintId, List.of());
    }

    /** Creates a graph whose compatibility edges are derived from group alternatives. */
    public static ResearchTreeGraph withRequirementGroups(
            List<Node> nodes,
            List<RequirementGroup> requirementGroups) {
        return new ResearchTreeGraph(nodes, edgesFor(requirementGroups), requirementGroups);
    }

    public Optional<Node> node(ResourceLocation blueprintId) {
        return blueprintId == null ? Optional.empty() : Optional.ofNullable(nodesById.get(blueprintId));
    }

    public List<ResourceLocation> prerequisitesOf(ResourceLocation blueprintId) {
        return blueprintId == null
                ? List.of()
                : prerequisitesById.getOrDefault(blueprintId, List.of());
    }

    /**
     * Derives one ordinal-local view without changing stable source ordinals or
     * manufacturing edges. Prerequisite counts are reduced to the truthful
     * edges retained by the view.
     */
    public ResearchTreeGraph inducedSubgraph(Set<ResourceLocation> includedNodeIds) {
        if (includedNodeIds == null || includedNodeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "research tree induced-subgraph IDs cannot be null");
        }
        if (!nodesById.keySet().containsAll(includedNodeIds)) {
            throw new IllegalArgumentException(
                    "research tree induced subgraph references an unknown node");
        }
        if (includedNodeIds.isEmpty()) {
            return EMPTY;
        }
        if (includedNodeIds.size() == nodes.size()) {
            return this;
        }

        return orderedInducedSubgraph(nodes.stream()
                .map(Node::blueprintId)
                .filter(includedNodeIds::contains)
                .toList());
    }

    /**
     * Derives a projection in caller-specified node order while retaining every
     * canonical group of an included dependent. Visible alternatives outside the
     * projection become linked/external counts rather than disclosure-hidden
     * alternatives, preserving AND-of-OR semantics without inventing local edges.
     */
    public ResearchTreeGraph orderedInducedSubgraph(
            List<ResourceLocation> orderedNodeIds) {
        if (orderedNodeIds == null || orderedNodeIds.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(orderedNodeIds).size() != orderedNodeIds.size()) {
            throw new IllegalArgumentException(
                    "research tree ordered-subgraph IDs are invalid");
        }
        Set<ResourceLocation> includedNodeIds = Set.copyOf(orderedNodeIds);
        if (!nodesById.keySet().containsAll(includedNodeIds)) {
            throw new IllegalArgumentException(
                    "research tree ordered subgraph references an unknown node");
        }
        if (orderedNodeIds.isEmpty()) {
            return EMPTY;
        }

        Map<ResourceLocation, Integer> prerequisiteCounts = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> hiddenPrerequisiteCounts = new LinkedHashMap<>();
        includedNodeIds.forEach(id -> prerequisiteCounts.put(id, 0));
        includedNodeIds.forEach(id -> hiddenPrerequisiteCounts.put(id, 0));
        List<Edge> retainedEdges = new ArrayList<>();
        for (Edge edge : edges) {
            if (includedNodeIds.contains(edge.prerequisiteId())
                    && includedNodeIds.contains(edge.dependentId())) {
                retainedEdges.add(edge);
                prerequisiteCounts.compute(
                        edge.dependentId(),
                        (ignored, count) -> count == null ? 1 : count + 1);
            }
        }

        Map<ResourceLocation, Integer> nextGroupOrdinal = new LinkedHashMap<>();
        List<RequirementGroup> retainedGroups = new ArrayList<>();
        for (RequirementGroup group : requirementGroups) {
            if (!includedNodeIds.contains(group.dependentId())) {
                continue;
            }
            List<ResourceLocation> alternatives = group.visibleAlternativeIds().stream()
                    .filter(includedNodeIds::contains)
                    .toList();
            int externalAlternatives = Math.addExact(
                    group.externalAlternativeCount(),
                    group.visibleAlternativeIds().size() - alternatives.size());
            int ordinal = nextGroupOrdinal.getOrDefault(group.dependentId(), 0);
            nextGroupOrdinal.put(group.dependentId(), ordinal + 1);
            retainedGroups.add(new RequirementGroup(
                    group.dependentId(),
                    ordinal,
                    alternatives,
                    group.hiddenAlternativeCount(),
                    externalAlternatives,
                    group.satisfactionDisclosed(),
                    group.satisfied()));
            hiddenPrerequisiteCounts.compute(
                    group.dependentId(),
                    (ignored, count) -> (count == null ? 0 : count)
                            + group.hiddenAlternativeCount());
        }

        List<Node> retainedNodes = new ArrayList<>(includedNodeIds.size());
        for (ResourceLocation nodeId : orderedNodeIds) {
            Node node = nodesById.get(nodeId);
            retainedNodes.add(new Node(
                    retainedNodes.size(),
                    node.sourceOrdinal(),
                    node.blueprintId(),
                    node.nameKey(),
                    node.itemType(),
                    node.displaySlotId(),
                    node.visibility(),
                    node.learned(),
                    node.discovered(),
                    node.policyEligible(),
                    node.pointCost(),
                    node.ingredientTypeCount(),
                    prerequisiteCounts.get(node.blueprintId()),
                    hiddenPrerequisiteCounts.get(node.blueprintId()),
                    node.availability()));
        }
        return new ResearchTreeGraph(retainedNodes, retainedEdges, retainedGroups);
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
        if (other == null || nodes.size() != other.nodes.size()
                || !edges.equals(other.edges)
                || !hasSameRequirementTopology(other)) {
            return false;
        }
        for (int index = 0; index < nodes.size(); index++) {
            Node left = nodes.get(index);
            Node right = other.nodes.get(index);
            if (!left.blueprintId().equals(right.blueprintId())
                    || left.sourceOrdinal() != right.sourceOrdinal()
                    || !left.itemType().equals(right.itemType())
                    || left.hiddenPrerequisiteCount() != right.hiddenPrerequisiteCount()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSameRequirementTopology(ResearchTreeGraph other) {
        if (requirementGroups.size() != other.requirementGroups.size()) {
            return false;
        }
        for (int index = 0; index < requirementGroups.size(); index++) {
            RequirementGroup left = requirementGroups.get(index);
            RequirementGroup right = other.requirementGroups.get(index);
            if (!left.dependentId().equals(right.dependentId())
                    || left.ordinal() != right.ordinal()
                    || !left.visibleAlternativeIds().equals(right.visibleAlternativeIds())
                    || left.hiddenAlternativeCount() != right.hiddenAlternativeCount()
                    || left.externalAlternativeCount() != right.externalAlternativeCount()) {
                return false;
            }
        }
        return true;
    }

    private static void validate(
            List<Node> nodes,
            List<Edge> edges,
            List<RequirementGroup> requirementGroups) {
        if (nodes.size() > MAX_NODES || edges.size() > MAX_EDGES
                || requirementGroups.size() > MAX_REQUIREMENT_GROUPS) {
            throw new IllegalArgumentException("research tree exceeds its node or edge limit");
        }
        Map<ResourceLocation, Node> byId = new LinkedHashMap<>();
        Set<Integer> sourceOrdinals = new HashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node.ordinal() != index) {
                throw new IllegalArgumentException("research tree node ordinals must be contiguous");
            }
            if (byId.put(node.blueprintId(), node) != null
                    || !sourceOrdinals.add(node.sourceOrdinal())) {
                throw new IllegalArgumentException(
                        "research tree contains a duplicate blueprint ID or source ordinal");
            }
        }

        Set<Edge> uniqueEdges = new HashSet<>();
        Map<ResourceLocation, Integer> visiblePrerequisites = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisitesByNode = new LinkedHashMap<>();
        for (Node node : nodes) {
            visiblePrerequisites.put(node.blueprintId(), 0);
            prerequisitesByNode.put(node.blueprintId(), new ArrayList<>());
        }


        Set<Edge> groupEdges = new LinkedHashSet<>();
        Map<ResourceLocation, Integer> nextOrdinal = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> hiddenAlternatives = new LinkedHashMap<>();
        for (RequirementGroup group : requirementGroups) {
            if (!byId.containsKey(group.dependentId())) {
                throw new IllegalArgumentException(
                        "research requirement group references an unknown dependent");
            }
            if (group.satisfactionDisclosed()
                    != byId.get(group.dependentId()).visibility().revealsExactPolicy()) {
                throw new IllegalArgumentException(
                        "research requirement group satisfaction violates dependent disclosure");
            }
            int expected = nextOrdinal.getOrDefault(group.dependentId(), 0);
            if (group.ordinal() != expected) {
                throw new IllegalArgumentException(
                        "research requirement group ordinals must be contiguous per dependent");
            }
            nextOrdinal.put(group.dependentId(), expected + 1);
            for (ResourceLocation alternative : group.visibleAlternativeIds()) {
                if (!byId.containsKey(alternative)) {
                    throw new IllegalArgumentException(
                            "research requirement group references an unknown visible alternative");
                }
                groupEdges.add(new Edge(alternative, group.dependentId()));
            }
            hiddenAlternatives.compute(
                    group.dependentId(),
                    (ignored, count) -> (count == null ? 0 : count)
                            + group.hiddenAlternativeCount());
        }
        if (!groupEdges.equals(new LinkedHashSet<>(edges))) {
            throw new IllegalArgumentException(
                    "research tree edges do not match its visible requirement alternatives");
        }
        for (Node node : nodes) {
            if (hiddenAlternatives.getOrDefault(node.blueprintId(), 0)
                    != node.hiddenPrerequisiteCount()) {
                throw new IllegalArgumentException(
                        "research tree hidden prerequisite counts do not match its groups");
            }
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
                && nodes.equals(other.nodes) && edges.equals(other.edges)
                && requirementGroups.equals(other.requirementGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, edges, requirementGroups);
    }

    @Override
    public String toString() {
        return "ResearchTreeGraph[nodes=" + nodes + ", edges=" + edges
                + ", requirementGroups=" + requirementGroups + "]";
    }

    public record Node(
            int ordinal,
            int sourceOrdinal,
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
        /** Full-publication nodes use their table ordinal as their stable source ordinal. */
        public Node(
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
            this(
                    ordinal,
                    ordinal,
                    blueprintId,
                    nameKey,
                    itemType,
                    displaySlotId,
                    visibility,
                    learned,
                    discovered,
                    policyEligible,
                    pointCost,
                    ingredientTypeCount,
                    prerequisiteCount,
                    hiddenPrerequisiteCount,
                    availability);
        }

        public Node {
            if (ordinal < 0 || ordinal >= MAX_NODES
                    || sourceOrdinal < 0 || sourceOrdinal >= MAX_NODES
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
                    || hiddenPrerequisiteCount < 0
                    || hiddenPrerequisiteCount > BlueprintResearchRule.MAX_PREREQUISITES
                    || learned != (availability == Availability.LEARNED)
                    || policyEligible != (availability == Availability.AVAILABLE)
                    || (availability == Availability.RESEARCH_DISABLED
                    && (pointCost != 0 || ingredientTypeCount != 0
                    || prerequisiteCount != 0 || hiddenPrerequisiteCount != 0))) {
                throw new IllegalArgumentException("invalid research tree node state");
            }
            if (!visibility.revealsIdentity()) {
                if (!isRedactedNodeId(blueprintId, sourceOrdinal)
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

    /** Disclosure-safe canonical any-of group for one public dependent node. */
    public record RequirementGroup(
            ResourceLocation dependentId,
            int ordinal,
            List<ResourceLocation> visibleAlternativeIds,
            int hiddenAlternativeCount,
            int externalAlternativeCount,
            boolean satisfactionDisclosed,
            boolean satisfied) {
        /** Compatibility constructor for fully disclosed requirement groups. */
        public RequirementGroup(
                ResourceLocation dependentId,
                int ordinal,
                List<ResourceLocation> visibleAlternativeIds,
                int hiddenAlternativeCount,
                boolean satisfied) {
            this(
                    dependentId,
                    ordinal,
                    visibleAlternativeIds,
                    hiddenAlternativeCount,
                    0,
                    true,
                    satisfied);
        }

        /** Full-publication constructor with no projection-external alternatives. */
        public RequirementGroup(
                ResourceLocation dependentId,
                int ordinal,
                List<ResourceLocation> visibleAlternativeIds,
                int hiddenAlternativeCount,
                boolean satisfactionDisclosed,
                boolean satisfied) {
            this(
                    dependentId,
                    ordinal,
                    visibleAlternativeIds,
                    hiddenAlternativeCount,
                    0,
                    satisfactionDisclosed,
                    satisfied);
        }

        public RequirementGroup {
            if (dependentId == null || ordinal < 0
                    || ordinal >= ResearchRequirements.MAX_GROUPS
                    || visibleAlternativeIds == null
                    || visibleAlternativeIds.stream().anyMatch(Objects::isNull)
                    || hiddenAlternativeCount < 0
                    || hiddenAlternativeCount > ResearchPrerequisiteGroup.MAX_ALTERNATIVES
                    || externalAlternativeCount < 0
                    || externalAlternativeCount > ResearchPrerequisiteGroup.MAX_ALTERNATIVES
                    || visibleAlternativeIds.isEmpty()
                            && hiddenAlternativeCount == 0
                            && externalAlternativeCount == 0
                    || visibleAlternativeIds.size() + hiddenAlternativeCount
                            + externalAlternativeCount
                            > ResearchPrerequisiteGroup.MAX_ALTERNATIVES
                    || !satisfactionDisclosed && satisfied) {
                throw new IllegalArgumentException("invalid research requirement group");
            }
            validateId(dependentId, "requirement-group dependent");
            LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>(
                    visibleAlternativeIds);
            if (unique.size() != visibleAlternativeIds.size()
                    || unique.contains(dependentId)) {
                throw new IllegalArgumentException(
                        "research requirement group contains a duplicate or self alternative");
            }
            visibleAlternativeIds = unique.stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .toList();
            visibleAlternativeIds.forEach(id ->
                    validateId(id, "requirement-group alternative"));
        }
    }

    private static List<RequirementGroup> singletonGroups(
            List<Node> nodes,
            List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        Map<ResourceLocation, Boolean> disclosedByDependent = new LinkedHashMap<>();
        if (nodes != null) {
            nodes.forEach(node -> disclosedByDependent.put(
                    node.blueprintId(), node.visibility().revealsExactPolicy()));
        }
        Map<ResourceLocation, Integer> ordinals = new LinkedHashMap<>();
        return edges.stream().sorted(EDGE_ORDER).map(edge -> {
            int ordinal = ordinals.getOrDefault(edge.dependentId(), 0);
            ordinals.put(edge.dependentId(), ordinal + 1);
            return new RequirementGroup(
                    edge.dependentId(),
                    ordinal,
                    List.of(edge.prerequisiteId()),
                    0,
                    0,
                    disclosedByDependent.getOrDefault(edge.dependentId(), false),
                    false);
        }).toList();
    }

    private static List<Edge> edgesFor(List<RequirementGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Edge> edges = new LinkedHashSet<>();
        groups.forEach(group -> group.visibleAlternativeIds().forEach(alternative ->
                edges.add(new Edge(alternative, group.dependentId()))));
        return List.copyOf(edges);
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
