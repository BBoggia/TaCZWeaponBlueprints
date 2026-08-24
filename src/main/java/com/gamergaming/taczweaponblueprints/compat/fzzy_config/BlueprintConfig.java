package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.tacz.guns.resource.CommonAssetsManager;

import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.WithPerms;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.util.AllowableStrings;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedSet;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.server.level.ServerPlayer;

@Translation(prefix = ModConfigs.BASE_KEY + "blueprint")
public class BlueprintConfig extends Config {
    public static final int MAX_BLUEPRINTS_PER_LOOT_CONTAINER = 64;
    public static final net.minecraft.resources.ResourceLocation DEFAULT_RESEARCH_PROFILE =
            TaCZWeaponBlueprints.loc("duplicate_recovery");
    private static final String RESOURCE_LOCATION_PATTERN =
            "(?=.{1," + PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH + "}$)"
                    + "[a-z0-9_.-]+:[a-z0-9/._-]+";

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableBlueprints = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableDiscoveryTracking = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableJournal = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedEnum<JournalVisibility> maximumUndiscoveredVisibility =
            new ValidatedEnum<>(JournalVisibility.FULL);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableResearch = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedEnum<DuplicateBlueprintPolicy> duplicateBlueprintPolicy =
            new ValidatedEnum<>(DuplicateBlueprintPolicy.MANUAL_RECYCLING);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean allowUnlearnedRecycling = new ValidatedBoolean(false);

    @WithPerms(opLevel = 2)
    public ValidatedInt researchPointCap = new ValidatedInt(
            BlueprintProgressionConfigSnapshot.DEFAULT_POINT_CAP,
            PlayerProgressionLimits.MAX_RESEARCH_POINTS,
            0);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean creativeBypassesResearchCost = new ValidatedBoolean(false);

    @WithPerms(opLevel = 2)
    public ValidatedString activeResearchProfile = new ValidatedString(
            DEFAULT_RESEARCH_PROFILE.toString(),
            RESOURCE_LOCATION_PATTERN);

    // @Comment("Blueprint spawn chance")
    @WithPerms(opLevel = 2)
    public ValidatedDouble blueprintSpawnChance = new ValidatedDouble(0.2, 1.0, 0.0);

    // @Comment("Minimum number of blueprints that can spawn if spawn chance is met")
    @WithPerms(opLevel = 2)
    public ValidatedInt minBlueprints = new ValidatedInt(1, MAX_BLUEPRINTS_PER_LOOT_CONTAINER, 0);

    // @Comment("Maximum number of blueprints that can spawn if spawn chance is met")
    @WithPerms(opLevel = 2)
    public ValidatedInt maxBlueprints = new ValidatedInt(2, MAX_BLUEPRINTS_PER_LOOT_CONTAINER, 0);

    private transient volatile BlueprintProgressionConfigSnapshot progressionSnapshot;
    
    public BlueprintConfig() {
        super(TaCZWeaponBlueprints.loc("blueprint"));
        publishProgressionSnapshot();
    }

    @Override
    public void update(int deserializedVersion) {
        normalizeAndPublish();
    }

    @Override
    public void onUpdateClient() {
        normalizeAndPublish();
    }

    @Override
    public void onUpdateServer(ServerPlayer player) {
        normalizeAndPublish();
        if (player != null) {
            player.server.getPlayerList().getPlayers().forEach(NetworkHandler::syncJournalData);
        }
    }

    @Override
    public void onSyncClient() {
        publishProgressionSnapshot();
    }

    @Override
    public void onSyncServer() {
        publishProgressionSnapshot();
    }

    public BlueprintProgressionConfigSnapshot progressionSnapshot() {
        return progressionSnapshot;
    }

    private void normalizeAndPublish() {
        int minValue = minBlueprints.get();
        int maxValue = maxBlueprints.get();
        if (maxValue < minValue) {
            maxBlueprints.setAndUpdate(minValue);
        }
        publishProgressionSnapshot();
    }

    private void publishProgressionSnapshot() {
        progressionSnapshot = BlueprintProgressionConfigSnapshot.from(this);
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
