package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot.Binding;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTarget.Specificity;

import net.minecraft.resources.ResourceLocation;

/** Deterministic group-exclusive resolution for one trusted normalized event. */
public final class ResearchPointAwardResolver {
    private static final Comparator<ResolvedAward> PRECEDENCE = Comparator
            .comparingInt((ResolvedAward value) -> value.specificity().rank()).reversed()
            .thenComparing(Comparator.comparingInt(
                    (ResolvedAward value) -> value.binding().definition().priority()).reversed())
            .thenComparing(value -> value.binding().definitionId().toString());

    private ResearchPointAwardResolver() {
    }

    public static Resolution resolve(
            ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardContext context) {
        if (snapshot == null || context == null) {
            return Resolution.invalidContext();
        }

        List<ResolvedAward> matches = new ArrayList<>();
        for (Binding binding : snapshot.candidatesFor(context)) {
            ResearchPointAwardDefinition definition = binding.definition();
            if (!definition.appliesToProfile(context.activeProfile())
                    || !definition.trigger().conditionsMatch(context)) {
                continue;
            }
            Specificity specificity = definition.trigger().targetSpecificity(context);
            if (specificity == Specificity.NONE) {
                continue;
            }
            matches.add(new ResolvedAward(binding, specificity));
        }

        return resolveMatched(matches);
    }

    static Resolution resolveMatched(List<ResolvedAward> matches) {
        if (matches == null || matches.stream().anyMatch(java.util.Objects::isNull)) {
            return Resolution.invalidContext();
        }
        Map<ResourceLocation, List<ResolvedAward>> groups = new LinkedHashMap<>();
        matches.forEach(match -> groups
                .computeIfAbsent(match.binding().definition().awardGroup(), ignored -> new ArrayList<>())
                .add(match));

        if (groups.size() > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_GROUPS_PER_EVENT) {
            return Resolution.tooManyGroups(groups.size());
        }

        List<ResolvedAward> winners = new ArrayList<>();
        List<Competition> competitions = new ArrayList<>();
        groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    List<ResolvedAward> candidates = entry.getValue().stream()
                            .sorted(PRECEDENCE)
                            .toList();
                    ResolvedAward winner = candidates.get(0);
                    winners.add(winner);
                    List<ResourceLocation> tiedIds = candidates.stream()
                            .filter(candidate -> candidate.specificity() == winner.specificity())
                            .filter(candidate -> candidate.binding().definition().priority()
                                    == winner.binding().definition().priority())
                            .map(candidate -> candidate.binding().definitionId())
                            .toList();
                    if (tiedIds.size() > 1) {
                        competitions.add(new Competition(
                                entry.getKey(), winner.binding().definitionId(), tiedIds));
                    }
                });
        return new Resolution(Status.RESOLVED, winners, competitions, groups.size());
    }

    public enum Status {
        RESOLVED,
        INVALID_CONTEXT,
        TOO_MANY_GROUPS
    }

    public record ResolvedAward(Binding binding, Specificity specificity) {
        public ResolvedAward {
            if (binding == null || specificity == null || specificity == Specificity.NONE) {
                throw new IllegalArgumentException("invalid resolved Research Point award");
            }
        }
    }

    public record Competition(
            ResourceLocation awardGroup,
            ResourceLocation selectedDefinitionId,
            List<ResourceLocation> tiedDefinitionIds) {
        public Competition {
            if (awardGroup == null || selectedDefinitionId == null
                    || tiedDefinitionIds == null || tiedDefinitionIds.size() < 2
                    || !tiedDefinitionIds.contains(selectedDefinitionId)) {
                throw new IllegalArgumentException("invalid Research Point award competition");
            }
            tiedDefinitionIds = List.copyOf(tiedDefinitionIds);
        }
    }

    public record Resolution(
            Status status,
            List<ResolvedAward> awards,
            List<Competition> competitions,
            int matchedGroupCount) {
        public Resolution {
            if (status == null || awards == null || competitions == null || matchedGroupCount < 0
                    || status != Status.RESOLVED && (!awards.isEmpty() || !competitions.isEmpty())) {
                throw new IllegalArgumentException("invalid Research Point award resolution");
            }
            awards = List.copyOf(awards);
            competitions = List.copyOf(competitions);
        }

        public boolean successful() {
            return status == Status.RESOLVED;
        }

        private static Resolution invalidContext() {
            return new Resolution(Status.INVALID_CONTEXT, List.of(), List.of(), 0);
        }

        private static Resolution tooManyGroups(int groups) {
            return new Resolution(Status.TOO_MANY_GROUPS, List.of(), List.of(), groups);
        }
    }
}
