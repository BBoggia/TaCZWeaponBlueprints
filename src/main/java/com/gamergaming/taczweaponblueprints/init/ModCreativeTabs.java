package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TaCZWeaponBlueprints.MODID);

    public static RegistryObject<CreativeModeTab> AMMO_BLUEPRINT_TAB = CREATIVE_TABS.register("ammo_blueprint", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tab.tacz.ammo").append(" Blueprints")).withTabsBefore(com.tacz.guns.init.ModCreativeTabs.GUN_MG_TAB.getId())
            .icon(() -> new ItemStack(ModItems.AMMO_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getAmmoBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_SCOPE_BLUEPRINT_TAB = CREATIVE_TABS.register("scope_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.scope.name").append(" Blueprints")).withTabsBefore(AMMO_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SCOPE_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getScopeBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_MUZZLE_BLUEPRINT_TAB = CREATIVE_TABS.register("muzzle_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.muzzle.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_SCOPE_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.MUZZLE_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getMuzzleBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_STOCK_BLUEPRINT_TAB = CREATIVE_TABS.register("stock_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.stock.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_MUZZLE_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.STOCK_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getStockBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_GRIP_BLUEPRINT_TAB = CREATIVE_TABS.register("grip_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.grip.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_STOCK_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.GRIP_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getGripBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> ATTACHMENT_EXTENDED_MAG_BLUEPRINT_TAB = CREATIVE_TABS.register("extended_mag_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.extended_mag.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_GRIP_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.EXTENDED_MAG_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getExtendedMagBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> GUN_PISTOL_BLUEPRINT_TAB = CREATIVE_TABS.register("pistol_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.pistol.name").append(" Blueprints")).withTabsBefore(ATTACHMENT_EXTENDED_MAG_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.PISTOL_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getPistolBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> GUN_SNIPER_BLUEPRINT_TAB = CREATIVE_TABS.register("sniper_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.sniper.name").append(" Blueprints")).withTabsBefore(GUN_PISTOL_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SNIPER_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getSniperBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> GUN_RIFLE_BLUEPRINT_TAB = CREATIVE_TABS.register("rifle_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.rifle.name").append(" Blueprints")).withTabsBefore(GUN_SNIPER_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.RIFLE_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getRifleBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> GUN_SHOTGUN_BLUEPRINT_TAB = CREATIVE_TABS.register("shotgun_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.shotgun.name").append(" Blueprints")).withTabsBefore(GUN_RIFLE_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SHOTGUN_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getShotgunBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> GUN_SMG_BLUEPRINT_TAB = CREATIVE_TABS.register("smg_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.smg.name").append(" Blueprints")).withTabsBefore(GUN_SHOTGUN_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.SMG_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getSmgBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> GUN_RPG_BLUEPRINT_TAB = CREATIVE_TABS.register("rpg_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.rpg.name").append(" Blueprints")).withTabsBefore(GUN_SMG_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.RPG_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getRpgBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static RegistryObject<CreativeModeTab> GUN_MG_BLUEPRINT_TAB = CREATIVE_TABS.register("mg_blueprints", () -> CreativeModeTab.builder()
            .title(Component.translatable("tacz.type.mg.name").append(" Blueprints")).withTabsBefore(GUN_RPG_BLUEPRINT_TAB.getId())
            .icon(() -> new ItemStack(ModItems.MG_BLUEPRINT_ITEM.get()))
            .displayItems((parameters, output) -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (BlueprintData data : BlueprintDataManager.INSTANCE.getMgBlueprints()) {
                        output.accept(BlueprintItem.createBlueprint(data.getBpId()));
                    }
                }
            })
            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
