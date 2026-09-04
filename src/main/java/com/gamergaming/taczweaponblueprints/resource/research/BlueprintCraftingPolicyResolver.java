package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;

import net.minecraft.resources.ResourceLocation;

/** Builds and validates the complete independent crafting-policy projection. */
public final class BlueprintCraftingPolicyResolver {
    private BlueprintCraftingPolicyResolver() {
    }

    public static BlueprintCraftingPolicySnapshot resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            long automaticRevision,
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> automaticByTree,
            AutomaticWeaponEvidenceManager.Publication evidence,
            BlueprintAmmoAssociationManager.Publication associations,
            ResearchFeatureConfigSnapshot config) {
        if (research == null || catalog == null || automaticByTree == null
                || evidence == null || associations == null || config == null
                || catalogRevision <= 0L || researchRevision <= 0L
                || automaticRevision < 0L
                || !evidence.readyForCatalogRevision(catalogRevision)
                || associations.state()
                        != BlueprintAmmoAssociationManager.PublicationState.READY
                || associations.catalogRevision() != catalogRevision
                || !associations.snapshot().matches(
                        catalogRevision, associations.revision())) {
            throw new IllegalArgumentException(
                    "complete crafting policy inputs are invalid or stale");
        }

        BlueprintGunCraftingPolicyResolver.Resolution guns =
                BlueprintGunCraftingPolicyResolver.resolve(
                        research,
                        catalog,
                        catalogRevision,
                        researchRevision,
                        automaticRevision,
                        automaticByTree,
                        evidence.snapshot(),
                        config.automaticTierPercentiles());
        BlueprintAmmoCraftingPolicyResolver.Resolution ammo =
                BlueprintAmmoCraftingPolicyResolver.resolve(
                        research,
                        catalog,
                        catalogRevision,
                        researchRevision,
                        automaticRevision,
                        guns,
                        associations.snapshot(),
                        associations.revision());
        BlueprintAttachmentCraftingPolicyResolver.Resolution attachments =
                BlueprintAttachmentCraftingPolicyResolver.resolve(
                        research,
                        catalog,
                        catalogRevision,
                        researchRevision,
                        automaticRevision);

        Set<ResourceLocation> expectedCatalogIds = sortedIds(catalog.keySet());
        Set<ResourceLocation> resolvedCatalogIds = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        resolvedCatalogIds.addAll(guns.gunBlueprintIds());
        resolvedCatalogIds.addAll(ammo.ammoBlueprintIds());
        resolvedCatalogIds.addAll(attachments.attachmentBlueprintIds());
        if (!resolvedCatalogIds.equals(expectedCatalogIds)) {
            throw new IllegalStateException(
                    "crafting policy kind projections do not completely cover the catalog");
        }

        Map<ResourceLocation, Map<ResourceLocation, ResolvedBlueprintCraftingPolicy>> merged =
                new LinkedHashMap<>();
        research.profiles().keySet().stream()
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .forEach(profileId -> {
                    Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> profile =
                            new LinkedHashMap<>();
                    merge(profile, guns.policiesByProfile().get(profileId), profileId, "gun");
                    merge(profile, ammo.policiesByProfile().get(profileId), profileId, "ammo");
                    merge(
                            profile,
                            attachments.policiesByProfile().get(profileId),
                            profileId,
                            "attachment");
                    if (!profile.keySet().equals(expectedCatalogIds)) {
                        throw new IllegalStateException(
                                "crafting policy profile does not completely cover the catalog: "
                                        + profileId);
                    }
                    merged.put(profileId, Collections.unmodifiableMap(profile));
                });

        BlueprintCraftingPolicySnapshot base = BlueprintCraftingPolicySnapshot.create(
                catalogRevision,
                researchRevision,
                automaticRevision,
                expectedCatalogIds,
                merged);
        return BlueprintCraftingConfigOverlay.apply(
                base, catalog, associations.snapshot(), config.craftingPolicy());
    }

    private static void merge(
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> target,
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> source,
            ResourceLocation profileId,
            String kind) {
        if (source == null) {
            throw new IllegalStateException(
                    "crafting " + kind + " projection is missing profile " + profileId);
        }
        source.forEach((blueprintId, policy) -> {
            if (target.putIfAbsent(blueprintId, policy) != null) {
                throw new IllegalStateException(
                        "crafting kind projections overlap at " + blueprintId);
            }
        });
    }

    private static Set<ResourceLocation> sortedIds(Set<ResourceLocation> source) {
        TreeSet<ResourceLocation> result = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        source.forEach(id -> {
            if (id == null) {
                throw new IllegalArgumentException("blueprint catalog contains a null ID");
            }
            result.add(id);
        });
        return Collections.unmodifiableSet(result);
    }
}
