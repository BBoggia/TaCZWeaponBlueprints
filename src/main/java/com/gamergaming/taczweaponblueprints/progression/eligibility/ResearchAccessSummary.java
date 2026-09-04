package com.gamergaming.taczweaponblueprints.progression.eligibility;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

/** Disclosure-safe primary non-economic reason for a research preview. */
public record ResearchAccessSummary(
        Kind kind,
        Optional<ResearchWorkbenchTier> currentTier,
        Optional<ResearchWorkbenchTier> requiredTier,
        Optional<String> messageKey) {
    public static final ResearchAccessSummary NONE = new ResearchAccessSummary(
            Kind.NONE, Optional.empty(), Optional.empty(), Optional.empty());
    public static final ResearchAccessSummary POLICY_UNAVAILABLE = new ResearchAccessSummary(
            Kind.POLICY_UNAVAILABLE, Optional.empty(), Optional.empty(), Optional.empty());

    public ResearchAccessSummary {
        currentTier = currentTier == null ? Optional.empty() : currentTier;
        requiredTier = requiredTier == null ? Optional.empty() : requiredTier;
        messageKey = messageKey == null ? Optional.empty() : messageKey
                .map(value -> ProgressionIds.messageKey(value, "research blocker message key"));
        if (kind == null
                || kind == Kind.NONE && (currentTier.isPresent()
                        || requiredTier.isPresent() || messageKey.isPresent())
                || kind == Kind.POLICY_UNAVAILABLE && (currentTier.isPresent()
                        || requiredTier.isPresent() || messageKey.isPresent())
                || kind == Kind.WORKBENCH_TIER && (requiredTier.isEmpty()
                        || messageKey.isPresent()
                        || currentTier.isPresent()
                                && currentTier.orElseThrow().satisfies(
                                        requiredTier.orElseThrow()))
                || kind == Kind.PROGRESSION_GATE && (currentTier.isPresent()
                        || requiredTier.isPresent() || messageKey.isEmpty())) {
            throw new IllegalArgumentException("research access summary is invalid");
        }
    }

    public static ResearchAccessSummary workbench(
            ResearchWorkbenchTier current,
            ResearchWorkbenchTier required) {
        return new ResearchAccessSummary(
                Kind.WORKBENCH_TIER,
                Optional.ofNullable(current),
                Optional.of(required),
                Optional.empty());
    }

    public static ResearchAccessSummary gate(String messageKey) {
        return new ResearchAccessSummary(
                Kind.PROGRESSION_GATE,
                Optional.empty(),
                Optional.empty(),
                Optional.of(messageKey));
    }

    public boolean blocked() {
        return kind != Kind.NONE;
    }

    public enum Kind {
        NONE,
        POLICY_UNAVAILABLE,
        WORKBENCH_TIER,
        PROGRESSION_GATE
    }
}
