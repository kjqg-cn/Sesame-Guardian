package io.github.lazyimmortal.sesamewatchdog.root;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class HardRecoveryThrottleTest {
    private static final long NOW = 10_000_000L;

    @Test
    public void allowsFirstAttempt() {
        assertEquals(
                HardRecoveryThrottle.Result.ALLOWED,
                HardRecoveryThrottle.evaluate(NOW, new long[3]));
    }

    @Test
    public void enforcesTwentyMinuteCooldown() {
        long lastAttempt = NOW - HardRecoveryThrottle.COOLDOWN_MILLIS + 1L;

        assertEquals(
                HardRecoveryThrottle.Result.COOLDOWN,
                HardRecoveryThrottle.evaluate(NOW, new long[]{lastAttempt, 0L, 0L}));
        assertEquals(
                HardRecoveryThrottle.Result.ALLOWED,
                HardRecoveryThrottle.evaluate(
                        NOW,
                        new long[]{NOW - HardRecoveryThrottle.COOLDOWN_MILLIS, 0L, 0L}));
    }

    @Test
    public void enforcesThreeAttemptsPerHour() {
        long[] attempts = {
                NOW - 59 * 60_000L,
                NOW - 40 * 60_000L,
                NOW - 21 * 60_000L
        };

        assertEquals(
                HardRecoveryThrottle.Result.HOURLY_LIMIT,
                HardRecoveryThrottle.evaluate(NOW, attempts));
    }

    @Test
    public void discardsAttemptsOutsideHourlyWindow() {
        long[] updated = HardRecoveryThrottle.append(
                NOW,
                new long[]{NOW - 61 * 60_000L, NOW - 30 * 60_000L, 0L});

        assertEquals(NOW - 30 * 60_000L, updated[0]);
        assertEquals(NOW, updated[1]);
        assertEquals(0L, updated[2]);
    }
}
