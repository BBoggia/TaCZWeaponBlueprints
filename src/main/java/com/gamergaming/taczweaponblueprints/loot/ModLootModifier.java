package com.gamergaming.taczweaponblueprints.loot;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModLootModifier {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TaCZWeaponBlueprints.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddItemsModifier>> ADD_ITEMS_MODIFIER =
        LOOT_MODIFIER_SERIALIZERS.register("add_items", AddItemsModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<DynamicBlueprintLootModifier>> DYNAMIC_BLUEPRINTS_MODIFIER =
        LOOT_MODIFIER_SERIALIZERS.register("dynamic_blueprints", DynamicBlueprintLootModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ResearchDataLootModifier>> RESEARCH_DATA_MODIFIER =
        LOOT_MODIFIER_SERIALIZERS.register("research_data", ResearchDataLootModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ScrapSupplyLootModifier>> SCRAP_SUPPLY_MODIFIER =
        LOOT_MODIFIER_SERIALIZERS.register("scrap_supply", ScrapSupplyLootModifier.CODEC);

    public static void register(IEventBus eventBus) {
        LOOT_MODIFIER_SERIALIZERS.register(eventBus);
    }
}
