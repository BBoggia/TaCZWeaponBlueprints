package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintResearchPolicyResolverTest {
    @AfterEach
    void clearCache() {
        BlueprintResearchPolicyResolver.clearCache();
    }

    @Test
    void exactThenTagThenSelectorPrecedenceUsesOneRuleOverlay() {
        ResourceLocation blueprintId = id("test:rifle");
        ResourceLocation taggedId = id("test:tagged_rifle");
        ResourceLocation tagId = id("test:featured");
        BlueprintResearchRule selector = rule(
                "selector",
                1000,
                target(List.of(), List.of(), selector(List.of("test"), List.of("rifle"))),
                Optional.empty(),
                Optional.of(2),
                Optional.of(JournalVisibility.NAME),
                Optional.empty());
        BlueprintResearchRule tag = rule(
                "tag",
                -1000,
                target(List.of(), List.of(tagId), null),
                Optional.empty(),
                Optional.of(3),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchRule exact = rule(
                "exact",
                -1000,
                target(List.of(blueprintId), List.of(), null),
                Optional.of(new BlueprintResearchCost(30, List.of())),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(tagId, new BlueprintLootTag(1, List.of(blueprintId, taggedId))),
                Map.of(profileId(), profile(false)),
                Map.of(
                        id("test:selector"), selector,
                        id("test:tag"), tag,
                        id("test:exact"), exact));

        BlueprintResearchPolicy policy = resolve(snapshot, catalog(blueprintId, "rifle"), blueprintId, null);

        assertEquals(id("test:exact"), policy.ruleId().orElseThrow());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.EXACT, policy.specificity());
        assertEquals(30, policy.researchCost().points());
        assertEquals(1, policy.recyclingValue());
        assertEquals(JournalVisibility.SILHOUETTE, policy.visibility());

        BlueprintResearchPolicy tagged = resolve(snapshot, catalog(taggedId, "rifle"), taggedId, null);
        assertEquals(id("test:tag"), tagged.ruleId().orElseThrow());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.TAG, tagged.specificity());
        assertEquals(3, tagged.recyclingValue());
    }

    @Test
    void exactCraftingOnlyRuleCannotMaskBroaderResearchDefinition() {
        ResourceLocation blueprintId = id("test:rifle");
        ResourceLocation tagId = id("test:featured");
        ResourceLocation prerequisiteId = id("test:basic");
        BlueprintResearchRule broaderResearch = rule(
                "research",
                -1000,
                target(List.of(), List.of(tagId), null),
                Optional.of(new BlueprintResearchCost(37, List.of())),
                Optional.of(6),
                Optional.of(JournalVisibility.NAME),
                Optional.empty());
        BlueprintResearchRule exactCrafting = craftingOnlyRule(
                blueprintId,
                1000,
                BlueprintCraftingDisposition.UNRESTRICTED);
        BlueprintResearchRule prerequisite = rule(
                "prerequisite",
                0,
                target(List.of(prerequisiteId), List.of(), null),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(tagId, new BlueprintLootTag(1, List.of(blueprintId))),
                Map.of(profileId(), profile(false)),
                Map.of(
                        id("test:research"), broaderResearch,
                        id("test:crafting"), exactCrafting,
                        id("test:prerequisite"), prerequisite));

        Map<ResourceLocation, BlueprintData> catalog = new java.util.LinkedHashMap<>();
        catalog.putAll(catalog(blueprintId, "rifle"));
        catalog.putAll(catalog(prerequisiteId, "rifle"));
        BlueprintResearchPolicy policy = resolve(snapshot, catalog, blueprintId, null);
        BlueprintResearchPolicyResolver.RuleSelection researchSelection =
                BlueprintResearchDiagnostics.inspectSelection(
                        snapshot, catalog, profileId(), blueprintId);
        BlueprintResearchPolicyResolver.RuleSelection craftingSelection =
                BlueprintResearchPolicyResolver.craftingRuleSelection(
                        snapshot, profileId(), blueprintId, catalog.get(blueprintId));

        assertEquals(id("test:research"), policy.ruleId().orElseThrow());
        assertEquals(id("test:research"), researchSelection.selectedRuleId().orElseThrow());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.TAG, researchSelection.specificity());
        assertEquals(37, policy.researchCost().points());
        assertEquals(6, policy.recyclingValue());
        assertEquals(JournalVisibility.NAME, policy.visibility());
        assertEquals(id("test:crafting"), craftingSelection.selectedRuleId().orElseThrow());
        assertEquals(BlueprintResearchTarget.MatchSpecificity.EXACT, craftingSelection.specificity());
    }

    @Test
    void craftingOnlyRuleCannotHideSelectedResearchPrerequisitesFromSnapshotValidation() {
        ResourceLocation blueprintId = id("test:rifle");
        BlueprintResearchRule cyclicResearch = rule(
                "cyclic",
                0,
                target(List.of(blueprintId), List.of(), null),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(blueprintId)));
        BlueprintResearchRule exactCrafting = craftingOnlyRule(
                blueprintId,
                1000,
                BlueprintCraftingDisposition.UNRESTRICTED);

        assertThrows(IllegalArgumentException.class, () -> BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false)),
                Map.of(
                        id("test:cyclic"), cyclicResearch,
                        id("test:crafting"), exactCrafting)));
    }

    @Test
    void formatTwoDomainPolicyIsTheFinalNonOverridableTreeAndResearchGate() {
        ResourceLocation weaponId = id("test:weapon");
        ResourceLocation attachmentId = id("test:attachment");
        ResourceLocation ammoId = id("test:ammo");
        BlueprintResearchRule attachmentRule = new BlueprintResearchRule(
                1,
                profileId(),
                100,
                target(List.of(attachmentId), List.of(), null),
                Optional.empty(),
                Optional.of(true),
                Optional.of(true),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), formatTwoProfile(false, false)),
                Map.of(id("test:attachment_rule"), attachmentRule));
        Map<ResourceLocation, BlueprintData> catalog = new java.util.LinkedHashMap<>();
        catalog.putAll(catalog(weaponId, "rifle"));
        catalog.putAll(catalog(attachmentId, "scope"));
        catalog.putAll(catalog(ammoId, "ammo"));

        BlueprintResearchPolicyDefinition weapon = BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, profileId(), weaponId);
        BlueprintResearchPolicyDefinition attachment = BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, profileId(), attachmentId);
        BlueprintResearchPolicyDefinition ammo = BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, profileId(), ammoId);

        assertTrue(weapon.treeEnabled());
        assertTrue(weapon.researchEnabled());
        assertFalse(attachment.treeEnabled());
        assertFalse(attachment.researchEnabled());
        assertEquals(id("test:attachment_rule"), attachment.ruleId().orElseThrow());
        assertFalse(ammo.treeEnabled());
        assertFalse(ammo.researchEnabled());
        assertTrue(attachment.recyclingEnabled());
        assertEquals(
                BlueprintReverseEngineeringPolicy.DEFAULT,
                attachment.reverseEngineering());
    }

    @Test
    void formatTwoCanOptAttachmentAndAmmoResearchBackIn() {
        ResourceLocation attachmentId = id("test:attachment");
        ResourceLocation ammoId = id("test:ammo");
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), formatTwoProfile(true, true)),
                Map.of());

        assertTrue(BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog(attachmentId, "scope"), profileId(), attachmentId).treeEnabled());
        assertTrue(BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog(ammoId, "ammo"), profileId(), ammoId).researchEnabled());
    }

    @Test
    void priorityAndDefinitionIdResolveSameSpecificityDeterministically() {
        ResourceLocation blueprintId = id("test:rifle");
        BlueprintResearchRule low = rule(
                "low",
                1,
                target(List.of(blueprintId), List.of(), null),
                Optional.empty(),
                Optional.of(2),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchRule highA = rule(
                "a",
                5,
                target(List.of(blueprintId), List.of(), null),
                Optional.empty(),
                Optional.of(4),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchRule highZ = rule(
                "z",
                5,
                target(List.of(blueprintId), List.of(), null),
                Optional.empty(),
                Optional.of(5),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false)),
                Map.of(
                        id("test:low"), low,
                        id("test:a"), highA,
                        id("test:z"), highZ));

        BlueprintResearchPolicy policy = resolve(snapshot, catalog(blueprintId, "rifle"), blueprintId, null);
        BlueprintResearchPolicyResolver.RuleSelection selection =
                BlueprintResearchDiagnostics.inspectSelection(
                        snapshot,
                        catalog(blueprintId, "rifle"),
                        profileId(),
                        blueprintId);

        assertEquals(id("test:a"), policy.ruleId().orElseThrow());
        assertEquals(4, policy.recyclingValue());
        assertTrue(selection.hasTie());
        assertEquals(List.of(id("test:a"), id("test:z")), selection.tiedRuleIds());
    }

    @Test
    void policyCombinesAvailabilityProgressionVisibilityAndPrerequisites() {
        ResourceLocation advanced = id("test:advanced");
        ResourceLocation basic = id("test:basic");
        BlueprintResearchRule rule = rule(
                "advanced",
                0,
                target(List.of(advanced), List.of(), null),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(basic)));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(true)),
                Map.of(id("test:advanced_rule"), rule));
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        data.discoverBlueprint(advanced.toString());

        BlueprintResearchPolicy missingPrerequisite = resolve(
                snapshot, catalog(advanced, "rifle"), advanced, data);
        assertEquals(JournalVisibility.PREVIEW, missingPrerequisite.visibility());
        assertFalse(missingPrerequisite.researchable());
        assertTrue(missingPrerequisite.canAffordPoints());

        data.addBlueprint(basic.toString());
        BlueprintResearchPolicy researchable = resolve(snapshot, catalog(advanced, "rifle"), advanced, data);
        assertTrue(researchable.researchable());

        data.addBlueprint(advanced.toString());
        BlueprintResearchPolicy learned = resolve(snapshot, catalog(advanced, "rifle"), advanced, data);
        assertEquals(JournalVisibility.FULL, learned.visibility());
        assertFalse(learned.researchable());
        assertTrue(learned.recyclable());

        BlueprintResearchPolicy blocked = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog(advanced, "rifle"),
                profileId(),
                advanced,
                data,
                ignored -> true);
        assertTrue(blocked.blocked());
        assertFalse(blocked.researchable());
        assertFalse(blocked.recyclable());

        BlueprintResearchPolicy unavailable = resolve(snapshot, Map.of(), advanced, data);
        assertFalse(unavailable.researchable());
        assertFalse(unavailable.recyclable());
    }

    @Test
    void liveExemptionSatisfiesPrerequisitesWithoutWritingLearnedKnowledge() {
        ResourceLocation advanced = id("test:advanced");
        ResourceLocation basic = id("test:basic");
        BlueprintResearchRule rule = rule(
                "advanced",
                0,
                target(List.of(advanced), List.of(), null),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(basic)));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false)),
                Map.of(id("test:advanced_rule"), rule));
        Map<ResourceLocation, BlueprintData> catalog = new java.util.LinkedHashMap<>();
        catalog.putAll(catalog(advanced, "rifle"));
        catalog.putAll(catalog(basic, "pistol"));
        PlayerRecipeData data = new PlayerRecipeData();

        BlueprintResearchPolicy policy = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                profileId(),
                advanced,
                data,
                ignored -> false,
                basic::equals);

        assertTrue(policy.prerequisitesSatisfied());
        assertTrue(policy.researchable());
        assertFalse(data.hasBlueprint(basic.toString()));
    }

    @Test
    void groupedRequirementsUseAndAcrossGroupsAndOrWithinEachGroup() {
        ResourceLocation advanced = id("test:advanced");
        ResourceLocation routeA = id("test:route_a");
        ResourceLocation routeB = id("test:route_b");
        ResourceLocation supportA = id("test:support_a");
        ResourceLocation supportB = id("test:support_b");
        ResearchRequirements requirements = new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(routeA, routeB)),
                new ResearchPrerequisiteGroup(List.of(supportA, supportB))));
        BlueprintResearchRule grouped = new BlueprintResearchRule(
                BlueprintResearchRule.CURRENT_FORMAT,
                profileId(),
                0,
                target(List.of(advanced), List.of(), null),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(requirements),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false)),
                Map.of(id("test:advanced_rule"), grouped));
        Map<ResourceLocation, BlueprintData> catalog = new java.util.LinkedHashMap<>();
        for (ResourceLocation id : List.of(
                advanced, routeA, routeB, supportA, supportB)) {
            catalog.putAll(catalog(id, "rifle"));
        }

        PlayerRecipeData incomplete = new PlayerRecipeData();
        incomplete.addBlueprint(routeA.toString());
        assertFalse(resolve(snapshot, catalog, advanced, incomplete)
                .prerequisitesSatisfied());

        PlayerRecipeData complete = new PlayerRecipeData();
        complete.addBlueprint(routeB.toString());
        complete.addBlueprint(supportA.toString());
        BlueprintResearchPolicy policy = resolve(snapshot, catalog, advanced, complete);
        assertTrue(policy.prerequisitesSatisfied());
        assertEquals(requirements, policy.requirements());
        assertEquals(4, policy.prerequisites().size());

        PlayerRecipeData exemptRoute = new PlayerRecipeData();
        exemptRoute.addBlueprint(supportB.toString());
        assertTrue(BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                profileId(),
                advanced,
                exemptRoute,
                ignored -> false,
                routeA::equals).prerequisitesSatisfied());
    }

    @Test
    void explicitRestrictiveRuleCanHideDiscoveredMetadata() {
        ResourceLocation blueprintId = id("test:hidden");
        BlueprintResearchRule hidden = rule(
                "hidden",
                0,
                target(List.of(blueprintId), List.of(), null),
                Optional.empty(),
                Optional.empty(),
                Optional.of(JournalVisibility.HIDDEN),
                Optional.empty());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false)),
                Map.of(id("test:hidden"), hidden));
        PlayerRecipeData data = new PlayerRecipeData();
        data.discoverBlueprint(blueprintId.toString());

        assertEquals(
                JournalVisibility.HIDDEN,
                resolve(snapshot, catalog(blueprintId, "rifle"), blueprintId, data).visibility());
    }

    @Test
    void missingContentRemainsDormantAndCatalogAndSnapshotIdentityInvalidateCache() {
        ResourceLocation blueprintId = id("test:item");
        BlueprintResearchRule rifleRule = rule(
                "rifle",
                0,
                target(List.of(), List.of(), selector(List.of("test"), List.of("rifle"))),
                Optional.empty(),
                Optional.of(4),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchSnapshot firstSnapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false)),
                Map.of(id("test:rifle"), rifleRule));

        assertEquals(4, resolve(firstSnapshot, catalog(blueprintId, "rifle"), blueprintId, null).recyclingValue());
        assertEquals(1, resolve(firstSnapshot, catalog(blueprintId, "ammo"), blueprintId, null).recyclingValue());

        BlueprintResearchRule exact = rule(
                "exact",
                0,
                target(List.of(blueprintId), List.of(), null),
                Optional.empty(),
                Optional.of(6),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchSnapshot secondSnapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false)),
                Map.of(id("test:exact"), exact));
        assertEquals(6, resolve(secondSnapshot, Map.of(), blueprintId, null).recyclingValue());
        assertFalse(resolve(secondSnapshot, Map.of(), blueprintId, null).available());
    }

    @Test
    void permissiveRecyclingStillRequiresPlayerData() {
        ResourceLocation blueprintId = id("test:recyclable");
        BlueprintResearchProfile permissive = new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.SILHOUETTE,
                true,
                true,
                true,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false);
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), permissive),
                Map.of());

        BlueprintResearchPolicy unavailableData = resolve(
                snapshot,
                catalog(blueprintId, "rifle"),
                blueprintId,
                null);
        assertFalse(unavailableData.playerDataAvailable());
        assertFalse(unavailableData.recyclable());
        assertFalse(unavailableData.canAffordPoints());

        BlueprintResearchPolicy availableData = resolve(
                snapshot,
                catalog(blueprintId, "rifle"),
                blueprintId,
                new PlayerRecipeData());
        assertTrue(availableData.playerDataAvailable());
        assertTrue(availableData.recyclable());
    }

    @Test
    void cacheRetainsMultipleProfilesAndResolvesEntriesLazily() {
        ResourceLocation blueprintId = id("test:cached");
        ResourceLocation alternateProfile = id("test:alternate");
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile(false), alternateProfile, profile(false)),
                Map.of());
        Map<ResourceLocation, BlueprintData> catalog = catalog(blueprintId, "rifle");

        BlueprintResearchPolicyResolver.resolve(
                snapshot, catalog, profileId(), blueprintId, new PlayerRecipeData(), ignored -> false);
        BlueprintResearchPolicyResolver.resolve(
                snapshot, catalog, alternateProfile, blueprintId, new PlayerRecipeData(), ignored -> false);
        assertEquals(2, BlueprintResearchPolicyResolver.cacheStateCount());

        BlueprintResearchPolicyResolver.resolve(
                snapshot, catalog, profileId(), blueprintId, new PlayerRecipeData(), ignored -> false);
        assertEquals(2, BlueprintResearchPolicyResolver.cacheStateCount());
    }

    private static BlueprintResearchPolicy resolve(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation blueprintId,
            PlayerRecipeData data) {
        return BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                profileId(),
                blueprintId,
                data,
                ignored -> false);
    }

    private static BlueprintResearchProfile profile(boolean requiresDiscovery) {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.SILHOUETTE,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                requiresDiscovery,
                false);
    }

    private static BlueprintResearchProfile formatTwoProfile(
            boolean attachmentsEnabled,
            boolean ammoEnabled) {
        return new BlueprintResearchProfile(
                2,
                true,
                JournalVisibility.SILHOUETTE,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                Map.of(
                        Domain.WEAPONS, BlueprintResearchProfile.DomainPolicy.ENABLED,
                        Domain.ATTACHMENTS, new BlueprintResearchProfile.DomainPolicy(
                                attachmentsEnabled, attachmentsEnabled),
                        Domain.AMMO, new BlueprintResearchProfile.DomainPolicy(
                                ammoEnabled, ammoEnabled)),
                List.of(),
                Map.of(),
                Optional.empty(),
                BlueprintReverseEngineeringPolicy.DEFAULT);
    }

    private static BlueprintResearchRule rule(
            String ignoredName,
            int priority,
            BlueprintResearchTarget target,
            Optional<BlueprintResearchCost> cost,
            Optional<Integer> recyclingValue,
            Optional<JournalVisibility> visibility,
            Optional<List<ResourceLocation>> prerequisites) {
        return new BlueprintResearchRule(
                1,
                profileId(),
                priority,
                target,
                visibility,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                recyclingValue,
                cost,
                Optional.empty(),
                prerequisites,
                Optional.empty());
    }

    private static BlueprintResearchRule craftingOnlyRule(
            ResourceLocation blueprintId,
            int priority,
            BlueprintCraftingDisposition disposition) {
        return new BlueprintResearchRule(
                BlueprintResearchRule.CRAFTING_FORMAT,
                profileId(),
                priority,
                target(List.of(blueprintId), List.of(), null),
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
                Optional.of(new BlueprintCraftingRuleOverride(
                        Optional.of(disposition),
                        Optional.empty(),
                        Optional.empty())));
    }

    private static BlueprintResearchTarget target(
            List<ResourceLocation> blueprints,
            List<ResourceLocation> tags,
            BlueprintCatalogSelector selector) {
        return new BlueprintResearchTarget(blueprints, tags, Optional.ofNullable(selector));
    }

    private static BlueprintCatalogSelector selector(List<String> namespaces, List<String> itemTypes) {
        return new BlueprintCatalogSelector(namespaces, itemTypes, List.of(), List.of(), 1.0f);
    }

    private static Map<ResourceLocation, BlueprintData> catalog(ResourceLocation id, String type) {
        return Map.of(id, new BlueprintData(
                id.toString(),
                "item.test.name",
                "item.test.tooltip",
                new ResourceLocation("test", "recipe/" + id.getPath()),
                null,
                type,
                new ResourceLocation("test", "display/" + type)));
    }

    private static ResourceLocation profileId() {
        return id("test:profile");
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(value);
        }
        return id;
    }
}
