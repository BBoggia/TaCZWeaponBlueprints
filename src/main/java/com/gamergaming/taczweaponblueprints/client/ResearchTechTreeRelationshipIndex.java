package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, disclosure-safe index of every published cross-domain Tech Tree
 * prerequisite. Relationships are stored once in their authoritative
 * prerequisite-to-dependent direction and exposed as reciprocal navigation
 * targets from either endpoint.
 */
public final class ResearchTechTreeRelationshipIndex {
    public static final ResearchTechTreeRelationshipIndex EMPTY = new ResearchTechTreeRelationshipIndex(
            List.of(), Map.of(), Map.of());

    private static final Comparator<Relationship> RELATIONSHIP_ORDER = Comparator
            .comparingInt((Relationship value) -> value.prerequisiteDomain().ordinal())
            .thenComparing(value -> value.prerequisiteId().toString())
            .thenComparingInt(value -> value.dependentDomain().ordinal())
            .thenComparing(value -> value.dependentId().toString());
    private static final Comparator<NavigationTarget> NAVIGATION_ORDER = Comparator
            .comparingInt((NavigationTarget value) -> value.direction().ordinal())
            .thenComparingInt(value -> value.remoteDomain().ordinal())
            .thenComparing(value -> value.remoteNodeId().toString());

    private final List<Relationship> relationships;
    private final Map<Domain, List<Relationship>> byDomain;
    private final Map<NodeKey, List<NavigationTarget>> navigationByNode;

    private ResearchTechTreeRelationshipIndex(
            List<Relationship> relationships,
            Map<Domain, List<Relationship>> byDomain,
            Map<NodeKey, List<NavigationTarget>> navigationByNode) {
        this.relationships = relationships;
        this.byDomain = byDomain;
        this.navigationByNode = navigationByNode;
    }

    static ResearchTechTreeRelationshipIndex build(
            List<ResearchTechTreeProjection> projections,
            Map<ResourceLocation, Domain> domainsByNode) {
        if (projections == null || domainsByNode == null
                || projections.stream().anyMatch(java.util.Objects::isNull)
                || domainsByNode.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("invalid Research Tech Tree relationship inputs");
        }
        if (projections.isEmpty()) {
            return EMPTY;
        }

        LinkedHashMap<Relationship, Integer> publicationCounts = new LinkedHashMap<>();
        for (ResearchTechTreeProjection projection : projections) {
            for (ResearchTechTreeProjection.BoundaryLink link : projection.boundaryLinks()) {
                Domain localDomain = domainsByNode.get(link.localNodeId());
                Domain remoteDomain = domainsByNode.get(link.remoteNodeId());
                if (localDomain != projection.domain()
                        || remoteDomain != link.remoteDomain()) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree boundary references an unpublished endpoint");
                }
                Relationship relationship = link.direction()
                        == ResearchTechTreeProjection.Direction.REQUIREMENT
                                ? new Relationship(
                                        link.remoteNodeId(),
                                        link.remoteDomain(),
                                        link.localNodeId(),
                                        projection.domain())
                                : new Relationship(
                                        link.localNodeId(),
                                        projection.domain(),
                                        link.remoteNodeId(),
                                        link.remoteDomain());
                publicationCounts.merge(relationship, 1, Integer::sum);
            }
        }
        if (publicationCounts.values().stream().anyMatch(count -> count != 2)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree cross-domain relationship is not published reciprocally");
        }

        ArrayList<Relationship> ordered = new ArrayList<>(publicationCounts.keySet());
        ordered.sort(RELATIONSHIP_ORDER);
        EnumMap<Domain, List<Relationship>> byDomainDraft = new EnumMap<>(Domain.class);
        LinkedHashMap<NodeKey, List<NavigationTarget>> byNodeDraft = new LinkedHashMap<>();
        for (Relationship relationship : ordered) {
            byDomainDraft.computeIfAbsent(
                    relationship.prerequisiteDomain(), ignored -> new ArrayList<>())
                    .add(relationship);
            byDomainDraft.computeIfAbsent(
                    relationship.dependentDomain(), ignored -> new ArrayList<>())
                    .add(relationship);
            addNavigation(byNodeDraft, new NodeKey(
                    relationship.prerequisiteDomain(), relationship.prerequisiteId()),
                    new NavigationTarget(
                            relationship.dependentId(),
                            relationship.dependentDomain(),
                            ResearchTechTreeProjection.Direction.UNLOCK));
            addNavigation(byNodeDraft, new NodeKey(
                    relationship.dependentDomain(), relationship.dependentId()),
                    new NavigationTarget(
                            relationship.prerequisiteId(),
                            relationship.prerequisiteDomain(),
                            ResearchTechTreeProjection.Direction.REQUIREMENT));
        }

