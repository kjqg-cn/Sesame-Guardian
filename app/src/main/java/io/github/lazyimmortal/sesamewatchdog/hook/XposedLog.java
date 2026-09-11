package io.github.lazyimmortal.sesamewatchdog.hook;

import android.content.Context;

import de.robv.android.xposed.XposedBridge;

final class XposedLog {
    private XposedLog() {
    }

    static void initialize(Context context, String source) {
        DiagnosticLogSender.initialize(context, source);
    }

    static void log(String message) {
        XposedBridge.log("SesameWatchdog: " + message);
        DiagnosticLogSender.send(message);
    }

    static void logError(String message, Throwable throwable) {
        XposedBridge.log("SesameWatchdog: " + message);
        XposedBridge.log(throwable);
        DiagnosticLogSender.send(message + "，异常=" + throwable);
    }
}
