package io.github.lazyimmortal.sesamewatchdog.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.lazyimmortal.sesamewatchdog.logging.DiagnosticLogStore;

public final class LogActivity extends Activity {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextView summaryView;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContent());
        refreshLogs();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private ScrollView createContent() {
        int padding = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("诊断日志");
        title.setTextSize(22);
        content.addView(title, matchWidth());

        summaryView = new TextView(this);
        summaryView.setTextSize(14);
        summaryView.setPadding(0, dp(8), 0, dp(12));
        content.addView(summaryView, matchWidth());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        Button refreshButton = new Button(this);
        refreshButton.setText("刷新");
        refreshButton.setOnClickListener(view -> refreshLogs());
        actions.addView(refreshButton, weightedWidth());

        Button clearButton = new Button(this);
        clearButton.setText("清除日志");
        clearButton.setOnClickListener(view -> confirmClearLogs());
        LinearLayout.LayoutParams clearParams = weightedWidth();
        clearParams.leftMargin = dp(12);
        actions.addView(clearButton, clearParams);
        content.addView(actions, matchWidth());

        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(16), 0, dp(24));
        content.addView(logView, matchWidth());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void refreshLogs() {
        summaryView.setText("正在读取...");
        ioExecutor.execute(() -> {
            try {
                String logs = DiagnosticLogStore.readNewestFirst(
                        this,
                        DiagnosticLogStore.DEFAULT_DISPLAY_LINES);
                long sizeBytes = DiagnosticLogStore.sizeBytes(this);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    summaryView.setText(formatSummary(sizeBytes));
                    logView.setText(logs.isEmpty() ? "暂无日志" : logs);
                });
            } catch (IOException exception) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        summaryView.setText("日志读取失败");
                        logView.setText(exception.toString());
                    }
                });
            }
        });
    }

    private void confirmClearLogs() {
        new AlertDialog.Builder(this)
                .setTitle("清除诊断日志")
                .setMessage("仅清除芝麻守护私有日志，不影响 LSPosed 或 Sesame-GR 日志。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> clearLogs())
                .show();
    }

    private void clearLogs() {
        ioExecutor.execute(() -> {
            try {
                DiagnosticLogStore.clear(this);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        summaryView.setText(formatSummary(0L));
                        logView.setText("暂无日志");
                        Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException exception) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, "日志清除失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private static String formatSummary(long sizeBytes) {
        double usedKiB = sizeBytes / 1024.0;
        double limitKiB = DiagnosticLogStore.MAX_TOTAL_BYTES / 1024.0;
        return String.format(
                Locale.getDefault(),
                "模块私有日志：%.1f KiB / %.0f KiB，最新记录显示在顶部",
                usedKiB,
                limitKiB);
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedWidth() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
