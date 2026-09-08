package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import net.minecraft.resources.ResourceLocation;

/** Pure pool preparation and sampling. Callers draw only after server transaction preflight. */
public final class ResearchDataPoolResolver {
    private ResearchDataPoolResolver() { }
    public static final int MAX_CATALOG = 65_536, MAX_CACHED_CANDIDATES = 262_144;
    public enum Status { READY, DISABLED, LEGACY_CURRENCY, PROFILE_UNAVAILABLE, STATION_TIER_REQUIRED,
        STALE_PROGRESSION, EMPTY_POOL, ALL_LEARNED }
    public record Defaults(boolean allowLearned, boolean allowUndiscovered) {
        public static final Defaults STANDARD = new Defaults(true, true);
    }
    public record PlayerView(Set<ResourceLocation> learned, Set<ResourceLocation> discovered,
            Map<ResourceLocation, AnalysisFragmentCounts> archived) {
        public PlayerView { learned = Set.copyOf(learned); discovered = Set.copyOf(discovered); archived = Map.copyOf(archived); }
        public static PlayerView from(IPlayerRecipeData data) {
            var archive = new HashMap<ResourceLocation, AnalysisFragmentCounts>();
            data.getArchivedAnalysisFragments().forEach((id, count) -> { var key = ResourceLocation.tryParse(id); if (key != null) archive.put(key, count); });
            return new PlayerView(ids(data.getLearnedBlueprints()), ids(data.getDiscoveredBlueprints()), archive);
        }
        private static Set<ResourceLocation> ids(Set<String> values) {
            var result = new HashSet<ResourceLocation>();
            values.forEach(id -> { var key = ResourceLocation.tryParse(id); if (key != null) result.add(key); });
            return result;
        }
    }
    public record Candidate(ResourceLocation target, AnalyzerTier tier, long weight,
            AnalyzerOutcomeTable outcomes, Optional<AnalysisFragmentReconstructionService.Policy> reconstruction) { }
    public record TierPool(AnalyzerTier tier, int weight, List<Candidate> candidates, long targetWeight) {
        public TierPool { candidates = List.copyOf(candidates); }
    }
    public record Pool(Status status, ResourceLocation definition, ResearchDataPolicy policy,
            List<TierPool> tiers, Map<String, Integer> exclusions, double blueprintProbability,
            int minimumFragments, int maximumFragments) {
        public Pool { tiers = List.copyOf(tiers); exclusions = Map.copyOf(exclusions); }
        public boolean ready() { return status == Status.READY; }
        public Outcome select(LongUnaryOperator random) {
            if (!ready()) throw new IllegalStateException("Research Data pool is unavailable: " + status);
            long tierTotal = tiers.stream().mapToLong(TierPool::weight).sum();
            long draw = tiers.size() == 1 ? 0 : bounded(random, tierTotal);
            TierPool selected = null;
            for (var tier : tiers) { if (draw < tier.weight()) { selected = tier; break; } draw -= tier.weight(); }
            if (selected == null) throw new IllegalStateException("invalid tier weight total");
            draw = selected.candidates().size() == 1 ? 0 : bounded(random, selected.targetWeight());
            for (var candidate : selected.candidates()) {
                if (draw < candidate.weight()) {
                    var outcome = candidate.outcomes().select(random);
                    return new Outcome(candidate.target(), candidate.tier(), outcome.kind(), outcome.count(), policy.rewardClass());
                }
                draw -= candidate.weight();
            }
            throw new IllegalStateException("invalid target weight total");
        }
    }
    public record Outcome(ResourceLocation target, AnalyzerTier tier, AnalyzerOutcomeTable.Kind kind, int count, AnalysisRewardClass rewardClass) { }
    private static long bounded(LongUnaryOperator random, long bound) {
        long value = Objects.requireNonNull(random).applyAsLong(bound);
        if (value < 0 || value >= bound) throw new IllegalArgumentException("random source returned a value outside its bound");
        return value;
    }

