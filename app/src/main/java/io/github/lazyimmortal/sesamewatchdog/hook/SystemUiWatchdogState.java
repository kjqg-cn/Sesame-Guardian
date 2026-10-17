package io.github.lazyimmortal.sesamewatchdog.hook;

import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfig;

final class SystemUiWatchdogState {
    enum Fault {
        NONE,
        DISABLED,
        NO_HEARTBEAT,
        HEARTBEAT_STALE,
        EXECUTION_PROGRESS_STALE,
        NEXT_EXECUTION_OVERDUE,
        RUNNING_TOO_LONG
    }

    enum ForegroundRecoveryTrigger {
        NONE,
        SCHEDULED_DUE,
        FALLBACK_INTERVAL
    }

    static final long SMART_WAKEUP_LEAD_TIME_MILLIS = 90_000L;
    static final long MIN_FOREGROUND_RECOVERY_COOLDOWN_MILLIS = 60_000L;

    private long startedElapsedRealtime;
    private int alipayPid;
    private long lastHeartbeatElapsedRealtime;
    private long lastNotificationElapsedRealtime;
    private long runningSinceElapsedRealtime;
    private long nextExecutionElapsedRealtime;
    private long lastExecutionElapsedRealtime;
    private long lastExecutionWallClockMillis;
    private boolean nextExecutionAdvancedWithoutProgress;
    private long lastScheduledTriggerTarget;

    SystemUiWatchdogState(long startedElapsedRealtime) {
        reset(startedElapsedRealtime);
    }

    void reset(long nowElapsedRealtime) {
        startedElapsedRealtime = nowElapsedRealtime;
        alipayPid = 0;
        lastHeartbeatElapsedRealtime = 0;
        lastNotificationElapsedRealtime = 0;
        runningSinceElapsedRealtime = 0;
        nextExecutionElapsedRealtime = 0;
        lastScheduledTriggerTarget = 0;
        lastExecutionElapsedRealtime = 0;
        lastExecutionWallClockMillis = 0;
        nextExecutionAdvancedWithoutProgress = false;
    }

    void onHeartbeat(int pid, long eventElapsedRealtime) {
        if (!acceptProcess(pid, eventElapsedRealtime)) {
            return;
        }
        if (eventElapsedRealtime > lastHeartbeatElapsedRealtime) {
            lastHeartbeatElapsedRealtime = eventElapsedRealtime;
        }
    }

    void onNotification(
            int pid,
            NotificationStatusParser.StatusKind status,
            long eventElapsedRealtime,
            Long nextExecutionElapsedRealtime,
            Long lastExecutionElapsedRealtime) {
        onNotification(
                pid,
                status,
                eventElapsedRealtime,
                nextExecutionElapsedRealtime,
                lastExecutionElapsedRealtime,
                null);
    }

    void onNotification(
            int pid,
            NotificationStatusParser.StatusKind status,
            long eventElapsedRealtime,
            Long nextExecutionElapsedRealtime,
            Long lastExecutionElapsedRealtime,
            Long lastExecutionWallClockMillis) {
        if (!acceptProcess(pid, eventElapsedRealtime)) {
            return;
        }
        if (eventElapsedRealtime < lastNotificationElapsedRealtime) {
            return;
        }

        long previousNextExecution = this.nextExecutionElapsedRealtime;
        long previousLastExecution = this.lastExecutionElapsedRealtime;
        long previousLastExecutionWallClock = this.lastExecutionWallClockMillis;
        boolean lastExecutionAdvanced;
        if (lastExecutionWallClockMillis != null) {
            lastExecutionAdvanced = lastExecutionWallClockMillis
                    > previousLastExecutionWallClock;
        } else {
            lastExecutionAdvanced = lastExecutionElapsedRealtime != null
                    && lastExecutionElapsedRealtime > previousLastExecution;
        }

        lastNotificationElapsedRealtime = eventElapsedRealtime;
        if (lastExecutionWallClockMillis != null) {
            this.lastExecutionWallClockMillis = lastExecutionWallClockMillis;
        }
        if (lastExecutionElapsedRealtime != null && (lastExecutionAdvanced
                || previousLastExecution == 0)) {
            this.lastExecutionElapsedRealtime = lastExecutionElapsedRealtime;
        }
        if (lastExecutionAdvanced) {
            nextExecutionAdvancedWithoutProgress = false;
        }
        if (nextExecutionElapsedRealtime != null) {
            if (!lastExecutionAdvanced
                    && previousNextExecution != 0
                    && nextExecutionElapsedRealtime > previousNextExecution) {
                nextExecutionAdvancedWithoutProgress = true;
            }
            this.nextExecutionElapsedRealtime = nextExecutionElapsedRealtime;
        }

        switch (status) {
            case STARTING:
                startedElapsedRealtime = eventElapsedRealtime;
                runningSinceElapsedRealtime = 0;
                this.nextExecutionElapsedRealtime = 0;
                nextExecutionAdvancedWithoutProgress = false;
                break;
            case RUNNING:
                this.nextExecutionElapsedRealtime = 0;
                if (runningSinceElapsedRealtime == 0) {
                    runningSinceElapsedRealtime = eventElapsedRealtime;
                }
                break;
            case NEXT_EXECUTION:
            case LAST_EXECUTION:
                runningSinceElapsedRealtime = 0;
                if (status == NotificationStatusParser.StatusKind.NEXT_EXECUTION
                        && nextExecutionElapsedRealtime == null) {
                    this.nextExecutionElapsedRealtime = 0;
                }
                break;
            case UNKNOWN:
                break;
        }
    }

