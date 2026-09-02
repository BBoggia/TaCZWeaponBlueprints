package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeAuthoringReport;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeEconomyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeTopologyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteMotifAssessment;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteQualityAudit;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteDecision;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/** Creates a deterministic, author-friendly view of the live research catalog. */
public final class BlueprintResearchCatalogExporter {
    /**
     * Format 18 records explicit generated relationship shape and hybrid-route
     * aggregate evidence. The legacy prerequisite union remains for older tools.
     */
    public static final int CURRENT_FORMAT = 18;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BlueprintResearchCatalogExporter() {
    }

    public static String export(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        return export(snapshot, catalog, profileId, null);
    }

    public static String export(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics) {
        ResearchTechTreeAuthoringReport authoring =
                ResearchTechTreeAuthoringReport.create(
                        snapshot,
                        catalog,
                        profileId,
                        automaticDiagnostics,
                        AutomaticWeaponEvidenceSnapshot.EMPTY);
        return exportWithDiagnostics(
                snapshot,
                catalog,
                profileId,
                automaticDiagnostics,
                authoring,
                ResearchTechTreeTopologyAudit.Audit.EMPTY,
                ResearchTechTreeEconomyAudit.Audit.EMPTY,
                ResearchGroupedRouteQualityAudit.Audit.EMPTY,
                ResearchGroupedRouteMotifAssessment.Assessment.EMPTY);
    }

    public static String exportWithDiagnostics(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            ResearchTechTreeAuthoringReport authoringReport,
            ResearchTechTreeTopologyAudit.Audit topologyAudit,
            ResearchTechTreeEconomyAudit.Audit economyAudit) {
        return exportWithDiagnostics(
                snapshot,
                catalog,
                profileId,
                automaticDiagnostics,
                authoringReport,
                topologyAudit,
                economyAudit,
                ResearchGroupedRouteQualityAudit.Audit.EMPTY,
                ResearchGroupedRouteMotifAssessment.Assessment.EMPTY);
    }

    public static String exportWithDiagnostics(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            ResearchTechTreeAuthoringReport authoringReport,
            ResearchTechTreeTopologyAudit.Audit topologyAudit,
            ResearchTechTreeEconomyAudit.Audit economyAudit,
            ResearchGroupedRouteQualityAudit.Audit groupedRouteQualityAudit) {
        return exportWithDiagnostics(
                snapshot,
                catalog,
                profileId,
                automaticDiagnostics,
                authoringReport,
                topologyAudit,
                economyAudit,
                groupedRouteQualityAudit,
                ResearchGroupedRouteMotifAssessment.Assessment.EMPTY);
    }

