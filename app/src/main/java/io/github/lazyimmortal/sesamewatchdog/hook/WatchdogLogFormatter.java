package io.github.lazyimmortal.sesamewatchdog.hook;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfig;

final class WatchdogLogFormatter {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private WatchdogLogFormatter() {
    }

    static String config(WatchdogConfig config) {
        String quietHoursSummary = config.isQuietHoursEnabled()
                ? String.format(Locale.getDefault(), "，夜间免打扰=是（%02d:%02d-%02d:%02d）",
                        config.getQuietHoursStartHour(), config.getQuietHoursStartMinute(),
                        config.getQuietHoursEndHour(), config.getQuietHoursEndMinute())
                : "，夜间免打扰=否";
        return "启用=" + yesNo(config.isEnabled())
                + "，启动宽限=" + config.getStartupGraceMinutes() + "分钟"
                + "，心跳超时=" + config.getHeartbeatTimeoutMinutes() + "分钟"
                + "，下次执行逾期宽限=" + config.getNextOverdueMinutes() + "分钟"
                + "，执行中超时=" + config.getRunningTimeoutMinutes() + "分钟"
                + "，执行进度停滞=" + config.getExecutionStallMinutes() + "分钟"
                + "，自动软恢复=" + yesNo(config.isAutoSoftRecovery())
                + "，root 硬恢复=" + yesNo(config.isHardRecoveryEnabled())
                + "，锁屏前台拉起=" + yesNo(config.isForegroundRecoveryEnabled())
                + "，前台拉起间隔=" + config.getForegroundRecoveryIntervalMinutes()
                + "分钟"
                + "，快速熄屏=" + yesNo(config.isFastSleepEnabled())
                + "，距离传感器避让=" + yesNo(config.isProximityAvoidanceEnabled())
                + quietHoursSummary;
    }

    static String elapsedRealtime(
            long value,
            long nowElapsedRealtime,
            long nowWallClockMillis) {
        if (value <= 0L) {
            return "未记录";
        }
        long wallClockMillis = nowWallClockMillis - nowElapsedRealtime + value;
        long differenceMillis = nowElapsedRealtime - value;
        String relative;
        if (Math.abs(differenceMillis) < 1_000L) {
            relative = "刚刚";
        } else if (differenceMillis > 0L) {
            relative = duration(differenceMillis) + "前";
        } else {
            relative = duration(-differenceMillis) + "后";
        }
        return wallClock(wallClockMillis) + "（" + relative + "）";
    }

    static String nullableElapsedRealtime(
            Long value,
            long nowElapsedRealtime,
            long nowWallClockMillis) {
        return value == null
                ? "未提供"
                : elapsedRealtime(value, nowElapsedRealtime, nowWallClockMillis);
    }

    static String wallClock(Long wallClockMillis) {
        return wallClockMillis == null ? "未提供" : wallClock(wallClockMillis.longValue());
    }

    static String wallClock(long wallClockMillis) {
        return new SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault())
                .format(new Date(wallClockMillis));
    }

    static String duration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1_000L;
        long days = totalSeconds / 86_400L;
        long hours = totalSeconds % 86_400L / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0L) {
            return days + "天" + hours + "小时";
        }
        if (hours > 0L) {
            return hours + "小时" + minutes + "分钟";
        }
        if (minutes > 0L) {
            return minutes + "分" + seconds + "秒";
        }
        return seconds + "秒";
    }

    static String status(NotificationStatusParser.StatusKind status) {
        switch (status) {
            case STARTING:
                return "启动中";
            case RUNNING:
                return "执行中";
            case NEXT_EXECUTION:
                return "等待下次执行";
            case LAST_EXECUTION:
                return "上次执行";
            case UNKNOWN:
            default:
                return "未知";
        }
    }

    static String fault(SystemUiWatchdogState.Fault fault) {
        switch (fault) {
            case NONE:
                return "正常";
            case DISABLED:
                return "观察已关闭";
            case NO_HEARTBEAT:
                return "启动宽限后仍未收到心跳";
            case HEARTBEAT_STALE:
                return "主 Looper 心跳超时";
            case EXECUTION_PROGRESS_STALE:
                return "执行进度停滞";
            case NEXT_EXECUTION_OVERDUE:
                return "下次执行已逾期";
            case RUNNING_TOO_LONG:
                return "执行中停留过久";
            default:
                return fault.name();
        }
    }

    static String trigger(String trigger) {
        if ("periodic".equals(trigger)) {
            return "定时检查";
        }
        if ("probe_event".equals(trigger)) {
            return "支付宝探针事件";
        }
        if ("config_update".equals(trigger)) {
            return "配置更新";
        }
        if ("android.intent.action.SCREEN_ON".equals(trigger)) {
            return "亮屏";
        }
        if ("android.intent.action.USER_PRESENT".equals(trigger)) {
            return "用户解锁";
        }
        return trigger;
    }

    static String recoveryStage(RecoverySequence.Stage stage) {
        switch (stage) {
            case WAITING_EXECUTE_RESULT:
                return "等待立即执行结果";
            case WAITING_RESTART_RESULT:
                return "等待重启 Sesame-GR 结果";
            case WAITING_HARD_RECOVERY_RESULT:
                return "等待支付宝硬恢复结果";
            case EXHAUSTED:
                return "软恢复已失败";
            case IDLE:
            default:
                return "空闲";
        }
    }

    static String yesNo(boolean value) {
        return value ? "是" : "否";
    }
}
