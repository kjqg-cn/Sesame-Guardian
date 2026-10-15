package io.github.lazyimmortal.sesamewatchdog.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

public final class ModuleActivationStatus {
    private static final String PREF_RUNTIME_STATUS = "sesame_runtime_status";
    private static final String KEY_LAST_SYSTEM_UI_ELAPSED = "last_system_ui_elapsed";
    private static final String KEY_LAST_BOOT_TIMESTAMP = "last_boot_timestamp";
    private static final long RECENT_ACTIVE_THRESHOLD_MILLIS = 30L * 60L * 1000L; // 30 分钟内有通信视作有效
    private static final long MAX_BOOT_TIME_DRIFT_MILLIS = 30L * 1000L; // 开机时间基准漂移容差

    private ModuleActivationStatus() {
    }

    /**
     * 由 LSPosed 在 handleLoadPackage 时动态 Hook 该方法返回 true。
     * 若未在 LSPosed 中激活或作用域未生效，则默认返回 false。
     */
    public static boolean isHookActivated() {
        return false;
    }

    /**
     * 记录 SystemUI 守护服务与模块的最近一次有效通信。
     * 使用单调时间（SystemClock.elapsedRealtime()）配合当前开机时间戳，避免用户调时、网络校时及跨开机误判。
     */
    public static void recordSystemUiInteraction(Context context) {
        if (context == null) {
            return;
        }
        try {
            long nowElapsed = SystemClock.elapsedRealtime();
            long bootTime = System.currentTimeMillis() - nowElapsed;
            SharedPreferences pref = context.getSharedPreferences(
                    PREF_RUNTIME_STATUS, Context.MODE_PRIVATE);
            pref.edit()
                    .putLong(KEY_LAST_SYSTEM_UI_ELAPSED, nowElapsed)
                    .putLong(KEY_LAST_BOOT_TIMESTAMP, bootTime)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 综合判断模块是否在 LSPosed 中激活生效：
     * 1. 优先检查自身包是否已被 LSPosed 框架成功注入 Hook；
     * 2. 备用检查当前开机周期内 SystemUI 守护服务最近是否有活跃通信。
     */
    public static boolean isModuleActive(Context context) {
        if (isHookActivated()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        try {
            SharedPreferences pref = context.getSharedPreferences(
                    PREF_RUNTIME_STATUS, Context.MODE_PRIVATE);
            long recordedElapsed = pref.getLong(KEY_LAST_SYSTEM_UI_ELAPSED, 0L);
            long recordedBootTime = pref.getLong(KEY_LAST_BOOT_TIMESTAMP, 0L);
            if (recordedElapsed <= 0L || recordedBootTime <= 0L) {
                return false;
            }

            long nowElapsed = SystemClock.elapsedRealtime();
            long currentBootTime = System.currentTimeMillis() - nowElapsed;

            return isRecentInteraction(recordedElapsed, recordedBootTime, nowElapsed, currentBootTime);
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean isRecentInteraction(
            long recordedElapsed,
            long recordedBootTime,
            long nowElapsed,
            long currentBootTime) {
        if (recordedElapsed <= 0L || recordedBootTime <= 0L) {
            return false;
        }
        // 若开机时间基准发生改变（偏差超过 30 秒）或单调时间倒退，说明设备已重启，旧记录作废
        if (Math.abs(currentBootTime - recordedBootTime) > MAX_BOOT_TIME_DRIFT_MILLIS
                || nowElapsed < recordedElapsed) {
            return false;
        }
        return (nowElapsed - recordedElapsed) < RECENT_ACTIVE_THRESHOLD_MILLIS;
    }
}
