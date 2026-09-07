package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot.TechTreeEntryBinding;

import net.minecraft.resources.ResourceLocation;

/**
 * Resolves catalog-dependent Tech Tree selectors before a catalog/research pair
 * becomes live. Snapshot compilation already handles exact and expanded-tag
 * placements; this pass closes the selector boundary that requires BlueprintData.
 */
public final class ResearchTechTreeCatalogValidator {
    private ResearchTechTreeCatalogValidator() {
    }

    public static void validate(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog) {
        if (snapshot == null || catalog == null
                || catalog.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null
                                || !entry.getKey().toString().equals(
                                        entry.getValue().getBpId()))) {
            throw new IllegalArgumentException(
                    "Research Tech Tree catalog validation inputs are invalid");
        }
        if (catalog.isEmpty()) {
            return;
        }

        snapshot.techTrees().forEach((treeId, tree) -> {
            for (var branch : snapshot.authoredBranches(treeId)) {
                var definition = tree.branches().stream().filter(b -> b.id().equals(branch.id())).findFirst().orElseThrow();
                for (var id : branch.members()) {
                    var data = catalog.get(id);
                    if (data == null) throw new IllegalArgumentException("Tech Tree " + treeId + " branch " + branch.id() + " references missing blueprint " + id);
                    var domain = com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.forKind(data.getKind());
                    var placement = ResearchTechTreePlacementResolver.resolve(snapshot,treeId,id,data).placement();
                    if (domain != definition.domain() || placement.filter(p -> p.domain() != domain).isPresent()
                            || placement.isEmpty() && !(tree.usesAutomaticWeaponPlacement() && domain == com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.WEAPONS))
                        throw new IllegalArgumentException("Tech Tree " + treeId + " branch " + branch.id() + " member has incompatible or absent placement: " + id);
                }
            }
        });
        snapshot.profiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(profileEntry -> profileEntry.getValue().techTree().ifPresent(treeId ->
                        validateProfile(
                                snapshot,
                                catalog,
                                profileEntry.getKey(),
                                treeId)));
    }

    private static void validateProfile(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation treeId) {
        Map<ResearchTechTreePlacementResolver.Source, TechTreeEntryBinding> bindingsBySource =
                new LinkedHashMap<>();
        snapshot.techTreeEntriesFor(treeId).forEach(binding -> bindingsBySource.put(
                new ResearchTechTreePlacementResolver.Source(
                        binding.bundleId(), binding.entryIndex()),
                binding));
        Map<ResourceLocation, TechTreeEntryBinding> placements = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisites =
                new LinkedHashMap<>();
        catalog.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation blueprintId = entry.getKey();
                    BlueprintResearchPolicyDefinition policy =
                            BlueprintResearchPolicyResolver.definitionFor(
                                    snapshot, catalog, profileId, blueprintId);
                    prerequisites.put(blueprintId, policy.prerequisites());
                    ResearchTechTreePlacementResolver.resolve(
                                    snapshot, treeId, blueprintId, entry.getValue())
                            .placement()
                            .ifPresent(placement -> {
                                TechTreeEntryBinding binding =
                                        bindingsBySource.get(placement.source());
                                if (binding == null) {
                                    throw new IllegalStateException(
                                            "Catalog-resolved Research Tech Tree source is absent");
                                }
                                placements.put(blueprintId, binding);
                            });
                });
        ResearchTechTreeProgressionResolver.resolve(
                profileId, prerequisites, placements);
    }
}
