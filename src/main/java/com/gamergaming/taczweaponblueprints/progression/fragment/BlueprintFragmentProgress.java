package com.gamergaming.taczweaponblueprints.progression.fragment;

/** Derived fragment-set progress; saved data continues to store only the raw count. */
public record BlueprintFragmentProgress(int archived, int threshold) {
    public BlueprintFragmentProgress {
        if (archived < 0
                || archived > BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS
                || threshold < 1
                || threshold > BlueprintFragmentPolicy.MAX_THRESHOLD) {
            throw new IllegalArgumentException("invalid blueprint fragment progress");
        }
    }

    public int completedSets() {
        return archived / threshold;
    }

    public int remainder() {
        return archived % threshold;
    }

    public int fragmentsToNextSet() {
        return remainder() == 0 && archived > 0 ? 0 : threshold - remainder();
    }

    public boolean hasCompleteSet() {
        return completedSets() > 0;
    }

    public BlueprintFragmentProgress consumeSets(int sets) {
        if (sets < 0 || sets > completedSets()) {
            throw new IllegalArgumentException("cannot consume unavailable fragment sets");
        }
        long consumed = (long) sets * (long) threshold;
        return new BlueprintFragmentProgress(Math.toIntExact(archived - consumed), threshold);
    }
}
