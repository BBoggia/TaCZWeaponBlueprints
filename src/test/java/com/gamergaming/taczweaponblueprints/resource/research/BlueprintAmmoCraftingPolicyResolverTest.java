package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

class BlueprintAmmoCraftingPolicyResolverTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation AMMO = id("test:shared_ammo");
    private static final ResourceLocation LOW = id("test:low_gun");
    private static final ResourceLocation HIGH = id("test:high_gun");
    private static final long CATALOG_REVISION = 4L;
    private static final long RESEARCH_REVISION = 7L;
    private static final long AUTOMATIC_REVISION = 9L;
    private static final long ASSOCIATION_REVISION = 3L;

    @Test
    void sharedAmmoUsesEarliestTieredCompatibleGun() {
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, HIGH, AMMO);
        BlueprintResearchSnapshot research = research(
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                Map.of());
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_1),
                HIGH, access(HIGH, BlueprintCraftingAccessPolicy.TIER_3)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW, HIGH),
                Set.of(AMMO),
                Map.of(LOW, AMMO, HIGH, AMMO),
                Map.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research, catalog, guns, associations));

        assertEquals(BlueprintCraftingDisposition.TIERED, policy.disposition());
        assertEquals(ResearchWorkbenchTier.TIER_1,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.LINKED_WEAPON, policy.source());
        assertEquals("lowest_tiered_linked_weapon", policy.reasonCode());
        assertTrue(policy.warnings().isEmpty());
    }

    @Test
    void orphanAmmoUsesConfiguredFallbackAndVisibleDiagnostic() {
        BlueprintCraftingStrategy fallback = BlueprintCraftingStrategy.linkedWeapon(
                BlueprintCraftingAccessPolicy.TIER_2);
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, AMMO);
        BlueprintResearchSnapshot research = research(fallback, Map.of());
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_1)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW),
                Set.of(AMMO),
                Map.of(),
                Map.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research, catalog, guns, associations));

        assertEquals(ResearchWorkbenchTier.TIER_2,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.PROFILE_FALLBACK, policy.source());
        assertTrue(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.AMMO_WITHOUT_LINKED_WEAPON));
    }

    @Test
    void unrestrictedLinksDoNotSilentlyCollapseTieredAmmo() {
        BlueprintCraftingStrategy fallback = BlueprintCraftingStrategy.linkedWeapon(
                BlueprintCraftingAccessPolicy.TIER_2);
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, HIGH, AMMO);
        BlueprintResearchSnapshot research = research(fallback, Map.of());
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW, HIGH),
                Set.of(AMMO),
                Map.of(LOW, AMMO, HIGH, AMMO),
                Map.of());

        var mixed = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.UNRESTRICTED),
                HIGH, access(HIGH, BlueprintCraftingAccessPolicy.TIER_3)));
        assertEquals(ResearchWorkbenchTier.TIER_3,
                policy(resolve(research, catalog, mixed, associations))
                        .requiredWorkbenchTier().orElseThrow());

        var unrestrictedOnly = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.UNRESTRICTED),
                HIGH, access(HIGH, BlueprintCraftingAccessPolicy.DISABLED)));
        ResolvedBlueprintCraftingPolicy fallbackPolicy = policy(resolve(
                research, catalog, unrestrictedOnly, associations));
        assertEquals(ResearchWorkbenchTier.TIER_2,
                fallbackPolicy.requiredWorkbenchTier().orElseThrow());
        assertTrue(fallbackPolicy.warnings().contains(
                BlueprintCraftingPolicyWarning.AMMO_WITHOUT_TIERED_LINKED_WEAPON));
    }

    @Test
    void ambiguousSourceLinksNeverSupplyAutomaticAuthority() {
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, AMMO);
        BlueprintResearchSnapshot research = research(
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                Map.of());
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_3)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW),
                Set.of(AMMO),
                Map.of(),
                Map.of(AMMO, Set.of(LOW)));

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research, catalog, guns, associations));

        assertEquals(ResearchWorkbenchTier.TIER_1,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.PROFILE_FALLBACK, policy.source());
        assertTrue(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.AMBIGUOUS_AMMO_LINK));
        assertTrue(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.AMMO_WITHOUT_LINKED_WEAPON));
    }

    @Test
    void exactAmmoRuleCanRaiseLinkedTierAndClearsResolvedFallbackWarnings() {
        ResourceLocation ruleId = id("test:ammo_override");
        BlueprintResearchRule rule = craftingRule(
                AMMO,
                new BlueprintCraftingRuleOverride(
                        Optional.of(BlueprintCraftingDisposition.TIERED),
                        Optional.of(ResearchWorkbenchTier.TIER_3),
                        Optional.empty()));
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, AMMO);
        BlueprintResearchSnapshot research = research(
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                Map.of(ruleId, rule));
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_1)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW),
                Set.of(AMMO),
                Map.of(),
                Map.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research, catalog, guns, associations));

        assertEquals(ResearchWorkbenchTier.TIER_3,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.EXACT_RULE, policy.source());
        assertEquals(Optional.of(ruleId), policy.selectedRuleId());
        assertEquals(MatchSpecificity.EXACT, policy.ruleSpecificity());
        assertTrue(policy.warnings().isEmpty());
    }

    @Test
    void directAmmoModesDoNotDependOnAssociationEvidence() {
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, AMMO);
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_3)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW), Set.of(AMMO), Map.of(), Map.of());

        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED,
                policy(resolve(
                        research(BlueprintCraftingStrategy.unrestricted(), Map.of()),
                        catalog,
                        guns,
                        associations)).disposition());
        assertEquals(BlueprintCraftingDisposition.DISABLED,
                policy(resolve(
                        research(BlueprintCraftingStrategy.disabled(), Map.of()),
                        catalog,
                        guns,
                        associations)).disposition());
        assertEquals(ResearchWorkbenchTier.TIER_2,
                policy(resolve(
                        research(
                                BlueprintCraftingStrategy.fixed(
                                        ResearchWorkbenchTier.TIER_2),
                                Map.of()),
                        catalog,
                        guns,
                        associations)).requiredWorkbenchTier().orElseThrow());
    }

    @Test
    void formatFourAmmoHonorsLegacyCraftingTierOverride() {
        BlueprintProgressionRuleOverride legacyTier = new BlueprintProgressionRuleOverride(
                Optional.empty(),
                Optional.of(ResearchWorkbenchTier.TIER_3),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchRule legacyRule = new BlueprintResearchRule(
                BlueprintResearchRule.PROGRESSION_FORMAT,
                PROFILE,
                0,
                new BlueprintResearchTarget(
                        List.of(AMMO), List.of(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(legacyTier),
                Optional.empty());
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, AMMO);
        BlueprintResearchSnapshot research = research(
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                Map.of(id("test:legacy_ammo_rule"), legacyRule));
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_1)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW), Set.of(AMMO), Map.of(LOW, AMMO), Map.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research, catalog, guns, associations));

        assertEquals(ResearchWorkbenchTier.TIER_3,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.EXACT_RULE, policy.source());
        assertEquals(Optional.of(id("test:legacy_ammo_rule")), policy.selectedRuleId());
    }

    @Test
    void legacyOmittedAmmoRemainsExplicitlyUnrestrictedForMigration() {
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, AMMO);
        BlueprintResearchSnapshot research = legacyResearchWithAmmoDisabled();
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_1)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW), Set.of(AMMO), Map.of(LOW, AMMO), Map.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research, catalog, guns, associations));

        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED, policy.disposition());
        assertEquals(BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY,
                policy.source());
        assertTrue(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY));
    }

    @Test
    void staleAssociationAndGunRevisionsFailBeforeResolution() {
        Map<ResourceLocation, BlueprintData> catalog = catalog(LOW, AMMO);
        BlueprintResearchSnapshot research = research(
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                Map.of());
        var guns = gunResolution(Map.of(
                LOW, access(LOW, BlueprintCraftingAccessPolicy.TIER_1)));
        BlueprintAmmoAssociationSnapshot associations = association(
                Set.of(LOW), Set.of(AMMO), Map.of(LOW, AMMO), Map.of());

        assertThrows(IllegalArgumentException.class, () ->
                BlueprintAmmoCraftingPolicyResolver.resolve(
                        research,
                        catalog,
                        CATALOG_REVISION,
                        RESEARCH_REVISION,
                        AUTOMATIC_REVISION,
                        guns,
                        associations,
                        ASSOCIATION_REVISION + 1));
        assertThrows(IllegalArgumentException.class, () ->
                BlueprintAmmoCraftingPolicyResolver.resolve(
                        research,
                        catalog,
                        CATALOG_REVISION,
                        RESEARCH_REVISION + 1,
                        AUTOMATIC_REVISION,
                        guns,
                        associations,
                        ASSOCIATION_REVISION));
    }

    private static BlueprintAmmoCraftingPolicyResolver.Resolution resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintGunCraftingPolicyResolver.Resolution guns,
            BlueprintAmmoAssociationSnapshot associations) {
        return BlueprintAmmoCraftingPolicyResolver.resolve(
                research,
                catalog,
                CATALOG_REVISION,
                RESEARCH_REVISION,
                AUTOMATIC_REVISION,
                guns,
                associations,
                ASSOCIATION_REVISION);
    }

    private static ResolvedBlueprintCraftingPolicy policy(
            BlueprintAmmoCraftingPolicyResolver.Resolution result) {
        return result.policy(PROFILE, AMMO).orElseThrow();
    }

    private static BlueprintResearchSnapshot research(
            BlueprintCraftingStrategy ammoStrategy,
            Map<ResourceLocation, BlueprintResearchRule> rules) {
        BlueprintCraftingProfilePolicy crafting = new BlueprintCraftingProfilePolicy(
                BlueprintAuthoredGunCraftingPolicy.DEFAULT,
                BlueprintCraftingStrategy.OMITTED_DEFAULT,
                BlueprintCraftingStrategy.AUTOMATIC_DEFAULT,
                ammoStrategy,
                BlueprintAttachmentCraftingPolicy.DEFAULT,
                BlueprintCraftingAccessPolicy.TIER_1);
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
                crafting);
        return BlueprintResearchSnapshot.create(
                Map.of(), Map.of(PROFILE, profile), rules);
    }

    private static BlueprintResearchSnapshot legacyResearchWithAmmoDisabled() {
        EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains =
                new EnumMap<>(Domain.class);
        for (Domain domain : Domain.values()) {
            domains.put(domain, BlueprintResearchProfile.DomainPolicy.ENABLED);
        }
        domains.put(Domain.AMMO, new BlueprintResearchProfile.DomainPolicy(false, false));
        BlueprintResearchProfile profile = new BlueprintResearchProfile(
                BlueprintResearchProfile.PROGRESSION_FORMAT,
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
                BlueprintCraftingProfilePolicy.LEGACY);
        return BlueprintResearchSnapshot.create(
                Map.of(), Map.of(PROFILE, profile), Map.of());
    }

    private static BlueprintResearchRule craftingRule(
            ResourceLocation blueprintId,
            BlueprintCraftingRuleOverride override) {
        return new BlueprintResearchRule(
                BlueprintResearchRule.CURRENT_FORMAT,
                PROFILE,
                0,
                new BlueprintResearchTarget(
                        List.of(blueprintId), List.of(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(override));
    }

    private static BlueprintGunCraftingPolicyResolver.Resolution gunResolution(
            Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> policies) {
        return new BlueprintGunCraftingPolicyResolver.Resolution(
                CATALOG_REVISION,
                RESEARCH_REVISION,
                AUTOMATIC_REVISION,
                policies.keySet(),
                Map.of(PROFILE, policies));
    }

    private static ResolvedBlueprintCraftingPolicy access(
            ResourceLocation gunId,
            BlueprintCraftingAccessPolicy access) {
        return new ResolvedBlueprintCraftingPolicy(
                PROFILE,
                gunId,
                access.disposition(),
                access.workbenchTier(),
                ProgressionGateRequirements.EMPTY,
                BlueprintCraftingPolicySource.CATEGORY_DEFAULT,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                "test_gun_policy",
                Set.of());
    }

    private static BlueprintAmmoAssociationSnapshot association(
            Set<ResourceLocation> guns,
            Set<ResourceLocation> ammo,
            Map<ResourceLocation, ResourceLocation> trusted,
            Map<ResourceLocation, Set<ResourceLocation>> ambiguousByAmmo) {
        Map<ResourceLocation, Set<ResourceLocation>> reverse = new LinkedHashMap<>();
        trusted.forEach((gun, ammoId) -> reverse
                .computeIfAbsent(ammoId, ignored -> new LinkedHashSet<>())
                .add(gun));
        Set<ResourceLocation> ambiguousGuns = ambiguousByAmmo.values().stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toSet());
        Map<ResourceLocation, String> rejected = new LinkedHashMap<>();
        guns.stream()
                .filter(id -> !trusted.containsKey(id) && !ambiguousGuns.contains(id))
                .forEach(id -> rejected.put(id, "missing_tacz_ammo_id"));
        return new BlueprintAmmoAssociationSnapshot(
                CATALOG_REVISION,
                ASSOCIATION_REVISION,
                guns,
                ammo,
                trusted,
                reverse,
                ambiguousGuns,
                ambiguousByAmmo,
                rejected);
    }

    private static Map<ResourceLocation, BlueprintData> catalog(
            ResourceLocation low,
            ResourceLocation ammo) {
        return catalog(low, null, ammo);
    }

    private static Map<ResourceLocation, BlueprintData> catalog(
            ResourceLocation low,
            ResourceLocation high,
            ResourceLocation ammo) {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        result.put(low, data(low, BlueprintKind.GUN));
        if (high != null) {
            result.put(high, data(high, BlueprintKind.GUN));
        }
        result.put(ammo, data(ammo, BlueprintKind.AMMO));
        return result;
    }

    private static BlueprintData data(ResourceLocation id, BlueprintKind kind) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip.test",
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                kind == BlueprintKind.AMMO ? "ammo" : "rifle",
                id("tacz:" + (kind == BlueprintKind.AMMO ? "ammo" : "rifle")),
                kind,
                1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
