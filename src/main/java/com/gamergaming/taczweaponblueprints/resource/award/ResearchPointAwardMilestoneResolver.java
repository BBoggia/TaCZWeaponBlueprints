package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver.ResolvedAward;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot.Binding;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTarget.Specificity;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.MilestoneState;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type;

import net.minecraft.resources.ResourceLocation;

/** Resolves milestone thresholds against each definition's own filtered catalog count. */
public final class ResearchPointAwardMilestoneResolver {
    private ResearchPointAwardMilestoneResolver() {
    }

    public static Resolution resolve(
            ResearchPointAwardSnapshot snapshot,
            ResourceLocation profile,
            DispatchMode mode,
            MilestoneState state,
            Set<ResourceLocation> currentIds,
            Optional<ResourceLocation> changedId,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {
        if (snapshot == null || profile == null || mode == null || state == null
                || currentIds == null || changedId == null || facts == null
                || (mode == DispatchMode.LIVE && changedId.isEmpty())) {
            return Resolution.invalid();
        }
        if (mode == DispatchMode.RETROACTIVE && changedId.isEmpty()) {
            return resolveRetroactive(snapshot, profile, state, currentIds, facts);
        }
        return resolveTransition(
                snapshot, profile, mode, state, currentIds, changedId.orElseThrow(), facts);
    }

    private static Resolution resolveRetroactive(
            ResearchPointAwardSnapshot snapshot,
            ResourceLocation profile,
            MilestoneState state,
            Set<ResourceLocation> currentIds,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {
        RetroactivePlan plan = retroactivePlan(snapshot, profile, state, currentIds, facts);
        while (plan.step()) {
            // The synchronous compatibility entry point drains the bounded plan.
        }
        return plan.finish();
    }

    /**
     * Creates a deterministic retroactive milestone plan whose {@link #step()}
     * method evaluates at most one definition. Runtime reconciliation uses this
     * form so large datapacks cannot monopolize one server tick.
     */
    public static RetroactivePlan retroactivePlan(
            ResearchPointAwardSnapshot snapshot,
            ResourceLocation profile,
            MilestoneState state,
            Set<ResourceLocation> currentIds,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {
        return new RetroactivePlan(snapshot, profile, state, currentIds, facts);
    }

    private static Resolution resolveTransition(
            ResearchPointAwardSnapshot snapshot,
            ResourceLocation profile,
            DispatchMode mode,
            MilestoneState state,
            Set<ResourceLocation> currentIds,
            ResourceLocation changedId,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {

        Map<Optional<ResearchPointAwardTarget>, Integer> countsByTarget = new LinkedHashMap<>();
        List<ResolvedAward> matches = new ArrayList<>();
        Map<ResourceLocation, ResearchPointAwardContext> contexts = new LinkedHashMap<>();
        for (Binding binding : snapshot.bindingsByTrigger().getOrDefault(Type.BLUEPRINT_MILESTONE, List.of())) {
            ResearchPointAwardDefinition definition = binding.definition();
            if (!definition.appliesToProfile(profile)
                    || definition.trigger().milestone().orElseThrow().state() != state
                    || (mode == DispatchMode.RETROACTIVE && !definition.trigger().retroactive())) {
                continue;
            }

            ResearchPointAwardBlueprintFacts representative;
            int previousCount;
            representative = facts.get(changedId);
            if (representative == null
                    || specificity(definition, representative, profile, mode) == Specificity.NONE) {
                continue;
            }
            int matchingCount = countsByTarget.computeIfAbsent(
                    definition.trigger().target(),
                    ignored -> matchingCount(definition, currentIds, facts, profile, mode));
            previousCount = Math.max(0, matchingCount - 1);
            ResearchPointAwardContext context = representative.context(
                    Type.BLUEPRINT_MILESTONE,
                    profile,
                    mode,
                    Optional.of(state),
                    previousCount,
                    matchingCount);
            if (!definition.trigger().conditionsMatch(context)) {
                continue;
            }
            Specificity specificity = definition.trigger().targetSpecificity(context);
            if (specificity == Specificity.NONE) {
                continue;
            }
            matches.add(new ResolvedAward(binding, specificity));
            contexts.put(binding.definitionId(), context);
        }

        ResearchPointAwardResolver.Resolution selected =
                ResearchPointAwardResolver.resolveMatched(matches);
        if (!selected.successful()) {
            return new Resolution(selected, List.of());
        }
        List<ResolvedMilestoneAward> awards = selected.awards().stream()
                .map(award -> new ResolvedMilestoneAward(
                        award, contexts.get(award.binding().definitionId())))
                .toList();
        return new Resolution(selected, awards);
    }

    private static Specificity specificity(
            ResearchPointAwardDefinition definition,
            ResearchPointAwardBlueprintFacts facts,
            ResourceLocation profile,
            DispatchMode mode) {
        return definition.trigger().target()
                .filter(value -> !value.isGeneric())
                .map(value -> value.match(facts))
                .orElse(Specificity.GENERIC);
    }

    /** Revalidates a queued milestone against the player's current durable state. */
    public static boolean currentlySatisfied(
            ResearchPointAwardDefinition definition,
            ResourceLocation profile,
            DispatchMode mode,
            Set<ResourceLocation> currentIds,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {
        if (definition == null || profile == null || mode == null || currentIds == null || facts == null
                || definition.trigger().type() != Type.BLUEPRINT_MILESTONE
                || !definition.appliesToProfile(profile)
                || mode == DispatchMode.RETROACTIVE && !definition.trigger().retroactive()) {
            return false;
        }
        int threshold = definition.trigger().milestone().orElseThrow().threshold();
        return matchingCount(definition, currentIds, facts, profile, mode) >= threshold;
    }

    private static int matchingCount(
            ResearchPointAwardDefinition definition,
            Set<ResourceLocation> currentIds,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts,
            ResourceLocation profile,
            DispatchMode mode) {
        int count = 0;
        for (ResourceLocation id : currentIds) {
            ResearchPointAwardBlueprintFacts value = facts.get(id);
            if (value != null && specificity(definition, value, profile, mode) != Specificity.NONE) {
                count++;
            }
        }
        return count;
    }

    public record ResolvedMilestoneAward(
            ResolvedAward award,
            ResearchPointAwardContext context) {
        public ResolvedMilestoneAward {
            if (award == null || context == null) {
                throw new IllegalArgumentException("invalid resolved milestone award");
            }
        }
    }

    public record Resolution(
            ResearchPointAwardResolver.Resolution groupResolution,
            List<ResolvedMilestoneAward> awards) {
        public Resolution {
            if (groupResolution == null || awards == null) {
                throw new IllegalArgumentException("invalid milestone resolution");
            }
            awards = List.copyOf(awards);
        }

        public boolean successful() {
            return groupResolution.successful();
        }

        private static Resolution invalid() {
            return new Resolution(
                    ResearchPointAwardResolver.resolveMatched(null), List.of());
        }
    }

    public static final class RetroactivePlan {
        private final ResourceLocation profile;
        private final MilestoneState state;
        private final List<Binding> bindings;
        private final List<ResearchPointAwardBlueprintFacts> sortedCurrentFacts;
        private final Map<Optional<ResearchPointAwardTarget>, List<ResearchPointAwardBlueprintFacts>>
                matchesByTarget = new LinkedHashMap<>();
        private final Map<ResourceLocation, List<ResolvedAward>> matchesBySyntheticTransition =
                new LinkedHashMap<>();
        private final Map<ResourceLocation, Map<ResourceLocation, ResearchPointAwardContext>>
                contextsByTransition = new LinkedHashMap<>();
        private final boolean valid;
        private int index;
        private Resolution finished;

        private RetroactivePlan(
                ResearchPointAwardSnapshot snapshot,
                ResourceLocation profile,
                MilestoneState state,
                Set<ResourceLocation> currentIds,
                Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts) {
            this.valid = snapshot != null && profile != null && state != null
                    && currentIds != null && facts != null;
            this.profile = profile;
            this.state = state;
            this.bindings = valid
                    ? snapshot.bindingsByTrigger().getOrDefault(Type.BLUEPRINT_MILESTONE, List.of())
                    : List.of();
            this.sortedCurrentFacts = valid
                    ? currentIds.stream()
                            .map(facts::get)
                            .filter(java.util.Objects::nonNull)
                            .sorted(Comparator.comparing(value -> value.id().toString()))
                            .toList()
                    : List.of();
        }

        /** Returns true when one definition was consumed from the plan. */
        public boolean step() {
            if (!valid || finished != null || index >= bindings.size()) {
                return false;
            }
            Binding binding = bindings.get(index++);
            ResearchPointAwardDefinition definition = binding.definition();
            if (!definition.appliesToProfile(profile)
                    || !definition.trigger().retroactive()
                    || definition.trigger().milestone().orElseThrow().state() != state) {
                return true;
            }
            int threshold = definition.trigger().milestone().orElseThrow().threshold();
            List<ResearchPointAwardBlueprintFacts> matching = matchesByTarget.computeIfAbsent(
                    definition.trigger().target(),
                    ignored -> sortedCurrentFacts.stream()
                            .filter(value -> specificity(
                                    definition, value, profile, DispatchMode.RETROACTIVE)
                                    != Specificity.NONE)
                            .toList());
            if (matching.size() < threshold) {
                return true;
            }
            ResearchPointAwardBlueprintFacts representative = matching.get(threshold - 1);
            ResearchPointAwardContext context = representative.context(
                    Type.BLUEPRINT_MILESTONE,
                    profile,
                    DispatchMode.RETROACTIVE,
                    Optional.of(state),
                    threshold - 1,
                    threshold);
            Specificity specificity = definition.trigger().targetSpecificity(context);
            if (definition.trigger().conditionsMatch(context) && specificity != Specificity.NONE) {
                matchesBySyntheticTransition
                        .computeIfAbsent(representative.id(), ignored -> new ArrayList<>())
                        .add(new ResolvedAward(binding, specificity));
                contextsByTransition
                        .computeIfAbsent(representative.id(), ignored -> new LinkedHashMap<>())
                        .put(binding.definitionId(), context);
            }
            return true;
        }

        public boolean complete() {
            return !valid || index >= bindings.size();
        }

        public Resolution finish() {
            if (finished != null) {
                return finished;
            }
            if (!valid || !complete()) {
                return Resolution.invalid();
            }
            List<ResolvedMilestoneAward> selectedAwards = new ArrayList<>();
            for (ResourceLocation transition : matchesBySyntheticTransition.keySet().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
                ResearchPointAwardResolver.Resolution selected = ResearchPointAwardResolver.resolveMatched(
                        matchesBySyntheticTransition.get(transition));
                if (!selected.successful()) {
                    finished = new Resolution(selected, List.of());
                    return finished;
                }
                selected.awards().forEach(award -> selectedAwards.add(new ResolvedMilestoneAward(
                        award,
                        contextsByTransition.get(transition).get(award.binding().definitionId()))));
            }
            finished = new Resolution(
                    ResearchPointAwardResolver.resolveMatched(List.of()), selectedAwards);
            return finished;
        }
    }

}
