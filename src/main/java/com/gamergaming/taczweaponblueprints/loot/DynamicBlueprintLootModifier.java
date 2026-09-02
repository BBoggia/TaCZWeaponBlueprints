package com.gamergaming.taczweaponblueprints.loot;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootSnapshot;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public final class DynamicBlueprintLootModifier extends LootModifier {
    public static final Supplier<Codec<DynamicBlueprintLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(instance ->
                    codecStart(instance).apply(instance, DynamicBlueprintLootModifier::new)));

    public DynamicBlueprintLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context) {
        BlueprintLootPolicyResolver.RuntimeDefaults defaults = BlueprintLootRuntimeConfig.capture();
        if (!defaults.blueprintsEnabled()) {
            return generatedLoot;
        }

        BlueprintLootSnapshot snapshot = BlueprintLootDataManager.INSTANCE.snapshot();
        ResourceLocation lootTableId = context.getQueriedLootTableId();
        List<BlueprintLootSnapshot.RuleBinding> bindings = snapshot.rulesFor(lootTableId);
        if (bindings.isEmpty()) {
            return generatedLoot;
        }

        int existingBlueprints = (int) generatedLoot.stream()
                .filter(stack -> stack.getItem() instanceof BlueprintItem)
                .count();
        int remainingBudget = BlueprintLootSelector.remainingBlueprintBudget(existingBlueprints);
        Map<ResourceLocation, BlueprintData> catalog = BlueprintDataManager.SERVER.getBlueprintDataMap();
        RandomSource random = context.getRandom();
        for (BlueprintLootSnapshot.RuleBinding binding : bindings) {
            if (remainingBudget == 0) {
                break;
            }
            remainingBudget -= applyRule(
                    generatedLoot,
                    snapshot,
                    catalog,
                    binding,
                    context.getLevel().dimension().location(),
                    context.getLuck(),
                    defaults,
                    random,
                    remainingBudget);
        }
        return generatedLoot;
    }

    private static int applyRule(
            ObjectArrayList<ItemStack> generatedLoot,
            BlueprintLootSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintLootSnapshot.RuleBinding binding,
            ResourceLocation dimension,
            float luck,
            BlueprintLootPolicyResolver.RuntimeDefaults defaults,
            RandomSource random,
            int remainingBudget) {
        BlueprintLootPolicyResolver.RuleSettings settings = BlueprintLootPolicyResolver.resolveSettings(
                binding, dimension, luck, defaults);
        if (!settings.shouldEvaluateChance()) {
            return 0;
        }
        if (random.nextFloat() >= settings.chance()) {
            return 0;
        }

        BlueprintLootPolicyResolver.EffectiveRule policy = BlueprintLootPolicyResolver.resolveCandidates(
                snapshot, binding, catalog, settings);
        if (policy.candidates().isEmpty()) {
            return 0;
        }

        BlueprintLootPolicyResolver.RollRange rolls = policy.rolls();
        int rollCount = rolls.min();
        if (rolls.max() > rolls.min()) {
            rollCount += random.nextInt(rolls.max() - rolls.min() + 1);
        }
        rollCount = BlueprintLootSelector.constrainRollsToBudget(rollCount, remainingBudget);

        int added = 0;
        for (int i = 0; i < rollCount; i++) {
            var selected = policy.select(random.nextFloat());
            if (selected.isPresent()) {
                ResourceLocation blueprintId = selected.get();
                generatedLoot.add(BlueprintItem.createBlueprint(blueprintId.toString()));
                added++;
            }
        }
        return added;
    }

}
