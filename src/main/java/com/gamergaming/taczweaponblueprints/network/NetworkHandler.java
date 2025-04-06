package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.tacz.guns.network.message.ServerMessageSound;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "1";
    private static final AtomicInteger ID_COUNT = new AtomicInteger(1);
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(TaCZWeaponBlueprints.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

//    public static void registerPackets() {
//        //INSTANCE.registerMessage(ID_COUNT.getAndIncrement(), GunSoundPacket.class, GunSoundPacket::encode, GunSoundPacket::decode, GunSoundPacket::handle);
//        int id = 0;
//        INSTANCE.registerMessage(id++, SyncPlayerRecipeDataPacket.class,
//                SyncPlayerRecipeDataPacket::toBytes, SyncPlayerRecipeDataPacket::new,
//                SyncPlayerRecipeDataPacket::handle,
//                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
//
//        INSTANCE.registerMessage(ID_COUNT.getAndIncrement(), ServerMessageSound.class, ServerMessageSound::encode, ServerMessageSound::decode, ServerMessageSound::handle,
//                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
//    }

    public static void registerPackets() {
        int id = 0;

        // Register PlayerRecipeDataSyncPacket
        INSTANCE.registerMessage(id++, SyncPlayerRecipeDataPacket.class,
                SyncPlayerRecipeDataPacket::toBytes, SyncPlayerRecipeDataPacket::new,
                SyncPlayerRecipeDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Register the SyncBlueprintDataPacket
        INSTANCE.registerMessage(id++, SyncBlueprintDataPacket.class,
                SyncBlueprintDataPacket::toBytes, SyncBlueprintDataPacket::new,
                SyncBlueprintDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Register ServerMessageSound with the next ID
        INSTANCE.registerMessage(id++, ServerMessageSound.class,
                ServerMessageSound::encode, ServerMessageSound::decode, ServerMessageSound::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}