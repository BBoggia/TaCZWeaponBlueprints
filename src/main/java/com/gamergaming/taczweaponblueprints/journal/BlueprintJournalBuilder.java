package com.gamergaming.taczweaponblueprints.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchProgressionConnectivity;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteOverlay;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;

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
        return build(
                catalog,
                researchSnapshot,
                config,
                playerData,
                blockedPredicate,
                null);
    }

    public static BlueprintJournalSnapshot build(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            AutomaticWeaponPrerequisitePlan automaticPrerequisites) {
        return build(
                catalog,
                researchSnapshot,
                config,
                playerData,
                blockedPredicate,
                ignored -> false,
                automaticPrerequisites);
    }

    public static BlueprintJournalSnapshot build(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot researchSnapshot,
            BlueprintProgressionConfigSnapshot config,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate,
            AutomaticWeaponPrerequisitePlan automaticPrerequisites) {
        if (catalog == null || researchSnapshot == null || config == null || playerData == null) {
            return BlueprintJournalSnapshot.EMPTY;
        }
        if (!config.blueprintsEnabled() || !config.journalEnabled()) {
            return BlueprintJournalSnapshot.EMPTY;
        }
        Predicate<String> blocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        Predicate<ResourceLocation> exempt = progressionExemptPredicate == null
                ? ignored -> false
                : progressionExemptPredicate;
        Map<ResourceLocation, ResourceLocation> entryPointReplacements =
                BlueprintResearchPolicyResolver.entryPointReplacements(
                        researchSnapshot,
                        catalog,
                        config.activeProfileId(),
                        blocked,
                        exempt);
        boolean automaticWeaponAuthority = researchSnapshot
                .usesAutomaticWeaponPlacement(config.activeProfileId());
        List<Map.Entry<ResourceLocation, BlueprintData>> sortedCatalog =
                new ArrayList<>(catalog.entrySet());
        sortedCatalog.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));

        Map<ResourceLocation, BlueprintResearchPolicy> structuralPolicies =
                new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BlueprintData> catalogEntry : sortedCatalog) {
            ResourceLocation blueprintId = catalogEntry.getKey();
            if (exempt.test(blueprintId)) {
                continue;
            }
            BlueprintResearchPolicy datapackPolicy = BlueprintResearchPolicyResolver.resolve(
                    researchSnapshot,
                    catalog,
                    config.activeProfileId(),
                    blueprintId,
                    playerData,
                    blocked,
                    exempt);
            BlueprintResearchPolicy policy = config.apply(datapackPolicy);
            policy = AutomaticWeaponPrerequisiteOverlay.apply(
                    policy,
                    automaticPrerequisites,
                    playerData,
                    blocked,
                    config.maximumUndiscoveredVisibility().allowsServerSelection(),
                    catalog::containsKey,
                    exempt,
                    entryPointReplacements,
                    automaticWeaponAuthority
                            && catalogEntry.getValue().getKind() == BlueprintKind.GUN);
            structuralPolicies.put(blueprintId, policy);
        }
        ResearchProgressionConnectivity connectivity =
                new ResearchProgressionConnectivity(
                        playerData, structuralPolicies::get, exempt);

        List<BlueprintJournalEntry> entries = new ArrayList<>();
        int learned = 0;
        int discovered = 0;
        int researchable = 0;
        for (Map.Entry<ResourceLocation, BlueprintData> catalogEntry : sortedCatalog) {
            BlueprintResearchPolicy structuralPolicy =
                    structuralPolicies.get(catalogEntry.getKey());
            if (structuralPolicy == null) {
                continue;
            }
            BlueprintResearchPolicy policy = structuralPolicy.withPrerequisitesSatisfied(
                    connectivity.requirementsSatisfied(structuralPolicy));
            if (!policy.journalEnabled() || !policy.visibility().appearsInJournal() || policy.blocked()) {
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
