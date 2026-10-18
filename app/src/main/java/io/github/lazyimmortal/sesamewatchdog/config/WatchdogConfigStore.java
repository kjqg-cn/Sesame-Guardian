package io.github.lazyimmortal.sesamewatchdog.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

public final class WatchdogConfigStore {
    private static final String PREFERENCES_NAME = "sesame_watchdog_config";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STARTUP_GRACE_MINUTES = "startupGraceMinutes";
    private static final String KEY_HEARTBEAT_TIMEOUT_MINUTES = "heartbeatTimeoutMinutes";
    private static final String KEY_NEXT_OVERDUE_MINUTES = "nextOverdueMinutes";
    private static final String KEY_RUNNING_TIMEOUT_MINUTES = "runningTimeoutMinutes";
    private static final String KEY_EXECUTION_STALL_MINUTES = "executionStallMinutes";
    private static final String KEY_AUTO_SOFT_RECOVERY = "autoSoftRecovery";
    private static final String KEY_HARD_RECOVERY_ENABLED = "hardRecoveryEnabled";
    private static final String KEY_FOREGROUND_RECOVERY_ENABLED =
            "foregroundRecoveryEnabled";
    private static final String KEY_FOREGROUND_RECOVERY_INTERVAL_MINUTES =
            "foregroundRecoveryIntervalMinutes";
    private static final String KEY_QUIET_HOURS_ENABLED = "quietHoursEnabled";
    private static final String KEY_QUIET_HOURS_START_HOUR = "quietHoursStartHour";
    private static final String KEY_QUIET_HOURS_START_MINUTE = "quietHoursStartMinute";
    private static final String KEY_QUIET_HOURS_END_HOUR = "quietHoursEndHour";
    private static final String KEY_QUIET_HOURS_END_MINUTE = "quietHoursEndMinute";
    private static final String KEY_FAST_SLEEP_ENABLED = "fastSleepEnabled";
    private static final String KEY_POLICY_VERSION = "policyVersion";
    private static final int RECOMMENDED_POLICY_VERSION = 2;

    private WatchdogConfigStore() {
    }

    public static WatchdogConfig load(Context context) {
        WatchdogConfig defaults = WatchdogConfig.defaults();
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean migrateRecoveryPolicy = preferences.getInt(
                KEY_POLICY_VERSION, 0) < RECOMMENDED_POLICY_VERSION;
        WatchdogConfig config = WatchdogConfig.create(
                preferences.getBoolean(KEY_ENABLED, defaults.isEnabled()),
                preferences.getInt(
                        KEY_STARTUP_GRACE_MINUTES, defaults.getStartupGraceMinutes()),
                preferences.getInt(
                        KEY_HEARTBEAT_TIMEOUT_MINUTES, defaults.getHeartbeatTimeoutMinutes()),
                preferences.getInt(
                        KEY_NEXT_OVERDUE_MINUTES, defaults.getNextOverdueMinutes()),
                preferences.getInt(
                        KEY_RUNNING_TIMEOUT_MINUTES, defaults.getRunningTimeoutMinutes()),
                migrateRecoveryPolicy
                        ? WatchdogConfig.DEFAULT_EXECUTION_STALL_MINUTES
                        : preferences.getInt(
                        KEY_EXECUTION_STALL_MINUTES, defaults.getExecutionStallMinutes()),
                migrateRecoveryPolicy
                        ? false
                        : preferences.getBoolean(
                        KEY_AUTO_SOFT_RECOVERY, defaults.isAutoSoftRecovery()),
                migrateRecoveryPolicy
                        ? false
                        : preferences.getBoolean(
                        KEY_HARD_RECOVERY_ENABLED, defaults.isHardRecoveryEnabled()),
                preferences.getBoolean(
                        KEY_FOREGROUND_RECOVERY_ENABLED,
                        defaults.isForegroundRecoveryEnabled()),
                preferences.getInt(
                        KEY_FOREGROUND_RECOVERY_INTERVAL_MINUTES,
                        defaults.getForegroundRecoveryIntervalMinutes()),
                preferences.getBoolean(
                        KEY_QUIET_HOURS_ENABLED,
                        defaults.isQuietHoursEnabled()),
                preferences.getInt(
                        KEY_QUIET_HOURS_START_HOUR,
                        defaults.getQuietHoursStartHour()),
                preferences.getInt(
                        KEY_QUIET_HOURS_START_MINUTE,
                        defaults.getQuietHoursStartMinute()),
                preferences.getInt(
                        KEY_QUIET_HOURS_END_HOUR,
                        defaults.getQuietHoursEndHour()),
                preferences.getInt(
                        KEY_QUIET_HOURS_END_MINUTE,
                        defaults.getQuietHoursEndMinute()),
                preferences.getBoolean(
                        KEY_FAST_SLEEP_ENABLED,
                        defaults.isFastSleepEnabled()));
        if (migrateRecoveryPolicy) {
            save(context, config);
        }
        return config;
    }

