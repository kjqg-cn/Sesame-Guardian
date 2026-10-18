package io.github.lazyimmortal.sesamewatchdog.config;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class WatchdogConfig {
    public static final int DEFAULT_STARTUP_GRACE_MINUTES = 15;
    public static final int DEFAULT_HEARTBEAT_TIMEOUT_MINUTES = 10;
    public static final int DEFAULT_NEXT_OVERDUE_MINUTES = 10;
    public static final int DEFAULT_RUNNING_TIMEOUT_MINUTES = 45;
    public static final int DEFAULT_EXECUTION_STALL_MINUTES = 60;
    public static final boolean DEFAULT_AUTO_SOFT_RECOVERY = false;
    public static final boolean DEFAULT_HARD_RECOVERY_ENABLED = false;
    public static final boolean DEFAULT_FOREGROUND_RECOVERY_ENABLED = true;
    public static final int DEFAULT_FOREGROUND_RECOVERY_INTERVAL_MINUTES = 5;
    public static final boolean DEFAULT_QUIET_HOURS_ENABLED = false;
    public static final int DEFAULT_QUIET_HOURS_START_HOUR = 1;
    public static final int DEFAULT_QUIET_HOURS_START_MINUTE = 0;
    public static final int DEFAULT_QUIET_HOURS_END_HOUR = 6;
    public static final int DEFAULT_QUIET_HOURS_END_MINUTE = 30;
    public static final boolean DEFAULT_FAST_SLEEP_ENABLED = true;
    public static final boolean DEFAULT_PROXIMITY_AVOIDANCE_ENABLED = true;

    private static final int MIN_THRESHOLD_MINUTES = 1;
    private static final int MAX_THRESHOLD_MINUTES = 24 * 60;

    private final boolean enabled;
    private final int startupGraceMinutes;
    private final int heartbeatTimeoutMinutes;
    private final int nextOverdueMinutes;
    private final int runningTimeoutMinutes;
    private final int executionStallMinutes;
    private final boolean autoSoftRecovery;
    private final boolean hardRecoveryEnabled;
    private final boolean foregroundRecoveryEnabled;
    private final int foregroundRecoveryIntervalMinutes;
    private final boolean quietHoursEnabled;
    private final int quietHoursStartHour;
    private final int quietHoursStartMinute;
    private final int quietHoursEndHour;
    private final int quietHoursEndMinute;
    private final boolean fastSleepEnabled;
    private final boolean proximityAvoidanceEnabled;

    private WatchdogConfig(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes,
            boolean autoSoftRecovery,
            boolean hardRecoveryEnabled,
            boolean foregroundRecoveryEnabled,
            int foregroundRecoveryIntervalMinutes,
            boolean quietHoursEnabled,
            int quietHoursStartHour,
            int quietHoursStartMinute,
            int quietHoursEndHour,
            int quietHoursEndMinute,
            boolean fastSleepEnabled,
            boolean proximityAvoidanceEnabled) {
        this.enabled = enabled;
        this.startupGraceMinutes = clampMinutes(startupGraceMinutes);
        this.heartbeatTimeoutMinutes = clampMinutes(heartbeatTimeoutMinutes);
        this.nextOverdueMinutes = clampMinutes(nextOverdueMinutes);
        this.runningTimeoutMinutes = clampMinutes(runningTimeoutMinutes);
        this.executionStallMinutes = clampMinutes(executionStallMinutes);
        this.autoSoftRecovery = autoSoftRecovery;
        this.hardRecoveryEnabled = hardRecoveryEnabled;
        this.foregroundRecoveryEnabled = foregroundRecoveryEnabled;
        this.foregroundRecoveryIntervalMinutes = clampMinutes(
                foregroundRecoveryIntervalMinutes);
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStartHour = clampHour(quietHoursStartHour);
        this.quietHoursStartMinute = clampMinute(quietHoursStartMinute);
        this.quietHoursEndHour = clampHour(quietHoursEndHour);
        this.quietHoursEndMinute = clampMinute(quietHoursEndMinute);
        this.fastSleepEnabled = fastSleepEnabled;
        this.proximityAvoidanceEnabled = proximityAvoidanceEnabled;
    }

    public static WatchdogConfig defaults() {
        return create(
                true,
                DEFAULT_STARTUP_GRACE_MINUTES,
                DEFAULT_HEARTBEAT_TIMEOUT_MINUTES,
                DEFAULT_NEXT_OVERDUE_MINUTES,
                DEFAULT_RUNNING_TIMEOUT_MINUTES,
                DEFAULT_EXECUTION_STALL_MINUTES,
                DEFAULT_AUTO_SOFT_RECOVERY,
                DEFAULT_HARD_RECOVERY_ENABLED,
                DEFAULT_FOREGROUND_RECOVERY_ENABLED,
                DEFAULT_FOREGROUND_RECOVERY_INTERVAL_MINUTES);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes) {
        return create(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                DEFAULT_EXECUTION_STALL_MINUTES,
                DEFAULT_AUTO_SOFT_RECOVERY,
                DEFAULT_HARD_RECOVERY_ENABLED);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes) {
        return create(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                executionStallMinutes,
                DEFAULT_AUTO_SOFT_RECOVERY,
                DEFAULT_HARD_RECOVERY_ENABLED);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes,
            boolean autoSoftRecovery) {
        return create(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                executionStallMinutes,
                autoSoftRecovery,
                DEFAULT_HARD_RECOVERY_ENABLED,
                DEFAULT_FOREGROUND_RECOVERY_ENABLED,
                DEFAULT_FOREGROUND_RECOVERY_INTERVAL_MINUTES);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes,
            boolean autoSoftRecovery,
            boolean hardRecoveryEnabled) {
        return create(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                executionStallMinutes,
                autoSoftRecovery,
                hardRecoveryEnabled,
                DEFAULT_FOREGROUND_RECOVERY_ENABLED,
                DEFAULT_FOREGROUND_RECOVERY_INTERVAL_MINUTES);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes,
            boolean autoSoftRecovery,
            boolean hardRecoveryEnabled,
            boolean foregroundRecoveryEnabled,
            int foregroundRecoveryIntervalMinutes) {
        return create(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                executionStallMinutes,
                autoSoftRecovery,
                hardRecoveryEnabled,
                foregroundRecoveryEnabled,
                foregroundRecoveryIntervalMinutes,
                DEFAULT_QUIET_HOURS_ENABLED,
                DEFAULT_QUIET_HOURS_START_HOUR,
                DEFAULT_QUIET_HOURS_START_MINUTE,
                DEFAULT_QUIET_HOURS_END_HOUR,
                DEFAULT_QUIET_HOURS_END_MINUTE);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes,
            boolean autoSoftRecovery,
            boolean hardRecoveryEnabled,
            boolean foregroundRecoveryEnabled,
            int foregroundRecoveryIntervalMinutes,
            boolean quietHoursEnabled,
            int quietHoursStartHour,
            int quietHoursStartMinute,
            int quietHoursEndHour,
            int quietHoursEndMinute) {
        return create(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                executionStallMinutes,
                autoSoftRecovery,
                hardRecoveryEnabled,
                foregroundRecoveryEnabled,
                foregroundRecoveryIntervalMinutes,
                quietHoursEnabled,
                quietHoursStartHour,
                quietHoursStartMinute,
                quietHoursEndHour,
                quietHoursEndMinute,
                DEFAULT_FAST_SLEEP_ENABLED);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes,
            boolean autoSoftRecovery,
            boolean hardRecoveryEnabled,
            boolean foregroundRecoveryEnabled,
            int foregroundRecoveryIntervalMinutes,
            boolean quietHoursEnabled,
            int quietHoursStartHour,
            int quietHoursStartMinute,
            int quietHoursEndHour,
            int quietHoursEndMinute,
            boolean fastSleepEnabled) {
        return create(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                executionStallMinutes,
                autoSoftRecovery,
                hardRecoveryEnabled,
                foregroundRecoveryEnabled,
                foregroundRecoveryIntervalMinutes,
                quietHoursEnabled,
                quietHoursStartHour,
                quietHoursStartMinute,
                quietHoursEndHour,
                quietHoursEndMinute,
                fastSleepEnabled,
                DEFAULT_PROXIMITY_AVOIDANCE_ENABLED);
    }

    public static WatchdogConfig create(
            boolean enabled,
            int startupGraceMinutes,
            int heartbeatTimeoutMinutes,
            int nextOverdueMinutes,
            int runningTimeoutMinutes,
            int executionStallMinutes,
            boolean autoSoftRecovery,
            boolean hardRecoveryEnabled,
            boolean foregroundRecoveryEnabled,
            int foregroundRecoveryIntervalMinutes,
            boolean quietHoursEnabled,
            int quietHoursStartHour,
            int quietHoursStartMinute,
            int quietHoursEndHour,
            int quietHoursEndMinute,
            boolean fastSleepEnabled,
            boolean proximityAvoidanceEnabled) {
        return new WatchdogConfig(
                enabled,
                startupGraceMinutes,
                heartbeatTimeoutMinutes,
                nextOverdueMinutes,
                runningTimeoutMinutes,
                executionStallMinutes,
                autoSoftRecovery,
                hardRecoveryEnabled,
                foregroundRecoveryEnabled,
                foregroundRecoveryIntervalMinutes,
                quietHoursEnabled,
                quietHoursStartHour,
                quietHoursStartMinute,
                quietHoursEndHour,
                quietHoursEndMinute,
                fastSleepEnabled,
                proximityAvoidanceEnabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFastSleepEnabled() {
        return fastSleepEnabled;
    }

    public boolean isProximityAvoidanceEnabled() {
        return proximityAvoidanceEnabled;
    }

    public int getStartupGraceMinutes() {
        return startupGraceMinutes;
    }

    public int getHeartbeatTimeoutMinutes() {
        return heartbeatTimeoutMinutes;
    }

    public int getNextOverdueMinutes() {
        return nextOverdueMinutes;
    }

    public int getRunningTimeoutMinutes() {
        return runningTimeoutMinutes;
    }

    public int getExecutionStallMinutes() {
        return executionStallMinutes;
    }

    public boolean isAutoSoftRecovery() {
        return autoSoftRecovery;
    }

    public boolean isHardRecoveryEnabled() {
        return hardRecoveryEnabled;
    }

    public boolean isForegroundRecoveryEnabled() {
        return foregroundRecoveryEnabled;
    }

    public int getForegroundRecoveryIntervalMinutes() {
        return foregroundRecoveryIntervalMinutes;
    }

    public boolean isQuietHoursEnabled() {
        return quietHoursEnabled;
    }

    public int getQuietHoursStartHour() {
        return quietHoursStartHour;
    }

    public int getQuietHoursStartMinute() {
        return quietHoursStartMinute;
    }

    public int getQuietHoursEndHour() {
        return quietHoursEndHour;
    }

    public int getQuietHoursEndMinute() {
        return quietHoursEndMinute;
    }

    public boolean isInQuietHours(long wallClockMillis) {
        if (!quietHoursEnabled) {
            return false;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(wallClockMillis);
        int currentMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        int startMinuteOfDay = quietHoursStartHour * 60 + quietHoursStartMinute;
        int endMinuteOfDay = quietHoursEndHour * 60 + quietHoursEndMinute;
        if (startMinuteOfDay == endMinuteOfDay) {
            return false;
        }
        if (startMinuteOfDay < endMinuteOfDay) {
            return currentMinuteOfDay >= startMinuteOfDay && currentMinuteOfDay < endMinuteOfDay;
        } else {
            return currentMinuteOfDay >= startMinuteOfDay || currentMinuteOfDay < endMinuteOfDay;
        }
    }

    public long getStartupGraceMillis() {
        return TimeUnit.MINUTES.toMillis(startupGraceMinutes);
    }

    public long getHeartbeatTimeoutMillis() {
        return TimeUnit.MINUTES.toMillis(heartbeatTimeoutMinutes);
    }

    public long getNextOverdueMillis() {
        return TimeUnit.MINUTES.toMillis(nextOverdueMinutes);
    }

    public long getRunningTimeoutMillis() {
        return TimeUnit.MINUTES.toMillis(runningTimeoutMinutes);
    }

    public long getExecutionStallMillis() {
        return TimeUnit.MINUTES.toMillis(executionStallMinutes);
    }

    public long getForegroundRecoveryIntervalMillis() {
        return TimeUnit.MINUTES.toMillis(foregroundRecoveryIntervalMinutes);
    }

    @Override
    public String toString() {
        return "enabled=" + enabled
                + ", startupGraceMinutes=" + startupGraceMinutes
                + ", heartbeatTimeoutMinutes=" + heartbeatTimeoutMinutes
                + ", nextOverdueMinutes=" + nextOverdueMinutes
                + ", runningTimeoutMinutes=" + runningTimeoutMinutes
                + ", executionStallMinutes=" + executionStallMinutes
                + ", autoSoftRecovery=" + autoSoftRecovery
                + ", hardRecoveryEnabled=" + hardRecoveryEnabled
                + ", foregroundRecoveryEnabled=" + foregroundRecoveryEnabled
                + ", foregroundRecoveryIntervalMinutes="
                + foregroundRecoveryIntervalMinutes
                + ", quietHoursEnabled=" + quietHoursEnabled
                + ", quietHours=" + String.format(java.util.Locale.getDefault(), "%02d:%02d-%02d:%02d",
                quietHoursStartHour, quietHoursStartMinute, quietHoursEndHour, quietHoursEndMinute)
                + ", fastSleepEnabled=" + fastSleepEnabled
                + ", proximityAvoidanceEnabled=" + proximityAvoidanceEnabled;
    }

    private static int clampMinutes(int value) {
        return Math.max(MIN_THRESHOLD_MINUTES, Math.min(MAX_THRESHOLD_MINUTES, value));
    }

    private static int clampHour(int value) {
        return Math.max(0, Math.min(23, value));
    }

    private static int clampMinute(int value) {
        return Math.max(0, Math.min(59, value));
    }
}
