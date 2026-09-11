package io.github.lazyimmortal.sesamewatchdog.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.lazyimmortal.sesamewatchdog.R;

public final class AboutActivity extends Activity {
    private static final String SOURCE_URL = "https://github.com/kjqg-cn/Sesame-Guardian";
    private static final String ISSUES_URL = "https://github.com/kjqg-cn/Sesame-Guardian/issues";
    private static final String LICENSE_URL = "https://github.com/kjqg-cn/Sesame-Guardian/blob/main/LICENSE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(8), 0, dp(12), 0);

        ImageButton backButton = new ImageButton(this);
        backButton.setImageResource(R.drawable.ic_arrow_back);
        backButton.setImageTintList(android.content.res.ColorStateList.valueOf(
                Color.rgb(52, 64, 84)));
        backButton.setBackgroundColor(Color.TRANSPARENT);
        backButton.setContentDescription("返回");
        backButton.setTooltipText("返回");
        backButton.setOnClickListener(view -> finish());
        appBar.addView(backButton, iconButtonParams());

        TextView appBarTitle = new TextView(this);
        appBarTitle.setText("关于");
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

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTextColor(Color.rgb(24, 32, 45));
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title, matchWidth());

        TextView version = new TextView(this);
        version.setText("当前模块版本 " + packageVersionName());
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        version.setTextColor(Color.rgb(102, 112, 133));
        version.setPadding(0, dp(8), 0, dp(34));
        content.addView(version, matchWidth());

        TextView linksTitle = new TextView(this);
        linksTitle.setText("项目");
        linksTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        linksTitle.setTextColor(Color.rgb(102, 112, 133));
        linksTitle.setTypeface(null, Typeface.BOLD);
        linksTitle.setPadding(0, 0, 0, dp(6));
        content.addView(linksTitle, matchWidth());

        content.addView(createLinkRow("源代码", "GitHub 项目主页", SOURCE_URL));
        content.addView(createLinkRow("反馈", "提交问题或功能建议", ISSUES_URL));
        content.addView(createLinkRow("许可证", "Apache License 2.0", LICENSE_URL));

        TextView disclaimer = new TextView(this);
        disclaimer.setText("本项目是独立的 Root + LSPosed 设备自定义工具，与支付宝、Sesame-GR 及相关平台不存在隶属、合作或授权关系。");
        disclaimer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        disclaimer.setTextColor(Color.rgb(102, 112, 133));
        disclaimer.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams disclaimerParams = matchWidth();
        disclaimerParams.topMargin = dp(48);
        content.addView(disclaimer, disclaimerParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        setContentView(root);
    }

    private LinearLayout createLinkRow(String title, String subtitle, String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(0, dp(6), 0, dp(6));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + "，" + subtitle);
        row.setOnClickListener(view -> openExternalLink(url));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTextColor(Color.rgb(24, 32, 45));
        textColumn.addView(titleView, matchWidth());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitleView.setTextColor(Color.rgb(102, 112, 133));
        LinearLayout.LayoutParams subtitleParams = matchWidth();
        subtitleParams.topMargin = dp(4);
        textColumn.addView(subtitleView, subtitleParams);
        row.addView(textColumn, weightedWidth());

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        arrow.setTextColor(Color.rgb(102, 112, 133));
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription("打开" + title);
        row.addView(arrow, arrowParams());
        return row;
    }

    private void openExternalLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private String packageVersionName() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
            return versionName == null ? "未知" : versionName;
        } catch (Throwable ignored) {
            return "未知";
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

    private LinearLayout.LayoutParams iconButtonParams() {
        return new LinearLayout.LayoutParams(dp(48), dp(48));
    }

    private LinearLayout.LayoutParams arrowParams() {
        return new LinearLayout.LayoutParams(dp(32), dp(32));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
