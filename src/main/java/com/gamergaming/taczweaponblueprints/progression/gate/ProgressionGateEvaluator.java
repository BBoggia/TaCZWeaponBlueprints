package com.gamergaming.taczweaponblueprints.progression.gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Advancement;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Criterion;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Disclosure;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.WorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateEvaluation.RequirementHint;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateEvaluation.UnmetGroup;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** On-demand, bounded server authority for one blueprint's Progression Gates. */
public final class ProgressionGateEvaluator {
    private ProgressionGateEvaluator() {
    }

    /** Evaluates the active, revision-matched policy for one blueprint. */
    public static ProgressionGateEvaluation evaluateBlueprint(
            ServerPlayer player,
            ResourceLocation blueprintId,
            ResearchInteractionMode interactionMode,
            Optional<ResearchWorkbenchContext> workbenchContext) {
        ResourceLocation subjectId = ProgressionIds.require(
                blueprintId, "Progression Gate subject ID");
        if (interactionMode == null || workbenchContext == null) {
            throw new IllegalArgumentException("Progression Gate evaluation inputs cannot be null");
        }
        ProgressionGateEvaluation authorityFailure = validatePlayer(
                player, subjectId, interactionMode);
        if (authorityFailure != null) {
            return authorityFailure;
        }

        ProgressionGateRequirements requirements;
        if (interactionMode == ResearchInteractionMode.CRAFTING) {
            requirements = ProgressionPolicyAccessService.acquireCrafting(
                            ProgressionPolicyAccessService.Mode.ENSURE_CURRENT)
                    .flatMap(context -> context.craftingPolicyFor(subjectId))
                    .map(com.gamergaming.taczweaponblueprints.resource.research
                            .ResolvedBlueprintCraftingPolicy::gates)
                    .orElse(null);
        } else {
            requirements = ProgressionPolicyAccessService.acquire(
                            ProgressionPolicyAccessService.Mode.ENSURE_CURRENT)
                    .flatMap(context -> context.policyFor(subjectId))
                    .map(com.gamergaming.taczweaponblueprints.resource.research
                            .ResolvedBlueprintProgressionPolicy::gates)
                    .orElse(null);
        }
        if (requirements == null) {
            return ProgressionGateEvaluation.unavailable(
                    subjectId,
                    interactionMode,
                    ProgressionGateEvaluation.Status.POLICY_UNAVAILABLE);
        }
        return evaluateRequirements(
                player,
                subjectId,
                requirements,
                interactionMode,
                workbenchContext);
    }

    /** Evaluates an immutable definition without consulting the active policy publication. */
    public static ProgressionGateEvaluation evaluateRequirements(
            ServerPlayer player,
            ResourceLocation subjectId,
            ProgressionGateRequirements requirements,
            ResearchInteractionMode interactionMode,
            Optional<ResearchWorkbenchContext> workbenchContext) {
        subjectId = ProgressionIds.require(subjectId, "Progression Gate subject ID");
        if (requirements == null || interactionMode == null || workbenchContext == null) {
            throw new IllegalArgumentException("Progression Gate evaluation inputs cannot be null");
        }
        ProgressionGateEvaluation authorityFailure = validatePlayer(
                player, subjectId, interactionMode);
        if (authorityFailure != null) {
            return authorityFailure;
        }
        IPlayerRecipeData playerData = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (playerData == null) {
            return ProgressionGateEvaluation.unavailable(
                    subjectId,
                    interactionMode,
                    ProgressionGateEvaluation.Status.PLAYER_DATA_UNAVAILABLE);
        }
        return evaluate(
                subjectId,
                requirements,
                interactionMode,
                playerData,
                advancementId -> advancementCompleted(player, advancementId),
                workbenchContext);
    }

