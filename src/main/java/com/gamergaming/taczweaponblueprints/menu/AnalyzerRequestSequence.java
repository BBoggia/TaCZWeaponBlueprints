package com.gamergaming.taczweaponblueprints.menu;

/** Constant-space replay rejection for one open menu. Exhaustion requires reopening. */
public final class AnalyzerRequestSequence {
    private int highest;
    public boolean accept(int requestId) {
        if (requestId <= highest) return false;
        highest = requestId;
        return true;
    }
}
