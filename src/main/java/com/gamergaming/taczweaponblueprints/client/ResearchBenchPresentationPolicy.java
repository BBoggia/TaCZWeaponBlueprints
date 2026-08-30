package com.gamergaming.taczweaponblueprints.client;

/** Player-facing boundary for the research-only Research Bench. */
public final class ResearchBenchPresentationPolicy {
    private ResearchBenchPresentationPolicy() {
    }

    public static boolean permanentFullscreen() {
        return true;
    }

    public static ExitAction fullscreenExitAction() {
        return ExitAction.CLOSE_SCREEN;
    }

    public enum ExitAction {
        CLOSE_SCREEN
    }
}
