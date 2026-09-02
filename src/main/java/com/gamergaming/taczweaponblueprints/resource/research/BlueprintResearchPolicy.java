package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchPolicy(
        ResourceLocation blueprintId,
        ResourceLocation profileId,
        boolean available,
        boolean blocked,
        boolean playerDataAvailable,
        boolean learned,
        boolean discovered,
        int researchPoints,
        int pointCap,
        boolean prerequisitesSatisfied,
        boolean journalEnabled,
        boolean treeEnabled,
        JournalVisibility visibility,
        boolean researchEnabled,
        boolean recyclingEnabled,
        boolean allowUnlearnedRecycling,
        int recyclingValue,
        BlueprintResearchCost researchCost,
        boolean requiresDiscovery,
        ResearchRequirements requirements,
        List<ResourceLocation> prerequisites,
        boolean automaticPrerequisitesAllowed,
        boolean creativeBypassesCost,
        Optional<ResourceLocation> ruleId,
        BlueprintResearchTarget.MatchSpecificity specificity) {

    /**
     * Compatibility constructor for resolved policies created before automatic
     * prerequisite ownership was represented explicitly.
     */
    public BlueprintResearchPolicy(
            ResourceLocation blueprintId,
            ResourceLocation profileId,
            boolean available,
            boolean blocked,
            boolean playerDataAvailable,
            boolean learned,
            boolean discovered,
            int researchPoints,
            int pointCap,
            boolean prerequisitesSatisfied,
            boolean journalEnabled,
            boolean treeEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            ResearchRequirements requirements,
            List<ResourceLocation> prerequisites,
            boolean creativeBypassesCost,
            Optional<ResourceLocation> ruleId,
            BlueprintResearchTarget.MatchSpecificity specificity) {
        this(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                prerequisitesSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                requirements,
                prerequisites,
                true,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    /** Canonical constructor using deterministic order for the flat compatibility view. */
    public BlueprintResearchPolicy(
            ResourceLocation blueprintId,
            ResourceLocation profileId,
            boolean available,
            boolean blocked,
            boolean playerDataAvailable,
            boolean learned,
            boolean discovered,
            int researchPoints,
            int pointCap,
            boolean prerequisitesSatisfied,
            boolean journalEnabled,
            boolean treeEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            ResearchRequirements requirements,
            boolean creativeBypassesCost,
            Optional<ResourceLocation> ruleId,
            BlueprintResearchTarget.MatchSpecificity specificity) {
        this(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                prerequisitesSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                requirements,
                requirements == null
                        ? null
                        : requirements.conservativeAlternatives(),
                true,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    /** Compatibility constructor for resolved policies with mandatory flat parents. */
    public BlueprintResearchPolicy(
            ResourceLocation blueprintId,
            ResourceLocation profileId,
            boolean available,
            boolean blocked,
            boolean playerDataAvailable,
            boolean learned,
            boolean discovered,
            int researchPoints,
            int pointCap,
            boolean prerequisitesSatisfied,
            boolean journalEnabled,
            boolean treeEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            List<ResourceLocation> prerequisites,
            boolean creativeBypassesCost,
            Optional<ResourceLocation> ruleId,
            BlueprintResearchTarget.MatchSpecificity specificity) {
        this(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                prerequisitesSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                ResearchRequirements.fromLegacy(
                        prerequisites == null ? List.of() : prerequisites),
                prerequisites == null ? List.of() : prerequisites,
                true,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    /** Backwards-compatible constructor for callers predating tree inclusion. */
    public BlueprintResearchPolicy(
            ResourceLocation blueprintId,
            ResourceLocation profileId,
            boolean available,
            boolean blocked,
            boolean playerDataAvailable,
            boolean learned,
            boolean discovered,
            int researchPoints,
            int pointCap,
            boolean prerequisitesSatisfied,
            boolean journalEnabled,
            JournalVisibility visibility,
            boolean researchEnabled,
            boolean recyclingEnabled,
            boolean allowUnlearnedRecycling,
            int recyclingValue,
            BlueprintResearchCost researchCost,
            boolean requiresDiscovery,
            List<ResourceLocation> prerequisites,
            boolean creativeBypassesCost,
            Optional<ResourceLocation> ruleId,
            BlueprintResearchTarget.MatchSpecificity specificity) {
        this(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                prerequisitesSatisfied,
                journalEnabled,
                true,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                ResearchRequirements.fromLegacy(
                        prerequisites == null ? List.of() : prerequisites),
                prerequisites == null ? List.of() : prerequisites,
                true,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    public BlueprintResearchPolicy {
        if (blueprintId == null || profileId == null || visibility == null
                || researchCost == null || requirements == null
                || prerequisites == null || specificity == null) {
            throw new IllegalArgumentException("resolved research policy contains null required state");
        }
        if (researchPoints < 0
                || researchPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointCap < 0
                || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("resolved research policy contains an invalid point balance or cap");
        }
        ruleId = ruleId == null ? Optional.empty() : ruleId;
        prerequisites = validatePrerequisiteOrder(requirements, prerequisites);
    }

    public boolean researchable() {
        return playerDataAvailable
                && available
                && !blocked
                && researchEnabled
                && !learned
                && researchCost.points() <= pointCap
                && (!requiresDiscovery || discovered)
                && prerequisitesSatisfied;
    }

    public boolean canAffordPoints() {
        return playerDataAvailable
                && researchCost.points() <= pointCap
                && researchPoints >= researchCost.points();
    }

    public boolean recyclable() {
        return playerDataAvailable
                && available
                && !blocked
                && recyclingEnabled
                && recyclingValue > 0
                && recyclingValue <= pointCap - Math.min(researchPoints, pointCap)
                && (learned || allowUnlearnedRecycling);
    }

    public BlueprintResearchPolicy withRuntimePolicy(
            boolean effectiveJournalEnabled,
            JournalVisibility effectiveVisibility,
            boolean effectiveResearchEnabled,
            boolean effectiveRecyclingEnabled,
            boolean effectiveAllowUnlearnedRecycling,
            boolean effectiveCreativeBypassesCost,
            int effectivePointCap) {
        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                effectivePointCap,
                prerequisitesSatisfied,
                effectiveJournalEnabled,
                treeEnabled,
                effectiveVisibility,
                effectiveResearchEnabled,
                effectiveRecyclingEnabled,
                effectiveAllowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                requirements,
                prerequisites,
                automaticPrerequisitesAllowed,
                effectiveCreativeBypassesCost,
                ruleId,
                specificity);
    }

    /** Returns this resolved policy with a server-selected effective cost. */
    public BlueprintResearchPolicy withResearchCost(BlueprintResearchCost effectiveCost) {
        if (effectiveCost == null) {
            throw new IllegalArgumentException("effective research cost cannot be null");
        }
        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                prerequisitesSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                effectiveCost,
                requiresDiscovery,
                requirements,
                prerequisites,
                automaticPrerequisitesAllowed,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    /** Replaces derived prerequisite satisfaction without changing graph identity or order. */
    public BlueprintResearchPolicy withPrerequisitesSatisfied(
            boolean effectivePrerequisitesSatisfied) {
        if (prerequisitesSatisfied == effectivePrerequisitesSatisfied) {
            return this;
        }
        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                effectivePrerequisitesSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                requirements,
                prerequisites,
                automaticPrerequisitesAllowed,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    /** Replaces prerequisite authority while preserving every non-topology field. */
    public BlueprintResearchPolicy withRequirements(
            ResearchRequirements effectiveRequirements,
            boolean effectivePrerequisitesSatisfied) {
        if (effectiveRequirements == null) {
            throw new IllegalArgumentException("effective research requirements cannot be null");
        }
        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                effectivePrerequisitesSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                effectiveRequirements,
                effectiveRequirements.conservativeAlternatives(),
                automaticPrerequisitesAllowed,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    /**
     * Adds one lower-precedence runtime prerequisite while retaining every
     * authored policy field. Callers must suppress this overlay whenever the
     * authored definition already owns prerequisite behavior.
     */
    public BlueprintResearchPolicy withAdditionalPrerequisite(
            ResourceLocation prerequisite,
            boolean prerequisiteSatisfied) {
        if (prerequisite == null || prerequisite.equals(blueprintId)) {
            throw new IllegalArgumentException(
                    "additional research prerequisite is invalid");
        }
        if (requirements.conservativeAlternatives().contains(prerequisite)) {
            return this;
        }
        List<ResearchPrerequisiteGroup> updated = new java.util.ArrayList<>(
                requirements.allOf());
        updated.add(ResearchPrerequisiteGroup.singleton(prerequisite));
        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                prerequisitesSatisfied && prerequisiteSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                new ResearchRequirements(updated),
                appendPrerequisiteOrder(prerequisites, List.of(prerequisite)),
                automaticPrerequisitesAllowed,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    /** Adds one lower-precedence generated any-of group. */
    public BlueprintResearchPolicy withAdditionalRequirementGroup(
            ResearchPrerequisiteGroup group,
            boolean groupSatisfied) {
        if (group == null) {
            throw new IllegalArgumentException(
                    "additional research prerequisite group is invalid");
        }
        group.validateFor(blueprintId);
        if (requirements.allOf().contains(group)) {
            return this;
        }
        List<ResearchPrerequisiteGroup> updated = new java.util.ArrayList<>(
                requirements.allOf());
        updated.add(group);
        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                pointCap,
                prerequisitesSatisfied && groupSatisfied,
                journalEnabled,
                treeEnabled,
                visibility,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                new ResearchRequirements(updated),
                appendPrerequisiteOrder(prerequisites, group.anyOf()),
                automaticPrerequisitesAllowed,
                creativeBypassesCost,
                ruleId,
                specificity);
    }

    private static List<ResourceLocation> validatePrerequisiteOrder(
            ResearchRequirements requirements,
            List<ResourceLocation> prerequisiteOrder) {
        List<ResourceLocation> copy = List.copyOf(prerequisiteOrder);
        if (copy.stream().anyMatch(java.util.Objects::isNull)
                || new LinkedHashSet<>(copy).size() != copy.size()
                || !new LinkedHashSet<>(copy).equals(new LinkedHashSet<>(
                        requirements.conservativeAlternatives()))) {
            throw new IllegalArgumentException(
                    "flat prerequisite compatibility order does not match canonical requirements");
        }
        return copy;
    }

    private static List<ResourceLocation> appendPrerequisiteOrder(
            List<ResourceLocation> current,
            List<ResourceLocation> additions) {
        LinkedHashSet<ResourceLocation> combined = new LinkedHashSet<>(current);
        combined.addAll(additions);
        return List.copyOf(combined);
    }
}