        EnumMap<Domain, List<Relationship>> frozenByDomain = new EnumMap<>(Domain.class);
        for (Domain domain : Domain.values()) {
            List<Relationship> values = new ArrayList<>(
                    byDomainDraft.getOrDefault(domain, List.of()));
            values.sort(RELATIONSHIP_ORDER);
            frozenByDomain.put(domain, List.copyOf(values));
        }
        LinkedHashMap<NodeKey, List<NavigationTarget>> frozenByNode = new LinkedHashMap<>();
        byNodeDraft.forEach((key, values) -> {
            ArrayList<NavigationTarget> sorted = new ArrayList<>(values);
            sorted.sort(NAVIGATION_ORDER);
            frozenByNode.put(key, List.copyOf(sorted));
        });
        return new ResearchTechTreeRelationshipIndex(
                List.copyOf(ordered),
                Collections.unmodifiableMap(frozenByDomain),
                Collections.unmodifiableMap(frozenByNode));
    }

    private static void addNavigation(
            Map<NodeKey, List<NavigationTarget>> targets,
            NodeKey local,
            NavigationTarget target) {
        List<NavigationTarget> values = targets.computeIfAbsent(
                local, ignored -> new ArrayList<>());
        if (values.contains(target)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree publishes a duplicate navigation target");
        }
        values.add(target);
    }

    public boolean isEmpty() {
        return relationships.isEmpty();
    }

    public List<Relationship> relationships() {
        return relationships;
    }

    public List<Relationship> relationships(Domain domain) {
        return domain == null ? List.of() : byDomain.getOrDefault(domain, List.of());
    }

    public List<NavigationTarget> navigationFrom(
            Domain localDomain,
            ResourceLocation localNodeId) {
        if (localDomain == null || localNodeId == null) {
            return List.of();
        }
        return navigationByNode.getOrDefault(
                new NodeKey(localDomain, localNodeId), List.of());
    }

    public List<NavigationTarget> requirementsOf(
            Domain localDomain,
            ResourceLocation localNodeId) {
        return navigationFrom(localDomain, localNodeId).stream()
                .filter(target -> target.direction()
                        == ResearchTechTreeProjection.Direction.REQUIREMENT)
                .toList();
    }

    public List<NavigationTarget> unlocksFrom(
            Domain localDomain,
            ResourceLocation localNodeId) {
        return navigationFrom(localDomain, localNodeId).stream()
                .filter(target -> target.direction()
                        == ResearchTechTreeProjection.Direction.UNLOCK)
                .toList();
    }

    public Optional<NavigationTarget> navigationTo(
            Domain localDomain,
            ResourceLocation localNodeId,
            Domain remoteDomain,
            ResourceLocation remoteNodeId) {
        if (remoteDomain == null || remoteNodeId == null) {
            return Optional.empty();
        }
        return navigationFrom(localDomain, localNodeId).stream()
                .filter(target -> target.remoteDomain() == remoteDomain
                        && target.remoteNodeId().equals(remoteNodeId))
                .findFirst();
    }

    public record Relationship(
            ResourceLocation prerequisiteId,
            Domain prerequisiteDomain,
            ResourceLocation dependentId,
            Domain dependentDomain) {
        public Relationship {
            if (prerequisiteId == null || prerequisiteDomain == null
                    || dependentId == null || dependentDomain == null
                    || prerequisiteId.equals(dependentId)
                    || prerequisiteDomain == dependentDomain) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree cross-domain relationship");
            }
        }
    }

    public record NavigationTarget(
            ResourceLocation remoteNodeId,
            Domain remoteDomain,
            ResearchTechTreeProjection.Direction direction) {
        public NavigationTarget {
            if (remoteNodeId == null || remoteDomain == null || direction == null) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree navigation target");
            }
        }
    }

    private record NodeKey(Domain domain, ResourceLocation nodeId) {
        private NodeKey {
            if (domain == null || nodeId == null) {
                throw new IllegalArgumentException("invalid Research Tech Tree node key");
            }
        }
    }
}
