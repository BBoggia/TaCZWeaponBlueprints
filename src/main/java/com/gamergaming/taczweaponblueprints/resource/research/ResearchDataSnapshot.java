package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;

/** Validated as part of the research publication, so a malformed reload cannot split authorities. */
public record ResearchDataSnapshot(Map<ResourceLocation, ResearchDataPolicy> policies,
        Map<ResourceLocation, AnalysisReconstructionDefinition> reconstruction) {
    public static final int MAX_DEFINITIONS = 256, MAX_TOTAL_TERMS = 65_536;
    public static final ResearchDataSnapshot EMPTY = new ResearchDataSnapshot(Map.of(), Map.of());
    public ResearchDataSnapshot {
        policies = sorted(policies); reconstruction = sorted(reconstruction);
        if (policies.size() > MAX_DEFINITIONS || reconstruction.size() > MAX_DEFINITIONS)
            throw new IllegalArgumentException("Research Data definition limit exceeded");
        var scopes = new HashSet<String>();
        for (var entry : reconstruction.entrySet()) {
            var r = entry.getValue();
            if (!scopes.add(r.profile() + "/" + r.target())) throw new IllegalArgumentException("conflicting reconstruction scope at " + entry.getKey());
        }
        long terms = 0;
        for (var entry : policies.entrySet()) {
            var p = entry.getValue(); p.cost().validateForSnapshot();
            terms += p.pool().weaponWeights().size() + p.pool().tierOverrides().size();
            for (var target : List.of(p.pool().include(), p.pool().exclude())) if (target.isPresent())
                terms += target.orElseThrow().blueprints().size() + target.orElseThrow().tags().size()
                        + target.orElseThrow().selector().map(s -> s.namespaces().size() + s.itemTypes().size()
                                + s.pathPrefixes().size() + s.exclude().size() + s.blueprintKinds().size()).orElse(0);
            terms += p.cost().ingredients().stream().mapToLong(v -> Math.max(1, v.items().size())).sum();
            if (p.reconstruction().isPresent()) {
                var id = p.reconstruction().orElseThrow(); var r = reconstruction.get(id);
                if (r == null || !r.fallback() || !r.enabled() || !r.learning().learningPermitted())
                    throw new IllegalArgumentException("Research Data " + entry.getKey() + " reconstruction must reference the enabled shared fallback: " + id);
            }
        }
        if (terms > MAX_TOTAL_TERMS) throw new IllegalArgumentException("Research Data aggregate term limit exceeded");
    }
    private static <T> Map<ResourceLocation, T> sorted(Map<ResourceLocation, T> values) {
        var result = new TreeMap<ResourceLocation, T>(); result.putAll(values);
        result.forEach((id, value) -> {
            if (id.toString().length() > 256 || value == null) throw new IllegalArgumentException("invalid Research Data definition");
        });
        return Collections.unmodifiableMap(result);
    }
    public void validateAgainst(BlueprintResearchSnapshot research) {
        policies.forEach((id, policy) -> {
            policy.profile().ifPresent(profile -> requireProfile(research, profile, id));
            for (var selector : List.of(policy.pool().include(), policy.pool().exclude())) selector.ifPresent(target ->
                    target.tags().forEach(tag -> { if (!research.tags().containsKey(tag)) throw new IllegalArgumentException("Research Data " + id + " references unknown blueprint tag " + tag); }));
        });
        reconstruction.forEach((id, policy) -> policy.profile().ifPresent(profile -> requireProfile(research, profile, id)));
    }
    private static void requireProfile(BlueprintResearchSnapshot research, ResourceLocation profile, ResourceLocation owner) {
        if (!research.profiles().containsKey(profile)) throw new IllegalArgumentException(owner + " references unknown research profile " + profile);
    }
    /** Disabled owners still win selection: never fall through to a lower-priority rewarding input. */
    public Selection select(ResourceLocation item, ResourceLocation profile, BiPredicate<ResourceLocation, ResourceLocation> inTag) {
        var matches = policies.entrySet().stream().filter(e -> e.getValue().profile().map(profile::equals).orElse(true)
                && e.getValue().input().matches(item, inTag)).sorted(Comparator
                .<Map.Entry<ResourceLocation, ResearchDataPolicy>>comparingInt(e -> e.getValue().priority()).reversed()
                .thenComparing(Comparator.<Map.Entry<ResourceLocation, ResearchDataPolicy>>comparingInt(e -> e.getValue().input().specificity()).reversed())
                .thenComparing(Map.Entry::getKey)).toList();
        return matches.isEmpty() ? new Selection(Optional.empty(), List.of())
                : new Selection(Optional.of(matches.get(0).getKey()), matches.stream().skip(1).map(Map.Entry::getKey).toList());
    }
    public record Selection(Optional<ResourceLocation> selected, List<ResourceLocation> shadowed) {
        public Selection { Objects.requireNonNull(selected); shadowed = List.copyOf(shadowed); }
    }
}
