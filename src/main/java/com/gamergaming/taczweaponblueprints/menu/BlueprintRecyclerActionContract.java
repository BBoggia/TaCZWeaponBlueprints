package com.gamergaming.taczweaponblueprints.menu;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryService;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService;

import net.minecraft.resources.ResourceLocation;

/** Bounded action/result vocabulary owned only by the Blueprint Recycler. */
public final class BlueprintRecyclerActionContract {
    private BlueprintRecyclerActionContract() {
    }

    public enum Action {
        RECYCLE,
        REDEEM,
        REDEEM_STACK,
        REVERSE_ENGINEER,
        RECOVER_POINTS,
        ARCHIVE_FRAGMENTS,
        ANALYZE_DATA
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
        NOT_A_WEAPON,
        VERIFIED_LOOT_REQUIRED,
        RECOVERY_DISABLED,
        ROLLBACK_FAILED,
        DELIVERY_FULL,
        STATION_REQUIRED, STATION_TIER_REQUIRED, DATA_DISABLED, ALL_LEARNED, EMPTY_POOL;

        public static ResultCode from(BlueprintFragmentAnalysisService.Status status) {
            if (status == null) {
                return TRANSACTION_FAILED;
            }
            return switch (status) {
                case SUCCESS -> SUCCESS;
                case INVALID_INPUT -> INVALID_INPUT;
                case PLAYER_DATA_UNAVAILABLE -> PLAYER_DATA_UNAVAILABLE;
                case POLICY_UNAVAILABLE -> POLICY_UNAVAILABLE;
                case STALE_POLICY -> STALE_POLICY;
                case FRAGMENTS_DISABLED -> RECYCLING_DISABLED;
                case ARCHIVE_FULL, PROGRESSION_CAPACITY_EXHAUSTED ->
                        PROGRESSION_CAPACITY_EXHAUSTED;
                case LEARNED_TARGET_RETURN_DISABLED -> NO_VALUE;
                case POINT_CAP_REACHED -> POINT_CAP_REACHED;
                case OUTPUT_OCCUPIED -> OUTPUT_OCCUPIED;
                case TRANSACTION_FAILED, READY -> TRANSACTION_FAILED;
                case ROLLBACK_FAILED -> ROLLBACK_FAILED;
            };
        }

        public static ResultCode from(BlueprintRecyclingService.Status status) {
            if (status == null) {
                return TRANSACTION_FAILED;
            }
            return switch (status) {
                case DELIVERY_FULL -> DELIVERY_FULL;
                case TRANSACTION_FAILED -> TRANSACTION_FAILED;
                case ROLLBACK_FAILED -> ROLLBACK_FAILED;
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
                case STATION_REQUIRED -> STATION_REQUIRED;
                case STATION_TIER_REQUIRED -> STATION_TIER_REQUIRED;
                case DATA_DISABLED -> DATA_DISABLED;
                case ALL_LEARNED -> ALL_LEARNED;
                case EMPTY_POOL -> EMPTY_POOL;
                case OUTPUT_OCCUPIED -> OUTPUT_OCCUPIED;
                case POINTS_REQUIRED -> POINTS_REQUIRED;
                case INGREDIENTS_REQUIRED -> INGREDIENTS_REQUIRED;
                case PROGRESSION_CAPACITY_EXHAUSTED -> PROGRESSION_CAPACITY_EXHAUSTED;
                case TRANSACTION_FAILED -> TRANSACTION_FAILED;
                case ROLLBACK_FAILED -> ROLLBACK_FAILED;
                case DELIVERY_FULL -> DELIVERY_FULL;
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
                case RECOVERY_MODE_DISABLED -> RECOVERY_DISABLED;
                case STALE_INPUT -> STALE_INPUT;
                case STALE_POLICY -> STALE_POLICY;
                case TRANSACTION_FAILED, READY -> TRANSACTION_FAILED;
                case ROLLBACK_FAILED -> ROLLBACK_FAILED;
            };
        }

        public static ResultCode from(FoundWeaponRecoveryService.Status status) {
            if (status == null) {
                return TRANSACTION_FAILED;
            }
            return switch (status) {
                case DELIVERY_FULL -> DELIVERY_FULL;
                case SUCCESS -> SUCCESS;
                case INVALID_INPUT -> INVALID_INPUT;
                case INVALID_PLAYER -> INVALID_PLAYER;
                case PLAYER_DATA_UNAVAILABLE -> PLAYER_DATA_UNAVAILABLE;
                case REVERSE_ENGINEERING_INELIGIBLE -> POLICY_INELIGIBLE;
                case NOT_A_WEAPON -> NOT_A_WEAPON;
                case VERIFIED_LOOT_REQUIRED -> VERIFIED_LOOT_REQUIRED;
                case RECOVERY_DISABLED -> RECOVERY_DISABLED;
                case POLICY_UNAVAILABLE -> POLICY_UNAVAILABLE;
                case STALE_POLICY -> STALE_POLICY;
                case POLICY_INELIGIBLE -> POLICY_INELIGIBLE;
                case RECYCLING_DISABLED -> RECYCLING_DISABLED;
                case NO_VALUE -> NO_VALUE;
                case POINT_CAP_REACHED -> POINT_CAP_REACHED;
                case STALE_INPUT -> STALE_INPUT;
                case TRANSACTION_FAILED, READY -> TRANSACTION_FAILED;
                case ROLLBACK_FAILED -> ROLLBACK_FAILED;
            };
        }
    }

    public record FragmentResult(int archived, int retained, int saved, boolean blueprintCreated) {
        public FragmentResult {
            if (archived < 0 || retained < 0 || (long) archived + retained > 64
                    || saved < 0 || saved > PlayerProgressionLimits.MAX_PROGRESS_VALUE)
                throw new IllegalArgumentException("invalid fragment result");
        }
    }

    public record ActionResult(
            Action action,
            Optional<ResourceLocation> inputId,
            ResultCode code,
            Optional<com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.Result> outcome,
            Optional<FragmentResult> fragments,
            long stateToken) {
        public ActionResult(Action action, Optional<ResourceLocation> inputId, ResultCode code) {
            this(action, inputId, code, Optional.empty(), Optional.empty());
        }
        public ActionResult(Action action, Optional<ResourceLocation> inputId, ResultCode code,
                Optional<com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.Result> outcome) {
            this(action, inputId, code, outcome, Optional.empty());
        }
        public ActionResult(Action action, Optional<ResourceLocation> inputId, ResultCode code,
                Optional<com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.Result> outcome,
                Optional<FragmentResult> fragments) {
            this(action, inputId, code, outcome, fragments, 0L);
        }
        public ActionResult withStateToken(long token) {
            return new ActionResult(action, inputId, code, outcome, fragments, token);
        }
        public ActionResult {
            if (stateToken < 0) throw new IllegalArgumentException("invalid result state token");
            if (fragments == null || fragments.isPresent() && (code != ResultCode.SUCCESS || action != Action.ARCHIVE_FRAGMENTS))
                throw new IllegalArgumentException("invalid fragment feedback");
            if (outcome == null || outcome.isPresent() && (code != ResultCode.SUCCESS || action != Action.REVERSE_ENGINEER && action != Action.ANALYZE_DATA))
                throw new IllegalArgumentException("invalid analysis outcome feedback");
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
