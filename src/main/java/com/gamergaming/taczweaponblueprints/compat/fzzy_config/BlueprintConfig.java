package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalanceSettings;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.StartingBlueprintGrantService;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.progression.TreeResearchResultMode;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

@Translation(prefix = ModConfigs.BASE_KEY + "blueprint")
public class BlueprintConfig extends Config {
    public static final int MAX_BLUEPRINTS_PER_LOOT_CONTAINER = 64;
    public static final net.minecraft.resources.ResourceLocation DEFAULT_RESEARCH_PROFILE =
            TaCZWeaponBlueprints.loc("duplicate_recovery");
    private static final String RESOURCE_LOCATION_PATTERN =
            "(?=.{1," + PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH + "}$)"
                    + "[a-z0-9_.-]+:[a-z0-9/._-]+";
    private static final Pattern PERSISTED_BALANCE_PRESET = Pattern.compile(
            "(?m)^\\s*balancePreset\\s*=\\s*\"([A-Z_]+)\"\\s*(?:#.*)?$");

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableBlueprints = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableDiscoveryTracking = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableJournal = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedEnum<BlueprintBalancePreset> balancePreset =
            new ValidatedEnum<>(BlueprintBalancePreset.CUSTOM);

    @WithPerms(opLevel = 2)
    public ValidatedEnum<JournalVisibility> maximumUndiscoveredVisibility =
            new ValidatedEnum<>(JournalVisibility.FULL);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableResearch = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedEnum<TreeResearchResultMode> treeResearchResultMode =
            new ValidatedEnum<>(TreeResearchResultMode.DIRECT_LEARN);

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

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableResearchPointAwards = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableCombatResearchPointAwards = new ValidatedBoolean(false);

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
    private transient volatile BlueprintAccessConfigSnapshot accessSnapshot;
    private transient volatile ResearchPointAwardConfigSnapshot awardSnapshot;
    
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
            // Exemptions are live policy rather than saved knowledge, so a
            // config-only add/remove must republish gun-smith access even when
            // no starting grant changed the capability.
            refreshOnlinePlayers(player.server);
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

    public ResearchPointAwardConfigSnapshot awardSnapshot() {
        return awardSnapshot;
    }

    public BlueprintAccessConfigSnapshot accessSnapshot() {
        return accessSnapshot;
    }

    public BlueprintBalanceSettings balanceSettings() {
        return BlueprintBalanceSettings.resolve(
                balancePreset.get(),
                maximumUndiscoveredVisibility.get(),
                blueprintSpawnChance.get(),
                minBlueprints.get(),
                maxBlueprints.get());
    }

    /** Applies only the reversible preset selector and republishes live policy. */
    public synchronized BalancePresetApplication applyBalancePreset(
            BlueprintBalancePreset preset,
            MinecraftServer server) {
        if (preset == null) {
            throw new IllegalArgumentException("balance preset cannot be null");
        }
        boolean changed = balancePreset.get() != preset;
        if (changed) {
            // Do not normalize the dormant custom fields here: switching a
            // reversible overlay must not rewrite the values it is preserving.
            balancePreset.validateAndSet(preset);
            publishProgressionSnapshot();
        }
        // An explicit confirmed apply also repairs a missing/stale file when the
        // in-memory selector already has the requested value.
        save();
        boolean persisted = isBalancePresetPersisted(preset);
        int synchronizedPlayers = server == null ? 0 : refreshOnlinePlayers(server);
        return new BalancePresetApplication(changed, persisted, synchronizedPlayers);
    }

    /** Applies an authoritative server publication on a remote client without marking a GUI edit. */
    public synchronized void acceptSynchronizedBalancePreset(BlueprintBalancePreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("synchronized balance preset cannot be null");
        }
        balancePreset.validateAndSet(preset);
        publishProgressionSnapshot();
    }

    private void normalizeAndPublish() {
        int minValue = minBlueprints.get();
        int maxValue = maxBlueprints.get();
        if (maxValue < minValue) {
            maxBlueprints.setAndUpdate(minValue);
        }
        publishProgressionSnapshot();
    }

    private int refreshOnlinePlayers(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(onlinePlayer -> {
            NetworkHandler.syncBalancePreset(onlinePlayer, balancePreset.get());
            StartingBlueprintGrantService.applyConfiguredGrants(onlinePlayer);
            NetworkHandler.syncPlayerRecipeData(onlinePlayer);
            ResearchPointPresentationService.syncHelp(onlinePlayer);
            ResearchPointAwardReconciliationScheduler.schedule(onlinePlayer);
        });
        return server.getPlayerList().getPlayerCount();
    }

    private boolean isBalancePresetPersisted(BlueprintBalancePreset expected) {
        Path file = configFile();
        try {
            boolean matches = serializedBalancePresetMatches(Files.readString(file), expected);
            if (!matches) {
                TaCZWeaponBlueprints.LOGGER.error(
                        "Balance preset {} is active in memory but was not confirmed in {}",
                        expected,
                        file);
            }
            return matches;
        } catch (IOException | RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Balance preset {} is active in memory but persistence verification failed for {}",
                    expected,
                    file,
                    exception);
            return false;
        }
    }

    static boolean serializedBalancePresetMatches(
            String serialized,
            BlueprintBalancePreset expected) {
        if (serialized == null || expected == null) {
            return false;
        }
        Matcher matcher = PERSISTED_BALANCE_PRESET.matcher(serialized);
        return matcher.find() && expected.name().equals(matcher.group(1));
    }

    private Path configFile() {
        Path path = FMLPaths.CONFIGDIR.get();
        if (!getFolder().isBlank()) {
            path = path.resolve(getFolder());
        }
        if (!getSubfolder().isBlank()) {
            path = path.resolve(getSubfolder());
        }
        return path.resolve(getName() + ".toml");
    }

    private void publishProgressionSnapshot() {
        progressionSnapshot = BlueprintProgressionConfigSnapshot.from(this);
        accessSnapshot = BlueprintAccessConfigSnapshot.from(this);
        awardSnapshot = ResearchPointAwardConfigSnapshot.from(this, progressionSnapshot);
    }

    public record BalancePresetApplication(
            boolean changed,
            boolean persisted,
            int synchronizedPlayers) {
        public BalancePresetApplication {
            if (synchronizedPlayers < 0) {
                throw new IllegalArgumentException("synchronized player count cannot be negative");
            }
        }
    }

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> progressionExemptBlueprints = new ValidatedSet<>(
            new HashSet<>(),
            new ValidatedString("tacz:ak47", RESOURCE_LOCATION_PATTERN));

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> progressionExemptKinds = new ValidatedSet<>(
            new HashSet<>(),
            new ValidatedString(
                    "gun",
                    new AllowableStrings(
                            value -> List.of("gun", "ammo", "attachment").contains(value),
                            () -> List.of("gun", "ammo", "attachment"))));

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> progressionExemptItemTypes = new ValidatedSet<>(
            new HashSet<>(),
            new ValidatedString("pistol", "[a-z0-9_.-]{1,256}"));

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> startingBlueprints = new ValidatedSet<>(
            new HashSet<>(),
            new ValidatedString("tacz:ak47", RESOURCE_LOCATION_PATTERN));

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
