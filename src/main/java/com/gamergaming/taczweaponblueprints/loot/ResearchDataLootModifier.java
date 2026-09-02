package com.gamergaming.taczweaponblueprints.loot;

import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Small, independently replaceable loot injection for physical Research Data.
 * Exact loot-table selection remains in the normal Forge modifier conditions,
 * while the item and chance remain visible in the generated datapack JSON.
 */
public final class ResearchDataLootModifier extends LootModifier {
    private static final Codec<Float> CHANCE_CODEC = Codec.FLOAT.flatXmap(
            ResearchDataLootModifier::validateChance,
            ResearchDataLootModifier::validateChance);

    public static final Supplier<Codec<ResearchDataLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(instance ->
                    codecStart(instance)
                            .and(ItemStack.CODEC.fieldOf("item").forGetter(value -> value.item))
                            .and(CHANCE_CODEC.fieldOf("chance").forGetter(value -> value.chance))
                            .apply(instance, ResearchDataLootModifier::new)));

    private final ItemStack item;
    private final float chance;

    public ResearchDataLootModifier(
            LootItemCondition[] conditions,
            ItemStack item,
            float chance) {
        super(conditions);
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("Research Data loot item cannot be empty");
        }
        if (!(chance > 0.0f && chance <= 1.0f) || !Float.isFinite(chance)) {
            throw new IllegalArgumentException("Research Data loot chance must be in (0, 1]");
        }
        this.item = item.copy();
        this.chance = chance;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context) {
        if (ModConfigs.BLUEPRINT.enableResearchPointAwards.get()
                && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(item.copy());
        }
        return generatedLoot;
    }

    ItemStack item() {
        return item.copy();
    }

    float chance() {
        return chance;
    }

    private static DataResult<Float> validateChance(float chance) {
        return chance > 0.0f && chance <= 1.0f && Float.isFinite(chance)
                ? DataResult.success(chance)
                : DataResult.error(() -> "Research Data loot chance must be in (0, 1]");
    }
}
