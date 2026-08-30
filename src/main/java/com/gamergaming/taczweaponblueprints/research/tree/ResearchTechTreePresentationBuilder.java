package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.BandBasis;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.BandDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.BandMode;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreePlacementResolver;

import net.minecraft.resources.ResourceLocation;

/** Sanitizes authored Tech Tree data against one player's public graph. */
final class ResearchTechTreePresentationBuilder {
    private ResearchTechTreePresentationBuilder() {
    }

    static ResearchTechTreePresentation build(
            ResearchTreeGraph graph,
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            Map<ResourceLocation, BlueprintData> catalog,
            Map<ResourceLocation, ResourceLocation> publicIds,
            AutomaticWeaponPlacementCandidateSnapshot automaticCandidates) {
        return build(
                graph,
                snapshot,
                profileId,
                catalog,
                publicIds,
                automaticCandidates,
                null);
    }

    static ResearchTechTreePresentation build(
            ResearchTreeGraph graph,
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            Map<ResourceLocation, BlueprintData> catalog,
            Map<ResourceLocation, ResourceLocation> publicIds,
            AutomaticWeaponPlacementCandidateSnapshot automaticCandidates,
            AutomaticWeaponPrerequisitePlan automaticPrerequisites) {
        if (graph == null || snapshot == null || profileId == null
                || catalog == null || publicIds == null) {
            throw new IllegalArgumentException("Research Tech Tree publication inputs cannot be null");
        }
        BlueprintResearchProfile profile = snapshot.profiles().get(profileId);
        if (profile == null || profile.techTree().isEmpty() || graph.nodes().isEmpty()) {
            return ResearchTechTreePresentation.EMPTY;
        }
        ResourceLocation treeId = profile.techTree().orElseThrow();
        ResearchTechTreeDefinition definition = snapshot.techTrees().get(treeId);
        if (definition == null) {
            return ResearchTechTreePresentation.EMPTY;
        }

        Map<ResourceLocation, MemberDraft> drafts = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> mapping : publicIds.entrySet()) {
            ResourceLocation realId = mapping.getKey();
            ResourceLocation publicId = mapping.getValue();
            ResearchTreeGraph.Node node = graph.node(publicId).orElseThrow();
            if (!node.visibility().revealsIdentity()) {
                continue;
            }
            BlueprintData data = catalog.get(realId);
            if (data == null) {
                continue;
            }
            ResearchTechTreePlacementResolver.EffectiveSelection selection =
                    ResearchTechTreePlacementResolver.resolveWithAutomaticForProfile(
                            snapshot,
                            profileId,
                            treeId,
                            realId,
                            data,
                            automaticCandidates);
            var proposal = selection.automaticProposal();
            if (selection.base().placement().isEmpty() && proposal.isEmpty()) {
                continue;
            }
            Optional<ResearchTechTreePlacementResolver.Placement> placement =
                    selection.base().placement();
            Domain domain = placement
                    .map(ResearchTechTreePlacementResolver.Placement::domain)
                    .orElse(Domain.WEAPONS);
            ResourceLocation lane = placement
                    .map(ResearchTechTreePlacementResolver.Placement::lane)
                    .orElseGet(() -> definition.domain(Domain.WEAPONS)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Automatic weapon placement requires a Weapons domain"))
                            .fallbackLane());
            ProgressionCoordinate coordinate = proposal
                    .map(com.gamergaming.taczweaponblueprints.research.tree.automatic
                            .AutomaticWeaponPlacementProposal::progressionCoordinate)
                    .orElseGet(() -> placement.orElseThrow().progressionCoordinate());
            ResearchTechTreeContract.Tier legacyTier = proposal
                    .map(com.gamergaming.taczweaponblueprints.research.tree.automatic
                            .AutomaticWeaponPlacementProposal::position)
                    .map(ResearchTechTreeContract.ProgressionPosition::tier)
                    .orElseGet(() -> placement.orElseThrow().tier());
            drafts.put(publicId, new MemberDraft(
                    publicId,
                    domain,
                    lane,
                    coordinate,
                    proposal.isPresent()
                            ? PlacementOrigin.AUTOMATIC
                            : placement.orElseThrow().origin(),
                    placement.flatMap(ResearchTechTreePlacementResolver.Placement::rating),
                    proposal.map(com.gamergaming.taczweaponblueprints.research.tree.automatic
                            .AutomaticWeaponPlacementProposal::mechanicalScore),
                    legacyTier,
                    proposal.isEmpty() && placement.orElseThrow().explicitRank(),
                    proposal.isPresent()
                            ? automaticBranch(automaticPrerequisites, realId)
                            : Optional.empty()));
        }

