package com.gamergaming.taczweaponblueprints.progression;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small server-lifetime cache that rejects duplicate death callbacks. */
final class ResearchPointCombatRecentEvents {
    static final int MAX_ENTRIES = 2_048;
    static final long RETENTION_TICKS = 20L;

    private final LinkedHashMap<DeathToken, Long> accepted = new LinkedHashMap<>();
    private long lastGameTime = -1L;

    boolean accept(UUID victimId, long gameTime) {
        if (victimId == null || gameTime < 0L) {
            return false;
        }
        if (lastGameTime >= 0L && gameTime < lastGameTime) {
            accepted.clear();
        }
        lastGameTime = gameTime;
        prune(gameTime);
        DeathToken token = new DeathToken(victimId, gameTime);
        if (accepted.containsKey(token)) {
            return false;
        }
        accepted.put(token, gameTime);
        trimToLimit();
        return true;
    }

    void clear() {
        accepted.clear();
        lastGameTime = -1L;
    }

    int size() {
        return accepted.size();
    }

    private void prune(long gameTime) {
        Iterator<Map.Entry<DeathToken, Long>> iterator = accepted.entrySet().iterator();
        while (iterator.hasNext()) {
            long recordedAt = iterator.next().getValue();
            if (gameTime - recordedAt > RETENTION_TICKS) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private void trimToLimit() {
        Iterator<DeathToken> iterator = accepted.keySet().iterator();
        while (accepted.size() > MAX_ENTRIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record DeathToken(UUID victimId, long gameTime) {
    }
}
