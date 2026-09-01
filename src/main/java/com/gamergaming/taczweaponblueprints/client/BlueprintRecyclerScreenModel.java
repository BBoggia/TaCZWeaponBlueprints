package com.gamergaming.taczweaponblueprints.client;

import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryService;

/** Pure client presentation derived only from a server-authored Recycler preview. */
public record BlueprintRecyclerScreenModel(
        String headingKey,
        String statusKey,
        Optional<BlueprintRecyclerActionContract.Action> primaryAction,
        Optional<BlueprintRecyclerActionContract.Action> secondaryAction,
        boolean controlsEnabled,
        StatusEmphasis statusEmphasis,
        boolean summaryVisible) {
    private static final String PREFIX = "gui.taczweaponblueprints.blueprint_recycler.";

    public BlueprintRecyclerScreenModel {
        if (headingKey == null || headingKey.isBlank()
                || statusKey == null || statusKey.isBlank()
                || primaryAction == null || secondaryAction == null
                || statusEmphasis == null
                || secondaryAction.isPresent() && primaryAction.isEmpty()) {
            throw new IllegalArgumentException("invalid Blueprint Recycler screen model");
        }
        if (controlsEnabled && primaryAction.isEmpty()) {
            throw new IllegalArgumentException("enabled Recycler model has no action");
        }
    }

    public static BlueprintRecyclerScreenModel from(
            BlueprintRecyclerPreview preview,
            boolean requestPending) {
        BlueprintRecyclerPreview safe = preview == null
                ? BlueprintRecyclerPreview.EMPTY
                : preview;
        return switch (safe.inputKind()) {
            case EMPTY -> passive("empty.title", "empty.detail");
            case INVALID -> passive("invalid.title", "invalid.detail");
            case BLUEPRINT -> new BlueprintRecyclerScreenModel(
                    PREFIX + "blueprint.title",
                    PREFIX + "blueprint.status."
                            + enumKey(safe.recyclingStatus().orElseThrow()),
                    safe.actionable()
                            ? Optional.of(BlueprintRecyclerActionContract.Action.RECYCLE)
                            : Optional.empty(),
                    Optional.empty(),
                    safe.actionable() && !requestPending,
                    safe.actionable() ? StatusEmphasis.POSITIVE : StatusEmphasis.MUTED,
                    true);
            case RESEARCH_DATA -> new BlueprintRecyclerScreenModel(
                    PREFIX + "research_data.title",
                    PREFIX + "research_data.status."
                            + enumKey(safe.researchDataStatus().orElseThrow()),
                    safe.actionable()
                            ? Optional.of(BlueprintRecyclerActionContract.Action.REDEEM)
                            : Optional.empty(),
                    safe.actionable() && safe.inputCount() > 1
                            ? Optional.of(BlueprintRecyclerActionContract.Action.REDEEM_STACK)
                            : Optional.empty(),
                    safe.actionable() && !requestPending,
                    safe.actionable() ? StatusEmphasis.POSITIVE : StatusEmphasis.MUTED,
                    true);
            case PHYSICAL_ITEM -> physical(safe, requestPending);
        };
    }

    private static BlueprintRecyclerScreenModel physical(
            BlueprintRecyclerPreview preview,
            boolean requestPending) {
        boolean reverseReady = preview.reverseEngineeringStatus().filter(status ->
                status == BlueprintReverseEngineeringService.Status.READY).isPresent();
        boolean recoveryReady = preview.recoveryStatus().filter(status ->
                status == FoundWeaponRecoveryService.Status.READY).isPresent();
        boolean knownCopy = preview.alreadyKnown() && reverseReady;
        boolean knownBlocked = preview.reverseEngineeringStatus().filter(status ->
                status == BlueprintReverseEngineeringService.Status.ALREADY_KNOWN).isPresent();
        boolean directOnly = preview.reverseEngineeringStatus().filter(status ->
                status == BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED)
                .isPresent();
        String statusKey;
        if (reverseReady && recoveryReady) {
            statusKey = PREFIX + "reverse.status.choice_ready";
        } else if (recoveryReady) {
            statusKey = PREFIX + "reverse.status.direct_ready";
        } else if (directOnly && preview.recoveryStatus().isPresent()) {
            statusKey = PREFIX + "recovery.status."
                    + enumKey(preview.recoveryStatus().orElseThrow());
        } else if (knownCopy) {
            statusKey = PREFIX + "reverse.status.known_copy";
        } else {
            statusKey = PREFIX + "reverse.status."
                    + enumKey(preview.reverseEngineeringStatus().orElseThrow());
        }
        return new BlueprintRecyclerScreenModel(
                PREFIX + "reverse.title",
                statusKey,
                reverseReady
                        ? Optional.of(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER)
                        : recoveryReady
                                ? Optional.of(BlueprintRecyclerActionContract.Action.RECOVER_POINTS)
                                : Optional.empty(),
                reverseReady && recoveryReady
                        ? Optional.of(BlueprintRecyclerActionContract.Action.RECOVER_POINTS)
                        : Optional.empty(),
                (reverseReady || recoveryReady) && !requestPending,
                knownCopy || knownBlocked
                        ? StatusEmphasis.NOTICE
                        : reverseReady || recoveryReady
                                ? StatusEmphasis.POSITIVE
                                : StatusEmphasis.MUTED,
                !knownBlocked || recoveryReady);
    }

    public static String actionKey(BlueprintRecyclerActionContract.Action action) {
        if (action == null) {
            throw new IllegalArgumentException("Recycler action is required");
        }
        return PREFIX + "action." + enumKey(action);
    }

    public static String resultKey(BlueprintRecyclerActionContract.ResultCode result) {
        if (result == null) {
            throw new IllegalArgumentException("Recycler result is required");
        }
        return PREFIX + "result." + enumKey(result);
    }

    private static BlueprintRecyclerScreenModel passive(String heading, String status) {
        return new BlueprintRecyclerScreenModel(
                PREFIX + heading,
                PREFIX + status,
                Optional.empty(),
                Optional.empty(),
                false,
                StatusEmphasis.MUTED,
                true);
    }

    private static String enumKey(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    public enum StatusEmphasis {
        MUTED,
        POSITIVE,
        NOTICE
    }
}