        int resolvedLayerWidth = resolvedLayerWidth(
                definition, treeId, snapshot, catalog, automaticCandidates);
        Map<ResourceLocation, ProgressionCoordinate> normalized = normalizeRanks(
                graph, drafts, resolvedLayerWidth);
        BandPublication bandPublication = progressionBands(
                definition, drafts, normalized, graph, publicIds);
        Map<Domain, Map<ResourceLocation, List<ResearchTechTreePresentation.Member>>> placements =
                new LinkedHashMap<>();
        for (MemberDraft draft : drafts.values()) {
            ProgressionCoordinate coordinate = normalized.get(draft.nodeId());
            placements
                    .computeIfAbsent(draft.domain(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(draft.laneId(), ignored -> new ArrayList<>())
                    .add(new ResearchTechTreePresentation.Member(
                            draft.nodeId(),
                            coordinate.rank(),
                            coordinate.siblingOrder(),
                            bandPublication.memberBands().getOrDefault(
                                    draft.nodeId(), Optional.empty()),
                            draft.origin(),
                            draft.rating(),
                            draft.automaticBranch()));
        }

        List<ResearchTechTreePresentation.DomainView> domains = new ArrayList<>();
        for (ResearchTechTreeDefinition.DomainDefinition domainDefinition : definition.domains()) {
            Map<ResourceLocation, List<ResearchTechTreePresentation.Member>> domainPlacements =
                    placements.getOrDefault(domainDefinition.domain(), Map.of());
            List<ResearchTechTreePresentation.LaneView> lanes = new ArrayList<>();
            for (ResearchTechTreeDefinition.LaneDefinition laneDefinition : domainDefinition.lanes()) {
                List<ResearchTechTreePresentation.Member> members = new ArrayList<>(
                        domainPlacements.getOrDefault(laneDefinition.id(), List.of()));
                if (members.isEmpty()) {
                    continue;
                }
                members.sort(Comparator
                        .comparingInt(ResearchTechTreePresentation.Member::rank)
                        .thenComparingLong(ResearchTechTreePresentation.Member::siblingOrder)
                        .thenComparing(value -> value.nodeId().toString()));
                Set<ResourceLocation> memberIds = members.stream()
                        .map(ResearchTechTreePresentation.Member::nodeId)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                lanes.add(new ResearchTechTreePresentation.LaneView(
                        laneDefinition.id(),
                        laneDefinition.title(),
                        laneDefinition.translationKey(),
                        safeIcon(laneDefinition.icon(), memberIds, publicIds, graph)
                                .or(() -> firstSafeIcon(memberIds, graph)),
                        laneDefinition.order(),
                        members));
            }
            if (lanes.isEmpty()) {
                continue;
            }
            Set<ResourceLocation> domainMemberIds = lanes.stream()
                    .flatMap(lane -> lane.members().stream())
                    .map(ResearchTechTreePresentation.Member::nodeId)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            domains.add(new ResearchTechTreePresentation.DomainView(
                    domainDefinition.domain(),
                    domainDefinition.title(),
                    domainDefinition.translationKey(),
                    safeIcon(domainDefinition.icon(), domainMemberIds, publicIds, graph)
                            .or(() -> firstSafeIcon(domainMemberIds, graph)),
                    lanes));
        }
        if (domains.isEmpty()) {
            return ResearchTechTreePresentation.EMPTY;
        }
        Set<ResourceLocation> allMembers = domains.stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .map(ResearchTechTreePresentation.Member::nodeId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        ResearchTechTreePresentation presentation = new ResearchTechTreePresentation(
                Optional.of(treeId),
                definition.title(),
                definition.translationKey(),
                safeIcon(definition.icon(), allMembers, publicIds, graph)
                        .or(() -> firstSafeIcon(allMembers, graph)),
                definition.bandPolicy().mode() == BandMode.LEGACY
                        ? definition.tiers().stream()
                                .map(tier -> new ResearchTechTreePresentation.TierLabel(
                                        tier.tier(), tier.title(), tier.translationKey()))
                                .toList()
                        : List.of(),
                bandPublication.labels(),
                resolvedLayerWidth,
                domains);
        validatePrerequisiteOrder(graph, presentation);
        presentation.validateAgainst(graph);
        return presentation;
    }

    private static Optional<ResearchTechTreePresentation.AutomaticBranchPlacement>
            automaticBranch(
                    AutomaticWeaponPrerequisitePlan plan,
                    ResourceLocation blueprintId) {
        if (plan == null) {
            return Optional.empty();
        }
        return plan.branchCoordinateFor(blueprintId)
                .map(coordinate ->
                        new ResearchTechTreePresentation.AutomaticBranchPlacement(
                                coordinate.branchIndex(),
                                coordinate.rankIndex(),
                                coordinate.familyStartIndex(),
                                coordinate.transitionEndIndex()));
    }

    private static int resolvedLayerWidth(
            ResearchTechTreeDefinition definition,
            ResourceLocation treeId,
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            AutomaticWeaponPlacementCandidateSnapshot automaticCandidates) {
        if (definition.format() >= ResearchTechTreeDefinition.CURRENT_FORMAT
                && automaticCandidates != null
                && automaticCandidates.treeId().equals(treeId)
                && definition.layout().acceptsResolvedWidth(
                        automaticCandidates.policy().maxNodesPerRank())) {
            return automaticCandidates.policy().maxNodesPerRank();
        }
        int weaponPopulation = Math.toIntExact(catalog.entrySet().stream()
                .filter(entry -> entry.getValue().getKind()
                        == com.gamergaming.taczweaponblueprints.item.BlueprintKind.GUN)
                .map(entry -> ResearchTechTreePlacementResolver.resolve(
                        snapshot, treeId, entry.getKey(), entry.getValue()))
                .flatMap(selection -> selection.placement().stream())
                .filter(placement -> placement.origin().authored()
                        && placement.domain() == Domain.WEAPONS)
                .count());
        return ResearchTechTreeLayerWidthResolver.resolve(
                definition.layout(), weaponPopulation);
    }

    private static BandPublication progressionBands(
            ResearchTechTreeDefinition definition,
            Map<ResourceLocation, MemberDraft> drafts,
            Map<ResourceLocation, ProgressionCoordinate> normalized,
            ResearchTreeGraph graph,
            Map<ResourceLocation, ResourceLocation> publicIds) {
        return switch (definition.bandPolicy().mode()) {
            case LEGACY -> legacyBands(definition, drafts);
            case NONE -> BandPublication.NONE;
            case DYNAMIC -> dynamicBands(definition, normalized);
            case CONFIGURED -> configuredBands(
                    definition, drafts, normalized, graph, publicIds);
        };
    }

    private static BandPublication legacyBands(
            ResearchTechTreeDefinition definition,
            Map<ResourceLocation, MemberDraft> drafts) {
        Map<ResourceLocation, Optional<ResourceLocation>> assignments = new LinkedHashMap<>();
        drafts.forEach((nodeId, draft) -> assignments.put(
                nodeId,
                Optional.of(ResearchTechTreeContract.legacyBandId(draft.legacyTier()))));
        return new BandPublication(
                definition.tiers().stream()
                        .map(tier -> new ResearchTechTreePresentation.BandLabel(
                                ResearchTechTreeContract.legacyBandId(tier.tier()),
                                tier.title(),
                                tier.translationKey()))
                        .toList(),
                assignments);
    }

    private static BandPublication dynamicBands(
            ResearchTechTreeDefinition definition,
            Map<ResourceLocation, ProgressionCoordinate> normalized) {
        List<Integer> occupiedRanks = normalized.values().stream()
                .map(ProgressionCoordinate::rank)
                .distinct()
                .sorted()
                .toList();
        int ranksPerBand = Math.max(
                definition.bandPolicy().ranksPerBand(),
                divideRoundUp(
                        occupiedRanks.size(),
                        ResearchTechTreeDefinition.MAX_PRESENTATION_BANDS));
        Map<Integer, ResourceLocation> bandByRank = new LinkedHashMap<>();
        int bandCount = divideRoundUp(occupiedRanks.size(), ranksPerBand);
        List<ResearchTechTreePresentation.BandLabel> labels = new ArrayList<>(bandCount);
        for (int index = 0; index < bandCount; index++) {
            ResourceLocation id = new ResourceLocation(
                    "taczweaponblueprints", "dynamic_band/" + index);
            labels.add(new ResearchTechTreePresentation.BandLabel(
                    id,
                    "Band " + (index + 1),
                    Optional.empty()));
        }
        for (int rankIndex = 0; rankIndex < occupiedRanks.size(); rankIndex++) {
            bandByRank.put(
                    occupiedRanks.get(rankIndex),
                    labels.get(rankIndex / ranksPerBand).id());
        }
        Map<ResourceLocation, Optional<ResourceLocation>> assignments = new LinkedHashMap<>();
        normalized.forEach((nodeId, coordinate) -> assignments.put(
                nodeId,
                Optional.of(bandByRank.get(coordinate.rank()))));
        return new BandPublication(labels, assignments);
    }

    private static BandPublication configuredBands(
            ResearchTechTreeDefinition definition,
            Map<ResourceLocation, MemberDraft> drafts,
            Map<ResourceLocation, ProgressionCoordinate> normalized,
            ResearchTreeGraph graph,
            Map<ResourceLocation, ResourceLocation> publicIds) {
        Map<Integer, BandDefinition> bandByRank = new LinkedHashMap<>();
        List<Integer> occupiedRanks = normalized.values().stream()
                .map(ProgressionCoordinate::rank)
                .distinct()
                .sorted()
                .toList();
        if (definition.bandPolicy().basis() == BandBasis.RANK) {
            occupiedRanks.forEach(rank -> bandByRank.put(
                    rank,
                    configuredBandFor(definition.bandPolicy().definitions(), rank)));
        } else {
            if (drafts.values().stream().anyMatch(draft -> draft.mechanicalScore().isEmpty())) {
                // Score bands cannot truthfully classify authored members. Keep the
                // complete mixed graph continuous instead of fabricating evidence.
                return BandPublication.NONE;
            }
            for (int rank : occupiedRanks) {
                List<BandDefinition> memberBands = drafts.values().stream()
                        .filter(draft -> normalized.get(draft.nodeId()).rank() == rank)
                        .map(MemberDraft::mechanicalScore)
                        .map(Optional::orElseThrow)
                        .map(score -> configuredBandFor(
                                definition.bandPolicy().definitions(), score))
                        .toList();
                if (memberBands.stream().map(BandDefinition::id).distinct().count() != 1L) {
                    // Bands span complete visual ranks. A rank containing scores
                    // from multiple configured intervals has no truthful single
                    // label, so keep the graph continuous and bandless.
                    return BandPublication.NONE;
                }
                bandByRank.put(rank, memberBands.get(0));
            }
        }

        Map<ResourceLocation, Integer> definitionOrder = new LinkedHashMap<>();
        for (int index = 0; index < definition.bandPolicy().definitions().size(); index++) {
            definitionOrder.put(definition.bandPolicy().definitions().get(index).id(), index);
        }
        int previous = -1;
        for (int rank : occupiedRanks) {
            int index = definitionOrder.get(bandByRank.get(rank).id());
            if (index < previous) {
                // Dependency lifting can make score bands non-monotonic. A continuous
                // view is more truthful than reversing presentation bands.
                return BandPublication.NONE;
            }
            previous = index;
        }

        Set<ResourceLocation> usedIds = bandByRank.values().stream()
                .map(BandDefinition::id)
                .collect(java.util.stream.Collectors.toCollection(
                        java.util.LinkedHashSet::new));
        List<ResearchTechTreePresentation.BandLabel> labels = definition
                .bandPolicy().definitions().stream()
                .filter(band -> usedIds.contains(band.id()))
                .map(band -> bandLabel(
                        band,
                        safeIcon(
                                band.icon(),
                                bandByRank.entrySet().stream()
                                        .filter(entry -> entry.getValue().id().equals(band.id()))
                                        .map(Map.Entry::getKey)
                                        .flatMap(rank -> normalized.entrySet().stream()
                                                .filter(entry -> entry.getValue().rank()
                                                        == rank)
                                                .map(Map.Entry::getKey))
                                        .collect(java.util.stream.Collectors.toCollection(
                                                java.util.LinkedHashSet::new)),
                                publicIds,
                                graph)))
                .toList();
        Map<ResourceLocation, Optional<ResourceLocation>> assignments = new LinkedHashMap<>();
        normalized.forEach((nodeId, coordinate) -> assignments.put(
                nodeId,
                Optional.of(bandByRank.get(coordinate.rank()).id())));
        return new BandPublication(labels, assignments);
    }

    private static BandDefinition configuredBandFor(
            List<BandDefinition> definitions,
            int value) {
        return definitions.stream()
                .filter(definition -> definition.maximum()
                        .map(maximum -> value <= maximum)
                        .orElse(true))
                .findFirst()
                .orElseThrow();
    }

    private static ResearchTechTreePresentation.BandLabel bandLabel(
            BandDefinition definition,
            Optional<ResourceLocation> safeIcon) {
        return new ResearchTechTreePresentation.BandLabel(
                definition.id(),
                definition.title(),
                definition.translationKey(),
                definition.color(),
                safeIcon);
    }

    private static int divideRoundUp(int value, int divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static Optional<ResourceLocation> safeIcon(
            Optional<ResourceLocation> privateIcon,
            Set<ResourceLocation> allowedPublicIds,
            Map<ResourceLocation, ResourceLocation> publicIds,
            ResearchTreeGraph graph) {
        return privateIcon
                .map(publicIds::get)
                .filter(java.util.Objects::nonNull)
                .filter(allowedPublicIds::contains)
                .filter(id -> graph.node(id).orElseThrow().visibility().revealsIcon());
    }

    private static Optional<ResourceLocation> firstSafeIcon(
            Set<ResourceLocation> publicIds,
            ResearchTreeGraph graph) {
        return publicIds.stream()
                .filter(id -> graph.node(id).orElseThrow().visibility().revealsIcon())
                .findFirst();
    }

    private static void validatePrerequisiteOrder(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation) {
        Map<ResourceLocation, ResearchTechTreePresentation.Member> members = new LinkedHashMap<>();
        presentation.domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .forEach(member -> members.put(member.nodeId(), member));
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            ResearchTechTreePresentation.Member prerequisite = members.get(edge.prerequisiteId());
            ResearchTechTreePresentation.Member dependent = members.get(edge.dependentId());
            if (prerequisite == null || dependent == null) {
                continue;
            }
            if (!ResearchTechTreeContract.progressionTransitionAllowed(
                    prerequisite.position(), dependent.position())) {
                throw new IllegalArgumentException(
                        "Public Research Tech Tree placement contradicts prerequisite order");
            }
        }
    }

    private static Map<ResourceLocation, ProgressionCoordinate> normalizeRanks(
            ResearchTreeGraph graph,
            Map<ResourceLocation, MemberDraft> drafts,
            int capacity) {
        Map<ResourceLocation, ProgressionCoordinate> resolved = new LinkedHashMap<>();
        Set<ResourceLocation> visiting = new java.util.LinkedHashSet<>();
        for (ResourceLocation nodeId : drafts.keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            normalizeRank(nodeId, graph, drafts, resolved, visiting);
        }
        return boundMixedRankWidths(graph, drafts, resolved, capacity);
    }

    /** Final publication guard for profile-specific edges absent from generated plans. */
    private static Map<ResourceLocation, ProgressionCoordinate> boundMixedRankWidths(
            ResearchTreeGraph graph,
            Map<ResourceLocation, MemberDraft> drafts,
            Map<ResourceLocation, ProgressionCoordinate> normalized,
            int capacity) {
        Map<ResourceLocation, ProgressionCoordinate> result = new LinkedHashMap<>();
        Map<Integer, Integer> widths = new LinkedHashMap<>();
        drafts.values().stream()
                .filter(draft -> draft.origin() != PlacementOrigin.AUTOMATIC)
                .sorted(Comparator.comparing(draft -> draft.nodeId().toString()))
                .forEach(draft -> {
                    ProgressionCoordinate coordinate = normalized.get(draft.nodeId());
                    result.put(draft.nodeId(), coordinate);
                    if (draft.domain() == Domain.WEAPONS) {
                        widths.merge(coordinate.rank(), 1, Math::addExact);
                    }
                });
        List<MemberDraft> automatic = drafts.values().stream()
                .filter(draft -> draft.origin() == PlacementOrigin.AUTOMATIC)
                .sorted(Comparator
                        .comparingInt((MemberDraft draft) ->
                                normalized.get(draft.nodeId()).rank())
                        .thenComparingLong(draft ->
                                normalized.get(draft.nodeId()).siblingOrder())
                        .thenComparing(draft -> draft.nodeId().toString()))
                .toList();
        List<List<MemberDraft>> batches = automaticBatches(graph, automatic);
        for (List<MemberDraft> batch : batches) {
            int rank = batch.stream()
                    .map(MemberDraft::nodeId)
                    .map(normalized::get)
                    .mapToInt(ProgressionCoordinate::rank)
                    .max()
                    .orElseThrow();
            for (MemberDraft draft : batch) {
                for (ResourceLocation prerequisiteId : graph.prerequisitesOf(draft.nodeId())) {
                    ProgressionCoordinate prerequisite = result.get(prerequisiteId);
                    if (prerequisite != null) {
                        rank = Math.max(rank, Math.addExact(prerequisite.rank(), 1));
                    }
                }
            }
            while (Math.addExact(widths.getOrDefault(rank, 0), batch.size()) > capacity) {
                rank = Math.addExact(rank, 1);
            }
            if (rank > ResearchTechTreeContract.MAX_PROGRESSION_RANK) {
                throw new IllegalArgumentException(
                        "Public Research Tech Tree exceeds the supported rank range");
            }
            for (MemberDraft draft : batch) {
                result.put(
                        draft.nodeId(),
                        normalized.get(draft.nodeId()).withRank(rank));
            }
            widths.merge(rank, batch.size(), Math::addExact);
        }
        return Map.copyOf(result);
    }

    private static List<List<MemberDraft>> automaticBatches(
            ResearchTreeGraph graph,
            List<MemberDraft> automatic) {
        Map<Integer, List<MemberDraft>> byPlannedRank = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> stableIndexes = new LinkedHashMap<>();
        List<List<MemberDraft>> result = new ArrayList<>();
        for (int index = 0; index < automatic.size(); index++) {
            MemberDraft draft = automatic.get(index);
            stableIndexes.put(draft.nodeId(), index);
            if (draft.automaticBranch().isPresent()) {
                byPlannedRank.computeIfAbsent(
                        draft.automaticBranch().orElseThrow().rankIndex(),
                        ignored -> new ArrayList<>()).add(draft);
            } else {
                result.add(List.of(draft));
            }
        }
        byPlannedRank.values().forEach(batch -> {
            Set<ResourceLocation> ids = batch.stream()
                    .map(MemberDraft::nodeId)
                    .collect(java.util.stream.Collectors.toSet());
            boolean internalEdge = batch.stream().anyMatch(draft ->
                    graph.prerequisitesOf(draft.nodeId()).stream().anyMatch(ids::contains));
            if (internalEdge) {
                batch.forEach(draft -> result.add(List.of(draft)));
            } else {
                result.add(List.copyOf(batch));
            }
        });
        result.sort(Comparator
                .comparingInt((List<MemberDraft> batch) -> batch.stream()
                        .mapToInt(draft -> stableIndexes.get(draft.nodeId()))
                        .min().orElseThrow())
                .thenComparing(batch -> batch.get(0).nodeId().toString()));
        return List.copyOf(result);
    }

    private static ProgressionCoordinate normalizeRank(
            ResourceLocation nodeId,
            ResearchTreeGraph graph,
            Map<ResourceLocation, MemberDraft> drafts,
            Map<ResourceLocation, ProgressionCoordinate> resolved,
            Set<ResourceLocation> visiting) {
        ProgressionCoordinate existing = resolved.get(nodeId);
        if (existing != null) {
            return existing;
        }
        MemberDraft draft = drafts.get(nodeId);
        if (draft == null) {
            return null;
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("Public Research Tech Tree progression cycle");
        }
        try {
            int rank = draft.coordinate().rank();
            for (ResourceLocation prerequisiteId : graph.prerequisitesOf(nodeId)) {
                ProgressionCoordinate prerequisite = normalizeRank(
                        prerequisiteId, graph, drafts, resolved, visiting);
                if (prerequisite == null) {
                    continue;
                }
                if (draft.explicitRank()) {
                    if (!ResearchTechTreeContract.progressionTransitionAllowed(
                            prerequisite, draft.coordinate())) {
                        throw new IllegalArgumentException(
                                "Explicit public Research Tech Tree rank "
                                        + draft.coordinate().rank() + " for " + nodeId
                                        + " must be greater than prerequisite rank "
                                        + prerequisite.rank() + " for " + prerequisiteId);
                    }
                } else {
                    rank = Math.max(rank, Math.addExact(prerequisite.rank(), 1));
                }
            }
            ProgressionCoordinate coordinate = draft.coordinate().withRank(rank);
            resolved.put(nodeId, coordinate);
            return coordinate;
        } finally {
            visiting.remove(nodeId);
        }
    }

    private record MemberDraft(
            ResourceLocation nodeId,
            Domain domain,
            ResourceLocation laneId,
            ProgressionCoordinate coordinate,
            PlacementOrigin origin,
            Optional<ResearchTechTreeContract.WeaponRating> rating,
            Optional<Integer> mechanicalScore,
            ResearchTechTreeContract.Tier legacyTier,
            boolean explicitRank,
            Optional<ResearchTechTreePresentation.AutomaticBranchPlacement>
                    automaticBranch) {
    }

    private record BandPublication(
            List<ResearchTechTreePresentation.BandLabel> labels,
            Map<ResourceLocation, Optional<ResourceLocation>> memberBands) {
        private static final BandPublication NONE = new BandPublication(List.of(), Map.of());

        private BandPublication {
            labels = List.copyOf(labels);
            memberBands = Map.copyOf(memberBands);
        }
    }
}
