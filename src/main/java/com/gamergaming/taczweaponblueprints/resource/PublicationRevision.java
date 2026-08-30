package com.gamergaming.taczweaponblueprints.resource;

/** Shared non-zero generation counter for immutable runtime publications. */
public final class PublicationRevision {
    private PublicationRevision() {
    }

    /** Advances a publication generation and reserves zero for the empty state. */
    public static long next(long currentRevision) {
        if (currentRevision < 0L) {
            throw new IllegalArgumentException(
                    "Publication revision cannot be negative");
        }
        return currentRevision == Long.MAX_VALUE ? 1L : currentRevision + 1L;
    }
}
