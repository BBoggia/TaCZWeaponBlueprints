package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;

import net.minecraft.resources.ResourceLocation;

/** Immutable normalized facts supplied by a future authoritative server source. */
public record ResearchPointAwardContext(
        ResearchPointAwardTrigger.Type triggerType,
        ResourceLocation activeProfile,
        DispatchMode dispatchMode,
        Optional<ResourceLocation> targetId,
        Set<ResourceLocation> targetTags,
        Optional<String> targetCategory,
        Optional<BlueprintKind> targetKind,
        Optional<ResearchPointAwardTrigger.MilestoneState> milestoneState,
        int previousCount,
        int currentCount,
        Optional<CombatFacts> combatFacts) {
    public ResearchPointAwardContext {
        if (triggerType == null || !validId(activeProfile) || dispatchMode == null
                || previousCount < 0 || currentCount < 0) {
            throw new IllegalArgumentException("invalid Research Point award context");
        }
        targetId = targetId == null ? Optional.empty() : targetId;
        if (targetId.filter(id -> !validId(id)).isPresent()) {
            throw new IllegalArgumentException("invalid Research Point award target ID");
        }
        if (targetTags != null && targetTags.stream().anyMatch(id -> !validId(id))) {
            throw new IllegalArgumentException("invalid Research Point award target tag");
        }
        targetTags = targetTags == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(targetTags));
        if (targetTags.size() > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_SELECTOR_TERMS) {
            throw new IllegalArgumentException("too many Research Point award context tags");
        }
        targetCategory = targetCategory == null
                ? Optional.empty()
                : targetCategory.map(value -> value.toLowerCase(Locale.ROOT));
        if (targetCategory.filter(value -> value.isBlank()
                || value.length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
            throw new IllegalArgumentException("invalid Research Point award target category");
        }
        targetKind = targetKind == null ? Optional.empty() : targetKind;
        milestoneState = milestoneState == null ? Optional.empty() : milestoneState;
        combatFacts = combatFacts == null ? Optional.empty() : combatFacts;
        if (triggerType == ResearchPointAwardTrigger.Type.BLUEPRINT_MILESTONE
                && milestoneState.isEmpty()) {
            throw new IllegalArgumentException("milestone context requires a state");
        }
        if (triggerType == ResearchPointAwardTrigger.Type.ENTITY_KILLED
                && combatFacts.isEmpty()) {
            throw new IllegalArgumentException("entity-killed context requires combat facts");
        }
    }

    public static ResearchPointAwardContext simple(
            ResearchPointAwardTrigger.Type type,
            ResourceLocation activeProfile,
            ResourceLocation targetId) {
        return new ResearchPointAwardContext(
                type,
                activeProfile,
                DispatchMode.LIVE,
                Optional.ofNullable(targetId),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                0,
                Optional.empty());
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    public enum DispatchMode {
        LIVE,
        RETROACTIVE
    }

    public record CombatFacts(
            CreditType creditType,
            boolean fakePlayer,
            boolean petCredit,
            boolean pvp,
            boolean baby,
            boolean named,
            boolean tamed,
            Optional<SpawnProvenance> spawnProvenance,
            long lifetimeTicks,
            ResourceLocation dimension,
            Difficulty difficulty,
            boolean boss) {
        public CombatFacts {
            if (creditType == null || lifetimeTicks < 0L || !validId(dimension)
                    || difficulty == null) {
                throw new IllegalArgumentException("invalid Research Point combat facts");
            }
            spawnProvenance = spawnProvenance == null ? Optional.empty() : spawnProvenance;
        }
    }

    public enum CreditType {
        DIRECT,
        OWNED_PROJECTILE,
        INDIRECT,
        PET
    }

    public enum SpawnProvenance {
        NATURAL,
        STRUCTURE,
        SPAWNER,
        BRED,
        SUMMONED,
        OTHER
    }

    public enum Difficulty {
        PEACEFUL,
        EASY,
        NORMAL,
        HARD
    }
}
