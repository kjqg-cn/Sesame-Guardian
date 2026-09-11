package io.github.lazyimmortal.sesamewatchdog.hook;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class AlipayNotificationProbe {
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);
    private static final ThreadLocal<Integer> NOTIFY_DEPTH = new ThreadLocal<>();

    private AlipayNotificationProbe() {
    }

    static void install(Context context) {
        try {
            if (context == null || !ALIPAY_PACKAGE.equals(context.getPackageName())) {
                return;
            }
            if (!HOOK_INSTALLED.compareAndSet(false, true)) {
                return;
            }

            boolean intOverloadInstalled = hookIntOverload();
            boolean tagOverloadInstalled = hookTagOverload();
            if (!intOverloadInstalled && !tagOverloadInstalled) {
                HOOK_INSTALLED.set(false);
                XposedBridge.log("SesameWatchdog: Hook NotificationManager.notify 重载全部失败");
                return;
            }
            XposedBridge.log(
                    "SesameWatchdog: NotificationManager.notify Hook 已安装"
                            + "，notify(int)=" + WatchdogLogFormatter.yesNo(intOverloadInstalled)
                            + "，notify(String,int)="
                            + WatchdogLogFormatter.yesNo(tagOverloadInstalled));
        } catch (Throwable throwable) {
            HOOK_INSTALLED.set(false);
            logError("安装通知探针失败", throwable);
        }
    }

    private static boolean hookIntOverload() {
        try {
            XposedHelpers.findAndHookMethod(
                    NotificationManager.class,
                    "notify",
                    int.class,
                    Notification.class,
                    new NotifyHook(0, 1));
            return true;
        } catch (Throwable throwable) {
            logError("Hook notify(int, Notification) 失败", throwable);
            return false;
        }
    }

    private static boolean hookTagOverload() {
        try {
            XposedHelpers.findAndHookMethod(
                    NotificationManager.class,
                    "notify",
                    String.class,
                    int.class,
                    Notification.class,
                    new NotifyHook(1, 2));
            return true;
        } catch (Throwable throwable) {
            logError("Hook notify(String, int, Notification) 失败", throwable);
            return false;
        }
    }

    private static void observe(int notificationId, Notification notification) {
        if (notification == null) {
            return;
        }

        CharSequence title = null;
        CharSequence content = null;
        CharSequence subText = null;
        if (notification.extras != null) {
            title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            content = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        }

        long observedWallClock = System.currentTimeMillis();
        long observedElapsedRealtime = SystemClock.elapsedRealtime();
        NotificationStatusParser.ParsedNotification parsed = NotificationStatusParser.parse(
                notificationId,
                notification.getChannelId(),
                subText,
                title,
                content,
                observedWallClock);
        if (!parsed.targetNotification) {
            return;
        }

        Long nextExecutionElapsedRealtime = NotificationStatusParser.mapWallClockToElapsedRealtime(
                parsed.nextExecutionAtWallClockMillis,
                observedWallClock,
                observedElapsedRealtime);
        Long lastExecutionElapsedRealtime = NotificationStatusParser.mapWallClockToElapsedRealtime(
                parsed.lastExecutionAtWallClockMillis,
                observedWallClock,
                observedElapsedRealtime);
        XposedBridge.log(
                "SesameWatchdog: 观察到 Sesame-GR 通知"
                        + "，状态=" + WatchdogLogFormatter.status(parsed.status)
                        + "，观察时间=" + WatchdogLogFormatter.wallClock(observedWallClock)
                        + "，下次执行=" + WatchdogLogFormatter.wallClock(
                        parsed.nextExecutionAtWallClockMillis)
                        + "，上次执行=" + WatchdogLogFormatter.wallClock(
                        parsed.lastExecutionAtWallClockMillis));
        ProbeEventSender.sendNotification(
                parsed.status,
                observedElapsedRealtime,
                nextExecutionElapsedRealtime,
                lastExecutionElapsedRealtime,
                parsed.lastExecutionAtWallClockMillis);
    }

    private static void logError(String message, Throwable throwable) {
        XposedBridge.log("SesameWatchdog: " + message);
        XposedBridge.log(throwable);
    }

    private static final class NotifyHook extends XC_MethodHook {
        private final int notificationIdIndex;
        private final int notificationIndex;

        private NotifyHook(int notificationIdIndex, int notificationIndex) {
            this.notificationIdIndex = notificationIdIndex;
            this.notificationIndex = notificationIndex;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                Integer depth = NOTIFY_DEPTH.get();
                NOTIFY_DEPTH.set(depth == null ? 1 : depth + 1);
            } catch (Throwable throwable) {
                logError("进入 NotificationManager.notify Hook 失败", throwable);
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                Integer depth = NOTIFY_DEPTH.get();
                int remainingDepth = depth == null ? 0 : Math.max(0, depth - 1);
                if (remainingDepth > 0) {
                    NOTIFY_DEPTH.set(remainingDepth);
                    return;
                }
                NOTIFY_DEPTH.remove();
                if (param.hasThrowable()) {
                    return;
                }

                Object id = param.args[notificationIdIndex];
                Object notification = param.args[notificationIndex];
                if (id instanceof Integer && notification instanceof Notification) {
                    observe((Integer) id, (Notification) notification);
                }
            } catch (Throwable throwable) {
                logError("观察 NotificationManager.notify 结果失败", throwable);
            }
        }
    }
}
