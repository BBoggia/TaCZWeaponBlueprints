package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.LearningTarget;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.Request;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.Result;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.Status;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintLearningServiceTest {
    private static final ResourceLocation BLUEPRINT = id("test:phase_two_rifle");
    private static final ResourceLocation RECIPE = id("test:gun/phase_two_rifle");
    private static final ResourceLocation PROFILE = id("test:phase_two_profile");
    private static final LearningTarget TARGET = new LearningTarget(BLUEPRINT, RECIPE);

    @Test
    void physicalBlueprintBypassCommitsCanonicalKnowledgeAtomically() {
        PlayerRecipeData data = new PlayerRecipeData();
        Request request = request(
                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                false);

        Result result = learn(request, data, policy(data, true, false, false));

        assertTrue(result.successful());
        assertEquals(BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT, result.origin());
        assertTrue(result.learnedChanged());
        assertTrue(result.discoveredChanged());
        assertTrue(result.legacyRecipeChanged());
        assertTrue(result.prerequisitesBypassed());
        assertTrue(result.liveAwardsEligible());
        assertEquals(Set.of(BLUEPRINT.toString()), data.getLearnedBlueprints());
        assertEquals(Set.of(BLUEPRINT.toString()), data.getDiscoveredBlueprints());
        assertEquals(Set.of(RECIPE.toString()), data.getLearnedRecipes());
        assertEquals(1, data.getRecentUnlockBatches().size());
        assertEquals(BLUEPRINT.toString(),
                data.getRecentUnlockBatches().get(0).targetBlueprintId());
    }

    @Test
    void priorDiscoveryDoesNotCreateASecondDiscoveryTransition() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.discoverBlueprint(BLUEPRINT.toString()));

        Result result = learn(
                request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        true,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        false),
                data,
                policy(data, true, false, true));

        assertTrue(result.successful());
        assertFalse(result.discoveredChanged());
        assertTrue(result.learnedChanged());
        assertTrue(result.legacyRecipeChanged());
    }

    @Test
    void physicalModeAndTreeOriginApplyTheirOwnPrerequisiteRules() {
        PlayerRecipeData requiredData = new PlayerRecipeData();
        Result physicalRequired = learn(
                request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        true,
                        PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                        false),
                requiredData,
                policy(requiredData, true, false, false));
        assertFailureWithoutMutation(
                Status.PREREQUISITES_REQUIRED,
                physicalRequired,
                requiredData);

        PlayerRecipeData disabledData = new PlayerRecipeData();
        Result physicalDisabled = learn(
                request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        true,
                        PhysicalBlueprintLearningMode.DISABLED,
                        false),
                disabledData,
                policy(disabledData, true, false, true));
        assertFailureWithoutMutation(
                Status.PHYSICAL_BLUEPRINT_LEARNING_DISABLED,
                physicalDisabled,
                disabledData);

        PlayerRecipeData treeData = new PlayerRecipeData();
        Result tree = learn(
                request(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        true,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        false),
                treeData,
                policy(treeData, true, false, false));
        assertFailureWithoutMutation(Status.PREREQUISITES_REQUIRED, tree, treeData);
    }

    @Test
    void grantsAndMigrationsDoNotEnterRecentHistoryButAdministratorLearningDoes() {
        for (BlueprintUnlockOrigin excluded : List.of(
                BlueprintUnlockOrigin.STARTING_GRANT,
                BlueprintUnlockOrigin.MIGRATION)) {
            PlayerRecipeData data = new PlayerRecipeData();
            Result result = learn(
                    request(excluded, true, PhysicalBlueprintLearningMode.DISABLED, false),
                    data,
                    policy(data, true, false, true));
            assertTrue(result.successful());
            assertTrue(data.getRecentUnlockBatches().isEmpty());
        }

        PlayerRecipeData administrator = new PlayerRecipeData();
        Result result = learn(
                request(
                        BlueprintUnlockOrigin.ADMINISTRATOR,
                        true,
                        PhysicalBlueprintLearningMode.DISABLED,
                        false),
                administrator,
                policy(administrator, true, false, true));
        assertTrue(result.successful());
        assertEquals(1, administrator.getRecentUnlockBatches().size());
    }

    @Test
    void globalBlockAndExemptionFailuresRemainSeparateAndAtomic() {
        PlayerRecipeData disabledData = new PlayerRecipeData();
        assertFailureWithoutMutation(
                Status.BLUEPRINTS_DISABLED,
                learn(
                        request(
                                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                                false,
                                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                                false),
                        disabledData,
                        policy(disabledData, true, false, true)),
                disabledData);

        PlayerRecipeData blockedData = new PlayerRecipeData();
        assertFailureWithoutMutation(
                Status.BLOCKED,
                learn(
                        request(
                                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                                true,
                                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                                false),
                        blockedData,
                        policy(blockedData, true, true, true)),
                blockedData);

        PlayerRecipeData exemptData = new PlayerRecipeData();
        assertFailureWithoutMutation(
                Status.PROGRESSION_EXEMPT,
                learn(
                        request(
                                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                                true,
                                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                                true),
                        exemptData,
                        policy(exemptData, true, false, true)),
                exemptData);
    }

    @Test
    void duplicateStaleAndUnavailableRequestsDoNotMutate() {
        PlayerRecipeData learnedData = new PlayerRecipeData();
        assertTrue(learnedData.applyBlueprintLearning(
                com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation
                        .Request.commit(BLUEPRINT.toString(), RECIPE.toString()))
                .committed());
        Result duplicate = learn(
                request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        true,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        false),
                learnedData,
                policy(learnedData, true, false, true));
        assertEquals(Status.ALREADY_LEARNED, duplicate.status());

        PlayerRecipeData staleData = new PlayerRecipeData();
        BlueprintResearchPolicy stale = policy(staleData, true, false, true)
                .withRuntimePolicy(
                        true,
                        JournalVisibility.FULL,
                        true,
                        true,
                        false,
                        false,
                        100);
        stale = new BlueprintResearchPolicy(
                stale.blueprintId(),
                stale.profileId(),
                stale.available(),
                stale.blocked(),
                stale.playerDataAvailable(),
                true,
                stale.discovered(),
                stale.researchPoints(),
                stale.pointCap(),
                stale.prerequisitesSatisfied(),
                stale.journalEnabled(),
                stale.treeEnabled(),
                stale.visibility(),
                stale.researchEnabled(),
                stale.recyclingEnabled(),
                stale.allowUnlearnedRecycling(),
                stale.recyclingValue(),
                stale.researchCost(),
                stale.requiresDiscovery(),
                stale.prerequisites(),
                stale.creativeBypassesCost(),
                stale.ruleId(),
                stale.specificity());
        assertFailureWithoutMutation(
                Status.STALE_POLICY,
                learn(
                        request(
                                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                                true,
                                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                                false),
                        staleData,
                        stale),
                staleData);

        PlayerRecipeData unavailableData = new PlayerRecipeData();
        Result unavailable = BlueprintLearningService.learn(
                request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        true,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        false),
                unavailableData,
                ignored -> null,
                ignored -> policy(unavailableData, true, false, true));
        assertFailureWithoutMutation(
                Status.CONTENT_UNAVAILABLE,
                unavailable,
                unavailableData);
    }

    @Test
    void capacityAndResolverFailuresFailWithoutPartialKnowledge() {
        PlayerRecipeData full = new PlayerRecipeData();
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
                index++) {
            assertTrue(full.addRecipe("full:value_" + index));
        }
        assertFailureWithoutMutation(
                Status.PROGRESSION_CAPACITY_EXHAUSTED,
                learn(
                        request(
                                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                                true,
                                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                                false),
                        full,
                        policy(full, true, false, true)),
                full);

        PlayerRecipeData mismatchData = new PlayerRecipeData();
        Result mismatch = BlueprintLearningService.learn(
                request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        true,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        false),
                mismatchData,
                ignored -> new LearningTarget(id("test:other"), RECIPE),
                ignored -> policy(mismatchData, true, false, true));
        assertFailureWithoutMutation(Status.INVALID_IDENTITY, mismatch, mismatchData);

        PlayerRecipeData thrownData = new PlayerRecipeData();
        Result thrown = BlueprintLearningService.learn(
                request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        true,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        false),
                thrownData,
                ignored -> {
                    throw new IllegalStateException("reload race");
                },
                ignored -> policy(thrownData, true, false, true));
        assertFailureWithoutMutation(Status.POLICY_UNAVAILABLE, thrown, thrownData);
    }

    @Test
    void preparedLearningIsNonMutatingAndBoundToItsPreflightedCapability() {
        PlayerRecipeData source = new PlayerRecipeData();
        Request request = request(
                BlueprintUnlockOrigin.TREE_RESEARCH,
                true,
                PhysicalBlueprintLearningMode.DISABLED,
                false);
        BlueprintLearningService.Preparation preparation =
                BlueprintLearningService.prepare(
                        request,
                        source,
                        ignored -> TARGET,
                        ignored -> policy(source, true, false, true));

        assertTrue(preparation.ready());
        assertTrue(source.getLearnedBlueprints().isEmpty());
        assertTrue(source.getDiscoveredBlueprints().isEmpty());
        assertTrue(source.getLearnedRecipes().isEmpty());

        PlayerRecipeData differentPlayer = new PlayerRecipeData();
        Result rejected = BlueprintLearningService.commitPrepared(
                preparation.prepared().orElseThrow(), differentPlayer);
        assertFailureWithoutMutation(
                Status.STALE_POLICY, rejected, differentPlayer);

        Result committed = BlueprintLearningService.commitPrepared(
                preparation.prepared().orElseThrow(), source);
        assertTrue(committed.successful());
        assertTrue(source.hasBlueprint(BLUEPRINT.toString()));
        assertTrue(source.hasRecipe(RECIPE.toString()));
    }

    private static Result learn(
            Request request,
            PlayerRecipeData data,
            BlueprintResearchPolicy policy) {
        return BlueprintLearningService.learn(
                request,
                data,
                ignored -> TARGET,
                ignored -> policy);
    }

    private static Request request(
            BlueprintUnlockOrigin origin,
            boolean enabled,
            PhysicalBlueprintLearningMode mode,
            boolean progressionExempt) {
        return new Request(origin, BLUEPRINT, enabled, mode, progressionExempt);
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            boolean available,
            boolean blocked,
            boolean prerequisitesSatisfied) {
        return new BlueprintResearchPolicy(
                BLUEPRINT,
                PROFILE,
                available,
                blocked,
                true,
                data.hasBlueprint(BLUEPRINT.toString()),
                data.hasDiscoveredBlueprint(BLUEPRINT.toString()),
                data.getResearchPoints(),
                100,
                prerequisitesSatisfied,
                true,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(0, List.of()),
                false,
                List.of(),
                false,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static void assertFailureWithoutMutation(
            Status expected,
            Result result,
            PlayerRecipeData data) {
        assertEquals(expected, result.status());
        assertFalse(result.successful());
        assertFalse(result.learnedChanged());
        assertFalse(result.discoveredChanged());
        assertFalse(result.legacyRecipeChanged());
        assertFalse(result.prerequisitesBypassed());
        assertFalse(result.liveAwardsEligible());
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
        assertFalse(data.hasDiscoveredBlueprint(BLUEPRINT.toString()));
        assertFalse(data.hasRecipe(RECIPE.toString()));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
