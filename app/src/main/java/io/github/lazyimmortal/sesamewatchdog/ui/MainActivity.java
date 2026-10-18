package io.github.lazyimmortal.sesamewatchdog.ui;

import android.app.Activity;
import android.app.TimePickerDialog;
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
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.lazyimmortal.sesamewatchdog.R;
import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfig;
import io.github.lazyimmortal.sesamewatchdog.config.WatchdogConfigStore;
import io.github.lazyimmortal.sesamewatchdog.logging.DiagnosticLogStore;
import io.github.lazyimmortal.sesamewatchdog.root.RootAccessVerifier;

public final class MainActivity extends Activity {
    private static final int MAIN_BACKGROUND = Color.rgb(246, 247, 249);
    private final ExecutorService rootCheckExecutor = Executors.newSingleThreadExecutor();
    private Switch enabledSwitch;
    private EditText startupGraceInput;
    private EditText heartbeatTimeoutInput;
    private EditText nextOverdueInput;
    private EditText runningTimeoutInput;
    private EditText executionStallInput;
    private Switch foregroundRecoverySwitch;
    private EditText foregroundRecoveryIntervalInput;
    private Switch fastSleepSwitch;
    private Switch quietHoursSwitch;
    private Button quietHoursStartTimeButton;
    private Button quietHoursEndTimeButton;
    private int quietHoursStartHour;
    private int quietHoursStartMinute;
    private int quietHoursEndHour;
    private int quietHoursEndMinute;
    private boolean isModuleActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(MAIN_BACKGROUND);
        getWindow().setNavigationBarColor(MAIN_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        isModuleActive = !getIntent().getBooleanExtra("debug_force_inactive", false)
                && ModuleActivationStatus.isModuleActive(this);
        WatchdogConfig config = WatchdogConfigStore.load(this);
        setContentView(createContent(config));
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean currentActive = !getIntent().getBooleanExtra("debug_force_inactive", false)
                && ModuleActivationStatus.isModuleActive(this);
        if (currentActive != isModuleActive) {
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        rootCheckExecutor.shutdownNow();
        super.onDestroy();
    }

    private View createContent(WatchdogConfig config) {
        int horizontalPadding = dp(20);
        int topPadding = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MAIN_BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(horizontalPadding, topPadding, horizontalPadding, dp(8));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleBar, matchWidth());

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTextColor(Color.rgb(24, 32, 45));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titleBar.addView(title, weightedWidth());

        ImageButton aboutButton = new ImageButton(this);
        aboutButton.setImageResource(R.drawable.ic_more_vert);
        aboutButton.setImageTintList(ColorStateList.valueOf(Color.rgb(52, 64, 84)));
        aboutButton.setBackgroundColor(Color.TRANSPARENT);
        aboutButton.setContentDescription("更多");
        aboutButton.setTooltipText("更多");
        aboutButton.setOnClickListener(this::showMoreMenu);
        titleBar.addView(aboutButton, iconButtonParams());

        TextView subtitle = new TextView(this);
        subtitle.setText("锁屏期间监控支付宝运行状态");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(Color.rgb(102, 112, 133));
        subtitle.setPadding(0, dp(4), 0, isModuleActive ? dp(6) : dp(10));
        header.addView(subtitle, matchWidth());

        if (isModuleActive) {
            TextView statusBadge = new TextView(this);
            statusBadge.setText("● 模块已在 LSPosed 激活生效");
            statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            statusBadge.setTextColor(Color.rgb(18, 183, 106));
            statusBadge.setPadding(0, 0, 0, dp(4));
            header.addView(statusBadge, matchWidth());
        } else {
            header.addView(createActivationBanner(), matchWidth());
        }
        root.addView(header, matchWidth());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout scrollableContent = new LinearLayout(this);
        scrollableContent.setOrientation(LinearLayout.VERTICAL);
        scrollableContent.setPadding(horizontalPadding, 0, horizontalPadding, dp(14));

        LinearLayout masterPanel = createPanel();
        addSectionTitle(masterPanel, "守护服务");
        enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(config.isEnabled());
        masterPanel.addView(createSwitchRow(
                "启用守护",
                "关闭后停止状态检查、诊断和自动操作",
                enabledSwitch), matchWidth());
        addPanel(scrollableContent, masterPanel);

        LinearLayout foregroundPanel = createPanel();
        addSectionTitle(foregroundPanel, "前台保活");

        foregroundRecoverySwitch = new Switch(this);
        foregroundRecoverySwitch.setChecked(config.isForegroundRecoveryEnabled());
        foregroundPanel.addView(createSwitchRow(
                "锁屏时拉起支付宝",
                "开启后优先根据计划时间按需唤醒，减少无效亮屏",
                foregroundRecoverySwitch), matchWidth());

        foregroundPanel.addView(createDivider());

        foregroundRecoveryIntervalInput = addMinutesField(
                foregroundPanel,
                "保底拉起间隔",
                config.getForegroundRecoveryIntervalMinutes(),
                "优先在 Sesame-GR 计划时间前按需唤醒；当计划时间缺失、计划逾期或心跳异常时，按此间隔保底拉起。");

        foregroundPanel.addView(createDivider());

        fastSleepSwitch = new Switch(this);
        fastSleepSwitch.setChecked(config.isFastSleepEnabled());
        foregroundPanel.addView(createSwitchRow(
                "唤醒后快速熄屏",
                "确认拉起就绪后主动熄灭屏幕并退回后台，减少亮屏耗电",
                fastSleepSwitch), matchWidth());

        addPanel(scrollableContent, foregroundPanel);

        LinearLayout quietHoursPanel = createPanel();
        addSectionTitle(quietHoursPanel, "夜间免打扰");

        quietHoursSwitch = new Switch(this);
        quietHoursSwitch.setChecked(config.isQuietHoursEnabled());
        quietHoursPanel.addView(createSwitchRow(
                "夜间免打扰",
                "设定时段内暂停锁屏唤醒与主动自愈，仅被动监控",
                quietHoursSwitch), matchWidth());

        quietHoursPanel.addView(createDivider());

        quietHoursStartHour = config.getQuietHoursStartHour();
        quietHoursStartMinute = config.getQuietHoursStartMinute();
        quietHoursEndHour = config.getQuietHoursEndHour();
        quietHoursEndMinute = config.getQuietHoursEndMinute();

        LinearLayout quietTimeRow = new LinearLayout(this);
        quietTimeRow.setOrientation(LinearLayout.HORIZONTAL);
        quietTimeRow.setGravity(Gravity.CENTER_VERTICAL);
        quietTimeRow.setPadding(0, dp(4), 0, dp(4));

        LinearLayout quietTimeTexts = createItemTitleWithSubtitle(
                "免打扰时段",
                "支持跨午夜；开始与结束相同时不生效");
        quietTimeRow.addView(quietTimeTexts, weightedWidth());

        LinearLayout.LayoutParams timeBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        quietHoursStartTimeButton = createTimePickerButton(
                formatTime(quietHoursStartHour, quietHoursStartMinute),
                "免打扰开始时间");
        quietHoursStartTimeButton.setOnClickListener(v -> showTimePicker(true));
        quietTimeRow.addView(quietHoursStartTimeButton, timeBtnParams);

        TextView quietTimeSeparator = new TextView(this);
        quietTimeSeparator.setText(" 至 ");
        quietTimeSeparator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        quietTimeSeparator.setTextColor(Color.rgb(102, 112, 133));
        quietTimeSeparator.setPadding(dp(6), 0, dp(6), 0);
        quietTimeRow.addView(quietTimeSeparator);

        quietHoursEndTimeButton = createTimePickerButton(
                formatTime(quietHoursEndHour, quietHoursEndMinute),
                "免打扰结束时间");
        quietHoursEndTimeButton.setOnClickListener(v -> showTimePicker(false));
        quietTimeRow.addView(quietHoursEndTimeButton, timeBtnParams);

        quietHoursPanel.addView(quietTimeRow, matchWidth());

        quietHoursSwitch.setOnCheckedChangeListener((btn, isChecked) -> updateQuietHoursControlsState());
        updateQuietHoursControlsState();

        addPanel(scrollableContent, quietHoursPanel);

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
        addPanel(scrollableContent, diagnosticPanel);

        if (!isModuleActive) {
            enabledSwitch.setEnabled(false);
            foregroundRecoverySwitch.setEnabled(false);
            foregroundRecoveryIntervalInput.setEnabled(false);
            fastSleepSwitch.setEnabled(false);
            quietHoursSwitch.setEnabled(false);
            startupGraceInput.setEnabled(false);
            heartbeatTimeoutInput.setEnabled(false);
            nextOverdueInput.setEnabled(false);
            runningTimeoutInput.setEnabled(false);
            executionStallInput.setEnabled(false);
        }

        scrollView.addView(scrollableContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        root.addView(scrollView, scrollParams);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(horizontalPadding, dp(10), horizontalPadding, dp(14));
        bottomBar.setBackgroundColor(MAIN_BACKGROUND);

        Button rootCheckButton = createButton("检查 root", false);
        rootCheckButton.setOnClickListener(view -> checkRootAccess());
        LinearLayout.LayoutParams rootBtnParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        bottomBar.addView(rootCheckButton, rootBtnParams);

        Button saveButton = createButton(
                isModuleActive ? "保存设置" : "模块未激活（已锁定）",
                isModuleActive);
        saveButton.setEnabled(isModuleActive);
        if (!isModuleActive) {
            saveButton.setBackgroundTintList(
                    ColorStateList.valueOf(Color.rgb(208, 213, 221)));
            saveButton.setTextColor(Color.rgb(102, 112, 133));
        }
        saveButton.setOnClickListener(view -> saveConfig());
        LinearLayout.LayoutParams saveBtnParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.4f);
        saveBtnParams.leftMargin = dp(12);
        bottomBar.addView(saveButton, saveBtnParams);

        root.addView(bottomBar, matchWidth());

        return root;
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

    private LinearLayout createItemTitleWithSubtitle(String title, String subtitle) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTextColor(Color.rgb(52, 64, 84));
        container.addView(titleView, matchWidth());

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subtitleView.setTextColor(Color.rgb(102, 112, 133));
            subtitleView.setPadding(0, dp(2), 0, 0);
            container.addView(subtitleView, matchWidth());
        }
        return container;
    }

    private LinearLayout createSwitchRow(String title, String subtitle, Switch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout texts = createItemTitleWithSubtitle(title, subtitle);
        row.addView(texts, weightedWidth());

        sw.setContentDescription(title);
        row.addView(sw);

        row.setOnClickListener(v -> {
            if (isModuleActive && sw.isEnabled()) {
                sw.toggle();
            }
        });
        return row;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(242, 244, 247));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1));
        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        divider.setLayoutParams(params);
        return divider;
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

    private LinearLayout.LayoutParams iconButtonParams() {
        return new LinearLayout.LayoutParams(dp(48), dp(48));
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

    private void showMoreMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "查看日志");
        popupMenu.getMenu().add(0, 2, 1, "关于");
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(new Intent(this, LogActivity.class));
                return true;
            } else if (item.getItemId() == 2) {
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            }
            return false;
        });
        popupMenu.show();
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
        } else {
            button.setTextColor(Color.rgb(52, 64, 84));
            button.setBackgroundTintList(
                    ColorStateList.valueOf(Color.rgb(234, 236, 240)));
        }
        return button;
    }

    private void saveConfig() {
        if (!isModuleActive) {
            Toast.makeText(this, "模块未激活，已锁定配置修改", Toast.LENGTH_SHORT).show();
            return;
        }
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
                        defaults.getForegroundRecoveryIntervalMinutes()),
                quietHoursSwitch.isChecked(),
                quietHoursStartHour,
                quietHoursStartMinute,
                quietHoursEndHour,
                quietHoursEndMinute,
                fastSleepSwitch.isChecked());

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
        fastSleepSwitch.setChecked(config.isFastSleepEnabled());
        quietHoursSwitch.setChecked(config.isQuietHoursEnabled());
        quietHoursStartHour = config.getQuietHoursStartHour();
        quietHoursStartMinute = config.getQuietHoursStartMinute();
        quietHoursEndHour = config.getQuietHoursEndHour();
        quietHoursEndMinute = config.getQuietHoursEndMinute();
        updateQuietHoursTimeButtons();
        updateQuietHoursControlsState();
    }

    private Button createTimePickerButton(String text, String contentDescription) {
        Button button = new Button(this);
        button.setText(text);
        button.setContentDescription(contentDescription);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(72));
        button.setMinimumWidth(dp(72));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private void updateQuietHoursControlsState() {
        boolean controlsEnabled = isModuleActive && quietHoursSwitch != null && quietHoursSwitch.isChecked();
        if (quietHoursStartTimeButton != null) {
            quietHoursStartTimeButton.setEnabled(controlsEnabled);
            quietHoursStartTimeButton.setTextColor(controlsEnabled
                    ? Color.rgb(42, 103, 218)
                    : Color.rgb(152, 162, 179));
            quietHoursStartTimeButton.setBackgroundTintList(
                    ColorStateList.valueOf(controlsEnabled
                            ? Color.rgb(238, 244, 255)
                            : Color.rgb(242, 244, 247)));
        }
        if (quietHoursEndTimeButton != null) {
            quietHoursEndTimeButton.setEnabled(controlsEnabled);
            quietHoursEndTimeButton.setTextColor(controlsEnabled
                    ? Color.rgb(42, 103, 218)
                    : Color.rgb(152, 162, 179));
            quietHoursEndTimeButton.setBackgroundTintList(
                    ColorStateList.valueOf(controlsEnabled
                            ? Color.rgb(238, 244, 255)
                            : Color.rgb(242, 244, 247)));
        }
    }

    private void showTimePicker(boolean isStart) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        int initialHour = isStart ? quietHoursStartHour : quietHoursEndHour;
        int initialMinute = isStart ? quietHoursStartMinute : quietHoursEndMinute;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    if (isStart) {
                        quietHoursStartHour = hourOfDay;
                        quietHoursStartMinute = minute;
                    } else {
                        quietHoursEndHour = hourOfDay;
                        quietHoursEndMinute = minute;
                    }
                    updateQuietHoursTimeButtons();
                },
                initialHour,
                initialMinute,
                true);
        dialog.setTitle(isStart ? "设置免打扰开始时间" : "设置免打扰结束时间");
        dialog.show();
    }

    private void updateQuietHoursTimeButtons() {
        if (quietHoursStartTimeButton != null) {
            quietHoursStartTimeButton.setText(formatTime(quietHoursStartHour, quietHoursStartMinute));
        }
        if (quietHoursEndTimeButton != null) {
            quietHoursEndTimeButton.setText(formatTime(quietHoursEndHour, quietHoursEndMinute));
        }
    }

    private static String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
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

    private View createActivationBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(254, 243, 242));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(254, 205, 202));
        banner.setBackground(background);

        TextView titleView = new TextView(this);
        titleView.setText("未在 LSPosed 中激活（只读模式）");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTextColor(Color.rgb(180, 35, 24));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        banner.addView(titleView, matchWidth());

        TextView descView = new TextView(this);
        descView.setText("请在 LSPosed 管理器中启用「芝麻守护」，并确认勾选「系统界面」和「支付宝」作用域后重启对应进程生效。未激活期间已锁定配置修改，现有配置不会被覆盖或回滚。");
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        descView.setTextColor(Color.rgb(180, 35, 24));
        descView.setPadding(0, dp(6), 0, 0);
        banner.addView(descView, matchWidth());

        if (isLSPosedManagerInstalled()) {
            Button openLsposedButton = new Button(this);
            openLsposedButton.setText("打开 LSPosed 管理器");
            openLsposedButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            openLsposedButton.setTextColor(Color.rgb(180, 35, 24));
            openLsposedButton.setBackgroundTintList(
                    ColorStateList.valueOf(Color.rgb(254, 228, 226)));
            openLsposedButton.setOnClickListener(v -> openLSPosedManager());
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(36));
            btnParams.topMargin = dp(10);
            banner.addView(openLsposedButton, btnParams);
        }

        LinearLayout.LayoutParams params = matchWidth();
        params.bottomMargin = dp(12);
        banner.setLayoutParams(params);
        return banner;
    }

    private boolean isLSPosedManagerInstalled() {
        try {
            return getPackageManager().getLaunchIntentForPackage("org.lsposed.manager") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openLSPosedManager() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
            if (intent != null) {
                startActivity(intent);
            }
        } catch (Throwable throwable) {
            Toast.makeText(this, "无法启动 LSPosed 管理器", Toast.LENGTH_SHORT).show();
        }
    }
}
