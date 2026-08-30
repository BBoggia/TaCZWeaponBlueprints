package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot.RuleBinding;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

import net.minecraft.resources.ResourceLocation;

public final class BlueprintResearchPolicyResolver {
    private static final int MAX_CACHE_STATES = 8;
    private static volatile List<CacheState> caches = List.of();

    private BlueprintResearchPolicyResolver() {
    }

    public static BlueprintResearchPolicy resolve(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate) {
        return resolve(
                snapshot,
                catalog,
                profileId,
                blueprintId,
                playerData,
                blockedPredicate,
                ignored -> false);
    }

    public static BlueprintResearchPolicy resolve(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate) {
        BlueprintResearchSnapshot stableSnapshot = snapshot == null ? BlueprintResearchSnapshot.EMPTY : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        Predicate<String> stableBlocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        Predicate<ResourceLocation> stableExempt = progressionExemptPredicate == null
                ? ignored -> false
                : progressionExemptPredicate;
        if (profileId == null || blueprintId == null) {
            throw new IllegalArgumentException("profile and blueprint IDs cannot be null");
        }

        CacheState state = cacheState(stableSnapshot, stableCatalog, profileId);
        BlueprintResearchPolicyDefinition rawDefinition = state.rawDefinitions().computeIfAbsent(
                blueprintId,
                id -> resolveRawDefinition(
                        stableSnapshot,
                        profileId,
                        id,
                        stableCatalog.get(id),
                        state.compiledProfile()));
        List<EntryPointResolution> runtimeEntryPoints = resolveEntryPoints(
                stableSnapshot,
                stableCatalog,
                profileId,
                state.compiledProfile(),
                id -> stableBlocked.test(id.toString()) || stableExempt.test(id));
        BlueprintResearchPolicyDefinition definition = rebaseEntryPoints(
                rawDefinition,
                blueprintId,
                runtimeEntryPoints);
        boolean playerDataAvailable = playerData != null;
        boolean learned = playerDataAvailable && playerData.hasBlueprint(blueprintId.toString());
        boolean discovered = playerDataAvailable && playerData.hasDiscoveredBlueprint(blueprintId.toString());
        int points = playerDataAvailable ? playerData.getResearchPoints() : 0;
        boolean prerequisitesSatisfied = playerDataAvailable
                && definition.prerequisites().stream()
                        .allMatch(id -> playerData.hasBlueprint(id.toString())
                                || stableExempt.test(id));

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
                playerDataAvailable,
                learned,
                discovered,
                points,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS,
                prerequisitesSatisfied,
                definition.journalEnabled(),
                definition.treeEnabled(),
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
        CacheState state = cacheState(snapshot, catalog, profileId);
        BlueprintResearchPolicyDefinition rawDefinition = state.rawDefinitions().computeIfAbsent(
                blueprintId,
                id -> resolveRawDefinition(
                        snapshot,
                        profileId,
                        id,
                        catalog.get(id),
                        state.compiledProfile()));
        return rebaseEntryPoints(rawDefinition, blueprintId, state.entryPoints());
    }

    /** Returns the same selector-resolved reverse policy used by server evaluation. */
    public static BlueprintReverseEngineeringPolicy reverseEngineeringPolicyFor(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation blueprintId) {
        if (profileId == null || blueprintId == null) {
            return BlueprintReverseEngineeringPolicy.DISABLED;
        }
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        return definitionFor(stableSnapshot, stableCatalog, profileId, blueprintId)
                .reverseEngineering();
    }

    static void clearCache() {
        caches = List.of();
    }

    static int cacheStateCount() {
        return caches.size();
    }

    public static RuleSelection ruleSelection(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData) {
        if (snapshot == null || profileId == null || blueprintId == null) {
            return RuleSelection.NONE;
        }
        return CompiledProfile.compile(snapshot, profileId).select(blueprintId, blueprintData);
    }

