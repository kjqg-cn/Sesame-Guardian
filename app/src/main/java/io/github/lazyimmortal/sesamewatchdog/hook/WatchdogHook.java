package io.github.lazyimmortal.sesamewatchdog.hook;

import android.os.Process;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class WatchdogHook implements IXposedHookLoadPackage {
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (ALIPAY_PACKAGE.equals(loadPackageParam.packageName)
                && ALIPAY_PACKAGE.equals(loadPackageParam.processName)) {
            XposedBridge.log("SesameWatchdog: 已识别支付宝主进程，支付宝PID=" + Process.myPid());
            AlipayProbe.install();
            return;
        }

        if (SYSTEM_UI_PACKAGE.equals(loadPackageParam.packageName)
                && SYSTEM_UI_PACKAGE.equals(loadPackageParam.processName)) {
            XposedBridge.log("SesameWatchdog: 已识别 SystemUI 进程");
            SystemUiWatchdog.install();
        }
    }
}
