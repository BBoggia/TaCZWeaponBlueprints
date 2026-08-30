package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Availability;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteOverlay;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;

import net.minecraft.resources.ResourceLocation;

/** Builds the per-player graph that future Research Bench tree views consume. */
public final class ResearchTreeBuilder {
    private ResearchTreeBuilder() {
    }

    public static ResearchTreeGraph build(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate) {
        return buildPublication(catalog, researchSnapshot, config, playerData, blockedPredicate).graph();
    }

    public static ResearchTreePublication buildPublication(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate) {
        return buildPublication(
                catalog,
                researchSnapshot,
                config,
                playerData,
                blockedPredicate,
                null,
                null);
    }

    public static ResearchTreePublication buildPublication(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            AutomaticWeaponPlacementCandidateSnapshot automaticCandidates) {
        return buildPublication(
                catalog,
                researchSnapshot,
                config,
                playerData,
                blockedPredicate,
                automaticCandidates,
                null);
    }

    public static ResearchTreePublication buildPublication(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            AutomaticWeaponPlacementCandidateSnapshot automaticCandidates,
            AutomaticWeaponPrerequisitePlan automaticPrerequisites) {
        return buildPublication(
                catalog,
                researchSnapshot,
                config,
                playerData,
                blockedPredicate,
                ignored -> false,
                automaticCandidates,
                automaticPrerequisites);
    }

