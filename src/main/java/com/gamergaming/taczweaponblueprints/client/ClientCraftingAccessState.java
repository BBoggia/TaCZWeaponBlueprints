package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.AccessIdentity;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Ephemeral allow-list for the exact native crafting menu currently open. */
@OnlyIn(Dist.CLIENT)
public final class ClientCraftingAccessState {
    static final long REQUEST_RETRY_DELAY_NANOS = 5_000_000_000L;

    private static int containerId = -1;
    private static Object menuIdentity;
    private static long nextRequestId;
    private static long requestId;
    private static long snapshotId;
    private static long revision;
    private static boolean requested;
    private static boolean retrySent;
    private static long pendingSinceNanos;
    private static boolean received;
    private static boolean available;
    private static CraftingEligibilityService.Status status =
            CraftingEligibilityService.Status.POLICY_UNAVAILABLE;
    private static Optional<AccessIdentity> accessIdentity = Optional.empty();
    private static Set<String> allowedRecipeIds = Set.of();

    private ClientCraftingAccessState() {
    }

    public static synchronized long beginRequest(
            int currentContainerId,
            Object currentMenuIdentity) {
        if (currentContainerId < 0 || currentMenuIdentity == null) {
            return 0L;
        }
        if (containerId != currentContainerId || menuIdentity != currentMenuIdentity) {
            containerId = currentContainerId;
            menuIdentity = currentMenuIdentity;
            requested = false;
            retrySent = false;
            pendingSinceNanos = 0L;
            received = false;
            available = false;
            status = CraftingEligibilityService.Status.POLICY_UNAVAILABLE;
            allowedRecipeIds = Set.of();
            requestId = 0L;
            snapshotId = 0L;
            accessIdentity = Optional.empty();
            revision++;
        }
        if (requested) {
            return 0L;
        }
        if (nextRequestId == Long.MAX_VALUE) {
            return 0L;
        }
        requested = true;
        nextRequestId++;
        requestId = nextRequestId;
        pendingSinceNanos = System.nanoTime();
        return requestId;
    }

    /** Returns the original request ID once when its response remains incomplete. */
    public static synchronized long retryRequestIfTimedOut(
            int currentContainerId,
            Object currentMenuIdentity,
            long nowNanos) {
        if (containerId != currentContainerId || menuIdentity != currentMenuIdentity
                || !requested || received || retrySent || requestId < 1L
                || pendingSinceNanos < 1L || nowNanos < pendingSinceNanos
                || nowNanos - pendingSinceNanos < REQUEST_RETRY_DELAY_NANOS) {
            return 0L;
        }
        retrySent = true;
        pendingSinceNanos = nowNanos;
        return requestId;
    }

    /** Clears a completed allow-list as soon as a newer server snapshot begins. */
    public static synchronized boolean beginSnapshot(
            int currentContainerId,
            long currentRequestId,
            long currentSnapshotId,
            Optional<AccessIdentity> currentAccessIdentity) {
        currentAccessIdentity = currentAccessIdentity == null
                ? Optional.empty()
                : currentAccessIdentity;
        if (currentContainerId < 0 || currentRequestId < 1L || currentSnapshotId < 1L
                || containerId != currentContainerId || requestId != currentRequestId
                || currentSnapshotId <= snapshotId) {
            return false;
        }
        snapshotId = currentSnapshotId;
        accessIdentity = currentAccessIdentity;
        received = false;
        available = false;
        status = CraftingEligibilityService.Status.POLICY_UNAVAILABLE;
        allowedRecipeIds = Set.of();
        pendingSinceNanos = System.nanoTime();
        revision++;
        return true;
    }

    public static synchronized boolean accept(
            int currentContainerId,
            long currentRequestId,
            long currentSnapshotId,
            Optional<AccessIdentity> currentAccessIdentity,
            CraftingEligibilityService.Status status,
            Set<String> recipeIds) {
        currentAccessIdentity = currentAccessIdentity == null
                ? Optional.empty()
                : currentAccessIdentity;
        if (currentContainerId < 0 || currentRequestId < 1L
                || currentSnapshotId < 1L || status == null || recipeIds == null
                || containerId != currentContainerId || requestId != currentRequestId
                || snapshotId != currentSnapshotId
                || !accessIdentity.equals(currentAccessIdentity)) {
            return false;
        }
        available = status == CraftingEligibilityService.Status.ALLOWED;
        ClientCraftingAccessState.status = status;
        received = true;
        allowedRecipeIds = available ? Set.copyOf(recipeIds) : Set.of();
        requested = true;
        pendingSinceNanos = 0L;
        revision++;
        return true;
    }

