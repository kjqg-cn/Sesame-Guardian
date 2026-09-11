package io.github.lazyimmortal.sesamewatchdog.ui;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.lazyimmortal.sesamewatchdog.R;
import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfig;
import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfigStore;
import io.github.lazyimmortal.sesamewatchdog.logging.DiagnosticLogStore;
import io.github.lazyimmortal.sesamewatchdog.root.RootAccessVerifier;

public final class MainActivity extends Activity {
    private final ExecutorService rootCheckExecutor = Executors.newSingleThreadExecutor();
    private Switch enabledSwitch;
    private EditText startupGraceInput;
    private EditText heartbeatTimeoutInput;
    private EditText nextOverdueInput;
    private EditText runningTimeoutInput;
    private EditText executionStallInput;
    private Switch foregroundRecoverySwitch;
    private EditText foregroundRecoveryIntervalInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WatchdogConfig config = WatchdogConfigStore.load(this);
        setContentView(createContent(config));
    }

    @Override
    protected void onDestroy() {
        rootCheckExecutor.shutdownNow();
        super.onDestroy();
    }

    private ScrollView createContent(WatchdogConfig config) {
        int padding = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.rgb(246, 247, 249));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTextColor(Color.rgb(24, 32, 45));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title, matchWidth());

        TextView subtitle = new TextView(this);
        subtitle.setText("锁屏期间监控支付宝运行状态");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(Color.rgb(102, 112, 133));
        subtitle.setPadding(0, dp(6), 0, dp(18));
        content.addView(subtitle, matchWidth());

        LinearLayout masterPanel = createPanel();
        addSectionTitle(masterPanel, "守护服务");
        enabledSwitch = new Switch(this);
        enabledSwitch.setText("启用守护");
        enabledSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        enabledSwitch.setChecked(config.isEnabled());
        masterPanel.addView(enabledSwitch, matchWidth());
        addDescription(masterPanel, "关闭后停止状态检查、诊断和自动操作。", false);
        addPanel(content, masterPanel);

        LinearLayout foregroundPanel = createPanel();
        addSectionTitle(foregroundPanel, "前台保活");
        foregroundRecoverySwitch = new Switch(this);
        foregroundRecoverySwitch.setText("锁屏时拉起支付宝");
        foregroundRecoverySwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        foregroundRecoverySwitch.setChecked(config.isForegroundRecoveryEnabled());
        foregroundPanel.addView(foregroundRecoverySwitch, matchWidth());
        addDescription(foregroundPanel, "开启后，按设定间隔拉起支付宝。", false);

        foregroundRecoveryIntervalInput = addMinutesField(
                foregroundPanel,
                "拉起间隔（分钟）",
                config.getForegroundRecoveryIntervalMinutes());
        addPanel(content, foregroundPanel);

        LinearLayout diagnosticPanel = createPanel();
        addSectionTitle(diagnosticPanel, "诊断阈值");
        startupGraceInput = addMinutesField(
                diagnosticPanel,
                "启动宽限",
                config.getStartupGraceMinutes(),
                "支付宝或 SystemUI 重启后，在此时间内不判定启动异常。");
        heartbeatTimeoutInput = addMinutesField(
                diagnosticPanel,
                "主线程心跳超时",
                config.getHeartbeatTimeoutMinutes(),
                "超过此时间没有收到支付宝主线程心跳时，记录进程或主线程可能异常。");
        nextOverdueInput = addMinutesField(
                diagnosticPanel,
                "下次执行逾期",
                config.getNextOverdueMinutes(),
                "通知中的下次执行时间超过此宽限仍未推进时，记录调度可能停滞。");
        runningTimeoutInput = addMinutesField(
                diagnosticPanel,
                "执行中超时",
                config.getRunningTimeoutMinutes(),
                "通知持续显示执行中超过此时间时，记录任务可能卡住。");
        executionStallInput = addMinutesField(
                diagnosticPanel,
                "执行进度停滞",
                config.getExecutionStallMinutes(),
                "计划时间已推进，但最近执行时间超过此时间没有更新时，记录进度可能停滞。");
        addPanel(content, diagnosticPanel);

        Button saveButton = createButton("保存设置", true);
        saveButton.setOnClickListener(view -> saveConfig());
        LinearLayout.LayoutParams saveParams = matchWidth();
        saveParams.topMargin = dp(18);
        content.addView(saveButton, saveParams);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);

        Button rootCheckButton = createButton("检查 root", false);
        rootCheckButton.setOnClickListener(view -> checkRootAccess());
        tools.addView(rootCheckButton, weightedWidth());

        Button logButton = createButton("打开日志", false);
        logButton.setOnClickListener(view ->
                startActivity(new Intent(this, LogActivity.class)));
        LinearLayout.LayoutParams logParams = weightedWidth();
        logParams.leftMargin = dp(12);
        tools.addView(logButton, logParams);
        LinearLayout.LayoutParams toolsParams = matchWidth();
        toolsParams.topMargin = dp(10);
        content.addView(tools, toolsParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private EditText addMinutesField(LinearLayout content, String label, int value) {
        return addMinutesField(content, label, value, null);
    }

    private EditText addMinutesField(
            LinearLayout content,
            String label,
            int value,
            String explanation) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView textView = new TextView(this);
        textView.setText(label + "（分钟）");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setTextColor(Color.rgb(52, 64, 84));
        row.addView(textView, weightedWidth());

        if (explanation != null) {
            row.addView(createInfoButton(label, explanation), infoButtonParams());
        }

        EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine(true);
        editText.setSelectAllOnFocus(true);
        editText.setGravity(Gravity.CENTER);
        editText.setText(String.valueOf(value));
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                dp(88),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(editText, inputParams);
        content.addView(row, matchWidth());
        return editText;
    }

    private LinearLayout createPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        panel.setBackground(background);
        return panel;
    }

    private void addPanel(LinearLayout content, View panel) {
        LinearLayout.LayoutParams params = matchWidth();
        params.topMargin = dp(10);
        content.addView(panel, params);
    }

    private void addSectionTitle(LinearLayout content, String title) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(title);
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sectionTitle.setTextColor(Color.rgb(102, 112, 133));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(0, 0, 0, dp(6));
        content.addView(sectionTitle, matchWidth());
    }

    private void addDescription(LinearLayout content, String text, boolean compact) {
        TextView description = new TextView(this);
        description.setText(text);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        description.setTextColor(Color.rgb(102, 112, 133));
        description.setPadding(0, compact ? 0 : dp(2), 0, dp(8));
        content.addView(description, matchWidth());
    }

    private ImageButton createInfoButton(String title, String explanation) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_info_outline);
        button.setImageTintList(ColorStateList.valueOf(Color.rgb(42, 103, 218)));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(5), dp(5), dp(5), dp(5));
        button.setContentDescription("查看" + title + "说明");
        button.setClickable(true);
        button.setOnClickListener(view -> showInfoPopup(view, title, explanation));
        return button;
    }

    private LinearLayout.LayoutParams infoButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(32),
                dp(32));
        params.rightMargin = dp(10);
        return params;
    }

    private void showInfoPopup(View anchor, String title, String explanation) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(220, 224, 230));
        box.setBackground(background);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTextColor(Color.rgb(24, 32, 45));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(titleView, matchWidth());

        TextView messageView = new TextView(this);
        messageView.setText(explanation);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        messageView.setTextColor(Color.rgb(71, 84, 103));
        messageView.setPadding(0, dp(6), 0, 0);
        box.addView(messageView, matchWidth());

        PopupWindow popup = new PopupWindow(
                box,
                dp(280),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        popup.showAsDropDown(anchor, -dp(248), dp(6));
    }

    private Button createButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setMinHeight(dp(48));
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setBackgroundTintList(
                    ColorStateList.valueOf(Color.rgb(42, 103, 218)));
        }
        return button;
    }

    private void saveConfig() {
        WatchdogConfig defaults = WatchdogConfig.defaults();
        WatchdogConfig config = WatchdogConfig.create(
                enabledSwitch.isChecked(),
                readMinutes(startupGraceInput, defaults.getStartupGraceMinutes()),
                readMinutes(heartbeatTimeoutInput, defaults.getHeartbeatTimeoutMinutes()),
                readMinutes(nextOverdueInput, defaults.getNextOverdueMinutes()),
                readMinutes(runningTimeoutInput, defaults.getRunningTimeoutMinutes()),
                readMinutes(executionStallInput, defaults.getExecutionStallMinutes()),
                false,
                false,
                foregroundRecoverySwitch.isChecked(),
                readMinutes(
                        foregroundRecoveryIntervalInput,
                        defaults.getForegroundRecoveryIntervalMinutes()));

        WatchdogConfigStore.save(this, config);
        updateInputs(config);
        sendConfig(config);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    private void sendConfig(WatchdogConfig config) {
        try {
            WatchdogConfigStore.sendToSystemUi(this, config);
        } catch (Throwable throwable) {
            Toast.makeText(this, "SystemUI 配置同步失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkRootAccess() {
        Toast.makeText(this, "正在检查 root 权限", Toast.LENGTH_SHORT).show();
        rootCheckExecutor.execute(() -> {
            RootAccessVerifier.Result result = RootAccessVerifier.verify();
            String message = result.isSuccess()
                    ? "root 权限检查成功：" + result.getMessage()
                    : "root 权限检查失败：" + result.getMessage();
            try {
                DiagnosticLogStore.append(
                        this,
                        System.currentTimeMillis(),
                        "Root执行器",
                        message);
            } catch (Throwable ignored) {
                // A missing diagnostic log must not hide the root check result.
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void updateInputs(WatchdogConfig config) {
        startupGraceInput.setText(String.valueOf(config.getStartupGraceMinutes()));
        heartbeatTimeoutInput.setText(String.valueOf(config.getHeartbeatTimeoutMinutes()));
        nextOverdueInput.setText(String.valueOf(config.getNextOverdueMinutes()));
        runningTimeoutInput.setText(String.valueOf(config.getRunningTimeoutMinutes()));
        executionStallInput.setText(String.valueOf(config.getExecutionStallMinutes()));
        foregroundRecoverySwitch.setChecked(config.isForegroundRecoveryEnabled());
        foregroundRecoveryIntervalInput.setText(
                String.valueOf(config.getForegroundRecoveryIntervalMinutes()));
    }

    private static int readMinutes(EditText input, int defaultValue) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedWidth() {
        return new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
