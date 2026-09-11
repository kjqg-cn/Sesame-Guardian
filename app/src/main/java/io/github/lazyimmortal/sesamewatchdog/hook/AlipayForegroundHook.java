package io.github.lazyimmortal.sesamewatchdog.hook;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

final class AlipayForegroundHook {
    private static final String LAUNCHER_ACTIVITY =
            "com.alipay.mobile.quinox.LauncherActivity";
    private static final String ALIPAY_ENTRY_ACTIVITY =
            "com.eg.android.AlipayGphone.AlipayLogin";
    private static final long VISIBLE_CHECK_INTERVAL_MILLIS = 500L;
    private static final int VISIBLE_CHECK_ATTEMPTS = 6;
    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);

    private AlipayForegroundHook() {
    }

    static void install(ClassLoader classLoader) {
        if (!HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            try {
                hookCreate(classLoader);
            } catch (Throwable throwable) {
                logError("Hook 支付宝 Activity.onCreate 失败", throwable);
            }
            try {
                hookBaseActivityCreate();
            } catch (Throwable throwable) {
                logError("Hook Android Activity.onCreate 失败", throwable);
            }
            try {
                hookBaseActivityResume();
            } catch (Throwable throwable) {
                logError("Hook Android Activity.onResume 失败", throwable);
            }
            try {
                hookBaseActivityWindowFocus();
            } catch (Throwable throwable) {
                logError("Hook Android Activity.onWindowFocusChanged 失败", throwable);
            }
            try {
                hookWindowAttributesChanged();
            } catch (Throwable throwable) {
                logError("Hook Android Activity.onWindowAttributesChanged 失败", throwable);
            }
            try {
                hookWindowFlags();
            } catch (Throwable throwable) {
                logError("Hook 支付宝 Window.setFlags 失败", throwable);
            }
            XposedHelpers.findAndHookMethod(
                    LAUNCHER_ACTIVITY,
                    classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                applyToRequestedActivity((Activity) param.thisObject);
                            } catch (Throwable throwable) {
                                logError("设置支付宝锁屏前台窗口失败", throwable);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                applyToRequestedActivity((Activity) param.thisObject);
                                scheduleVisibleCheck((Activity) param.thisObject, VISIBLE_CHECK_ATTEMPTS);
                            } catch (Throwable throwable) {
                                logError("发送支付宝 Activity 恢复事件失败", throwable);
                            }
                        }
                    });
            try {
                hookNewIntent();
            } catch (Throwable throwable) {
                logError("Hook 支付宝 Activity.onNewIntent 失败", throwable);
            }
            XposedBridge.log("SesameWatchdog: 支付宝前台 Activity Hook 已安装");
        } catch (Throwable throwable) {
            HOOK_INSTALLED.set(false);
            logError("Hook 支付宝前台 Activity 失败", throwable);
        }
    }

    private static void hookCreate(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                LAUNCHER_ACTIVITY,
                classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            applyToRequestedActivity((Activity) param.thisObject);
                        } catch (Throwable throwable) {
                            logError("在支付宝 Activity 创建前设置锁屏窗口失败", throwable);
                        }
                    }
                });
    }

    private static void hookNewIntent() {
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "onNewIntent",
                Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        Intent intent = (Intent) param.args[0];
                        if (!isEntryActivity(activity) || !isRecoveryIntent(intent)) {
                            return;
                        }
                        activity.setIntent(intent);
                        applyWindowFlags(activity);
                    }
                });
    }

    private static void hookBaseActivityCreate() {
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            applyToRequestedActivity((Activity) param.thisObject);
                        } catch (Throwable throwable) {
                            logError("在 Android Activity 创建前设置锁屏窗口失败", throwable);
                        }
                    }
        });
    }

    private static void hookBaseActivityResume() {
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (isEntryActivity(activity)) {
                            applyToRequestedActivity(activity);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (isEntryActivity(activity) && isRecoveryIntent(activity.getIntent())) {
                            scheduleVisibleCheck(activity, VISIBLE_CHECK_ATTEMPTS);
                        }
                    }
                });
    }

    private static void hookBaseActivityWindowFocus() {
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "onWindowFocusChanged",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (isEntryActivity(activity)
                                && isRecoveryIntent(activity.getIntent())
                                && (Boolean) param.args[0]) {
                            applyWindowFlags(activity);
                            sendResumeEvent(activity);
                        }
                    }
                });
    }

    private static void hookWindowAttributesChanged() {
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "onWindowAttributesChanged",
                WindowManager.LayoutParams.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (!isEntryActivity(activity)
                                || !isRecoveryIntent(activity.getIntent())) {
                            return;
                        }
                        WindowManager.LayoutParams attributes =
                                (WindowManager.LayoutParams) param.args[0];
                        if (attributes != null) {
                            attributes.flags |=
                                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
                            Log.i(
                                    "SesameWatchdog",
                                    "保留支付宝锁屏窗口 flags=" + attributes.flags);
                        }
                    }
        });
    }

    private static void hookWindowFlags() {
        XposedHelpers.findAndHookMethod(
                Window.class,
                "setFlags",
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Activity activity = getWindowActivity((Window) param.thisObject);
                        if (activity == null
                                || !isEntryActivity(activity)
                                || !isRecoveryIntent(activity.getIntent())) {
                            return;
                        }
                        int requiredFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
                        param.args[0] = ((Integer) param.args[0]) | requiredFlags;
                        param.args[1] = ((Integer) param.args[1]) | requiredFlags;
                    }
                });
    }

    private static void applyToRequestedActivity(Activity activity) {
        if (isEntryActivity(activity) && isRecoveryIntent(activity.getIntent())) {
            applyWindowFlags(activity);
        }
    }

    private static void applyWindowFlags(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true);
            activity.setTurnScreenOn(true);
        }
        Window window = activity.getWindow();
        if (window != null) {
            int requiredFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.flags |= requiredFlags;
            window.setAttributes(attributes);
            window.setFlags(requiredFlags, requiredFlags);
            Log.i(
                    "SesameWatchdog",
                    "设置支付宝锁屏窗口 flags=" + window.getAttributes().flags);
        }
        XposedBridge.log(
                "SesameWatchdog: 已为支付宝 Activity 设置 showWhenLocked/turnScreenOn"
                        + "，class=" + activity.getClass().getName());
    }

    private static void scheduleVisibleCheck(Activity activity, int attemptsRemaining) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !isRecoveryIntent(activity.getIntent())) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                applyToRequestedActivity(activity);
                if (isActuallyVisible(activity)) {
                    sendResumeEvent(activity);
                } else if (attemptsRemaining > 1) {
                    scheduleVisibleCheck(activity, attemptsRemaining - 1);
                } else {
                    XposedBridge.log(
                            "SesameWatchdog: 支付宝 Activity 已恢复但窗口仍未获得焦点"
                                    + "，class=" + activity.getClass().getName());
                }
            } catch (Throwable throwable) {
                logError("确认支付宝 Activity 窗口可见性失败", throwable);
            }
        }, VISIBLE_CHECK_INTERVAL_MILLIS);
    }

    private static boolean isActuallyVisible(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return false;
        }
        View decorView = window.getDecorView();
        return activity.hasWindowFocus()
                && decorView.getVisibility() == View.VISIBLE
                && decorView.getWindowVisibility() == View.VISIBLE;
    }

    private static Activity getWindowActivity(Window window) {
        if (window == null) {
            return null;
        }
        android.content.Context context = window.getContext();
        while (context != null) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            if (!(context instanceof android.content.ContextWrapper)) {
                return null;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static void sendResumeEvent(Activity activity) {
        if (!isEntryActivity(activity) || !isRecoveryIntent(activity.getIntent())) {
            return;
        }
        long requestId = activity.getIntent().getLongExtra(
                WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID,
                0L);
        if (requestId <= 0L) {
            return;
        }
        ProbeEventSender.sendActivityResumed(
                SystemClock.elapsedRealtime(),
                requestId,
                activity.getClass().getName());
    }

    private static boolean isRecoveryIntent(Intent intent) {
        return intent != null
                && intent.getBooleanExtra(WatchdogContract.EXTRA_FOREGROUND_RECOVERY, false);
    }

    private static boolean isEntryActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        Class<?> type = activity.getClass();
        while (type != null) {
            String name = type.getName();
            if (ALIPAY_ENTRY_ACTIVITY.equals(name) || LAUNCHER_ACTIVITY.equals(name)) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static void logError(String message, Throwable throwable) {
        XposedBridge.log("SesameWatchdog: " + message);
        XposedBridge.log(throwable);
    }

}
