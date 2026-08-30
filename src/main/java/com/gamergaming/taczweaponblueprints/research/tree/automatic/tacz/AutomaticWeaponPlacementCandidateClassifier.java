package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchAnalyzer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponRoleAnalyzer;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot.TechTreeEntryBinding;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchAutomaticPlacementProfile;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreePlacementResolver;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreePlacementResolver.Placement;

import net.minecraft.resources.ResourceLocation;

/** Pure non-authored weapon classifier used before any automatic placement is trusted. */
public final class AutomaticWeaponPlacementCandidateClassifier {
    private AutomaticWeaponPlacementCandidateClassifier() {
    }

    public static AutomaticWeaponCandidateClassification classify(
            BlueprintResearchSnapshot research,
            long researchRevision,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            AutomaticWeaponEvidenceSnapshot evidence,
            ResearchAutomaticPlacementProfile profile) {
        if (research == null || catalog == null || evidence == null || profile == null
                || researchRevision < 0L || catalogRevision < 0L
                || !evidence.matchesCatalogRevision(catalogRevision)) {
            throw new IllegalArgumentException("Automatic placement classification inputs are inconsistent");
        }
        if (!research.automaticPlacementProfileForTree(profile.tree())
                .filter(profile::equals).isPresent()) {
            throw new IllegalArgumentException(
                    "Automatic placement profile is not part of the supplied research snapshot");
        }
        if (catalog.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null
                        || !entry.getKey().toString().equals(entry.getValue().getBpId()))) {
            throw new IllegalArgumentException(
                    "Automatic placement catalog contains an invalid or mismatched entry");
        }

        List<Map.Entry<ResourceLocation, BlueprintData>> weapons = catalog.entrySet().stream()
                .filter(entry -> entry.getValue().getKind() == BlueprintKind.GUN)
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .toList();
        Set<String> authored = new LinkedHashSet<>();
        List<String> automaticCandidates = new ArrayList<>();
        for (Map.Entry<ResourceLocation, BlueprintData> weapon : weapons) {
            ResearchTechTreePlacementResolver.Selection selection =
                    ResearchTechTreePlacementResolver.resolve(
                            research, profile.tree(), weapon.getKey(), weapon.getValue());
            if (selection.placement().isEmpty()) {
                automaticCandidates.add(weapon.getKey().toString());
                continue;
            }
            Placement placement = selection.placement().orElseThrow();
            if (placement.origin().authored()) {
                authored.add(weapon.getKey().toString());
            } else if (placement.origin() == PlacementOrigin.LEGACY_FALLBACK) {
                validateGenuineFallback(research, placement);
                automaticCandidates.add(weapon.getKey().toString());
            } else {
                throw new IllegalStateException(
                        "Unexpected placement origin during automatic classification: "
                                + placement.origin());
            }
        }

        Map<String, String> excluded = new LinkedHashMap<>();
        Map<String, AutomaticWeaponPlacementProposal> eligible = new LinkedHashMap<>();
        List<String> scoreable = new ArrayList<>();
        AutomaticWeaponPlacementPlanner planner = new AutomaticWeaponPlacementPlanner();
        var tree = research.techTrees().get(profile.tree());
        boolean hasWeaponsDomain = tree != null && tree.domain(Domain.WEAPONS).isPresent();
        AutomaticWeaponPlacementPolicy basePlacementPolicy = profile.placementPolicy();
        for (String id : automaticCandidates) {
            if (!profile.mode().assignsPlacement()) {
                excluded.put(id, "mode_independent");
            } else if (!hasWeaponsDomain) {
                excluded.put(id, "tree_missing_weapons_domain");
            } else if (evidence.rejectedBlueprints().containsKey(id)) {
                publishConservativeProposalOrExclude(
                        id,
                        "evidence_rejected:" + evidence.rejectedBlueprints().get(id),
                        catalog,
                        profile,
                        basePlacementPolicy,
                        planner,
                        eligible,
                        excluded);
            } else if (!evidence.scoresByBlueprint().containsKey(id)) {
                publishConservativeProposalOrExclude(
                        id,
                        "missing_mechanical_score",
                        catalog,
                        profile,
                        basePlacementPolicy,
                        planner,
                        eligible,
                        excluded);
            } else {
                scoreable.add(id);
            }
        }

