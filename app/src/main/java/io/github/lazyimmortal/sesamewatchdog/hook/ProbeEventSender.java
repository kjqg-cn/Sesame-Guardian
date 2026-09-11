package io.github.lazyimmortal.sesamewatchdog.hook;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

final class ProbeEventSender {
    private static volatile Context applicationContext;

    private ProbeEventSender() {
    }

    static void initialize(Context context) {
        if (context != null) {
            Context appContext = context.getApplicationContext();
            applicationContext = appContext == null ? context : appContext;
        }
    }

    static void sendHeartbeat(long heartbeatElapsedRealtime) {
        Intent intent = newProbeEvent(
                WatchdogContract.EVENT_HEARTBEAT, heartbeatElapsedRealtime);
        send(intent);
    }

    static void sendNotification(
            NotificationStatusParser.StatusKind status,
            long observedElapsedRealtime,
            Long nextExecutionElapsedRealtime,
            Long lastExecutionElapsedRealtime,
            Long lastExecutionWallClockMillis) {
        Intent intent = newProbeEvent(
                WatchdogContract.EVENT_NOTIFICATION, observedElapsedRealtime)
                .putExtra(WatchdogContract.EXTRA_NOTIFICATION_STATUS, status.name());
        if (nextExecutionElapsedRealtime != null) {
            intent.putExtra(
                    WatchdogContract.EXTRA_NEXT_EXECUTION_ELAPSED_REALTIME,
                    nextExecutionElapsedRealtime);
        }
        if (lastExecutionElapsedRealtime != null) {
            intent.putExtra(
                    WatchdogContract.EXTRA_LAST_EXECUTION_ELAPSED_REALTIME,
                    lastExecutionElapsedRealtime);
        }
        if (lastExecutionWallClockMillis != null) {
            intent.putExtra(
                    WatchdogContract.EXTRA_LAST_EXECUTION_WALL_CLOCK_MILLIS,
                    lastExecutionWallClockMillis);
        }
        send(intent);
    }

    static void sendActivityResumed(
            long observedElapsedRealtime,
            long foregroundRecoveryRequestId,
            String activityClass) {
        Intent intent = newProbeEvent(
                WatchdogContract.EVENT_ACTIVITY_RESUMED,
                observedElapsedRealtime)
                .putExtra(
                        WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID,
                        foregroundRecoveryRequestId)
                .putExtra(WatchdogContract.EXTRA_ACTIVITY_CLASS, activityClass);
        send(intent);
    }

    private static Intent newProbeEvent(int eventType, long eventElapsedRealtime) {
        return new Intent(WatchdogContract.ACTION_PROBE_EVENT)
                .setPackage(WatchdogContract.SYSTEM_UI_PACKAGE)
                .putExtra(
                        WatchdogContract.EXTRA_PROTOCOL_VERSION,
                        WatchdogContract.PROTOCOL_VERSION)
                .putExtra(
                        WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                        WatchdogContract.PROTOCOL_TOKEN)
                .putExtra(WatchdogContract.EXTRA_EVENT_TYPE, eventType)
                .putExtra(
                        WatchdogContract.EXTRA_SOURCE_PACKAGE,
                        WatchdogContract.ALIPAY_PACKAGE)
                .putExtra(WatchdogContract.EXTRA_PROCESS_ID, Process.myPid())
                .putExtra(
                        WatchdogContract.EXTRA_EVENT_ELAPSED_REALTIME,
                        eventElapsedRealtime);
    }

    private static void send(Intent intent) {
        Context context = applicationContext;
        if (context == null) {
            return;
        }
        try {
            context.sendBroadcast(intent);
        } catch (Throwable throwable) {
            XposedLog.logError("发送支付宝探针事件失败", throwable);
        }
    }
}
