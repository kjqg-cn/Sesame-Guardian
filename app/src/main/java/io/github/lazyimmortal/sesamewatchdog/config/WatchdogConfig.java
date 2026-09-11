package io.github.lazyimmortal.sesamewatchdog.config;

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
            int foregroundRecoveryIntervalMinutes) {
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
                foregroundRecoveryIntervalMinutes);
    }

    public boolean isEnabled() {
        return enabled;
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
                + foregroundRecoveryIntervalMinutes;
    }

    private static int clampMinutes(int value) {
        return Math.max(MIN_THRESHOLD_MINUTES, Math.min(MAX_THRESHOLD_MINUTES, value));
    }
}
