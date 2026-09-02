package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDiagnostics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/** Pure, deterministic catalog assessment used by the operator setup command. */
public final class BlueprintSetupAssistant {
    public static final int CURRENT_EXPORT_FORMAT = 1;
    static final int LARGE_CATALOG_THRESHOLD = 240;
    static final int ADDON_HEAVY_THRESHOLD = 96;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BlueprintSetupAssistant() {
    }

    public static Assessment assess(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchDiagnostics.Audit audit,
            BlueprintAccessConfigSnapshot access) {
        return assess(catalog, audit, access, RuntimeReadiness.DEFAULT);
    }

    public static Assessment assess(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchDiagnostics.Audit audit,
            BlueprintAccessConfigSnapshot access,
            RuntimeReadiness runtimeReadiness) {
        Map<ResourceLocation, BlueprintData> stableCatalog = new TreeMap<>(
                Comparator.comparing(ResourceLocation::toString));
        if (catalog != null) {
            catalog.forEach((id, data) -> {
                if (id != null && data != null) {
                    stableCatalog.put(id, data);
                }
            });
        }
        BlueprintAccessConfigSnapshot stableAccess = access == null
                ? BlueprintAccessConfigSnapshot.EMPTY
                : access;
        RuntimeReadiness stableRuntime = runtimeReadiness == null
                ? RuntimeReadiness.DEFAULT
                : runtimeReadiness;
        EnumMap<BlueprintKind, Integer> kinds = new EnumMap<>(BlueprintKind.class);
        for (BlueprintKind kind : BlueprintKind.values()) {
            kinds.put(kind, 0);
        }
        TreeSet<String> namespaces = new TreeSet<>();
        int addOnCount = 0;
        for (Map.Entry<ResourceLocation, BlueprintData> entry : stableCatalog.entrySet()) {
            namespaces.add(entry.getKey().getNamespace());
            kinds.merge(entry.getValue().getKind(), 1, Integer::sum);
            if (!"tacz".equals(entry.getKey().getNamespace())) {
                addOnCount++;
            }
        }
        BlueprintResearchDiagnostics.Audit stableAudit = audit == null
                ? BlueprintResearchDiagnostics.Audit.empty()
                : audit;
        Set<ResourceLocation> exemptions = BlueprintProgressionAccess.exemptBlueprintIds(
                stableAccess, stableCatalog);
        Set<ResourceLocation> excludedFromDiscovery = new HashSet<>(exemptions);
        stableAccess.startingBlueprints().stream()
                .filter(stableCatalog::containsKey)
                .forEach(excludedFromDiscovery::add);
        int effectiveDiscoveryCount = 0;
        int effectiveAddOnDiscoveryCount = 0;
        for (ResourceLocation id : stableCatalog.keySet()) {
            if (excludedFromDiscovery.contains(id)) {
                continue;
            }
            effectiveDiscoveryCount++;
            if (!"tacz".equals(id.getNamespace())) {
                effectiveAddOnDiscoveryCount++;
            }
        }
        int configuredStarting = stableAccess.startingBlueprints().size();
        int missingStarting = (int) stableAccess.startingBlueprints().stream()
                .filter(id -> !stableCatalog.containsKey(id))
                .count();
        int unmatchedExemptionSelectors = (int) stableAccess.progressionExemptBlueprints().stream()
                .filter(id -> !stableCatalog.containsKey(id))
                .count();
        unmatchedExemptionSelectors += (int) stableAccess.progressionExemptKinds().stream()
                .filter(kind -> stableCatalog.values().stream()
                        .noneMatch(data -> data.getKind() == kind))
                .count();
        unmatchedExemptionSelectors += (int) stableAccess.progressionExemptItemTypes().stream()
                .filter(type -> stableCatalog.values().stream().noneMatch(data ->
                        type.equals(data.getItemType() == null
                                ? ""
                                : data.getItemType().toLowerCase(java.util.Locale.ROOT))))
                .count();

        Status status;
        BlueprintBalancePreset recommendation;
        List<String> reasons;
        if (stableCatalog.isEmpty()) {
            status = Status.BLOCKED;
            recommendation = BlueprintBalancePreset.BALANCED;
            reasons = List.of("empty_catalog");
        } else {
            boolean needsReview = stableAudit.hasStructuralProblems()
                    || missingStarting > 0
                    || unmatchedExemptionSelectors > 0
                    || !stableRuntime.researchEnabled()
                    || !stableRuntime.lootDistributionAvailable();
            status = !stableRuntime.blueprintsEnabled()
                    ? Status.BLOCKED
                    : needsReview
                    ? Status.REVIEW_REQUIRED
                    : Status.READY;
            List<String> reasonList = new ArrayList<>();
            if (effectiveDiscoveryCount >= LARGE_CATALOG_THRESHOLD
                    || effectiveAddOnDiscoveryCount >= ADDON_HEAVY_THRESHOLD) {
                recommendation = BlueprintBalancePreset.ACCESSIBLE;
                reasonList.add("large_or_addon_heavy_discovery_workload");
            } else if (effectiveDiscoveryCount == 0) {
                recommendation = BlueprintBalancePreset.BALANCED;
                reasonList.add("no_remaining_discovery_workload");
            } else {
                recommendation = BlueprintBalancePreset.BALANCED;
                reasonList.add("standard_discovery_workload");
            }
            if (stableAudit.hasStructuralProblems()) {
                reasonList.add("research_structure_needs_review");
            }
            if (missingStarting > 0) {
                reasonList.add("missing_starting_blueprints");
            }
            if (unmatchedExemptionSelectors > 0) {
                reasonList.add("unmatched_exemption_selectors");
            }
            if (!stableRuntime.blueprintsEnabled()) {
                reasonList.add("blueprints_disabled");
            }
            if (!stableRuntime.researchEnabled()) {
                reasonList.add("research_disabled");
            }
            if (!stableRuntime.lootDistributionAvailable()) {
                reasonList.add("loot_distribution_unavailable");
            }
            reasons = List.copyOf(reasonList);
        }
        return new Assessment(
                status,
                recommendation,
                reasons,
                stableCatalog.size(),
                Map.copyOf(kinds),
                addOnCount,
                List.copyOf(namespaces),
                stableAudit,
                exemptions.size(),
                unmatchedExemptionSelectors,
                configuredStarting,
                missingStarting,
                effectiveDiscoveryCount,
                effectiveAddOnDiscoveryCount,
                stableRuntime);
    }

