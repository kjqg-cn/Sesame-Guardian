package io.github.lazyimmortal.sesamewatchdog.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class WatchdogConfigTest {
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
    public void defaultsIncludeExpectedQuietHoursSettings() {
        WatchdogConfig config = WatchdogConfig.defaults();
        assertFalse(config.isQuietHoursEnabled());
        assertEquals(1, config.getQuietHoursStartHour());
        assertEquals(0, config.getQuietHoursStartMinute());
        assertEquals(6, config.getQuietHoursEndHour());
        assertEquals(30, config.getQuietHoursEndMinute());
        assertTrue(config.isFastSleepEnabled());
    }

    @Test
    public void preservesFastSleepConfiguration() {
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, true, 5,
                false, 1, 0, 6, 30, false);
        assertFalse(config.isFastSleepEnabled());
    }

    @Test
    public void clampsHourAndMinuteRanges() {
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, true, 5,
                true, -5, -1, 30, 99);
        assertEquals(0, config.getQuietHoursStartHour());
        assertEquals(0, config.getQuietHoursStartMinute());
        assertEquals(23, config.getQuietHoursEndHour());
        assertEquals(59, config.getQuietHoursEndMinute());
    }

    @Test
    public void disabledQuietHoursNeverSuppresses() {
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, true, 5,
                false, 1, 0, 6, 30);
        long atOneThirty = timeAt(1, 30);
        assertFalse(config.isInQuietHours(atOneThirty));
    }

    @Test
    public void evaluatesNormalQuietHoursWindow() {
        // 01:00 - 06:30
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, true, 5,
                true, 1, 0, 6, 30);

        assertFalse(config.isInQuietHours(timeAt(0, 59)));
        assertTrue(config.isInQuietHours(timeAt(1, 0)));
        assertTrue(config.isInQuietHours(timeAt(3, 45)));
        assertTrue(config.isInQuietHours(timeAt(6, 29)));
        assertFalse(config.isInQuietHours(timeAt(6, 30)));
        assertFalse(config.isInQuietHours(timeAt(12, 0)));
        assertFalse(config.isInQuietHours(timeAt(23, 59)));
    }

    @Test
    public void evaluatesCrossMidnightQuietHoursWindow() {
        // 23:00 - 06:30 (crosses midnight)
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, true, 5,
                true, 23, 0, 6, 30);

        assertFalse(config.isInQuietHours(timeAt(22, 59)));
        assertTrue(config.isInQuietHours(timeAt(23, 0)));
        assertTrue(config.isInQuietHours(timeAt(23, 59)));
        assertTrue(config.isInQuietHours(timeAt(0, 0)));
        assertTrue(config.isInQuietHours(timeAt(4, 0)));
        assertTrue(config.isInQuietHours(timeAt(6, 29)));
        assertFalse(config.isInQuietHours(timeAt(6, 30)));
        assertFalse(config.isInQuietHours(timeAt(12, 0)));
    }

    @Test
    public void evaluatesEqualStartAndEndTimeAsDisabled() {
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, true, 5,
                true, 1, 0, 1, 0);
        assertFalse(config.isInQuietHours(timeAt(1, 0)));
        assertFalse(config.isInQuietHours(timeAt(12, 0)));
        assertFalse(config.isInQuietHours(timeAt(23, 59)));
    }

    @Test
    public void toStringIncludesQuietHoursSummary() {
        WatchdogConfig config = WatchdogConfig.create(
                true, 15, 10, 10, 45, 60, false, false, true, 5,
                true, 1, 0, 6, 30);
        assertTrue(config.toString().contains("quietHoursEnabled=true"));
        assertTrue(config.toString().contains("quietHours=01:00-06:30"));
    }

    private static long timeAt(int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TEST_TIME_ZONE);
        calendar.set(2026, Calendar.OCTOBER, 18, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
