package io.github.lazyimmortal.sesamewatchdog.ipc;

public final class WatchdogContract {
    public static final String MODULE_PACKAGE =
            "io.github.lazyimmortal.sesamewatchdog";
    public static final String DIAGNOSTIC_LOG_RECEIVER =
            MODULE_PACKAGE + ".logging.DiagnosticLogReceiver";
    public static final String ROOT_RECOVERY_RECEIVER =
            MODULE_PACKAGE + ".root.RootRecoveryReceiver";
    public static final String COMMAND_PROVIDER_AUTHORITY =
            MODULE_PACKAGE + ".commands";
    public static final String COMMAND_PROVIDER_URI =
            "content://" + COMMAND_PROVIDER_AUTHORITY;
    public static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    public static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";

    public static final String ACTION_PROBE_EVENT =
            "io.github.lazyimmortal.sesamewatchdog.action.PROBE_EVENT";
    public static final String ACTION_CONFIG_UPDATE =
            "io.github.lazyimmortal.sesamewatchdog.action.CONFIG_UPDATE";
    public static final String ACTION_DIAGNOSTIC_LOG =
            "io.github.lazyimmortal.sesamewatchdog.action.DIAGNOSTIC_LOG";
    public static final String ACTION_HARD_RECOVERY_REQUEST =
            "io.github.lazyimmortal.sesamewatchdog.action.HARD_RECOVERY_REQUEST";
    public static final String ACTION_HARD_RECOVERY_RESULT =
            "io.github.lazyimmortal.sesamewatchdog.action.HARD_RECOVERY_RESULT";
    public static final String ACTION_SESAME_EXECUTE =
            "com.eg.android.AlipayGphone.sesame.execute";
    public static final String ACTION_SESAME_RESTART =
            "com.eg.android.AlipayGphone.sesame.restart";

    public static final String METHOD_APPEND_DIAGNOSTIC_LOG =
            "appendDiagnosticLog";
    public static final String METHOD_REQUEST_HARD_RECOVERY =
            "requestHardRecovery";
    public static final String METHOD_BRING_ALIPAY_TO_FRONT =
            "bringAlipayToFront";
    public static final String METHOD_REQUEST_GO_TO_SLEEP =
            "requestGoToSleep";

    public static final String EXTRA_PROTOCOL_VERSION = "protocolVersion";
    public static final String EXTRA_PROTOCOL_TOKEN = "protocolToken";
    public static final String EXTRA_EVENT_TYPE = "eventType";
    public static final String EXTRA_SOURCE_PACKAGE = "sourcePackage";
    public static final String EXTRA_PROCESS_ID = "processId";
    public static final String EXTRA_EVENT_ELAPSED_REALTIME = "eventElapsedRealtime";
    public static final String EXTRA_NOTIFICATION_STATUS = "notificationStatus";
    public static final String EXTRA_NEXT_EXECUTION_ELAPSED_REALTIME =
            "nextExecutionElapsedRealtime";
    public static final String EXTRA_LAST_EXECUTION_ELAPSED_REALTIME =
            "lastExecutionElapsedRealtime";
    public static final String EXTRA_LAST_EXECUTION_WALL_CLOCK_MILLIS =
            "lastExecutionWallClockMillis";

    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_STARTUP_GRACE_MINUTES = "startupGraceMinutes";
    public static final String EXTRA_HEARTBEAT_TIMEOUT_MINUTES = "heartbeatTimeoutMinutes";
    public static final String EXTRA_NEXT_OVERDUE_MINUTES = "nextOverdueMinutes";
    public static final String EXTRA_RUNNING_TIMEOUT_MINUTES = "runningTimeoutMinutes";
    public static final String EXTRA_EXECUTION_STALL_MINUTES = "executionStallMinutes";
    public static final String EXTRA_AUTO_SOFT_RECOVERY = "autoSoftRecovery";
    public static final String EXTRA_HARD_RECOVERY_ENABLED = "hardRecoveryEnabled";
    public static final String EXTRA_FOREGROUND_RECOVERY_ENABLED =
            "foregroundRecoveryEnabled";
    public static final String EXTRA_FOREGROUND_RECOVERY_INTERVAL_MINUTES =
            "foregroundRecoveryIntervalMinutes";
    public static final String EXTRA_QUIET_HOURS_ENABLED = "quietHoursEnabled";
    public static final String EXTRA_QUIET_HOURS_START_HOUR = "quietHoursStartHour";
    public static final String EXTRA_QUIET_HOURS_START_MINUTE = "quietHoursStartMinute";
    public static final String EXTRA_QUIET_HOURS_END_HOUR = "quietHoursEndHour";
    public static final String EXTRA_QUIET_HOURS_END_MINUTE = "quietHoursEndMinute";
    public static final String EXTRA_FAST_SLEEP_ENABLED = "fastSleepEnabled";
    public static final String EXTRA_HARD_RECOVERY_REQUEST_ID = "hardRecoveryRequestId";
    public static final String EXTRA_TARGET_USER_ID = "targetUserId";
    public static final String EXTRA_HARD_RECOVERY_SUCCESS = "hardRecoverySuccess";
    public static final String EXTRA_HARD_RECOVERY_MESSAGE = "hardRecoveryMessage";
    public static final String EXTRA_FOREGROUND_RECOVERY =
            MODULE_PACKAGE + ".extra.FOREGROUND_RECOVERY";
    public static final String EXTRA_FOREGROUND_RECOVERY_REQUEST_ID =
            "foregroundRecoveryRequestId";
    public static final String EXTRA_WAKE_SCREEN = "wakeScreen";
    public static final String EXTRA_ACTIVITY_CLASS = "activityClass";
    public static final String EXTRA_LOG_SOURCE = "logSource";
    public static final String EXTRA_LOG_MESSAGE = "logMessage";
    public static final String EXTRA_LOG_WALL_CLOCK_MILLIS = "logWallClockMillis";
    public static final String EXTRA_COMMAND_ACCEPTED = "commandAccepted";
    public static final String EXTRA_COMMAND_MESSAGE = "commandMessage";

    public static final int PROTOCOL_VERSION = 1;
    public static final String PROTOCOL_TOKEN = "sesame-watchdog-observer-v1";

    public static final int EVENT_HEARTBEAT = 1;
    public static final int EVENT_NOTIFICATION = 2;
    public static final int EVENT_ACTIVITY_RESUMED = 3;

    private WatchdogContract() {
    }
}