    public static String exportWithDiagnostics(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            ResearchTechTreeAuthoringReport authoringReport,
            ResearchTechTreeTopologyAudit.Audit topologyAudit,
            ResearchTechTreeEconomyAudit.Audit economyAudit,
            ResearchGroupedRouteQualityAudit.Audit groupedRouteQualityAudit,
            ResearchGroupedRouteMotifAssessment.Assessment motifAssessment) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId cannot be null");
        }
        if (automaticDiagnostics != null
                && !automaticDiagnostics.profileId().equals(profileId)) {
            throw new IllegalArgumentException(
                    "automatic diagnostics do not match the exported profile");
        }
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        ResearchTechTreeTopologyAudit.Audit stableTopology = topologyAudit == null
                ? ResearchTechTreeTopologyAudit.Audit.EMPTY : topologyAudit;
        ResearchTechTreeEconomyAudit.Audit stableEconomy = economyAudit == null
                ? ResearchTechTreeEconomyAudit.Audit.EMPTY : economyAudit;
        ResearchGroupedRouteQualityAudit.Audit stableGroupedRouteQuality =
                groupedRouteQualityAudit == null
                        ? ResearchGroupedRouteQualityAudit.Audit.EMPTY
                        : groupedRouteQualityAudit;
        ResearchGroupedRouteMotifAssessment.Assessment stableMotifAssessment =
                motifAssessment == null
                        ? ResearchGroupedRouteMotifAssessment.Assessment.EMPTY
                        : motifAssessment;
        if (stableMotifAssessment.available()
                && (!stableGroupedRouteQuality.available()
                        || stableMotifAssessment.weaponNodeCount()
                                != stableGroupedRouteQuality.weaponNodeCount())) {
            throw new IllegalArgumentException(
                    "motif assessment does not match grouped-route quality evidence");
        }
        List<Map.Entry<ResourceLocation, BlueprintData>> entries = new ArrayList<>(stableCatalog.entrySet());
        entries.removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        entries.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        Set<ResourceLocation> catalogIds = entries.stream()
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ResourceLocation> catalogWeaponIds = entries.stream()
                .filter(entry -> entry.getValue().getKind() == BlueprintKind.GUN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (authoringReport == null
                || !authoringReport.profileId().equals(profileId)
                || !authoringReport.entries().keySet().equals(catalogWeaponIds)) {
            throw new IllegalArgumentException(
                    "authoring report does not match the exported catalog or profile");
        }
        BlueprintResearchProfile profile = stableSnapshot.profiles().get(profileId);
        if (automaticDiagnostics != null
                && (!automaticDiagnostics.entries().keySet().equals(catalogWeaponIds)
                        || (profile != null
                                && profile.techTree().isPresent()
                                && !profile.techTree().orElseThrow().equals(
                                        automaticDiagnostics.treeId())))) {
            throw new IllegalArgumentException(
                    "automatic diagnostics do not match the exported catalog or tree");
        }

        JsonObject root = new JsonObject();
        root.addProperty("format", CURRENT_FORMAT);
        root.addProperty("profile", profileId.toString());
        root.addProperty("catalog_size", entries.size());
        if (profile != null && profile.techTree().isPresent()) {
            ResourceLocation treeId = profile.techTree().orElseThrow();
            ResearchTechTreeDefinition tree = stableSnapshot.techTrees().get(treeId);
            if (tree != null) {
                root.add("tech_tree_presentation", exportPresentationPolicy(treeId, tree));
            }
        }
        root.add("topology_audit", exportTopology(stableTopology));
        root.add("economy_review", exportEconomy(stableEconomy));
        root.add(
                "grouped_route_quality",
                exportGroupedRouteQuality(stableGroupedRouteQuality));
        root.add(
                "grouped_route_motif_assessment",
                exportGroupedRouteMotifAssessment(stableMotifAssessment));
        if (automaticDiagnostics != null) {
            JsonObject automatic = new JsonObject();
            automatic.addProperty("tree", automaticDiagnostics.treeId().toString());
            automatic.addProperty(
                    "mode",
                    automaticDiagnostics.mode().name().toLowerCase(java.util.Locale.ROOT));
            automatic.addProperty(
                    "review_handling",
                    automaticDiagnostics.reviewHandling().serializedName());
            automatic.addProperty(
                    "layering_strategy",
                    automaticDiagnostics.layeringStrategy().name().toLowerCase(
                            java.util.Locale.ROOT));
            automatic.addProperty(
                    "prerequisite_strategy",
                    automaticDiagnostics.prerequisiteStrategy().serializedName());
            automatic.addProperty(
                    "max_generated_prerequisites",
                    automaticDiagnostics.maxGeneratedPrerequisites());
            automatic.addProperty("merge_interval", automaticDiagnostics.mergeInterval());
            automatic.addProperty("catalog_revision", automaticDiagnostics.catalogRevision());
            automatic.addProperty("research_revision", automaticDiagnostics.researchRevision());
            automatic.addProperty("catalog_weapon_count", automaticDiagnostics.catalogWeaponCount());
            automatic.addProperty(
                    "topology_weapon_count", automaticDiagnostics.topologyWeaponCount());
            automatic.addProperty(
                    "resolved_nodes_per_layer", automaticDiagnostics.resolvedNodesPerLayer());
            automatic.addProperty(
                    "authored_count",
                    automaticDiagnostics.count(AutomaticWeaponPlacementDiagnostics.State.AUTHORED));
            automatic.addProperty(
                    "automatic_count",
                    automaticDiagnostics.count(AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC));
            automatic.addProperty(
                    "excluded_automatic_count",
                    automaticDiagnostics.excludedAutomaticCount());
            automatic.addProperty(
                    "excluded_fallback_count",
                    automaticDiagnostics.excludedAutomaticCount());
            automatic.addProperty(
                    "unplaced_count",
                    automaticDiagnostics.count(AutomaticWeaponPlacementDiagnostics.State.UNPLACED));
            automatic.addProperty(
                    "planned_prerequisite_count",
                    automaticDiagnostics.generatedPrerequisiteCount());
            automatic.addProperty(
                    "planned_requirement_group_count",
                    automaticDiagnostics.generatedRequirementGroupCount());
            automatic.addProperty(
                    "planned_alternative_group_count",
                    automaticDiagnostics.generatedAlternativeGroupCount());
            automatic.addProperty(
                    "planned_alternative_route_decision_count",
                    automaticDiagnostics.generatedAlternativeRouteDecisionCount());
            automatic.addProperty(
                    "planned_mandatory_convergence_count",
                    automaticDiagnostics.generatedMandatoryConvergenceCount());
            automatic.addProperty(
                    "planned_mixed_requirement_count",
                    automaticDiagnostics.generatedMixedRequirementCount());
            automatic.add(
                    "branch_prerequisites",
                    exportBranchTopologySummary(
                            automaticDiagnostics.branchTopologySummary()));
            automatic.add(
                    "publication",
                    exportAutomaticPublicationSummary(
                            automaticDiagnostics.publicationSummary()));
            stableSnapshot.automaticPlacementProfileForTree(
                    automaticDiagnostics.treeId()).ifPresent(automaticProfile -> {
                        automatic.addProperty(
                                "layering_strategy",
                                automaticProfile.placementPolicy().layeringStrategy()
                                        .name().toLowerCase(java.util.Locale.ROOT));
                        automatic.addProperty(
                                "progression_band_count",
                                automaticProfile.progressionBands().size());
                        automatic.addProperty(
                                "foundation_count",
                                automaticProfile.foundationCount());
                        automatic.addProperty(
                                "scoring_model",
                                automaticProfile.scoringModel().serializedName());
                    });
            ResearchTechTreeDefinition automaticTree = stableSnapshot.techTrees().get(
                    automaticDiagnostics.treeId());
            if (automaticTree != null) {
                boolean treeOwned = automaticTree.format()
                        >= ResearchTechTreeDefinition.CURRENT_FORMAT;
                int capacity = treeOwned
                        ? automaticDiagnostics.resolvedNodesPerLayer()
                        : stableSnapshot.automaticPlacementProfileForTree(
                                automaticDiagnostics.treeId())
                                .map(ResearchAutomaticPlacementProfile::maxNodesPerRank)
                                .orElse(automaticTree.layout().maxNodesPerLayer());
                automatic.addProperty(
                        treeOwned ? "max_nodes_per_layer" : "max_nodes_per_rank",
                        capacity);
                automatic.addProperty(
                        "layer_capacity_source",
                        treeOwned ? "tree_layout" : "legacy_automatic_profile");
                if (treeOwned) {
                    automatic.addProperty(
                            "width_mode",
                            automaticTree.layout().widthMode().name().toLowerCase(
                                    java.util.Locale.ROOT));
                    automatic.addProperty(
                            "configured_min_nodes_per_layer",
                            automaticTree.layout().minNodesPerLayer());
                    automatic.addProperty(
                            "configured_max_nodes_per_layer",
                            automaticTree.layout().maxNodesPerLayer());
                }
            }
            root.add("automatic_placement", automatic);
        }
        List<BlueprintResearchSnapshot.GroupBinding> groups = stableSnapshot.groupsForProfile(profileId);
        root.addProperty("authored_group_count", groups.size());
        JsonArray exportedGroups = new JsonArray();
        for (BlueprintResearchSnapshot.GroupBinding binding : groups) {
            ResearchTreeGroupDefinition definition = binding.definition();
            JsonObject exportedGroup = new JsonObject();
            exportedGroup.addProperty("id", binding.groupId().toString());
            exportedGroup.addProperty("title", definition.title());
            definition.translationKey().ifPresent(value ->
                    exportedGroup.addProperty("translation_key", value));
            exportedGroup.addProperty("icon", definition.icon().toString());
            exportedGroup.addProperty("order", definition.order());
            exportedGroup.addProperty(
                    "include_in_overview",
                    definition.includeInOverview().orElse(true));
            exportedGroup.addProperty("rank_count", definition.ranks().size());
            exportedGroup.addProperty("member_count", definition.memberCount());
            JsonArray missingMembers = new JsonArray();
            definition.members().stream()
                    .filter(id -> !catalogIds.contains(id))
                    .forEach(id -> missingMembers.add(id.toString()));
            exportedGroup.add("missing_members", missingMembers);
            exportedGroups.add(exportedGroup);
        }
        root.add("groups", exportedGroups);
        JsonArray exportedEntries = new JsonArray();
        for (Map.Entry<ResourceLocation, BlueprintData> entry : entries) {
            ResourceLocation blueprintId = entry.getKey();
            BlueprintData data = entry.getValue();
            BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                    stableSnapshot,
                    stableCatalog,
                    profileId,
                    blueprintId);
            JsonObject exported = new JsonObject();
            exported.addProperty("blueprint", blueprintId.toString());
            exported.addProperty("blueprint_kind", data.getKind().serializedName());
            exported.addProperty("item_type", data.getItemType());
            if (data.getRecipeId() != null) {
                exported.addProperty("recipe", data.getRecipeId().toString());
            }
            definition.ruleId().ifPresent(id -> exported.addProperty("selected_rule", id.toString()));
            exported.addProperty("specificity", definition.specificity().name().toLowerCase(java.util.Locale.ROOT));
            exported.addProperty("visibility", definition.visibility().serializedName());
            exported.addProperty("tree_enabled", definition.treeEnabled());
            exported.addProperty("research_enabled", definition.researchEnabled());
            exported.addProperty("research_points", definition.researchCost().points());
            exported.addProperty("ingredient_types", definition.researchCost().ingredients().size());
            JsonArray prerequisites = new JsonArray();
            definition.prerequisites().forEach(id -> prerequisites.add(id.toString()));
            exported.add("prerequisites", prerequisites);
            JsonArray prerequisiteGroups = new JsonArray();
            definition.requirements().allOf().forEach(group -> {
                JsonObject exportedRequirementGroup = new JsonObject();
                JsonArray alternatives = new JsonArray();
                group.anyOf().forEach(id -> alternatives.add(id.toString()));
                exportedRequirementGroup.add("any_of", alternatives);
                prerequisiteGroups.add(exportedRequirementGroup);
            });
            exported.add("prerequisite_groups", prerequisiteGroups);
            stableSnapshot.placementFor(profileId, blueprintId).ifPresentOrElse(placement -> {
                ResearchTreeGroupDefinition group = stableSnapshot.groups()
                        .get(placement.groupId());
                exported.addProperty("presentation_source", "authored");
                exported.addProperty("research_group", placement.groupId().toString());
                if (group != null) {
                    exported.addProperty("research_group_title", group.title());
                    exported.addProperty("research_group_order", group.order());
                    exported.addProperty(
                            "research_group_included_in_overview",
                            group.includeInOverview().orElse(true));
                }
                exported.addProperty("research_rank", placement.rank());
                exported.addProperty("research_order_in_rank", placement.orderInRank());
            }, () -> {
                boolean legacyKind = ResearchTechTreeContract.includesKind(
                        ResearchTechTreeContract.BrowseIntent.BRANCHES,
                        data.getKind());
                exported.addProperty(
                        "presentation_source",
                        legacyKind ? "automatic_fallback" : "tech_tree_only");
                if (legacyKind) {
                    exported.addProperty("research_group_included_in_overview", false);
                }
            });
            if (profile != null && profile.techTree().isPresent()) {
                ResourceLocation treeId = profile.techTree().orElseThrow();
                ResearchTechTreePlacementResolver.resolve(
                                stableSnapshot, treeId, blueprintId, data)
                        .placement()
                        .ifPresent(placement -> {
                            exported.addProperty("tech_tree", treeId.toString());
                            exported.addProperty(
                                    "tech_domain", placement.domain().name().toLowerCase(
                                            java.util.Locale.ROOT));
                            exported.addProperty("tech_lane", placement.lane().toString());
                            exported.addProperty(
                                    "tech_tier", placement.tier().name().toLowerCase(
                                            java.util.Locale.ROOT));
                            exported.addProperty("tech_level", placement.level());
                            exported.addProperty("tech_order", placement.order());
                            exported.addProperty(
                                    "tech_placement_specificity",
                                    placement.specificity().name().toLowerCase(
                                            java.util.Locale.ROOT));
                        });
            }
            if (automaticDiagnostics != null) {
                automaticDiagnostics.entry(blueprintId).ifPresent(decision -> {
                    JsonObject automatic = new JsonObject();
                    automatic.addProperty("state", decision.state().serializedName());
                    decision.proposal().ifPresent(proposal -> {
                        automatic.addProperty("mechanical_score", proposal.mechanicalScore());
                        automatic.addProperty("confidence", proposal.confidence());
                        automatic.addProperty(
                                "tier",
                                proposal.position().tier().name().toLowerCase(
                                        java.util.Locale.ROOT));
                        automatic.addProperty("level", proposal.position().level());
                        automatic.addProperty(
                                "sibling_order", proposal.position().siblingOrder());
                        automatic.addProperty(
                                "rank", proposal.progressionCoordinate().rank());
                        proposal.progressionCoordinate().bandId().ifPresent(value ->
                                automatic.addProperty("band", value.toString()));
                        automatic.addProperty("formula_version", proposal.formulaVersion());
                        automatic.addProperty("reference_version", proposal.referenceVersion());
                        automatic.addProperty("placement_version", proposal.placementVersion());
                        JsonArray reviewReasons = new JsonArray();
                        proposal.reviewReasons().forEach(reviewReasons::add);
                        automatic.add("review_reasons", reviewReasons);
                    });
                    decision.generatedPrerequisite().ifPresent(value ->
                            automatic.addProperty(
                                    "planned_prerequisite", value.toString()));
                    JsonArray plannedPrerequisites = new JsonArray();
                    decision.generatedPrerequisites().forEach(value ->
                            plannedPrerequisites.add(value.toString()));
                    automatic.add("planned_prerequisites", plannedPrerequisites);
                    JsonArray plannedRequirementGroups = new JsonArray();
                    decision.generatedRequirements().allOf().forEach(requirement -> {
                        JsonObject group = new JsonObject();
                        JsonArray alternatives = new JsonArray();
                        requirement.anyOf().forEach(value ->
                                alternatives.add(value.toString()));
                        group.add("any_of", alternatives);
                        plannedRequirementGroups.add(group);
                    });
                    automatic.add(
                            "planned_prerequisite_groups",
                            plannedRequirementGroups);
                    decision.prerequisiteDecision().ifPresent(value ->
                            automatic.add(
                                    "prerequisite_decision",
                                    exportPrerequisiteDecision(value)));
                    decision.reason().ifPresent(value ->
                            automatic.addProperty("reason", value));
                    exported.add("automatic_placement", automatic);
                });
            }
            ResearchTechTreeAuthoringReport.Entry authoring =
                    authoringReport.entries().get(blueprintId);
            if (authoring != null) {
                exported.add("authoring", exportAuthoringEntry(authoring));
            }
            exportedEntries.add(exported);
        }
        root.add("entries", exportedEntries);
        return GSON.toJson(root) + System.lineSeparator();
    }

    private static JsonObject exportTopology(ResearchTechTreeTopologyAudit.Audit audit) {
        JsonObject result = new JsonObject();
        JsonArray domains = new JsonArray();
        for (ResearchTechTreeTopologyAudit.DomainAudit domain : audit.domains()) {
            JsonObject value = new JsonObject();
            value.addProperty("domain", domain.domain().name().toLowerCase(java.util.Locale.ROOT));
            value.addProperty("node_count", domain.nodeCount());
            value.addProperty("foundation_count", domain.rootIds().size());
            value.addProperty("weak_component_count", domain.componentCount());
            value.addProperty("reachable_node_count", domain.reachableNodeCount());
            value.addProperty("maximum_prerequisites", domain.maximumPrerequisiteCount());
            value.addProperty("maximum_dependents", domain.maximumDependentCount());
            value.addProperty("maximum_depth", domain.maximumDepth());
            value.addProperty("maximum_rank_population", domain.maximumRankPopulation());
            value.addProperty("empty_rank_count", domain.emptyRankCount());
            value.addProperty("and_merge_count", domain.mergeCount());
            value.addProperty("cross_branch_merge_count", domain.crossBranchMergeCount());
            value.addProperty("approximate_edge_crossings", domain.approximateEdgeCrossingCount());
            value.addProperty("total_edge_rank_span", domain.totalEdgeRankSpan());
            value.addProperty("maximum_edge_rank_span", domain.maximumEdgeRankSpan());
            value.addProperty("average_edge_rank_span", domain.averageEdgeRankSpan());
            value.addProperty("manual_count", domain.manualNodeCount());
            value.addProperty("automatic_count", domain.automaticNodeCount());
            value.addProperty("fallback_count", domain.fallbackNodeCount());
            value.addProperty("excluded_automatic_count", domain.excludedAutomaticCount());
            domains.add(value);
        }
        result.add("domains", domains);
        ResearchTechTreeTopologyAudit.ParentRetention retention = audit.parentRetention();
        JsonObject parentRetention = new JsonObject();
        parentRetention.addProperty("available", retention.available());
        parentRetention.addProperty("compared_node_count", retention.comparedNodeCount());
        parentRetention.addProperty("retained_parent_set_count", retention.retainedParentSetCount());
        parentRetention.addProperty("changed_parent_set_count", retention.changedParentSetCount());
        parentRetention.addProperty("added_node_count", retention.addedNodeCount());
        parentRetention.addProperty("removed_node_count", retention.removedNodeCount());
        parentRetention.addProperty("retention_basis_points", retention.retentionBasisPoints());
        JsonArray changed = new JsonArray();
        retention.changedNodeIds().forEach(id -> changed.add(id.toString()));
        parentRetention.add("changed_nodes", changed);
        result.add("parent_retention", parentRetention);
        return result;
    }

    private static JsonObject exportEconomy(ResearchTechTreeEconomyAudit.Audit audit) {
        JsonObject result = new JsonObject();
        result.addProperty("cost_authority", audit.costAuthority());
        result.addProperty("research_cost_mode", audit.researchCostMode().name());
        result.addProperty(
                "point_income_coverage_applicable",
                audit.pointCoverageApplicable());
        result.addProperty("automatic_cost_curve_enabled", audit.automaticCostCurveEnabled());
        result.addProperty(
                "maximum_finite_point_income",
                audit.pointIncome().maximumFinitePoints());
        result.addProperty(
                "renewable_point_source_count",
                audit.pointIncome().renewableDefinitionCount());
        JsonArray domains = new JsonArray();
        for (ResearchTechTreeEconomyAudit.DomainEconomy domain : audit.domains()) {
            JsonObject value = new JsonObject();
            value.addProperty("domain", domain.domain().name().toLowerCase(java.util.Locale.ROOT));
            value.addProperty("node_count", domain.nodeCount());
            value.addProperty("full_tree_cost", domain.fullTreeCost());
            value.addProperty("foundation_count", domain.foundationCount());
            value.addProperty("foundation_cost", domain.foundationCost());
            value.addProperty("leaf_count", domain.leafCount());
            value.addProperty("minimum_leaf_single_path_cost", domain.minimumLeafSinglePathCost());
            value.addProperty("maximum_leaf_single_path_cost", domain.maximumLeafSinglePathCost());
            value.addProperty(
                    "minimum_leaf_unlock_closure_cost",
                    domain.minimumLeafUnlockClosureCost());
            value.addProperty(
                    "maximum_leaf_unlock_closure_cost",
                    domain.maximumLeafUnlockClosureCost());
            value.addProperty("and_merge_count", domain.andMergeCount());
            value.addProperty(
                    "additional_merge_prerequisite_count",
                    domain.additionalMergePrerequisiteCount());
            value.addProperty(
                    "finite_income_coverage_basis_points",
                    domain.finiteIncomeCoverageBasisPoints());
            domains.add(value);
        }
        result.add("domains", domains);
        return result;
    }

    private static JsonObject exportGroupedRouteQuality(
            ResearchGroupedRouteQualityAudit.Audit audit) {
        JsonObject result = new JsonObject();
        result.addProperty("available", audit.available());
        result.addProperty("interpretation", audit.interpretation());
        result.addProperty("weapon_node_count", audit.weaponNodeCount());
        result.addProperty("automatic_target_count", audit.automaticTargetCount());
        result.addProperty(
                "matched_automatic_target_count",
                audit.matchedAutomaticTargetCount());
        result.addProperty(
                "unmatched_automatic_target_count",
                audit.unmatchedAutomaticTargetCount());
        result.addProperty("alternative_group_count", audit.alternativeGroupCount());
        result.addProperty(
                "effective_alternative_group_count",
                audit.effectiveAlternativeGroupCount());
        result.addProperty(
                "maximum_finite_point_income",
                audit.maximumFinitePointIncome());
        result.addProperty(
                "affordable_terminal_count",
                audit.affordableTerminalCount());
        result.addProperty(
                "unaffordable_terminal_count",
                audit.unaffordableTerminalCount());
        result.addProperty(
                "indeterminate_terminal_count",
                audit.indeterminateTerminalCount());
        result.addProperty("warning_occurrence_count", audit.warningOccurrenceCount());

        ResearchGroupedRouteQualityAudit.AlternativeEvidence alternatives =
                audit.alternatives();
        JsonObject alternativeEvidence = new JsonObject();
        alternativeEvidence.addProperty("group_count", alternatives.groupCount());
        alternativeEvidence.addProperty(
                "effective_group_count", alternatives.effectiveGroupCount());
        alternativeEvidence.addProperty(
                "dependent_alternative_pair_count",
                alternatives.dependentAlternativePairCount());
        alternativeEvidence.addProperty(
                "exact_route_cost_group_count",
                alternatives.exactRouteCostGroupCount());
        alternativeEvidence.addProperty(
                "zero_cost_imbalanced_group_count",
                alternatives.zeroCostImbalancedGroupCount());
        alternativeEvidence.add(
                "mandatory_ancestry_overlap_basis_points",
                exportDistribution(alternatives.mandatoryAncestryOverlapBasisPoints()));
        alternativeEvidence.add(
                "ancestry_divergence_basis_points",
                exportDistribution(alternatives.ancestryDivergenceBasisPoints()));
        alternativeEvidence.add(
                "route_cost_ratio_lower_bound_basis_points",
                exportDistribution(
                        alternatives.routeCostRatioLowerBoundBasisPoints()));
        alternativeEvidence.add(
                "route_cost_ratio_upper_bound_basis_points",
                exportDistribution(
                        alternatives.routeCostRatioUpperBoundBasisPoints()));
        result.add("alternatives", alternativeEvidence);
        result.add(
                "mandatory_ancestor_shares_basis_points",
                exportDistribution(audit.mandatoryAncestorSharesBasisPoints()));
        result.add(
                "single_route_chain_lengths",
                exportDistribution(audit.singleRouteChainLengths()));
        result.add(
                "branch_entry_redundancy",
                exportDistribution(audit.branchEntryRedundancy()));
        result.add(
                "branch_entry_ancestry_overlap_basis_points",
                exportDistribution(audit.branchEntryAncestryOverlapBasisPoints()));

        JsonArray phases = new JsonArray();
        for (ResearchGroupedRouteQualityAudit.PhaseSummary phase : audit.phases()) {
            JsonObject value = new JsonObject();
            value.addProperty("phase", phase.phase().serializedName());
            value.addProperty("target_count", phase.targetCount());
            value.addProperty("alternative_group_count", phase.alternativeGroupCount());
            value.addProperty(
                    "effective_alternative_group_count",
                    phase.effectiveAlternativeGroupCount());
            value.addProperty(
                    "same_family_alternative_group_count",
                    phase.sameFamilyAlternativeGroupCount());
            value.addProperty(
                    "cross_family_alternative_group_count",
                    phase.crossFamilyAlternativeGroupCount());
            value.addProperty(
                    "unclassified_alternative_group_count",
                    phase.unclassifiedAlternativeGroupCount());
            value.addProperty(
                    "alternative_density_basis_points",
                    phase.alternativeDensityBasisPoints());
            value.addProperty(
                    "same_family_density_basis_points",
                    phase.sameFamilyDensityBasisPoints());
            value.addProperty(
                    "cross_family_density_basis_points",
                    phase.crossFamilyDensityBasisPoints());
            value.add("parent_fan_out", exportDistribution(phase.parentFanOut()));
            value.add(
                    "mandatory_ancestor_shares_basis_points",
                    exportDistribution(phase.mandatoryAncestorSharesBasisPoints()));
            phases.add(value);
        }
        result.add("phases", phases);

        JsonArray branchEntries = new JsonArray();
        for (ResearchGroupedRouteQualityAudit.BranchEntrySummary branch
                : audit.branchEntries()) {
            JsonObject value = new JsonObject();
            value.addProperty("branch_index", branch.branchIndex());
            value.addProperty("target_count", branch.targetCount());
            value.addProperty("distinct_entrance_count", branch.distinctEntranceCount());
            value.addProperty("redundant_entrance_count", branch.redundantEntranceCount());
            value.addProperty("alternative_group_count", branch.alternativeGroupCount());
            value.addProperty(
                    "effective_alternative_group_count",
                    branch.effectiveAlternativeGroupCount());
            value.addProperty(
                    "mandatory_ancestry_overlap_basis_points",
                    branch.mandatoryAncestryOverlapBasisPoints());
            branchEntries.add(value);
        }
        result.add("branch_entries", branchEntries);

        JsonArray terminalRoutes = new JsonArray();
        for (ResearchGroupedRouteQualityAudit.TerminalRoute route
                : audit.terminalRoutes()) {
            JsonObject value = new JsonObject();
            value.addProperty("terminal", route.terminalId().toString());
            value.addProperty(
                    "minimum_route_lower_bound",
                    route.minimumRouteLowerBound());
            value.addProperty(
                    "minimum_route_upper_bound",
                    route.minimumRouteUpperBound());
            value.addProperty("exact", route.exact());
            value.addProperty("affordability", route.affordability().serializedName());
            terminalRoutes.add(value);
        }
        result.add("terminal_routes", terminalRoutes);

        JsonArray warnings = new JsonArray();
        for (ResearchGroupedRouteQualityAudit.Warning warning : audit.warnings()) {
            JsonObject value = new JsonObject();
            value.addProperty("code", warning.code().serializedName());
            value.addProperty("occurrence_count", warning.occurrenceCount());
            warnings.add(value);
        }
        result.add("warnings", warnings);
        return result;
    }

    private static JsonObject exportGroupedRouteMotifAssessment(
            ResearchGroupedRouteMotifAssessment.Assessment assessment) {
        JsonObject result = new JsonObject();
        result.addProperty("available", assessment.available());
        result.addProperty("contract", assessment.contract());
        result.addProperty("decision", assessment.decision().serializedName());
        result.addProperty("weapon_node_count", assessment.weaponNodeCount());
        result.addProperty(
                "ladder_p95_review_limit",
                assessment.ladderP95ReviewLimit());
        result.addProperty(
                "route_cost_ratio_p95_review_limit_basis_points",
                assessment.routeCostRatioP95ReviewLimitBasisPoints());
        result.addProperty(
                "decisive_signal_count",
                assessment.decisiveSignalCount());
        result.addProperty(
                "motif_prototype_recommended",
                assessment.motifPrototypeRecommended());

        JsonArray signals = new JsonArray();
        for (ResearchGroupedRouteMotifAssessment.Signal signal
                : assessment.signals()) {
            JsonObject value = new JsonObject();
            value.addProperty("code", signal.code().serializedName());
            value.addProperty("observed", signal.observed());
            value.addProperty("review_limit", signal.reviewLimit());
            value.addProperty("triggered", signal.triggered());
            value.addProperty("decision_relevant", signal.decisionRelevant());
            signals.add(value);
        }
        result.add("signals", signals);

        JsonArray motifs = new JsonArray();
        assessment.recommendedMotifs().stream()
                .map(ResearchGroupedRouteMotifAssessment.Motif::serializedName)
                .forEach(motifs::add);
        result.add("recommended_motifs", motifs);

        ResearchGroupedRouteMotifAssessment.VisualEvidence visual =
                assessment.visualEvidence();
        JsonObject visualEvidence = new JsonObject();
        visualEvidence.addProperty(
                "pre_junction_approximate_crossing_count",
                visual.preJunctionApproximateCrossingCount());
        visualEvidence.addProperty(
                "post_junction_measurement_available",
                visual.postJunctionMeasurementAvailable());
        visualEvidence.addProperty(
                "post_junction_crossing_count",
                visual.postJunctionCrossingCount());
        visualEvidence.addProperty(
                "manual_review_required",
                visual.manualReviewRequired());
        result.add("visual_evidence", visualEvidence);
        return result;
    }

    private static JsonObject exportDistribution(
            ResearchGroupedRouteQualityAudit.IntDistribution distribution) {
        JsonObject result = new JsonObject();
        result.addProperty("sample_count", distribution.sampleCount());
        result.addProperty("minimum", distribution.minimum());
        result.addProperty("median", distribution.median());
        result.addProperty("percentile_90", distribution.percentile90());
        result.addProperty("percentile_95", distribution.percentile95());
        result.addProperty("maximum", distribution.maximum());
        return result;
    }

    private static JsonObject exportDistribution(
            ResearchGroupedRouteQualityAudit.LongDistribution distribution) {
        JsonObject result = new JsonObject();
        result.addProperty("sample_count", distribution.sampleCount());
        result.addProperty("minimum", distribution.minimum());
        result.addProperty("median", distribution.median());
        result.addProperty("percentile_90", distribution.percentile90());
        result.addProperty("percentile_95", distribution.percentile95());
        result.addProperty("maximum", distribution.maximum());
        return result;
    }

    private static JsonObject exportAuthoringEntry(
            ResearchTechTreeAuthoringReport.Entry entry) {
        JsonObject result = new JsonObject();
        result.addProperty("state", entry.state());
        entry.mechanicalScore().ifPresent(value -> result.addProperty("mechanical_score", value));
        entry.capabilityScore().ifPresent(value -> result.addProperty("capability_v3_score", value));
        entry.capabilityConfidence().ifPresent(value ->
                result.addProperty("capability_v3_confidence", value));
        entry.capabilitySuggestedTier().ifPresent(value -> result.addProperty(
                "capability_v3_suggested_tier",
                value.name().toLowerCase(java.util.Locale.ROOT)));
        entry.authoredCapabilityTierDelta().ifPresent(value -> result.addProperty(
                "authored_capability_v3_tier_delta", value));
        entry.assignedRank().ifPresent(value -> result.addProperty("assigned_rank", value));
        entry.bandId().ifPresent(value -> result.addProperty("band", value.toString()));
        entry.similarityScore().ifPresent(value -> result.addProperty("similarity_score", value));
        result.addProperty("fan_out_penalty", entry.fanOutPenalty());
        result.addProperty("parent_choice_reason", entry.parentChoiceReason());
        result.addProperty("merge_reason", entry.mergeReason());
        entry.prerequisiteDecision().ifPresent(value -> result.add(
                "prerequisite_decision", exportPrerequisiteDecision(value)));
        JsonArray parents = new JsonArray();
        for (ResearchTechTreeAuthoringReport.ParentChoice parent : entry.parentChoices()) {
            JsonObject value = new JsonObject();
            value.addProperty("blueprint", parent.parentId().toString());
            parent.similarityScore().ifPresent(score -> value.addProperty("similarity_score", score));
            value.addProperty("dependent_load", parent.dependentLoad());
            value.addProperty("fan_out_penalty", parent.fanOutPenalty());
            value.addProperty("relationship", parent.relationship());
            parents.add(value);
        }
        result.add("parent_choices", parents);
        JsonArray reasons = new JsonArray();
        entry.reviewFallbackReasons().forEach(reasons::add);
        result.add("review_fallback_reasons", reasons);
        JsonArray capabilityWarnings = new JsonArray();
        entry.capabilityWarnings().forEach(capabilityWarnings::add);
        result.add("capability_v3_warnings", capabilityWarnings);
        return result;
    }

    private static JsonObject exportPrerequisiteDecision(
            AutomaticWeaponPrerequisiteDecision decision) {
        JsonObject result = new JsonObject();
        result.addProperty("strategy", decision.strategy().serializedName());
        result.addProperty(
                "requirement_shape",
                decision.generatedRequirementShape().serializedName());
        decision.branchIndex().ifPresent(value -> result.addProperty("branch", value));
        result.addProperty("rank_index", decision.rankIndex());
        decision.publishedRank().ifPresent(value ->
                result.addProperty("published_rank", value));
        result.addProperty("family_start_index", decision.familyStartIndex());
        result.addProperty("transition_end_index", decision.transitionEndIndex());
        result.addProperty("desired_parent_count", decision.desiredParentCount());
        result.addProperty(
                "second_parent_quota_basis_points",
                decision.secondParentQuotaBasisPoints());
        result.addProperty("second_parent_eligible", decision.secondParentEligible());
        result.addProperty(
                "selected_parent_count", decision.selectedParentRelations().size());
        result.addProperty("same_family_parent_count", decision.sameFamilyParentCount());
        result.addProperty("cross_family_parent_count", decision.crossFamilyParentCount());
        result.addProperty(
                "unclassified_parent_count", decision.unclassifiedParentCount());
        result.addProperty("depth_shortcut", decision.depthShortcut());
        result.addProperty("terminal_peer", decision.terminalPeer());
        JsonArray relationships = new JsonArray();
        decision.selectedParentRelations().forEach((parent, relation) -> {
            JsonObject value = new JsonObject();
            value.addProperty("blueprint", parent.toString());
            value.addProperty("relationship", relation.serializedName());
            relationships.add(value);
        });
        result.add("parent_relationships", relationships);
        decision.mergeRejection().ifPresent(rejection -> {
            JsonObject value = new JsonObject();
            value.addProperty("blueprint", rejection.parentId().toString());
            value.addProperty("reason", rejection.reason().serializedName());
            value.addProperty(
                    "existing_closure_cost", rejection.existingClosureCost());
            value.addProperty(
                    "candidate_closure_cost", rejection.candidateClosureCost());
            value.addProperty("union_closure_cost", rejection.unionClosureCost());
            value.addProperty(
                    "maximum_allowed_closure_cost",
                    rejection.maximumAllowedClosureCost());
            result.add("merge_rejection", value);
        });
        decision.alternativeRouteReview().ifPresent(review -> {
            JsonObject value = new JsonObject();
            value.addProperty("blueprint", review.parentId().toString());
            value.addProperty("outcome", review.outcome().serializedName());
            value.addProperty(
                    "existing_route_cost_lower_bound",
                    review.existingRouteCostLowerBound());
            value.addProperty(
                    "existing_route_cost_upper_bound",
                    review.existingRouteCostUpperBound());
            value.addProperty(
                    "candidate_route_cost_lower_bound",
                    review.candidateRouteCostLowerBound());
            value.addProperty(
                    "candidate_route_cost_upper_bound",
                    review.candidateRouteCostUpperBound());
            value.addProperty(
                    "route_cost_ratio_lower_bound_basis_points",
                    review.routeCostRatioLowerBoundBasisPoints());
            value.addProperty(
                    "route_cost_ratio_upper_bound_basis_points",
                    review.routeCostRatioUpperBoundBasisPoints());
            value.addProperty(
                    "mandatory_ancestry_overlap_basis_points",
                    review.mandatoryAncestryOverlapBasisPoints());
            value.addProperty(
                    "divergent_mandatory_node_count",
                    review.divergentMandatoryNodeCount());
            value.addProperty("exact", review.exact());
            result.add("alternative_route_review", value);
        });
        return result;
    }

    private static JsonObject exportBranchTopologySummary(
            AutomaticWeaponPlacementDiagnostics.BranchTopologySummary summary) {
        JsonObject result = new JsonObject();
        result.addProperty("available", summary.available());
        if (!summary.available()) {
            return result;
        }
        result.addProperty("family_start_index", summary.familyStartIndex());
        result.addProperty("transition_end_index", summary.transitionEndIndex());
        result.addProperty("branch_count", summary.branchCount());
        result.addProperty("foundation_node_count", summary.foundationNodeCount());
        result.addProperty("shared_trunk_node_count", summary.sharedTrunkNodeCount());
        result.addProperty("transition_node_count", summary.transitionNodeCount());
        result.addProperty("specialization_node_count", summary.specializationNodeCount());
        result.addProperty("same_family_edge_count", summary.sameFamilyEdgeCount());
        result.addProperty("cross_family_edge_count", summary.crossFamilyEdgeCount());
        result.addProperty("unclassified_edge_count", summary.unclassifiedEdgeCount());
        result.addProperty("same_family_merge_count", summary.sameFamilyMergeCount());
        result.addProperty("cross_family_merge_count", summary.crossFamilyMergeCount());
        result.addProperty(
                "same_family_multi_parent_set_count",
                summary.sameFamilyMergeCount());
        result.addProperty(
                "cross_family_multi_parent_set_count",
                summary.crossFamilyMergeCount());
        result.addProperty("depth_shortcut_count", summary.depthShortcutCount());
        result.addProperty("terminal_peer_count", summary.terminalPeerCount());
        result.addProperty(
                "closure_inflation_rejection_count",
                summary.closureInflationRejectionCount());
        result.addProperty(
                "closure_rejected_additional_parent_count",
                summary.closureInflationRejectionCount());
        result.addProperty(
                "alternative_route_review_count",
                summary.alternativeRouteReviewCount());
        result.addProperty(
                "accepted_alternative_route_count",
                summary.acceptedAlternativeRouteCount());
        result.addProperty(
                "rejected_alternative_route_cost_imbalance_count",
                summary.rejectedAlternativeRouteCostImbalanceCount());
        result.addProperty("maximum_fan_out", summary.maximumFanOut());
        return result;
    }

    private static JsonObject exportAutomaticPublicationSummary(
            AutomaticWeaponPlacementDiagnostics.PublicationSummary summary) {
        JsonObject result = new JsonObject();
        result.addProperty("applicable", summary.applicable());
        result.addProperty("candidate_count", summary.candidateCount());
        result.addProperty(
                "canonical_branch_coordinate_count",
                summary.canonicalBranchCoordinateCount());
        result.addProperty(
                "canonical_branch_coordinates_available",
                summary.canonicalBranchCoordinatesAvailable());
        result.addProperty(
                "canonical_branch_coordinates_complete",
                summary.canonicalBranchCoordinatesComplete());
        result.addProperty(
                "canonical_branch_coverage_basis_points",
                summary.canonicalBranchCoverageBasisPoints());
        result.addProperty(
                "prerequisite_decision_count",
                summary.prerequisiteDecisionCount());
        result.addProperty("published_rank_count", summary.publishedRankCount());
        result.addProperty(
                "published_rank_coverage_basis_points",
                summary.publishedRankCoverageBasisPoints());
        result.addProperty(
                "rank_reconciliation_complete",
                summary.rankReconciliationComplete());
        result.addProperty(
                "unexpected_parentless_candidate_count",
                summary.unexpectedParentlessCandidateCount());
        result.addProperty(
                "connected_topology_complete",
                summary.connectedTopologyComplete());
        result.addProperty("complete", summary.complete());
        return result;
    }

    private static JsonObject exportPresentationPolicy(
            ResourceLocation treeId,
            ResearchTechTreeDefinition tree) {
        JsonObject presentation = new JsonObject();
        presentation.addProperty("tree", treeId.toString());
        presentation.addProperty(
                "width_mode",
                tree.layout().widthMode().name().toLowerCase(java.util.Locale.ROOT));
        presentation.addProperty(
                "min_nodes_per_layer", tree.layout().minNodesPerLayer());
        presentation.addProperty(
                "max_nodes_per_layer", tree.layout().maxNodesPerLayer());
        presentation.addProperty(
                "band_mode",
                tree.bandPolicy().mode().name().toLowerCase(java.util.Locale.ROOT));
        switch (tree.bandPolicy().mode()) {
            case LEGACY -> presentation.addProperty("legacy_tier_count", tree.tiers().size());
            case DYNAMIC -> presentation.addProperty(
                    "ranks_per_band", tree.bandPolicy().ranksPerBand());
            case CONFIGURED -> {
                presentation.addProperty(
                        "basis",
                        tree.bandPolicy().basis().name().toLowerCase(java.util.Locale.ROOT));
                JsonArray bands = new JsonArray();
                for (ResearchTechTreeDefinition.BandDefinition definition
                        : tree.bandPolicy().definitions()) {
                    JsonObject band = new JsonObject();
                    band.addProperty("id", definition.id().toString());
                    band.addProperty("title", definition.title());
                    definition.translationKey().ifPresent(value ->
                            band.addProperty("translation_key", value));
                    definition.color().ifPresent(value -> band.addProperty("color", value));
                    definition.icon().ifPresent(value ->
                            band.addProperty("icon", value.toString()));
                    definition.maximum().ifPresent(value ->
                            band.addProperty("maximum", value));
                    bands.add(band);
                }
                presentation.add("bands", bands);
            }
            case NONE -> {
                // The explicit mode remains the complete band policy in format 9 and later.
            }
        }
        return presentation;
    }
}