    private static CacheState cacheState(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        CacheState found = findCacheState(snapshot, catalog, profileId);
        if (found != null) {
            return found;
        }
        synchronized (BlueprintResearchPolicyResolver.class) {
            found = findCacheState(snapshot, catalog, profileId);
            if (found != null) {
                return found;
            }
            CompiledProfile compiledProfile = CompiledProfile.compile(snapshot, profileId);
            CacheState created = new CacheState(
                    snapshot,
                    catalog,
                    profileId,
                    compiledProfile,
                    resolveEntryPoints(
                            snapshot,
                            catalog,
                            profileId,
                            compiledProfile,
                            ignored -> false),
                    new ConcurrentHashMap<>());
            List<CacheState> updated = new ArrayList<>(Math.min(MAX_CACHE_STATES, caches.size() + 1));
            updated.add(created);
            for (CacheState existing : caches) {
                if (updated.size() >= MAX_CACHE_STATES) {
                    break;
                }
                updated.add(existing);
            }
            caches = List.copyOf(updated);
            return created;
        }
    }

    private static CacheState findCacheState(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        for (CacheState state : caches) {
            if (state.snapshot() == snapshot
                    && state.catalog() == catalog
                    && profileId.equals(state.profileId())) {
                return state;
            }
        }
        return null;
    }

    private static BlueprintResearchPolicyDefinition resolveRawDefinition(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            BlueprintData blueprintData,
            CompiledProfile compiledProfile) {
        BlueprintResearchProfile profile = snapshot.profiles().get(profileId);
        if (profile == null) {
            return disabledDefinition();
        }
        BlueprintResearchPolicyDefinition base = BlueprintResearchPolicyDefinition.fromProfile(profile);
        RuleSelection selection = compiledProfile.select(blueprintId, blueprintData);
        BlueprintResearchPolicyDefinition resolved = selection.selectedRuleId().isEmpty()
                ? base
                : base.apply(
                        selection.selectedRuleId().orElseThrow(),
                        snapshot.rules().get(selection.selectedRuleId().orElseThrow()),
                        selection.specificity());
        return blueprintData == null
                ? resolved
                : resolved.applyDomainPolicy(profile.domainPolicy(Domain.forKind(blueprintData.getKind())));
    }

    private static BlueprintResearchPolicyDefinition disabledDefinition() {
        return new BlueprintResearchPolicyDefinition(
                false,
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
                false,
                BlueprintReverseEngineeringPolicy.DISABLED);
    }

    public static EntryPointResolution entryPointResolution(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        return entryPointResolution(snapshot, catalog, profileId, ignored -> false);
    }

    public static EntryPointResolution entryPointResolution(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            Predicate<String> blockedPredicate) {
        Predicate<String> stableBlocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        return resolveLegacyEntryPoint(
                snapshot == null ? BlueprintResearchSnapshot.EMPTY : snapshot,
                catalog == null ? Map.of() : catalog,
                profileId,
                CompiledProfile.compile(
                        snapshot == null ? BlueprintResearchSnapshot.EMPTY : snapshot,
                        profileId),
                id -> stableBlocked.test(id.toString()));
    }

    /** Resolves the legacy weapon entry plus every configured Tech Tree domain entry. */
    public static List<EntryPointResolution> entryPointResolutions(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        return entryPointResolutions(snapshot, catalog, profileId, ignored -> false);
    }

    /** Resolves the legacy weapon entry plus every configured Tech Tree domain entry. */
    public static List<EntryPointResolution> entryPointResolutions(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            Predicate<String> blockedPredicate) {
        return entryPointResolutions(
                snapshot,
                catalog,
                profileId,
                blockedPredicate,
                ignored -> false);
    }

    /** Resolves entry points after both hard blocks and live exemptions. */
    public static List<EntryPointResolution> entryPointResolutions(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate) {
        Predicate<String> stableBlocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        Predicate<ResourceLocation> stableExempt = progressionExemptPredicate == null
                ? ignored -> false
                : progressionExemptPredicate;
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        return resolveEntryPoints(
                stableSnapshot,
                stableCatalog,
                profileId,
                CompiledProfile.compile(stableSnapshot, profileId),
                id -> stableBlocked.test(id.toString()) || stableExempt.test(id));
    }

