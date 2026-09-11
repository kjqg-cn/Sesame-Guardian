package io.github.lazyimmortal.sesamewatchdog.root;

final class HardRecoveryThrottle {
    static final long COOLDOWN_MILLIS = 20 * 60_000L;
    static final long HOURLY_WINDOW_MILLIS = 60 * 60_000L;
    static final int MAX_ATTEMPTS_PER_HOUR = 3;

    enum Result {
        ALLOWED,
        COOLDOWN,
        HOURLY_LIMIT
    }

    private HardRecoveryThrottle() {
    }

    static Result evaluate(long nowWallClockMillis, long[] attemptWallClockMillis) {
        long newestAttempt = 0L;
        int attemptsInWindow = 0;
        for (long attempt : attemptWallClockMillis) {
            if (attempt <= 0L) {
                continue;
            }
            long age = nowWallClockMillis - attempt;
            if (age < COOLDOWN_MILLIS && attempt > newestAttempt) {
                newestAttempt = attempt;
            }
            if (age < HOURLY_WINDOW_MILLIS) {
                attemptsInWindow++;
            }
        }
        if (newestAttempt != 0L) {
            return Result.COOLDOWN;
        }
        return attemptsInWindow >= MAX_ATTEMPTS_PER_HOUR
                ? Result.HOURLY_LIMIT
                : Result.ALLOWED;
    }

    static long[] append(long nowWallClockMillis, long[] attemptWallClockMillis) {
        long[] updated = new long[MAX_ATTEMPTS_PER_HOUR];
        int index = 0;
        for (long attempt : attemptWallClockMillis) {
            if (attempt > 0L
                    && nowWallClockMillis - attempt < HOURLY_WINDOW_MILLIS
                    && index < MAX_ATTEMPTS_PER_HOUR - 1) {
                updated[index++] = attempt;
            }
        }
        updated[index] = nowWallClockMillis;
        return updated;
    }
}
