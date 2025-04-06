package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TaCZWeaponBlueprints.MODID);

    public static RegistryObject<CreativeModeTab> AMMO_BLUEPRINT_TAB = CREATIVE_TABS.register("ammo_blueprint", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tab.tacz.ammo").append(" Blueprints")).withTabsBefore(com.tacz.guns.init.ModCreativeTabs.GUN_MG_TAB.getId())
            .icon(() -> new ItemStack(ModItems.AMMO_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_SCOPE_BLUEPRINT_TAB = CREATIVE_TABS.register("scope_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.scope.name").append(" Blueprints")).withTabsBefore(AMMO_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SCOPE_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_MUZZLE_BLUEPRINT_TAB = CREATIVE_TABS.register("muzzle_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.muzzle.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_SCOPE_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.MUZZLE_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_STOCK_BLUEPRINT_TAB = CREATIVE_TABS.register("stock_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.stock.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_MUZZLE_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.STOCK_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_GRIP_BLUEPRINT_TAB = CREATIVE_TABS.register("grip_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.grip.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_STOCK_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.GRIP_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_EXTENDED_MAG_BLUEPRINT_TAB = CREATIVE_TABS.register("extended_mag_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.extended_mag.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_GRIP_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.EXTENDED_MAG_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> GUN_PISTOL_BLUEPRINT_TAB = CREATIVE_TABS.register("pistol_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.pistol.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_EXTENDED_MAG_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.PISTOL_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> GUN_SNIPER_BLUEPRINT_TAB = CREATIVE_TABS.register("sniper_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.sniper.name").append(" Blueprints")).withTabsBefore(GUN_PISTOL_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SNIPER_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> GUN_RIFLE_BLUEPRINT_TAB = CREATIVE_TABS.register("rifle_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.rifle.name").append(" Blueprints")).withTabsBefore(GUN_SNIPER_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.RIFLE_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> GUN_SHOTGUN_BLUEPRINT_TAB = CREATIVE_TABS.register("shotgun_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.shotgun.name").append(" Blueprints")).withTabsBefore(GUN_RIFLE_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SHOTGUN_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> GUN_SMG_BLUEPRINT_TAB = CREATIVE_TABS.register("smg_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.smg.name").append(" Blueprints")).withTabsBefore(GUN_SHOTGUN_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SMG_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> GUN_RPG_BLUEPRINT_TAB = CREATIVE_TABS.register("rpg_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.rpg.name").append(" Blueprints")).withTabsBefore(GUN_SMG_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.RPG_BLUEPRINT_ITEM.get()))
            .build());

    public static RegistryObject<CreativeModeTab> GUN_MG_BLUEPRINT_TAB = CREATIVE_TABS.register("mg_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.mg.name").append(" Blueprints")).withTabsBefore(GUN_RPG_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.MG_BLUEPRINT_ITEM.get()))
            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }

    @SubscribeEvent
    public static void buildCreativeModeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTabs.AMMO_BLUEPRINT_TAB.getKey()) {
            if (BlueprintDataManager.INSTANCE.getAllBlueprints().isEmpty() && !ModConfigs.BLUEPRINT.enableBlueprints.get()) { // Only warns if blueprints are enabled but manager is empty
                TaCZWeaponBlueprints.LOGGER.warn("Attempted to populate Ammo Blueprints tab, but BlueprintDataManager is empty!");
            }
            TaCZWeaponBlueprints.LOGGER.debug("Populating Ammo Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getAmmoBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) { // Check blacklist
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.GUN_RIFLE_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Rifle Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getRifleBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.GUN_PISTOL_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Pistol Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getPistolBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.GUN_SNIPER_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Sniper Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getSniperBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.GUN_SHOTGUN_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Shotgun Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getShotgunBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.GUN_SMG_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating SMG Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getSmgBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.GUN_RPG_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating RPG Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getRpgBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.GUN_MG_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating MG Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getMgBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.ATTACHMENT_SCOPE_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Scope Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getScopeBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.ATTACHMENT_MUZZLE_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Muzzle Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getMuzzleBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.ATTACHMENT_STOCK_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Stock Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getStockBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.ATTACHMENT_GRIP_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Grip Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getGripBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        } else if (event.getTabKey() == ModCreativeTabs.ATTACHMENT_EXTENDED_MAG_BLUEPRINT_TAB.getKey()) {
            TaCZWeaponBlueprints.LOGGER.debug("Populating Extended Mag Blueprints tab (Key: {})...", event.getTabKey());
            for (BlueprintData data : BlueprintDataManager.INSTANCE.getExtendedMagBlueprints()) {
                if (!ModConfigs.BLUEPRINT.isItemBlacklisted(data.getBpId())) {
                    event.accept(BlueprintItem.createBlueprint(data.getBpId()));
                }
            }
        }

        TaCZWeaponBlueprints.LOGGER.debug("Finished populating tab (Key: {})", event.getTabKey());
        
    }
}
