package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Disclosure;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateGroup;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateScope;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityScorer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityScore;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScorer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScore;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

class BlueprintGunCraftingPolicyResolverTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TREE = id("test:tree");
    private static final ResourceLocation LANE = id("test:weapons");
    private static final ResourceLocation LOW = id("test:low");
    private static final ResourceLocation MID = id("test:mid");
    private static final ResourceLocation HIGH = id("test:high");
    private static final ResourceLocation REVIEW = id("test:review");
    private static final ResourceLocation EXCLUDED = id("test:excluded");

    @Test
    void automaticTreeAssignsTrustedPercentilesAndExplicitFallbacksToEveryGun() {
        Map<ResourceLocation, BlueprintData> catalog = gunCatalog(
                LOW, MID, HIGH, REVIEW, EXCLUDED);
        BlueprintResearchSnapshot research = automaticResearch(profile(
                BlueprintResearchProfile.CURRENT_FORMAT,
                Optional.of(TREE),
                BlueprintCraftingProfilePolicy.DEFAULT));
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                4L,
                7L,
                Map.of(
                        LOW.toString(), proposal(LOW, 10, false, 0),
                        MID.toString(), proposal(MID, 50, false, 1),
                        HIGH.toString(), proposal(HIGH, 90, false, 2),
                        REVIEW.toString(), proposal(REVIEW, 80, true, 3)),
                Map.of(EXCLUDED.toString(), "insufficient_evidence"));

        BlueprintGunCraftingPolicyResolver.Resolution result = resolve(
                research,
                catalog,
                4L,
                7L,
                Map.of(TREE, candidates),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(4L));

        assertEquals(5, result.gunBlueprintIds().size());
        assertEquals(ResearchWorkbenchTier.TIER_1, tier(result, LOW));
        assertEquals(ResearchWorkbenchTier.TIER_2, tier(result, MID));
        assertEquals(ResearchWorkbenchTier.TIER_3, tier(result, HIGH));
        assertEquals(BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE,
                policy(result, HIGH).source());
        assertTrue(policy(result, HIGH).automaticPercentileBasisPoints().isPresent());

        ResolvedBlueprintCraftingPolicy review = policy(result, REVIEW);
        assertEquals(ResearchWorkbenchTier.TIER_2, tier(result, REVIEW));
        assertEquals(BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                review.source());
        assertTrue(review.reviewRequired());
        assertTrue(review.warnings().contains(
                BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK));

        ResolvedBlueprintCraftingPolicy excluded = policy(result, EXCLUDED);
        assertEquals(ResearchWorkbenchTier.TIER_2, tier(result, EXCLUDED));
        assertEquals(BlueprintCraftingPolicySource.PROFILE_FALLBACK, excluded.source());
        assertFalse(excluded.reviewRequired());
        assertEquals(5, result.diagnosticsByProfile().get(PROFILE).assignedCount());
    }

    @Test
    void automaticReviewFallbackCanBeDisabledWithoutLosingDiagnosticEvidence() {
        BlueprintCraftingProfilePolicy crafting = new BlueprintCraftingProfilePolicy(
                BlueprintAuthoredGunCraftingPolicy.DEFAULT,
                BlueprintCraftingStrategy.OMITTED_DEFAULT,
                BlueprintCraftingStrategy.automaticTier(BlueprintCraftingAccessPolicy.DISABLED),
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                BlueprintAttachmentCraftingPolicy.DEFAULT,
                BlueprintCraftingAccessPolicy.TIER_1);
        Map<ResourceLocation, BlueprintData> catalog = gunCatalog(REVIEW);
        BlueprintResearchSnapshot research = automaticResearch(profile(
                BlueprintResearchProfile.CURRENT_FORMAT,
                Optional.of(TREE),
                crafting));
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                1L,
                1L,
                Map.of(REVIEW.toString(), proposal(REVIEW, 70, true, 0)),
                Map.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research,
                catalog,
                1L,
                1L,
                Map.of(TREE, candidates),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(1L)), REVIEW);

        assertEquals(BlueprintCraftingDisposition.DISABLED, policy.disposition());
        assertEquals(BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                policy.source());
        assertTrue(policy.reviewRequired());
        assertEquals(Optional.of(70), policy.automaticScore());
    }

    @Test
    void authoredTreeResolvesBandsOmissionsAndIndependentRuleSpecificities() {
        ResourceLocation authored = id("test:authored");
        ResourceLocation exact = id("test:exact_omitted");
        ResourceLocation tagged = id("test:tagged_omitted");
        ResourceLocation selected = id("test:selector_omitted");
        ResourceLocation ammo = id("test:ammo");
        ResourceLocation tagId = id("test:crafting_targets");
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>(gunCatalog(
                authored, exact, tagged, selected));
        catalog.put(ammo, data(ammo, BlueprintKind.AMMO));

        BlueprintResearchRule exactCrafting = craftingRule(
                exactTarget(exact),
                20,
                accessOverride(BlueprintCraftingDisposition.TIERED,
                        Optional.of(ResearchWorkbenchTier.TIER_3)));
        BlueprintResearchRule ordinaryExact = ordinaryRule(exactTarget(tagged), 100);
        BlueprintResearchRule tagCrafting = craftingRule(
                new BlueprintResearchTarget(List.of(), List.of(tagId), Optional.empty()),
                5,
                accessOverride(BlueprintCraftingDisposition.UNRESTRICTED, Optional.empty()));
        BlueprintResearchRule selectorCrafting = craftingRule(
                new BlueprintResearchTarget(
                        List.of(),
                        List.of(),
                        Optional.of(new BlueprintCatalogSelector(
                                List.of("test"),
                                List.of("rifle"),
                                List.of("selector_"),
                                List.of(),
                                List.of(BlueprintKind.GUN),
                                1.0F))),
                1,
                accessOverride(BlueprintCraftingDisposition.TIERED,
                        Optional.of(ResearchWorkbenchTier.TIER_2)));
        BlueprintResearchSnapshot research = authoredResearch(
                profile(
                        BlueprintResearchProfile.CURRENT_FORMAT,
                        Optional.of(TREE),
                        BlueprintCraftingProfilePolicy.DEFAULT),
                Map.of(
                        id("test:exact_crafting"), exactCrafting,
                        id("test:ordinary_exact"), ordinaryExact,
                        id("test:tag_crafting"), tagCrafting,
                        id("test:selector_crafting"), selectorCrafting),
                Map.of(tagId, new BlueprintLootTag(1, List.of(tagged))),
                List.of(authoredEntry(authored, Tier.ADVANCED)));

        BlueprintGunCraftingPolicyResolver.Resolution result = resolve(
                research,
                catalog,
                5L,
                8L,
                Map.of(),
                evidence(5L, List.of(authored, exact, tagged, selected)));

        assertEquals(Set.of(authored, exact, tagged, selected), result.gunBlueprintIds());
        assertFalse(result.gunBlueprintIds().contains(ammo));
        assertEquals(ResearchWorkbenchTier.TIER_2, tier(result, authored));
        assertEquals(BlueprintCraftingPolicySource.AUTHORED_BAND,
                policy(result, authored).source());
        assertTrue(policy(result, authored).automaticScore().isEmpty());

        assertEquals(ResearchWorkbenchTier.TIER_3, tier(result, exact));
        assertEquals(BlueprintCraftingPolicySource.EXACT_RULE, policy(result, exact).source());
        assertEquals(MatchSpecificity.EXACT, policy(result, exact).ruleSpecificity());

        ResolvedBlueprintCraftingPolicy taggedPolicy = policy(result, tagged);
        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED, taggedPolicy.disposition());
        assertEquals(BlueprintCraftingPolicySource.AUTHORED_RULE, taggedPolicy.source());
        assertEquals(MatchSpecificity.TAG, taggedPolicy.ruleSpecificity());
        assertEquals(Optional.of(id("test:tag_crafting")), taggedPolicy.selectedRuleId());

        assertEquals(ResearchWorkbenchTier.TIER_2, tier(result, selected));
        assertEquals(MatchSpecificity.SELECTOR,
                policy(result, selected).ruleSpecificity());
    }

    @Test
    void catalogAndAutomaticEvidenceChangesCannotMoveAuthoredOrRestoreOmittedGuns() {
        ResourceLocation authored = id("test:stable_authored");
        ResourceLocation omitted = id("test:stable_omitted");
        BlueprintResearchSnapshot research = authoredResearch(
                profile(
                        BlueprintResearchProfile.CURRENT_FORMAT,
                        Optional.of(TREE),
                        BlueprintCraftingProfilePolicy.DEFAULT),
                Map.of(),
                Map.of(),
                List.of(authoredEntry(authored, Tier.BASIC)));

        Map<ResourceLocation, BlueprintData> firstCatalog = gunCatalog(authored, omitted);
        BlueprintGunCraftingPolicyResolver.Resolution first = resolve(
                research,
                firstCatalog,
                1L,
                1L,
                Map.of(),
                evidence(1L, List.of(authored, omitted)));

        ResourceLocation added = id("test:extreme_added");
        Map<ResourceLocation, BlueprintData> secondCatalog = gunCatalog(
                authored, omitted, added);
        BlueprintGunCraftingPolicyResolver.Resolution second = resolve(
                research,
                secondCatalog,
                2L,
                1L,
                Map.of(),
                evidence(2L, List.of(added, omitted, authored)));

        assertEquals(ResearchWorkbenchTier.TIER_1, tier(first, authored));
        assertEquals(ResearchWorkbenchTier.TIER_1, tier(second, authored));
        assertEquals(BlueprintCraftingPolicySource.AUTHORED_BAND,
                policy(second, authored).source());
        assertTrue(policy(second, authored).automaticScore().isEmpty());
        assertEquals(BlueprintCraftingDisposition.DISABLED,
                policy(first, omitted).disposition());
        assertEquals(BlueprintCraftingDisposition.DISABLED,
                policy(second, omitted).disposition());
        assertTrue(policy(second, omitted).warnings().contains(
                BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK));
    }

    @Test
    void authoredOmissionUsesAutomaticEvidenceOnlyWhenExplicitlyRequested() {
        ResourceLocation omitted = id("test:opted_in_omitted");
        BlueprintCraftingProfilePolicy crafting = new BlueprintCraftingProfilePolicy(
                BlueprintAuthoredGunCraftingPolicy.DEFAULT,
                BlueprintCraftingStrategy.automaticTier(BlueprintCraftingAccessPolicy.DISABLED),
                BlueprintCraftingStrategy.AUTOMATIC_DEFAULT,
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                BlueprintAttachmentCraftingPolicy.DEFAULT,
                BlueprintCraftingAccessPolicy.TIER_1);
        BlueprintResearchSnapshot research = authoredResearch(
                profile(
                        BlueprintResearchProfile.CURRENT_FORMAT,
                        Optional.of(TREE),
                        crafting),
                Map.of(),
                Map.of(),
                List.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research,
                gunCatalog(omitted),
                3L,
                2L,
                Map.of(),
                evidence(3L, List.of(omitted))), omitted);

        assertEquals(BlueprintCraftingDisposition.TIERED, policy.disposition());
        assertEquals(BlueprintCraftingPolicySource.AUTOMATIC_PERCENTILE, policy.source());
        assertTrue(policy.automaticScore().isPresent());
        assertFalse(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.AUTHORED_OMITTED_FALLBACK));
    }

    @Test
    void authoredAutomaticOmissionUsesFallbackForReviewRequiredEvidence() {
        ResourceLocation omitted = id("test:review_omitted");
        BlueprintCraftingProfilePolicy crafting = new BlueprintCraftingProfilePolicy(
                BlueprintAuthoredGunCraftingPolicy.DEFAULT,
                BlueprintCraftingStrategy.automaticTier(BlueprintCraftingAccessPolicy.DISABLED),
                BlueprintCraftingStrategy.AUTOMATIC_DEFAULT,
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                BlueprintAttachmentCraftingPolicy.DEFAULT,
                BlueprintCraftingAccessPolicy.TIER_1);
        BlueprintResearchSnapshot research = authoredResearch(
                profile(
                        BlueprintResearchProfile.CURRENT_FORMAT,
                        Optional.of(TREE),
                        crafting),
                Map.of(),
                Map.of(),
                List.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research,
                gunCatalog(omitted),
                3L,
                2L,
                Map.of(),
                evidence(3L, List.of(omitted), true)), omitted);

        assertEquals(BlueprintCraftingDisposition.DISABLED, policy.disposition());
        assertEquals(BlueprintCraftingPolicySource.AUTOMATIC_REVIEW_FALLBACK,
                policy.source());
        assertTrue(policy.reviewRequired());
        assertTrue(policy.automaticScore().isPresent());
        assertTrue(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.AUTOMATIC_REVIEW_FALLBACK));
    }

    @Test
    void formatFourProfileHonorsLegacyCraftingOverridesBySpecificity() {
        ResourceLocation weapon = id("test:legacy_override");
        ResourceLocation tagId = id("test:legacy_crafting_targets");
        BlueprintProgressionRuleOverride legacyTier = new BlueprintProgressionRuleOverride(
                Optional.empty(),
                Optional.of(ResearchWorkbenchTier.TIER_3),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchRule legacyTagRule = rule(
                new BlueprintResearchTarget(List.of(), List.of(tagId), Optional.empty()),
                0,
                Optional.of(legacyTier),
                Optional.empty());
        BlueprintResearchRule researchOnlyExact = ordinaryRule(exactTarget(weapon), 100);
        BlueprintResearchSnapshot research = authoredResearch(
                profile(
                        BlueprintResearchProfile.CURRENT_FORMAT,
                        Optional.of(TREE),
                        BlueprintCraftingProfilePolicy.DEFAULT),
                Map.of(
                        id("test:legacy_tag_rule"), legacyTagRule,
                        id("test:research_only_exact"), researchOnlyExact),
                Map.of(tagId, new BlueprintLootTag(1, List.of(weapon))),
                List.of(authoredEntry(weapon, Tier.BASIC)));

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research,
                gunCatalog(weapon),
                1L,
                1L,
                Map.of(),
                evidence(1L, List.of(weapon))), weapon);

        assertEquals(ResearchWorkbenchTier.TIER_3,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.AUTHORED_RULE, policy.source());
        assertEquals(Optional.of(id("test:legacy_tag_rule")), policy.selectedRuleId());
        assertEquals(MatchSpecificity.TAG, policy.ruleSpecificity());
    }

    @Test
    void formatThreeKeepsIncludedCraftingTierAndMigratesOmissionsToUnrestricted() {
        ResourceLocation authored = id("test:legacy_authored");
        ResourceLocation omitted = id("test:legacy_omitted");
        BlueprintResearchProfile legacy = profile(
                BlueprintResearchProfile.PROGRESSION_FORMAT,
                Optional.of(TREE),
                BlueprintCraftingProfilePolicy.LEGACY);
        BlueprintResearchSnapshot research = authoredResearch(
                legacy,
                Map.of(),
                Map.of(),
                List.of(authoredEntry(authored, Tier.ELITE)));

        BlueprintGunCraftingPolicyResolver.Resolution result = resolve(
                research,
                gunCatalog(authored, omitted),
                1L,
                1L,
                Map.of(),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(1L));

        assertEquals(ResearchWorkbenchTier.TIER_3, tier(result, authored));
        assertEquals(BlueprintCraftingPolicySource.AUTHORED_BAND,
                policy(result, authored).source());
        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED,
                policy(result, omitted).disposition());
        assertEquals(BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY,
                policy(result, omitted).source());
        assertTrue(policy(result, omitted).warnings().contains(
                BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY));
    }

    @Test
    void independentCraftingRuleAppliesToLegacyOmissionWithoutResearchInclusion() {
        ResourceLocation omitted = id("test:legacy_rule_omitted");
        BlueprintResearchProfile legacy = profile(
                BlueprintResearchProfile.PROGRESSION_FORMAT,
                Optional.of(TREE),
                BlueprintCraftingProfilePolicy.LEGACY);
        BlueprintResearchSnapshot research = authoredResearch(
                legacy,
                Map.of(id("test:independent_rule"), craftingRule(
                        exactTarget(omitted),
                        0,
                        accessOverride(
                                BlueprintCraftingDisposition.TIERED,
                                Optional.of(ResearchWorkbenchTier.TIER_3)))),
                Map.of(),
                List.of());

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research,
                gunCatalog(omitted),
                1L,
                1L,
                Map.of(),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(1L)), omitted);

        assertEquals(ResearchWorkbenchTier.TIER_3,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.EXACT_RULE, policy.source());
        assertFalse(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY));
    }

    @Test
    void publishesOnlyCraftingApplicableProfileGatesAndHonorsGateOnlyRules() {
        ResourceLocation inherited = id("test:inherited_gates");
        ResourceLocation overridden = id("test:overridden_gates");
        ProgressionGateCondition researchOnly = criterion(
                "test:research_only", ProgressionGateScope.RESEARCH);
        ProgressionGateCondition craftingOnly = criterion(
                "test:crafting_only", ProgressionGateScope.CRAFTING);
        ProgressionGateCondition both = criterion("test:both", ProgressionGateScope.BOTH);
        ProgressionGateRequirements profileGates = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(researchOnly)),
                new ProgressionGateGroup(List.of(craftingOnly))));
        BlueprintProgressionProfilePolicy progression = new BlueprintProgressionProfilePolicy(
                BlueprintProgressionProfilePolicy.DEFAULT.fallbackTiers(),
                BlueprintProgressionProfilePolicy.DEFAULT.authoredTierBands(),
                BlueprintProgressionProfilePolicy.DEFAULT.fragments(),
                profileGates);
        BlueprintResearchProfile profile = profile(
                BlueprintResearchProfile.CURRENT_FORMAT,
                Optional.empty(),
                progression,
                BlueprintCraftingProfilePolicy.DEFAULT);
        BlueprintResearchRule gateOnlyRule = craftingRule(
                exactTarget(overridden),
                0,
                new BlueprintCraftingRuleOverride(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new ProgressionGateRequirements(List.of(
                                new ProgressionGateGroup(List.of(both)))))));
        BlueprintResearchSnapshot research = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile),
                Map.of(id("test:gate_only"), gateOnlyRule));

        BlueprintGunCraftingPolicyResolver.Resolution result = resolve(
                research,
                gunCatalog(inherited, overridden),
                1L,
                1L,
                Map.of(),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(1L));

        assertEquals(List.of(craftingOnly),
                policy(result, inherited).gates().allOf().get(0).anyOf());
        assertEquals(List.of(both),
                policy(result, overridden).gates().allOf().get(0).anyOf());
        assertEquals(BlueprintCraftingPolicySource.EXACT_RULE,
                policy(result, overridden).source());
    }

    @Test
    void rejectsStaleEvidenceIncompleteCandidatesAndExcessiveCrossProduct() {
        Map<ResourceLocation, BlueprintData> oneGun = gunCatalog(LOW);
        BlueprintResearchSnapshot automatic = automaticResearch(profile(
                BlueprintResearchProfile.CURRENT_FORMAT,
                Optional.of(TREE),
                BlueprintCraftingProfilePolicy.DEFAULT));

        assertThrows(IllegalArgumentException.class, () -> resolve(
                automatic,
                oneGun,
                2L,
                1L,
                Map.of(),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(1L)));

        AutomaticWeaponPlacementCandidateSnapshot wrongCoverage = candidates(
                2L,
                1L,
                Map.of(),
                Map.of());
        assertThrows(IllegalArgumentException.class, () -> resolve(
                automatic,
                oneGun,
                2L,
                1L,
                Map.of(TREE, wrongCoverage),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(2L)));

        Map<ResourceLocation, BlueprintResearchProfile> profiles = new LinkedHashMap<>();
        for (int index = 0; index < 257; index++) {
            profiles.put(id("test:profile_" + index), profile(
                    BlueprintResearchProfile.CURRENT_FORMAT,
                    Optional.empty(),
                    BlueprintCraftingProfilePolicy.DEFAULT));
        }
        Map<ResourceLocation, BlueprintData> largeCatalog = new LinkedHashMap<>();
        for (int index = 0; index < 1_021; index++) {
            ResourceLocation blueprintId = id("test:gun_" + index);
            largeCatalog.put(blueprintId, data(blueprintId, BlueprintKind.GUN));
        }
        BlueprintResearchSnapshot largeResearch = BlueprintResearchSnapshot.create(
                Map.of(), profiles, Map.of());
        assertThrows(IllegalArgumentException.class, () -> resolve(
                largeResearch,
                largeCatalog,
                1L,
                1L,
                Map.of(),
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(1L)));
    }

    private static BlueprintGunCraftingPolicyResolver.Resolution resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            long researchRevision,
            Map<ResourceLocation, AutomaticWeaponPlacementCandidateSnapshot> candidates,
            AutomaticWeaponEvidenceSnapshot evidence) {
        return BlueprintGunCraftingPolicyResolver.resolve(
                research,
                catalog,
                catalogRevision,
                researchRevision,
                9L,
                candidates,
                evidence,
                AutomaticWorkbenchTierPercentiles.DEFAULT);
    }

    private static ResolvedBlueprintCraftingPolicy policy(
            BlueprintGunCraftingPolicyResolver.Resolution result,
            ResourceLocation blueprintId) {
        return result.policy(PROFILE, blueprintId).orElseThrow();
    }

    private static ResearchWorkbenchTier tier(
            BlueprintGunCraftingPolicyResolver.Resolution result,
            ResourceLocation blueprintId) {
        return policy(result, blueprintId).requiredWorkbenchTier().orElseThrow();
    }

    private static BlueprintResearchSnapshot automaticResearch(
            BlueprintResearchProfile profile) {
        ResearchAutomaticPlacementProfile automaticProfile =
                new ResearchAutomaticPlacementProfile(
                        ResearchAutomaticPlacementProfile.LEGACY_FORMAT,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        60,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED);
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree(ResearchTechTreeDefinition.WeaponPlacementMode.AUTOMATIC)),
                Map.of(),
                Map.of(id("test:automatic"), automaticProfile));
    }

    private static BlueprintResearchSnapshot authoredResearch(
            BlueprintResearchProfile profile,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, BlueprintLootTag> tags,
            List<ResearchTechTreeEntryBundle.Entry> entries) {
        Map<ResourceLocation, ResearchTechTreeEntryBundle> bundles = entries.isEmpty()
                ? Map.of()
                : Map.of(id("test:entries"), new ResearchTechTreeEntryBundle(
                        ResearchTechTreeEntryBundle.LEGACY_FORMAT,
                        TREE,
                        0,
                        entries));
        return BlueprintResearchSnapshot.create(
                tags,
                Map.of(PROFILE, profile),
                rules,
                Map.of(),
                Map.of(TREE, tree(ResearchTechTreeDefinition.WeaponPlacementMode.AUTHORED_ONLY)),
                bundles);
    }

    private static BlueprintResearchProfile profile(
            int format,
            Optional<ResourceLocation> tree,
            BlueprintCraftingProfilePolicy crafting) {
        return profile(
                format,
                tree,
                BlueprintProgressionProfilePolicy.DEFAULT,
                crafting);
    }

    private static BlueprintResearchProfile profile(
            int format,
            Optional<ResourceLocation> tree,
            BlueprintProgressionProfilePolicy progression,
            BlueprintCraftingProfilePolicy crafting) {
        EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains =
                new EnumMap<>(Domain.class);
        for (Domain domain : Domain.values()) {
            domains.put(domain, BlueprintResearchProfile.DomainPolicy.ENABLED);
        }
        return new BlueprintResearchProfile(
                format,
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
                tree,
                BlueprintReverseEngineeringPolicy.DEFAULT,
                progression,
                crafting);
    }

    private static ResearchTechTreeDefinition tree(
            ResearchTechTreeDefinition.WeaponPlacementMode mode) {
        List<ResearchTechTreeDefinition.TierDefinition> tiers = Arrays.stream(Tier.values())
                .map(tier -> new ResearchTechTreeDefinition.TierDefinition(
                        tier, tier.name(), Optional.empty()))
                .toList();
        ResearchTechTreeDefinition.LaneDefinition lane =
                new ResearchTechTreeDefinition.LaneDefinition(
                        LANE, "Weapons", Optional.empty(), Optional.empty(), 0);
        ResearchTechTreeDefinition.DomainDefinition domain =
                new ResearchTechTreeDefinition.DomainDefinition(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        LANE,
                        Tier.STARTER,
                        List.of(lane));
        return new ResearchTechTreeDefinition(
                ResearchTechTreeDefinition.CURRENT_FORMAT,
                "Test Tree",
                Optional.empty(),
                Optional.empty(),
                mode,
                ResearchTechTreeDefinition.LayoutDefinition.DEFAULT,
                ResearchTechTreeDefinition.BandPolicyDefinition.NONE,
                tiers,
                List.of(domain));
    }

    private static ResearchTechTreeEntryBundle.Entry authoredEntry(
            ResourceLocation blueprintId,
            Tier tier) {
        return new ResearchTechTreeEntryBundle.Entry(
                exactTarget(blueprintId),
                Domain.WEAPONS,
                LANE,
                tier,
                0,
                Optional.empty(),
                Optional.empty());
    }

    private static BlueprintResearchRule ordinaryRule(
            BlueprintResearchTarget target,
            int priority) {
        return rule(target, priority, Optional.empty(), Optional.empty());
    }

    private static BlueprintResearchRule craftingRule(
            BlueprintResearchTarget target,
            int priority,
            BlueprintCraftingRuleOverride crafting) {
        return rule(target, priority, Optional.empty(), Optional.of(crafting));
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

    private static BlueprintCraftingRuleOverride accessOverride(
            BlueprintCraftingDisposition disposition,
            Optional<ResearchWorkbenchTier> tier) {
        return new BlueprintCraftingRuleOverride(
                Optional.of(disposition), tier, Optional.empty());
    }

    private static BlueprintResearchTarget exactTarget(ResourceLocation id) {
        return new BlueprintResearchTarget(List.of(id), List.of(), Optional.empty());
    }

    private static ProgressionGateCondition criterion(
            String criterionId,
            ProgressionGateScope scope) {
        return ProgressionGateCondition.Criterion.of(
                criterionId,
                1,
                scope,
                "taczweaponblueprints.gate.test",
                Disclosure.PUBLIC);
    }

    private static AutomaticWeaponPlacementCandidateSnapshot candidates(
            long catalogRevision,
            long researchRevision,
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, String> excluded) {
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED);
        return new AutomaticWeaponPlacementCandidateSnapshot(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                policy,
                catalogRevision,
                researchRevision,
                proposals.size() + excluded.size(),
                proposals,
                excluded,
                Set.of(),
                Set.of());
    }

    private static AutomaticWeaponPlacementProposal proposal(
            ResourceLocation blueprintId,
            int score,
            boolean review,
            long siblingOrder) {
        return new AutomaticWeaponPlacementProposal(
                blueprintId.toString(),
                score,
                review ? 40 : 100,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, 3),
                        siblingOrder),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                review ? List.of("test_review") : List.of());
    }

    private static AutomaticWeaponEvidenceSnapshot evidence(
            long catalogRevision,
            List<ResourceLocation> blueprintIds) {
        return evidence(catalogRevision, blueprintIds, false);
    }

    private static AutomaticWeaponEvidenceSnapshot evidence(
            long catalogRevision,
            List<ResourceLocation> blueprintIds,
            boolean scriptControlled) {
        WeaponMechanicalReferenceCatalog mechanicalReference =
                WeaponMechanicalReferenceCatalog.bundled();
        Map<String, WeaponStatEvidence> evidence = new LinkedHashMap<>();
        Map<String, WeaponMechanicalScore> mechanicalScores = new LinkedHashMap<>();
        Map<String, WeaponCapabilityScore> capabilityScores = new LinkedHashMap<>();
        for (int index = 0; index < blueprintIds.size(); index++) {
            ResourceLocation blueprintId = blueprintIds.get(index);
            WeaponStatEvidence raw = weaponEvidence(
                    blueprintId.toString(), 8.0 + index * 30.0, scriptControlled);
            evidence.put(blueprintId.toString(), raw);
            mechanicalScores.put(
                    blueprintId.toString(),
                    new WeaponMechanicalScorer().score(raw, mechanicalReference.reference()));
            WeaponCapabilityScore scored = new WeaponCapabilityScorer().score(
                    raw,
                    WeaponCapabilityReferenceCatalog.bundled().reference());
            capabilityScores.put(
                    blueprintId.toString(),
                    scriptControlled ? scored : trusted(scored));
        }
        AutomaticWeaponPlacementPlan plan = new AutomaticWeaponPlacementPlanner().plan(
                mechanicalScores,
                blueprintIds.stream().map(ResourceLocation::toString).toList(),
                AutomaticWeaponPlacementPolicy.DEFAULT);
        return new AutomaticWeaponEvidenceSnapshot(
                catalogRevision,
                mechanicalReference.referenceVersion(),
                mechanicalReference.sourceVersion(),
                evidence.size(),
                mechanicalReference.blueprintIds().size(),
                0,
                Set.of(),
                evidence,
                mechanicalScores,
                capabilityScores,
                Map.of(),
                plan);
    }

    private static WeaponCapabilityScore trusted(WeaponCapabilityScore score) {
        return new WeaponCapabilityScore(
                score.evidence(),
                score.progressionScore(),
                score.combatStrength(),
                score.handling(),
                score.versatility(),
                100,
                score.formulaVersion(),
                score.referenceVersion(),
                score.packageScores(),
                score.packageConfidence(),
                score.observedMetrics(),
                score.resolvedMetrics(),
                score.metricScores(),
                List.of());
    }

    private static WeaponStatEvidence weaponEvidence(String blueprintId, double damage) {
        return weaponEvidence(blueprintId, damage, false);
    }

    private static WeaponStatEvidence weaponEvidence(
            String blueprintId,
            double damage,
            boolean scriptControlled) {
        return new WeaponStatEvidence(
                blueprintId,
                "rifle",
                damage,
                0.0,
                500.0,
                15,
                2.0,
                100.0,
                50.0,
                0.1,
                1.5,
                1,
                0.2,
                0.3,
                2.0,
                0.2,
                0.4,
                -0.2,
                1,
                2,
                null,
                "magazine",
                false,
                scriptControlled,
                List.of());
    }

    private static Map<ResourceLocation, BlueprintData> gunCatalog(
            ResourceLocation... blueprintIds) {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        for (ResourceLocation blueprintId : blueprintIds) {
            catalog.put(blueprintId, data(blueprintId, BlueprintKind.GUN));
        }
        return catalog;
    }

    private static BlueprintData data(
            ResourceLocation blueprintId,
            BlueprintKind kind) {
        return new BlueprintData(
                blueprintId.toString(),
                "item." + blueprintId.getNamespace() + "." + blueprintId.getPath(),
                "tooltip.test",
                id(blueprintId.getNamespace() + ":recipe/" + blueprintId.getPath()),
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
