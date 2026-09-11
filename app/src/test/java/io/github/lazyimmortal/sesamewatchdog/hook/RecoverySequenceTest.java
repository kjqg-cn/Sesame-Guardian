package io.github.lazyimmortal.sesamewatchdog.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RecoverySequenceTest {
    @Test
    public void escalatesFromExecuteToRestartThenHardRecoveryAndExhausts() {
        RecoverySequence sequence = new RecoverySequence();

        assertTrue(sequence.start(10_000L, 5_000L, 20_000L));
        assertEquals(
                RecoverySequence.Decision.SEND_RESTART,
                sequence.onObservationTimeout(5_000L));
        assertEquals(RecoverySequence.Stage.WAITING_RESTART_RESULT, sequence.getStage());
        assertEquals(
                RecoverySequence.Decision.REQUEST_HARD_RECOVERY,
                sequence.onObservationTimeout(5_000L));
        assertEquals(
                RecoverySequence.Stage.WAITING_HARD_RECOVERY_RESULT,
                sequence.getStage());
        assertEquals(
                RecoverySequence.Decision.EXHAUSTED,
                sequence.onObservationTimeout(5_000L));
        assertEquals(RecoverySequence.Stage.EXHAUSTED, sequence.getStage());
    }

    @Test
    public void stopsImmediatelyWhenExecutionProgresses() {
        RecoverySequence sequence = new RecoverySequence();
        assertTrue(sequence.start(10_000L, 5_000L, 20_000L));

        assertEquals(
                RecoverySequence.Decision.SUCCEEDED,
                sequence.observeProgress(5_001L));
        assertEquals(RecoverySequence.Stage.IDLE, sequence.getStage());
    }

    @Test
    public void leavesExhaustedStateAfterNaturalRecovery() {
        RecoverySequence sequence = new RecoverySequence();
        assertTrue(sequence.start(10_000L, 5_000L, 20_000L));
        sequence.onObservationTimeout(5_000L);
        sequence.onObservationTimeout(5_000L);
        sequence.onObservationTimeout(5_000L);

        assertEquals(
                RecoverySequence.Decision.SUCCEEDED,
                sequence.observeProgress(5_001L));
        assertEquals(RecoverySequence.Stage.IDLE, sequence.getStage());
    }

    @Test
    public void respectsCooldownAfterSuccessfulAttempt() {
        RecoverySequence sequence = new RecoverySequence();
        assertTrue(sequence.start(10_000L, 5_000L, 20_000L));
        assertEquals(
                RecoverySequence.Decision.SUCCEEDED,
                sequence.observeProgress(6_000L));

        assertFalse(sequence.start(29_999L, 6_000L, 20_000L));
        assertTrue(sequence.start(30_000L, 6_000L, 20_000L));
    }

    @Test
    public void acceptsAttemptWithoutKnownExecutionBaseline() {
        RecoverySequence sequence = new RecoverySequence();

        assertTrue(sequence.start(10_000L, 0L, 20_000L));
        assertEquals(RecoverySequence.Stage.WAITING_EXECUTE_RESULT, sequence.getStage());
        assertEquals(RecoverySequence.Decision.NONE, sequence.observeProgress(1L));
        assertEquals(RecoverySequence.Decision.SUCCEEDED, sequence.observeProgress(2L));
    }

    @Test
    public void hardRecoveryDoesNotSucceedWithoutExecutionProgress() {
        RecoverySequence sequence = new RecoverySequence();
        assertTrue(sequence.start(10_000L, 5_000L, 20_000L));
        sequence.onObservationTimeout(5_000L);
        sequence.onObservationTimeout(5_000L);

        assertEquals(RecoverySequence.Decision.NONE, sequence.observeProgress(5_000L));
        assertEquals(
                RecoverySequence.Stage.WAITING_HARD_RECOVERY_RESULT,
                sequence.getStage());
    }

    @Test
    public void failedHardRecoveryCanRetryAfterCooldown() {
        RecoverySequence sequence = new RecoverySequence();
        assertTrue(sequence.start(10_000L, 5_000L, 20_000L));
        sequence.onObservationTimeout(5_000L);
        sequence.onObservationTimeout(5_000L);
        assertEquals(
                RecoverySequence.Decision.EXHAUSTED,
                sequence.onObservationTimeout(5_000L));

        sequence.finishFailedAttempt();

        assertEquals(RecoverySequence.Stage.IDLE, sequence.getStage());
        assertEquals(0L, sequence.getBaselineLastExecutionElapsedRealtime());
        assertFalse(sequence.start(29_999L, 5_000L, 20_000L));
        assertTrue(sequence.start(30_000L, 5_000L, 20_000L));
    }
}
