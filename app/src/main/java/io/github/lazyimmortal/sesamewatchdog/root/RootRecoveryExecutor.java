package io.github.lazyimmortal.sesamewatchdog.root;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;
import io.github.lazyimmortal.sesamewatchdog.logging.DiagnosticLogStore;

public final class RootRecoveryExecutor {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int MAX_USER_ID = 999;

    private RootRecoveryExecutor() {
    }

    public static boolean submit(
            Context context,
            long requestId,
            int userId,
            Runnable completion) {
        if (context == null || requestId <= 0L || !isValidUserId(userId)) {
            finish(completion);
            return false;
        }
        Context appContext = context.getApplicationContext();
        Context safeContext = appContext == null ? context : appContext;
        try {
            EXECUTOR.execute(() -> {
                try {
                    execute(safeContext, requestId, userId);
                } finally {
                    finish(completion);
                }
            });
            return true;
        } catch (Throwable throwable) {
            finish(completion);
            return false;
        }
    }

    public static boolean execute(Context context, long requestId, int userId) {
        return execute(context, requestId, userId, false, true);
    }

    public static boolean execute(
            Context context,
            long requestId,
            int userId,
            boolean wakeScreen) {
        return execute(context, requestId, userId, wakeScreen, true);
    }

    public static boolean execute(
            Context context,
            long requestId,
            int userId,
            boolean wakeScreen,
            boolean fastSleepEnabled) {
        if (context == null || requestId <= 0L || !isValidUserId(userId)) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        Context safeContext = appContext == null ? context : appContext;
        try {
            runRecovery(safeContext, requestId, userId, wakeScreen, fastSleepEnabled);
        } catch (Throwable throwable) {
            String message = "root 硬恢复执行器异常：" + throwable;
            appendDiagnostic(safeContext, message);
            sendResult(safeContext, requestId, false, message);
        }
        return true;
    }

    public static boolean bringAlipayToFront(
            Context context,
            long requestId,
            int userId,
            boolean wakeScreen) {
        return bringAlipayToFront(context, requestId, userId, wakeScreen, true);
    }

    public static boolean bringAlipayToFront(
            Context context,
            long requestId,
            int userId,
            boolean wakeScreen,
            boolean fastSleepEnabled) {
        if (context == null || requestId <= 0L || !isValidUserId(userId)) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        Context safeContext = appContext == null ? context : appContext;
        RootCommandRunner.Result result = RootCommandRunner.bringAlipayToFront(
                userId,
                requestId,
                wakeScreen,
                fastSleepEnabled);
        String message = result.isSuccess()
                ? "root 前台拉起命令执行成功"
                : "root 前台拉起命令执行失败：" + result.getOutput();
        appendDiagnostic(safeContext, message);
        return result.isSuccess();
    }

    public static boolean goToSleep(Context context) {
        RootCommandRunner.Result result = RootCommandRunner.goToSleep();
        if (context != null) {
            appendDiagnostic(
                    context,
                    "Root 降级快速熄屏命令执行结果=" + (result.isSuccess() ? "成功" : "失败: " + result.getOutput()));
        }
        return result.isSuccess();
    }

    public static boolean isValidUserId(int userId) {
        return userId >= 0 && userId <= MAX_USER_ID;
    }

    private static void runRecovery(
            Context context,
            long requestId,
            int userId,
            boolean wakeScreen,
            boolean fastSleepEnabled) {
        HardRecoveryThrottle.Result throttleResult = HardRecoveryAttemptStore.tryAcquire(
                context, System.currentTimeMillis());
        if (throttleResult != HardRecoveryThrottle.Result.ALLOWED) {
            String message = throttleMessage(throttleResult);
            appendDiagnostic(context, message);
            sendResult(context, requestId, false, message);
            return;
        }

        appendDiagnostic(context, "开始 root 硬恢复，支付宝用户=" + userId);
        RootCommandRunner.Result result = RootCommandRunner.recoverAlipay(
                userId,
                wakeScreen,
                requestId,
                fastSleepEnabled);
        String message = result.isSuccess()
                ? "root 硬恢复命令执行成功，已强制停止支付宝并启动登录页"
                : "root 硬恢复命令执行失败：" + result.getOutput();
        appendDiagnostic(context, message);
        sendResult(context, requestId, result.isSuccess(), message);
    }

    private static String throttleMessage(HardRecoveryThrottle.Result result) {
        if (result == HardRecoveryThrottle.Result.COOLDOWN) {
            return "已拒绝 root 硬恢复：距上次硬恢复不足 20 分钟";
        }
        return "已拒绝 root 硬恢复：最近一小时已达到 3 次上限";
    }

    private static void appendDiagnostic(Context context, String message) {
        try {
            DiagnosticLogStore.append(
                    context,
                    System.currentTimeMillis(),
                    "Root执行器",
                    message);
        } catch (Throwable ignored) {
            // Root recovery must not depend on diagnostic storage.
        }
    }

    private static void sendResult(
            Context context,
            long requestId,
            boolean success,
            String message) {
        try {
            Intent result = new Intent(WatchdogContract.ACTION_HARD_RECOVERY_RESULT)
                    .setPackage(WatchdogContract.SYSTEM_UI_PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(
                            WatchdogContract.EXTRA_PROTOCOL_VERSION,
                            WatchdogContract.PROTOCOL_VERSION)
                    .putExtra(
                            WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                            WatchdogContract.PROTOCOL_TOKEN)
                    .putExtra(
                            WatchdogContract.EXTRA_SOURCE_PACKAGE,
                            WatchdogContract.MODULE_PACKAGE)
                    .putExtra(WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID, requestId)
                    .putExtra(WatchdogContract.EXTRA_HARD_RECOVERY_SUCCESS, success)
                    .putExtra(WatchdogContract.EXTRA_HARD_RECOVERY_MESSAGE, message);
            context.sendBroadcast(result);
        } catch (Throwable ignored) {
            // SystemUI may restart while root recovery is completing.
        }
    }

    private static void finish(Runnable completion) {
        if (completion == null) {
            return;
        }
        try {
            completion.run();
        } catch (Throwable ignored) {
            // Completion is best effort.
        }
    }
}