    public static void save(Context context, WatchdogConfig config) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, config.isEnabled())
                .putInt(KEY_STARTUP_GRACE_MINUTES, config.getStartupGraceMinutes())
                .putInt(KEY_HEARTBEAT_TIMEOUT_MINUTES, config.getHeartbeatTimeoutMinutes())
                .putInt(KEY_NEXT_OVERDUE_MINUTES, config.getNextOverdueMinutes())
                .putInt(KEY_RUNNING_TIMEOUT_MINUTES, config.getRunningTimeoutMinutes())
                .putInt(KEY_EXECUTION_STALL_MINUTES, config.getExecutionStallMinutes())
                .putBoolean(KEY_AUTO_SOFT_RECOVERY, config.isAutoSoftRecovery())
                .putBoolean(KEY_HARD_RECOVERY_ENABLED, config.isHardRecoveryEnabled())
                .putBoolean(
                        KEY_FOREGROUND_RECOVERY_ENABLED,
                        config.isForegroundRecoveryEnabled())
                .putInt(
                        KEY_FOREGROUND_RECOVERY_INTERVAL_MINUTES,
                        config.getForegroundRecoveryIntervalMinutes())
                .putBoolean(KEY_QUIET_HOURS_ENABLED, config.isQuietHoursEnabled())
                .putInt(KEY_QUIET_HOURS_START_HOUR, config.getQuietHoursStartHour())
                .putInt(KEY_QUIET_HOURS_START_MINUTE, config.getQuietHoursStartMinute())
                .putInt(KEY_QUIET_HOURS_END_HOUR, config.getQuietHoursEndHour())
                .putInt(KEY_QUIET_HOURS_END_MINUTE, config.getQuietHoursEndMinute())
                .putBoolean(KEY_FAST_SLEEP_ENABLED, config.isFastSleepEnabled())
                .putInt(KEY_POLICY_VERSION, RECOMMENDED_POLICY_VERSION)
                .apply();
    }

    public static WatchdogConfig fromIntent(Intent intent) {
        WatchdogConfig defaults = WatchdogConfig.defaults();
        return WatchdogConfig.create(
                intent.getBooleanExtra(WatchdogContract.EXTRA_ENABLED, defaults.isEnabled()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_STARTUP_GRACE_MINUTES,
                        defaults.getStartupGraceMinutes()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_HEARTBEAT_TIMEOUT_MINUTES,
                        defaults.getHeartbeatTimeoutMinutes()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_NEXT_OVERDUE_MINUTES,
                        defaults.getNextOverdueMinutes()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_RUNNING_TIMEOUT_MINUTES,
                        defaults.getRunningTimeoutMinutes()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_EXECUTION_STALL_MINUTES,
                        defaults.getExecutionStallMinutes()),
                intent.getBooleanExtra(
                        WatchdogContract.EXTRA_AUTO_SOFT_RECOVERY,
                        defaults.isAutoSoftRecovery()),
                intent.getBooleanExtra(
                        WatchdogContract.EXTRA_HARD_RECOVERY_ENABLED,
                        defaults.isHardRecoveryEnabled()),
                intent.getBooleanExtra(
                        WatchdogContract.EXTRA_FOREGROUND_RECOVERY_ENABLED,
                        defaults.isForegroundRecoveryEnabled()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_FOREGROUND_RECOVERY_INTERVAL_MINUTES,
                        defaults.getForegroundRecoveryIntervalMinutes()),
                intent.getBooleanExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_ENABLED,
                        defaults.isQuietHoursEnabled()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_START_HOUR,
                        defaults.getQuietHoursStartHour()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_START_MINUTE,
                        defaults.getQuietHoursStartMinute()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_END_HOUR,
                        defaults.getQuietHoursEndHour()),
                intent.getIntExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_END_MINUTE,
                        defaults.getQuietHoursEndMinute()),
                intent.getBooleanExtra(
                        WatchdogContract.EXTRA_FAST_SLEEP_ENABLED,
                        defaults.isFastSleepEnabled()));
    }

    public static void sendToSystemUi(Context context, WatchdogConfig config) {
        Intent intent = new Intent(WatchdogContract.ACTION_CONFIG_UPDATE)
                .setPackage(WatchdogContract.SYSTEM_UI_PACKAGE)
                .putExtra(
                WatchdogContract.EXTRA_PROTOCOL_VERSION,
                WatchdogContract.PROTOCOL_VERSION)
                .putExtra(
                        WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                        WatchdogContract.PROTOCOL_TOKEN)
                .putExtra(WatchdogContract.EXTRA_ENABLED, config.isEnabled())
                .putExtra(
                        WatchdogContract.EXTRA_STARTUP_GRACE_MINUTES,
                        config.getStartupGraceMinutes())
                .putExtra(
                        WatchdogContract.EXTRA_HEARTBEAT_TIMEOUT_MINUTES,
                        config.getHeartbeatTimeoutMinutes())
                .putExtra(
                        WatchdogContract.EXTRA_NEXT_OVERDUE_MINUTES,
                        config.getNextOverdueMinutes())
                .putExtra(
                        WatchdogContract.EXTRA_RUNNING_TIMEOUT_MINUTES,
                        config.getRunningTimeoutMinutes())
                .putExtra(
                        WatchdogContract.EXTRA_EXECUTION_STALL_MINUTES,
                        config.getExecutionStallMinutes())
                .putExtra(
                        WatchdogContract.EXTRA_AUTO_SOFT_RECOVERY,
                        config.isAutoSoftRecovery())
                .putExtra(
                        WatchdogContract.EXTRA_HARD_RECOVERY_ENABLED,
                        config.isHardRecoveryEnabled())
                .putExtra(
                        WatchdogContract.EXTRA_FOREGROUND_RECOVERY_ENABLED,
                        config.isForegroundRecoveryEnabled())
                .putExtra(
                        WatchdogContract.EXTRA_FOREGROUND_RECOVERY_INTERVAL_MINUTES,
                        config.getForegroundRecoveryIntervalMinutes())
                .putExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_ENABLED,
                        config.isQuietHoursEnabled())
                .putExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_START_HOUR,
                        config.getQuietHoursStartHour())
                .putExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_START_MINUTE,
                        config.getQuietHoursStartMinute())
                .putExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_END_HOUR,
                        config.getQuietHoursEndHour())
                .putExtra(
                        WatchdogContract.EXTRA_QUIET_HOURS_END_MINUTE,
                        config.getQuietHoursEndMinute())
                .putExtra(
                        WatchdogContract.EXTRA_FAST_SLEEP_ENABLED,
                        config.isFastSleepEnabled());
        context.sendBroadcast(intent);
    }
}
