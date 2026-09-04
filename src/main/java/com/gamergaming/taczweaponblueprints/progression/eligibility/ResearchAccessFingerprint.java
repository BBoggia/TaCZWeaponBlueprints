package com.gamergaming.taczweaponblueprints.progression.eligibility;

/** Opaque identity of the live workstation and route access evidence. */
public record ResearchAccessFingerprint(long high, long low) {
    public static final ResearchAccessFingerprint EMPTY = new ResearchAccessFingerprint(0L, 0L);

    public boolean present() {
        return high != 0L || low != 0L;
    }
}
