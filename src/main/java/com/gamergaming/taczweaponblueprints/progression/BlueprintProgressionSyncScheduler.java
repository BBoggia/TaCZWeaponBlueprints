package com.gamergaming.taczweaponblueprints.progression;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.server.level.ServerPlayer;

/** Coalesces progression updates to at most one full snapshot per player tick. */
public final class BlueprintProgressionSyncScheduler {
    private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();

    private BlueprintProgressionSyncScheduler() {
    }

    public static void markDirty(ServerPlayer player) {
        if (player != null) {
            markDirty(player.getUUID());
        }
    }

    public static void flush(ServerPlayer player) {
        if (player != null && consume(player.getUUID())) {
            NetworkHandler.syncPlayerProgressionData(player);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            DIRTY_PLAYERS.remove(player.getUUID());
        }
    }

    static boolean markDirty(UUID playerId) {
        return playerId != null && DIRTY_PLAYERS.add(playerId);
    }

    static boolean consume(UUID playerId) {
        return playerId != null && DIRTY_PLAYERS.remove(playerId);
    }

    public static void clearAll() {
        DIRTY_PLAYERS.clear();
    }
}
