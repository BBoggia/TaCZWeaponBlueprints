package com.gamergaming.taczweaponblueprints.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.world.item.ItemStack;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonAssetsManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public class BlueprintDataManager {

    public static final BlueprintDataManager INSTANCE = new BlueprintDataManager();

    private final Map<ResourceLocation, BlueprintData> blueprintDataMap = new HashMap<>();

    private BlueprintDataManager() { }

    public void initialize(MinecraftServer server) {
        TaCZWeaponBlueprints.LOGGER.info("Initializing Blueprint Data Manager");
        assert CommonAssetsManager.getInstance() != null;
        TaCZWeaponBlueprints.LOGGER.info("Recipe Manager is not null");
        blueprintDataMap.clear();

        List<GunSmithTableRecipe> recipes = CommonAssetsManager.getInstance().recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get());

        for (GunSmithTableRecipe recipe : recipes) {
            ItemStack itemStack = recipe.getResult().getResult();

            String recipeIdStr = recipe.getId().toString();

            if (!recipeIdStr.contains(":") || !recipeIdStr.contains("/")) {
                TaCZWeaponBlueprints.LOGGER.warn("Recipe ID {} is malformed, skipping.", recipeIdStr);
                continue;
            }
            String splitId = recipeIdStr.split(":")[1].split("/")[0];

            String nameKey = "";
            String tooltipKey = "";
            String itemType = "";
            ResourceLocation displaySlotKey = null;
            ResourceLocation itemId = null;

            switch (splitId) {
                case "gun" -> {
                    if (itemStack.getItem() instanceof IGun gun) {
                        itemId = gun.getGunId(itemStack);

                        if (itemId == null) {
                            TaCZWeaponBlueprints.LOGGER.warn("Gun item in recipe {} returned null ID, skipping.", recipeIdStr);
                            continue;
                        }
                        CommonGunIndex indx = CommonAssetsManager.getInstance().getGunIndex(gun.getGunId(itemStack));
                        assert indx != null;
                        nameKey = indx.getPojo().getName();
                        tooltipKey = indx.getPojo().getTooltip();
                        itemId = gun.getGunId(itemStack);
                        itemType = indx.getType();
                        displaySlotKey = indx.getPojo().getDisplay();
                    } else {
                        TaCZWeaponBlueprints.LOGGER.error("Item is not an instance of IGun: {}", itemStack.getItem().getClass().getName());
                    }
                }
                case "ammo" -> {
                    if (itemStack.getItem() instanceof IAmmo ammo) {
                        CommonAmmoIndex indx = CommonAssetsManager.getInstance().getAmmoIndex(ammo.getAmmoId(itemStack));
                        assert indx != null;
                        nameKey = indx.getPojo().getName();
                        tooltipKey = indx.getPojo().getTooltip();
                        itemId = ammo.getAmmoId(itemStack);
                        itemType = "ammo";
                        displaySlotKey = indx.getPojo().getDisplay();
                    } else {
                        TaCZWeaponBlueprints.LOGGER.error("Item is not an instance of IAmmo: {}", itemStack.getItem().getClass().getName());
                    }
                }
                case "attachments" -> {
                    if (itemStack.getItem() instanceof IAttachment attachment) {
                        CommonAttachmentIndex indx = CommonAssetsManager.getInstance().getAttachmentIndex(attachment.getAttachmentId(itemStack));
                        assert indx != null;
                        nameKey = indx.getPojo().getName();
                        tooltipKey = indx.getPojo().getTooltip();
                        itemId = attachment.getAttachmentId(itemStack);
                        itemType = indx.getType().name().toLowerCase();
                        displaySlotKey = indx.getPojo().getDisplay();
                    } else {
                        TaCZWeaponBlueprints.LOGGER.error("Item is not an instance of IAttachment: {}", itemStack.getItem().getClass().getName());
                    }
                }
                default -> TaCZWeaponBlueprints.LOGGER.info("Unknown item type: {}", splitId);
            }

            tooltipKey = "item.taczweaponblueprints.blueprint.tooltip";

//            TaCZWeaponBlueprints.LOGGER.info("FOR REAL ADDING Blueprint Recipe {} to recipe {} with item type: {}", itemId, recipe.getId(), itemType);

            if (nameKey.isEmpty() || itemId == null || displaySlotKey == null) {
                TaCZWeaponBlueprints.LOGGER.error("Failed to add Blueprint Recipe {} to recipe {} with item type: {}", itemId, recipe.getId(), itemType);
                continue;
            }

            BlueprintData data = new BlueprintData(itemId.toString(), nameKey, tooltipKey, recipe.getId(), recipe, itemType, displaySlotKey);
            blueprintDataMap.put(itemId, data);

        }
    }

    public Map<ResourceLocation, BlueprintData> getBlueprintDataMap() {
        return blueprintDataMap;
    }

    public void setBlueprintDataMap(Map<ResourceLocation, BlueprintData> blueprintDataMap) {
        this.blueprintDataMap.clear();
        this.blueprintDataMap.putAll(blueprintDataMap);
    }

    public static String getBlueprintIdFromResourceLocation(ResourceLocation recipeId) {
        return recipeId.getNamespace() + ":" + recipeId.getPath().split("/")[1];
    }

    public BlueprintData getBlueprintData(String bpId) {
        if (bpId == null || bpId.isEmpty()) {
            TaCZWeaponBlueprints.LOGGER.error("Blueprint ID is null or empty");
            return null;
        }
        return blueprintDataMap.get(new ResourceLocation(bpId));
    }

    public Collection<BlueprintData> getAllBlueprints() {
        return blueprintDataMap.values();
    }

    public Collection<BlueprintData> getRifleBlueprints() { 
        return getBlueprintsByType("rifle");
    }

    public Collection<BlueprintData> getPistolBlueprints() {
        return getBlueprintsByType("pistol");
    }

    public Collection<BlueprintData> getSniperBlueprints() {
        return getBlueprintsByType("sniper");
    }

    public Collection<BlueprintData> getShotgunBlueprints() {
        return getBlueprintsByType("shotgun");
    }

    public Collection<BlueprintData> getSmgBlueprints() {
        return getBlueprintsByType("smg");
    }

    public Collection<BlueprintData> getRpgBlueprints() {
        return getBlueprintsByType("rpg");
    }

    public Collection<BlueprintData> getMgBlueprints() {
        return getBlueprintsByType("mg");
    }

    public Collection<BlueprintData> getAmmoBlueprints() {
        return getBlueprintsByType("ammo");
    }

    public Collection<BlueprintData> getExtendedMagBlueprints() {
        return getBlueprintsByType("extended_mag");
    }

    public Collection<BlueprintData> getScopeBlueprints() {
        return getBlueprintsByType("scope");
    }

    public Collection<BlueprintData> getMuzzleBlueprints() {
        return getBlueprintsByType("muzzle");
    }

    public Collection<BlueprintData> getStockBlueprints() {
        return getBlueprintsByType("stock");
    }

    public Collection<BlueprintData> getGripBlueprints() {
        return getBlueprintsByType("grip");
    }

    public Collection<BlueprintData> getBlueprintsByType(String itemType) {
        List<BlueprintData> blueprints = new ArrayList<>();
        for (BlueprintData data : blueprintDataMap.values()) {
            if (data.getItemType().equals(itemType)) {
                blueprints.add(data);
            }
        }
        return blueprints;
    }
}
