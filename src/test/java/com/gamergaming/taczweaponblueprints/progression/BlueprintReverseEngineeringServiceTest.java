package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class BlueprintReverseEngineeringServiceTest {
    private static final ResourceLocation PROFILE = id("test:reverse_profile");
    private static final ResourceLocation AMMO = id("addon_pack:test_ammo");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ammoCommitConsumesOneCanonicalBatchAndCreatesProtectedOutput() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        TestTransaction transaction = new TestTransaction(
                new ItemStack(Items.PAPER, 12),
                List.of(new ItemStack(Items.IRON_INGOT, 3)));
        BlueprintReverseEngineeringPolicy reverse = policy(3, 2, false);

        BlueprintReverseEngineeringService.Evaluation evaluation = evaluate(
                transaction, data, reverse, false);
        assertTrue(evaluation.ready(), () -> "unexpected status: " + evaluation.status());
        assertEquals(6, evaluation.requiredInputCount());

        BlueprintReverseEngineeringService.Result result =
                commit(evaluation, data, transaction);

        assertTrue(result.successful());
        assertEquals(6, transaction.input.getCount());
        assertEquals(1, transaction.inventory.get(0).getCount());
        assertEquals(7, data.getResearchPoints());
        assertTrue(data.hasDiscoveredBlueprint(AMMO.toString()));
        assertEquals(AMMO.toString(), transaction.output.getTag().getString("bpId"));
        assertFalse(BlueprintProvenance.allowsRecycling(transaction.output.getTag()));
        BlueprintProvenance provenance = BlueprintProvenance.fromTag(
                transaction.output.getTag()).orElseThrow();
        assertEquals(BlueprintProvenance.Source.REVERSE_ENGINEERING, provenance.source());
        assertEquals(
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                provenance.learningMode());
    }

    @Test
    void previewSeparatesOutputPointsMaterialsAndEquipmentBlockers() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(2);
        TestTransaction transaction = new TestTransaction(
                new ItemStack(Items.PAPER, 12),
                List.of(new ItemStack(Items.IRON_INGOT)));
        BlueprintReverseEngineeringPolicy reverse = policy(3, 2, false);

        assertEquals(
                BlueprintReverseEngineeringService.Status.POINTS_REQUIRED,
                evaluate(transaction, data, reverse, false).status());

        data.setResearchPoints(10);
        assertEquals(
                BlueprintReverseEngineeringService.Status.INGREDIENTS_REQUIRED,
                evaluate(transaction, data, reverse, false).status());

        transaction.inventory.get(0).grow(1);
        transaction.output = blueprint(AMMO, BlueprintProvenance.reverseEngineered(
                false,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES));
        assertEquals(
                BlueprintReverseEngineeringService.Status.OUTPUT_OCCUPIED,
                evaluate(transaction, data, reverse, false).status());

        transaction.output = ItemStack.EMPTY;
        assertEquals(
                BlueprintReverseEngineeringService.Status.LOADED_GUN,
                evaluate(transaction, data, reverse, true).status());
    }

    @Test
    void failedPlacementRestoresPhysicalMaterialsAndPoints() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        TestTransaction transaction = new TestTransaction(
                new ItemStack(Items.PAPER, 12),
                List.of(new ItemStack(Items.IRON_INGOT, 3)));
        transaction.failPlacement = true;
        BlueprintReverseEngineeringService.Evaluation evaluation = evaluate(
                transaction, data, policy(3, 2, false), false);

        BlueprintReverseEngineeringService.Result result =
                commit(evaluation, data, transaction);

        assertEquals(BlueprintReverseEngineeringService.Status.STALE_INPUT, result.status());
        assertEquals(12, transaction.input.getCount());
        assertEquals(3, transaction.inventory.get(0).getCount());
        assertTrue(transaction.output.isEmpty());
        assertEquals(10, data.getResearchPoints());
        assertFalse(data.hasDiscoveredBlueprint(AMMO.toString()));
    }

    @Test
    void staleMaterialInventoryFailsBeforeSpendingOrConsumingAnything() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        TestTransaction transaction = new TestTransaction(
                new ItemStack(Items.PAPER, 12),
                List.of(new ItemStack(Items.IRON_INGOT, 3)));
        BlueprintReverseEngineeringService.Evaluation evaluation = evaluate(
                transaction, data, policy(3, 2, false), false);
        transaction.inventory.get(0).shrink(3);

        BlueprintReverseEngineeringService.Result result =
                commit(evaluation, data, transaction);

        assertEquals(BlueprintReverseEngineeringService.Status.STALE_INPUT, result.status());
        assertEquals(12, transaction.input.getCount());
        assertTrue(transaction.inventory.get(0).isEmpty());
        assertTrue(transaction.output.isEmpty());
        assertEquals(10, data.getResearchPoints());
        assertFalse(data.hasDiscoveredBlueprint(AMMO.toString()));
    }

    @Test
    void malformedOutputFailsBeforeAnyCostIsCommitted() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        TestTransaction transaction = new TestTransaction(
                new ItemStack(Items.PAPER, 12),
                List.of(new ItemStack(Items.IRON_INGOT, 3)));
        BlueprintReverseEngineeringService.Evaluation evaluation = evaluate(
                transaction, data, policy(3, 2, false), false);

        BlueprintReverseEngineeringService.Result result =
                BlueprintReverseEngineeringService.commit(
                        evaluation, data, transaction, (output, expectedId) -> false);

        assertEquals(
                BlueprintReverseEngineeringService.Status.TRANSACTION_FAILED,
                result.status());
        assertEquals(12, transaction.input.getCount());
        assertEquals(3, transaction.inventory.get(0).getCount());
        assertTrue(transaction.output.isEmpty());
        assertEquals(10, data.getResearchPoints());
    }

    @Test
    void protectedReverseOutputCannotEnterTheDuplicateRpLoop() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(4);
        data.addBlueprint(AMMO.toString());
        TestRecyclingInput protectedBlueprint = new TestRecyclingInput(1, false);
        BlueprintRecyclingService.Result result = BlueprintRecyclingService.recycle(
                protectedBlueprint,
                data,
                ignored -> recyclingPolicy(data));

        assertEquals(BlueprintRecyclingService.Status.POLICY_INELIGIBLE, result.status());
        assertEquals(1, protectedBlueprint.count());
        assertEquals(4, data.getResearchPoints());
    }

    @Test
    void verifiedFoundWeaponOverrideProducesARecyclableBlueprint() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        TestTransaction transaction = new TestTransaction(
                new ItemStack(Items.PAPER, 12),
                List.of(new ItemStack(Items.IRON_INGOT, 3)));
        BlueprintReverseEngineeringService.Evaluation evaluation = evaluate(
                transaction,
                data,
                policy(3, 2, false),
                false).withFoundBlueprintRecyclable(true);

        BlueprintReverseEngineeringService.Result result =
                commit(evaluation, data, transaction);

        assertTrue(result.successful());
        assertTrue(BlueprintProvenance.allowsRecycling(transaction.output.getTag()));
    }

    @Test
    void reconstructedBlueprintProvenanceCannotRecycleOrBypassTreePrerequisites() {
        BlueprintProvenance reconstructed = BlueprintProvenance.fragmentReconstructed();
        CompoundTag root = new CompoundTag();
        root.put(BlueprintProvenance.TAG_KEY, reconstructed.toTag());

        assertEquals(
                BlueprintProvenance.Source.FRAGMENT_RECONSTRUCTION,
                BlueprintProvenance.fromTag(root).orElseThrow().source());
        assertFalse(BlueprintProvenance.allowsRecycling(root));
        assertEquals(
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                BlueprintProvenance.learningMode(
                        root, PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES));

        root.getCompound(BlueprintProvenance.TAG_KEY).putString("source", "invalid");
        assertEquals(
                PhysicalBlueprintLearningMode.DISABLED,
                BlueprintProvenance.learningMode(
                        root, PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES));
    }

    private static BlueprintReverseEngineeringService.Evaluation evaluate(
            TestTransaction transaction,
            PlayerRecipeData data,
            BlueprintReverseEngineeringPolicy reverse,
            boolean loaded) {
        return BlueprintReverseEngineeringService.evaluate(
                transaction.input,
                transaction.inventoryStacks(),
                transaction.output.isEmpty(),
                snapshot(reverse),
                Map.of(AMMO, blueprintData()),
                PROFILE,
                data,
                ignored -> false,
                ignored -> false,
                true,
                100,
                ignored -> new PhysicalItemBlueprintResolver.InspectedIdentity(
                        AMMO,
                        BlueprintKind.AMMO,
                        loaded,
                        false,
                        false));
    }

    private static BlueprintReverseEngineeringPolicy policy(
            int points,
            int iron,
            boolean recyclable) {
        return new BlueprintReverseEngineeringPolicy(
                true,
                Optional.empty(),
                new BlueprintResearchCost(
                        points,
                        List.of(new BlueprintResearchIngredient(
                                List.of(id("minecraft:iron_ingot")),
                                Optional.empty(),
                                iron))),
                false,
                true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                recyclable,
                false);
    }

    private static BlueprintResearchSnapshot snapshot(
            BlueprintReverseEngineeringPolicy reverse) {
        BlueprintResearchProfile profile = new BlueprintResearchProfile(
                1,
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
                List.of(),
                Map.of(),
                Optional.empty(),
                reverse);
        return BlueprintResearchSnapshot.create(
                Map.of(), Map.of(PROFILE, profile), Map.of());
    }

    private static BlueprintData blueprintData() {
        return new BlueprintData(
                AMMO.toString(),
                "item.addon_pack.test_ammo",
                "tooltip.test",
                id("addon_pack:recipe/test_ammo"),
                null,
                "ammo",
                id("tacz:slot"),
                BlueprintKind.AMMO,
                6);
    }

    private static BlueprintResearchPolicy recyclingPolicy(PlayerRecipeData data) {
        return new BlueprintResearchPolicy(
                AMMO,
                PROFILE,
                true,
                false,
                true,
                true,
                true,
                data.getResearchPoints(),
                100,
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                true,
                true,
                3,
                new BlueprintResearchCost(0, List.of()),
                false,
                List.of(),
                false,
                Optional.empty(),
                com.gamergaming.taczweaponblueprints.resource.research
                        .BlueprintResearchTarget.MatchSpecificity.NONE);
    }

    private static ItemStack blueprint(
            ResourceLocation id,
            BlueprintProvenance provenance) {
        ItemStack output = new ItemStack(Items.PAPER);
        CompoundTag tag = output.getOrCreateTag();
        tag.putString("bpId", id.toString());
        tag.put(BlueprintProvenance.TAG_KEY, provenance.toTag());
        return output;
    }

    private static BlueprintReverseEngineeringService.Result commit(
            BlueprintReverseEngineeringService.Evaluation evaluation,
            PlayerRecipeData data,
            TestTransaction transaction) {
        return BlueprintReverseEngineeringService.commit(
                evaluation,
                data,
                transaction,
                (output, expectedId) -> !output.isEmpty()
                        && output.getCount() == 1
                        && output.hasTag()
                        && expectedId.toString().equals(output.getTag().getString("bpId"))
                        && BlueprintProvenance.fromTag(output.getTag()).isPresent());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static final class TestTransaction
            implements BlueprintReverseEngineeringService.WorkstationTransaction {
        private ItemStack input;
        private final List<ItemStack> inventory = new ArrayList<>();
        private ItemStack output = ItemStack.EMPTY;
        private boolean failPlacement;

        private TestTransaction(ItemStack input, List<ItemStack> inventory) {
            this.input = input.copy();
            inventory.stream().map(ItemStack::copy).forEach(this.inventory::add);
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
            return blueprint(blueprintId, provenance);
        }

        @Override
        public boolean consumeMaterials(
                ResearchIngredientPlanner.Plan plan,
                List<ItemStack> expectedInventory) {
            if (!matches(expectedInventory, inventory)) {
                return false;
            }
            for (int index = 0; index < inventory.size(); index++) {
                inventory.get(index).shrink(plan.decrement(index));
            }
            return true;
        }

        @Override
        public boolean consumePhysical(ItemStack expectedInput, int count) {
            if (!ItemStack.matches(expectedInput, input) || input.getCount() < count) {
                return false;
            }
            input.shrink(count);
            return true;
        }

        @Override
        public boolean placeOutput(ItemStack output, ItemStack expectedOutput) {
            if (failPlacement || !this.output.isEmpty()
                    || !ItemStack.matches(expectedOutput, this.output)) {
                return false;
            }
            this.output = output.copy();
            return true;
        }

        @Override
        public boolean restore(
                ItemStack physicalInput,
                List<ItemStack> inventory,
                ItemStack output) {
            this.input = physicalInput.copy();
            this.inventory.clear();
            inventory.stream().map(ItemStack::copy).forEach(this.inventory::add);
            this.output = output.copy();
            return true;
        }

        private static boolean matches(List<ItemStack> left, List<ItemStack> right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int index = 0; index < left.size(); index++) {
                if (!ItemStack.matches(left.get(index), right.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class TestRecyclingInput
            implements BlueprintRecyclingService.RecyclingInput {
        private int count;
        private final boolean recyclable;

        private TestRecyclingInput(int count, boolean recyclable) {
            this.count = count;
            this.recyclable = recyclable;
        }

        @Override
        public Optional<ResourceLocation> blueprintId() {
            return Optional.of(AMMO);
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void consumeOne() {
            count--;
        }

        @Override
        public boolean provenanceAllowsRecycling() {
            return recyclable;
        }
    }
}
