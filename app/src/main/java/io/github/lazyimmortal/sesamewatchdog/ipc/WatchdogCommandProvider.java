package io.github.lazyimmortal.sesamewatchdog.ipc;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import io.github.lazyimmortal.sesamewatchdog.logging.DiagnosticLogStore;
import io.github.lazyimmortal.sesamewatchdog.root.RootRecoveryExecutor;

public final class WatchdogCommandProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID || !isValidProtocol(extras)) {
            return result(false, "调用方或协议无效");
        }
        io.github.lazyimmortal.sesamewatchdog.ui.ModuleActivationStatus.recordSystemUiInteraction(getContext());
        try {
            if (WatchdogContract.METHOD_APPEND_DIAGNOSTIC_LOG.equals(method)) {
                return appendDiagnosticLog(extras);
            }
            if (WatchdogContract.METHOD_REQUEST_HARD_RECOVERY.equals(method)) {
                return requestHardRecovery(extras);
            }
            if (WatchdogContract.METHOD_BRING_ALIPAY_TO_FRONT.equals(method)) {
                return bringAlipayToFront(extras);
            }
            return result(false, "未知命令");
        } catch (Throwable throwable) {
            return result(false, "命令执行异常：" + throwable);
        }
    }

    private Bundle appendDiagnosticLog(Bundle extras) throws Exception {
        String source = extras.getString(WatchdogContract.EXTRA_LOG_SOURCE);
        String message = extras.getString(WatchdogContract.EXTRA_LOG_MESSAGE);
        if (!"SystemUI".equals(source) || message == null || getContext() == null) {
            return result(false, "日志参数无效");
        }
        DiagnosticLogStore.append(
                getContext(),
                extras.getLong(
                        WatchdogContract.EXTRA_LOG_WALL_CLOCK_MILLIS,
                        System.currentTimeMillis()),
                source,
                message);
        return result(true, "日志已写入");
    }

    private Bundle requestHardRecovery(Bundle extras) {
        if (!WatchdogContract.SYSTEM_UI_PACKAGE.equals(
                extras.getString(WatchdogContract.EXTRA_SOURCE_PACKAGE))) {
            return result(false, "恢复请求来源无效");
        }
        long requestId = extras.getLong(
                WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID, 0L);
        int userId = extras.getInt(WatchdogContract.EXTRA_TARGET_USER_ID, -1);
        boolean wakeScreen = extras.getBoolean(WatchdogContract.EXTRA_WAKE_SCREEN, false);
        if (requestId <= 0L || !RootRecoveryExecutor.isValidUserId(userId)
                || getContext() == null) {
            return result(false, "恢复请求参数无效");
        }
        boolean accepted = RootRecoveryExecutor.execute(
                getContext(), requestId, userId, wakeScreen);
        return result(
                accepted,
                accepted ? "恢复请求已提交" : "恢复请求提交失败");
    }

    private Bundle bringAlipayToFront(Bundle extras) {
        if (!WatchdogContract.SYSTEM_UI_PACKAGE.equals(
                extras.getString(WatchdogContract.EXTRA_SOURCE_PACKAGE))) {
            return result(false, "前台拉起请求来源无效");
        }
        long requestId = extras.getLong(
                WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID, 0L);
        int userId = extras.getInt(WatchdogContract.EXTRA_TARGET_USER_ID, -1);
        boolean wakeScreen = extras.getBoolean(WatchdogContract.EXTRA_WAKE_SCREEN, false);
        if (requestId <= 0L || !RootRecoveryExecutor.isValidUserId(userId)
                || getContext() == null) {
            return result(false, "前台拉起请求参数无效");
        }
        boolean accepted = RootRecoveryExecutor.bringAlipayToFront(
                getContext(),
                requestId,
                userId,
                wakeScreen);
        return result(
                accepted,
                accepted ? "前台拉起命令执行成功" : "前台拉起命令执行失败");
    }

    private static boolean isValidProtocol(Bundle extras) {
        return extras != null
                && extras.getInt(WatchdogContract.EXTRA_PROTOCOL_VERSION, -1)
                == WatchdogContract.PROTOCOL_VERSION
                && WatchdogContract.PROTOCOL_TOKEN.equals(
                extras.getString(WatchdogContract.EXTRA_PROTOCOL_TOKEN));
    }

    private static Bundle result(boolean accepted, String message) {
        Bundle result = new Bundle();
        result.putBoolean(WatchdogContract.EXTRA_COMMAND_ACCEPTED, accepted);
        result.putString(WatchdogContract.EXTRA_COMMAND_MESSAGE, message);
        return result;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        return 0;
    }
}