    private static List<EntryPointResolution> resolveEntryPoints(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            CompiledProfile compiledProfile,
            Predicate<ResourceLocation> blockedPredicate) {
        BlueprintResearchProfile profile = profileId == null ? null : snapshot.profiles().get(profileId);
        if (profile == null) {
            return List.of();
        }
        List<EntryPointResolution> resolutions = new ArrayList<>();
        if (!profile.entryPointCandidates().isEmpty()) {
            resolutions.add(resolveEntryPointGroup(
                    snapshot,
                    catalog,
                    profileId,
                    compiledProfile,
                    blockedPredicate,
                    profile.entryPointCandidates()));
        }
        for (ResearchTechTreeContract.Domain domain : ResearchTechTreeContract.DOMAIN_ORDER) {
            List<ResourceLocation> candidates = profile.techEntryPointCandidates()
                    .getOrDefault(domain, List.of());
            if (!candidates.isEmpty()) {
                resolutions.add(resolveEntryPointGroup(
                        snapshot,
                        catalog,
                        profileId,
                        compiledProfile,
                        blockedPredicate,
                        candidates));
            }
        }
        return List.copyOf(resolutions);
    }

    private static EntryPointResolution resolveLegacyEntryPoint(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            CompiledProfile compiledProfile,
            Predicate<ResourceLocation> blockedPredicate) {
        BlueprintResearchProfile profile = profileId == null ? null : snapshot.profiles().get(profileId);
        if (profile == null || profile.entryPointCandidates().isEmpty()) {
            return EntryPointResolution.NONE;
        }
        return resolveEntryPointGroup(
                snapshot,
                catalog,
                profileId,
                compiledProfile,
                blockedPredicate,
                profile.entryPointCandidates());
    }

    private static EntryPointResolution resolveEntryPointGroup(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            CompiledProfile compiledProfile,
            Predicate<ResourceLocation> blockedPredicate,
            List<ResourceLocation> candidates) {
        ResourceLocation preferred = candidates.get(0);
        Optional<ResourceLocation> resolved = candidates.stream()
                .filter(id -> entryPointUsable(
                        snapshot,
                        catalog,
                        profileId,
                        id,
                        compiledProfile,
                        blockedPredicate))
                .findFirst();
        return new EntryPointResolution(Optional.of(preferred), resolved);
    }

    private static boolean entryPointUsable(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            ResourceLocation candidate,
            CompiledProfile compiledProfile,
            Predicate<ResourceLocation> blockedPredicate) {
        BlueprintData data = catalog.get(candidate);
        if (data == null || blockedPredicate.test(candidate)) {
            return false;
        }
        BlueprintResearchPolicyDefinition definition = resolveRawDefinition(
                snapshot,
                profileId,
                candidate,
                data,
                compiledProfile);
        return definition.journalEnabled()
                && definition.treeEnabled()
                && definition.researchEnabled()
                && !definition.requiresDiscovery()
                && definition.visibility().allowsServerSelection();
    }

    private static BlueprintResearchPolicyDefinition rebaseEntryPoint(
            BlueprintResearchPolicyDefinition definition,
            ResourceLocation blueprintId,
            EntryPointResolution entryPoint) {
        if (!entryPoint.usesFallback()) {
            return definition;
        }
        ResourceLocation preferred = entryPoint.preferred().orElseThrow();
        ResourceLocation resolved = entryPoint.resolved().orElseThrow();
        if (blueprintId.equals(resolved)) {
            return definition.withPrerequisites(List.of());
        }
        List<ResourceLocation> rebased = definition.prerequisites().stream()
                .map(id -> id.equals(preferred) ? resolved : id)
                .filter(id -> !id.equals(blueprintId))
                .distinct()
                .toList();
        return rebased.equals(definition.prerequisites())
                ? definition
                : definition.withPrerequisites(rebased);
    }

    private static BlueprintResearchPolicyDefinition rebaseEntryPoints(
            BlueprintResearchPolicyDefinition definition,
            ResourceLocation blueprintId,
            List<EntryPointResolution> entryPoints) {
        BlueprintResearchPolicyDefinition rebased = definition;
        for (EntryPointResolution entryPoint : entryPoints) {
            rebased = rebaseEntryPoint(rebased, blueprintId, entryPoint);
        }
        return rebased;
    }

