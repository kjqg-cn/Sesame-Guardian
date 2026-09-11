package io.github.lazyimmortal.sesamewatchdog.logging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

public final class DiagnosticLogReceiver extends BroadcastReceiver {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isValid(intent)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        Context safeContext = appContext == null ? context : appContext;
        long wallClockMillis = intent.getLongExtra(
                WatchdogContract.EXTRA_LOG_WALL_CLOCK_MILLIS,
                System.currentTimeMillis());
        String source = intent.getStringExtra(WatchdogContract.EXTRA_LOG_SOURCE);
        String message = intent.getStringExtra(WatchdogContract.EXTRA_LOG_MESSAGE);
        PendingResult pendingResult = goAsync();
        try {
            WRITER.execute(() -> {
                try {
                    DiagnosticLogStore.append(
                            safeContext, wallClockMillis, source, message);
                } catch (Throwable ignored) {
                    // Diagnostic logging must never affect the monitored processes.
                } finally {
                    pendingResult.finish();
                }
            });
        } catch (Throwable ignored) {
            pendingResult.finish();
        }
    }

    private static boolean isValid(Intent intent) {
        if (intent == null
                || !WatchdogContract.ACTION_DIAGNOSTIC_LOG.equals(intent.getAction())
                || intent.getIntExtra(WatchdogContract.EXTRA_PROTOCOL_VERSION, -1)
                != WatchdogContract.PROTOCOL_VERSION
                || !WatchdogContract.PROTOCOL_TOKEN.equals(
                intent.getStringExtra(WatchdogContract.EXTRA_PROTOCOL_TOKEN))) {
            return false;
        }
        String source = intent.getStringExtra(WatchdogContract.EXTRA_LOG_SOURCE);
        String message = intent.getStringExtra(WatchdogContract.EXTRA_LOG_MESSAGE);
        return "SystemUI".equals(source) && message != null;
    }
}
