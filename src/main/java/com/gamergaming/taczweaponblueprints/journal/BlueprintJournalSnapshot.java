package com.gamergaming.taczweaponblueprints.journal;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.RecentBlueprintUnlockBatch.Source;

import net.minecraft.resources.ResourceLocation;

/** One complete atomic Journal publication for a player. */
public record BlueprintJournalSnapshot(
        List<BlueprintJournalEntry> entries,
        List<HistoryEntry> unavailableHistory,
        List<RecentUnlockBatch> recentUnlocks,
        int researchPoints,
        int pointCap,
        int learnedCount,
        int discoveredCount,
        int researchableCount) {
    public static final BlueprintJournalSnapshot EMPTY =
            new BlueprintJournalSnapshot(List.of(), List.of(), List.of(), 0, 0, 0, 0, 0);

    public BlueprintJournalSnapshot(
            List<BlueprintJournalEntry> entries,
            List<HistoryEntry> unavailableHistory,
            int researchPoints,
            int pointCap,
            int learnedCount,
            int discoveredCount,
            int researchableCount) {
        this(entries, unavailableHistory, List.of(), researchPoints, pointCap,
                learnedCount, discoveredCount, researchableCount);
    }

    public BlueprintJournalSnapshot {
        if ((entries != null && entries.stream().anyMatch(java.util.Objects::isNull))
                || (unavailableHistory != null
                        && unavailableHistory.stream().anyMatch(java.util.Objects::isNull))
                || (recentUnlocks != null
                        && recentUnlocks.stream().anyMatch(java.util.Objects::isNull))) {
            throw new IllegalArgumentException("Journal snapshots cannot contain null entries");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
        unavailableHistory = unavailableHistory == null ? List.of() : List.copyOf(unavailableHistory);
        recentUnlocks = recentUnlocks == null ? List.of() : List.copyOf(recentUnlocks);
        if (entries.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || unavailableHistory.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || recentUnlocks.size() > PlayerProgressionLimits.MAX_RECENT_UNLOCK_BATCHES
                || recentUnlocks.stream().mapToInt(batch -> batch.memberBlueprintIds().size()).sum()
                        > PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBER_IDS) {
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
        long previousSequence = 0L;
        for (RecentUnlockBatch batch : recentUnlocks) {
            if (batch.sequence() <= previousSequence) {
                throw new IllegalArgumentException(
                        "Recent Journal unlocks must have increasing sequences");
            }
            previousSequence = batch.sequence();
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

    public record RecentUnlockBatch(
            long sequence,
            Source source,
            ResourceLocation targetBlueprintId,
            List<ResourceLocation> memberBlueprintIds,
            int totalMemberCount) {
        public RecentUnlockBatch {
            if (sequence < 1L || source == null || targetBlueprintId == null
                    || memberBlueprintIds == null || memberBlueprintIds.isEmpty()
                    || memberBlueprintIds.size()
                            > PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBERS_PER_BATCH
                    || memberBlueprintIds.stream().anyMatch(java.util.Objects::isNull)
                    || memberBlueprintIds.stream().distinct().count() != memberBlueprintIds.size()
                    || !memberBlueprintIds.contains(targetBlueprintId)
                    || totalMemberCount < memberBlueprintIds.size()
                    || totalMemberCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                throw new IllegalArgumentException("Journal recent unlock batch is invalid");
            }
            memberBlueprintIds = List.copyOf(memberBlueprintIds);
        }

        public boolean truncated() {
            return totalMemberCount > memberBlueprintIds.size();
        }
    }
}
