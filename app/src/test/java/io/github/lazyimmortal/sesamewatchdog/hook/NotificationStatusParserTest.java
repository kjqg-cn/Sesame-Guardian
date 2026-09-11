package io.github.lazyimmortal.sesamewatchdog.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class NotificationStatusParserTest {
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
    public void recognizesRunningNotification() {
        NotificationStatusParser.ParsedNotification parsed = parse(
                "执行中", "上次执行 19:21:47 收:119 帮:0", at(2026, 8, 10, 19, 22, 0));

        assertTrue(parsed.targetNotification);
        assertEquals(NotificationStatusParser.StatusKind.RUNNING, parsed.status);
        assertNotNull(parsed.lastExecutionAtWallClockMillis);
        assertEquals(at(2026, 8, 10, 19, 21, 47), parsed.lastExecutionAtWallClockMillis.longValue());
    }

    @Test
    public void recognizesStartingNotification() {
        NotificationStatusParser.ParsedNotification parsed = parse(
                "启动中", "暂无消息", at(2026, 8, 10, 19, 22, 0));

        assertTrue(parsed.targetNotification);
        assertEquals(NotificationStatusParser.StatusKind.STARTING, parsed.status);
        assertNull(parsed.nextExecutionAtWallClockMillis);
        assertNull(parsed.lastExecutionAtWallClockMillis);
    }

    @Test
    public void resolvesNextExecutionAcrossMidnight() {
        long now = at(2026, 8, 10, 23, 55, 0);
        NotificationStatusParser.ParsedNotification parsed = parse(
                "下次执行 00:05:00", "上次执行 23:50:00 收:1 帮:0", now);

        assertEquals(NotificationStatusParser.StatusKind.NEXT_EXECUTION, parsed.status);
        assertEquals(at(2026, 8, 11, 0, 5, 0), parsed.nextExecutionAtWallClockMillis.longValue());
        assertEquals(at(2026, 8, 10, 23, 50, 0), parsed.lastExecutionAtWallClockMillis.longValue());
    }

    @Test
    public void resolvesLastExecutionBeforeCurrentDayAcrossMidnight() {
        long now = at(2026, 8, 11, 0, 5, 0);
        NotificationStatusParser.ParsedNotification parsed = parse(
                "下次执行 00:15:00", "上次执行 23:50:00 收:1 帮:0", now);

        assertEquals(at(2026, 8, 11, 0, 15, 0), parsed.nextExecutionAtWallClockMillis.longValue());
        assertEquals(at(2026, 8, 10, 23, 50, 0), parsed.lastExecutionAtWallClockMillis.longValue());
    }

    @Test
    public void mapsWallClockTargetToElapsedRealtimeAnchor() {
        long nowWallClock = at(2026, 8, 10, 23, 55, 0);
        long targetWallClock = at(2026, 8, 11, 0, 5, 0);

        Long targetElapsedRealtime = NotificationStatusParser.mapWallClockToElapsedRealtime(
                targetWallClock, nowWallClock, 5_000_000L);

        assertEquals(5_600_000L, targetElapsedRealtime.longValue());
    }

    @Test
    public void rejectsNotificationWithoutCombinedSesameFeatures() {
        NotificationStatusParser.ParsedNotification wrongId = NotificationStatusParser.parse(
                98,
                NotificationStatusParser.CHANNEL_ID,
                NotificationStatusParser.SUB_TEXT,
                "执行中",
                null,
                at(2026, 8, 10, 19, 22, 0));
        NotificationStatusParser.ParsedNotification wrongChannel = NotificationStatusParser.parse(
                NotificationStatusParser.NOTIFICATION_ID,
                "other.channel",
                NotificationStatusParser.SUB_TEXT,
                "执行中",
                null,
                at(2026, 8, 10, 19, 22, 0));
        NotificationStatusParser.ParsedNotification wrongSubText = NotificationStatusParser.parse(
                NotificationStatusParser.NOTIFICATION_ID,
                NotificationStatusParser.CHANNEL_ID,
                "其他模块",
                "执行中",
                null,
                at(2026, 8, 10, 19, 22, 0));

        assertFalse(wrongId.targetNotification);
        assertFalse(wrongChannel.targetNotification);
        assertFalse(wrongSubText.targetNotification);
    }

    private static NotificationStatusParser.ParsedNotification parse(
            String title,
            String content,
            long nowWallClockMillis) {
        return NotificationStatusParser.parse(
                NotificationStatusParser.NOTIFICATION_ID,
                NotificationStatusParser.CHANNEL_ID,
                NotificationStatusParser.SUB_TEXT,
                title,
                content,
                nowWallClockMillis);
    }

    private static long at(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance(TEST_TIME_ZONE);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }
}