    Fault evaluate(WatchdogConfig config, long nowElapsedRealtime) {
        if (!config.isEnabled()) {
            return Fault.DISABLED;
        }
        if (lastHeartbeatElapsedRealtime == 0) {
            if (nowElapsedRealtime - startedElapsedRealtime > config.getStartupGraceMillis()) {
                return Fault.NO_HEARTBEAT;
            }
            return Fault.NONE;
        }
        if (nowElapsedRealtime - lastHeartbeatElapsedRealtime
                > config.getHeartbeatTimeoutMillis()) {
            return Fault.HEARTBEAT_STALE;
        }
        if (runningSinceElapsedRealtime != 0
                && nowElapsedRealtime - runningSinceElapsedRealtime
                > config.getRunningTimeoutMillis()) {
            return Fault.RUNNING_TOO_LONG;
        }
        long executionProgressReference = lastExecutionElapsedRealtime != 0
                ? lastExecutionElapsedRealtime
                : startedElapsedRealtime;
        if (nextExecutionAdvancedWithoutProgress
                && nowElapsedRealtime - startedElapsedRealtime
                > config.getStartupGraceMillis()
                && nowElapsedRealtime - executionProgressReference
                > config.getExecutionStallMillis()) {
            return Fault.EXECUTION_PROGRESS_STALE;
        }
        if (nextExecutionElapsedRealtime != 0
                && nowElapsedRealtime - nextExecutionElapsedRealtime
                > config.getNextOverdueMillis()) {
            return Fault.NEXT_EXECUTION_OVERDUE;
        }
        return Fault.NONE;
    }

    String snapshot(long nowElapsedRealtime, long nowWallClockMillis) {
        return "支付宝PID=" + alipayPid
                + "，最近心跳=" + WatchdogLogFormatter.elapsedRealtime(
                lastHeartbeatElapsedRealtime, nowElapsedRealtime, nowWallClockMillis)
                + "，最近通知=" + WatchdogLogFormatter.elapsedRealtime(
                lastNotificationElapsedRealtime, nowElapsedRealtime, nowWallClockMillis)
                + "，执行中开始=" + WatchdogLogFormatter.elapsedRealtime(
                runningSinceElapsedRealtime, nowElapsedRealtime, nowWallClockMillis)
                + "，计划下次执行=" + WatchdogLogFormatter.elapsedRealtime(
                nextExecutionElapsedRealtime, nowElapsedRealtime, nowWallClockMillis)
                + "，最近一次执行=" + WatchdogLogFormatter.elapsedRealtime(
                lastExecutionElapsedRealtime, nowElapsedRealtime, nowWallClockMillis)
                + "，计划时间已推进但执行时间未更新="
                + WatchdogLogFormatter.yesNo(nextExecutionAdvancedWithoutProgress);
    }

    long getLastExecutionElapsedRealtime() {
        return lastExecutionElapsedRealtime;
    }

    int getAlipayPid() {
        return alipayPid;
    }

    long getLastHeartbeatElapsedRealtime() {
        return lastHeartbeatElapsedRealtime;
    }

    long getNextExecutionElapsedRealtime() {
        return nextExecutionElapsedRealtime;
    }

    long getRunningSinceElapsedRealtime() {
        return runningSinceElapsedRealtime;
    }

    long getStartedElapsedRealtime() {
        return startedElapsedRealtime;
    }

    long getLastScheduledTriggerTarget() {
        return lastScheduledTriggerTarget;
    }

    void markForegroundRecoveryTriggered(ForegroundRecoveryTrigger trigger) {
        if (trigger == ForegroundRecoveryTrigger.SCHEDULED_DUE) {
            lastScheduledTriggerTarget = nextExecutionElapsedRealtime;
        }
    }