        AutomaticWeaponPlacementPlan plan = planner.plan(
                evidence.scoresByBlueprint(), scoreable, basePlacementPolicy);
        plan.rejectedCandidates().forEach((id, reason) ->
                excluded.put(id, "proposal_rejected:" + reason));
        plan.proposals().forEach((id, proposal) -> {
            if (proposal.reviewRequired()
                    && !profile.reviewHandling().assignsPlacement()) {
                excluded.put(id, "review_required:" + String.join(",", proposal.reviewReasons()));
            } else {
                eligible.put(id, proposal);
            }
        });

        Map<String, String> fallbackArchetypes = new LinkedHashMap<>();
        java.util.stream.Stream.concat(eligible.keySet().stream(), authored.stream()).forEach(id -> {
            ResourceLocation blueprintId = ResourceLocation.tryParse(id);
            BlueprintData data = blueprintId == null ? null : catalog.get(blueprintId);
            if (data == null) {
                throw new IllegalStateException(
                        "Eligible automatic weapon is absent from its classified catalog");
            }
            String itemType = data.getItemType();
            fallbackArchetypes.put(
                    id,
                    itemType == null || itemType.isBlank() ? "unknown" : itemType.trim());
        });
        var roleSignatures = new AutomaticWeaponRoleAnalyzer().analyze(
                eligible, evidence.scoresByBlueprint(), fallbackArchetypes);
        var authoredRoleSignatures = new AutomaticWeaponRoleAnalyzer().analyzeAuthored(
                authored, evidence.scoresByBlueprint(), fallbackArchetypes);
        int topologyWeaponCount = Math.addExact(authored.size(), eligible.size());
        AutomaticWeaponPlacementPolicy resolvedPolicy =
                AutomaticWeaponCandidatePositioner.resolvePolicy(
                        basePlacementPolicy,
                        topologyWeaponCount,
                        research.techTrees().get(profile.tree()));
        var branchModel = roleSignatures.isEmpty()
                ? com.gamergaming.taczweaponblueprints.research.tree.automatic
                        .AutomaticWeaponBranchModel.EMPTY
                : new AutomaticWeaponBranchAnalyzer().discover(
                        roleSignatures,
                        authoredRoleSignatures,
                        AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                                resolvedPolicy.maxNodesPerRank()));
        return new AutomaticWeaponCandidateClassification(
                profile.tree(),
                profile.mode(),
                basePlacementPolicy,
                catalogRevision,
                researchRevision,
                weapons.size(),
                eligible,
                roleSignatures,
                authoredRoleSignatures,
                branchModel,
                excluded,
                authored,
                Set.of());
    }

    private static void publishConservativeProposalOrExclude(
            String blueprintId,
            String reason,
            Map<ResourceLocation, BlueprintData> catalog,
            ResearchAutomaticPlacementProfile profile,
            AutomaticWeaponPlacementPolicy placementPolicy,
            AutomaticWeaponPlacementPlanner planner,
            Map<String, AutomaticWeaponPlacementProposal> eligible,
            Map<String, String> excluded) {
        if (!profile.reviewHandling().assignsPlacement()) {
            excluded.put(blueprintId, reason);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(blueprintId);
        BlueprintData data = id == null ? null : catalog.get(id);
        if (data == null) {
            excluded.put(blueprintId, reason);
            return;
        }
        eligible.put(blueprintId, planner.conservativeFallback(
                blueprintId,
                data.getItemType(),
                reason,
                placementPolicy));
    }

    private static void validateGenuineFallback(
            BlueprintResearchSnapshot snapshot,
            Placement placement) {
        TechTreeEntryBinding binding = snapshot.techTreeEntriesFor(placement.treeId()).stream()
                .filter(value -> value.bundleId().equals(placement.source().bundleId())
                        && value.entryIndex() == placement.source().entryIndex())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Resolved fallback source is absent from its research snapshot"));
        var target = binding.entry().target();
        if (!binding.entry().fallback()
                || binding.bundle().priority() != 0
                || target.selector().isEmpty()
                || !target.blueprints().isEmpty()
                || !target.tags().isEmpty()) {
            throw new IllegalStateException(
                    "Automatic placement candidate did not originate from a selector-only legacy fallback");
        }
    }
}