    /** Own this context for one immutable research/catalog/progression publication, never per render frame. */
    public static final class Context {
        private final BlueprintAnalyzerPolicyResolver.Context source;
        private final LinkedHashMap<Key, StaticPool> cache = new LinkedHashMap<>(16, .75f, true);
        private int cachedCandidates;
        public Context(BlueprintAnalyzerPolicyResolver.Context source) {
            this.source = Objects.requireNonNull(source);
            if (source.catalog().size() > MAX_CATALOG) throw new IllegalArgumentException("Research Data catalog limit exceeded");
        }
        public BlueprintAnalyzerPolicyResolver.Context source() { return source; }
        public synchronized int cachedCandidateCount() { return cachedCandidates; }
        private synchronized StaticPool candidates(ResourceLocation definition, ResourceLocation profile, AnalyzerTier station) {
            var key = new Key(definition, profile, station); var found = cache.get(key);
            if (found != null) return found;
            var policy = source.research().researchData().policies().get(definition);
            var result = build(source, profile, station, policy);
            while (!cache.isEmpty() && (cache.size() >= 8 || cachedCandidates + result.candidates.size() > MAX_CACHED_CANDIDATES)) {
                var iterator = cache.entrySet().iterator(); cachedCandidates -= iterator.next().getValue().candidates.size(); iterator.remove();
            }
            cache.put(key, result); cachedCandidates += result.candidates.size(); return result;
        }
    }
    private record Key(ResourceLocation definition, ResourceLocation profile, AnalyzerTier station) { }
    private record StaticPool(List<Candidate> candidates, Map<String, Integer> exclusions) { }