    static ProgressionGateEvaluation evaluate(
            ResourceLocation subjectId,
            ProgressionGateRequirements requirements,
            ResearchInteractionMode interactionMode,
            IPlayerRecipeData playerData,
            AdvancementCompletionLookup advancementLookup,
            Optional<ResearchWorkbenchContext> workbenchContext) {
        subjectId = ProgressionIds.require(subjectId, "Progression Gate subject ID");
        if (requirements == null || interactionMode == null || playerData == null
                || advancementLookup == null || workbenchContext == null) {
            throw new IllegalArgumentException("Progression Gate evaluation inputs cannot be null");
        }
        Map<String, Integer> savedCriteria = playerData.getProgressionCriteria();
        if (savedCriteria == null
                || savedCriteria.size() > PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA) {
            return ProgressionGateEvaluation.unavailable(
                    subjectId,
                    interactionMode,
                    ProgressionGateEvaluation.Status.PLAYER_DATA_UNAVAILABLE);
        }

        Set<ResourceLocation> criterionIds = new LinkedHashSet<>();
        Set<ResourceLocation> advancementIds = new LinkedHashSet<>();
        requirements.allOf().forEach(group -> group.anyOf().forEach(condition -> {
            if (!condition.appliesTo(interactionMode)) {
                return;
            }
            if (condition instanceof Criterion criterion) {
                criterionIds.add(criterion.criterionId());
            } else if (condition instanceof Advancement advancement) {
                advancementIds.add(advancement.advancementId());
            }
        }));

        List<ProgressionCriterionProgress> criteria = new ArrayList<>(criterionIds.size());
        for (ResourceLocation criterionId : criterionIds) {
            Integer rawValue = savedCriteria.get(criterionId.toString());
            if (rawValue == null && savedCriteria.containsKey(criterionId.toString())) {
                return ProgressionGateEvaluation.unavailable(
                        subjectId,
                        interactionMode,
                        ProgressionGateEvaluation.Status.PLAYER_DATA_UNAVAILABLE);
            }
            int value = rawValue == null ? 0 : rawValue;
            if (rawValue != null && (value <= 0
                    || value > ProgressionCriterionProgress.MAX_VALUE)) {
                return ProgressionGateEvaluation.unavailable(
                        subjectId,
                        interactionMode,
                        ProgressionGateEvaluation.Status.PLAYER_DATA_UNAVAILABLE);
            }
            if (value > 0) {
                criteria.add(new ProgressionCriterionProgress(criterionId, value));
            }
        }

        Set<ResourceLocation> completedAdvancements = new LinkedHashSet<>();
        Map<ResourceLocation, Boolean> advancementResults = new HashMap<>();
        for (ResourceLocation advancementId : advancementIds) {
            boolean completed;
            try {
                completed = advancementResults.computeIfAbsent(
                        advancementId, advancementLookup::completed);
            } catch (RuntimeException exception) {
                return ProgressionGateEvaluation.unavailable(
                        subjectId,
                        interactionMode,
                        ProgressionGateEvaluation.Status.ADVANCEMENT_STATE_UNAVAILABLE);
            }
            if (completed) {
                completedAdvancements.add(advancementId);
            }
        }
        ProgressionGateEvidence evidence = new ProgressionGateEvidence(
                criteria, completedAdvancements, workbenchContext);
        ProgressionGateRequirements.Evaluation raw = requirements.evaluate(
                interactionMode, evidence);
        List<UnmetGroup> unmet = disclosureSafeUnmetGroups(
                requirements.allOf(), raw.groups(), evidence, interactionMode);
        return new ProgressionGateEvaluation(
                subjectId,
                interactionMode,
                ProgressionGateEvaluation.Status.EVALUATED,
                unmet);
    }

    private static List<UnmetGroup> disclosureSafeUnmetGroups(
            List<ProgressionGateGroup> definitions,
            List<ProgressionGateGroup.Evaluation> evaluations,
            ProgressionGateEvidence evidence,
            ResearchInteractionMode interactionMode) {
        List<UnmetGroup> unmet = new ArrayList<>();
        for (int index = 0; index < evaluations.size(); index++) {
            ProgressionGateGroup.Evaluation evaluation = evaluations.get(index);
            if (!evaluation.applies() || evaluation.satisfied()) {
                continue;
            }
            List<RequirementHint> alternatives = definitions.get(index).anyOf().stream()
                    .filter(condition -> condition.appliesTo(interactionMode))
                    .map(condition -> hint(condition, evidence, interactionMode))
                    .toList();
            unmet.add(new UnmetGroup(index, alternatives));
        }
        return List.copyOf(unmet);
    }

    private static RequirementHint hint(
            ProgressionGateCondition condition,
            ProgressionGateEvidence evidence,
            ResearchInteractionMode interactionMode) {
        if (condition.disclosure() == Disclosure.HIDDEN) {
            return new RequirementHint(
                    condition.type(),
                    condition.messageKey(),
                    condition.disclosure(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }
        if (condition instanceof Criterion criterion) {
            return new RequirementHint(
                    condition.type(),
                    condition.messageKey(),
                    condition.disclosure(),
                    Optional.of(criterion.criterionId()),
                    Optional.of(evidence.criterionValue(criterion.criterionId())),
                    Optional.of(criterion.requiredValue()),
                    Optional.empty(),
                    Optional.empty());
        }
        if (condition instanceof Advancement advancement) {
            return new RequirementHint(
                    condition.type(),
                    condition.messageKey(),
                    condition.disclosure(),
                    Optional.of(advancement.advancementId()),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }
        WorkbenchTier workbench = (WorkbenchTier) condition;
        Optional<ResearchWorkbenchTier> currentTier = evidence.workbenchContext()
                        .filter(context -> context.interactionMode() == interactionMode)
                        .map(ResearchWorkbenchContext::tier);
        return new RequirementHint(
                condition.type(),
                condition.messageKey(),
                condition.disclosure(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                currentTier,
                Optional.of(workbench.requiredTier()));
    }

    private static ProgressionGateEvaluation validatePlayer(
            ServerPlayer player,
            ResourceLocation subjectId,
            ResearchInteractionMode interactionMode) {
        if (player == null || player.server == null || player.level().isClientSide) {
            return ProgressionGateEvaluation.unavailable(
                    subjectId,
                    interactionMode,
                    ProgressionGateEvaluation.Status.INVALID_PLAYER);
        }
        if (!player.server.isSameThread()) {
            return ProgressionGateEvaluation.unavailable(
                    subjectId,
                    interactionMode,
                    ProgressionGateEvaluation.Status.WRONG_THREAD);
        }
        return null;
    }

    private static boolean advancementCompleted(
            ServerPlayer player,
            ResourceLocation advancementId) {
        net.minecraft.advancements.Advancement advancement =
                player.server.getAdvancements().getAdvancement(advancementId);
        return advancement != null
                && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @FunctionalInterface
    interface AdvancementCompletionLookup {
        boolean completed(ResourceLocation advancementId);
    }
}
