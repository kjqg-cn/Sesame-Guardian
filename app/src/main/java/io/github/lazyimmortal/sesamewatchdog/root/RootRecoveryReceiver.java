package io.github.lazyimmortal.sesamewatchdog.root;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

public final class RootRecoveryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isValidRequest(intent)) {
            return;
        }

        long requestId = intent.getLongExtra(
                WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID, 0L);
        int userId = intent.getIntExtra(WatchdogContract.EXTRA_TARGET_USER_ID, -1);
        PendingResult pendingResult = goAsync();
        RootRecoveryExecutor.submit(
                context,
                requestId,
                userId,
                pendingResult::finish);
    }

    private static boolean isValidRequest(Intent intent) {
        if (intent == null
                || !WatchdogContract.ACTION_HARD_RECOVERY_REQUEST.equals(intent.getAction())
                || intent.getIntExtra(WatchdogContract.EXTRA_PROTOCOL_VERSION, -1)
                != WatchdogContract.PROTOCOL_VERSION
                || !WatchdogContract.PROTOCOL_TOKEN.equals(
                intent.getStringExtra(WatchdogContract.EXTRA_PROTOCOL_TOKEN))
                || !WatchdogContract.SYSTEM_UI_PACKAGE.equals(
                intent.getStringExtra(WatchdogContract.EXTRA_SOURCE_PACKAGE))) {
            return false;
        }
        long requestId = intent.getLongExtra(
                WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID, 0L);
        int userId = intent.getIntExtra(WatchdogContract.EXTRA_TARGET_USER_ID, -1);
        return requestId > 0L && RootRecoveryExecutor.isValidUserId(userId);
    }
}
