package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

class ResearchProgressionConnectivityTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation ROOT = id("test:root");
    private static final ResourceLocation MISSING = id("test:missing");
    private static final ResourceLocation LEARNED_OUT_OF_ORDER = id("test:learned_out_of_order");
    private static final ResourceLocation TARGET = id("test:target");

    @Test
    void learnedOutOfOrderNodeIsNotConnectedUntilItsMissingAncestryIsLearned() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(ROOT.toString());
        data.addBlueprint(LEARNED_OUT_OF_ORDER.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> initialPolicies = policies(
                data,
                Map.of(
                        ROOT, ResearchRequirements.EMPTY,
                        MISSING, legacy(ROOT),
                        LEARNED_OUT_OF_ORDER, legacy(MISSING),
                        TARGET, legacy(LEARNED_OUT_OF_ORDER)));
        ResearchProgressionConnectivity connectivity =
                new ResearchProgressionConnectivity(
                        data, initialPolicies::get, ignored -> false);

        assertTrue(connectivity.isConnected(ROOT));
        assertFalse(connectivity.isConnected(LEARNED_OUT_OF_ORDER));
        assertFalse(connectivity.requirementsSatisfied(initialPolicies.get(TARGET)));

        data.addBlueprint(MISSING.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> repairedPolicies = policies(
                data,
                Map.of(
                        ROOT, ResearchRequirements.EMPTY,
                        MISSING, legacy(ROOT),
                        LEARNED_OUT_OF_ORDER, legacy(MISSING),
                        TARGET, legacy(LEARNED_OUT_OF_ORDER)));
        connectivity = new ResearchProgressionConnectivity(
                data, repairedPolicies::get, ignored -> false);

        assertTrue(connectivity.isConnected(LEARNED_OUT_OF_ORDER));
        assertTrue(connectivity.requirementsSatisfied(repairedPolicies.get(TARGET)));
    }

    @Test
    void groupedRequirementsKeepAndAcrossGroupsAndOrWithinEachGroup() {
        ResourceLocation otherRoot = id("test:other_root");
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(ROOT.toString());
        Map<ResourceLocation, ResearchRequirements> requirements = new LinkedHashMap<>();
        requirements.put(ROOT, ResearchRequirements.EMPTY);
        requirements.put(otherRoot, ResearchRequirements.EMPTY);
        requirements.put(TARGET, new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(ROOT, MISSING)),
                ResearchPrerequisiteGroup.singleton(otherRoot))));
        Map<ResourceLocation, BlueprintResearchPolicy> initialPolicies =
                policies(data, requirements);
        ResearchProgressionConnectivity connectivity =
                new ResearchProgressionConnectivity(
                        data, initialPolicies::get, ignored -> false);

        assertFalse(connectivity.requirementsSatisfied(initialPolicies.get(TARGET)));

        data.addBlueprint(otherRoot.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> completedPolicies =
                policies(data, requirements);
        connectivity = new ResearchProgressionConnectivity(
                data, completedPolicies::get, ignored -> false);
        assertTrue(connectivity.requirementsSatisfied(completedPolicies.get(TARGET)));
    }

    @Test
    void progressionExemptionActsAsAStableRootButCyclesFailClosed() {
        ResourceLocation exempt = id("test:exempt");
        ResourceLocation cycleA = id("test:cycle_a");
        ResourceLocation cycleB = id("test:cycle_b");
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(cycleA.toString());
        data.addBlueprint(cycleB.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(
                data,
                Map.of(
                        TARGET, legacy(exempt),
                        cycleA, legacy(cycleB),
                        cycleB, legacy(cycleA)));
        ResearchProgressionConnectivity connectivity =
                new ResearchProgressionConnectivity(data, policies::get, exempt::equals);

        assertTrue(connectivity.requirementsSatisfied(policies.get(TARGET)));
        assertFalse(connectivity.isConnected(cycleA));
        assertFalse(connectivity.isConnected(cycleB));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> policies(
            PlayerRecipeData data,
            Map<ResourceLocation, ResearchRequirements> requirements) {
        Map<ResourceLocation, BlueprintResearchPolicy> policies = new LinkedHashMap<>();
        requirements.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> policies.put(
                        entry.getKey(), policy(data, entry.getKey(), entry.getValue())));
        return policies;
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            ResourceLocation id,
            ResearchRequirements requirements) {
        boolean learned = data.hasBlueprint(id.toString());
        return new BlueprintResearchPolicy(
                id,
                PROFILE,
                true,
                false,
                true,
                learned,
                data.hasDiscoveredBlueprint(id.toString()),
                data.getResearchPoints(),
                100,
                requirements.satisfiedBy(value -> data.hasBlueprint(value.toString())),
                true,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(1, List.of()),
                false,
                requirements,
                requirements.conservativeAlternatives(),
                true,
                false,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static ResearchRequirements legacy(ResourceLocation prerequisite) {
        return ResearchRequirements.fromLegacy(List.of(prerequisite));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
