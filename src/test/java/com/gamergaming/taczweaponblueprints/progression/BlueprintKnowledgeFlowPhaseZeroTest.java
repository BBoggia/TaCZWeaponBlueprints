package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService.ResearchInput;
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

/** Characterizes the exact live boundary before direct learning and reverse engineering. */
class BlueprintKnowledgeFlowPhaseZeroTest {
    private static final ResourceLocation BLUEPRINT = id("test:phase_zero_rifle");
    private static final ResourceLocation RECIPE = id("test:phase_zero_rifle_recipe");
    private static final ResourceLocation PROFILE = id("test:phase_zero_profile");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void currentResearchCommitProducesOnePhysicalOutputWithoutLearning() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(5);
        TestResearchInput input = new TestResearchInput(
                new ItemStack(Items.PAPER, 2));
        BlueprintResearchCost cost = new BlueprintResearchCost(
                3,
                List.of(new BlueprintResearchIngredient(
                        List.of(new ResourceLocation("minecraft:paper")),
                        Optional.empty(),
                        1)));

        BlueprintResearchService.Result result = BlueprintResearchService.research(
                BLUEPRINT,
                data,
                ignored -> policy(data, cost),
                input,
                false);

        assertTrue(result.successful());
        assertEquals(3, result.spentPoints());
        assertEquals(2, result.balanceAfterCost());
        assertEquals(1, input.stacks.get(0).getCount());
        assertEquals(List.of(BLUEPRINT), input.deliveredBlueprints);
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
        assertFalse(data.hasRecipe(RECIPE.toString()));
    }

    @Test
    void currentCapabilityCouplesLearnedAndDiscoveredButNotLegacyRecipeState() {
        PlayerRecipeData data = new PlayerRecipeData();

        assertTrue(data.addBlueprint(BLUEPRINT.toString()));
        assertTrue(data.hasBlueprint(BLUEPRINT.toString()));
        assertTrue(data.hasDiscoveredBlueprint(BLUEPRINT.toString()));
        assertFalse(data.hasRecipe(RECIPE.toString()));

        assertTrue(data.addRecipe(RECIPE.toString()));
        assertTrue(data.hasRecipe(RECIPE.toString()));
        assertFalse(data.addBlueprint(BLUEPRINT.toString()));
    }

    @Test
    void laterPhaseActivatesReverseEngineeringAndTheExtractOnlyOutputSlot() {
        assertEquals(
                Set.of("RECYCLE", "REDEEM", "REDEEM_STACK", "REVERSE_ENGINEER", "RECOVER_POINTS"),
                Arrays.stream(BlueprintRecyclerActionContract.Action.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
        assertEquals(
                Set.of("EMPTY", "INVALID", "BLUEPRINT", "RESEARCH_DATA", "PHYSICAL_ITEM"),
                Arrays.stream(BlueprintRecyclerPreview.InputKind.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
        assertEquals(0, BlueprintRecyclerMenu.INPUT_SLOT);
        assertEquals(1, BlueprintRecyclerMenu.OUTPUT_SLOT);
        assertEquals(2, BlueprintRecyclerMenu.FIRST_PLAYER_SLOT);
        assertTrue(Arrays.stream(BlueprintResearchService.Status.values())
                .anyMatch(status -> status == BlueprintResearchService.Status.OUTPUT_FULL));
        assertEquals("42", NetworkHandler.PROTOCOL_VERSION);
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost) {
        return new BlueprintResearchPolicy(
                BLUEPRINT,
                PROFILE,
                true,
                false,
                true,
                data.hasBlueprint(BLUEPRINT.toString()),
                data.hasDiscoveredBlueprint(BLUEPRINT.toString()),
                data.getResearchPoints(),
                100,
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                cost,
                false,
                List.of(),
                false,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static final class TestResearchInput implements ResearchInput {
        private final List<ItemStack> stacks = new ArrayList<>();
        private final List<ResourceLocation> deliveredBlueprints = new ArrayList<>();

        private TestResearchInput(ItemStack... inputs) {
            Arrays.stream(inputs).map(ItemStack::copy).forEach(stacks::add);
        }

        @Override
        public List<ItemStack> stacks() {
            return stacks.stream().map(ItemStack::copy).toList();
        }

        @Override
        public boolean canAcceptOutput() {
            return true;
        }

        @Override
        public void consume(ResearchIngredientPlanner.Plan plan) {
            for (int slot = 0; slot < stacks.size(); slot++) {
                stacks.get(slot).shrink(plan.decrement(slot));
            }
        }

        @Override
        public void restore(List<ItemStack> snapshot) {
            stacks.clear();
            snapshot.stream().map(ItemStack::copy).forEach(stacks::add);
        }

        @Override
        public ItemStack createOutput(ResourceLocation blueprintId) {
            assertEquals(BLUEPRINT, blueprintId);
            return new ItemStack(Items.PAPER);
        }

        @Override
        public boolean deliver(ItemStack output) {
            if (output == null || output.isEmpty()) {
                return false;
            }
            deliveredBlueprints.add(BLUEPRINT);
            return true;
        }
    }
}