    public static Pool evaluate(Context context, ResourceLocation definition, ResourceLocation profile, AnalyzerTier station,
            Defaults defaults, PlayerView player, Predicate<ResourceLocation> allowed) {
        Objects.requireNonNull(defaults); Objects.requireNonNull(player); Objects.requireNonNull(allowed);
        var source = context.source(); var policy = source.research().researchData().policies().get(definition);
        if (policy == null) throw new IllegalArgumentException("unknown Research Data definition " + definition);
        Status status = !policy.enabled() || policy.behavior() == ResearchDataPolicy.Behavior.DISABLED ? Status.DISABLED
                : policy.behavior() == ResearchDataPolicy.Behavior.LEGACY_CURRENCY ? Status.LEGACY_CURRENCY
                : !source.research().profiles().containsKey(profile) || policy.profile().filter(id -> !id.equals(profile)).isPresent() ? Status.PROFILE_UNAVAILABLE
                : source.stations().analyzer() != com.gamergaming.taczweaponblueprints.progression.StationSetup.AnalyzerMode.SINGLE && station.level() < policy.minimumAnalyzerTier().level() ? Status.STATION_TIER_REQUIRED
                : !source.progression().matches(source.catalogRevision(), source.researchRevision()) ? Status.STALE_PROGRESSION : Status.READY;
        if (status != Status.READY) return new Pool(status, definition, policy, List.of(), Map.of(), 0, 0, 0);
        var prepared = context.candidates(definition, profile, station);
        var exclusions = new TreeMap<>(prepared.exclusions());
        var groups = new EnumMap<AnalyzerTier, List<Candidate>>(AnalyzerTier.class);
        int beforeLearned = 0, learnedRemoved = 0;
        for (var candidate : prepared.candidates()) {
            var id = candidate.target();
            if (!allowed.test(id)) { count(exclusions, "blocked_or_exempt"); continue; }
            if (!policy.allowUndiscovered().orElse(defaults.allowUndiscovered()) && !player.discovered().contains(id) && !player.learned().contains(id)) {
                count(exclusions, "undiscovered"); continue;
            }
            beforeLearned++;
            if (!policy.allowLearned().orElse(defaults.allowLearned()) && player.learned().contains(id)) {
                learnedRemoved++; count(exclusions, "learned"); continue;
            }
            long weight = candidate.weight();
            if (policy.progressBonus() > 0 && candidate.reconstruction().isPresent()) {
                int threshold = candidate.reconstruction().orElseThrow().threshold();
                int partial = player.archived().getOrDefault(id, AnalysisFragmentCounts.EMPTY).get(policy.rewardClass());
                // A completed archived set is not partial progress. No inventory scan or class promotion.
                if (partial > 0 && partial < threshold)
                    weight = Math.addExact(weight, Math.multiplyExact(Math.multiplyExact(weight, policy.progressBonus()), partial) / threshold);
            }
            groups.computeIfAbsent(candidate.tier(), ignored -> new ArrayList<>()).add(new Candidate(id, candidate.tier(), weight, candidate.outcomes(), candidate.reconstruction()));
        }
        var tiers = new ArrayList<TierPool>();
        groups.forEach((tier, candidates) -> {
            long total = 0; for (var c : candidates) total = Math.addExact(total, c.weight());
            tiers.add(new TierPool(tier, policy.pool().tierWeights().get(tier), candidates, total));
        });
        status = tiers.isEmpty() ? beforeLearned > 0 && learnedRemoved == beforeLearned ? Status.ALL_LEARNED : Status.EMPTY_POOL : Status.READY;
        long total = tiers.stream().mapToLong(TierPool::weight).sum();
        double probability = 0; int min = 65, max = 0;
        for (var tier : tiers) for (var candidate : tier.candidates()) {
            probability += ((double) tier.weight() / total) * ((double) candidate.weight() / tier.targetWeight()) * candidate.outcomes().blueprintProbability();
            if (candidate.outcomes().fragmentWeight() > 0) {
                min = Math.min(min, candidate.outcomes().minimumFragments()); max = Math.max(max, candidate.outcomes().maximumFragments());
            }
        }
        return new Pool(status, definition, policy, tiers, exclusions, probability, max == 0 ? 0 : min, max);
    }
    private static StaticPool build(BlueprintAnalyzerPolicyResolver.Context context, ResourceLocation profile,
            AnalyzerTier station, ResearchDataPolicy policy) {
        var candidates = new ArrayList<Candidate>(); var exclusions = new TreeMap<String, Integer>();
        for (var entry : new TreeMap<>(context.catalog()).entrySet()) {
            var id = entry.getKey(); var data = entry.getValue();
            // Catalog keys are canonical item IDs. Recipe aliases cannot add a second weighted ticket.
            if (!id.toString().equals(data.getBpId()) || data.getKind() != BlueprintKind.GUN || BlueprintFragmentItem.parseTarget(id.toString()).isEmpty()) {
                count(exclusions, "noncanonical_or_nonweapon"); continue;
            }
            var pool = policy.pool();
            if (pool.include().filter(t -> t.match(id, data, context.research().tags()) == BlueprintResearchTarget.MatchSpecificity.NONE).isPresent()
                    || pool.exclude().filter(t -> t.match(id, data, context.research().tags()) != BlueprintResearchTarget.MatchSpecificity.NONE).isPresent()) {
                count(exclusions, "selector"); continue;
            }
            var tier = Optional.ofNullable(pool.tierOverrides().get(id)).or(() -> context.progression().policy(profile, id)
                    .map(p -> AnalyzerTier.fromLevel(p.researchWorkbenchTier().level())));
            if (tier.isEmpty()) { count(exclusions, "tier_unavailable"); continue; }
            int weight = pool.weaponWeights().getOrDefault(id, 1);
            if (weight == 0 || pool.tierWeights().getOrDefault(tier.orElseThrow(), 0) == 0) { count(exclusions, "zero_weight_or_tier"); continue; }
            var outcomes = policy.outcome(context.stations().dataRewardTier(station), tier.orElseThrow());
            var reconstruction = AnalysisReconstructionResolver.resolve(context, profile, id, station);
            if (outcomes.fragmentWeight() > 0 && !reconstruction.available()) { count(exclusions, "reconstruction_unavailable"); continue; }
            candidates.add(new Candidate(id, tier.orElseThrow(), weight, outcomes,
                    reconstruction.available() ? Optional.of(reconstruction.policy()) : Optional.empty()));
        }
        return new StaticPool(List.copyOf(candidates), Map.copyOf(exclusions));
    }
    private static void count(Map<String, Integer> counts, String reason) { counts.merge(reason, 1, Integer::sum); }
}
