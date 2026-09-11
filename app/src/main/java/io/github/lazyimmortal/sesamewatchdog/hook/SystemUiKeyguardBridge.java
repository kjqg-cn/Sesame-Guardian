package io.github.lazyimmortal.sesamewatchdog.hook;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

final class SystemUiKeyguardBridge {
    private static final boolean ENABLED = false;
    private static final String[] MEDIATOR_CLASSES = {
            "com.android.systemui.keyguard.KeyguardViewMediator",
            "com.android.keyguard.KeyguardViewMediator"
    };
    private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);
    private static volatile Object keyguardMediator;
    private static volatile Object keyguardViewController;
    private static volatile Handler mainHandler;

    private SystemUiKeyguardBridge() {
    }

    static void install(ClassLoader classLoader) {
        if (!ENABLED) {
            return;
        }
        if (!HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        mainHandler = new Handler(Looper.getMainLooper());
        for (String className : MEDIATOR_CLASSES) {
            try {
                try {
                    XposedHelpers.findAndHookConstructor(
                            className,
                            classLoader,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    captureMediator(param.thisObject);
                                }
                            });
                } catch (Throwable ignored) {
                    // Constructor signatures vary; the method hook below is sufficient.
                }
                XposedHelpers.findAndHookMethod(
                        className,
                        classLoader,
                        "setOccluded",
                        boolean.class,
                        boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                captureMediator(param.thisObject);
                            }
                        });
                hookMediatorStateMethod(className, classLoader, "isShowingAndNotOccluded");
                XposedLog.log("SystemUI KeyguardViewMediator Hook 已安装：" + className);
                return;
            } catch (Throwable ignored) {
                // Try the alternate class name used by older SystemUI builds.
            }
        }
        HOOK_INSTALLED.set(false);
        XposedLog.log("SystemUI KeyguardViewMediator Hook 未找到");
    }

    private static void hookMediatorStateMethod(
            String className,
            ClassLoader classLoader,
            String methodName) {
        try {
            XposedHelpers.findAndHookMethod(
                    className,
                    classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            captureMediator(param.thisObject);
                        }
                    });
        } catch (Throwable ignored) {
            // The method name varies across SystemUI generations.
        }
    }

    private static void captureMediator(Object mediator) {
        if (mediator != null) {
            keyguardMediator = mediator;
            try {
                Object lazyController = XposedHelpers.getObjectField(
                        mediator,
                        "mKeyguardViewControllerLazy");
                keyguardViewController = XposedHelpers.callMethod(lazyController, "get");
            } catch (Throwable ignored) {
                // The controller may not be initialized yet.
            }
        }
    }

    static boolean setOccluded(boolean occluded) {
        if (!ENABLED) {
            return false;
        }
        Handler handler = mainHandler;
        if (handler == null) {
            XposedLog.log("SystemUI 主线程桥接未初始化，无法设置锁屏遮挡=" + occluded);
            return false;
        }
        handler.post(() -> setOccludedOnMainThread(occluded));
        return true;
    }

    private static void setOccludedOnMainThread(boolean occluded) {
        Object mediator = keyguardMediator;
        if (mediator == null) {
            XposedLog.log("未捕获 KeyguardViewMediator，无法设置锁屏遮挡=" + occluded);
            return;
        }
        try {
            try {
                invokeOccluded(mediator, occluded);
            } catch (Throwable throwable) {
                XposedLog.logError("调用 KeyguardViewMediator.setOccluded 失败", unwrap(throwable));
            }
            if (keyguardViewController != null) {
                try {
                    invokeOccluded(keyguardViewController, occluded);
                } catch (Throwable throwable) {
                    XposedLog.logError(
                            "调用 StatusBarKeyguardViewManager.setOccluded 失败",
                            unwrap(throwable));
                }
            }
            XposedLog.log("SystemUI 已设置 Keyguard 遮挡=" + occluded);
        } catch (Throwable throwable) {
            XposedLog.logError("设置 SystemUI Keyguard 遮挡失败", throwable);
        }
    }

    private static void invokeOccluded(Object target, boolean occluded) throws Exception {
        Method method;
        try {
            method = target.getClass().getDeclaredMethod(
                    "setOccluded",
                    boolean.class,
                    boolean.class);
        } catch (NoSuchMethodException ignored) {
            method = target.getClass().getMethod(
                    "setOccluded",
                    boolean.class,
                    boolean.class);
        }
        method.setAccessible(true);
        method.invoke(target, occluded, true);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.lang.reflect.InvocationTargetException
                && ((java.lang.reflect.InvocationTargetException) throwable).getCause() != null) {
            return ((java.lang.reflect.InvocationTargetException) throwable).getCause();
        }
        return throwable;
    }
}
