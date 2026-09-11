package io.github.lazyimmortal.sesamewatchdog.hook;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NotificationStatusParser {
    static final int NOTIFICATION_ID = 99;
    static final String CHANNEL_ID = "io.github.lazyimmortal.sesame.ANTFOREST_NOTIFY_CHANNEL";
    static final String SUB_TEXT = "芝麻粒";

    private static final Pattern CLOCK_TIME_PATTERN = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})");

    private NotificationStatusParser() {
    }

    static ParsedNotification parse(
            int notificationId,
            String channelId,
            CharSequence subText,
            CharSequence title,
            CharSequence content,
            long nowWallClockMillis) {
        String normalizedSubText = normalize(subText);
        String normalizedTitle = normalize(title);
        String normalizedContent = normalize(content);
        if (!isTargetNotification(notificationId, channelId, normalizedSubText)) {
            return ParsedNotification.notTarget();
        }

        StatusKind status = detectStatus(normalizedTitle, normalizedContent);
        Long nextExecutionAt = null;
        Long lastExecutionAt = null;

        if (startsWith(normalizedTitle, "下次执行")) {
            nextExecutionAt = resolveClockTime(
                    extractClockTime(normalizedTitle, "下次执行"),
                    nowWallClockMillis,
                    true,
                    TimeZone.getDefault());
        }
        if (startsWith(normalizedContent, "上次执行")) {
            lastExecutionAt = resolveClockTime(
                    extractClockTime(normalizedContent, "上次执行"),
                    nowWallClockMillis,
                    false,
                    TimeZone.getDefault());
        }

        return new ParsedNotification(true, status, nextExecutionAt, lastExecutionAt);
    }

    static boolean isTargetNotification(int notificationId, String channelId, CharSequence subText) {
        return notificationId == NOTIFICATION_ID
                && CHANNEL_ID.equals(channelId)
                && SUB_TEXT.equals(normalize(subText));
    }

    static Long resolveClockTime(
            String clockTime,
            long nowWallClockMillis,
            boolean future,
            TimeZone timeZone) {
        if (clockTime == null) {
            return null;
        }

        Matcher matcher = CLOCK_TIME_PATTERN.matcher(clockTime);
        if (!matcher.matches()) {
            return null;
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        int second = Integer.parseInt(matcher.group(3));
        if (hour > 23 || minute > 59 || second > 59) {
            return null;
        }

        Calendar candidate = Calendar.getInstance(timeZone);
        candidate.setTimeInMillis(nowWallClockMillis);
        candidate.set(Calendar.HOUR_OF_DAY, hour);
        candidate.set(Calendar.MINUTE, minute);
        candidate.set(Calendar.SECOND, second);
        candidate.set(Calendar.MILLISECOND, 0);

        if (future && candidate.getTimeInMillis() < nowWallClockMillis) {
            candidate.add(Calendar.DAY_OF_MONTH, 1);
        } else if (!future && candidate.getTimeInMillis() > nowWallClockMillis) {
            candidate.add(Calendar.DAY_OF_MONTH, -1);
        }
        return candidate.getTimeInMillis();
    }

    static Long mapWallClockToElapsedRealtime(
            Long targetWallClockMillis,
            long nowWallClockMillis,
            long nowElapsedRealtime) {
        if (targetWallClockMillis == null) {
            return null;
        }
        return nowElapsedRealtime + targetWallClockMillis - nowWallClockMillis;
    }

    private static StatusKind detectStatus(String title, String content) {
        if (startsWith(title, "执行中")) {
            return StatusKind.RUNNING;
        }
        if (startsWith(title, "启动中")) {
            return StatusKind.STARTING;
        }
        if (startsWith(title, "下次执行")) {
            return StatusKind.NEXT_EXECUTION;
        }
        if (startsWith(content, "上次执行")) {
            return StatusKind.LAST_EXECUTION;
        }
        return StatusKind.UNKNOWN;
    }

    private static String extractClockTime(String text, String prefix) {
        if (!startsWith(text, prefix)) {
            return null;
        }
        String remainder = text.substring(prefix.length()).trim();
        Matcher matcher = CLOCK_TIME_PATTERN.matcher(remainder);
        return matcher.lookingAt() ? matcher.group() : null;
    }

    private static boolean startsWith(String text, String prefix) {
        return text != null && text.startsWith(prefix);
    }

    private static String normalize(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    enum StatusKind {
        UNKNOWN,
        STARTING,
        RUNNING,
        NEXT_EXECUTION,
        LAST_EXECUTION
    }

    static final class ParsedNotification {
        final boolean targetNotification;
        final StatusKind status;
        final Long nextExecutionAtWallClockMillis;
        final Long lastExecutionAtWallClockMillis;

        private ParsedNotification(
                boolean targetNotification,
                StatusKind status,
                Long nextExecutionAtWallClockMillis,
                Long lastExecutionAtWallClockMillis) {
            this.targetNotification = targetNotification;
            this.status = status;
            this.nextExecutionAtWallClockMillis = nextExecutionAtWallClockMillis;
            this.lastExecutionAtWallClockMillis = lastExecutionAtWallClockMillis;
        }

        private static ParsedNotification notTarget() {
            return new ParsedNotification(false, StatusKind.UNKNOWN, null, null);
        }
    }
}
