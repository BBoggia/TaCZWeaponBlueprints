package com.gamergaming.taczweaponblueprints.journal;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

/** One complete atomic Journal publication for a player. */
public record BlueprintJournalSnapshot(
        List<BlueprintJournalEntry> entries,
        List<HistoryEntry> unavailableHistory,
        int researchPoints,
        int pointCap,
        int learnedCount,
        int discoveredCount,
        int researchableCount) {
    public static final BlueprintJournalSnapshot EMPTY =
            new BlueprintJournalSnapshot(List.of(), List.of(), 0, 0, 0, 0, 0);

    public BlueprintJournalSnapshot {
        if ((entries != null && entries.stream().anyMatch(java.util.Objects::isNull))
                || (unavailableHistory != null
                        && unavailableHistory.stream().anyMatch(java.util.Objects::isNull))) {
            throw new IllegalArgumentException("Journal snapshots cannot contain null entries");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
        unavailableHistory = unavailableHistory == null ? List.of() : List.copyOf(unavailableHistory);
        if (entries.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || unavailableHistory.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("Journal snapshot exceeds the entry limit");
        }
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).ordinal() != index) {
                throw new IllegalArgumentException("Journal entry ordinals must be contiguous and deterministic");
            }
        }
        Set<ResourceLocation> disclosedIds = new HashSet<>();
        entries.forEach(entry -> entry.blueprintId().ifPresent(id -> {
            if (!disclosedIds.add(id)) {
                throw new IllegalArgumentException("Journal snapshot contains a duplicate disclosed blueprint ID");
            }
        }));
        ResourceLocation previousHistoryId = null;
        for (HistoryEntry entry : unavailableHistory) {
            if (previousHistoryId != null
                    && previousHistoryId.toString().compareTo(entry.blueprintId().toString()) >= 0) {
                throw new IllegalArgumentException("Journal history must be unique and sorted by blueprint ID");
            }
            previousHistoryId = entry.blueprintId();
        }
        if (researchPoints < 0 || researchPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || learnedCount < 0 || learnedCount > entries.size()
                || discoveredCount < learnedCount || discoveredCount > entries.size()
                || researchableCount < 0 || researchableCount > entries.size()) {
            throw new IllegalArgumentException("Journal summary is outside the supported range");
        }
    }

    public int completionTotal() {
        return entries.size();
    }

    public record HistoryEntry(ResourceLocation blueprintId, boolean learned) {
        public HistoryEntry {
            if (blueprintId == null
                    || blueprintId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                throw new IllegalArgumentException("Journal history contains an invalid blueprint ID");
            }
        }
    }
}
