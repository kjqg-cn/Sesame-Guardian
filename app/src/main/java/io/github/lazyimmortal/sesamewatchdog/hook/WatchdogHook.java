package io.github.lazyimmortal.sesamewatchdog.hook;

import android.os.Process;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class WatchdogHook implements IXposedHookLoadPackage {
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MODULE_PACKAGE = "io.github.lazyimmortal.sesamewatchdog";
    private static final String ACTIVATION_CLASS =
            "io.github.lazyimmortal.sesamewatchdog.ui.ModuleActivationStatus";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (MODULE_PACKAGE.equals(loadPackageParam.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(
                        ACTIVATION_CLASS,
                        loadPackageParam.classLoader,
                        "isHookActivated",
                        XC_MethodReplacement.returnConstant(true));
                XposedBridge.log("SesameGuardian: 模块自身 Hook 激活成功");
            } catch (Throwable throwable) {
                XposedBridge.log(throwable);
            }
            return;
        }

        if (ALIPAY_PACKAGE.equals(loadPackageParam.packageName)
                && ALIPAY_PACKAGE.equals(loadPackageParam.processName)) {
            XposedBridge.log("SesameGuardian: 已识别支付宝主进程，支付宝PID=" + Process.myPid());
            AlipayProbe.install();
            return;
        }

        if (SYSTEM_UI_PACKAGE.equals(loadPackageParam.packageName)
                && SYSTEM_UI_PACKAGE.equals(loadPackageParam.processName)) {
            XposedBridge.log("SesameGuardian: 已识别 SystemUI 进程");
            SystemUiWatchdog.install();
        }
    }
}
