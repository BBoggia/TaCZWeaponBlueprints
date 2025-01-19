package com.gamergaming.taczweaponblueprints.loot;

import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public class AddItemModifier extends LootModifier {

    private final ItemStack itemStack;

    public static final Supplier<Codec<AddItemModifier>> CODEC = Suppliers.memoize(() ->
    RecordCodecBuilder.create(inst -> codecStart(inst)
        .and(ItemStack.CODEC.fieldOf("itemStack").forGetter(m -> m.itemStack))
        .apply(inst, AddItemModifier::new))
    );

    public AddItemModifier(LootItemCondition[] conditionsIn, ItemStack itemStack) {
        super(conditionsIn);
        this.itemStack = itemStack;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        for (LootItemCondition condition : this.conditions) {
            if (!condition.test(context)) {
                return generatedLoot;
            }
        }
        
        generatedLoot.add(itemStack.copy());
        return generatedLoot;
    }
    
}
