package com.gamergaming.taczweaponblueprints.loot;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.mojang.serialization.Codec;

import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModLootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TaCZWeaponBlueprints.MODID);
    
    public static final RegistryObject<Codec<AddItemsModifier>> ADD_ITEMS_MODIFIER =
        LOOT_MODIFIER_SERIALIZERS.register("add_items", AddItemsModifier.CODEC);

    public static void register(IEventBus eventBus) {
        LOOT_MODIFIER_SERIALIZERS.register(eventBus);
    }
}
