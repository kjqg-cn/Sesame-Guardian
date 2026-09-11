package io.github.lazyimmortal.sesamewatchdog.root;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

final class RootCommandRunner {
    private static final String SU_PATH = "/system/bin/su";
    private static final long ROOT_CHECK_TIMEOUT_SECONDS = 10L;
    private static final long HARD_RECOVERY_TIMEOUT_SECONDS = 15L;
    private static final int MAX_OUTPUT_BYTES = 2_048;

    private RootCommandRunner() {
    }

    static Result verifyAccess() {
        Result result = execute("id", ROOT_CHECK_TIMEOUT_SECONDS);
        if (result.success && !result.output.contains("uid=0")) {
            return new Result(false, "未获得 root 身份，输出=" + result.output);
        }
        return result;
    }

    static Result recoverAlipay(int userId) {
        return recoverAlipay(userId, false, 0L);
    }

    static Result recoverAlipay(int userId, boolean wakeScreen, long requestId) {
        if (userId < 0 || userId > 999) {
            return new Result(false, "支付宝用户 ID 不合法");
        }
        String command = wakeCommand(wakeScreen)
                + "am force-stop --user " + userId
                + " com.eg.android.AlipayGphone"
                + " && am start --user " + userId
                + foregroundIntentArguments(requestId)
                + " -n com.eg.android.AlipayGphone/.AlipayLogin";
        return execute(command, HARD_RECOVERY_TIMEOUT_SECONDS);
    }

    static Result bringAlipayToFront(
            int userId,
            long requestId,
            boolean wakeScreen) {
        if (userId < 0 || userId > 999) {
            return new Result(false, "支付宝用户 ID 不合法");
        }
        if (requestId <= 0L) {
            return new Result(false, "前台拉起请求编号不合法");
        }
        String command = wakeCommand(wakeScreen)
                + "am start --user " + userId
                + " -W"
                + foregroundIntentArguments(requestId)
                + " -n com.eg.android.AlipayGphone/.AlipayLogin";
        return execute(command, HARD_RECOVERY_TIMEOUT_SECONDS);
    }

    private static String wakeCommand(boolean wakeScreen) {
        return wakeScreen ? "input keyevent 26 && " : "";
    }

    private static String foregroundIntentArguments(long requestId) {
        if (requestId <= 0L) {
            return "";
        }
        return " --ez " + WatchdogContract.EXTRA_FOREGROUND_RECOVERY + " true"
                + " --el " + WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID
                + " " + requestId;
    }

    private static Result execute(String command, long timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(SU_PATH, "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(false, "命令超时（" + timeoutSeconds + " 秒）");
            }
            String output = readOutput(process.getInputStream());
            if (process.exitValue() != 0) {
                return new Result(false, "退出码=" + process.exitValue() + formatOutput(output));
            }
            return new Result(true, output);
        } catch (Throwable throwable) {
            return new Result(false, throwable.toString());
        }
    }

    private static String readOutput(InputStream inputStream) throws IOException {
        try (InputStream stream = inputStream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[512];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1 && total < MAX_OUTPUT_BYTES) {
                int writable = Math.min(count, MAX_OUTPUT_BYTES - total);
                output.write(buffer, 0, writable);
                total += writable;
            }
            return output.toString(StandardCharsets.UTF_8.name()).trim();
        }
    }

    private static String formatOutput(String output) {
        return output.isEmpty() ? "" : "，输出=" + output;
    }

    static final class Result {
        private final boolean success;
        private final String output;

        private Result(boolean success, String output) {
            this.success = success;
            this.output = output;
        }

        boolean isSuccess() {
            return success;
        }

        String getOutput() {
            return output;
        }
    }
}
