package io.github.lazyimmortal.sesamewatchdog.hook;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;

import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfig;
import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfigStore;
import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

@SuppressLint({"InlinedApi", "StaticFieldLeak"})
final class SystemUiWatchdog {
    private static final long CHECK_INTERVAL_MILLIS = 60_000L;
    private static final long HEALTH_LOG_INTERVAL_MILLIS = 5 * 60_000L;
    private static final long MAX_EVENT_FUTURE_SKEW_MILLIS = 5 * 60_000L;
    private static final long SOFT_RECOVERY_COOLDOWN_MILLIS = 20 * 60_000L;
    private static final long RECOVERY_OBSERVATION_MILLIS = 90_000L;
    private static final long HARD_RECOVERY_OBSERVATION_MILLIS = 15 * 60_000L;
    private static final long FOREGROUND_RECOVERY_TIMEOUT_MILLIS = 30_000L;
    private static final int ALIPAY_USER_ID = 0;
    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final Runnable CHECK_RUNNABLE = SystemUiWatchdog::check;
    private static final Runnable RECOVERY_CHECK_RUNNABLE =
            SystemUiWatchdog::checkRecoveryResult;
    private static final Runnable FOREGROUND_RECOVERY_TIMEOUT_RUNNABLE =
            SystemUiWatchdog::handleForegroundRecoveryTimeout;

    private static Handler workerHandler;
    private static Context systemUiContext;
    private static WatchdogConfig config;
    private static SystemUiWatchdogState state;
    private static SystemUiWatchdogState.Fault lastFault;
    private static long lastHealthLogElapsedRealtime;
    private static long activeHardRecoveryRequestId;
    private static long hardRecoveryRequestedElapsedRealtime;
    private static int hardRecoveryOriginalAlipayPid;
    private static boolean hardRecoveryProcessRestartObserved;
    private static long lastForegroundRecoveryElapsedRealtime;
    private static long activeForegroundRecoveryRequestId;
    private static long activeForegroundHardRecoveryRequestId;
    private static boolean foregroundHardRecoveryAttempted;
    private static PowerManager.WakeLock foregroundWakeLock;
    private static final RecoverySequence RECOVERY_SEQUENCE = new RecoverySequence();