    private static final Comparator<RuleBinding> RULE_ORDER = Comparator
            .comparingInt((RuleBinding value) -> value.rule().priority()).reversed()
            .thenComparing(value -> value.ruleId().toString());

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
            CompiledProfile compiledProfile,
            List<EntryPointResolution> entryPoints,
            ConcurrentMap<ResourceLocation, BlueprintResearchPolicyDefinition> rawDefinitions) {
    }

    public record EntryPointResolution(
            Optional<ResourceLocation> preferred,
            Optional<ResourceLocation> resolved) {
        private static final EntryPointResolution NONE = new EntryPointResolution(Optional.empty(), Optional.empty());

        public EntryPointResolution {
            preferred = preferred == null ? Optional.empty() : preferred;
            resolved = resolved == null ? Optional.empty() : resolved;
        }

        public boolean usesFallback() {
            return preferred.isPresent() && resolved.isPresent() && !preferred.equals(resolved);
        }

        public boolean unavailable() {
            return preferred.isPresent() && resolved.isEmpty();
        }
    }

    private record CompiledProfile(
            Map<ResourceLocation, List<RuleBinding>> exactRules,
            Map<ResourceLocation, List<RuleBinding>> tagRules,
            List<RuleBinding> selectorRules) {
        private static CompiledProfile compile(
                BlueprintResearchSnapshot snapshot,
                ResourceLocation profileId) {
            Map<ResourceLocation, LinkedHashSet<RuleBinding>> exact = new LinkedHashMap<>();
            Map<ResourceLocation, LinkedHashSet<RuleBinding>> tags = new LinkedHashMap<>();
            List<RuleBinding> selectors = new ArrayList<>();
            for (RuleBinding binding : snapshot.rulesForProfile(profileId)) {
                BlueprintResearchTarget target = binding.rule().target();
                target.blueprints().forEach(id ->
                        exact.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(binding));
                for (ResourceLocation tagId : target.tags()) {
                    BlueprintLootTag tag = snapshot.tags().get(tagId);
                    if (tag != null) {
                        tag.values().forEach(id ->
                                tags.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(binding));
                    }
                }
                if (target.selector().isPresent()) {
                    selectors.add(binding);
                }
            }
            selectors.sort(RULE_ORDER);
            return new CompiledProfile(
                    immutableBindingMap(exact),
                    immutableBindingMap(tags),
                    List.copyOf(selectors));
        }

        private RuleSelection select(ResourceLocation blueprintId, BlueprintData blueprintData) {
            List<RuleBinding> exact = exactRules.getOrDefault(blueprintId, List.of());
            if (!exact.isEmpty()) {
                return selection(exact, MatchSpecificity.EXACT);
            }
            List<RuleBinding> tags = tagRules.getOrDefault(blueprintId, List.of());
            if (!tags.isEmpty()) {
                return selection(tags, MatchSpecificity.TAG);
            }
            if (blueprintData == null) {
                return RuleSelection.NONE;
            }
            List<RuleBinding> matchingSelectors = selectorRules.stream()
                    .filter(binding -> binding.rule().target().selector()
                            .filter(selector -> selector.matches(blueprintId, blueprintData))
                            .isPresent())
                    .toList();
            return matchingSelectors.isEmpty()
                    ? RuleSelection.NONE
                    : selection(matchingSelectors, MatchSpecificity.SELECTOR);
        }

        private static RuleSelection selection(
                List<RuleBinding> orderedBindings,
                MatchSpecificity specificity) {
            RuleBinding selected = orderedBindings.get(0);
            List<ResourceLocation> ties = orderedBindings.stream()
                    .filter(binding -> binding.rule().priority() == selected.rule().priority())
                    .map(RuleBinding::ruleId)
                    .toList();
            return new RuleSelection(
                    Optional.of(selected.ruleId()),
                    specificity,
                    selected.rule().priority(),
                    ties.size() > 1 ? ties : List.of());
        }

        private static Map<ResourceLocation, List<RuleBinding>> immutableBindingMap(
                Map<ResourceLocation, LinkedHashSet<RuleBinding>> bindings) {
            Map<ResourceLocation, List<RuleBinding>> immutable = new LinkedHashMap<>();
            bindings.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        List<RuleBinding> sorted = new ArrayList<>(entry.getValue());
                        sorted.sort(RULE_ORDER);
                        immutable.put(entry.getKey(), List.copyOf(sorted));
                    });
            return Collections.unmodifiableMap(immutable);
        }
    }
}