    public static synchronized Snapshot snapshot(int currentContainerId) {
        return containerId == currentContainerId
                ? new Snapshot(
                        revision, requestId, snapshotId, requested, received, available,
                        status, accessIdentity, allowedRecipeIds)
                : new Snapshot(
                        revision, 0L, 0L, false, false, false,
                        CraftingEligibilityService.Status.POLICY_UNAVAILABLE,
                        Optional.empty(), Set.of());
    }

    /**
     * Returns access only when both the container number and concrete menu
     * session match. Container numbers are reusable, so screen initialization
     * must not consume a completed response from an older menu instance.
     */
    public static synchronized Snapshot snapshot(
            int currentContainerId,
            Object currentMenuIdentity) {
        return containerId == currentContainerId && menuIdentity == currentMenuIdentity
                ? new Snapshot(
                        revision, requestId, snapshotId, requested, received, available,
                        status, accessIdentity, allowedRecipeIds)
                : new Snapshot(
                        revision, 0L, 0L, false, false, false,
                        CraftingEligibilityService.Status.POLICY_UNAVAILABLE,
                        Optional.empty(), Set.of());
    }

    public static synchronized void clear() {
        containerId = -1;
        menuIdentity = null;
        requested = false;
        retrySent = false;
        pendingSinceNanos = 0L;
        received = false;
        available = false;
        status = CraftingEligibilityService.Status.POLICY_UNAVAILABLE;
        allowedRecipeIds = Set.of();
        requestId = 0L;
        snapshotId = 0L;
        accessIdentity = Optional.empty();
        nextRequestId = 0L;
        revision++;
    }

    public record Snapshot(
            long revision,
            long requestId,
            long snapshotId,
            boolean requested,
            boolean received,
            boolean available,
            CraftingEligibilityService.Status status,
            Optional<AccessIdentity> accessIdentity,
            Set<String> allowedRecipeIds) {
        public Snapshot {
            accessIdentity = accessIdentity == null ? Optional.empty() : accessIdentity;
            if (requestId < 0L || snapshotId < 0L || status == null
                    || available != (received
                            && status == CraftingEligibilityService.Status.ALLOWED)
                    || received && snapshotId < 1L
                    || !available && !allowedRecipeIds.isEmpty()) {
                throw new IllegalArgumentException("crafting access snapshot is invalid");
            }
            allowedRecipeIds = Set.copyOf(allowedRecipeIds);
        }

        public boolean allows(String recipeId) {
            return available && recipeId != null
                    && (unrestrictedCrafting() || allowedRecipeIds.contains(recipeId));
        }

        /** Returns whether this response was produced for the active progression mode. */
        public boolean matchesBlueprintMode(boolean blueprintsEnabled) {
            return accessIdentity
                    .map(identity -> identity.blueprintsEnabled() == blueprintsEnabled)
                    .orElse(true);
        }

        public boolean receivedForMode(boolean blueprintsEnabled) {
            return received && matchesBlueprintMode(blueprintsEnabled);
        }

        public boolean availableForMode(boolean blueprintsEnabled) {
            return available && matchesBlueprintMode(blueprintsEnabled);
        }

        public boolean allows(String recipeId, boolean blueprintsEnabled) {
            return matchesBlueprintMode(blueprintsEnabled) && allows(recipeId);
        }

        public boolean unrestrictedCrafting(boolean blueprintsEnabled) {
            return matchesBlueprintMode(blueprintsEnabled) && unrestrictedCrafting();
        }

        /** Blueprint progression disabled is an explicit grant for TaCZ's native view. */
        public boolean unrestrictedCrafting() {
            return available && accessIdentity
                    .map(identity -> !identity.blueprintsEnabled())
                    .orElse(false);
        }
    }
}
