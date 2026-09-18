package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** One validated publication. Resource-path replacement is the only override mechanism. */
public record TradeSnapshot(Map<ResourceLocation, TradeDefinition> definitions) {
    public static final TradeSnapshot EMPTY = new TradeSnapshot(Map.of());
    public TradeSnapshot {
        definitions = Collections.unmodifiableMap(new TreeMap<>(definitions));
        if (definitions.size() > TradeLimits.DEFINITIONS) throw new IllegalArgumentException("too many trade definitions");
        Set<ResourceLocation> slots = new HashSet<>();
        Map<TradeDefinition.Profession, Integer> counts = new EnumMap<>(TradeDefinition.Profession.class);
        int candidates = 0;
        for (var entry : definitions.entrySet()) {
            var d = entry.getValue();
            if (!entry.getKey().equals(d.id())) throw new IllegalArgumentException("definition ID mismatch");
            // Disabled definitions retain ownership, avoiding order-dependent takeover.
            if (d.role() != TradeDefinition.Role.FALLBACK) {
                if (!slots.add(d.slot())) throw new IllegalArgumentException("conflicting slot ownership: " + d.slot());
                if (counts.merge(d.profession(), 1, Integer::sum) > TradeLimits.SLOTS_PER_PROFESSION)
                    throw new IllegalArgumentException("too many profession slots");
            }
            candidates += d.output().candidates().size();
            if (candidates > TradeLimits.CANDIDATE_IDS) throw new IllegalArgumentException("aggregate candidate ID limit exceeded");
            for (var id : d.fallbacks()) {
                var fallback = definitions.get(id);
                if (fallback == null || fallback.role() != TradeDefinition.Role.FALLBACK
                        || fallback.profession() != d.profession() || fallback.level() != d.level())
                    throw new IllegalArgumentException("fallback must exist at the same profession and level: " + id);
            }
        }
        // Memoized DAG walk: shared fallback branches cannot create exponential work.
        Map<ResourceLocation, Integer> depths = new HashMap<>();
        for (var id : definitions.keySet()) depth(id, definitions, depths, new HashSet<>());
    }
    private static int depth(ResourceLocation id, Map<ResourceLocation, TradeDefinition> defs,
            Map<ResourceLocation, Integer> depths, Set<ResourceLocation> visiting) {
        if (depths.containsKey(id)) return depths.get(id);
        if (!visiting.add(id)) throw new IllegalArgumentException("cyclic fallbacks: " + id);
        if (visiting.size() > TradeLimits.FALLBACK_DEPTH) throw new IllegalArgumentException("fallback depth exceeded");
        int depth = 1;
        for (var child : defs.get(id).fallbacks()) depth = Math.max(depth, 1 + depth(child, defs, depths, visiting));
        if (depth > TradeLimits.FALLBACK_DEPTH) throw new IllegalArgumentException("fallback depth exceeded");
        visiting.remove(id); depths.put(id, depth); return depth;
    }
}
