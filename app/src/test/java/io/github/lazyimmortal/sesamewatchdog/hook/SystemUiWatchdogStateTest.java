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

    private static long minutes(long value) {
        return TimeUnit.MINUTES.toMillis(value);
    }
}
