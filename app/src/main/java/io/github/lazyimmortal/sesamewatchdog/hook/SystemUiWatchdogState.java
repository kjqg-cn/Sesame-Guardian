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

    private long startedElapsedRealtime;
    private int alipayPid;
    private long lastHeartbeatElapsedRealtime;
    private long lastNotificationElapsedRealtime;
    private long runningSinceElapsedRealtime;
    private long nextExecutionElapsedRealtime;
    private long lastExecutionElapsedRealtime;
    private long lastExecutionWallClockMillis;
    private boolean nextExecutionAdvancedWithoutProgress;

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
            nextExecutionAdvancedWithoutProgress = false;
        }
        alipayPid = pid;
        return true;
    }
}
