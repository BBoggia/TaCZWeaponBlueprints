package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;

import net.minecraft.resources.ResourceLocation;

/** Sanitizes authored grouping into metadata safe to publish with one graph. */
final class ResearchTreePresentationBuilder {
    private static final String SYNTHETIC_NAMESPACE = "taczweaponblueprints";

    private ResearchTreePresentationBuilder() {
    }

    static ResearchTreePresentation build(
            ResearchTreeGraph graph,
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            Map<ResourceLocation, ResourceLocation> publicIds) {
        if (graph == null || snapshot == null || profileId == null || publicIds == null) {
            throw new IllegalArgumentException("research presentation inputs cannot be null");
        }
        validatePublicIds(graph, publicIds);
        if (graph.nodes().isEmpty()) {
            return ResearchTreePresentation.EMPTY;
        }

        Map<ResourceLocation, Integer> depths = publishedDepths(graph);
        List<DraftGroup> drafts = new ArrayList<>();
        Set<ResourceLocation> assignedRealIds = new LinkedHashSet<>();
        Set<ResourceLocation> usedGroupIds = new LinkedHashSet<>();
        Map<Integer, Integer> publicAuthoredRanks = publicAuthoredRanks(
                graph,
                snapshot,
                profileId,
                publicIds);

        for (BlueprintResearchSnapshot.GroupBinding binding : snapshot.groupsForProfile(profileId)) {
            DraftGroup authored = authoredGroup(
                    graph,
                    binding,
                    publicIds,
                    publicAuthoredRanks,
                    assignedRealIds);
            if (authored != null) {
                drafts.add(authored);
                usedGroupIds.add(authored.id());
            }
        }

        Map<String, List<ResourceLocation>> fallbackByItemType = new LinkedHashMap<>();
        List<ResourceLocation> undisclosed = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : publicIds.entrySet()) {
            ResourceLocation realId = entry.getKey();
            ResourceLocation publicId = entry.getValue();
            ResearchTreeGraph.Node node = graph.node(publicId).orElseThrow();
            if (!node.visibility().revealsIdentity()) {
                undisclosed.add(publicId);
            } else if (!assignedRealIds.contains(realId)) {
                fallbackByItemType.computeIfAbsent(node.itemType(), ignored -> new ArrayList<>())
                        .add(publicId);
            }
        }

        SyntheticIdAllocator fallbackIds = new SyntheticIdAllocator("published/fallback");
        fallbackByItemType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(entry -> {
                    ResourceLocation groupId = fallbackIds.next(usedGroupIds);
                    List<ResearchTreePresentation.Member> members = depthMembers(entry.getValue(), depths);
                    drafts.add(new DraftGroup(
                            groupId,
                            fallbackTitle(entry.getKey()),
                            Optional.empty(),
                            Optional.of(members.get(0).nodeId()),
                            ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK,
                            ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK
                                    .includedInOverviewByDefault(),
                            members));
                    usedGroupIds.add(groupId);
                });

        if (!undisclosed.isEmpty()) {
            ResourceLocation groupId = new SyntheticIdAllocator("published/undisclosed")
                    .next(usedGroupIds);
            drafts.add(new DraftGroup(
                    groupId,
                    ResearchTreePresentation.UNDISCLOSED_TITLE,
                    Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                    Optional.empty(),
                    ResearchTreePresentation.Kind.UNDISCLOSED,
                    ResearchTreePresentation.Kind.UNDISCLOSED.includedInOverviewByDefault(),
                    depthMembers(undisclosed, depths)));
        }

        List<DraftGroup> normalizedDrafts = normalizeRanks(graph, drafts);

