package com.gamergaming.taczweaponblueprints.progression.gate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;

import net.minecraft.resources.ResourceLocation;

/** Immutable on-demand facts used to evaluate Progression Gates. */
public final class ProgressionGateEvidence {
    public static final ProgressionGateEvidence EMPTY = new ProgressionGateEvidence(
            List.of(),
            Set.of(),
            Optional.empty());

    private final List<ProgressionCriterionProgress> criteria;
    private final Map<ResourceLocation, Integer> criterionValues;
    private final Set<ResourceLocation> completedAdvancements;
    private final Optional<ResearchWorkbenchContext> workbenchContext;

    public ProgressionGateEvidence(
            Collection<ProgressionCriterionProgress> criteria,
            Collection<ResourceLocation> completedAdvancements,
            Optional<ResearchWorkbenchContext> workbenchContext) {
        if (criteria == null || completedAdvancements == null || workbenchContext == null) {
            throw new IllegalArgumentException("Progression Gate evidence cannot be null");
        }
        if (criteria.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || completedAdvancements.size()
                        > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("Progression Gate evidence exceeds collection bounds");
        }

        List<ProgressionCriterionProgress> orderedCriteria = new ArrayList<>(criteria);
        if (orderedCriteria.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Progression Gate criteria contain null");
        }
        orderedCriteria.sort(Comparator.comparing(value -> value.criterionId().toString()));
        LinkedHashMap<ResourceLocation, Integer> values = new LinkedHashMap<>();
        for (ProgressionCriterionProgress progress : orderedCriteria) {
            if (values.putIfAbsent(progress.criterionId(), progress.value()) != null) {
                throw new IllegalArgumentException("duplicate Progression Gate criterion evidence");
            }
        }

        List<ResourceLocation> orderedAdvancements = new ArrayList<>(completedAdvancements);
        if (orderedAdvancements.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Progression Gate advancements contain null");
        }
        orderedAdvancements.replaceAll(id -> ProgressionIds.require(id, "advancement ID"));
        orderedAdvancements.sort(Comparator.comparing(ResourceLocation::toString));
        LinkedHashSet<ResourceLocation> advancements = new LinkedHashSet<>(orderedAdvancements);
        if (advancements.size() != orderedAdvancements.size()) {
            throw new IllegalArgumentException("duplicate completed advancement evidence");
        }

        this.criteria = List.copyOf(orderedCriteria);
        this.criterionValues = Collections.unmodifiableMap(values);
        this.completedAdvancements = Collections.unmodifiableSet(advancements);
        this.workbenchContext = workbenchContext;
    }

    public List<ProgressionCriterionProgress> criteria() {
        return criteria;
    }

    public Set<ResourceLocation> completedAdvancements() {
        return completedAdvancements;
    }

    public Optional<ResearchWorkbenchContext> workbenchContext() {
        return workbenchContext;
    }

    public int criterionValue(ResourceLocation criterionId) {
        return criterionValues.getOrDefault(
                ProgressionIds.require(criterionId, "criterion ID"),
                0);
    }

    public boolean hasCompletedAdvancement(ResourceLocation advancementId) {
        return completedAdvancements.contains(
                ProgressionIds.require(advancementId, "advancement ID"));
    }
}
