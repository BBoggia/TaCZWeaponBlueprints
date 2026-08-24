package com.gamergaming.taczweaponblueprints.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/** Builds a deterministic, server-authoritative, disclosure-filtered Journal. */
public final class BlueprintJournalBuilder {
    private BlueprintJournalBuilder() {
    }

    public static BlueprintJournalSnapshot build(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate) {
        if (catalog == null || researchSnapshot == null || config == null || playerData == null) {
            return BlueprintJournalSnapshot.EMPTY;
        }
        if (!config.blueprintsEnabled() || !config.journalEnabled()) {
            return BlueprintJournalSnapshot.EMPTY;
        }
        Predicate<String> blocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        List<Map.Entry<ResourceLocation, BlueprintData>> sortedCatalog =
                new ArrayList<>(catalog.entrySet());
        sortedCatalog.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));

        List<BlueprintJournalEntry> entries = new ArrayList<>();
        int learned = 0;
        int discovered = 0;
        int researchable = 0;
        for (Map.Entry<ResourceLocation, BlueprintData> catalogEntry : sortedCatalog) {
            ResourceLocation blueprintId = catalogEntry.getKey();
            BlueprintResearchPolicy datapackPolicy = BlueprintResearchPolicyResolver.resolve(
                    researchSnapshot,
                    catalog,
                    config.activeProfileId(),
                    blueprintId,
                    playerData,
                    blocked);
            BlueprintResearchPolicy policy = config.apply(datapackPolicy);
            if (!policy.journalEnabled() || policy.visibility() == JournalVisibility.HIDDEN || policy.blocked()) {
                continue;
            }
            BlueprintJournalEntry entry = BlueprintJournalEntry.create(
                    entries.size(), catalogEntry.getValue(), policy);
            entries.add(entry);
            learned += policy.learned() ? 1 : 0;
            discovered += policy.discovered() ? 1 : 0;
            researchable += policy.researchable() ? 1 : 0;
        }

        TreeSet<String> historyIds = new TreeSet<>(playerData.getDiscoveredBlueprints());
        historyIds.removeAll(catalog.keySet().stream().map(ResourceLocation::toString).toList());
        List<BlueprintJournalSnapshot.HistoryEntry> history = historyIds.stream()
                .map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull)
                .map(id -> new BlueprintJournalSnapshot.HistoryEntry(
                        id,
                        playerData.hasBlueprint(id.toString())))
                .toList();
        return new BlueprintJournalSnapshot(
                entries,
                history,
                playerData.getResearchPoints(),
                config.pointCap(),
                learned,
                discovered,
                researchable);
    }
}
