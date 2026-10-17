package io.github.lazyimmortal.sesamewatchdog.hook;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfig;

public final class SystemUiWatchdogStateTest {
    @Test
    public void waitsForFirstHeartbeatDuringStartupGrace() {
        long started = 1_000L;
        WatchdogConfig config = WatchdogConfig.create(true, 10, 10, 10, 45);
        SystemUiWatchdogState state = new SystemUiWatchdogState(started);

        assertEquals(
                SystemUiWatchdogState.Fault.NONE,
                state.evaluate(config, started + minutes(10)));
        assertEquals(
                SystemUiWatchdogState.Fault.NO_HEARTBEAT,
                state.evaluate(config, started + minutes(10) + 1));
    }

    @Test
    public void detectsStaleHeartbeat() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 10, 10, 45);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);

        assertEquals(
                SystemUiWatchdogState.Fault.HEARTBEAT_STALE,
                state.evaluate(config, 2_000L + minutes(10) + 1));
    }

    @Test
    public void repeatedRunningNotificationDoesNotResetTimeout() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 10, 45);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.RUNNING,
                3_000L,
                null,
                null);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.RUNNING,
                3_000L + minutes(20),
                null,
                3_000L + minutes(19));
        long now = 3_000L + minutes(45) + 1;
        state.onHeartbeat(100, now - 1);

        assertEquals(
                SystemUiWatchdogState.Fault.RUNNING_TOO_LONG,
                state.evaluate(config, now));
    }

    @Test
    public void detectsOverdueNextExecution() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 10, 45);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        long nextExecution = 10_000L;
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                nextExecution,
                null);
        long now = nextExecution + minutes(10) + 1;
        state.onHeartbeat(100, now - 1);

        assertEquals(
                SystemUiWatchdogState.Fault.NEXT_EXECUTION_OVERDUE,
                state.evaluate(config, now));
    }

    @Test
    public void detectsExecutionProgressStallAfterNextExecutionIsRescheduled() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 120, 45, 30, false);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        long lastExecution = 3_000L;
        long firstNextExecution = 10_000L;
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                4_000L,
                firstNextExecution,
                lastExecution);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                5_000L,
                firstNextExecution + minutes(5),
                lastExecution);
        long now = lastExecution + minutes(30) + 1;
        state.onHeartbeat(100, now - 1);

        assertEquals(
                SystemUiWatchdogState.Fault.EXECUTION_PROGRESS_STALE,
                state.evaluate(config, now));
    }

    @Test
    public void doesNotFlagSingleNextExecutionAsStalled() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 120, 45, 30, false);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        long lastExecution = 3_000L;
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                4_000L,
                lastExecution + minutes(5),
                lastExecution);
        long now = lastExecution + minutes(30) + 1;
        state.onHeartbeat(100, now - 1);

        assertEquals(SystemUiWatchdogState.Fault.NONE, state.evaluate(config, now));
    }

    @Test
    public void sameWallClockExecutionDoesNotLookLikeNewProgress() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 120, 45, 120, false);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        long lastExecutionWallClock = 1_700_000_000_000L;
        long firstLastExecution = 3_000L;
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                4_000L,
                10_000L,
                firstLastExecution,
                lastExecutionWallClock);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                5_000L,
                10_000L + minutes(5),
                firstLastExecution + 900L,
                lastExecutionWallClock);

        long now = firstLastExecution + minutes(30) + 1;
        state.onHeartbeat(100, now - 1);

        assertEquals(firstLastExecution, state.getLastExecutionElapsedRealtime());
        assertEquals(
                SystemUiWatchdogState.Fault.NONE,
                state.evaluate(config, now));
    }

    @Test
    public void newProcessClearsPreviousRunningState() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 10, 45);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.RUNNING,
                3_000L,
                null,
                null);
        long now = 3_000L + minutes(60);
        state.onHeartbeat(200, now - 1);

        assertEquals(SystemUiWatchdogState.Fault.NONE, state.evaluate(config, now));
    }

    @Test
    public void newProcessKeepsExecutionBaselineAndDetectsMissingProgress() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 120, 45, 15, false);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        long lastExecution = 3_000L;
        long lastExecutionWallClock = 1_700_000_000_000L;
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                4_000L,
                10_000L,
                lastExecution,
                lastExecutionWallClock);

        long newProcessStarted = minutes(60);
        state.onHeartbeat(200, newProcessStarted);
        state.onNotification(
                200,
                NotificationStatusParser.StatusKind.STARTING,
                newProcessStarted + 1_000L,
                null,
                null,
                null);
        state.onNotification(
                200,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                newProcessStarted + 2_000L,
                newProcessStarted + minutes(5),
                lastExecution + 500L,
                lastExecutionWallClock);
        state.onNotification(
                200,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                newProcessStarted + minutes(5),
                newProcessStarted + minutes(10),
                lastExecution + 1_000L,
                lastExecutionWallClock);

        long now = newProcessStarted + minutes(16) + 1;
        state.onHeartbeat(200, now - 1);

        assertEquals(lastExecution, state.getLastExecutionElapsedRealtime());
        assertEquals(
                SystemUiWatchdogState.Fault.EXECUTION_PROGRESS_STALE,
                state.evaluate(config, now));
    }

    @Test
    public void detectsMissingProgressWhenNoExecutionHasEverBeenObserved() {
        WatchdogConfig config = WatchdogConfig.create(true, 15, 120, 120, 45, 15, false);
        long started = 1_000L;
        SystemUiWatchdogState state = new SystemUiWatchdogState(started);
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                3_000L,
                started + minutes(5),
                null);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                started + minutes(5),
                started + minutes(10),
                null);

        long now = started + minutes(15) + 1;
        state.onHeartbeat(100, now - 1);

        assertEquals(
                SystemUiWatchdogState.Fault.EXECUTION_PROGRESS_STALE,
                state.evaluate(config, now));
    }

    @Test
    public void disabledConfigStopsObservation() {
        WatchdogConfig config = WatchdogConfig.create(false, 15, 10, 10, 45);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);

        assertEquals(
                SystemUiWatchdogState.Fault.DISABLED,
                state.evaluate(config, 1_000L + minutes(60)));
    }

    @Test
    public void clampsThresholdMinutesToSafeRange() {
        WatchdogConfig config = WatchdogConfig.create(true, 0, -1, 5_000, 45, 5_000, false);

        assertEquals(1, config.getStartupGraceMinutes());
        assertEquals(1, config.getHeartbeatTimeoutMinutes());
        assertEquals(24 * 60, config.getNextOverdueMinutes());
        assertEquals(45, config.getRunningTimeoutMinutes());
        assertEquals(24 * 60, config.getExecutionStallMinutes());
        assertEquals(false, config.isAutoSoftRecovery());
        assertEquals(false, config.isHardRecoveryEnabled());
    }

    @Test
    public void hardRecoveryIsExplicitlyOptIn() {
        assertEquals(false, WatchdogConfig.defaults().isHardRecoveryEnabled());
        assertEquals(
                true,
                WatchdogConfig.create(true, 15, 10, 10, 45, 15, true, true)
                        .isHardRecoveryEnabled());
    }

    @Test
    public void smartWakeupSuppressedWhenTargetIsFarAway() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(30);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);

        // 距上次拉起已过 10 分钟（> 5分钟保底），心跳正常，但离下次执行还有 20 分钟，不应盲目拉起
        long now = 2_000L + minutes(10);
        state.onHeartbeat(100, now - 1_000L);
        long lastRecovery = 2_000L;
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, now, lastRecovery));
    }

    @Test
    public void smartWakeupTriggersWithinLeadTimeWindow() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(30);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);

        // 距离 target 提前 90 秒时进入唤醒窗口，心跳正常
        long nowAtLeadEdge = target - SystemUiWatchdogState.SMART_WAKEUP_LEAD_TIME_MILLIS;
        state.onHeartbeat(100, nowAtLeadEdge - 1_000L);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.SCHEDULED_DUE,
                state.checkForegroundRecoveryTrigger(config, nowAtLeadEdge, 2_000L));

        // 距离 target 提前 30 秒时同样处于窗口期
        long nowInsideWindow = target - 30_000L;
        state.onHeartbeat(100, nowInsideWindow - 1_000L);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.SCHEDULED_DUE,
                state.checkForegroundRecoveryTrigger(config, nowInsideWindow, 2_000L));
    }

    @Test
    public void smartWakeupDeduplicatesForSameTarget() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(30);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);

        long nowInsideWindow = target - 30_000L;
        state.onHeartbeat(100, nowInsideWindow - 1_000L);
        // 首次判定触发
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.SCHEDULED_DUE,
                state.checkForegroundRecoveryTrigger(config, nowInsideWindow, 2_000L));

        // 模拟拉起请求成功，内部标记该目标已唤醒
        state.markForegroundRecoveryTriggered(SystemUiWatchdogState.ForegroundRecoveryTrigger.SCHEDULED_DUE);

        // 同一目标不再重复触发
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, nowInsideWindow, 2_000L));
    }

    @Test
    public void newProcessClearsScheduledTriggerTargetAndAllowsWakeup() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(30);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);

        long nowInsideWindow = target - 30_000L;
        state.onHeartbeat(100, nowInsideWindow - 1_000L);
        state.markForegroundRecoveryTriggered(SystemUiWatchdogState.ForegroundRecoveryTrigger.SCHEDULED_DUE);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, nowInsideWindow, 2_000L));

        // 新进程出现（PID 从 100 变成 200），旧目标已去重标记被自动清除
        state.onHeartbeat(200, nowInsideWindow);
        state.onNotification(
                200,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                nowInsideWindow,
                target,
                null);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.SCHEDULED_DUE,
                state.checkForegroundRecoveryTrigger(config, nowInsideWindow, 2_000L));
    }

    @Test
    public void smartWakeupSuppressedDuringActiveRunning() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(5);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.RUNNING,
                2_500L,
                null,
                null);

        long nowInsideWindow = target - 30_000L;
        state.onHeartbeat(100, nowInsideWindow - 1_000L);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, nowInsideWindow, 2_000L));
    }

    @Test
    public void smartWakeupSuppressedDuringCooldown() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(30);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);

        long nowInsideWindow = target - 30_000L;
        state.onHeartbeat(100, nowInsideWindow - 1_000L);
        // 距上次拉起不足 60 秒冷却，应被抑制
        long recentRecovery = nowInsideWindow - 20_000L;
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, nowInsideWindow, recentRecovery));
    }

    @Test
    public void fallbackTriggeredWhenNoScheduleAndIntervalElapsed() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);

        // 首次拉起（lastForegroundRecoveryElapsedRealtime == 0）
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, 2_000L, 0L));

        // 距上次拉起不满 5 分钟，不拉起
        long now4Min = 2_000L + minutes(4);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, now4Min, 2_000L));

        // 达到 5 分钟保底间隔，触发保底拉起
        long now5Min = 2_000L + minutes(5);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, now5Min, 2_000L));
    }

    @Test
    public void fallbackTriggeredWhenScheduleOverdue() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(5);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);

        // 目标时间已过，未产生新通知
        long nowOverdue = target + 1_000L;
        state.onHeartbeat(100, nowOverdue - 1);

        // 距上次拉起不足保底间隔时返回 NONE
        long recentRecovery = nowOverdue - minutes(2);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, nowOverdue, recentRecovery));

        // 距上次拉起达到保底间隔（5分钟）时触发保底拉起
        long oldRecovery = nowOverdue - minutes(5);
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, nowOverdue, oldRecovery));
    }

    @Test
    public void fallbackTriggeredWhenHeartbeatStale() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long target = 2_000L + minutes(30);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                target,
                null);

        // 心跳超时（> 10分钟未更新），不能盲目信任计划时间进入休眠，必须回退到保底机制
        long nowHeartbeatStale = 2_000L + minutes(15);
        long oldRecovery = 2_000L;
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, nowHeartbeatStale, oldRecovery));
    }

    @Test
    public void foregroundRecoveryDisabledReturnsNone() {
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, false, 5);
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);

        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.NONE,
                state.checkForegroundRecoveryTrigger(config, 2_000L + minutes(10), 0L));
    }

    @Test
    public void runningWithStaleHeartbeatTriggersFallback() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.RUNNING,
                3_000L,
                null,
                null);

        // 心跳停留在 2000L，当前时间心跳已超过 10 分钟超时
        long now = 2_000L + minutes(12);
        long lastRecovery = 2_000L;
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, now, lastRecovery));
    }

    @Test
    public void runningTimeoutTriggersFallback() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        long runningStart = 3_000L;
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.RUNNING,
                runningStart,
                null,
                null);

        // 持续处于执行中超过 45 分钟，虽然心跳正常，但属于卡死故障
        long now = runningStart + minutes(46);
        state.onHeartbeat(100, now - 1_000L);
        long lastRecovery = 2_000L;
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, now, lastRecovery));
    }

    @Test
    public void staleNotificationFallsBackToInterval() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        // 通知在 2000L 收到，虽然计划时间在更远的未来，但通知已经超过 60 分钟未更新
        long farTarget = 2_000L + minutes(120);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                farTarget,
                null);

        long now = 2_000L + minutes(65);
        state.onHeartbeat(100, now - 1_000L); // 心跳正常
        long lastRecovery = now - minutes(5);  // 达到 5 分钟保底间隔
        // 通知已停滞，不应盲信旧计划，应触发保底拉起
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, now, lastRecovery));
    }

    @Test
    public void excessivelyFarScheduleFallsBackToInterval() {
        WatchdogConfig config = WatchdogConfig.defaults();
        SystemUiWatchdogState state = new SystemUiWatchdogState(1_000L);
        state.onHeartbeat(100, 2_000L);
        // 通知刚收到，但计划时间跨度达 24 小时（远超 60 分钟停滞上限）
        long extremeTarget = 2_000L + minutes(24 * 60);
        state.onNotification(
                100,
                NotificationStatusParser.StatusKind.NEXT_EXECUTION,
                2_000L,
                extremeTarget,
                null);

        long now = 2_000L + minutes(6);
        state.onHeartbeat(100, now - 1_000L); // 心跳正常
        long lastRecovery = 2_000L;           // 达到 5 分钟保底间隔
        // 不应盲目等待 24 小时，而应触发保底拉起
        assertEquals(
                SystemUiWatchdogState.ForegroundRecoveryTrigger.FALLBACK_INTERVAL,
                state.checkForegroundRecoveryTrigger(config, now, lastRecovery));
    }

    private static long minutes(long value) {
        return TimeUnit.MINUTES.toMillis(value);
    }
}
