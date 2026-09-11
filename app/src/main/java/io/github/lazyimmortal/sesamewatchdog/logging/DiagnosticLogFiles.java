package io.github.lazyimmortal.sesamewatchdog.logging;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class DiagnosticLogFiles {
    static final long MAX_FILE_BYTES = 512L * 1024L;
    static final String CURRENT_FILE_NAME = "watchdog.log";
    static final String PREVIOUS_FILE_NAME = "watchdog.log.1";

    private DiagnosticLogFiles() {
    }

    static synchronized void append(File directory, String line) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("创建日志目录失败：" + directory);
        }

        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        File currentFile = new File(directory, CURRENT_FILE_NAME);
        if (currentFile.isFile() && currentFile.length() + bytes.length > MAX_FILE_BYTES) {
            rotate(directory, currentFile);
        }

        try (FileOutputStream outputStream = new FileOutputStream(currentFile, true)) {
            outputStream.write(bytes);
        }
    }

    static synchronized String read(File directory) throws IOException {
        StringBuilder result = new StringBuilder();
        appendFile(result, new File(directory, PREVIOUS_FILE_NAME));
        appendFile(result, new File(directory, CURRENT_FILE_NAME));
        return result.toString();
    }

    static synchronized void clear(File directory) throws IOException {
        deleteIfPresent(new File(directory, CURRENT_FILE_NAME));
        deleteIfPresent(new File(directory, PREVIOUS_FILE_NAME));
    }

    static synchronized long sizeBytes(File directory) {
        return fileSize(new File(directory, CURRENT_FILE_NAME))
                + fileSize(new File(directory, PREVIOUS_FILE_NAME));
    }

    private static void rotate(File directory, File currentFile) throws IOException {
        File previousFile = new File(directory, PREVIOUS_FILE_NAME);
        deleteIfPresent(previousFile);
        if (!currentFile.renameTo(previousFile)) {
            throw new IOException("轮转诊断日志失败");
        }
    }

    private static void appendFile(StringBuilder result, File file) throws IOException {
        if (!file.isFile()) {
            return;
        }
        if (result.length() > 0) {
            result.append('\n');
        }
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, count);
            }
            result.append(outputStream.toString(StandardCharsets.UTF_8.name()));
        }
    }

    private static void deleteIfPresent(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("删除日志文件失败：" + file.getName());
        }
    }

    private static long fileSize(File file) {
        return file.isFile() ? file.length() : 0L;
    }
}
