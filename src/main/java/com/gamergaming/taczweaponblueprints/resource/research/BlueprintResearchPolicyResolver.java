package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot.RuleBinding;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

public final class BlueprintResearchPolicyResolver {
    private static volatile CacheState cache = CacheState.EMPTY;

    private BlueprintResearchPolicyResolver() {
    }

    public static BlueprintResearchPolicy resolve(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate) {
        BlueprintResearchSnapshot stableSnapshot = snapshot == null ? BlueprintResearchSnapshot.EMPTY : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        Predicate<String> stableBlocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        if (profileId == null || blueprintId == null) {
            throw new IllegalArgumentException("profile and blueprint IDs cannot be null");
        }

        BlueprintResearchPolicyDefinition definition = definitionFor(
                stableSnapshot,
                stableCatalog,
                profileId,
                blueprintId);
        boolean learned = playerData != null && playerData.hasBlueprint(blueprintId.toString());
        boolean discovered = playerData != null && playerData.hasDiscoveredBlueprint(blueprintId.toString());
        int points = playerData == null ? 0 : playerData.getResearchPoints();
        boolean prerequisitesSatisfied = playerData != null
                && definition.prerequisites().stream()
                        .allMatch(id -> playerData.hasBlueprint(id.toString()));

        JournalVisibility visibility = definition.visibility();
        if (learned) {
            visibility = JournalVisibility.FULL;
        } else if (discovered && !definition.visibilityRestricted()) {
            visibility = visibility.atLeast(JournalVisibility.PREVIEW);
        }

        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                stableCatalog.containsKey(blueprintId),
                stableBlocked.test(blueprintId.toString()),
                learned,
                discovered,
                points,
                prerequisitesSatisfied,
                definition.journalEnabled(),
                visibility,
                definition.researchEnabled(),
                definition.recyclingEnabled(),
                definition.allowUnlearnedRecycling(),
                definition.recyclingValue(),
                definition.researchCost(),
                definition.requiresDiscovery(),
                definition.prerequisites(),
                definition.creativeBypassesCost(),
                definition.ruleId(),
                definition.specificity());
    }

    static BlueprintResearchPolicyDefinition definitionFor(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        CacheState current = cache;
        if (current.snapshot() != snapshot
                || current.catalog() != catalog
                || !profileId.equals(current.profileId())) {
            synchronized (BlueprintResearchPolicyResolver.class) {
                current = cache;
                if (current.snapshot() != snapshot
                        || current.catalog() != catalog
                        || !profileId.equals(current.profileId())) {
                    current = rebuild(snapshot, catalog, profileId);
                    cache = current;
                }
            }
        }
        BlueprintResearchPolicyDefinition cached = current.definitions().get(blueprintId);
        return cached != null
                ? cached
                : resolveDefinition(snapshot, profileId, blueprintId, null);
    }

    static void clearCache() {
        cache = CacheState.EMPTY;
    }

    public static RuleSelection ruleSelection(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData) {
        if (snapshot == null || profileId == null || blueprintId == null) {
            return RuleSelection.NONE;
        }
        List<Candidate> matches = snapshot.rulesForProfile(profileId).stream()
                .map(binding -> new Candidate(
                        binding,
                        binding.rule().target().match(blueprintId, blueprintData, snapshot.tags())))
                .filter(candidate -> candidate.specificity() != MatchSpecificity.NONE)
                .sorted(CANDIDATE_ORDER)
                .toList();
        if (matches.isEmpty()) {
            return RuleSelection.NONE;
        }
        Candidate selected = matches.get(0);
        List<ResourceLocation> ties = matches.stream()
                .filter(candidate -> candidate.specificity() == selected.specificity()
                        && candidate.binding().rule().priority() == selected.binding().rule().priority())
                .map(candidate -> candidate.binding().ruleId())
                .toList();
        return new RuleSelection(
                Optional.of(selected.binding().ruleId()),
                selected.specificity(),
                selected.binding().rule().priority(),
                ties.size() > 1 ? ties : List.of());
    }

    private static CacheState rebuild(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        Map<ResourceLocation, BlueprintResearchPolicyDefinition> definitions = new LinkedHashMap<>();
        catalog.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> definitions.put(
                        entry.getKey(),
                        resolveDefinition(snapshot, profileId, entry.getKey(), entry.getValue())));
        return new CacheState(snapshot, catalog, profileId, Map.copyOf(definitions));
    }

    private static BlueprintResearchPolicyDefinition resolveDefinition(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData) {
        BlueprintResearchProfile profile = snapshot.profiles().get(profileId);
        if (profile == null) {
            return disabledDefinition();
        }
        BlueprintResearchPolicyDefinition base = BlueprintResearchPolicyDefinition.fromProfile(profile);
        RuleSelection selection = ruleSelection(snapshot, profileId, blueprintId, blueprintData);
        if (selection.selectedRuleId().isEmpty()) {
            return base;
        }
        ResourceLocation ruleId = selection.selectedRuleId().orElseThrow();
        return base.apply(ruleId, snapshot.rules().get(ruleId), selection.specificity());
    }

    private static BlueprintResearchPolicyDefinition disabledDefinition() {
        return new BlueprintResearchPolicyDefinition(
                false,
                JournalVisibility.HIDDEN,
                false,
                false,
                false,
                0,
                new BlueprintResearchCost(1, List.of()),
                false,
                List.of(),
                false,
                Optional.empty(),
                MatchSpecificity.NONE,
                false);
    }

    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparingInt((Candidate value) -> value.specificity().rank()).reversed()
            .thenComparing(Comparator.comparingInt(
                    (Candidate value) -> value.binding().rule().priority()).reversed())
            .thenComparing(value -> value.binding().ruleId().toString());

    private record Candidate(RuleBinding binding, MatchSpecificity specificity) {
    }

    public record RuleSelection(
            Optional<ResourceLocation> selectedRuleId,
            MatchSpecificity specificity,
            int priority,
            List<ResourceLocation> tiedRuleIds) {
        private static final RuleSelection NONE = new RuleSelection(
                Optional.empty(), MatchSpecificity.NONE, 0, List.of());

        public RuleSelection {
            selectedRuleId = selectedRuleId == null ? Optional.empty() : selectedRuleId;
            tiedRuleIds = tiedRuleIds == null ? List.of() : List.copyOf(tiedRuleIds);
        }

        public boolean hasTie() {
            return tiedRuleIds.size() > 1;
        }
    }

    private record CacheState(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            Map<ResourceLocation, BlueprintResearchPolicyDefinition> definitions) {
        private static final CacheState EMPTY = new CacheState(null, null, null, Map.of());
    }
}
