package com.gamergaming.taczweaponblueprints.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

@Mixin(CreativeModeTabs.class)
public interface ICreativeModeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError();
    }
}
