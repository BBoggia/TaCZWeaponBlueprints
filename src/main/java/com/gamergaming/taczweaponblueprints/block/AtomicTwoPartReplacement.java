package com.gamergaming.taczweaponblueprints.block;

/** Small rollback kernel for atomic two-block workstation placement. */
final class AtomicTwoPartReplacement {
    private AtomicTwoPartReplacement() {
    }

    static <S> Outcome replace(
            S sourceFirst,
            S sourceSecond,
            S targetFirst,
            S targetSecond,
            Access<S> access) {
        if (sourceFirst == null || sourceSecond == null
                || targetFirst == null || targetSecond == null || access == null) {
            throw new IllegalArgumentException("two-part replacement inputs cannot be null");
        }
        try {
            if (!writeAndVerify(access, Part.FIRST, targetFirst)
                    || !writeAndVerify(access, Part.SECOND, targetSecond)) {
                return restore(access, sourceFirst, sourceSecond);
            }
            return Outcome.SUCCESS;
        } catch (RuntimeException exception) {
            return restore(access, sourceFirst, sourceSecond);
        }
    }

    private static <S> boolean writeAndVerify(Access<S> access, Part part, S state) {
        access.write(part, state);
        return state.equals(access.read(part));
    }

    private static <S> Outcome restore(Access<S> access, S first, S second) {
        try {
            boolean firstRestored = writeAndVerify(access, Part.FIRST, first);
            boolean secondRestored = writeAndVerify(access, Part.SECOND, second);
            return firstRestored && secondRestored
                    ? Outcome.ROLLED_BACK
                    : Outcome.ROLLBACK_FAILED;
        } catch (RuntimeException exception) {
            return Outcome.ROLLBACK_FAILED;
        }
    }

    enum Part {
        FIRST,
        SECOND
    }

    enum Outcome {
        SUCCESS,
        ROLLED_BACK,
        ROLLBACK_FAILED
    }

    interface Access<S> {
        void write(Part part, S state);

        S read(Part part);
    }
}