    private SystemUiWatchdog() {
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
                                start((Context) param.args[0]);
                            } catch (Throwable throwable) {
                                XposedLog.logError("初始化 SystemUI 守护服务失败", throwable);
                            }
                        }
                    });
            XposedLog.log("SystemUI Application.attach Hook 已安装");
        } catch (Throwable throwable) {
            HOOK_INSTALLED.set(false);
            XposedLog.logError("Hook SystemUI Application.attach 失败", throwable);
        }
    }

    private static void start(Context context) {
        if (context == null || !WatchdogContract.SYSTEM_UI_PACKAGE.equals(context.getPackageName())) {
            return;
        }
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        systemUiContext = appContext == null ? context : appContext;
        XposedLog.initialize(systemUiContext, "SystemUI");
        try {
            SystemUiKeyguardBridge.install(context.getClassLoader());
        } catch (Throwable throwable) {
            XposedLog.logError("初始化 SystemUI Keyguard 桥接失败", throwable);
        }
        HandlerThread workerThread = new HandlerThread(
                "SesameGuardian-SystemUI", Process.THREAD_PRIORITY_BACKGROUND);
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        workerHandler.post(SystemUiWatchdog::initializeOnWorker);
    }

    private static void initializeOnWorker() {
        try {
            config = WatchdogConfigStore.load(systemUiContext);
            state = new SystemUiWatchdogState(SystemClock.elapsedRealtime());
            registerReceiver();
            XposedLog.log(
                "SystemUI 守护服务已初始化，配置：" + WatchdogLogFormatter.config(config)
                            + "，SystemUI PID=" + Process.myPid());
            lastFault = null;
            lastHealthLogElapsedRealtime = 0L;
            activeHardRecoveryRequestId = 0L;
            hardRecoveryRequestedElapsedRealtime = 0L;
            hardRecoveryOriginalAlipayPid = 0;
            hardRecoveryProcessRestartObserved = false;
            lastForegroundRecoveryElapsedRealtime = 0L;
            activeForegroundRecoveryRequestId = 0L;
            activeForegroundHardRecoveryRequestId = 0L;
            foregroundHardRecoveryAttempted = false;
            RECOVERY_SEQUENCE.reset();
            scheduleCheck();
        } catch (Throwable throwable) {
            XposedLog.logError("初始化 SystemUI 守护观察器失败", throwable);
        }
    }

    private static void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WatchdogContract.ACTION_PROBE_EVENT);
        filter.addAction(WatchdogContract.ACTION_CONFIG_UPDATE);
        filter.addAction(WatchdogContract.ACTION_HARD_RECOVERY_RESULT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        BroadcastReceiver receiver = new WatchdogReceiver();
        systemUiContext.registerReceiver(
                receiver,
                filter,
                null,
                workerHandler,
                Context.RECEIVER_EXPORTED);
    }

    private static void scheduleCheck() {
        workerHandler.removeCallbacks(CHECK_RUNNABLE);
        if (config != null && config.isEnabled()) {
            workerHandler.postDelayed(CHECK_RUNNABLE, CHECK_INTERVAL_MILLIS);
        }
    }

    private static void check() {
        try {
            evaluateAndLog("periodic");
        } catch (Throwable throwable) {
            XposedLog.logError("SystemUI 守护检查失败", throwable);
        } finally {
            scheduleCheck();
        }
    }

    private static void handleProbeEvent(Intent intent) {
        if (!isValidProbeEvent(intent) || config == null || !config.isEnabled()) {
            return;
        }

        int eventType = intent.getIntExtra(WatchdogContract.EXTRA_EVENT_TYPE, 0);
        int processId = intent.getIntExtra(WatchdogContract.EXTRA_PROCESS_ID, 0);
        long eventElapsedRealtime = intent.getLongExtra(
                WatchdogContract.EXTRA_EVENT_ELAPSED_REALTIME, 0L);
        if (eventType == WatchdogContract.EVENT_ACTIVITY_RESUMED) {
            handleForegroundActivityResumed(intent, processId, eventElapsedRealtime);
            return;
        } else if (eventType == WatchdogContract.EVENT_HEARTBEAT) {
            state.onHeartbeat(processId, eventElapsedRealtime);
            XposedLog.log(
                    "收到支付宝主 Looper 心跳，" + stateSnapshot());
        } else if (eventType == WatchdogContract.EVENT_NOTIFICATION) {
            NotificationStatusParser.StatusKind status = parseStatus(intent);
            if (status == null) {
                return;
            }
            Long nextExecution = getOptionalLong(
                    intent, WatchdogContract.EXTRA_NEXT_EXECUTION_ELAPSED_REALTIME);
            Long lastExecution = getOptionalLong(
                    intent, WatchdogContract.EXTRA_LAST_EXECUTION_ELAPSED_REALTIME);
            Long lastExecutionWallClock = getOptionalLong(
                    intent, WatchdogContract.EXTRA_LAST_EXECUTION_WALL_CLOCK_MILLIS);
            state.onNotification(
                    processId,
                    status,
                    eventElapsedRealtime,
                    nextExecution,
                    lastExecution,
                    lastExecutionWallClock);
            XposedLog.log(
                    "收到 Sesame-GR 通知，状态=" + WatchdogLogFormatter.status(status)
                            + "，" + stateSnapshot());
        } else {
            return;
        }
        evaluateAndLog("probe_event");
    }

    private static void handleForegroundActivityResumed(
            Intent intent,
            int processId,
            long eventElapsedRealtime) {
        long requestId = intent.getLongExtra(
                WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID,
                0L);
        if (activeForegroundRecoveryRequestId == 0L
                || activeForegroundRecoveryRequestId != requestId) {
            return;
        }
        workerHandler.removeCallbacks(FOREGROUND_RECOVERY_TIMEOUT_RUNNABLE);
        activeForegroundRecoveryRequestId = 0L;
        foregroundHardRecoveryAttempted = false;
        XposedLog.log(
                "支付宝已进入锁屏前台生命周期，前台拉起成功"
                        + "，请求编号=" + requestId
                        + "，PID=" + processId
                        + "，Activity=" + intent.getStringExtra(
                        WatchdogContract.EXTRA_ACTIVITY_CLASS)
                        + "，事件时间=" + elapsedRealtime(eventElapsedRealtime));
    }

    private static void handleConfigUpdate(Intent intent) {
        if (!isValidProtocol(intent)) {
            return;
        }
        WatchdogConfig newConfig = WatchdogConfigStore.fromIntent(intent);
        WatchdogConfigStore.save(systemUiContext, newConfig);
        config = newConfig;
        lastFault = null;
        if (!newConfig.isEnabled()
                || !newConfig.isAutoSoftRecovery()
                || newConfig.isInQuietHours(System.currentTimeMillis())
                || (!newConfig.isHardRecoveryEnabled()
                && RECOVERY_SEQUENCE.getStage()
                == RecoverySequence.Stage.WAITING_HARD_RECOVERY_RESULT)) {
            cancelRecoverySequence();
        }
        if (!newConfig.isEnabled()
                || !newConfig.isForegroundRecoveryEnabled()
                || newConfig.isInQuietHours(System.currentTimeMillis())) {
            cancelForegroundRecovery();
        }
        scheduleCheck();
        XposedLog.log(
                "SystemUI 守护配置已更新，运行状态已保留："
                        + WatchdogLogFormatter.config(config));
        evaluateAndLog("config_update");
    }

    private static void handleScreenEvent(String action) {
        if (Intent.ACTION_USER_PRESENT.equals(action)) {
            SystemUiKeyguardBridge.setOccluded(false);
        }
        if (config != null && config.isEnabled()) {
            evaluateAndLog(action);
        }
    }

    private static void evaluateAndLog(String trigger) {
        if (state == null || config == null) {
            return;
        }
        long nowElapsedRealtime = SystemClock.elapsedRealtime();
        SystemUiWatchdogState.Fault fault = state.evaluate(config, nowElapsedRealtime);
        if (fault != lastFault) {
            lastFault = fault;
            lastHealthLogElapsedRealtime = nowElapsedRealtime;
            XposedLog.log(
                    "守护状态变化，触发方式=" + WatchdogLogFormatter.trigger(trigger)
                            + "，状态=" + WatchdogLogFormatter.fault(fault)
                            + "，" + stateSnapshot());
        } else if ("periodic".equals(trigger)
                && nowElapsedRealtime - lastHealthLogElapsedRealtime
                >= HEALTH_LOG_INTERVAL_MILLIS) {
            lastHealthLogElapsedRealtime = nowElapsedRealtime;
            XposedLog.log(
                    "守护定时检查完成，当前状态="
                            + WatchdogLogFormatter.fault(fault)
                            + "，" + stateSnapshot());
        }
        handleRecovery(fault, nowElapsedRealtime);
        handleForegroundRecovery(nowElapsedRealtime);
    }

    private static void handleForegroundRecovery(long nowElapsedRealtime) {
        if (config == null
                || !config.isEnabled()
                || !config.isForegroundRecoveryEnabled()
                || activeForegroundRecoveryRequestId != 0L
                || activeForegroundHardRecoveryRequestId != 0L) {
            return;
        }
        if (!isKeyguardLocked()) {
            SystemUiKeyguardBridge.setOccluded(false);
            return;
        }
        SystemUiWatchdogState.ForegroundRecoveryTrigger trigger =
                state.checkForegroundRecoveryTrigger(
                        config,
                        nowElapsedRealtime,
                        lastForegroundRecoveryElapsedRealtime);
        if (trigger == SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE) {
            return;
        }

        long requestId = Math.max(1L, nowElapsedRealtime);
        activeForegroundRecoveryRequestId = requestId;
        foregroundHardRecoveryAttempted = false;
        // 尝试时间立即记录：建立 60 秒冷却防抖，避免重试风暴
        lastForegroundRecoveryElapsedRealtime = nowElapsedRealtime;

        SystemUiKeyguardBridge.setOccluded(true);
        boolean wakeScreen = prepareScreenForRecovery();
        if (!sendForegroundRecoveryRequest(requestId, wakeScreen)) {
            activeForegroundRecoveryRequestId = 0L;
            releaseForegroundWakeLock();
            SystemUiKeyguardBridge.setOccluded(false);
            return;
        }

        // 计划目标仅在拉起请求成功后标记：避免瞬时 IPC 失败导致放弃本轮唤醒
        state.markForegroundRecoveryTriggered(trigger);
        if (trigger == SystemUiWatchdogState.ForegroundRecoveryTrigger.SCHEDULED_DUE) {
            XposedLog.log(
                    "触发智能按需锁屏拉起（即将到达计划执行时间），目标="
                            + WatchdogLogFormatter.elapsedRealtime(
                            state.getLastScheduledTriggerTarget(), nowElapsedRealtime, System.currentTimeMillis())
                            + "，请求编号=" + requestId);
        } else {
            XposedLog.log(
                    "触发保底锁屏前台拉起（间隔="
                            + config.getForegroundRecoveryIntervalMinutes()
                            + "分钟），请求编号=" + requestId);
        }
        scheduleForegroundRecoveryTimeout();
    }

    private static void handleForegroundRecoveryTimeout() {
        if (activeForegroundRecoveryRequestId == 0L) {
            return;
        }
        long requestId = activeForegroundRecoveryRequestId;
        activeForegroundRecoveryRequestId = 0L;
        XposedLog.log(
                "锁屏前台拉起后 "
                        + WatchdogLogFormatter.duration(
                        FOREGROUND_RECOVERY_TIMEOUT_MILLIS)
                        + "仍未收到支付宝 Activity onResume"
                        + "，请求编号=" + requestId);

        if (config == null
                || !config.isHardRecoveryEnabled()
                || config.isInQuietHours(System.currentTimeMillis())) {
            if (config != null && config.isInQuietHours(System.currentTimeMillis())) {
                XposedLog.log("跳过锁屏前台拉起后的 root 硬回退：当前处于夜间免打扰时段");
            } else {
                XposedLog.log("跳过锁屏前台拉起后的 root 硬回退：root 硬恢复未开启");
            }
            releaseForegroundWakeLock();
            SystemUiKeyguardBridge.setOccluded(false);
            return;
        }

        if (foregroundHardRecoveryAttempted) {
            XposedLog.log("锁屏前台拉起和一次 root 硬回退均未收到 Activity onResume");
            releaseForegroundWakeLock();
            SystemUiKeyguardBridge.setOccluded(false);
            return;
        }

        activeForegroundRecoveryRequestId = requestId;
        activeForegroundHardRecoveryRequestId = requestId;
        foregroundHardRecoveryAttempted = true;
        if (!sendForegroundHardRecoveryRequest(
                requestId,
                prepareScreenForRecovery())) {
            activeForegroundRecoveryRequestId = 0L;
            activeForegroundHardRecoveryRequestId = 0L;
            foregroundHardRecoveryAttempted = false;
            releaseForegroundWakeLock();
            SystemUiKeyguardBridge.setOccluded(false);
            return;
        }
        scheduleForegroundRecoveryTimeout();
    }

    private static boolean sendForegroundRecoveryRequest(
            long requestId,
            boolean wakeScreen) {
        try {
            Bundle request = new Bundle();
            request.putInt(
                    WatchdogContract.EXTRA_PROTOCOL_VERSION,
                    WatchdogContract.PROTOCOL_VERSION);
            request.putString(
                    WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                    WatchdogContract.PROTOCOL_TOKEN);
            request.putString(
                    WatchdogContract.EXTRA_SOURCE_PACKAGE,
                    WatchdogContract.SYSTEM_UI_PACKAGE);
            request.putLong(
                    WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID,
                    requestId);
            request.putInt(WatchdogContract.EXTRA_TARGET_USER_ID, ALIPAY_USER_ID);
            request.putBoolean(WatchdogContract.EXTRA_WAKE_SCREEN, wakeScreen);
            Bundle result = systemUiContext.getContentResolver().call(
                    Uri.parse(WatchdogContract.COMMAND_PROVIDER_URI),
                    WatchdogContract.METHOD_BRING_ALIPAY_TO_FRONT,
                    null,
                    request);
            if (result == null
                    || !result.getBoolean(
                    WatchdogContract.EXTRA_COMMAND_ACCEPTED, false)) {
                String message = result == null
                        ? "模块命令通道未返回结果"
                        : result.getString(
                        WatchdogContract.EXTRA_COMMAND_MESSAGE,
                        "模块命令通道拒绝前台拉起请求");
                XposedLog.log("请求模块前台拉起支付宝失败：" + message);
                return false;
            }
            XposedLog.log(
                    "已请求锁屏前台拉起支付宝"
                            + "，请求编号=" + requestId
                            + "，唤醒屏幕=" + WatchdogLogFormatter.yesNo(wakeScreen));
            return true;
        } catch (Throwable throwable) {
            XposedLog.logError("请求模块前台拉起支付宝失败", throwable);
            return false;
        }
    }

    private static boolean sendForegroundHardRecoveryRequest(
            long requestId,
            boolean wakeScreen) {
        try {
            Bundle request = new Bundle();
            request.putInt(
                    WatchdogContract.EXTRA_PROTOCOL_VERSION,
                    WatchdogContract.PROTOCOL_VERSION);
            request.putString(
                    WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                    WatchdogContract.PROTOCOL_TOKEN);
            request.putString(
                    WatchdogContract.EXTRA_SOURCE_PACKAGE,
                    WatchdogContract.SYSTEM_UI_PACKAGE);
            request.putLong(
                    WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID,
                    requestId);
            request.putInt(WatchdogContract.EXTRA_TARGET_USER_ID, ALIPAY_USER_ID);
            request.putBoolean(WatchdogContract.EXTRA_WAKE_SCREEN, wakeScreen);
            Bundle result = systemUiContext.getContentResolver().call(
                    Uri.parse(WatchdogContract.COMMAND_PROVIDER_URI),
                    WatchdogContract.METHOD_REQUEST_HARD_RECOVERY,
                    null,
                    request);
            if (result == null
                    || !result.getBoolean(
                    WatchdogContract.EXTRA_COMMAND_ACCEPTED, false)) {
                String message = result == null
                        ? "模块命令通道未返回结果"
                        : result.getString(
                        WatchdogContract.EXTRA_COMMAND_MESSAGE,
                        "模块命令通道拒绝 root 硬回退请求");
                XposedLog.log("请求 root 硬回退失败：" + message);
                return false;
            }
            XposedLog.log(
                    "已请求 root 硬回退支付宝前台拉起"
                            + "，请求编号=" + requestId);
            return true;
        } catch (Throwable throwable) {
            XposedLog.logError("请求 root 硬回退失败", throwable);
            return false;
        }
    }

    private static void scheduleForegroundRecoveryTimeout() {
        workerHandler.removeCallbacks(FOREGROUND_RECOVERY_TIMEOUT_RUNNABLE);
        workerHandler.postDelayed(
                FOREGROUND_RECOVERY_TIMEOUT_RUNNABLE,
                FOREGROUND_RECOVERY_TIMEOUT_MILLIS);
    }

    private static void cancelForegroundRecovery() {
        if (workerHandler != null) {
            workerHandler.removeCallbacks(FOREGROUND_RECOVERY_TIMEOUT_RUNNABLE);
        }
        releaseForegroundWakeLock();
        SystemUiKeyguardBridge.setOccluded(false);
        lastForegroundRecoveryElapsedRealtime = 0L;
        activeForegroundRecoveryRequestId = 0L;
        activeForegroundHardRecoveryRequestId = 0L;
        foregroundHardRecoveryAttempted = false;
        if (state != null) {
            state.resetScheduledTriggerTarget();
        }
    }

    private static boolean isKeyguardLocked() {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) systemUiContext
                    .getSystemService(Context.KEYGUARD_SERVICE);
            return keyguardManager != null && keyguardManager.isKeyguardLocked();
        } catch (Throwable throwable) {
            XposedLog.logError("读取锁屏状态失败", throwable);
            return false;
        }
    }

    private static boolean shouldWakeScreen() {
        try {
            DisplayManager displayManager = (DisplayManager) systemUiContext
                    .getSystemService(Context.DISPLAY_SERVICE);
            Display display = displayManager == null
                    ? null
                    : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null && display.getState() != Display.STATE_ON) {
                return true;
            }
            PowerManager powerManager = (PowerManager) systemUiContext
                    .getSystemService(Context.POWER_SERVICE);
            return powerManager == null || !powerManager.isInteractive();
        } catch (Throwable throwable) {
            XposedLog.logError("读取屏幕交互状态失败", throwable);
            return true;
        }
    }

    private static boolean prepareScreenForRecovery() {
        if (!isKeyguardLocked()) {
            return false;
        }
        return !wakeScreenFromSystemUi();
    }

    private static boolean wakeScreenFromSystemUi() {
        try {
            PowerManager powerManager = (PowerManager) systemUiContext
                    .getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                return false;
            }
            releaseForegroundWakeLock();
            Method wakeUp = PowerManager.class.getDeclaredMethod(
                    "wakeUp",
                    long.class,
                    int.class,
                    String.class);
            wakeUp.setAccessible(true);
            wakeUp.invoke(
                    powerManager,
                    SystemClock.uptimeMillis(),
                    2,
                    "SesameGuardian");
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SesameGuardian:ForegroundRecovery");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(FOREGROUND_RECOVERY_TIMEOUT_MILLIS);
            foregroundWakeLock = wakeLock;
            for (int attempt = 0; attempt < 5; attempt++) {
                if (isDisplayOn()) {
                    XposedLog.log("SystemUI 已直接请求点亮屏幕");
                    return true;
                }
                SystemClock.sleep(100L);
            }
            releaseForegroundWakeLock();
            XposedLog.log("SystemUI 唤醒请求后屏幕仍未进入 STATE_ON");
            return false;
        } catch (Throwable throwable) {
            XposedLog.logError("SystemUI 直接点亮屏幕失败，将回退到 root 输入事件", throwable);
            return false;
        }
    }

    private static void releaseForegroundWakeLock() {
        if (foregroundWakeLock == null) {
            return;
        }
        try {
            if (foregroundWakeLock.isHeld()) {
                foregroundWakeLock.release();
            }
        } catch (Throwable throwable) {
            XposedLog.logError("释放前台拉起屏幕唤醒锁失败", throwable);
        } finally {
            foregroundWakeLock = null;
        }
    }

    private static boolean isDisplayOn() {
        DisplayManager displayManager = (DisplayManager) systemUiContext
                .getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager == null
                ? null
                : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        return display != null && display.getState() == Display.STATE_ON;
    }

    private static void handleRecovery(
            SystemUiWatchdogState.Fault fault,
            long nowElapsedRealtime) {
        RecoverySequence.Stage previousStage = RECOVERY_SEQUENCE.getStage();
        if (previousStage == RecoverySequence.Stage.WAITING_HARD_RECOVERY_RESULT
                && !hardRecoveryProcessRestartObserved
                && hardRecoveryProcessRestarted()) {
            hardRecoveryProcessRestartObserved = true;
            XposedLog.log(
                    "支付宝硬恢复后新的主进程已上报心跳，继续等待新的执行进度"
                            + "，原支付宝PID=" + hardRecoveryOriginalAlipayPid
                            + "，新支付宝PID=" + state.getAlipayPid()
                            + "，最近心跳=" + elapsedRealtime(
                            state.getLastHeartbeatElapsedRealtime()));
        }
        RecoverySequence.Decision progressDecision = RECOVERY_SEQUENCE.observeProgress(
                state.getLastExecutionElapsedRealtime());
        if (progressDecision == RecoverySequence.Decision.SUCCEEDED) {
            workerHandler.removeCallbacks(RECOVERY_CHECK_RUNNABLE);
            activeHardRecoveryRequestId = 0L;
            hardRecoveryRequestedElapsedRealtime = 0L;
            hardRecoveryOriginalAlipayPid = 0;
            hardRecoveryProcessRestartObserved = false;
            if (previousStage == RecoverySequence.Stage.WAITING_HARD_RECOVERY_RESULT) {
                XposedLog.log(
                        "支付宝硬恢复成功，已检测到新的执行进度，最近一次执行="
                                + elapsedRealtime(state.getLastExecutionElapsedRealtime()));
            } else if (previousStage == RecoverySequence.Stage.EXHAUSTED) {
                XposedLog.log(
                        "此前恢复流程未生效，现检测到执行进度自行恢复，最近一次执行="
                                + elapsedRealtime(state.getLastExecutionElapsedRealtime()));
            } else {
                XposedLog.log(
                        "软恢复成功，确认方式=通知中的执行进度已更新，恢复阶段="
                                + WatchdogLogFormatter.recoveryStage(previousStage)
                                + "，最近一次执行="
                                + elapsedRealtime(state.getLastExecutionElapsedRealtime()));
            }
            return;
        }

        if (fault != SystemUiWatchdogState.Fault.EXECUTION_PROGRESS_STALE
                || RECOVERY_SEQUENCE.getStage() != RecoverySequence.Stage.IDLE
                || config.isInQuietHours(System.currentTimeMillis())) {
            return;
        }

        if (!config.isAutoSoftRecovery()) {
            XposedLog.log("跳过软恢复：自动软恢复已关闭");
            return;
        }
        if (!RECOVERY_SEQUENCE.start(
                nowElapsedRealtime,
                state.getLastExecutionElapsedRealtime(),
                SOFT_RECOVERY_COOLDOWN_MILLIS)) {
            XposedLog.log("跳过软恢复：20 分钟冷却尚未结束");
            return;
        }
        if (!sendRecoveryBroadcast(
                WatchdogContract.ACTION_SESAME_EXECUTE, "execute")) {
            cancelRecoverySequence();
            return;
        }
        scheduleRecoveryCheck(RECOVERY_OBSERVATION_MILLIS);
    }

    private static void checkRecoveryResult() {
        try {
            if (config != null && config.isInQuietHours(System.currentTimeMillis())) {
                XposedLog.log("当前处于夜间免打扰时段，中止正在进行的故障恢复流程");
                cancelRecoverySequence();
                return;
            }
            RecoverySequence.Stage previousStage = RECOVERY_SEQUENCE.getStage();
            RecoverySequence.Decision decision = RECOVERY_SEQUENCE.onObservationTimeout(
                    state.getLastExecutionElapsedRealtime());
            if (decision == RecoverySequence.Decision.SUCCEEDED) {
                activeHardRecoveryRequestId = 0L;
                hardRecoveryRequestedElapsedRealtime = 0L;
                hardRecoveryOriginalAlipayPid = 0;
                hardRecoveryProcessRestartObserved = false;
                String recoveryKind = previousStage
                        == RecoverySequence.Stage.WAITING_HARD_RECOVERY_RESULT
                        ? "支付宝硬恢复成功"
                        : "软恢复成功";
                XposedLog.log(
                        recoveryKind + "，恢复阶段="
                                + WatchdogLogFormatter.recoveryStage(previousStage)
                                + "，最近一次执行="
                                + elapsedRealtime(state.getLastExecutionElapsedRealtime()));
            } else if (decision == RecoverySequence.Decision.SEND_RESTART) {
                XposedLog.log(
                        "发送立即执行广播后 "
                                + WatchdogLogFormatter.duration(RECOVERY_OBSERVATION_MILLIS)
                                + "仍无进度，升级为重启 Sesame-GR"
                                + "，恢复前最近执行=" + elapsedRealtime(
                                RECOVERY_SEQUENCE.getBaselineLastExecutionElapsedRealtime())
                                + "，当前最近执行=" + elapsedRealtime(
                                state.getLastExecutionElapsedRealtime()));
                if (sendRecoveryBroadcast(
                        WatchdogContract.ACTION_SESAME_RESTART, "restart")) {
                    scheduleRecoveryCheck(RECOVERY_OBSERVATION_MILLIS);
                } else {
                    cancelRecoverySequence();
                }
            } else if (decision == RecoverySequence.Decision.REQUEST_HARD_RECOVERY) {
                if (!config.isHardRecoveryEnabled()) {
                    RECOVERY_SEQUENCE.markExhausted();
                    XposedLog.log(
                            "重启 Sesame-GR 后仍无进度，软恢复流程已失败；"
                                    + "root 硬恢复未开启"
                                    + "，恢复前最近执行=" + elapsedRealtime(
                                    RECOVERY_SEQUENCE.getBaselineLastExecutionElapsedRealtime())
                                    + "，当前最近执行=" + elapsedRealtime(
                                    state.getLastExecutionElapsedRealtime()));
                } else if (sendHardRecoveryRequest()) {
                    scheduleRecoveryCheck(HARD_RECOVERY_OBSERVATION_MILLIS);
                } else {
                    RECOVERY_SEQUENCE.markExhausted();
                }
            } else if (decision == RecoverySequence.Decision.EXHAUSTED) {
                long baselineLastExecutionElapsedRealtime =
                        RECOVERY_SEQUENCE.getBaselineLastExecutionElapsedRealtime();
                activeHardRecoveryRequestId = 0L;
                hardRecoveryRequestedElapsedRealtime = 0L;
                hardRecoveryOriginalAlipayPid = 0;
                boolean processRestartObserved = hardRecoveryProcessRestartObserved;
                hardRecoveryProcessRestartObserved = false;
                XposedLog.log(
                        "请求支付宝硬恢复后 "
                                + WatchdogLogFormatter.duration(
                                HARD_RECOVERY_OBSERVATION_MILLIS)
                                + "仍未检测到新的执行进度，本轮恢复未成功"
                                + "，已检测到新支付宝进程="
                                + WatchdogLogFormatter.yesNo(processRestartObserved)
                                + "，恢复前最近执行=" + elapsedRealtime(
                                baselineLastExecutionElapsedRealtime)
                                + "，当前最近执行=" + elapsedRealtime(
                                state.getLastExecutionElapsedRealtime()));
                RECOVERY_SEQUENCE.finishFailedAttempt();
            }
        } catch (Throwable throwable) {
            XposedLog.logError("检查 SystemUI 恢复结果失败", throwable);
            cancelRecoverySequence();
        }
    }

    private static boolean sendRecoveryBroadcast(String action, String stage) {
        try {
            Intent intent = new Intent(action).setPackage(WatchdogContract.ALIPAY_PACKAGE);
            systemUiContext.sendBroadcast(intent);
            XposedLog.log(
                    "已发送软恢复广播，阶段=" + recoveryAction(stage)
                            + "，广播=" + action
                            + "，恢复前最近执行=" + elapsedRealtime(
                            RECOVERY_SEQUENCE.getBaselineLastExecutionElapsedRealtime()));
            return true;
        } catch (Throwable throwable) {
            XposedLog.logError(
                    "发送软恢复广播失败，阶段=" + recoveryAction(stage), throwable);
            return false;
        }
    }

    private static boolean sendHardRecoveryRequest() {
        long requestId = Math.max(1L, SystemClock.elapsedRealtime());
        try {
            Bundle request = new Bundle();
            request.putInt(
                    WatchdogContract.EXTRA_PROTOCOL_VERSION,
                    WatchdogContract.PROTOCOL_VERSION);
            request.putString(
                    WatchdogContract.EXTRA_PROTOCOL_TOKEN,
                    WatchdogContract.PROTOCOL_TOKEN);
            request.putString(
                    WatchdogContract.EXTRA_SOURCE_PACKAGE,
                    WatchdogContract.SYSTEM_UI_PACKAGE);
            request.putLong(
                    WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID,
                    requestId);
            request.putInt(WatchdogContract.EXTRA_TARGET_USER_ID, ALIPAY_USER_ID);
            request.putBoolean(
                    WatchdogContract.EXTRA_WAKE_SCREEN,
                    shouldWakeScreen());
            activeHardRecoveryRequestId = requestId;
            hardRecoveryRequestedElapsedRealtime = SystemClock.elapsedRealtime();
            hardRecoveryOriginalAlipayPid = state.getAlipayPid();
            hardRecoveryProcessRestartObserved = false;
            Bundle result = systemUiContext.getContentResolver().call(
                    Uri.parse(WatchdogContract.COMMAND_PROVIDER_URI),
                    WatchdogContract.METHOD_REQUEST_HARD_RECOVERY,
                    null,
                    request);
            if (result == null
                    || !result.getBoolean(
                    WatchdogContract.EXTRA_COMMAND_ACCEPTED, false)) {
                activeHardRecoveryRequestId = 0L;
                hardRecoveryRequestedElapsedRealtime = 0L;
                hardRecoveryOriginalAlipayPid = 0;
                hardRecoveryProcessRestartObserved = false;
                String message = result == null
                        ? "模块命令通道未返回结果"
                        : result.getString(
                        WatchdogContract.EXTRA_COMMAND_MESSAGE,
                        "模块命令通道拒绝请求");
                XposedLog.log("请求模块 root 执行器失败：" + message);
                return false;
            }
            XposedLog.log(
                    "已向模块 root 执行器请求支付宝硬恢复"
                            + "，请求编号=" + requestId
                            + "，支付宝用户=" + ALIPAY_USER_ID
                            + "，恢复前最近执行=" + elapsedRealtime(
                            RECOVERY_SEQUENCE.getBaselineLastExecutionElapsedRealtime()));
            return true;
        } catch (Throwable throwable) {
            activeHardRecoveryRequestId = 0L;
            hardRecoveryRequestedElapsedRealtime = 0L;
            hardRecoveryOriginalAlipayPid = 0;
            hardRecoveryProcessRestartObserved = false;
            XposedLog.logError("请求模块 root 执行器失败", throwable);
            return false;
        }
    }

    private static void handleHardRecoveryResult(Intent intent) {
        if (!isValidHardRecoveryResult(intent)) {
            return;
        }
        long requestId = intent.getLongExtra(
                WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID, 0L);
        if (activeForegroundHardRecoveryRequestId == requestId) {
            activeForegroundHardRecoveryRequestId = 0L;
            boolean foregroundFallbackSuccess = intent.getBooleanExtra(
                    WatchdogContract.EXTRA_HARD_RECOVERY_SUCCESS, false);
            String foregroundFallbackMessage = intent.getStringExtra(
                    WatchdogContract.EXTRA_HARD_RECOVERY_MESSAGE);
            if (foregroundFallbackSuccess) {
                XposedLog.log(
                        "root 硬回退命令已完成，等待支付宝 Activity onResume"
                                + "，请求编号=" + requestId
                                + "，结果=" + foregroundFallbackMessage);
                scheduleForegroundRecoveryTimeout();
            } else {
                activeForegroundRecoveryRequestId = 0L;
                workerHandler.removeCallbacks(FOREGROUND_RECOVERY_TIMEOUT_RUNNABLE);
                foregroundHardRecoveryAttempted = false;
                SystemUiKeyguardBridge.setOccluded(false);
                XposedLog.log(
                        "root 硬回退未执行成功，前台拉起流程结束"
                                + "，请求编号=" + requestId
                                + "，原因=" + foregroundFallbackMessage);
            }
            return;
        }
        if (activeHardRecoveryRequestId == 0L || activeHardRecoveryRequestId != requestId) {
            return;
        }

        boolean success = intent.getBooleanExtra(
                WatchdogContract.EXTRA_HARD_RECOVERY_SUCCESS, false);
        String message = intent.getStringExtra(
                WatchdogContract.EXTRA_HARD_RECOVERY_MESSAGE);
        if (success) {
            XposedLog.log(
                    "模块 root 执行器已完成支付宝硬恢复，等待新的心跳和执行进度"
                            + "，请求编号=" + requestId
                            + "，结果=" + message);
            scheduleRecoveryCheck(HARD_RECOVERY_OBSERVATION_MILLIS);
            return;
        }

        workerHandler.removeCallbacks(RECOVERY_CHECK_RUNNABLE);
        RECOVERY_SEQUENCE.markExhausted();
        RECOVERY_SEQUENCE.finishFailedAttempt();
        activeHardRecoveryRequestId = 0L;
        hardRecoveryRequestedElapsedRealtime = 0L;
        hardRecoveryOriginalAlipayPid = 0;
        hardRecoveryProcessRestartObserved = false;
        XposedLog.log(
                "支付宝硬恢复未执行成功，恢复流程已结束"
                        + "，请求编号=" + requestId
                        + "，原因=" + message);
    }

    private static void scheduleRecoveryCheck(long delayMillis) {
        workerHandler.removeCallbacks(RECOVERY_CHECK_RUNNABLE);
        workerHandler.postDelayed(RECOVERY_CHECK_RUNNABLE, delayMillis);
    }

    private static void cancelRecoverySequence() {
        if (workerHandler != null) {
            workerHandler.removeCallbacks(RECOVERY_CHECK_RUNNABLE);
        }
        RECOVERY_SEQUENCE.reset();
        activeHardRecoveryRequestId = 0L;
        hardRecoveryRequestedElapsedRealtime = 0L;
        hardRecoveryOriginalAlipayPid = 0;
        hardRecoveryProcessRestartObserved = false;
    }

    private static boolean isValidProtocol(Intent intent) {
        return intent.getIntExtra(
                        WatchdogContract.EXTRA_PROTOCOL_VERSION, -1)
                        == WatchdogContract.PROTOCOL_VERSION
                && WatchdogContract.PROTOCOL_TOKEN.equals(
                        intent.getStringExtra(WatchdogContract.EXTRA_PROTOCOL_TOKEN));
    }

    private static boolean isValidProbeEvent(Intent intent) {
        if (!isValidProtocol(intent)
                || !WatchdogContract.ALIPAY_PACKAGE.equals(
                intent.getStringExtra(WatchdogContract.EXTRA_SOURCE_PACKAGE))) {
            return false;
        }
        long eventElapsedRealtime = intent.getLongExtra(
                WatchdogContract.EXTRA_EVENT_ELAPSED_REALTIME, 0L);
        return eventElapsedRealtime > 0
                && eventElapsedRealtime
                <= SystemClock.elapsedRealtime() + MAX_EVENT_FUTURE_SKEW_MILLIS;
    }

    private static boolean isValidHardRecoveryResult(Intent intent) {
        return WatchdogContract.ACTION_HARD_RECOVERY_RESULT.equals(intent.getAction())
                && isValidProtocol(intent)
                && WatchdogContract.MODULE_PACKAGE.equals(
                intent.getStringExtra(WatchdogContract.EXTRA_SOURCE_PACKAGE))
                && intent.getLongExtra(
                WatchdogContract.EXTRA_HARD_RECOVERY_REQUEST_ID, 0L) > 0L;
    }

    private static NotificationStatusParser.StatusKind parseStatus(Intent intent) {
        String status = intent.getStringExtra(WatchdogContract.EXTRA_NOTIFICATION_STATUS);
        if (status == null) {
            return null;
        }
        try {
            return NotificationStatusParser.StatusKind.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Long getOptionalLong(Intent intent, String key) {
        return intent.hasExtra(key) ? intent.getLongExtra(key, 0L) : null;
    }

    private static final class WatchdogReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (WatchdogContract.ACTION_PROBE_EVENT.equals(action)) {
                    handleProbeEvent(intent);
                } else if (WatchdogContract.ACTION_CONFIG_UPDATE.equals(action)) {
                    handleConfigUpdate(intent);
                } else if (WatchdogContract.ACTION_HARD_RECOVERY_RESULT.equals(action)) {
                    handleHardRecoveryResult(intent);
                } else if (Intent.ACTION_SCREEN_ON.equals(action)
                        || Intent.ACTION_USER_PRESENT.equals(action)) {
                    handleScreenEvent(action);
                }
            } catch (Throwable throwable) {
                XposedLog.logError("处理 SystemUI 守护广播失败", throwable);
            }
        }
    }

    private static String stateSnapshot() {
        return state.snapshot(SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    private static String elapsedRealtime(long value) {
        return WatchdogLogFormatter.elapsedRealtime(
                value, SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    private static String recoveryAction(String stage) {
        return "execute".equals(stage) ? "立即执行" : "重启 Sesame-GR";
    }

    private static boolean hardRecoveryProcessRestarted() {
        return hardRecoveryRequestedElapsedRealtime > 0L
                && state.getLastHeartbeatElapsedRealtime()
                > hardRecoveryRequestedElapsedRealtime
                && state.getAlipayPid() > 0
                && state.getAlipayPid() != hardRecoveryOriginalAlipayPid;
    }
}
