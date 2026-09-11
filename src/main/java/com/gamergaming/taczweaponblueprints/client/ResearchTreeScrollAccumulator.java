package com.gamergaming.taczweaponblueprints.client;

/** Retains subpixel wheel motion until it can move a discrete scroll position. */
final class ResearchTreeScrollAccumulator {
    private double remainder;
    int consume(double distance) {
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("non-finite scroll distance");
        remainder += distance;
        int whole = (int) remainder;
        remainder -= whole;
        return whole;
    }
    void reset() { remainder = 0; }
}