    void resetScheduledTriggerTarget() {
        lastScheduledTriggerTarget = 0L;
    }

    ForegroundRecoveryTrigger checkForegroundRecoveryTrigger(
            WatchdogConfig config,
            long nowElapsedRealtime,
            long lastForegroundRecoveryElapsedRealtime) {
        if (config == null || !config.isEnabled() || !config.isForegroundRecoveryEnabled()) {
            return ForegroundRecoveryTrigger.NONE;
        }

        // 1. 冷却防抖：距上次拉起不足最小冷却时间，不重复拉起
        if (lastForegroundRecoveryElapsedRealtime != 0L
                && nowElapsedRealtime - lastForegroundRecoveryElapsedRealtime
                < MIN_FOREGROUND_RECOVERY_COOLDOWN_MILLIS) {
            return ForegroundRecoveryTrigger.NONE;
        }

        // 2. 判断主线程心跳与启动宽限期是否健康
        boolean isHeartbeatHealthy = (lastHeartbeatElapsedRealtime != 0L
                && (nowElapsedRealtime - lastHeartbeatElapsedRealtime
                <= config.getHeartbeatTimeoutMillis()))
                || (lastHeartbeatElapsedRealtime == 0L
                && (nowElapsedRealtime - startedElapsedRealtime
                <= config.getStartupGraceMillis()));

        // 3. 正在健康执行中：若任务正在运行、心跳正常且未超时，不触发拉起打断正常任务；
        // 反之，若主线程心跳已超时（冻结）或执行已超时（卡死），则放行并回退到保底拉起自愈
        boolean isRunningHealthy = runningSinceElapsedRealtime != 0L
                && isHeartbeatHealthy
                && (nowElapsedRealtime - runningSinceElapsedRealtime
                <= config.getRunningTimeoutMillis());
        if (isRunningHealthy) {
            return ForegroundRecoveryTrigger.NONE;
        }

        // 4. 判断通知与未来计划时间的新鲜度：
        // 通知不能长时间停滞（不超过进度停滞阈值），且计划跨度合理（不超过进度停滞阈值）
        boolean isNotificationFresh = lastNotificationElapsedRealtime != 0L
                && (nowElapsedRealtime - lastNotificationElapsedRealtime
                <= config.getExecutionStallMillis());

        boolean hasValidFutureSchedule = nextExecutionElapsedRealtime > 0L
                && nowElapsedRealtime < nextExecutionElapsedRealtime
                && isHeartbeatHealthy
                && isNotificationFresh
                && (nextExecutionElapsedRealtime - lastNotificationElapsedRealtime
                <= config.getExecutionStallMillis())
                && runningSinceElapsedRealtime == 0L;

        if (hasValidFutureSchedule) {
            // 智能按需唤醒模式：
            // 若针对当前目标时间已触发过唤醒，且目标时间尚未到达，不重复拉起
            if (lastScheduledTriggerTarget == nextExecutionElapsedRealtime) {
                return ForegroundRecoveryTrigger.NONE;
            }
            // 到达提前量窗口（<= 90秒），触发智能按需拉起
            if (nowElapsedRealtime >= nextExecutionElapsedRealtime - SMART_WAKEUP_LEAD_TIME_MILLIS) {
                return ForegroundRecoveryTrigger.SCHEDULED_DUE;
            }
            // 尚未到达窗口期，静默休眠省电
            return ForegroundRecoveryTrigger.NONE;
        }

        // 5. 保底回退模式（无计划 / 计划过期 / 心跳超时 / 通知停滞 / 任务卡死）：
        // 回退至配置的固定保底拉起间隔
        if (lastForegroundRecoveryElapsedRealtime == 0L
                || nowElapsedRealtime - lastForegroundRecoveryElapsedRealtime
                >= config.getForegroundRecoveryIntervalMillis()) {
            return ForegroundRecoveryTrigger.FALLBACK_INTERVAL;
        }

        return ForegroundRecoveryTrigger.NONE;
    }

    private boolean acceptProcess(int pid, long eventElapsedRealtime) {
        if (pid <= 0 || eventElapsedRealtime <= 0) {
            return false;
        }
        if (alipayPid != 0 && alipayPid != pid) {
            startedElapsedRealtime = eventElapsedRealtime;
            lastHeartbeatElapsedRealtime = 0;
            lastNotificationElapsedRealtime = 0;
            runningSinceElapsedRealtime = 0;
            nextExecutionElapsedRealtime = 0;
            lastScheduledTriggerTarget = 0L;
            nextExecutionAdvancedWithoutProgress = false;
        }
        alipayPid = pid;
        return true;
    }
}
