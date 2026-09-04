package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;

import net.minecraft.resources.ResourceLocation;

/** Representative upper-bound checks for crafting-policy rebuild and lookup cost. */
class CraftingPolicyPerformanceTest {
    private static final int CATALOG_SIZE = BlueprintCraftingPolicySnapshot.MAX_CATALOG_ENTRIES;
    private static final int PROFILE_COUNT = 16;
    private static final long CATALOG_REVISION = 11L;
    private static final long RESEARCH_REVISION = 13L;
    private static final long EVIDENCE_REVISION = 17L;
    private static final long ASSOCIATION_REVISION = 19L;

    @Test
    void largeCatalogAndMultipleProfilesRebuildAndRemainConstantTimeToQuery() {
        Map<ResourceLocation, BlueprintData> catalog = catalog();
        BlueprintResearchSnapshot research = research();
        AutomaticWeaponEvidenceManager.Publication evidence =
                new AutomaticWeaponEvidenceManager.Publication(
                        AutomaticWeaponEvidenceSnapshot.emptyForCatalog(CATALOG_REVISION),
                        EVIDENCE_REVISION,
                        AutomaticWeaponEvidenceManager.PublicationState.READY);
        BlueprintAmmoAssociationManager.Publication associations = associations(catalog.keySet());

        BlueprintCraftingPolicySnapshot snapshot = assertTimeout(
                Duration.ofSeconds(15),
                () -> BlueprintCraftingPolicyResolver.resolve(
                        research,
                        catalog,
                        CATALOG_REVISION,
                        RESEARCH_REVISION,
                        0L,
                        Map.of(),
                        evidence,
                        associations,
                        new BlueprintConfig().researchFeatureSnapshot()));

        assertEquals(PROFILE_COUNT, snapshot.policiesByProfile().size());
        assertEquals(CATALOG_SIZE, snapshot.catalogBlueprintIds().size());
        assertEquals(
                (long) PROFILE_COUNT * CATALOG_SIZE,
                snapshot.policiesByProfile().values().stream().mapToLong(Map::size).sum());

        List<ResourceLocation> blueprintIds = List.copyOf(snapshot.catalogBlueprintIds());
        List<ResourceLocation> profileIds = List.copyOf(snapshot.policiesByProfile().keySet());
        assertTimeout(Duration.ofSeconds(3), () -> {
            for (int index = 0; index < 200_000; index++) {
                assertTrue(snapshot.policy(
                        profileIds.get(index % profileIds.size()),
                        blueprintIds.get(index % blueprintIds.size())).isPresent());
            }
        });
    }

    private static Map<ResourceLocation, BlueprintData> catalog() {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        for (int index = 0; index < CATALOG_SIZE; index++) {
            ResourceLocation blueprintId = id("test:gun_" + index);
            result.put(blueprintId, new BlueprintData(
                    blueprintId.toString(),
                    "item.test.gun_" + index,
                    "item.test.blueprint.tooltip",
                    id("test:recipe/gun_" + index),
                    null,
                    "rifle",
                    id("tacz:rifle"),
                    BlueprintKind.GUN,
                    1));
        }
        return result;
    }

    private static BlueprintResearchSnapshot research() {
        Map<ResourceLocation, BlueprintResearchProfile> profiles = new LinkedHashMap<>();
        for (int index = 0; index < PROFILE_COUNT; index++) {
            profiles.put(id("test:profile_" + index), profile());
        }
        return BlueprintResearchSnapshot.create(Map.of(), profiles, Map.of());
    }

    private static BlueprintResearchProfile profile() {
        EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains =
                new EnumMap<>(Domain.class);
        for (Domain domain : Domain.values()) {
            domains.put(domain, BlueprintResearchProfile.DomainPolicy.ENABLED);
        }
        return new BlueprintResearchProfile(
                BlueprintResearchProfile.CURRENT_FORMAT,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                domains,
                List.of(),
                Map.of(),
                Optional.empty(),
                BlueprintReverseEngineeringPolicy.DEFAULT,
                BlueprintProgressionProfilePolicy.DEFAULT,
                BlueprintCraftingProfilePolicy.DEFAULT);
    }

    private static BlueprintAmmoAssociationManager.Publication associations(
            Set<ResourceLocation> guns) {
        Set<ResourceLocation> stableGuns = new LinkedHashSet<>(guns);
        Map<ResourceLocation, String> rejected = new LinkedHashMap<>();
        stableGuns.forEach(id -> rejected.put(id, "no_recipe_backed_ammo"));
        BlueprintAmmoAssociationSnapshot snapshot = new BlueprintAmmoAssociationSnapshot(
                CATALOG_REVISION,
                ASSOCIATION_REVISION,
                stableGuns,
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                rejected);
        return new BlueprintAmmoAssociationManager.Publication(
                snapshot,
                ASSOCIATION_REVISION,
                CATALOG_REVISION,
                BlueprintAmmoAssociationManager.PublicationState.READY);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
