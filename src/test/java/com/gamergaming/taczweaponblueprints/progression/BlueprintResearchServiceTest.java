package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService.ResearchInput;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService.Status;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class BlueprintResearchServiceTest {
    private static final ResourceLocation BLUEPRINT = id("test:rifle");
    private static final ResourceLocation PROFILE = id("test:profile");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void plannerReroutesOverlappingAlternativesInsteadOfGreedilyRejectingThem() {
        BlueprintResearchCost cost = new BlueprintResearchCost(0, List.of(
                ingredient(1, "minecraft:paper", "minecraft:iron_ingot"),
                ingredient(1, "minecraft:paper")));

        ResearchIngredientPlanner.Plan plan = ResearchIngredientPlanner.plan(
                List.of(new ItemStack(Items.PAPER), new ItemStack(Items.IRON_INGOT)), cost)
                .orElseThrow();

        assertEquals(1, plan.decrement(0));
        assertEquals(1, plan.decrement(1));
        assertEquals(2, plan.totalConsumed());
    }

    @Test
    void successfulResearchSpendsTheCompleteCostConsumesInputsAndProducesOneBlueprint() {
        PlayerRecipeData data = data(10, true);
        TestInput input = input(new ItemStack(Items.PAPER, 4), new ItemStack(Items.IRON_INGOT, 3));
        BlueprintResearchCost cost = new BlueprintResearchCost(7, List.of(
                ingredient(3, "minecraft:paper"),
                ingredient(2, "minecraft:iron_ingot")));

        BlueprintResearchService.Result result = research(data, input, policy(data, cost), false);

        assertTrue(result.successful());
        assertEquals(7, result.spentPoints());
        assertEquals(3, result.newBalance());
        assertEquals(1, input.stacks.get(0).getCount());
        assertEquals(1, input.stacks.get(1).getCount());
        assertEquals(List.of(BLUEPRINT), input.outputs);
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
    }

    @Test
    void creativeBypassWaivesBothPointAndIngredientCostsOnlyWhenPolicyAllowsIt() {
        PlayerRecipeData data = data(0, true);
        TestInput input = input(ItemStack.EMPTY);
        BlueprintResearchPolicy bypassPolicy = policy(
                data,
                new BlueprintResearchCost(8, List.of(ingredient(4, "minecraft:paper"))),
                true, false, true, true, true, true);

        BlueprintResearchService.Result result = research(data, input, bypassPolicy, true);

        assertTrue(result.successful());
        assertTrue(result.costBypassed());
        assertEquals(0, result.spentPoints());
        assertEquals(0, data.getResearchPoints());
        assertEquals(List.of(BLUEPRINT), input.outputs);
    }

    @Test
    void everyEconomicFailurePreservesPointsInputsAndOutput() {
        assertAtomicFailure(Status.POINTS_REQUIRED, data -> policy(
                data, new BlueprintResearchCost(6, List.of()), true, false, true, true, true, false));
        assertAtomicFailure(Status.INGREDIENTS_REQUIRED, data -> policy(
                data,
                new BlueprintResearchCost(1, List.of(ingredient(2, "minecraft:iron_ingot"))),
                true, false, true, true, true, false));

        PlayerRecipeData data = data(5, true);
        TestInput full = input(new ItemStack(Items.PAPER, 2));
        full.acceptOutput = false;
        BlueprintResearchService.Result result = research(
                data,
                full,
                policy(data, new BlueprintResearchCost(1, List.of(ingredient(1, "minecraft:paper"))), false),
                false);
        assertEquals(Status.OUTPUT_FULL, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, full.stacks.get(0).getCount());
        assertTrue(full.outputs.isEmpty());
    }

    @Test
    void policyFailuresAreTypedAndAtomic() {
        assertPolicyFailure(Status.POLICY_UNAVAILABLE, data -> null);
        assertPolicyFailure(Status.CONTENT_UNAVAILABLE,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), false, false, true, true, true, false));
        assertPolicyFailure(Status.BLOCKED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, true, true, true, true, false));
        assertPolicyFailure(Status.RESEARCH_DISABLED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, false, false, true, true, false));
        assertPolicyFailure(Status.DISCOVERY_REQUIRED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, false, true, false, true, false));
        assertPolicyFailure(Status.PREREQUISITES_REQUIRED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, false, true, true, false, false));

        PlayerRecipeData learned = data(5, true);
        learned.addBlueprint(BLUEPRINT.toString());
        TestInput input = input(ItemStack.EMPTY);
        BlueprintResearchService.Result alreadyLearned = research(
                learned,
                input,
                policy(learned, new BlueprintResearchCost(0, List.of())),
                false);
        assertEquals(Status.ALREADY_LEARNED, alreadyLearned.status());
        assertTrue(input.outputs.isEmpty());
    }

    private static void assertAtomicFailure(
            Status expected,
            java.util.function.Function<PlayerRecipeData, BlueprintResearchPolicy> policyFactory) {
        PlayerRecipeData data = data(5, true);
        TestInput input = input(new ItemStack(Items.PAPER, 2));
        BlueprintResearchService.Result result = research(data, input, policyFactory.apply(data), false);
        assertEquals(expected, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, input.stacks.get(0).getCount());
        assertTrue(input.outputs.isEmpty());
    }

    private static void assertPolicyFailure(
            Status expected,
            java.util.function.Function<PlayerRecipeData, BlueprintResearchPolicy> policyFactory) {
        PlayerRecipeData data = data(5, false);
        TestInput input = input(new ItemStack(Items.PAPER, 2));
        BlueprintResearchService.Result result = research(data, input, policyFactory.apply(data), false);
        assertEquals(expected, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, input.stacks.get(0).getCount());
        assertTrue(input.outputs.isEmpty());
    }

    private static BlueprintResearchService.Result research(
            PlayerRecipeData data,
            ResearchInput input,
            BlueprintResearchPolicy policy,
            boolean creative) {
        return BlueprintResearchService.research(BLUEPRINT, data, ignored -> policy, input, creative);
    }

    private static PlayerRecipeData data(int points, boolean discovered) {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(points);
        if (discovered) {
            data.discoverBlueprint(BLUEPRINT.toString());
        }
        return data;
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost) {
        return policy(data, cost, true, false, true, true, true, false);
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost,
            boolean creativeBypass) {
        return policy(data, cost, true, false, true, true, true, creativeBypass);
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost,
            boolean available,
            boolean blocked,
            boolean researchEnabled,
            boolean discovered,
            boolean prerequisites,
            boolean creativeBypass) {
        return new BlueprintResearchPolicy(
                BLUEPRINT,
                PROFILE,
                available,
                blocked,
                true,
                data.hasBlueprint(BLUEPRINT.toString()),
                discovered,
                data.getResearchPoints(),
                100,
                prerequisites,
                true,
                JournalVisibility.FULL,
                researchEnabled,
                true,
                false,
                1,
                cost,
                !discovered,
                prerequisites ? List.of() : List.of(id("test:required")),
                creativeBypass,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static BlueprintResearchIngredient ingredient(int count, String... items) {
        return new BlueprintResearchIngredient(
                java.util.Arrays.stream(items).map(BlueprintResearchServiceTest::id).toList(),
                Optional.empty(),
                count);
    }

    private static TestInput input(ItemStack... stacks) {
        List<ItemStack> slots = new ArrayList<>();
        for (ItemStack stack : stacks) {
            slots.add(stack.copy());
        }
        return new TestInput(slots);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static final class TestInput implements ResearchInput {
        private final List<ItemStack> stacks;
        private final List<ResourceLocation> outputs = new ArrayList<>();
        private boolean acceptOutput = true;

        private TestInput(List<ItemStack> stacks) {
            this.stacks = stacks;
        }

        @Override
        public List<ItemStack> stacks() {
            return stacks.stream().map(ItemStack::copy).toList();
        }

        @Override
        public boolean canAcceptOutput() {
            return acceptOutput;
        }

        @Override
        public void consume(ResearchIngredientPlanner.Plan plan) {
            for (int slot = 0; slot < stacks.size(); slot++) {
                stacks.get(slot).shrink(plan.decrement(slot));
            }
        }

        @Override
        public void produce(ResourceLocation blueprintId) {
            outputs.add(blueprintId);
        }
    }
}
