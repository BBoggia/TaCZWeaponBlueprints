package com.gamergaming.taczweaponblueprints.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.GunSmithTableResult;

@Mixin(GunSmithTableRecipe.class)
public interface IGunSmithTableRecipeAccessor {
    
    @Accessor(value = "result", remap = false)
    void setResult(GunSmithTableResult result);
}
