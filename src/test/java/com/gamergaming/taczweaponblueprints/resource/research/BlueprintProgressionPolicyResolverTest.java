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
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteMotifAssessment;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteQualityAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeAuthoringReport;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeEconomyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeTopologyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class BlueprintProgressionPolicyResolverTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation OVERRIDDEN = id("test:overridden");
    private static final ResourceLocation FALLBACK = id("test:fallback");

    @Test
    void exactRuleOverridesProfileTiersAndFragmentThreshold() {
        BlueprintResearchSnapshot research = researchSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog();

        BlueprintProgressionPolicySnapshot result = BlueprintProgressionPolicyResolver.resolve(
                research,
                catalog,
                4L,
                7L,
                Map.of(),
                new BlueprintConfig().researchFeatureSnapshot());

        ResolvedBlueprintProgressionPolicy overridden = result.policy(PROFILE, OVERRIDDEN).orElseThrow();
        assertEquals(ResearchWorkbenchTier.TIER_3, overridden.researchWorkbenchTier());
        assertEquals(ResolvedBlueprintProgressionPolicy.TierSource.EXACT_RULE, overridden.tierSource());
        assertEquals(Optional.of(id("test:override")), overridden.selectedProgressionRuleId());
        assertEquals(12, overridden.fragments().threshold());
        assertTrue(overridden.exactFragmentThreshold());

        ResolvedBlueprintProgressionPolicy fallback = result.policy(PROFILE, FALLBACK).orElseThrow();
        assertEquals(ResearchWorkbenchTier.TIER_2, fallback.researchWorkbenchTier());
        assertEquals(ResolvedBlueprintProgressionPolicy.TierSource.FALLBACK, fallback.tierSource());
        assertEquals(10, fallback.fragments().threshold());
        assertEquals(2, result.diagnosticsByProfile().get(PROFILE).includedCount());
        assertEquals(0, result.diagnosticsByProfile().get(PROFILE).omittedCount());
    }

    @Test
    void disabledFragmentsDoNotPublishDormantExactThresholdEvidence() {
        BlueprintConfig config = new BlueprintConfig();
        config.fragmentPreset.validateAndSet(
                com.gamergaming.taczweaponblueprints.progression.BlueprintFragmentPreset.DISABLED);
        config.exactFragmentThresholds.validateAndSet(Map.of(
                OVERRIDDEN.toString(), 37));
        config.update(3);

        ResolvedBlueprintProgressionPolicy policy = BlueprintProgressionPolicyResolver.resolve(
                researchSnapshot(),
                catalog(),
                4L,
                7L,
                Map.of(),
                config.researchFeatureSnapshot())
                .policy(PROFILE, OVERRIDDEN).orElseThrow();

        assertFalse(policy.fragments().enabled());
        assertFalse(policy.exactFragmentThreshold());
    }

    @Test
    void diagnosticExportIncludesResolvedPolicyAndConfigMappings() {
        BlueprintResearchSnapshot research = researchSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog();
        BlueprintConfig config = new BlueprintConfig();
        config.exactFragmentThresholds.validateAndSet(Map.of(OVERRIDDEN.toString(), 2_000));
        config.update(3);
        BlueprintProgressionPolicySnapshot progression =
                BlueprintProgressionPolicyResolver.resolve(
                        research,
                        catalog,
                        4L,
                        7L,
                        Map.of(),
                        config.researchFeatureSnapshot());
        ResearchTechTreeAuthoringReport authoring = ResearchTechTreeAuthoringReport.create(
                research,
                catalog,
                PROFILE,
                null,
                AutomaticWeaponEvidenceSnapshot.EMPTY);
        Map<ResourceLocation, ResolvedBlueprintCraftingPolicy> craftingAssignments =
                new LinkedHashMap<>();
        catalog.keySet().forEach(id -> craftingAssignments.put(
                id,
                new ResolvedBlueprintCraftingPolicy(
                        PROFILE,
                        id,
                        BlueprintCraftingDisposition.TIERED,
                        Optional.of(id.equals(OVERRIDDEN)
                                ? ResearchWorkbenchTier.TIER_3
                                : ResearchWorkbenchTier.TIER_1),
                        ProgressionGateRequirements.EMPTY,
                        BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                        Optional.empty(),
                        MatchSpecificity.NONE,
                        Optional.empty(),
                        Optional.empty(),
                        false,
                        "test_export",
                        Set.of())));
        BlueprintCraftingPolicySnapshot crafting = BlueprintCraftingPolicySnapshot.create(
                4L, 7L, 0L, catalog.keySet(), Map.of(PROFILE, craftingAssignments));

        var root = JsonParser.parseString(BlueprintResearchCatalogExporter.exportWithDiagnostics(
                research,
                catalog,
                PROFILE,
                null,
                authoring,
                ResearchTechTreeTopologyAudit.Audit.EMPTY,
                ResearchTechTreeEconomyAudit.Audit.EMPTY,
                ResearchGroupedRouteQualityAudit.Audit.EMPTY,
                ResearchGroupedRouteMotifAssessment.Assessment.EMPTY,
                progression,
                crafting,
                config.researchFeatureSnapshot())).getAsJsonObject();

        assertEquals(BlueprintResearchCatalogExporter.CURRENT_FORMAT,
                root.get("format").getAsInt());
        var summary = root.getAsJsonObject("progression_policy");
        assertTrue(summary.get("available").getAsBoolean());
        assertEquals(2, summary.get("included_count").getAsInt());
        assertFalse(summary.has("crafting_tier_counts"));
        var entries = root.getAsJsonArray("entries");
        var overridden = java.util.stream.StreamSupport.stream(entries.spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(value -> OVERRIDDEN.toString().equals(value.get("blueprint").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("progression_policy");
        assertEquals("tier_3", overridden.get("research_workbench_tier").getAsString());
        assertEquals("exact_rule", overridden.get("tier_source").getAsString());
        assertEquals(2_000, overridden.getAsJsonObject("fragments").get("threshold").getAsInt());
        assertEquals(2_000,
                overridden.getAsJsonObject("fragments").get("retained_progress_cap").getAsInt());
        assertTrue(overridden.getAsJsonObject("fragments").get("exact_threshold").getAsBoolean());
        var craftingSummary = root.getAsJsonObject("crafting_policy");
        assertTrue(craftingSummary.get("available").getAsBoolean());
        assertTrue(craftingSummary.get("complete_catalog_coverage").getAsBoolean());
        assertEquals(2, craftingSummary.get("assigned_count").getAsInt());
        assertEquals(0, craftingSummary.get("external_workstation_mapping_count").getAsInt());
        var craftingEntry = java.util.stream.StreamSupport.stream(entries.spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(value -> OVERRIDDEN.toString().equals(
                        value.get("blueprint").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("crafting_policy");
        assertEquals("tiered", craftingEntry.get("disposition").getAsString());
        assertEquals(3, craftingEntry.get("workbench_level").getAsInt());
        assertEquals("profile_fallback", craftingEntry.get("source").getAsString());
        assertTrue(craftingEntry.getAsJsonArray("warnings").isEmpty());
    }

    @Test
    void failedRebuildPreservesTheLastValidPublication() {
        BlueprintProgressionPolicyManager manager = new BlueprintProgressionPolicyManager();
        BlueprintConfig config = new BlueprintConfig();
        assertTrue(rebuild(
                manager,
                researchSnapshot(),
                1L,
                catalog(),
                1L,
                config.researchFeatureSnapshot()));
        BlueprintProgressionPolicyManager.Publication valid = manager.publication();
        assertEquals(
                config.researchFeatureSnapshot().policyShape(),
                valid.policyShape().orElseThrow());
        assertEquals(
                AutomaticWeaponPlacementCandidateManager.INSTANCE.publication().revision(),
                valid.automaticRevision());
        assertEquals(catalog().keySet(), valid.craftingSnapshot().catalogBlueprintIds());
        assertEquals(2,
                valid.craftingSnapshot().diagnosticsByProfile().get(PROFILE).assignedCount());

        assertFalse(rebuild(
                manager,
                researchSnapshot(),
                1L,
                catalog(),
                0L,
                config.researchFeatureSnapshot()));

        assertSame(valid, manager.publication());
        assertTrue(manager.lastFailure().isPresent());
        assertTrue(manager.publication().snapshot().policy(PROFILE, OVERRIDDEN).isPresent());
    }

    @Test
    void identicalFailedPolicyInputsAreSuppressedUntilTheirShapeChanges() {
        BlueprintProgressionPolicyManager manager = new BlueprintProgressionPolicyManager();
        BlueprintConfig config = new BlueprintConfig();

        assertFalse(rebuild(
                manager,
                researchSnapshot(),
                1L,
                catalog(),
                0L,
                config.researchFeatureSnapshot()));
        Optional<String> firstFailure = manager.lastFailure();

        config.creativeBypassesWorkbenchTiers.validateAndSet(
                !config.creativeBypassesWorkbenchTiers.get());
        config.update(3);
        assertFalse(rebuild(
                manager,
                researchSnapshot(),
                1L,
                catalog(),
                0L,
                config.researchFeatureSnapshot()));

        assertSame(firstFailure, manager.lastFailure());
    }

    @Test
    void ordinaryResearchRuleIsNotReportedAsAProgressionOverride() {
        BlueprintResearchRule ordinary = ruleFor(FALLBACK, Optional.empty());
        BlueprintResearchSnapshot research = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(id("test:ordinary"), ordinary));

        ResolvedBlueprintProgressionPolicy policy = BlueprintProgressionPolicyResolver.resolve(
                research,
                Map.of(FALLBACK, data(FALLBACK)),
                1L,
                1L,
                Map.of(),
                new BlueprintConfig().researchFeatureSnapshot())
                .policy(PROFILE, FALLBACK).orElseThrow();

        assertTrue(policy.selectedProgressionRuleId().isEmpty());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.NONE, policy.ruleSpecificity());
    }

    @Test
    void exactCraftingOnlyRuleDoesNotHideBroaderResearchProgressionRule() {
        ResourceLocation tagId = id("test:research_progression");
        ResourceLocation researchRuleId = id("test:research_rule");
        ResourceLocation craftingRuleId = id("test:crafting_rule");
        BlueprintResearchRule researchRule = rule(
                new BlueprintResearchTarget(List.of(), List.of(tagId), Optional.empty()),
                1,
                Optional.of(new BlueprintProgressionRuleOverride(
                        Optional.of(ResearchWorkbenchTier.TIER_3),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())),
                Optional.empty());
        BlueprintResearchRule craftingRule = rule(
                new BlueprintResearchTarget(List.of(OVERRIDDEN), List.of(), Optional.empty()),
                100,
                Optional.empty(),
                Optional.of(new BlueprintCraftingRuleOverride(
                        Optional.of(BlueprintCraftingDisposition.TIERED),
                        Optional.of(ResearchWorkbenchTier.TIER_1),
                        Optional.empty())));
        BlueprintResearchSnapshot research = BlueprintResearchSnapshot.create(
                Map.of(tagId, new BlueprintLootTag(
                        BlueprintLootTag.CURRENT_FORMAT, List.of(OVERRIDDEN))),
                Map.of(PROFILE, profile()),
                Map.of(researchRuleId, researchRule, craftingRuleId, craftingRule));

        ResolvedBlueprintProgressionPolicy policy = BlueprintProgressionPolicyResolver.resolve(
                research,
                Map.of(OVERRIDDEN, data(OVERRIDDEN)),
                1L,
                1L,
                Map.of(),
                new BlueprintConfig().researchFeatureSnapshot())
                .policy(PROFILE, OVERRIDDEN).orElseThrow();

        assertEquals(ResearchWorkbenchTier.TIER_3, policy.researchWorkbenchTier());
        assertEquals(Optional.of(researchRuleId), policy.selectedProgressionRuleId());
        assertEquals(MatchSpecificity.TAG, policy.ruleSpecificity());
    }

    @Test
    void resolverRejectsProfileCatalogCrossProductAboveAggregateBudget() {
        Map<ResourceLocation, BlueprintResearchProfile> profiles = new LinkedHashMap<>();
        for (int index = 0; index < 257; index++) {
            profiles.put(id("test:profile_" + index), profile());
        }
        Map<ResourceLocation, BlueprintData> largeCatalog = new LinkedHashMap<>();
        for (int index = 0; index < 1_021; index++) {
            ResourceLocation blueprintId = id("test:weapon_" + index);
            largeCatalog.put(blueprintId, data(blueprintId));
        }
        BlueprintResearchSnapshot research = BlueprintResearchSnapshot.create(
                Map.of(), profiles, Map.of());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintProgressionPolicyResolver.resolve(
                        research,
                        largeCatalog,
                        1L,
                        1L,
                        Map.of(),
                        new BlueprintConfig().researchFeatureSnapshot()));
        assertTrue(exception.getMessage().contains("profile-catalog assignments"));
    }

    @Test
    void exporterRejectsProgressionPublicationMissingCatalogCoverage() {
        BlueprintResearchSnapshot research = researchSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog();
        BlueprintConfig config = new BlueprintConfig();
        BlueprintProgressionPolicySnapshot complete = BlueprintProgressionPolicyResolver.resolve(
                research,
                catalog,
                4L,
                7L,
                Map.of(),
                config.researchFeatureSnapshot());
        ResolvedBlueprintProgressionPolicy retained = complete.policy(PROFILE, OVERRIDDEN).orElseThrow();
        BlueprintProgressionPolicySnapshot incomplete = new BlueprintProgressionPolicySnapshot(
                4L,
                7L,
                complete.automaticPercentiles(),
                Map.of(PROFILE, Map.of(OVERRIDDEN, retained)),
                Map.of(PROFILE, Map.of()),
                Map.of(PROFILE, diagnosticsFor(retained)));
        ResearchTechTreeAuthoringReport authoring = ResearchTechTreeAuthoringReport.create(
                research,
                catalog,
                PROFILE,
                null,
                AutomaticWeaponEvidenceSnapshot.EMPTY);

        assertThrows(IllegalArgumentException.class, () ->
                BlueprintResearchCatalogExporter.exportWithDiagnostics(
                        research,
                        catalog,
                        PROFILE,
                        null,
                        authoring,
                        ResearchTechTreeTopologyAudit.Audit.EMPTY,
                        ResearchTechTreeEconomyAudit.Audit.EMPTY,
                        ResearchGroupedRouteQualityAudit.Audit.EMPTY,
                        ResearchGroupedRouteMotifAssessment.Assessment.EMPTY,
                        incomplete,
                        config.researchFeatureSnapshot()));
    }

    @Test
    void explicitOmissionsAreDistinctFromUnknownPolicyState() {
        BlueprintProgressionPolicySnapshot snapshot = new BlueprintProgressionPolicySnapshot(
                1L,
                1L,
                AutomaticWorkbenchTierPercentiles.DEFAULT,
                Map.of(PROFILE, Map.of()),
                Map.of(PROFILE, Map.of(FALLBACK, "not_in_effective_tree")),
                Map.of(PROFILE, emptyDiagnostics(1)));

        assertTrue(snapshot.explicitlyOutsideTieredProgression(PROFILE, FALLBACK));
        assertEquals(Optional.of("not_in_effective_tree"),
                snapshot.omissionReason(PROFILE, FALLBACK));
        assertFalse(snapshot.explicitlyOutsideTieredProgression(PROFILE, OVERRIDDEN));
    }

    private static BlueprintResearchSnapshot researchSnapshot() {
        BlueprintResearchRule exact = ruleFor(
                OVERRIDDEN,
                Optional.of(new BlueprintProgressionRuleOverride(
                        Optional.of(ResearchWorkbenchTier.TIER_3),
                        Optional.empty(),
                        Optional.of(12),
                        Optional.empty())));
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(id("test:override"), exact));
    }

    private static boolean rebuild(
            BlueprintProgressionPolicyManager manager,
            BlueprintResearchSnapshot research,
            long researchRevision,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot
                    config) {
        long sourceCatalogRevision = Math.max(1L, catalogRevision);
        return manager.rebuild(
                research,
                researchRevision,
                catalog,
                catalogRevision,
                AutomaticWeaponPlacementCandidateManager.INSTANCE.publication(),
                new AutomaticWeaponEvidenceManager.Publication(
                        AutomaticWeaponEvidenceSnapshot.emptyForCatalog(
                                sourceCatalogRevision),
                        1L,
                        AutomaticWeaponEvidenceManager.PublicationState.READY),
                associationPublication(catalog, sourceCatalogRevision),
                config);
    }

    private static BlueprintAmmoAssociationManager.Publication associationPublication(
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision) {
        Set<ResourceLocation> guns = catalog.entrySet().stream()
                .filter(entry -> entry.getValue().getKind() == BlueprintKind.GUN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> ammo = catalog.entrySet().stream()
                .filter(entry -> entry.getValue().getKind() == BlueprintKind.AMMO)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        Map<ResourceLocation, String> rejected = guns.stream().collect(
                java.util.stream.Collectors.toMap(
                        id -> id,
                        ignored -> "missing_tacz_ammo_id"));
        BlueprintAmmoAssociationSnapshot snapshot = new BlueprintAmmoAssociationSnapshot(
                catalogRevision,
                1L,
                guns,
                ammo,
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                rejected);
        return new BlueprintAmmoAssociationManager.Publication(
                snapshot,
                1L,
                catalogRevision,
                BlueprintAmmoAssociationManager.PublicationState.READY);
    }

    private static BlueprintResearchRule ruleFor(
            ResourceLocation target,
            Optional<BlueprintProgressionRuleOverride> progression) {
        return rule(
                new BlueprintResearchTarget(List.of(target), List.of(), Optional.empty()),
                10,
                progression,
                Optional.empty());
    }

    private static BlueprintResearchRule rule(
            BlueprintResearchTarget target,
            int priority,
            Optional<BlueprintProgressionRuleOverride> progression,
            Optional<BlueprintCraftingRuleOverride> crafting) {
        return new BlueprintResearchRule(
                BlueprintResearchRule.CURRENT_FORMAT,
                PROFILE,
                priority,
                target,
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
                progression,
                crafting);
    }

    private static BlueprintProgressionPolicySnapshot.ProfileDiagnostics diagnosticsFor(
            ResolvedBlueprintProgressionPolicy policy) {
        EnumMap<ResearchWorkbenchTier, Integer> research = zeroTierCounts();
        research.put(policy.researchWorkbenchTier(), 1);
        return new BlueprintProgressionPolicySnapshot.ProfileDiagnostics(
                1,
                0,
                research,
                policy.reviewRequired() ? 1 : 0,
                policy.gates().allOf().size(),
                policy.gates().conditionCount(),
                policy.fragments().enabled()
                        ? Map.of(policy.fragments().threshold(), 1)
                        : Map.of());
    }

    private static BlueprintProgressionPolicySnapshot.ProfileDiagnostics emptyDiagnostics(
            int omissions) {
        return new BlueprintProgressionPolicySnapshot.ProfileDiagnostics(
                0,
                omissions,
                zeroTierCounts(),
                0,
                0,
                0,
                Map.of());
    }

    private static EnumMap<ResearchWorkbenchTier, Integer> zeroTierCounts() {
        EnumMap<ResearchWorkbenchTier, Integer> counts = new EnumMap<>(ResearchWorkbenchTier.class);
        for (ResearchWorkbenchTier tier : ResearchWorkbenchTier.values()) {
            counts.put(tier, 0);
        }
        return counts;
    }

    private static BlueprintResearchProfile profile() {
        EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains = new EnumMap<>(Domain.class);
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
                BlueprintProgressionProfilePolicy.DEFAULT);
    }

    private static Map<ResourceLocation, BlueprintData> catalog() {
        return Map.of(
                OVERRIDDEN, data(OVERRIDDEN),
                FALLBACK, data(FALLBACK));
    }

    private static BlueprintData data(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "item." + id.getNamespace() + "." + id.getPath(),
                "tooltip.test",
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                "rifle",
                id("tacz:rifle"),
                BlueprintKind.GUN,
                1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
