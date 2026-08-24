package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "3";
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
        });
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

}
