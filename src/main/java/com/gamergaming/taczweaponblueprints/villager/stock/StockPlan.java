package com.gamergaming.taczweaponblueprints.villager.stock;

import java.util.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import net.minecraft.resources.ResourceLocation;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

/** Chosen merchandise only; prices and live permissions are never saved as authority. */
public record StockPlan(Profession profession, Map<ResourceLocation, Entry> entries,
        Map<ResourceLocation, String> excluded, boolean reachable) {
    public static final int MAX_ENTRIES = TradeLimits.SLOTS_PER_PROFESSION;
    public record Entry(ResourceLocation slot, ResourceLocation template, ResourceLocation definition,
            ResourceLocation target, Kind kind, int count, int level, Role role, Selection selection,
            List<ResourceLocation> dependencies) {
        public Entry {
            for (var id : List.of(slot, template, definition, target)) TradeDefinition.parseId(id.toString());
            Objects.requireNonNull(kind); Objects.requireNonNull(role); Objects.requireNonNull(selection);
            dependencies = List.copyOf(dependencies);
            if (count < 1 || count > 64 || level < 1 || level > 5 || role == Role.FALLBACK
                    || dependencies.size() > 4 || new HashSet<>(dependencies).size() != dependencies.size())
                throw new IllegalArgumentException("Invalid saved stock entry");
            dependencies.forEach(id -> TradeDefinition.parseId(id.toString()));
        }
        public boolean matches(TradeResolver.Row row) {
            return row != null && row.ready() && row.definition().equals(definition) && row.output().kind() == kind
                    && row.slot().equals(template) && row.role() == role && row.output().count() == count && row.level() == level && row.output().selection() == selection
                    && row.candidates().stream().anyMatch(c -> c.id().equals(target));
        }
    }
    public StockPlan {
        Objects.requireNonNull(profession);
        entries = Collections.unmodifiableMap(new TreeMap<>(entries)); excluded = Collections.unmodifiableMap(new TreeMap<>(excluded));
        if (entries.size() > MAX_ENTRIES || excluded.size() > MAX_ENTRIES) throw new IllegalArgumentException("Stock plan exceeds bounds");
        for (var e : entries.entrySet()) {
            if (!e.getKey().equals(e.getValue().slot())) throw new IllegalArgumentException("Stock identity mismatch");
            for (var dependency : e.getValue().dependencies()) {
                var other = entries.get(dependency);
                if (other == null || other.level() > e.getValue().level() || !other.dependencies().isEmpty())
                    throw new IllegalArgumentException("Invalid stock supply dependency");
            }
        }
        excluded.forEach((id, reason) -> {
            TradeDefinition.parseId(id.toString());
            if (reason == null || reason.length() > 160) throw new IllegalArgumentException("Invalid stock exclusion");
        });
    }
    public List<Entry> core(int level) {
        return entries.values().stream().filter(e -> e.level() == level && e.role() == Role.CORE).toList();
    }
}
