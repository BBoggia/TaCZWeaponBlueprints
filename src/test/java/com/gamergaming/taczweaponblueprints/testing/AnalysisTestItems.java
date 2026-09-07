package com.gamergaming.taczweaponblueprints.testing;

import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;

/** Registers two real item fixtures without starting a client or loading mod configuration. */
public final class AnalysisTestItems {
    public static BlueprintFragmentItem fragment;
    public static BlueprintItem blueprint;
    private AnalysisTestItems() { }
    public static synchronized void initialize() {
        if (fragment != null) return;
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        var forge = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
        registry.unfreeze(); forge.unfreeze();
        try {
            fragment = new BlueprintFragmentItem(new Item.Properties());
            forge.register(new ResourceLocation("analysis_test:fragment"), fragment);
            blueprint = new BlueprintItem(new Item.Properties());
            forge.register(new ResourceLocation("analysis_test:blueprint"), blueprint);
        } catch (RuntimeException exception) {
            try { registry.freeze(); } catch (RuntimeException freeze) { exception.addSuppressed(freeze); }
            forge.freeze(); throw exception;
        }
        registry.freeze(); forge.freeze();
    }
}
