package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;

import net.minecraft.resources.ResourceLocation;

/** Deterministic, UI-neutral diagnostics for research policy inspection. */
public final class BlueprintResearchDiagnostics {
    private BlueprintResearchDiagnostics() {
    }

    public static Summary summarize(BlueprintResearchSnapshot snapshot) {
        BlueprintResearchSnapshot stable = snapshot == null ? BlueprintResearchSnapshot.EMPTY : snapshot;
        int exact = 0;
        int tags = 0;
        int selectors = 0;
        for (BlueprintResearchRule rule : stable.rules().values()) {
            exact += rule.target().blueprints().size();
            tags += rule.target().tags().size();
            selectors += rule.target().selector().isPresent() ? 1 : 0;
        }
        return new Summary(
                stable.tags().size(),
                stable.profiles().size(),
                stable.rules().size(),
                stable.groups().size(),
                exact,
                tags,
                selectors,
                stable.groups().values().stream()
                        .mapToInt(ResearchTreeGroupDefinition::memberCount)
                        .sum());
    }

    public static BlueprintResearchPolicy inspect(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData) {
        return BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                profileId,
                blueprintId,
                playerData,
                ignored -> false);
    }

    public static BlueprintResearchPolicyResolver.RuleSelection inspectSelection(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        return BlueprintResearchPolicyResolver.ruleSelection(
                snapshot,
                profileId,
                blueprintId,
                stableCatalog.get(blueprintId));
    }

    /**
     * Audits the effective authored graph for one profile against the current
     * catalog. The report is deterministic and intentionally does not depend on
     * a player's learned state, so it is safe for reload logs and admin tools.
     */
    public static Audit audit(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        if (profileId == null) {
            return Audit.EMPTY;
        }

        Map<ResourceLocation, BlueprintData> sortedCatalog = new LinkedHashMap<>();
        if (catalog != null) {
            catalog.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> sortedCatalog.put(entry.getKey(), entry.getValue()));
        }

        Set<ResourceLocation> assigned = new LinkedHashSet<>();
        Set<ResourceLocation> treeVisible = new LinkedHashSet<>();
        Set<ResourceLocation> missingPrerequisites = new LinkedHashSet<>();
        Set<ResourceLocation> hiddenPrerequisiteTargets = new LinkedHashSet<>();
        List<Competition> competitions = new ArrayList<>();
        Map<ResourceLocation, BlueprintResearchPolicyDefinition> definitions = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, BlueprintData> entry : sortedCatalog.entrySet()) {
            ResourceLocation blueprintId = entry.getKey();
            BlueprintResearchPolicyResolver.RuleSelection selection =
                    BlueprintResearchPolicyResolver.ruleSelection(
                            stableSnapshot,
                            profileId,
                            blueprintId,
                            entry.getValue());
            if (selection.selectedRuleId().isPresent()) {
                assigned.add(blueprintId);
            }
            if (selection.hasTie()) {
                competitions.add(new Competition(
                        blueprintId,
                        selection.selectedRuleId(),
                        selection.tiedRuleIds()));
            }

            BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                    stableSnapshot,
                    sortedCatalog,
                    profileId,
                    blueprintId);
            definitions.put(blueprintId, definition);
            if (isTreeVisible(definition)) {
                treeVisible.add(blueprintId);
            }
        }

        Set<ResourceLocation> roots = new LinkedHashSet<>();
        Set<ResourceLocation> leaves = new LinkedHashSet<>(treeVisible);
        Map<ResourceLocation, Set<ResourceLocation>> adjacency = new LinkedHashMap<>();
        treeVisible.forEach(id -> adjacency.put(id, new LinkedHashSet<>()));

        for (Map.Entry<ResourceLocation, BlueprintData> entry : sortedCatalog.entrySet()) {
            ResourceLocation blueprintId = entry.getKey();
            BlueprintResearchPolicyDefinition definition = definitions.get(blueprintId);
            boolean visibleTarget = treeVisible.contains(blueprintId);
            if (visibleTarget && definition.prerequisites().isEmpty()) {
                roots.add(blueprintId);
            }
            for (ResourceLocation prerequisiteId : definition.prerequisites()) {
                if (!sortedCatalog.containsKey(prerequisiteId)) {
                    missingPrerequisites.add(prerequisiteId);
                    continue;
                }
                if (visibleTarget && !treeVisible.contains(prerequisiteId)) {
                    hiddenPrerequisiteTargets.add(blueprintId);
                    continue;
                }
                if (visibleTarget) {
                    adjacency.get(blueprintId).add(prerequisiteId);
                    adjacency.get(prerequisiteId).add(blueprintId);
                    leaves.remove(prerequisiteId);
                }
            }
        }

        List<ResourceLocation> independent = adjacency.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        return new Audit(
                sortedCatalog.size(),
                assigned.size(),
                treeVisible.size(),
                sortedDifference(sortedCatalog.keySet(), assigned),
                roots.size(),
                leaves.size(),
                componentCount(adjacency),
                independent,
                List.copyOf(missingPrerequisites),
                List.copyOf(hiddenPrerequisiteTargets),
                competitions);
    }

    /** Audits authored presentation coverage without treating supported fallback as an error. */
    public static GroupAudit auditGroups(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        if (profileId == null) {
            return GroupAudit.EMPTY;
        }
        Set<ResourceLocation> catalogIds = new LinkedHashSet<>();
        if (catalog != null) {
            catalog.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .forEach(catalogIds::add);
        }

        Set<ResourceLocation> authoredMembers = new LinkedHashSet<>();
        stableSnapshot.groupsForProfile(profileId).forEach(binding ->
                authoredMembers.addAll(binding.definition().members()));
        List<ResourceLocation> missingMembers = authoredMembers.stream()
                .filter(id -> !catalogIds.contains(id))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        List<ResourceLocation> fallback = catalogIds.stream()
                .filter(id -> stableSnapshot.placementFor(profileId, id).isEmpty())
                .toList();
        return new GroupAudit(
                catalogIds.size(),
                stableSnapshot.groupsForProfile(profileId).size(),
                authoredMembers.size(),
                catalogIds.size() - fallback.size(),
                fallback,
                missingMembers);
    }

    private static boolean isTreeVisible(BlueprintResearchPolicyDefinition definition) {
        return definition.journalEnabled()
                && definition.visibility().appearsInTree();
    }

    private static List<ResourceLocation> sortedDifference(
            Set<ResourceLocation> candidates,
            Set<ResourceLocation> assigned) {
        return candidates.stream()
                .filter(id -> !assigned.contains(id))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static int componentCount(Map<ResourceLocation, Set<ResourceLocation>> adjacency) {
        Set<ResourceLocation> visited = new LinkedHashSet<>();
        int components = 0;
        for (ResourceLocation start : adjacency.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            components++;
            Deque<ResourceLocation> remaining = new ArrayDeque<>();
            remaining.add(start);
            while (!remaining.isEmpty()) {
                ResourceLocation current = remaining.removeFirst();
                for (ResourceLocation neighbor : adjacency.getOrDefault(current, Set.of())) {
                    if (visited.add(neighbor)) {
                        remaining.addLast(neighbor);
                    }
                }
            }
        }
        return components;
    }

    public record Summary(
            int tagCount,
            int profileCount,
            int ruleCount,
            int groupCount,
            int exactTargetCount,
            int tagTargetCount,
            int selectorTargetCount,
            int groupMemberCount) {
    }

    public record GroupAudit(
            int catalogSize,
            int authoredGroupCount,
            int authoredMemberCount,
            int groupedCatalogCount,
            List<ResourceLocation> fallbackBlueprintIds,
            List<ResourceLocation> missingMemberIds) {
        private static final GroupAudit EMPTY = new GroupAudit(
                0, 0, 0, 0, List.of(), List.of());

        public GroupAudit {
            fallbackBlueprintIds = List.copyOf(fallbackBlueprintIds);
            missingMemberIds = List.copyOf(missingMemberIds);
        }

        public boolean hasProblems() {
            return !missingMemberIds.isEmpty();
        }
    }

    public record Audit(
            int catalogSize,
            int assignedBlueprintCount,
            int treeVisibleBlueprintCount,
            List<ResourceLocation> unassignedBlueprintIds,
            int rootCount,
            int leafCount,
            int componentCount,
            List<ResourceLocation> independentBlueprintIds,
            List<ResourceLocation> missingPrerequisiteIds,
            List<ResourceLocation> hiddenPrerequisiteTargetIds,
            List<Competition> competitions) {
        private static final Audit EMPTY = new Audit(
                0, 0, 0, List.of(), 0, 0, 0, List.of(), List.of(), List.of(), List.of());

        public Audit {
            unassignedBlueprintIds = List.copyOf(unassignedBlueprintIds);
            independentBlueprintIds = List.copyOf(independentBlueprintIds);
            missingPrerequisiteIds = List.copyOf(missingPrerequisiteIds);
            hiddenPrerequisiteTargetIds = List.copyOf(hiddenPrerequisiteTargetIds);
            competitions = List.copyOf(competitions);
        }

        public boolean emptyTree() {
            return treeVisibleBlueprintCount == 0;
        }

        public boolean hasStructuralProblems() {
            return (catalogSize > 0 && emptyTree())
                    || !missingPrerequisiteIds.isEmpty()
                    || !hiddenPrerequisiteTargetIds.isEmpty()
                    || !competitions.isEmpty();
        }
    }

    public record Competition(
            ResourceLocation blueprintId,
            java.util.Optional<ResourceLocation> selectedRuleId,
            List<ResourceLocation> tiedRuleIds) {
        public Competition {
            selectedRuleId = selectedRuleId == null ? java.util.Optional.empty() : selectedRuleId;
            tiedRuleIds = List.copyOf(tiedRuleIds);
        }
    }
}