    public static String export(
            Assessment assessment,
            long catalogRevision,
            long researchRevision,
            BlueprintBalanceSettings activeSettings) {
        if (assessment == null || activeSettings == null) {
            throw new IllegalArgumentException("setup export requires an assessment and active settings");
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", CURRENT_EXPORT_FORMAT);
        root.addProperty("catalog_revision", catalogRevision);
        root.addProperty("research_revision", researchRevision);
        root.addProperty("status", assessment.status().serializedName());
        root.addProperty("recommended_preset", assessment.recommendedPreset().serializedName());
        JsonArray reasons = new JsonArray();
        assessment.reasons().forEach(reasons::add);
        root.add("recommendation_reasons", reasons);

        JsonObject active = new JsonObject();
        active.addProperty("preset", activeSettings.preset().serializedName());
        active.addProperty(
                "maximum_undiscovered_visibility",
                activeSettings.maximumUndiscoveredVisibility().serializedName());
        active.addProperty("loot_chance", activeSettings.lootChance());
        active.addProperty("minimum_loot_rolls", activeSettings.minimumLootRolls());
        active.addProperty("maximum_loot_rolls", activeSettings.maximumLootRolls());
        root.add("active_settings", active);

        JsonObject catalog = new JsonObject();
        catalog.addProperty("total", assessment.catalogSize());
        catalog.addProperty("guns", assessment.kindCount(BlueprintKind.GUN));
        catalog.addProperty("attachments", assessment.kindCount(BlueprintKind.ATTACHMENT));
        catalog.addProperty("ammo", assessment.kindCount(BlueprintKind.AMMO));
        catalog.addProperty("addon_entries", assessment.addOnBlueprintCount());
        catalog.addProperty("effective_discovery_entries", assessment.effectiveDiscoveryCount());
        catalog.addProperty(
                "effective_addon_discovery_entries",
                assessment.effectiveAddOnDiscoveryCount());
        JsonArray namespaces = new JsonArray();
        assessment.namespaces().forEach(namespaces::add);
        catalog.add("namespaces", namespaces);
        root.add("catalog", catalog);

        BlueprintResearchDiagnostics.Audit audit = assessment.researchAudit();
        JsonObject research = new JsonObject();
        research.addProperty("assigned", audit.assignedBlueprintCount());
        research.addProperty("tree_visible", audit.treeVisibleBlueprintCount());
        research.addProperty("roots", audit.rootCount());
        research.addProperty("components", audit.componentCount());
        research.addProperty("independent", audit.independentBlueprintIds().size());
        research.addProperty("missing_prerequisites", audit.missingPrerequisiteIds().size());
        research.addProperty("hidden_prerequisite_targets", audit.hiddenPrerequisiteTargetIds().size());
        research.addProperty("rule_competitions", audit.competitions().size());
        root.add("research", research);

        JsonObject access = new JsonObject();
        access.addProperty("effective_exemptions", assessment.effectiveExemptionCount());
        access.addProperty(
                "unmatched_exemption_selectors",
                assessment.unmatchedExemptionSelectorCount());
        access.addProperty("configured_starting_blueprints", assessment.configuredStartingCount());
        access.addProperty("missing_starting_blueprints", assessment.missingStartingCount());
        root.add("access", access);

        JsonObject runtime = new JsonObject();
        runtime.addProperty("blueprints_enabled", assessment.runtimeReadiness().blueprintsEnabled());
        runtime.addProperty("research_enabled", assessment.runtimeReadiness().researchEnabled());
        runtime.addProperty(
                "loot_distribution_available",
                assessment.runtimeReadiness().lootDistributionAvailable());
        root.add("runtime", runtime);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public enum Status {
        READY,
        REVIEW_REQUIRED,
        BLOCKED;

        public String serializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Assessment(
            Status status,
            BlueprintBalancePreset recommendedPreset,
            List<String> reasons,
            int catalogSize,
            Map<BlueprintKind, Integer> kindCounts,
            int addOnBlueprintCount,
            List<String> namespaces,
            BlueprintResearchDiagnostics.Audit researchAudit,
            int effectiveExemptionCount,
            int unmatchedExemptionSelectorCount,
            int configuredStartingCount,
            int missingStartingCount,
            int effectiveDiscoveryCount,
            int effectiveAddOnDiscoveryCount,
            RuntimeReadiness runtimeReadiness) {
        public Assessment {
            if (status == null || recommendedPreset == null || researchAudit == null
                    || runtimeReadiness == null) {
                throw new IllegalArgumentException("setup assessment contains null required state");
            }
            if (catalogSize < 0 || addOnBlueprintCount < 0 || effectiveExemptionCount < 0
                    || unmatchedExemptionSelectorCount < 0 || configuredStartingCount < 0
                    || missingStartingCount < 0 || effectiveDiscoveryCount < 0
                    || effectiveAddOnDiscoveryCount < 0
                    || effectiveDiscoveryCount > catalogSize
                    || effectiveAddOnDiscoveryCount > effectiveDiscoveryCount) {
                throw new IllegalArgumentException("setup assessment contains invalid counts");
            }
            reasons = List.copyOf(reasons);
            kindCounts = Map.copyOf(kindCounts);
            namespaces = namespaces.stream().sorted(Comparator.naturalOrder()).toList();
        }

        public int kindCount(BlueprintKind kind) {
            return kindCounts.getOrDefault(kind, 0);
        }
    }

    public record RuntimeReadiness(
            boolean blueprintsEnabled,
            boolean researchEnabled,
            boolean lootDistributionAvailable) {
        public static final RuntimeReadiness DEFAULT = new RuntimeReadiness(true, true, true);
    }
}
