package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-thread-only, short-lived confirmations. No saved claim or balance is created. */
public final class ResearchCurrencyWithdrawals {
    public static final int MAX_PENDING = 256;
    public static final long LIFETIME_TICKS = 1200;
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    public record Pending(String owner, UUID player, long expires, ResearchCurrencyPlanner.Plan plan,
                          ResearchCurrencyTransaction.Context context) { }

    public UUID preview(String owner, UUID player, long tick, ResearchCurrencyPlanner.Plan plan,
                        ResearchCurrencyTransaction.Context context) {
        if (!plan.quote().ready()) throw new IllegalArgumentException("withdrawal is not ready");
        prune(tick);
        pending.values().removeIf(p -> p.player().equals(player));
        if (pending.size() >= MAX_PENDING) pending.remove(pending.keySet().iterator().next());
        var id = UUID.randomUUID();
        pending.put(id, new Pending(owner, player, tick + LIFETIME_TICKS, plan, context));
        return id;
    }
    /** Consume before committing, including stale/failing attempts. Another operator cannot consume it. */
    public Optional<Pending> take(UUID token, String owner, long tick) {
        prune(tick);
        var value = pending.get(token);
        if (value == null || !value.owner().equals(owner)) return Optional.empty();
        pending.remove(token);
        return Optional.of(value);
    }
    private void prune(long tick) { pending.values().removeIf(p -> tick >= p.expires() || tick < p.expires() - LIFETIME_TICKS); }
    public void clear(UUID player) { pending.values().removeIf(p -> p.player().equals(player) || p.owner().equals(player.toString())); }
    public void clear() { pending.clear(); }
}