    public static ResearchTreePublication buildPublication(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate,
            AutomaticWeaponPlacementCandidateSnapshot automaticCandidates,
            AutomaticWeaponPrerequisitePlan automaticPrerequisites) {
        if (catalog == null || researchSnapshot == null || config == null || playerData == null
                || !config.blueprintsEnabled() || !config.journalEnabled()) {
            return ResearchTreePublication.EMPTY;
        }
        if (catalog.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("research tree catalog cannot contain null entries");
        }

        Predicate<String> blocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        Predicate<ResourceLocation> exempt = progressionExemptPredicate == null
                ? ignored -> false
                : progressionExemptPredicate;
        List<Map.Entry<ResourceLocation, BlueprintData>> sortedCatalog = new ArrayList<>(catalog.entrySet());
        sortedCatalog.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));

        Map<ResourceLocation, Candidate> candidates = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BlueprintData> entry : sortedCatalog) {
            if (exempt.test(entry.getKey())) {
                continue;
            }
            BlueprintResearchPolicy policy = config.apply(BlueprintResearchPolicyResolver.resolve(
                    researchSnapshot,
                    catalog,
                    config.activeProfileId(),
                    entry.getKey(),
                    playerData,
                    blocked,
                    exempt));
            policy = AutomaticWeaponPrerequisiteOverlay.apply(
                    policy,
                    automaticPrerequisites,
                    playerData,
                    blocked,
                    config.maximumUndiscoveredVisibility().allowsServerSelection(),
                    catalog::containsKey,
                    exempt);
            if (!policy.journalEnabled()
                    || !policy.treeEnabled()
                    || policy.blocked()
                    || !policy.visibility().appearsInTree()) {
                continue;
            }
            candidates.put(entry.getKey(), new Candidate(entry.getValue(), policy));
        }

        Set<ResourceLocation> visibleIds = Set.copyOf(candidates.keySet());
        Map<ResourceLocation, ResourceLocation> publicIds = new LinkedHashMap<>();
        int publicOrdinal = 0;
        for (Map.Entry<ResourceLocation, Candidate> entry : candidates.entrySet()) {
            ResourceLocation publicId = entry.getKey();
            if (!entry.getValue().policy().visibility().revealsIdentity()) {
                int disambiguator = 0;
                do {
                    publicId = ResearchTreeGraph.redactedNodeId(publicOrdinal, disambiguator++);
                } while (visibleIds.contains(publicId) || publicIds.containsValue(publicId));
            }
            publicIds.put(
                    entry.getKey(),
                    publicId);
            publicOrdinal++;
        }
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>(candidates.size());
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Candidate> entry : candidates.entrySet()) {
            ResourceLocation blueprintId = entry.getKey();
            BlueprintData data = entry.getValue().data();
            BlueprintResearchPolicy policy = entry.getValue().policy();
            JournalVisibility visibility = policy.visibility();
            boolean showTopology = policy.researchEnabled();
            boolean showResearch = showTopology && visibility.revealsResearchSummary();
            int visiblePrerequisiteCount = 0;
            if (showTopology) {
                for (ResourceLocation prerequisite : policy.prerequisites()) {
                    if (visibleIds.contains(prerequisite)) {
                        edges.add(new ResearchTreeGraph.Edge(
                                publicIds.get(prerequisite), publicIds.get(blueprintId)));
                        visiblePrerequisiteCount++;
                    }
                }
            }
            Availability availability = !visibility.revealsResearchSummary()
                    ? Availability.REDACTED
                    : visibility.revealsExactPolicy()
                            ? availability(policy)
                            : Availability.PREVIEW;
            nodes.add(new ResearchTreeGraph.Node(
                    nodes.size(),
                    publicIds.get(blueprintId),
                    visibility.revealsName()
                            ? data.getNameKey()
                            : ResearchTreeGraph.REDACTED_NAME_KEY,
                    visibility.revealsIdentity()
                            ? data.getItemType()
                            : ResearchTreeGraph.REDACTED_ITEM_TYPE,
                    visibility.revealsIcon()
                            ? data.getDisplaySlotKey()
                            : ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                    visibility,
                    visibility.revealsExactPolicy() && policy.learned(),
                    visibility.revealsExactPolicy() && policy.discovered(),
                    visibility.revealsExactPolicy() && availability == Availability.AVAILABLE,
                    showResearch ? policy.researchCost().points() : 0,
                    showResearch ? policy.researchCost().ingredients().size() : 0,
                    showTopology ? visiblePrerequisiteCount : 0,
                    0,
                    availability));
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, edges);
        Map<ResourceLocation, ResourceLocation> legacyPublicIds = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Candidate> entry : candidates.entrySet()) {
            if (ResearchTechTreeContract.includesKind(
                    ResearchTechTreeContract.BrowseIntent.BRANCHES,
                    entry.getValue().data().getKind())) {
                legacyPublicIds.put(entry.getKey(), publicIds.get(entry.getKey()));
            }
        }
        ResearchTreeGraph legacyGraph = graph.inducedSubgraph(
                Set.copyOf(legacyPublicIds.values()));
        ResearchTreePresentation presentation = ResearchTreePresentationBuilder.build(
                legacyGraph,
                researchSnapshot,
                config.activeProfileId(),
                legacyPublicIds);
        ResearchTechTreePresentation techTree;
        try {
            if (automaticCandidates == null) {
                techTree = ResearchTechTreePresentationBuilder.build(
                        graph,
                        researchSnapshot,
                        config.activeProfileId(),
                        catalog,
                        publicIds,
                        null);
            } else {
                try {
                    techTree = ResearchTechTreePresentationBuilder.build(
                            graph,
                            researchSnapshot,
                            config.activeProfileId(),
                            catalog,
                            publicIds,
                            automaticCandidates,
                            automaticPrerequisites);
                } catch (IllegalArgumentException | IllegalStateException automaticException) {
                    com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn(
                            "Automatic placements could not be applied safely to the current public Research Tech Tree; "
                                    + "publishing the complete legacy-fallback presentation instead",
                            automaticException);
                    techTree = ResearchTechTreePresentationBuilder.build(
                            graph,
                            researchSnapshot,
                            config.activeProfileId(),
                            catalog,
                            publicIds,
                            null);
                }
            }
        } catch (IllegalArgumentException exception) {
            // Tech Tree data is an optional presentation overlay. A catalog-specific
            // selector conflict must not take the established Branches and All Weapons
            // publication down with it.
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn(
                    "Could not publish the selected Research Tech Tree; hiding that view",
                    exception);
            techTree = ResearchTechTreePresentation.EMPTY;
        }
        return new ResearchTreePublication(graph, presentation, techTree);
    }

    private static Availability availability(BlueprintResearchPolicy policy) {
        if (policy.learned()) {
            return Availability.LEARNED;
        }
        if (!policy.available() || !policy.playerDataAvailable()) {
            return Availability.CONTENT_UNAVAILABLE;
        }
        if (!policy.researchEnabled()) {
            return Availability.RESEARCH_DISABLED;
        }
        if (policy.researchCost().points() > policy.pointCap()) {
            return Availability.COST_ABOVE_CAP;
        }
        if (policy.requiresDiscovery() && !policy.discovered()) {
            return Availability.DISCOVERY_REQUIRED;
        }
        if (!policy.prerequisitesSatisfied()) {
            return Availability.PREREQUISITES_REQUIRED;
        }
        return Availability.AVAILABLE;
    }

    private record Candidate(BlueprintData data, BlueprintResearchPolicy policy) {
    }
}
