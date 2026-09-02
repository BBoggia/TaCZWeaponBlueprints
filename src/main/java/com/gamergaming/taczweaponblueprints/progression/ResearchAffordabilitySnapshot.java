package com.gamergaming.taczweaponblueprints.progression;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

/** Bounded affordability-only evidence for one progressive Research Tree batch. */
public record ResearchAffordabilitySnapshot(List<Entry> entries) {
    public static final int MAX_TARGETS_PER_BATCH = 8;

    public ResearchAffordabilitySnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
        Set<ResourceLocation> identities = new LinkedHashSet<>();
        if (entries.isEmpty()
                || entries.size() > MAX_TARGETS_PER_BATCH
                || entries.stream().anyMatch(java.util.Objects::isNull)
                || entries.stream().anyMatch(entry -> !identities.add(entry.targetId()))) {
            throw new IllegalArgumentException("invalid research affordability snapshot");
        }
    }

    public record Entry(
            ResourceLocation targetId,
            ResearchGuidanceSnapshot.State state,
            boolean transactionCapacityAvailable) {
        public Entry {
            if (targetId == null
                    || targetId.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                    || state == null
                    || state == ResearchGuidanceSnapshot.State.CHECKING) {
                throw new IllegalArgumentException("invalid research affordability entry");
            }
        }

        /** Resource affordability deliberately remains separate from commit capacity. */
        public boolean affordableNow() {
            return state == ResearchGuidanceSnapshot.State.AFFORDABLE;
        }
    }
}
