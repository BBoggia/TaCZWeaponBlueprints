package com.gamergaming.taczweaponblueprints.init;

import net.minecraft.world.item.Item;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
        public static final DeferredRegister<Item> ITEMS =
                DeferredRegister.create(Registries.ITEM, TaCZWeaponBlueprints.MODID);

        public static final RegistryObject<Item> BLUEPRINT_ITEM =
                ITEMS.register("blueprint", () -> new BlueprintItem(
                        new Item.Properties()
                        .stacksTo(1)
                ));
        public static final RegistryObject<Item> EMPTY_BLUEPRINT_ITEM =
                ITEMS.register("empty_blueprint", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> AMMO_BLUEPRINT_ITEM =
                ITEMS.register("ammo_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> MG_BLUEPRINT_ITEM =
                ITEMS.register("mg_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> SHOTGUN_BLUEPRINT_ITEM =
                ITEMS.register("shotgun_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> SNIPER_BLUEPRINT_ITEM =
                ITEMS.register("sniper_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> SMG_BLUEPRINT_ITEM =
                ITEMS.register("smg_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> PISTOL_BLUEPRINT_ITEM =
                ITEMS.register("pistol_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> RIFLE_BLUEPRINT_ITEM =
                ITEMS.register("rifle_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> RPG_BLUEPRINT_ITEM =
                ITEMS.register("rpg_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> EXTENDED_MAG_BLUEPRINT_ITEM =
                ITEMS.register("extended_mag_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> SCOPE_BLUEPRINT_ITEM =
                ITEMS.register("scope_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> MUZZLE_BLUEPRINT_ITEM =
                ITEMS.register("muzzle_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> STOCK_BLUEPRINT_ITEM =
                ITEMS.register("stock_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));

        public static final RegistryObject<Item> GRIP_BLUEPRINT_ITEM =
                ITEMS.register("grip_blueprint_tab", () -> new Item(
                        new Item.Properties()
                        .stacksTo(1)
                ));



        public static void register(IEventBus eventBus) {
                ITEMS.register(eventBus);
        }
}