        List<ResearchTreePresentation.Group> published = new ArrayList<>(normalizedDrafts.size());
        for (int order = 0; order < normalizedDrafts.size(); order++) {
            DraftGroup draft = normalizedDrafts.get(order);
            published.add(new ResearchTreePresentation.Group(
                    draft.id(),
                    draft.title(),
                    draft.translationKey(),
                    draft.iconNodeId(),
                    order,
                    draft.kind(),
                    draft.includedInOverview(),
                    draft.members()));
        }
        return new ResearchTreePresentation(published);
    }

    /**
     * Authored, fallback, and undisclosed groups start with independently derived
     * ranks. Reconcile those ranks against the complete public DAG so every
     * prerequisite is always below its dependent, even when an edge crosses a
     * group or disclosure boundary.
     */
    private static List<DraftGroup> normalizeRanks(
            ResearchTreeGraph graph,
            List<DraftGroup> drafts) {
        Map<ResourceLocation, ResearchTreePresentation.Member> originalMemberships =
                new LinkedHashMap<>();
        for (DraftGroup draft : drafts) {
            for (ResearchTreePresentation.Member member : draft.members()) {
                if (originalMemberships.put(member.nodeId(), member) != null) {
                    throw new IllegalArgumentException(
                            "research presentation assigns one node to multiple groups");
                }
            }
        }

        Map<ResourceLocation, Integer> normalizedRanks = new LinkedHashMap<>();
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            normalizedRank(
                    node.blueprintId(),
                    graph,
                    originalMemberships,
                    normalizedRanks,
                    new LinkedHashSet<>());
        }

        List<DraftGroup> normalized = new ArrayList<>(drafts.size());
        for (DraftGroup draft : drafts) {
            List<ResearchTreePresentation.Member> ordered = new ArrayList<>(draft.members());
            ordered.sort(Comparator
                    .comparingInt((ResearchTreePresentation.Member member) ->
                            normalizedRanks.get(member.nodeId()))
                    .thenComparingInt(ResearchTreePresentation.Member::rank)
                    .thenComparingInt(ResearchTreePresentation.Member::orderInRank)
                    .thenComparing(member -> member.nodeId().toString()));
            Map<Integer, Integer> nextOrderByRank = new LinkedHashMap<>();
            List<ResearchTreePresentation.Member> members = new ArrayList<>(ordered.size());
            for (ResearchTreePresentation.Member member : ordered) {
                int rank = normalizedRanks.get(member.nodeId());
                int order = nextOrderByRank.getOrDefault(rank, 0);
                members.add(new ResearchTreePresentation.Member(member.nodeId(), rank, order));
                nextOrderByRank.put(rank, order + 1);
            }
            normalized.add(new DraftGroup(
                    draft.id(),
                    draft.title(),
                    draft.translationKey(),
                    draft.iconNodeId(),
                    draft.kind(),
                    draft.includedInOverview(),
                    members));
        }
        return normalized;
    }

    private static int normalizedRank(
            ResourceLocation nodeId,
            ResearchTreeGraph graph,
            Map<ResourceLocation, ResearchTreePresentation.Member> originalMemberships,
            Map<ResourceLocation, Integer> normalizedRanks,
            Set<ResourceLocation> visiting) {
        Integer known = normalizedRanks.get(nodeId);
        if (known != null) {
            return known;
        }
        ResearchTreePresentation.Member membership = originalMemberships.get(nodeId);
        if (membership == null) {
            throw new IllegalArgumentException(
                    "research presentation does not assign public node " + nodeId);
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("published research graph contains a cycle");
        }
        try {
            int rank = membership.rank();
            for (ResourceLocation prerequisiteId : graph.prerequisitesOf(nodeId)) {
                rank = Math.max(rank, normalizedRank(
                        prerequisiteId,
                        graph,
                        originalMemberships,
                        normalizedRanks,
                        visiting) + 1);
            }
            if (rank >= ResearchTreeGraph.MAX_NODES) {
                throw new IllegalArgumentException("published research ranks exceed their limit");
            }
            normalizedRanks.put(nodeId, rank);
            return rank;
        } finally {
            visiting.remove(nodeId);
        }
    }

    private static void validatePublicIds(
            ResearchTreeGraph graph,
            Map<ResourceLocation, ResourceLocation> publicIds) {
        if (publicIds.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("research presentation ID mapping contains null values");
        }
        Set<ResourceLocation> mappedIds = new LinkedHashSet<>(publicIds.values());
        Set<ResourceLocation> graphIds = new LinkedHashSet<>();
        graph.nodes().forEach(node -> graphIds.add(node.blueprintId()));
        if (mappedIds.size() != publicIds.size() || !mappedIds.equals(graphIds)) {
            throw new IllegalArgumentException(
                    "research presentation ID mapping does not match the public graph");
        }
    }

    private static DraftGroup authoredGroup(
            ResearchTreeGraph graph,
            BlueprintResearchSnapshot.GroupBinding binding,
            Map<ResourceLocation, ResourceLocation> publicIds,
            Map<Integer, Integer> publicAuthoredRanks,
            Set<ResourceLocation> assignedRealIds) {
        ResearchTreeGroupDefinition definition = binding.definition();
        List<AuthoredMember> visibleMembers = new ArrayList<>();
        for (int rank = 0; rank < definition.ranks().size(); rank++) {
            for (int sibling = 0; sibling < definition.ranks().get(rank).size(); sibling++) {
                ResourceLocation realId = definition.ranks().get(rank).get(sibling);
                ResourceLocation publicId = publicIds.get(realId);
                if (publicId == null) {
                    continue;
                }
                ResearchTreeGraph.Node node = graph.node(publicId).orElseThrow();
                if (node.visibility().revealsIdentity()) {
                    visibleMembers.add(new AuthoredMember(realId, publicId, rank));
                }
            }
        }
        if (visibleMembers.isEmpty()) {
            return null;
        }

        List<ResearchTreePresentation.Member> members = new ArrayList<>(visibleMembers.size());
        int currentSourceRank = Integer.MIN_VALUE;
        int orderInRank = 0;
        for (AuthoredMember member : visibleMembers) {
            if (member.sourceRank() != currentSourceRank) {
                currentSourceRank = member.sourceRank();
                orderInRank = 0;
            }
            members.add(new ResearchTreePresentation.Member(
                    member.publicId(),
                    publicAuthoredRanks.get(member.sourceRank()),
                    orderInRank++));
            assignedRealIds.add(member.realId());
        }

        ResourceLocation iconPublicId = publicIds.get(definition.icon());
        Optional<ResourceLocation> icon = Optional.ofNullable(iconPublicId)
                .filter(candidate -> members.stream().anyMatch(member -> member.nodeId().equals(candidate)))
                .filter(candidate -> graph.node(candidate).orElseThrow().visibility().revealsIcon());
        if (icon.isEmpty() && iconPublicId == null) {
            icon = visibleMembers.stream()
                    .map(AuthoredMember::publicId)
                    .filter(candidate -> graph.node(candidate).orElseThrow().visibility().revealsIcon())
                    .findFirst();
        }
        return new DraftGroup(
                binding.groupId(),
                definition.title(),
                definition.translationKey(),
                icon,
                ResearchTreePresentation.Kind.AUTHORED,
                definition.includeInOverview().orElseGet(
                        ResearchTreePresentation.Kind.AUTHORED::includedInOverviewByDefault),
                members);
    }

    private static Map<Integer, Integer> publicAuthoredRanks(
            ResearchTreeGraph graph,
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            Map<ResourceLocation, ResourceLocation> publicIds) {
        Set<Integer> retainedSourceRanks = new TreeSet<>();
        for (BlueprintResearchSnapshot.GroupBinding binding : snapshot.groupsForProfile(profileId)) {
            List<List<ResourceLocation>> ranks = binding.definition().ranks();
            for (int rank = 0; rank < ranks.size(); rank++) {
                for (ResourceLocation realId : ranks.get(rank)) {
                    ResourceLocation publicId = publicIds.get(realId);
                    if (publicId != null
                            && graph.node(publicId).orElseThrow().visibility().revealsIdentity()) {
                        retainedSourceRanks.add(rank);
                    }
                }
            }
        }
        Map<Integer, Integer> compactRanks = new LinkedHashMap<>();
        int publishedRank = 0;
        for (int sourceRank : retainedSourceRanks) {
            compactRanks.put(sourceRank, publishedRank++);
        }
        return compactRanks;
    }

    private static List<ResearchTreePresentation.Member> depthMembers(
            List<ResourceLocation> nodeIds,
            Map<ResourceLocation, Integer> depths) {
        List<ResourceLocation> ordered = new ArrayList<>(nodeIds);
        ordered.sort(Comparator
                .comparingInt((ResourceLocation id) -> depths.getOrDefault(id, 0))
                .thenComparing(ResourceLocation::toString));
        Map<Integer, Integer> nextOrderByRank = new LinkedHashMap<>();
        List<ResearchTreePresentation.Member> members = new ArrayList<>(ordered.size());
        for (ResourceLocation nodeId : ordered) {
            int rank = depths.getOrDefault(nodeId, 0);
            int order = nextOrderByRank.getOrDefault(rank, 0);
            members.add(new ResearchTreePresentation.Member(nodeId, rank, order));
            nextOrderByRank.put(rank, order + 1);
        }
        return members;
    }

    private static Map<ResourceLocation, Integer> publishedDepths(ResearchTreeGraph graph) {
        Map<ResourceLocation, Integer> depths = new LinkedHashMap<>();
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            publishedDepth(node.blueprintId(), graph, depths, new LinkedHashSet<>());
        }
        return depths;
    }

    private static int publishedDepth(
            ResourceLocation nodeId,
            ResearchTreeGraph graph,
            Map<ResourceLocation, Integer> depths,
            Set<ResourceLocation> visiting) {
        Integer known = depths.get(nodeId);
        if (known != null) {
            return known;
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("published research graph contains a cycle");
        }
        try {
            int depth = 0;
            for (ResourceLocation prerequisiteId : graph.prerequisitesOf(nodeId)) {
                depth = Math.max(depth, publishedDepth(prerequisiteId, graph, depths, visiting) + 1);
            }
            depths.put(nodeId, depth);
            return depth;
        } finally {
            visiting.remove(nodeId);
        }
    }

    private static String fallbackTitle(String itemType) {
        StringBuilder safe = new StringBuilder(itemType.length());
        itemType.codePoints().forEach(character -> {
            if (!Character.isISOControl(character)) {
                safe.appendCodePoint(character);
            }
        });
        String normalized = safe.toString().trim();
        if (normalized.isBlank()) {
            return "Other Weapons";
        }
        String first = normalized.substring(0, 1).toUpperCase(Locale.ROOT);
        return "Other: " + first + normalized.substring(1);
    }

    private record AuthoredMember(
            ResourceLocation realId,
            ResourceLocation publicId,
            int sourceRank) {
    }

    private record DraftGroup(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> iconNodeId,
            ResearchTreePresentation.Kind kind,
            boolean includedInOverview,
            List<ResearchTreePresentation.Member> members) {
    }

    private static final class SyntheticIdAllocator {
        private final String basePath;
        private int nextSuffix;

        private SyntheticIdAllocator(String basePath) {
            this.basePath = basePath;
        }

        private ResourceLocation next(Set<ResourceLocation> usedIds) {
            while (nextSuffix < ResearchTreeGraph.MAX_NODES) {
                int suffix = nextSuffix++;
                String path = suffix == 0 ? basePath : basePath + "/" + suffix;
                ResourceLocation candidate = new ResourceLocation(SYNTHETIC_NAMESPACE, path);
                if (!usedIds.contains(candidate)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("could not allocate a bounded synthetic research group ID");
        }
    }
}
