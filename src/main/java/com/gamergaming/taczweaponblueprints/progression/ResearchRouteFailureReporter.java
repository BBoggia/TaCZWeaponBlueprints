package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

/** Rate-limits diagnostics for fail-closed route evaluation boundaries. */
public final class ResearchRouteFailureReporter {
    private static final long LOG_INTERVAL_NANOS = 60_000_000_000L;
    private static boolean logged;
    private static long lastLogNanos;
    private static int suppressedFailures;

    private ResearchRouteFailureReporter() {
    }

    /** Clears server-instance-local suppression when the integrated or dedicated server stops. */
    public static synchronized void clear() {
        logged = false;
        lastLogNanos = 0L;
        suppressedFailures = 0;
    }

    public static synchronized void report(
            String operation,
            RuntimeException exception) {
        if (exception == null) {
            return;
        }
        long now = System.nanoTime();
        if (logged && now - lastLogNanos < LOG_INTERVAL_NANOS) {
            if (suppressedFailures < Integer.MAX_VALUE) {
                suppressedFailures++;
            }
            return;
        }
        int suppressed = suppressedFailures;
        logged = true;
        lastLogNanos = now;
        suppressedFailures = 0;
        String context = operation == null || operation.isBlank()
                ? "unknown operation"
                : operation;
        if (suppressed == 0) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Unexpected Research Tree failure during {}; the request was rejected safely",
                    context,
                    exception);
        } else {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Unexpected Research Tree failure during {}; the request was rejected safely"
                            + " ({} similar failures suppressed)",
                    context,
                    suppressed,
                    exception);
        }
    }
}
