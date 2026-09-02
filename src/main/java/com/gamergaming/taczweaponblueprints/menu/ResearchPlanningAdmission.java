package com.gamergaming.taczweaponblueprints.menu;

/**
 * Server-wide fuse for Research Bench requests that can invoke route planning.
 * Minecraft handles these requests on the server thread, so one tick counter is
 * sufficient and avoids retaining player or server identities across restarts.
 */
public final class ResearchPlanningAdmission {
    static final int MAX_PLANS_PER_SERVER_TICK = 1;

    private static long admittedTick = Long.MIN_VALUE;
    private static int admittedPlans;

    private ResearchPlanningAdmission() {
    }

    public static synchronized boolean admit(long currentServerTick) {
        if (currentServerTick < 0L) {
            return false;
        }
        if (admittedTick == Long.MIN_VALUE || currentServerTick != admittedTick) {
            admittedTick = currentServerTick;
            admittedPlans = 0;
        }
        if (admittedPlans >= MAX_PLANS_PER_SERVER_TICK) {
            return false;
        }
        admittedPlans++;
        return true;
    }

    public static synchronized void clear() {
        admittedTick = Long.MIN_VALUE;
        admittedPlans = 0;
    }
}
