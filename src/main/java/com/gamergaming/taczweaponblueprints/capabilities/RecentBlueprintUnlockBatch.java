package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.List;

/** One bounded, persisted record of an authoritative blueprint-learning commit. */
public record RecentBlueprintUnlockBatch(
        long sequence,
        Source source,
        String targetBlueprintId,
        List<String> memberBlueprintIds,
        int totalMemberCount) {
    public RecentBlueprintUnlockBatch {
        if (sequence < 1L || source == null
                || PlayerRecipeData.normalizeResourceId(targetBlueprintId) == null
                || memberBlueprintIds == null || memberBlueprintIds.isEmpty()
                || memberBlueprintIds.size()
                        > PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBERS_PER_BATCH
                || memberBlueprintIds.stream().anyMatch(id ->
                        PlayerRecipeData.normalizeResourceId(id) == null)
                || memberBlueprintIds.stream().distinct().count() != memberBlueprintIds.size()
                || !memberBlueprintIds.contains(targetBlueprintId)
                || totalMemberCount < memberBlueprintIds.size()
                || totalMemberCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("invalid recent blueprint unlock batch");
        }
        memberBlueprintIds = List.copyOf(memberBlueprintIds);
    }

    public boolean truncated() {
        return totalMemberCount > memberBlueprintIds.size();
    }

    public enum Source {
        TREE_RESEARCH,
        PHYSICAL_BLUEPRINT,
        ADMINISTRATOR
    }
}
