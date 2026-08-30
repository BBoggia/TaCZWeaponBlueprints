package com.gamergaming.taczweaponblueprints.progression;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.server.level.ServerPlayer;

/** Coalesces progression updates to at most one full snapshot per player tick. */
public final class BlueprintProgressionSyncScheduler {
    private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> KNOWLEDGE_DIRTY_PLAYERS =
            ConcurrentHashMap.newKeySet();

    private BlueprintProgressionSyncScheduler() {
    }

    public static void markDirty(ServerPlayer player) {
        if (player != null) {
            markDirty(player.getUUID());
        }
    }

    /** Schedules the stronger recipe-plus-progression publication. */
    public static void markKnowledgeDirty(ServerPlayer player) {
        if (player != null) {
            markKnowledgeDirty(player.getUUID());
        }
    }

    public static void flush(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        boolean knowledgeDirty = KNOWLEDGE_DIRTY_PLAYERS.remove(playerId);
        boolean progressionDirty = DIRTY_PLAYERS.remove(playerId);
        if (!knowledgeDirty && !progressionDirty) {
            return;
        }
        try {
            if (knowledgeDirty) {
                NetworkHandler.syncPlayerRecipeData(player);
            } else {
                NetworkHandler.syncPlayerProgressionData(player);
            }
        } catch (RuntimeException exception) {
            if (knowledgeDirty) {
                markKnowledgeDirty(playerId);
            } else {
                markDirty(playerId);
            }
            TaCZWeaponBlueprints.LOGGER.error(
                    "Deferred blueprint progression sync failed for {}; retaining it for retry",
                    player.getGameProfile().getName(),
                    exception);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            DIRTY_PLAYERS.remove(player.getUUID());
            KNOWLEDGE_DIRTY_PLAYERS.remove(player.getUUID());
        }
    }

    /**
     * Returns whether a complete progression publication is already queued for
     * this player. Point-only synchronization defers to that publication.
     */
    public static boolean hasPendingFullSync(ServerPlayer player) {
        return player != null && hasPendingFullSync(player.getUUID());
    }

    static boolean markDirty(UUID playerId) {
        return playerId != null && DIRTY_PLAYERS.add(playerId);
    }

    static boolean markKnowledgeDirty(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        DIRTY_PLAYERS.remove(playerId);
        return KNOWLEDGE_DIRTY_PLAYERS.add(playerId);
    }

    static boolean consume(UUID playerId) {
        return playerId != null && DIRTY_PLAYERS.remove(playerId);
    }

    static boolean hasPendingFullSync(UUID playerId) {
        return playerId != null && (DIRTY_PLAYERS.contains(playerId)
                || KNOWLEDGE_DIRTY_PLAYERS.contains(playerId));
    }

    static boolean hasPendingKnowledgeSync(UUID playerId) {
        return playerId != null && KNOWLEDGE_DIRTY_PLAYERS.contains(playerId);
    }

    public static void clearAll() {
        DIRTY_PLAYERS.clear();
        KNOWLEDGE_DIRTY_PLAYERS.clear();
    }
}
