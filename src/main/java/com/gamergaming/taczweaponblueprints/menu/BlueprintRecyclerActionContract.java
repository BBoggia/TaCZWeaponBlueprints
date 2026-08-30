package com.gamergaming.taczweaponblueprints.menu;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;

import net.minecraft.resources.ResourceLocation;

/** Bounded action/result vocabulary owned only by the Blueprint Recycler. */
public final class BlueprintRecyclerActionContract {
    private BlueprintRecyclerActionContract() {
    }

    public enum Action {
        RECYCLE,
        REDEEM,
        REDEEM_STACK,
        REVERSE_ENGINEER
    }

    public enum ResultCode {
        SUCCESS,
        INVALID_INPUT,
        INVALID_PLAYER,
        PLAYER_DATA_UNAVAILABLE,
        POLICY_UNAVAILABLE,
        POLICY_MISMATCH,
        STALE_POLICY,
        CONTENT_UNAVAILABLE,
        BLOCKED,
        RECYCLING_DISABLED,
        NO_VALUE,
        DUPLICATE_REQUIRED,
        POINT_CAP_REACHED,
        POLICY_INELIGIBLE,
        NO_MATCH,
        NO_ELIGIBLE_AWARD,
        STALE_INPUT,
        TRANSACTION_FAILED,
        INVALID_ITEM_DATA,
        MISSING_LOGICAL_ID,
        CATALOG_KIND_MISMATCH,
        BLUEPRINTS_DISABLED,
        PROGRESSION_EXEMPT,
        REVERSE_ENGINEERING_DISABLED,
        ALREADY_KNOWN,
        LOADED_GUN,
        GUN_HAS_ATTACHMENTS,
        MODIFIED_ITEM_NOT_ALLOWED,
        INSUFFICIENT_INPUT_COUNT,
        PROGRESSION_CAPACITY_EXHAUSTED,
        POINTS_REQUIRED,
        INGREDIENTS_REQUIRED,
        OUTPUT_OCCUPIED,
        ROLLBACK_FAILED;

        public static ResultCode from(BlueprintRecyclingService.Status status) {
            if (status == null) {
                return TRANSACTION_FAILED;
            }
            return switch (status) {
                case SUCCESS -> SUCCESS;
                case INVALID_INPUT -> INVALID_INPUT;
                case PLAYER_DATA_UNAVAILABLE -> PLAYER_DATA_UNAVAILABLE;
                case POLICY_UNAVAILABLE -> POLICY_UNAVAILABLE;
                case POLICY_MISMATCH -> POLICY_MISMATCH;
                case STALE_POLICY -> STALE_POLICY;
                case CONTENT_UNAVAILABLE -> CONTENT_UNAVAILABLE;
                case BLOCKED -> BLOCKED;
                case RECYCLING_DISABLED -> RECYCLING_DISABLED;
                case NO_VALUE -> NO_VALUE;
                case DUPLICATE_REQUIRED -> DUPLICATE_REQUIRED;
                case POINT_CAP_REACHED -> POINT_CAP_REACHED;
                case POLICY_INELIGIBLE -> POLICY_INELIGIBLE;
            };
        }

        public static ResultCode from(ResearchDataRedemptionService.Status status) {
            if (status == null) {
                return TRANSACTION_FAILED;
            }
            return switch (status) {
                case SUCCESS -> SUCCESS;
                case INVALID_PLAYER -> INVALID_PLAYER;
                case PLAYER_DATA_UNAVAILABLE -> PLAYER_DATA_UNAVAILABLE;
                case NO_MATCH -> NO_MATCH;
                case NO_ELIGIBLE_AWARD -> NO_ELIGIBLE_AWARD;
                case POINT_CAP_REACHED -> POINT_CAP_REACHED;
                case STALE_INVENTORY -> STALE_INPUT;
            };
        }

        public static ResultCode from(BlueprintReverseEngineeringService.Status status) {
            if (status == null) {
                return TRANSACTION_FAILED;
            }
            return switch (status) {
                case SUCCESS -> SUCCESS;
                case EMPTY_INPUT, UNSUPPORTED_ITEM, INVALID_PLAYER -> INVALID_INPUT;
                case INVALID_ITEM_DATA -> INVALID_ITEM_DATA;
                case MISSING_LOGICAL_ID -> MISSING_LOGICAL_ID;
                case CONTENT_UNAVAILABLE -> CONTENT_UNAVAILABLE;
                case CATALOG_KIND_MISMATCH -> CATALOG_KIND_MISMATCH;
                case PLAYER_DATA_UNAVAILABLE -> PLAYER_DATA_UNAVAILABLE;
                case BLUEPRINTS_DISABLED -> BLUEPRINTS_DISABLED;
                case BLOCKED -> BLOCKED;
                case PROGRESSION_EXEMPT -> PROGRESSION_EXEMPT;
                case REVERSE_ENGINEERING_DISABLED -> REVERSE_ENGINEERING_DISABLED;
                case ALREADY_KNOWN -> ALREADY_KNOWN;
                case LOADED_GUN -> LOADED_GUN;
                case GUN_HAS_ATTACHMENTS -> GUN_HAS_ATTACHMENTS;
                case MODIFIED_ITEM_NOT_ALLOWED -> MODIFIED_ITEM_NOT_ALLOWED;
                case INSUFFICIENT_INPUT_COUNT -> INSUFFICIENT_INPUT_COUNT;
                case PROGRESSION_CAPACITY_EXHAUSTED -> PROGRESSION_CAPACITY_EXHAUSTED;
                case POLICY_INELIGIBLE -> POLICY_INELIGIBLE;
                case POINTS_REQUIRED -> POINTS_REQUIRED;
                case INGREDIENTS_REQUIRED -> INGREDIENTS_REQUIRED;
                case OUTPUT_OCCUPIED -> OUTPUT_OCCUPIED;
                case STALE_INPUT -> STALE_INPUT;
                case STALE_POLICY -> STALE_POLICY;
                case TRANSACTION_FAILED, READY -> TRANSACTION_FAILED;
                case ROLLBACK_FAILED -> ROLLBACK_FAILED;
            };
        }
    }

    public record ActionResult(
            Action action,
            Optional<ResourceLocation> inputId,
            ResultCode code) {
        public ActionResult {
            inputId = inputId == null ? Optional.empty() : inputId;
            if (action == null || code == null || inputId.isEmpty()
                    || inputId.filter(id -> id.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
                throw new IllegalArgumentException("invalid Blueprint Recycler action result");
            }
        }

        public boolean successful() {
            return code == ResultCode.SUCCESS;
        }
    }
}
