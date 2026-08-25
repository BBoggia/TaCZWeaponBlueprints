package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalBuilder;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "6";
    // A random per-server seed prevents a partial chunk set from an earlier
    // connection being mistaken for a new sync after reconnecting.
    private static final AtomicLong SYNC_SEQUENCE =
            new AtomicLong(ThreadLocalRandom.current().nextLong());
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(TaCZWeaponBlueprints.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public static void registerPackets() {
        int id = 0;

        INSTANCE.registerMessage(id++, SyncPlayerRecipeDataPacket.class,
                SyncPlayerRecipeDataPacket::toBytes, SyncPlayerRecipeDataPacket::new,
                SyncPlayerRecipeDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncBlueprintDataPacket.class,
                SyncBlueprintDataPacket::toBytes, SyncBlueprintDataPacket::new,
                SyncBlueprintDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncPlayerProgressionPacket.class,
                SyncPlayerProgressionPacket::toBytes, SyncPlayerProgressionPacket::new,
                SyncPlayerProgressionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, SyncBlueprintJournalPacket.class,
                SyncBlueprintJournalPacket::toBytes, SyncBlueprintJournalPacket::new,
                SyncBlueprintJournalPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++, ResearchBenchActionPacket.class,
                ResearchBenchActionPacket::toBytes, ResearchBenchActionPacket::new,
                ResearchBenchActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++, SyncResearchBenchPreviewPacket.class,
                SyncResearchBenchPreviewPacket::toBytes, SyncResearchBenchPreviewPacket::new,
                SyncResearchBenchPreviewPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void syncPlayerRecipeData(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(recipeData -> {
            BlueprintDataManager.SERVER.migrateLegacyUnlocks(recipeData);
            var activeRecipes = RecipeSyncFilter.activeLearnedRecipes(
                    recipeData.getLearnedRecipes(),
                    recipeData.getLearnedBlueprints(),
                    BlueprintDataManager.SERVER.getBlueprintDataMap(),
                    BlueprintDataManager.SERVER.getRecipeToBlueprintMap());
            SyncPlayerRecipeDataPacket.split(activeRecipes, SYNC_SEQUENCE.incrementAndGet())
                    .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
            sendPlayerProgressionData(player, recipeData);
        });
    }

    public static void syncPlayerProgressionData(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendPlayerProgressionData(player, recipeData));
    }

    public static void syncJournalData(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(recipeData -> sendJournalData(player, recipeData));
        if (player.containerMenu instanceof com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu menu) {
            menu.refreshAuthoritativePreview(player);
        }
    }

    public static void syncBlueprintData(ServerPlayer player) {
        SyncBlueprintDataPacket.split(
                        BlueprintDataManager.SERVER.getBlueprintDataMap(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
    }

    public static void syncAllPlayerData(ServerPlayer player) {
        syncBlueprintData(player);
        syncPlayerRecipeData(player);
    }

    public static void sendResearchBenchPreview(
            ServerPlayer player,
            int containerId,
            com.gamergaming.taczweaponblueprints.menu.ResearchBenchPreview preview) {
        INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncResearchBenchPreviewPacket(containerId, preview));
    }

    private static void sendPlayerProgressionData(ServerPlayer player, IPlayerRecipeData recipeData) {
        BlueprintProgressionSyncScheduler.clear(player);
        SyncPlayerProgressionPacket.split(
                        recipeData.getLearnedBlueprints(),
                        recipeData.getDiscoveredBlueprints(),
                        recipeData.getResearchPoints(),
                        SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
        sendJournalData(player, recipeData);
    }

    private static void sendJournalData(ServerPlayer player, IPlayerRecipeData recipeData) {
        var snapshot = BlueprintJournalBuilder.build(
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                BlueprintResearchDataManager.INSTANCE.snapshot(),
                BlueprintResearchDataManager.INSTANCE.progressionConfig(),
                recipeData,
                ModConfigs.BLUEPRINT::isItemBlacklisted);
        SyncBlueprintJournalPacket.split(snapshot, SYNC_SEQUENCE.incrementAndGet())
                .forEach(packet -> INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet));
    }

}
