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
     * Format 12 distinguishes planned rank ordinals from finalized publication
     * ranks. Legacy tier/level diagnostic fields remain for compatibility.
     */
    public static final int CURRENT_FORMAT = 12;
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
                ResearchTechTreeEconomyAudit.Audit.EMPTY);
    }

    public static String exportWithDiagnostics(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            ResearchTechTreeAuthoringReport authoringReport,
            ResearchTechTreeTopologyAudit.Audit topologyAudit,
            ResearchTechTreeEconomyAudit.Audit economyAudit) {
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

    private static JsonObject exportAuthoringEntry(
            ResearchTechTreeAuthoringReport.Entry entry) {
        JsonObject result = new JsonObject();
        result.addProperty("state", entry.state());
        entry.mechanicalScore().ifPresent(value -> result.addProperty("mechanical_score", value));
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
        return result;
    }

    private static JsonObject exportPrerequisiteDecision(
            AutomaticWeaponPrerequisiteDecision decision) {
        JsonObject result = new JsonObject();
        result.addProperty("strategy", decision.strategy().serializedName());
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
        result.addProperty("depth_shortcut_count", summary.depthShortcutCount());
        result.addProperty("terminal_peer_count", summary.terminalPeerCount());
        result.addProperty(
                "closure_inflation_rejection_count",
                summary.closureInflationRejectionCount());
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
