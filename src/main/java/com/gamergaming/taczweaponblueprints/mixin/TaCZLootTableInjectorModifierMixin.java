package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance;
import com.tacz.guns.loot.LootTableInjectorModifier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks guns observed in TaCZ's authoritative global loot-generation pass. */
@Mixin(LootTableInjectorModifier.class)
public abstract class TaCZLootTableInjectorModifierMixin {
    @Inject(method = "doApply", at = @At("RETURN"), remap = false)
    private void markGeneratedWeapons(
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> callback) {
        ObjectArrayList<ItemStack> result = callback.getReturnValue();
        ResourceLocation lootTableId = context == null
                ? null
                : context.getQueriedLootTableId();
        if (result == null || lootTableId == null) {
            return;
        }
        result.forEach(stack ->
                PhysicalWeaponProvenance.stampLootGenerated(stack, lootTableId));
    }
}
