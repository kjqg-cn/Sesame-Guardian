package io.github.lazyimmortal.sesamewatchdog.hook;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class WatchdogLogFormatterTest {
    private static final TimeZone TEST_TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private TimeZone originalTimeZone;

    @Before
    public void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TEST_TIME_ZONE);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void mapsElapsedRealtimeToReadableWallClock() {
        long nowWallClock = at(2026, 8, 14, 12, 0, 0);

        assertEquals(
                "2026-08-14 11:59:55（5秒前）",
                WatchdogLogFormatter.elapsedRealtime(5_000L, 10_000L, nowWallClock));
        assertEquals(
                "2026-08-14 12:00:05（5秒后）",
                WatchdogLogFormatter.elapsedRealtime(15_000L, 10_000L, nowWallClock));
        assertEquals(
                "未记录",
                WatchdogLogFormatter.elapsedRealtime(0L, 10_000L, nowWallClock));
    }

    @Test
    public void formatsDurationsForLogs() {
        assertEquals("45秒", WatchdogLogFormatter.duration(45_999L));
        assertEquals("2分5秒", WatchdogLogFormatter.duration(125_000L));
        assertEquals("3小时4分钟", WatchdogLogFormatter.duration(11_040_000L));
    }

    @Test
    public void translatesStateValues() {
        assertEquals(
                "执行进度停滞",
                WatchdogLogFormatter.fault(
                        SystemUiWatchdogState.Fault.EXECUTION_PROGRESS_STALE));
        assertEquals(
                "等待下次执行",
                WatchdogLogFormatter.status(
                        NotificationStatusParser.StatusKind.NEXT_EXECUTION));
    }

    private static long at(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance(TEST_TIME_ZONE);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }
}
