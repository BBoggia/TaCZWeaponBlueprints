package com.gamergaming.taczweaponblueprints.menu;

/**
 * Server-wide fuse for Research Bench requests that can invoke route planning.
 * Minecraft handles these requests on the server thread, so one tick counter is
 * sufficient and avoids retaining player or server identities across restarts.
 */
public final class ResearchPlanningAdmission {
    static final int MAX_PLANS_PER_SERVER_TICK = 1;
    /** Reserves regular progress for queued background work under interactive load. */
    static final int BACKGROUND_RESERVATION_INTERVAL_TICKS = 4;

    private static long admittedTick = Long.MIN_VALUE;
    private static int admittedPlans;
    private static boolean interactiveSlotUsed;
    private static final java.util.LinkedHashSet<java.util.UUID> QUEUED_REQUESTERS =
            new java.util.LinkedHashSet<>();

    private ResearchPlanningAdmission() {
    }

    public static synchronized boolean admit(long currentServerTick) {
        if (currentServerTick < 0L) {
            return false;
        }
        advanceTick(currentServerTick);
        if (!QUEUED_REQUESTERS.isEmpty()
                && Math.floorMod(currentServerTick, BACKGROUND_RESERVATION_INTERVAL_TICKS) == 0L) {
            return false;
        }
        return claimSlot();
    }

    /**
     * Allows one player selection or purchase per tick alongside the single
     * background planning slot, regardless of which request arrives first.
     */
    public static synchronized boolean admitInteractive(long currentServerTick) {
        if (currentServerTick < 0L) {
            return false;
        }
        advanceTick(currentServerTick);
        if (interactiveSlotUsed) {
            return false;
        }
        interactiveSlotUsed = true;
        return true;
    }

    private static void advanceTick(long currentServerTick) {
        if (admittedTick == Long.MIN_VALUE || currentServerTick != admittedTick) {
            admittedTick = currentServerTick;
            admittedPlans = 0;
            interactiveSlotUsed = false;
        }
    }

    private static boolean claimSlot() {
        if (admittedPlans >= MAX_PLANS_PER_SERVER_TICK) {
            return false;
        }
        admittedPlans++;
        return true;
    }

    public static synchronized void registerQueued(java.util.UUID requester) {
        if (requester == null) {
            throw new IllegalArgumentException("queued research planner requester is invalid");
        }
        QUEUED_REQUESTERS.add(requester);
    }

    /** Grants the shared planning slot in stable round-robin requester order. */
    public static synchronized boolean admitQueued(
            long currentServerTick,
            java.util.UUID requester) {
        if (requester == null || !QUEUED_REQUESTERS.contains(requester)
                || !QUEUED_REQUESTERS.iterator().next().equals(requester)) {
            return false;
        }
        if (currentServerTick < 0L) {
            return false;
        }
        advanceTick(currentServerTick);
        if (!claimSlot()) {
            return false;
        }
        QUEUED_REQUESTERS.remove(requester);
        QUEUED_REQUESTERS.add(requester);
        return true;
    }

    public static synchronized void unregisterQueued(java.util.UUID requester) {
        if (requester != null) {
            QUEUED_REQUESTERS.remove(requester);
        }
    }

    public static synchronized void clear() {
        admittedTick = Long.MIN_VALUE;
        admittedPlans = 0;
        interactiveSlotUsed = false;
        QUEUED_REQUESTERS.clear();
    }
}
