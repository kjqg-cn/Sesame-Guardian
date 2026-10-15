package io.github.lazyimmortal.sesamewatchdog.ui;

import android.content.Context;
import android.content.SharedPreferences;

public final class ModuleActivationStatus {
    private static final String PREF_RUNTIME_STATUS = "sesame_runtime_status";
    private static final String KEY_LAST_SYSTEM_UI_TIME = "last_system_ui_time";
    private static final long RECENT_ACTIVE_THRESHOLD_MILLIS = 30L * 60L * 1000L; // 30 分钟内有通信视作有效

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
     * 记录 SystemUI 守护服务与模块的最近一次有效通信时间戳。
     */
    public static void recordSystemUiInteraction(Context context) {
        if (context == null) {
            return;
        }
        try {
            SharedPreferences pref = context.getSharedPreferences(
                    PREF_RUNTIME_STATUS, Context.MODE_PRIVATE);
            pref.edit()
                    .putLong(KEY_LAST_SYSTEM_UI_TIME, System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 综合判断模块是否在 LSPosed 中激活生效：
     * 1. 优先检查自身包是否已被 LSPosed 框架成功注入 Hook；
     * 2. 备用检查 SystemUI 守护服务最近是否有活跃通信（兼容老配置仅勾选 SystemUI 的情况）。
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
            long lastTime = pref.getLong(KEY_LAST_SYSTEM_UI_TIME, 0L);
            if (lastTime > 0L
                    && System.currentTimeMillis() - lastTime < RECENT_ACTIVE_THRESHOLD_MILLIS) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
