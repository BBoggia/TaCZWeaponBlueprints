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
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class FoundWeaponRecoveryServiceTest {
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("test:found_rifle");
    private static final ResourceLocation PROFILE = new ResourceLocation("test:profile");
    private static final ResourceLocation LOOT =
            new ResourceLocation("minecraft:chests/simple_dungeon");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyPositiveLootProvenanceAndDirectModesBecomeReady() {
        PlayerRecipeData data = data(10);
        ItemStack unknown = gun();
        BlueprintReverseEngineeringService.Evaluation reverse = reverse(data, 20);

        assertEquals(
                FoundWeaponRecoveryService.Status.VERIFIED_LOOT_REQUIRED,
                evaluate(unknown, data, reverse, FoundWeaponRecoveryMode.PLAYER_CHOICE).status());

        ItemStack found = gun();
        mark(found, PhysicalWeaponProvenance.Origin.LOOT_GENERATED, LOOT);
        FoundWeaponRecoveryService.Evaluation ready = evaluate(
                found, data, reverse, FoundWeaponRecoveryMode.PLAYER_CHOICE);
        assertTrue(ready.ready());
        assertEquals(3, ready.pointValue());
        assertEquals(11, ready.projectedBalance());

        assertEquals(
                FoundWeaponRecoveryService.Status.RECOVERY_DISABLED,
                evaluate(
                        found,
                        data,
                        reverse,
                        FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY).status());

        ItemStack crafted = gun();
        mark(
                crafted,
                PhysicalWeaponProvenance.Origin.CRAFTED_SURVIVAL,
                new ResourceLocation("test:rifle_recipe"));
        assertEquals(
                FoundWeaponRecoveryService.Status.VERIFIED_LOOT_REQUIRED,
                evaluate(crafted, data, reverse, FoundWeaponRecoveryMode.PLAYER_CHOICE).status());
    }

    @Test
    void pointCapIsCalculatedAfterPayingTheReverseEngineeringCost() {
        PlayerRecipeData data = data(20);
        ItemStack found = gun();
        mark(found, PhysicalWeaponProvenance.Origin.LOOT_GENERATED, LOOT);

        FoundWeaponRecoveryService.Evaluation evaluation = evaluate(
                found,
                data,
                reverse(data, 20),
                FoundWeaponRecoveryMode.DIRECT_RP_ONLY);

        assertEquals(FoundWeaponRecoveryService.Status.POINT_CAP_REACHED, evaluation.status());
        // Paying 2 RP leaves room for only 2, while the target's shared recycle value is 3.
        assertEquals(3, evaluation.pointValue());
    }

    @Test
    void commitConsumesOncePaysCostAwardsSharedValueAndDiscoversWithoutLearning() {
        PlayerRecipeData data = data(10);
        ItemStack found = gun();
        mark(found, PhysicalWeaponProvenance.Origin.LOOT_GENERATED, LOOT);
        TestTransaction transaction = new TestTransaction(found);
        FoundWeaponRecoveryService.Evaluation evaluation = evaluate(
                found,
                data,
                reverse(data, 20),
                FoundWeaponRecoveryMode.PLAYER_CHOICE);

        FoundWeaponRecoveryService.Result result =
                FoundWeaponRecoveryService.commit(evaluation, data, transaction);

        assertTrue(result.successful());
        assertTrue(transaction.input.isEmpty());
        assertEquals(11, data.getResearchPoints());
        assertTrue(data.hasDiscoveredBlueprint(BLUEPRINT.toString()));
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
        assertEquals(1, transaction.output.getCount(), "direct recovery must not require or replace output");
    }

    @Test
    void failedPhysicalConsumptionRollsBackPointsInventoryAndDiscovery() {
        PlayerRecipeData data = data(10);
        ItemStack found = gun();
        mark(found, PhysicalWeaponProvenance.Origin.LOOT_GENERATED, LOOT);
        TestTransaction transaction = new TestTransaction(found);
        transaction.failConsumption = true;
        FoundWeaponRecoveryService.Evaluation evaluation = evaluate(
                found,
                data,
                reverse(data, 20),
                FoundWeaponRecoveryMode.PLAYER_CHOICE);

        FoundWeaponRecoveryService.Result result =
                FoundWeaponRecoveryService.commit(evaluation, data, transaction);

        assertEquals(FoundWeaponRecoveryService.Status.STALE_INPUT, result.status());
        assertEquals(1, transaction.input.getCount());
        assertEquals(10, data.getResearchPoints());
        assertFalse(data.hasDiscoveredBlueprint(BLUEPRINT.toString()));
    }

    private static FoundWeaponRecoveryService.Evaluation evaluate(
            ItemStack input,
            PlayerRecipeData data,
            BlueprintReverseEngineeringService.Evaluation reverse,
            FoundWeaponRecoveryMode mode) {
        return FoundWeaponRecoveryService.evaluate(
                input, data, reverse, mode, ignored -> policy(data, 20));
    }

    private static BlueprintReverseEngineeringService.Evaluation reverse(
            PlayerRecipeData data,
            int pointCap) {
        BlueprintData blueprint = new BlueprintData(
                BLUEPRINT.toString(),
                "item.test.found_rifle",
                "tooltip.test",
                new ResourceLocation("test:found_rifle_recipe"),
                null,
                "rifle",
                new ResourceLocation("tacz:slot"),
                BlueprintKind.GUN,
                1);
        PhysicalItemBlueprintResolver.Resolution physical =
                new PhysicalItemBlueprintResolver.Resolution(
                        PhysicalItemBlueprintResolver.Status.RESOLVED,
                        Optional.of(BLUEPRINT),
                        Optional.of(blueprint),
                        BlueprintKind.GUN,
                        1,
                        false,
                        false,
                        false);
        BlueprintReverseEngineeringEvaluator.Evaluation base =
                new BlueprintReverseEngineeringEvaluator.Evaluation(
                        BlueprintReverseEngineeringEvaluator.Status.READY,
                        physical,
                        Optional.of(policy(data, pointCap)),
                        Optional.of(BlueprintReverseEngineeringPolicy.DEFAULT),
                        1);
        return new BlueprintReverseEngineeringService.Evaluation(
                BlueprintReverseEngineeringService.Status.READY,
                base,
                new BlueprintResearchCost(2, List.of()),
                data.getResearchPoints(),
                pointCap,
                true,
                true,
                Optional.empty());
    }

    private static BlueprintResearchPolicy policy(PlayerRecipeData data, int pointCap) {
        return new BlueprintResearchPolicy(
                BLUEPRINT,
                PROFILE,
                true,
                false,
                true,
                data.hasBlueprint(BLUEPRINT.toString()),
                data.hasDiscoveredBlueprint(BLUEPRINT.toString()),
                data.getResearchPoints(),
                pointCap,
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                3,
                new BlueprintResearchCost(8, List.of()),
                false,
                List.of(),
                false,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static PlayerRecipeData data(int points) {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(points);
        return data;
    }

    private static ItemStack gun() {
        // The pure recovery evaluator consumes trusted NBT plus its separately
        // resolved physical kind; no TaCZ client-backed item class is needed.
        return new ItemStack(Items.PAPER);
    }

    private static void mark(
            ItemStack stack,
            PhysicalWeaponProvenance.Origin origin,
            ResourceLocation source) {
        stack.getOrCreateTag().put(
                PhysicalWeaponProvenance.TAG_KEY,
                new PhysicalWeaponProvenance(
                        PhysicalWeaponProvenance.CURRENT_FORMAT,
                        origin,
                        source).toTag());
    }

    private static final class TestTransaction
            implements BlueprintReverseEngineeringService.WorkstationTransaction {
        private ItemStack input;
        private final List<ItemStack> inventory = new ArrayList<>();
        private ItemStack output = new ItemStack(Items.PAPER);
        private boolean failConsumption;

        private TestTransaction(ItemStack input) {
            this.input = input.copy();
        }

        @Override
        public ItemStack physicalInput() {
            return input;
        }

        @Override
        public ItemStack outputStack() {
            return output;
        }

        @Override
        public List<ItemStack> inventoryStacks() {
            return inventory.stream().map(ItemStack::copy).toList();
        }

        @Override
        public ItemStack createOutput(
                ResourceLocation blueprintId,
                BlueprintProvenance provenance) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean consumeMaterials(
                ResearchIngredientPlanner.Plan plan,
                List<ItemStack> expectedInventory) {
            return plan != null && plan.slotCount() == 0 && expectedInventory.isEmpty();
        }

        @Override
        public boolean consumePhysical(ItemStack expectedInput, int count) {
            if (failConsumption || count != 1 || !ItemStack.matches(input, expectedInput)) {
                return false;
            }
            input.shrink(1);
            return true;
        }

        @Override
        public boolean placeOutput(ItemStack output, ItemStack expectedOutput) {
            return false;
        }

        @Override
        public boolean restore(
                ItemStack physicalInput,
                List<ItemStack> restoredInventory,
                ItemStack restoredOutput) {
            input = physicalInput.copy();
            inventory.clear();
            restoredInventory.stream().map(ItemStack::copy).forEach(inventory::add);
            output = restoredOutput.copy();
            return true;
        }
    }
}
