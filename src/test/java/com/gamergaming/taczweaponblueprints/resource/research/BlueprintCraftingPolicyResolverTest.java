package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.AmmoCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.AttachmentCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.CraftingAccessOverride;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;

import net.minecraft.resources.ResourceLocation;

class BlueprintCraftingPolicyResolverTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation GUN = id("test:gun");
    private static final ResourceLocation AMMO = id("test:ammo");
    private static final ResourceLocation ATTACHMENT = id("test:scope");
    private static final long CATALOG_REVISION = 4L;
    private static final long RESEARCH_REVISION = 7L;
    private static final long EVIDENCE_REVISION = 3L;
    private static final long ASSOCIATION_REVISION = 5L;

    @Test
    void aggregateResolverPublishesOnePolicyForEveryCatalogKind() {
        BlueprintCraftingPolicySnapshot result = resolve(
                evidence(AutomaticWeaponEvidenceManager.PublicationState.READY),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY));

        assertEquals(catalog().keySet(), result.catalogBlueprintIds());
        assertEquals(catalog().keySet(), result.policiesByProfile().get(PROFILE).keySet());
        assertEquals(3, result.diagnosticsByProfile().get(PROFILE).assignedCount());
        assertTrue(result.policy(PROFILE, GUN).isPresent());
        assertEquals(BlueprintCraftingPolicySource.LINKED_WEAPON,
                result.policy(PROFILE, AMMO).orElseThrow().source());
        assertEquals(BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                result.policy(PROFILE, ATTACHMENT).orElseThrow().source());
    }

    @Test
    void aggregateResolverRejectsInvalidatedEvidenceAndAssociations() {
        assertThrows(IllegalArgumentException.class, () -> resolve(
                evidence(AutomaticWeaponEvidenceManager.PublicationState.INVALIDATED),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY)));
        assertThrows(IllegalArgumentException.class, () -> resolve(
                evidence(AutomaticWeaponEvidenceManager.PublicationState.READY),
                associations(BlueprintAmmoAssociationManager.PublicationState.INVALIDATED)));
    }

    @Test
    void configExactAndSelectorsOverrideCategoryStrategiesDeterministically() {
        BlueprintConfig config = new BlueprintConfig();
        config.ammoCraftingStrategy.validateAndSet(AmmoCraftingStrategy.DISABLED);
        config.attachmentCraftingStrategy.validateAndSet(
                AttachmentCraftingStrategy.DISABLED);
        config.craftingDisabledKinds.validateAndSet(List.of("gun"));
        config.craftingUnrestrictedItemTypes.validateAndSet(List.of("ammo"));
        config.exactCraftingOverrides.validateAndSet(Map.of(
                GUN.toString(), CraftingAccessOverride.TIER_3));
        config.update(4);

        BlueprintCraftingPolicySnapshot result = resolve(
                evidence(AutomaticWeaponEvidenceManager.PublicationState.READY),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY),
                config);

        assertEquals(ResearchWorkbenchTier.TIER_3,
                result.policy(PROFILE, GUN).orElseThrow()
                        .requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED,
                result.policy(PROFILE, AMMO).orElseThrow().disposition());
        assertEquals(BlueprintCraftingDisposition.DISABLED,
                result.policy(PROFILE, ATTACHMENT).orElseThrow().disposition());
        assertEquals(BlueprintCraftingPolicySource.CONFIG_OVERRIDE,
                result.policy(PROFILE, GUN).orElseThrow().source());
    }

    @Test
    void configuredLinkedAmmoUsesTheFinalOverriddenGunTier() {
        BlueprintConfig config = new BlueprintConfig();
        config.ammoCraftingStrategy.validateAndSet(AmmoCraftingStrategy.LINKED_WEAPON);
        config.exactCraftingOverrides.validateAndSet(Map.of(
                GUN.toString(), CraftingAccessOverride.TIER_3));
        config.update(4);

        BlueprintCraftingPolicySnapshot result = resolve(
                evidence(AutomaticWeaponEvidenceManager.PublicationState.READY),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY),
                config);

        assertEquals(ResearchWorkbenchTier.TIER_3,
                result.policy(PROFILE, AMMO).orElseThrow()
                        .requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.CONFIG_OVERRIDE,
                result.policy(PROFILE, AMMO).orElseThrow().source());
    }

    @Test
    void failedCraftingProjectionPreservesThePreviousAggregatePublication() {
        BlueprintProgressionPolicyManager manager = new BlueprintProgressionPolicyManager();
        BlueprintConfig config = new BlueprintConfig();
        var automatic = AutomaticWeaponPlacementCandidateManager.INSTANCE.publication();
        assertTrue(manager.rebuild(
                research(),
                RESEARCH_REVISION,
                catalog(),
                CATALOG_REVISION,
                automatic,
                evidence(AutomaticWeaponEvidenceManager.PublicationState.READY),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY),
                config.researchFeatureSnapshot()));
        BlueprintProgressionPolicyManager.Publication valid = manager.publication();

        assertFalse(manager.rebuild(
                research(),
                RESEARCH_REVISION,
                catalog(),
                CATALOG_REVISION,
                automatic,
                evidence(AutomaticWeaponEvidenceManager.PublicationState.INVALIDATED),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY),
                config.researchFeatureSnapshot()));

        assertSame(valid, manager.publication());
        assertEquals(catalog().keySet(),
                manager.publication().craftingSnapshot().catalogBlueprintIds());
        assertEquals(EVIDENCE_REVISION, valid.identity().evidenceRevision());
        assertEquals(ASSOCIATION_REVISION,
                valid.identity().ammoAssociationRevision());
    }

    @Test
    void profileReplacementPublishesOnlyTheNewCompleteProfileMap() {
        BlueprintProgressionPolicyManager manager = new BlueprintProgressionPolicyManager();
        BlueprintConfig config = new BlueprintConfig();
        var automatic = AutomaticWeaponPlacementCandidateManager.INSTANCE.publication();
        assertTrue(manager.rebuild(
                research(PROFILE),
                RESEARCH_REVISION,
                catalog(),
                CATALOG_REVISION,
                automatic,
                evidence(AutomaticWeaponEvidenceManager.PublicationState.READY),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY),
                config.researchFeatureSnapshot()));
        long firstPublicationRevision = manager.publication().revision();
        ResourceLocation replacementProfile = id("test:replacement_profile");

        assertTrue(manager.rebuild(
                research(replacementProfile),
                RESEARCH_REVISION + 1L,
                catalog(),
                CATALOG_REVISION,
                automatic,
                evidence(AutomaticWeaponEvidenceManager.PublicationState.READY),
                associations(BlueprintAmmoAssociationManager.PublicationState.READY),
                config.researchFeatureSnapshot()));

        var publication = manager.publication();
        assertTrue(publication.revision() > firstPublicationRevision);
        assertEquals(Set.of(replacementProfile),
                publication.snapshot().policiesByProfile().keySet());
        assertEquals(Set.of(replacementProfile),
                publication.craftingSnapshot().policiesByProfile().keySet());
        assertEquals(catalog().keySet(), publication.craftingSnapshot()
                .policiesByProfile().get(replacementProfile).keySet());
    }

    private static BlueprintCraftingPolicySnapshot resolve(
            AutomaticWeaponEvidenceManager.Publication evidence,
            BlueprintAmmoAssociationManager.Publication associations) {
        return resolve(evidence, associations, new BlueprintConfig());
    }

    private static BlueprintCraftingPolicySnapshot resolve(
            AutomaticWeaponEvidenceManager.Publication evidence,
            BlueprintAmmoAssociationManager.Publication associations,
            BlueprintConfig config) {
        return BlueprintCraftingPolicyResolver.resolve(
                research(),
                catalog(),
                CATALOG_REVISION,
                RESEARCH_REVISION,
                0L,
                Map.of(),
                evidence,
                associations,
                config.researchFeatureSnapshot());
    }

    private static AutomaticWeaponEvidenceManager.Publication evidence(
            AutomaticWeaponEvidenceManager.PublicationState state) {
        return new AutomaticWeaponEvidenceManager.Publication(
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(CATALOG_REVISION),
                EVIDENCE_REVISION,
                state);
    }

    private static BlueprintAmmoAssociationManager.Publication associations(
            BlueprintAmmoAssociationManager.PublicationState state) {
        if (state != BlueprintAmmoAssociationManager.PublicationState.READY) {
            return new BlueprintAmmoAssociationManager.Publication(
                    BlueprintAmmoAssociationSnapshot.EMPTY,
                    ASSOCIATION_REVISION,
                    CATALOG_REVISION,
                    state);
        }
        BlueprintAmmoAssociationSnapshot snapshot = new BlueprintAmmoAssociationSnapshot(
                CATALOG_REVISION,
                ASSOCIATION_REVISION,
                Set.of(GUN),
                Set.of(AMMO),
                Map.of(GUN, AMMO),
                Map.of(AMMO, Set.of(GUN)),
                Set.of(),
                Map.of(),
                Map.of());
        return new BlueprintAmmoAssociationManager.Publication(
                snapshot,
                ASSOCIATION_REVISION,
                CATALOG_REVISION,
                state);
    }

    private static BlueprintResearchSnapshot research() {
        return research(PROFILE);
    }

    private static BlueprintResearchSnapshot research(ResourceLocation profileId) {
        EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains =
                new EnumMap<>(Domain.class);
        for (Domain domain : Domain.values()) {
            domains.put(domain, BlueprintResearchProfile.DomainPolicy.ENABLED);
        }
        BlueprintResearchProfile profile = new BlueprintResearchProfile(
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
        return BlueprintResearchSnapshot.create(
                Map.of(), Map.of(profileId, profile), Map.of());
    }

    private static Map<ResourceLocation, BlueprintData> catalog() {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        result.put(GUN, data(GUN, "rifle", BlueprintKind.GUN));
        result.put(AMMO, data(AMMO, "ammo", BlueprintKind.AMMO));
        result.put(ATTACHMENT, data(ATTACHMENT, "scope", BlueprintKind.ATTACHMENT));
        return result;
    }

    private static BlueprintData data(
            ResourceLocation id,
            String itemType,
            BlueprintKind kind) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip.test",
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                itemType,
                id("tacz:" + itemType),
                kind,
                1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
