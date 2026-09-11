package io.github.lazyimmortal.sesamewatchdog.logging;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagnosticLogStore {
    public static final long MAX_TOTAL_BYTES = DiagnosticLogFiles.MAX_FILE_BYTES * 2L;
    public static final int DEFAULT_DISPLAY_LINES = 300;

    private static final String DIRECTORY_NAME = "diagnostics";
    private static final int MAX_SOURCE_LENGTH = 32;
    private static final int MAX_MESSAGE_LENGTH = 4_096;

    private DiagnosticLogStore() {
    }

    public static void append(
            Context context,
            long wallClockMillis,
            String source,
            String message) throws IOException {
        String safeSource = sanitize(source, MAX_SOURCE_LENGTH);
        if ("SystemUI".equals(safeSource)) {
            safeSource = "系统界面";
        }
        String safeMessage = sanitize(message, MAX_MESSAGE_LENGTH);
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date(wallClockMillis));
        DiagnosticLogFiles.append(
                getDirectory(context),
                timestamp + " [" + safeSource + "] " + safeMessage + "\n");
    }

    public static String read(Context context) throws IOException {
        return DiagnosticLogFiles.read(getDirectory(context));
    }

    public static String readNewestFirst(Context context, int maxLines) throws IOException {
        String logs = read(context);
        if (logs.isEmpty()) {
            return logs;
        }
        String[] lines = logs.split("\\R");
        int firstLine = Math.max(0, lines.length - Math.max(1, maxLines));
        StringBuilder result = new StringBuilder();
        for (int index = lines.length - 1; index >= firstLine; index--) {
            if (lines[index].isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(lines[index]);
        }
        return result.toString();
    }

    public static void clear(Context context) throws IOException {
        DiagnosticLogFiles.clear(getDirectory(context));
    }

    public static long sizeBytes(Context context) {
        return DiagnosticLogFiles.sizeBytes(getDirectory(context));
    }

    private static File getDirectory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY_NAME);
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }
}
