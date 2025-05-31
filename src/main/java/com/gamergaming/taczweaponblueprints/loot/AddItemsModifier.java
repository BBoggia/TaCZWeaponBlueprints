package com.gamergaming.taczweaponblueprints.loot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList; 
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.fml.ModList;

public class AddItemsModifier extends LootModifier {
    private final List<Pair<ItemStack, Float>> itemsWithWeights;
    private final Map<String, Integer> randomRollRange; 
    private final Float poolProbability; 

    private static final Codec<Map<String, Integer>> RANDOM_ROLL_RANGE_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("min").forGetter(m -> m.get("min")),
            Codec.INT.fieldOf("max").forGetter(m -> m.get("max"))
        ).apply(instance, (min, max) -> Map.of("min", min, "max", max))
    );

    private static final Codec<Pair<ItemStack, Float>> ITEM_WEIGHT_PAIR_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(Pair::getLeft),
            Codec.FLOAT.fieldOf("weight").forGetter(Pair::getRight)
        ).apply(instance, Pair::of)
    );

    public static final Supplier<Codec<AddItemsModifier>> CODEC = Suppliers.memoize(() ->
       RecordCodecBuilder.create(instance ->
           codecStart(instance)
                .and(Codec.FLOAT.fieldOf("poolProbability").forGetter(m -> m.poolProbability))
                .and(RANDOM_ROLL_RANGE_CODEC.fieldOf("rolls").forGetter(m -> m.randomRollRange))
                .and(ITEM_WEIGHT_PAIR_CODEC.listOf().fieldOf("items").forGetter(m -> m.itemsWithWeights))
                .apply(instance, (conditions, poolProbability, randomRollRange, itemsWithWeights) -> new AddItemsModifier(conditions, itemsWithWeights, randomRollRange.get("min"), randomRollRange.get("max"), poolProbability))
       )
   );

    public AddItemsModifier(LootItemCondition[] conditionsIn, List<Pair<ItemStack, Float>> itemsWithWeights, int min, int max, float poolProbability) {
        super(conditionsIn);
        this.itemsWithWeights = itemsWithWeights;
        this.randomRollRange = Map.of("min", min, "max", max);
        this.poolProbability = poolProbability;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    public LootItemCondition[] getConditions() {
        return conditions;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!Arrays.stream(this.conditions).allMatch(condition -> condition.test(context))) {
            return generatedLoot;
        }

        RandomSource random = context.getRandom();

        // Check if pool should be activated based on its probability
        if (random.nextFloat() < this.poolProbability) {

            // Filter items based on if mod loaded at runtime
            List<Pair<ItemStack, Float>> availableItems = new ArrayList<>();
            for (Pair<ItemStack, Float> itemWeightPair : this.itemsWithWeights) {
                ItemStack potentialItem = itemWeightPair.getLeft();
                String bpId = BlueprintItem.getBpId(potentialItem); 

                if (bpId == null || bpId.equals("NULL") || bpId.isEmpty()) {
                    TaCZWeaponBlueprints.LOGGER.warn("BlueprintItem has invalid bpId in AddItemsModifier: {}", potentialItem.getDisplayName().getString());
                    continue;
                }

                String[] idParts = bpId.split(":", 2);
                if (idParts.length < 2) {
                    TaCZWeaponBlueprints.LOGGER.warn("Malformed blueprint ID (missing namespace) in AddItemsModifier: {}. Full ID: {}", potentialItem.getDisplayName().getString(), bpId);
                    continue;
                }
                String itemNamespace = idParts[0];

                if (ModList.get().isLoaded(itemNamespace)) {
                    availableItems.add(itemWeightPair);
                } else {
                    // TaCZWeaponBlueprints.LOGGER.debug("Skipping item {} for loot generation as mod {} is not loaded.", bpId, itemNamespace);
                }
            }

            if (availableItems.isEmpty()) {
                return generatedLoot; 
            }

            int minRolls = this.randomRollRange.get("min");
            int maxRolls = this.randomRollRange.get("max");
            int rolls = 0;
            if (maxRolls > minRolls) {
                rolls = minRolls + random.nextInt(maxRolls - minRolls + 1);
            } else if (maxRolls == minRolls) {
                rolls = minRolls;
            }
            rolls = Math.max(0, rolls);


            if (rolls > 0 && !availableItems.isEmpty()) {

                float totalWeight = 0f;
                for (Pair<ItemStack, Float> pair : availableItems) {
                    totalWeight += pair.getRight();
                }

                if (totalWeight > 0) { 
                    for (int i = 0; i < rolls; ++i) {
                        float randomPick = random.nextFloat() * totalWeight;
                        float cumulativeWeight = 0f;
                        ItemStack selectedItem = null;

                        for (Pair<ItemStack, Float> pair : availableItems) {
                            cumulativeWeight += pair.getRight();
                            if (randomPick <= cumulativeWeight) {
                                selectedItem = pair.getLeft();
                                break;
                            }
                        }
                        
                        if (selectedItem != null) {
                            generatedLoot.add(selectedItem.copy());
                        } else if (!availableItems.isEmpty()) {
                            // Falback to first item is selection failed
                             TaCZWeaponBlueprints.LOGGER.warn("Weighted selection failed in AddItemsModifier, falling back to first available item. TotalWeight: {}, RandomPick: {}", totalWeight, randomPick);
                            generatedLoot.add(availableItems.get(0).getLeft().copy());
                             TaCZWeaponBlueprints.LOGGER.warn("Weighted selection failed in AddItemsModifier, falling back to first available item. TotalWeight: {}, RandomPick: {}", totalWeight, randomPick);
                        }
                    }
                }
            }
        }
        return generatedLoot;
    }
}