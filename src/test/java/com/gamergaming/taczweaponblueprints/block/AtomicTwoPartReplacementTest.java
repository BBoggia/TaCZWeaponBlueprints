package com.gamergaming.taczweaponblueprints.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AtomicTwoPartReplacementTest {
    @Test
    void successfulReplacementCommitsBothTargetStates() {
        FakeAccess access = new FakeAccess("source-root", "source-extension", Failure.NONE);

        AtomicTwoPartReplacement.Outcome outcome = AtomicTwoPartReplacement.replace(
                "source-root",
                "source-extension",
                "target-root",
                "target-extension",
                access);

        assertEquals(AtomicTwoPartReplacement.Outcome.SUCCESS, outcome);
        assertEquals("target-root", access.read(AtomicTwoPartReplacement.Part.FIRST));
        assertEquals("target-extension", access.read(AtomicTwoPartReplacement.Part.SECOND));
    }

    @Test
    void failedSecondWriteRestoresBothCapturedSourceStates() {
        FakeAccess access = new FakeAccess(
                "source-root", "source-extension", Failure.CORRUPT_TARGET_SECOND_ONCE);

        AtomicTwoPartReplacement.Outcome outcome = AtomicTwoPartReplacement.replace(
                "source-root",
                "source-extension",
                "target-root",
                "target-extension",
                access);

        assertEquals(AtomicTwoPartReplacement.Outcome.ROLLED_BACK, outcome);
        assertEquals("source-root", access.read(AtomicTwoPartReplacement.Part.FIRST));
        assertEquals("source-extension", access.read(AtomicTwoPartReplacement.Part.SECOND));
        assertEquals(4, access.writeCount);
    }

    @Test
    void rollbackFailureIsExplicitAndNeverReportedAsSuccess() {
        FakeAccess access = new FakeAccess(
                "source-root", "source-extension", Failure.CORRUPT_TARGET_AND_ROLLBACK_SECOND);

        AtomicTwoPartReplacement.Outcome outcome = AtomicTwoPartReplacement.replace(
                "source-root",
                "source-extension",
                "target-root",
                "target-extension",
                access);

        assertEquals(AtomicTwoPartReplacement.Outcome.ROLLBACK_FAILED, outcome);
        assertEquals("source-root", access.read(AtomicTwoPartReplacement.Part.FIRST));
        assertEquals("corrupt", access.read(AtomicTwoPartReplacement.Part.SECOND));
    }

    @Test
    void replacementRejectsMissingInputsBeforeWriting() {
        FakeAccess access = new FakeAccess("source-root", "source-extension", Failure.NONE);

        assertThrows(IllegalArgumentException.class, () -> AtomicTwoPartReplacement.replace(
                null,
                "source-extension",
                "target-root",
                "target-extension",
                access));
        assertEquals(0, access.writeCount);
    }

    private enum Failure {
        NONE,
        CORRUPT_TARGET_SECOND_ONCE,
        CORRUPT_TARGET_AND_ROLLBACK_SECOND
    }

    private static final class FakeAccess implements AtomicTwoPartReplacement.Access<String> {
        private final Map<AtomicTwoPartReplacement.Part, String> states =
                new EnumMap<>(AtomicTwoPartReplacement.Part.class);
        private final Failure failure;
        private int writeCount;

        private FakeAccess(String first, String second, Failure failure) {
            states.put(AtomicTwoPartReplacement.Part.FIRST, first);
            states.put(AtomicTwoPartReplacement.Part.SECOND, second);
            this.failure = failure;
        }

        @Override
        public void write(AtomicTwoPartReplacement.Part part, String state) {
            writeCount++;
            if (part == AtomicTwoPartReplacement.Part.SECOND && writeCount == 2
                    && failure != Failure.NONE) {
                states.put(part, "corrupt");
                return;
            }
            if (part == AtomicTwoPartReplacement.Part.SECOND && writeCount == 4
                    && failure == Failure.CORRUPT_TARGET_AND_ROLLBACK_SECOND) {
                return;
            }
            states.put(part, state);
        }

        @Override
        public String read(AtomicTwoPartReplacement.Part part) {
            return states.get(part);
        }
    }
}
