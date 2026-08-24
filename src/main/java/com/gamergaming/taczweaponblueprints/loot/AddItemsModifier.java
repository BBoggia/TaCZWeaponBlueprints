package com.gamergaming.taczweaponblueprints.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
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

public class AddItemsModifier extends LootModifier {
    private final List<Pair<ItemStack, Float>> itemsWithWeights;
    // Retained in the codec so existing generated modifier JSON remains compatible.
    // Runtime chance and roll bounds come from the live synchronized config.
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
        this.itemsWithWeights = snapshotItems(itemsWithWeights);
        this.randomRollRange = Map.of("min", min, "max", max);
        this.poolProbability = poolProbability;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // Phase 4 compatibility bridge: keep decoding legacy modifiers, but do not
        // apply them while a valid dynamic datapack snapshot owns distribution.
        if (BlueprintLootDataManager.INSTANCE.ownsLootDistribution(context.getQueriedLootTableId())) {
            return generatedLoot;
        }
        if (!ModConfigs.BLUEPRINT.enableBlueprints.get()) {
            return generatedLoot;
        }

        RandomSource random = context.getRandom();
        float poolChance = BlueprintLootSelector.sanitizeProbability(ModConfigs.BLUEPRINT.blueprintSpawnChance.get());
        if (random.nextFloat() >= poolChance) {
            return generatedLoot;
        }

        List<BlueprintLootSelector.WeightedEntry<ItemStack>> candidates = new ArrayList<>();
        for (Pair<ItemStack, Float> item : this.itemsWithWeights) {
            BlueprintLootSelector.createEntry(
                    BlueprintItem.getBpId(item.getLeft()),
                    item.getLeft(),
                    item.getRight()).ifPresent(candidates::add);
        }
        List<BlueprintLootSelector.WeightedEntry<ItemStack>> availableItems = BlueprintLootSelector.filterEligible(
                candidates,
                blueprintId -> BlueprintDataManager.SERVER.getBlueprintData(blueprintId.toString()) != null
                        && !ModConfigs.BLUEPRINT.isItemBlacklisted(blueprintId.toString()));
        if (availableItems.isEmpty()) {
            return generatedLoot;
        }

        int existingBlueprints = (int) generatedLoot.stream()
                .filter(stack -> stack.getItem() instanceof BlueprintItem)
                .count();
        int remainingBudget = BlueprintLootSelector.remainingBlueprintBudget(existingBlueprints);
        if (remainingBudget == 0) {
            return generatedLoot;
        }

        BlueprintLootSelector.RollRange rollRange = BlueprintLootSelector.sanitizeRollRange(
                ModConfigs.BLUEPRINT.minBlueprints.get(),
                ModConfigs.BLUEPRINT.maxBlueprints.get());
        int rolls = rollRange.min();
        if (rollRange.max() > rollRange.min()) {
            rolls += random.nextInt(rollRange.max() - rollRange.min() + 1);
        }
        rolls = BlueprintLootSelector.constrainRollsToBudget(rolls, remainingBudget);

        for (int i = 0; i < rolls; i++) {
            BlueprintLootSelector.selectWeighted(availableItems, random.nextFloat())
                    .ifPresent(selected -> generatedLoot.add(selected.value().copy()));
        }
        return generatedLoot;
    }

    private static List<Pair<ItemStack, Float>> snapshotItems(List<Pair<ItemStack, Float>> items) {
        if (items == null) {
            return List.of();
        }
        List<Pair<ItemStack, Float>> snapshot = new ArrayList<>();
        for (Pair<ItemStack, Float> item : items) {
            if (item != null && item.getLeft() != null) {
                snapshot.add(Pair.of(item.getLeft().copy(), item.getRight()));
            }
        }
        return List.copyOf(snapshot);
    }

}
