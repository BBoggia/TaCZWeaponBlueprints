package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

class BlueprintAttachmentCraftingPolicyResolverTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation SCOPE = id("test:scope");
    private static final ResourceLocation GRIP = id("test:grip");
    private static final ResourceLocation MALFORMED = id("test:malformed_type");
    private static final long CATALOG_REVISION = 4L;
    private static final long RESEARCH_REVISION = 7L;
    private static final long AUTOMATIC_REVISION = 9L;

    @Test
    void canonicalTypeMappingsAndUnknownFallbacksAreExplicit() {
        BlueprintAttachmentCraftingPolicy attachments =
                new BlueprintAttachmentCraftingPolicy(
                        BlueprintAttachmentCraftingPolicy.Mode.TYPE_MAPPED,
                        BlueprintCraftingAccessPolicy.TIER_3,
                        Map.of(
                                "scope", BlueprintCraftingAccessPolicy.UNRESTRICTED,
                                "muzzle", BlueprintCraftingAccessPolicy.DISABLED));
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(SCOPE, data(SCOPE, "scope"));
        catalog.put(GRIP, data(GRIP, "grip"));
        catalog.put(MALFORMED, data(MALFORMED, "Scope"));

        var result = resolve(research(attachments, Map.of()), catalog);
        ResolvedBlueprintCraftingPolicy scope = policy(result, SCOPE);
        ResolvedBlueprintCraftingPolicy grip = policy(result, GRIP);
        ResolvedBlueprintCraftingPolicy malformed = policy(result, MALFORMED);

        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED, scope.disposition());
        assertEquals(BlueprintCraftingPolicySource.CATEGORY_DEFAULT, scope.source());
        assertEquals("attachment_item_type_mapping", scope.reasonCode());
        assertEquals(ResearchWorkbenchTier.TIER_3,
                grip.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.PROFILE_FALLBACK, grip.source());
        assertTrue(grip.warnings().contains(
                BlueprintCraftingPolicyWarning.UNKNOWN_ATTACHMENT_TYPE));
        assertEquals(ResearchWorkbenchTier.TIER_3,
                malformed.requiredWorkbenchTier().orElseThrow());
        assertTrue(malformed.warnings().contains(
                BlueprintCraftingPolicyWarning.UNKNOWN_ATTACHMENT_TYPE));
    }

    @Test
    void directAttachmentModesResolveWithoutTypeEvidence() {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                SCOPE, data(SCOPE, null));

        assertEquals(ResearchWorkbenchTier.TIER_2,
                policy(resolve(
                        research(
                                BlueprintAttachmentCraftingPolicy.fixed(
                                        BlueprintCraftingAccessPolicy.TIER_2),
                                Map.of()),
                        catalog), SCOPE).requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED,
                policy(resolve(
                        research(BlueprintAttachmentCraftingPolicy.unrestricted(), Map.of()),
                        catalog), SCOPE).disposition());
        assertEquals(BlueprintCraftingDisposition.DISABLED,
                policy(resolve(
                        research(BlueprintAttachmentCraftingPolicy.disabled(), Map.of()),
                        catalog), SCOPE).disposition());
    }

    @Test
    void exactCraftingRuleOverridesAnUnknownTypeFallback() {
        ResourceLocation ruleId = id("test:attachment_override");
        BlueprintResearchRule rule = craftingRule(
                MALFORMED,
                new BlueprintCraftingRuleOverride(
                        Optional.of(BlueprintCraftingDisposition.TIERED),
                        Optional.of(ResearchWorkbenchTier.TIER_2),
                        Optional.empty()));
        BlueprintAttachmentCraftingPolicy attachments =
                new BlueprintAttachmentCraftingPolicy(
                        BlueprintAttachmentCraftingPolicy.Mode.TYPE_MAPPED,
                        BlueprintCraftingAccessPolicy.TIER_1,
                        Map.of("scope", BlueprintCraftingAccessPolicy.TIER_1));

        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                research(attachments, Map.of(ruleId, rule)),
                Map.of(MALFORMED, data(MALFORMED, "unknown"))), MALFORMED);

        assertEquals(ResearchWorkbenchTier.TIER_2,
                policy.requiredWorkbenchTier().orElseThrow());
        assertEquals(BlueprintCraftingPolicySource.EXACT_RULE, policy.source());
        assertEquals(Optional.of(ruleId), policy.selectedRuleId());
        assertEquals(MatchSpecificity.EXACT, policy.ruleSpecificity());
        assertTrue(policy.warnings().isEmpty());
    }

    @Test
    void legacyOmittedAttachmentsRemainExplicitlyUnrestricted() {
        ResolvedBlueprintCraftingPolicy policy = policy(resolve(
                legacyResearchWithAttachmentsDisabled(),
                Map.of(SCOPE, data(SCOPE, "scope"))), SCOPE);

        assertEquals(BlueprintCraftingDisposition.UNRESTRICTED, policy.disposition());
        assertEquals(BlueprintCraftingPolicySource.MIGRATED_COMPATIBILITY,
                policy.source());
        assertTrue(policy.warnings().contains(
                BlueprintCraftingPolicyWarning.MIGRATED_COMPATIBILITY));
    }

    @Test
    void resolverCoversOnlyAttachmentsAndRejectsStaleOrMismatchedInputs() {
        ResourceLocation gun = id("test:gun");
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(gun, data(gun, "rifle", BlueprintKind.GUN));
        catalog.put(SCOPE, data(SCOPE, "scope"));

        var result = resolve(
                research(BlueprintAttachmentCraftingPolicy.DEFAULT, Map.of()),
                catalog);
        assertEquals(Set.of(SCOPE), result.attachmentBlueprintIds());
        assertEquals(Set.of(SCOPE), result.policiesByProfile().get(PROFILE).keySet());

        assertThrows(IllegalArgumentException.class, () ->
                BlueprintAttachmentCraftingPolicyResolver.resolve(
                        research(BlueprintAttachmentCraftingPolicy.DEFAULT, Map.of()),
                        catalog,
                        CATALOG_REVISION,
                        0L,
                        AUTOMATIC_REVISION));

        Map<ResourceLocation, BlueprintData> mismatched = Map.of(
                SCOPE, data(id("test:different"), "scope"));
        assertThrows(IllegalArgumentException.class, () -> resolve(
                research(BlueprintAttachmentCraftingPolicy.DEFAULT, Map.of()),
                mismatched));
    }

    @Test
    void canonicalAttachmentTypesHaveStrictStableSyntax() {
        assertTrue(BlueprintAttachmentCraftingPolicy.isCanonicalItemType("extended_mag"));
        assertTrue(BlueprintAttachmentCraftingPolicy.isCanonicalItemType("scope.v2"));
        assertFalse(BlueprintAttachmentCraftingPolicy.isCanonicalItemType("Scope"));
        assertFalse(BlueprintAttachmentCraftingPolicy.isCanonicalItemType("scope name"));
        assertFalse(BlueprintAttachmentCraftingPolicy.isCanonicalItemType(null));
    }

    private static BlueprintAttachmentCraftingPolicyResolver.Resolution resolve(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog) {
        return BlueprintAttachmentCraftingPolicyResolver.resolve(
                research,
                catalog,
                CATALOG_REVISION,
                RESEARCH_REVISION,
                AUTOMATIC_REVISION);
    }

    private static ResolvedBlueprintCraftingPolicy policy(
            BlueprintAttachmentCraftingPolicyResolver.Resolution result,
            ResourceLocation blueprintId) {
        return result.policy(PROFILE, blueprintId).orElseThrow();
    }

    private static BlueprintResearchSnapshot research(
            BlueprintAttachmentCraftingPolicy attachments,
            Map<ResourceLocation, BlueprintResearchRule> rules) {
        BlueprintCraftingProfilePolicy crafting = new BlueprintCraftingProfilePolicy(
                BlueprintAuthoredGunCraftingPolicy.DEFAULT,
                BlueprintCraftingStrategy.OMITTED_DEFAULT,
                BlueprintCraftingStrategy.AUTOMATIC_DEFAULT,
                BlueprintCraftingStrategy.AMMO_DEFAULT,
                attachments,
                BlueprintCraftingAccessPolicy.TIER_1);
        BlueprintResearchProfile profile = profile(
                BlueprintResearchProfile.CURRENT_FORMAT,
                enabledDomains(),
                crafting);
        return BlueprintResearchSnapshot.create(
                Map.of(), Map.of(PROFILE, profile), rules);
    }

    private static BlueprintResearchSnapshot legacyResearchWithAttachmentsDisabled() {
        EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains = enabledDomains();
        domains.put(Domain.ATTACHMENTS,
                new BlueprintResearchProfile.DomainPolicy(false, false));
        BlueprintResearchProfile profile = profile(
                BlueprintResearchProfile.PROGRESSION_FORMAT,
                domains,
                BlueprintCraftingProfilePolicy.LEGACY);
        return BlueprintResearchSnapshot.create(
                Map.of(), Map.of(PROFILE, profile), Map.of());
    }

    private static BlueprintResearchProfile profile(
            int format,
            EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains,
            BlueprintCraftingProfilePolicy crafting) {
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
                Optional.empty(),
                BlueprintReverseEngineeringPolicy.DEFAULT,
                BlueprintProgressionProfilePolicy.DEFAULT,
                crafting);
    }

    private static EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> enabledDomains() {
        EnumMap<Domain, BlueprintResearchProfile.DomainPolicy> domains =
                new EnumMap<>(Domain.class);
        for (Domain domain : Domain.values()) {
            domains.put(domain, BlueprintResearchProfile.DomainPolicy.ENABLED);
        }
        return domains;
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

    private static BlueprintData data(ResourceLocation id, String itemType) {
        return data(id, itemType, BlueprintKind.ATTACHMENT);
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
                id("tacz:" + (itemType == null ? "unknown" : itemType.toLowerCase())),
                kind,
                1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
