package io.github.lazyimmortal.sesamewatchdog.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.lazyimmortal.sesamewatchdog.R;
import io.github.lazyimmortal.sesamewatchdog.logging.DiagnosticLogStore;

public final class LogActivity extends Activity {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextView summaryView;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        setContentView(createContent());
        refreshLogs();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(8), 0, dp(12), 0);

        ImageButton backButton = new ImageButton(this);
        backButton.setImageResource(R.drawable.ic_arrow_back);
        backButton.setImageTintList(ColorStateList.valueOf(
                Color.rgb(52, 64, 84)));
        backButton.setBackgroundColor(Color.TRANSPARENT);
        backButton.setContentDescription("返回");
        backButton.setTooltipText("返回");
        backButton.setOnClickListener(view -> finish());
        appBar.addView(backButton, iconButtonParams());

        TextView appBarTitle = new TextView(this);
        appBarTitle.setText("诊断日志");
        appBarTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        appBarTitle.setTextColor(Color.rgb(24, 32, 45));
        appBarTitle.setTypeface(null, Typeface.BOLD);
        appBar.addView(appBarTitle, weightedWidth());

        root.addView(appBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(229, 231, 235));
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)));

        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.VERTICAL);
        controlBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        controlBar.setBackgroundColor(Color.rgb(249, 250, 251));

        summaryView = new TextView(this);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        summaryView.setTextColor(Color.rgb(102, 112, 133));
        summaryView.setPadding(0, 0, 0, dp(10));
        controlBar.addView(summaryView, matchWidth());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button refreshButton = createButton("刷新", true);
        refreshButton.setOnClickListener(view -> refreshLogs());
        actions.addView(refreshButton, weightedWidth());

        Button clearButton = createButton("清除日志", false);
        clearButton.setOnClickListener(view -> confirmClearLogs());
        LinearLayout.LayoutParams clearParams = weightedWidth();
        clearParams.leftMargin = dp(12);
        actions.addView(clearButton, clearParams);

        controlBar.addView(actions, matchWidth());
        root.addView(controlBar, matchWidth());

        View subDivider = new View(this);
        subDivider.setBackgroundColor(Color.rgb(229, 231, 235));
        root.addView(subDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        logView = new TextView(this);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logView.setTextColor(Color.rgb(52, 64, 84));
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(16), dp(16), dp(16), dp(24));

        scrollView.addView(logView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        return root;
    }

    private Button createButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setMinHeight(dp(40));
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setBackgroundTintList(
                    ColorStateList.valueOf(Color.rgb(42, 103, 218)));
        } else {
            button.setTextColor(Color.rgb(52, 64, 84));
            button.setBackgroundTintList(
                    ColorStateList.valueOf(Color.rgb(234, 236, 240)));
        }
        return button;
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

    private LinearLayout.LayoutParams iconButtonParams() {
        return new LinearLayout.LayoutParams(dp(48), dp(48));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
