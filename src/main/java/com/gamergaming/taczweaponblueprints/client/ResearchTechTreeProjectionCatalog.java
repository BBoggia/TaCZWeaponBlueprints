package com.gamergaming.taczweaponblueprints.client;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;

import net.minecraft.resources.ResourceLocation;

/** Immutable index of all non-empty public Tech Tree domain projections. */
public final class ResearchTechTreeProjectionCatalog {
    public static final ResearchTechTreeProjectionCatalog EMPTY =
            new ResearchTechTreeProjectionCatalog(
                    ResearchTechTreePresentation.EMPTY,
                    List.of());

    private final ResearchTechTreePresentation presentation;
    private final List<ResearchTechTreeProjection> projections;
    private final Map<Domain, ResearchTechTreeProjection> byDomain;
    private final Map<ResourceLocation, Domain> domainByNode;
    private final ResearchTechTreeRelationshipIndex relationships;

    ResearchTechTreeProjectionCatalog(
            ResearchTechTreePresentation presentation,
            List<ResearchTechTreeProjection> projections) {
        if (presentation == null || projections == null
                || projections.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Research Tech Tree projection catalog");
        }
        this.presentation = presentation;
        this.projections = List.copyOf(projections);
        if (presentation.available() != !this.projections.isEmpty()
                || presentation.domains().size() != this.projections.size()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree projection catalog does not match its presentation");
        }

        EnumMap<Domain, ResearchTechTreeProjection> domains = new EnumMap<>(Domain.class);
        LinkedHashMap<ResourceLocation, Domain> nodes = new LinkedHashMap<>();
        for (int index = 0; index < this.projections.size(); index++) {
            ResearchTechTreeProjection projection = this.projections.get(index);
            ResearchTechTreePresentation.DomainView domainPresentation =
                    presentation.domains().get(index);
            if (!projection.presentation().equals(domainPresentation)
                    || domains.put(projection.domain(), projection) != null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree projection catalog has an invalid domain table");
            }
            for (ResourceLocation nodeId : projection.placements().keySet()) {
                if (nodes.put(nodeId, projection.domain()) != null) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree projection catalog assigns a node twice");
                }
            }
        }
        byDomain = Collections.unmodifiableMap(domains);
        domainByNode = Collections.unmodifiableMap(nodes);
        relationships = ResearchTechTreeRelationshipIndex.build(
                this.projections, domainByNode);
    }

    public ResearchTechTreePresentation presentation() {
        return presentation;
    }

    public boolean available() {
        return presentation.available();
    }

    public List<ResearchTechTreeProjection> projections() {
        return projections;
    }

    public List<Domain> domains() {
        return projections.stream().map(ResearchTechTreeProjection::domain).toList();
    }

    public Optional<ResearchTechTreeProjection> projection(Domain domain) {
        return Optional.ofNullable(domain == null ? null : byDomain.get(domain));
    }

    public Optional<Domain> domainOf(ResourceLocation nodeId) {
        return Optional.ofNullable(nodeId == null ? null : domainByNode.get(nodeId));
    }

    /** Truthful reciprocal navigation metadata derived from the public graph. */
    public ResearchTechTreeRelationshipIndex relationships() {
        return relationships;
    }

    public boolean hasSameTopology(ResearchTechTreeProjectionCatalog other) {
        if (other == null || projections.size() != other.projections.size()
                || !presentation.treeId().equals(other.presentation.treeId())) {
            return false;
        }
        for (int index = 0; index < projections.size(); index++) {
            if (!projections.get(index).hasSameTopology(other.projections.get(index))) {
                return false;
            }
        }
        return true;
    }

}
