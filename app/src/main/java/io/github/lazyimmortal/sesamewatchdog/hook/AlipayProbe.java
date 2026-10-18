package io.github.lazyimmortal.sesamewatchdog.hook;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class AlipayProbe {
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static final long HEARTBEAT_INTERVAL_MILLIS = 60_000L;

    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean HEARTBEAT_STARTED = new AtomicBoolean(false);

    private AlipayProbe() {
    }

    static void install() {
        if (!HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "attach",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                startHeartbeat((Context) param.args[0]);
                            } catch (Throwable throwable) {
                                logError("初始化支付宝探针失败", throwable);
                            }
                        }
                    });
            XposedBridge.log("SesameGuardian: 支付宝 Application.attach Hook 已安装");
        } catch (Throwable throwable) {
            HOOK_INSTALLED.set(false);
            logError("Hook 支付宝 Application.attach 失败", throwable);
        }
    }

    private static void startHeartbeat(Context context) {
        if (!ALIPAY_PACKAGE.equals(context.getPackageName())) {
            XposedBridge.log(
                    "SesameGuardian: 忽略非支付宝主进程的 Application.attach，包名="
                            + context.getPackageName());
            return;
        }
        ProbeEventSender.initialize(context);
        XposedLog.initialize(context, "支付宝");
        try {
            AlipayForegroundHook.install(context.getClassLoader());
        } catch (Throwable throwable) {
            logError("初始化支付宝前台 Activity Hook 失败", throwable);
        }
        try {
            AlipayNotificationProbe.install(context);
        } catch (Throwable throwable) {
            logError("初始化通知探针失败", throwable);
        }
        if (!HEARTBEAT_STARTED.compareAndSet(false, true)) {
            return;
        }

        try {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            HeartbeatRunnable heartbeatRunnable = new HeartbeatRunnable(mainHandler);
            if (!mainHandler.post(heartbeatRunnable)) {
                HEARTBEAT_STARTED.set(false);
                XposedBridge.log("SesameGuardian: 首次主 Looper 心跳入队失败");
                return;
            }
            XposedBridge.log(
                    "SesameGuardian: 支付宝主 Looper 心跳已启动，支付宝PID="
                            + Process.myPid());
        } catch (Throwable throwable) {
            HEARTBEAT_STARTED.set(false);
            logError("启动主 Looper 心跳失败", throwable);
        }
    }

    private static void logError(String message, Throwable throwable) {
        XposedBridge.log("SesameGuardian: " + message);
        XposedBridge.log(throwable);
    }

    private static final class HeartbeatRunnable implements Runnable {
        private final Handler mainHandler;
        private long sequence;
        private long lastHeartbeatElapsedRealtime;

        private HeartbeatRunnable(Handler mainHandler) {
            this.mainHandler = mainHandler;
        }

        @Override
        public void run() {
            try {
                long now = SystemClock.elapsedRealtime();
                long interval = lastHeartbeatElapsedRealtime == 0L
                        ? 0L
                        : now - lastHeartbeatElapsedRealtime;
                lastHeartbeatElapsedRealtime = now;
                sequence++;

                XposedBridge.log(
                        "SesameGuardian: 支付宝主 Looper 心跳"
                                + "，支付宝PID=" + Process.myPid()
                                + "，序号=" + sequence
                                + "，距上次心跳=" + (interval == 0L
                                ? "首次心跳"
                                : WatchdogLogFormatter.duration(interval)));
                ProbeEventSender.sendHeartbeat(now);
            } catch (Throwable throwable) {
                logError("记录主 Looper 心跳失败", throwable);
            }

            try {
                if (!mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MILLIS)) {
                    XposedBridge.log("SesameGuardian: 安排下一次主 Looper 心跳失败");
                }
            } catch (Throwable throwable) {
                logError("安排下一次主 Looper 心跳失败", throwable);
            }
        }
    }
}
