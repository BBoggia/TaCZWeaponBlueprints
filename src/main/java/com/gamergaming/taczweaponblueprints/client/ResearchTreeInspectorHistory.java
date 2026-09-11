package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.function.Predicate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import net.minecraft.resources.ResourceLocation;

/** Screen-local history for deliberate detail links, never hover or camera movement. */
final class ResearchTreeInspectorHistory {
    static final int LIMIT = 32;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    void push(Entry entry) {
        if (entries.size() == LIMIT) entries.removeFirst();
        entries.addLast(entry);
    }
    void retain(Predicate<Entry> valid) { entries.removeIf(valid.negate()); }
    Optional<Entry> pop() { return Optional.ofNullable(entries.pollLast()); }
    boolean available() { return !entries.isEmpty(); }
    int size() { return entries.size(); }
    void clear() { entries.clear(); }
    record Entry(Domain domain, ResourceLocation target, ResearchTreeViewport.Snapshot camera, int scroll, Boolean expanded) {
        Entry {
            if (domain == null || target == null || camera == null || scroll < 0)
                throw new IllegalArgumentException("invalid inspector history entry");
        }
    }
}
