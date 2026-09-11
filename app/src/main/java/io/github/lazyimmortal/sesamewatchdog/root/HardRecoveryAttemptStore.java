package io.github.lazyimmortal.sesamewatchdog.root;

import android.content.Context;
import android.content.SharedPreferences;

final class HardRecoveryAttemptStore {
    private static final String PREFERENCES_NAME = "sesame_watchdog_hard_recovery";
    private static final String KEY_ATTEMPT_PREFIX = "attempt_";

    private HardRecoveryAttemptStore() {
    }

    static synchronized HardRecoveryThrottle.Result tryAcquire(
            Context context,
            long nowWallClockMillis) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        long[] attempts = readAttempts(preferences);
        HardRecoveryThrottle.Result result = HardRecoveryThrottle.evaluate(
                nowWallClockMillis, attempts);
        if (result != HardRecoveryThrottle.Result.ALLOWED) {
            return result;
        }

        long[] updated = HardRecoveryThrottle.append(nowWallClockMillis, attempts);
        SharedPreferences.Editor editor = preferences.edit();
        for (int index = 0; index < updated.length; index++) {
            editor.putLong(KEY_ATTEMPT_PREFIX + index, updated[index]);
        }
        editor.commit();
        return result;
    }

    private static long[] readAttempts(SharedPreferences preferences) {
        long[] attempts = new long[HardRecoveryThrottle.MAX_ATTEMPTS_PER_HOUR];
        for (int index = 0; index < attempts.length; index++) {
            attempts[index] = preferences.getLong(KEY_ATTEMPT_PREFIX + index, 0L);
        }
        return attempts;
    }
}
