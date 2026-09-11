package io.github.lazyimmortal.sesamewatchdog.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.XposedBridge;
import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

final class DiagnosticLogSender {
    private static volatile Context applicationContext;
    private static volatile String source;

    private DiagnosticLogSender() {
    }

    static void initialize(Context context, String logSource) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        applicationContext = appContext == null ? context : appContext;
        source = logSource;
    }

    static void send(String message) {
        Context context = applicationContext;
        String logSource = source;
        if (context == null || logSource == null || message == null) {
            return;
        }
        try {
            Bundle request = new Bundle();
            request.putInt(
                    WatchdogContract.EXTRA_PROTOCOL_VERSION,
                    WatchdogContract.PROTOCOL_VERSION);
            request.putString(
                    WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                    WatchdogContract.PROTOCOL_TOKEN);
            request.putString(WatchdogContract.EXTRA_LOG_SOURCE, logSource);
            request.putString(WatchdogContract.EXTRA_LOG_MESSAGE, message);
            request.putLong(
                    WatchdogContract.EXTRA_LOG_WALL_CLOCK_MILLIS,
                    System.currentTimeMillis());
            Bundle result = context.getContentResolver().call(
                    Uri.parse(WatchdogContract.COMMAND_PROVIDER_URI),
                    WatchdogContract.METHOD_APPEND_DIAGNOSTIC_LOG,
                    null,
                    request);
            if (result != null
                    && result.getBoolean(WatchdogContract.EXTRA_COMMAND_ACCEPTED, false)) {
                return;
            }

            Intent intent = new Intent(WatchdogContract.ACTION_DIAGNOSTIC_LOG)
                    .setComponent(new ComponentName(
                            WatchdogContract.MODULE_PACKAGE,
                            WatchdogContract.DIAGNOSTIC_LOG_RECEIVER))
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND
                            | Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra(
                            WatchdogContract.EXTRA_PROTOCOL_VERSION,
                            WatchdogContract.PROTOCOL_VERSION)
                    .putExtra(
                            WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                            WatchdogContract.PROTOCOL_TOKEN)
                    .putExtra(WatchdogContract.EXTRA_LOG_SOURCE, logSource)
                    .putExtra(WatchdogContract.EXTRA_LOG_MESSAGE, message)
                    .putExtra(
                            WatchdogContract.EXTRA_LOG_WALL_CLOCK_MILLIS,
                            request.getLong(WatchdogContract.EXTRA_LOG_WALL_CLOCK_MILLIS));
            context.sendBroadcast(intent);
        } catch (Throwable throwable) {
            XposedBridge.log("SesameWatchdog: 写入模块私有日志失败：" + throwable);
        }
    }
}
