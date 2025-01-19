package com.gamergaming.taczweaponblueprints.loot;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.commons.lang3.tuple.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public class AddItemsModifier extends LootModifier {
    private final Map<String, Integer> randomRollRange;
    private final List<Pair<ItemStack, Float>> itemsWithWeights;
    private final Float poolProbability;

    private static final Codec<Map<String, Integer>> RANDOM_ROLL_RANGE = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("min").forGetter(m -> m.get("min")),
            Codec.INT.fieldOf("max").forGetter(m -> m.get("max"))
        ).apply(instance, (min, max) -> Map.of("min", min, "max", max))
    );

    // Codec for Pair<ItemStack, Float>
    private static final Codec<Pair<ItemStack, Float>> ITEM_WEIGHT_PAIR_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(Pair::getLeft),
            Codec.FLOAT.fieldOf("weight").forGetter(Pair::getRight)
        ).apply(instance, Pair::of)
    );

    // Codec for AddItemsModifier
    public static final Supplier<Codec<AddItemsModifier>> CODEC = Suppliers.memoize(() ->
       RecordCodecBuilder.create(instance ->
           codecStart(instance)
                .and(
                     Codec.FLOAT.fieldOf("poolProbability").forGetter(m -> m.poolProbability)
                )
                .and(
                     RANDOM_ROLL_RANGE.fieldOf("rolls").forGetter(m -> m.randomRollRange)
                )
               .and(
                   ITEM_WEIGHT_PAIR_CODEC.listOf().fieldOf("items").forGetter(m -> m.itemsWithWeights)
               )
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
        if (Arrays.stream(this.conditions).allMatch(condition -> condition.test(context))) {
            RandomSource random = context.getRandom();
    
            if (random.nextFloat() < poolProbability) {
                int rolls = random.nextInt(randomRollRange.get("max") - randomRollRange.get("min") + 1) + randomRollRange.get("min"); // Random number between min and max inclusive
        
                for (int i = 0; i < rolls; ++i) {
                    // Calculate total weight
                    float totalWeight = itemsWithWeights.stream().map(Pair::getRight).reduce(0f, Float::sum);
        
                    // Generate a random number between 0 and totalWeight
                    float rand = random.nextFloat() * totalWeight;
        
                    float cumulativeWeight = 0f;
                    for (Pair<ItemStack, Float> pair : itemsWithWeights) {
                        cumulativeWeight += pair.getRight();
                        if (rand <= cumulativeWeight) {
                            generatedLoot.add(pair.getLeft().copy());
                            break;
                        }
                    }
                }
            }
        }
        return generatedLoot;
    }
}














// public class AddItemsModifier extends LootModifier {
//     private final List<Pair<ItemStack, Float>> itemsWithWeights;

//     private static final Codec<Pair<ItemStack, Float>> ITEM_WEIGHT_PAIR_CODEC = RecordCodecBuilder.create(instance ->
//         instance.group(
//             ItemStack.CODEC.fieldOf("item").forGetter(Pair::getLeft),
//             Codec.FLOAT.fieldOf("chance").forGetter(Pair::getRight)
//         ).apply(instance, Pair::of)
//     );

//     // Codec for AddItemsModifier
//     public static final Supplier<Codec<AddItemsModifier>> CODEC = Suppliers.memoize(() ->
//         RecordCodecBuilder.create(instance -> codecStart(instance)
//             .and(
//                 ITEM_WEIGHT_PAIR_CODEC.listOf().fieldOf("items").forGetter(m -> m.itemsWithWeights)
//             )
//             .apply(instance, AddItemsModifier::new))
//     );

//     public AddItemsModifier(LootItemCondition[] conditionsIn, List<Pair<ItemStack, Float>> itemsWithWeights) {
//         super(conditionsIn);
//         this.itemsWithWeights = itemsWithWeights;
//     }

//     @Override
//     public Codec<? extends IGlobalLootModifier> codec() {
//         return CODEC.get();
//     }

//     @Override
//     protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
//         if (Arrays.stream(this.conditions).allMatch(condition -> condition.test(context))) {
//             // Calculate total weight
//             float totalWeight = itemsWithWeights.stream().map(Pair::getRight).reduce(0f, Float::sum);

//             // Generate a random number between 0 and totalWeight
//             float rand = context.getRandom().nextFloat() * totalWeight;

//             float cumulativeWeight = 0f;
//             for (Pair<ItemStack, Float> pair : itemsWithWeights) {
//                 cumulativeWeight += pair.getRight();
//                 if (rand <= cumulativeWeight) {
//                     generatedLoot.add(pair.getLeft().copy());
//                     break;
//                 }
//             }
//         }
//         return generatedLoot;
//     }

//     // @Override
//     // protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
//     //     if (Arrays.stream(this.conditions).allMatch(condition -> condition.test(context))) {
//     //         for (Pair<ItemStack, Float> pair : itemsWithWeights) {
//     //             if (context.getRandom().nextFloat() <= pair.getRight()) {
//     //                 generatedLoot.add(pair.getLeft().copy());
//     //             }
//     //         }
//     //     }
//     //     return generatedLoot;
//     // }
// }