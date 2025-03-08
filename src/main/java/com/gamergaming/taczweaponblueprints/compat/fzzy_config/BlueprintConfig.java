package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackLoader;

import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.WithPerms;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.util.AllowableStrings;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedSet;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.resources.ResourceLocation;

@Translation(prefix = ModConfigs.BASE_KEY + "blueprint")
public class BlueprintConfig extends Config {

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableBlueprints = new ValidatedBoolean(true);

    // @Comment("Blueprint spawn chance")
    @WithPerms(opLevel = 2)
    public ValidatedDouble blueprintSpawnChance = new ValidatedDouble(0.2, 1.0, 0.0);

    // @Comment("Minimum number of blueprints that can spawn if spawn chance is met")
    @WithPerms(opLevel = 2)
    public ValidatedInt minBlueprints = new ValidatedInt(1, Integer.MAX_VALUE, 0);

    // @Comment("Maximum number of blueprints that can spawn if spawn chance is met")
    @WithPerms(opLevel = 2)
    public ValidatedInt maxBlueprints = new ValidatedInt(2, Integer.MAX_VALUE, 0);
    
    public BlueprintConfig() {
        super(TaCZWeaponBlueprints.loc("blueprint"));
        // config.taczweaponblueprints.blueprint
    }

    @Override
    public void update(int deserializedVersion) {
        // Make sure maxBlueprints >= minBlueprints

        int minValue = minBlueprints.get();
        int maxValue = maxBlueprints.get();
        
        if (maxValue < minValue) {
            maxBlueprints.setAndUpdate(minValue);
            // LOGGER.warn("maxBlueprints was less than minBlueprints; adjusted to match minBlueprints value.");
        }
    }

    @Override
    public void onUpdateClient() {
        int minValue = minBlueprints.get();
        int maxValue = maxBlueprints.get();
        
        if (maxValue < minValue) {
            maxBlueprints.setAndUpdate(minValue);
            // LOGGER.warn("maxBlueprints was less than minBlueprints; adjusted to match minBlueprints value.");
        }
    }


    // @WithPerms(opLevel = 2)
    // public ValidatedSet<String> startingBlueprints = new ValidatedSet<>(
    //     new HashSet<>(),
    //     new ValidatedString(
    //         "tacz:ak47", 
    //         new AllowableStrings(createGunIdFilter(), getGunItemIdStrings())
    //     )
    // );

    // @Comment("Blacklist of guns that will not have blueprints generated for them")
    @WithPerms(opLevel = 2)
    public ValidatedSet<String> gunBlacklist = new ValidatedSet<>(
        new HashSet<>(),
        new ValidatedString(
            "tacz:ak47", 
            new AllowableStrings(createGunIdFilter(), getGunItemIdStrings())
        )
    );

    // @Comment("Blacklist of ammo that will not have blueprints generated for them")
    @WithPerms(opLevel = 2)
    public ValidatedSet<String> ammoBlacklist = new ValidatedSet<>(
        new HashSet<>(),
        new ValidatedString(
            "oldgun:pf60_ammo", 
            new AllowableStrings(createAmmoIdFilter(), getAmmoItemIdStrings())
        )
    );

    // @Comment("Blacklist of attachments that will not have blueprints generated for them")
    @WithPerms(opLevel = 2)
    public ValidatedSet<String> attachmentBlacklist = new ValidatedSet<>(
        new HashSet<>(),
        new ValidatedString(
            "tacz:extended_mag_1", 
            new AllowableStrings(createAttachmentIdFilter(), getAttachmentItemIdStrings())
        )
    );

    public boolean isItemBlacklisted(String itemId) {
        return gunBlacklist.contains(itemId) || ammoBlacklist.contains(itemId) || attachmentBlacklist.contains(itemId);
    }

    public boolean isItemRecipeBlacklisted(String recipeId) {
        return gunBlacklist.get().stream().map(item -> item.replace(":", ":gun/")).collect(Collectors.toList()).contains(recipeId) ||
               ammoBlacklist.get().stream().map(item -> item.replace(":", ":ammo/")).collect(Collectors.toList()).contains(recipeId) ||
               attachmentBlacklist.get().stream().map(item -> item.replace(":", ":attachment/")).collect(Collectors.toList()).contains(recipeId);
    }

    private static Predicate<String> createGunIdFilter() {
       return gunId -> {
           return CommonAssetsManager.getInstance().getAllGuns()
               .stream()
               .map(gun -> gun.getKey().toString())
               .collect(Collectors.toSet())
               .contains(gunId);
       };
   }

    private static Predicate<String> createAttachmentIdFilter() {
        return attachmentId -> {
            return CommonAssetsManager.getInstance().getAllAttachments()
                .stream()
                .map(attachment -> attachment.getKey().toString())
                .collect(Collectors.toList())
                .contains(attachmentId);
        };
    }

    private static Predicate<String> createAmmoIdFilter() {
        return ammoId -> {
            return CommonAssetsManager.getInstance().getAllAmmos()
                .stream()
                .map(ammo -> ammo.getKey().toString())
                .collect(Collectors.toList())
                .contains(ammoId);
        };
    }

    private static Supplier<List<String>> getAttachmentItemIdStrings() {
        return () -> {
            List<String> attachmentIds = new ArrayList<>();
            CommonAssetsManager.getInstance().getAllAttachments().forEach(attachment -> attachmentIds.add(attachment.getKey().toString()));
            return attachmentIds;
        };
    }

    private static Supplier<List<String>> getAmmoItemIdStrings() {
        return () -> {
            List<String> ammoIds = new ArrayList<>();
            CommonAssetsManager.getInstance().getAllAmmos().forEach(ammo -> ammoIds.add(ammo.getKey().toString()));
            return ammoIds;
        };
    }

    private static Supplier<List<String>> getGunItemIdStrings() {
        return () -> {
            List<String> gunIds = new ArrayList<>();
            CommonAssetsManager.getInstance().getAllGuns().forEach(gun -> gunIds.add(gun.getKey().toString()));
            return gunIds;
        };
    }
    
}
