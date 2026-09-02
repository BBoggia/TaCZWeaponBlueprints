package com.gamergaming.taczweaponblueprints.progression;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Pure eligibility evaluation. It reads stack/player state and mutates neither. */
public final class BlueprintReverseEngineeringEvaluator {
    private BlueprintReverseEngineeringEvaluator() {
    }

    public static Evaluation evaluate(
            ItemStack stack,
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate) {
        return evaluate(
                stack,
                snapshot,
                catalog,
                profileId,
                playerData,
                blockedPredicate,
                progressionExemptPredicate,
                null);
    }

    static Evaluation evaluate(
            ItemStack stack,
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate,
            PhysicalItemBlueprintResolver.IdentityAdapter identityAdapter) {
        return evaluate(
                stack,
                snapshot,
                catalog,
                profileId,
                playerData,
                blockedPredicate,
                progressionExemptPredicate,
                identityAdapter,
                false);
    }

    static Evaluation evaluate(
            ItemStack stack,
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate,
            PhysicalItemBlueprintResolver.IdentityAdapter identityAdapter,
            boolean nonLearningResultPermitted) {
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        PhysicalItemBlueprintResolver.Resolution physical =
                identityAdapter == null
                        ? PhysicalItemBlueprintResolver.resolve(stack, stableCatalog)
                        : PhysicalItemBlueprintResolver.resolve(stack, stableCatalog, identityAdapter);
        if (!physical.resolved()) {
            return Evaluation.physicalFailure(mapPhysicalStatus(physical.status()), physical);
        }
        ResourceLocation blueprintId = physical.blueprintId().orElseThrow();
        Predicate<String> stableBlocked = blockedPredicate == null ? ignored -> false : blockedPredicate;
        Predicate<ResourceLocation> stableExempt = progressionExemptPredicate == null
                ? ignored -> false
                : progressionExemptPredicate;
        BlueprintResearchPolicy researchPolicy = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                stableCatalog,
                profileId,
                blueprintId,
                playerData,
                stableBlocked);
        BlueprintReverseEngineeringPolicy reversePolicy =
                BlueprintResearchPolicyResolver.reverseEngineeringPolicyFor(
                        snapshot,
                        stableCatalog,
                        profileId,
                        blueprintId);
        int requiredCount = requiredInputCount(physical.data().orElseThrow(), reversePolicy);

        Status status;
        if (!researchPolicy.available()) {
            status = Status.CONTENT_UNAVAILABLE;
        } else if (researchPolicy.blocked()) {
            status = Status.BLOCKED;
        } else if (stableExempt.test(blueprintId)) {
            status = Status.PROGRESSION_EXEMPT;
        } else if (!reversePolicy.enabled()
                || (!reversePolicy.physicalBlueprintLearningMode().learningPermitted()
                        && !reversePolicy.outputRecyclable()
                        && !nonLearningResultPermitted)) {
            status = Status.REVERSE_ENGINEERING_DISABLED;
        } else if (!researchPolicy.playerDataAvailable()) {
            status = Status.PLAYER_DATA_UNAVAILABLE;
        } else if (researchPolicy.learned() && !reversePolicy.allowKnown()) {
            status = Status.ALREADY_KNOWN;
        } else if (physical.loadedGun()) {
            status = Status.LOADED_GUN;
        } else if (physical.containsAttachments()) {
            status = Status.GUN_HAS_ATTACHMENTS;
        } else if (physical.modified() && !reversePolicy.allowModified()) {
            status = Status.MODIFIED_ITEM_NOT_ALLOWED;
        } else if (physical.stackCount() < requiredCount) {
            status = Status.INSUFFICIENT_INPUT_COUNT;
        } else {
            status = Status.READY;
        }
        return new Evaluation(
                status,
                physical,
                Optional.of(researchPolicy),
                Optional.of(reversePolicy),
                requiredCount);
    }

    public static int requiredInputCount(
            BlueprintData data,
            BlueprintReverseEngineeringPolicy policy) {
        if (data == null || policy == null) {
            throw new IllegalArgumentException("reverse-engineering data and policy cannot be null");
        }
        return policy.inputCount().orElseGet(() -> data.getKind() == BlueprintKind.AMMO
                ? data.getCanonicalOutputCount()
                : 1);
    }

    private static Status mapPhysicalStatus(PhysicalItemBlueprintResolver.Status status) {
        return switch (status) {
            case EMPTY_INPUT -> Status.EMPTY_INPUT;
            case UNSUPPORTED_ITEM -> Status.UNSUPPORTED_ITEM;
            case INVALID_ITEM_DATA -> Status.INVALID_ITEM_DATA;
            case MISSING_LOGICAL_ID -> Status.MISSING_LOGICAL_ID;
            case NOT_RECIPE_BACKED -> Status.CONTENT_UNAVAILABLE;
            case CATALOG_KIND_MISMATCH -> Status.CATALOG_KIND_MISMATCH;
            case RESOLVED -> throw new IllegalArgumentException(
                    "resolved physical item cannot map to a failure status");
        };
    }

    public enum Status {
        EMPTY_INPUT,
        UNSUPPORTED_ITEM,
        INVALID_ITEM_DATA,
        MISSING_LOGICAL_ID,
        CONTENT_UNAVAILABLE,
        CATALOG_KIND_MISMATCH,
        BLOCKED,
        PROGRESSION_EXEMPT,
        REVERSE_ENGINEERING_DISABLED,
        PLAYER_DATA_UNAVAILABLE,
        ALREADY_KNOWN,
        LOADED_GUN,
        GUN_HAS_ATTACHMENTS,
        MODIFIED_ITEM_NOT_ALLOWED,
        INSUFFICIENT_INPUT_COUNT,
        READY
    }

    public record Evaluation(
            Status status,
            PhysicalItemBlueprintResolver.Resolution physical,
            Optional<BlueprintResearchPolicy> researchPolicy,
            Optional<BlueprintReverseEngineeringPolicy> reversePolicy,
            int requiredInputCount) {
        public Evaluation {
            if (status == null || physical == null) {
                throw new IllegalArgumentException("reverse-engineering evaluation is incomplete");
            }
            researchPolicy = researchPolicy == null ? Optional.empty() : researchPolicy;
            reversePolicy = reversePolicy == null ? Optional.empty() : reversePolicy;
            if (requiredInputCount < 0
                    || requiredInputCount > BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT) {
                throw new IllegalArgumentException(
                        "reverse-engineering evaluation has an invalid input count");
            }
        }

        static Evaluation physicalFailure(
                Status status,
                PhysicalItemBlueprintResolver.Resolution physical) {
            return new Evaluation(status, physical, Optional.empty(), Optional.empty(), 0);
        }

        public boolean ready() {
            return status == Status.READY;
        }
    }
}
