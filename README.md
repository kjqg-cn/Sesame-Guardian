# Sesame Guardian

独立的 LSPosed 模块，用于在支付宝锁屏运行期间维持 Sesame-GR 的调度机会，并从支付宝进程外记录运行状态。

## 当前策略

看门狗的主要恢复动作是：

1. 在锁屏期间按配置间隔唤醒屏幕。
2. 通过 root 启动 `com.eg.android.AlipayGphone/.AlipayLogin`。
3. 让支付宝重新进入前台生命周期，触发支付宝内已注入模块的初始化逻辑。
4. 通过主线程心跳和 Sesame-GR 通知记录运行状态。

自动软恢复和 root 硬恢复默认关闭。它们的配置字段仍保留用于兼容旧版本数据，但设置页不再提供入口。

## 重要限制

- 本模块不引用、反射或 Hook `io.github.lazyimmortal.sesame.*` 类。
- 当前通知解析针对 Sesame-GR 的通知特征，不是通用模块协议。
- 其他运行在支付宝主进程中的类似 LSPosed 模块，可以受益于支付宝前台拉起；但它们的通知、广播和任务状态不会自动被监控。
- `showWhenLocked` 和 `turnScreenOn` 只做尽力设置，不会读取或输入锁屏 PIN。HyperOS 可能仍然阻止普通 Activity 覆盖安全锁屏。
- root 硬恢复会真正停止支付宝，可能中断正在执行的任务，因此默认关闭。
- 该模块不保证支付宝的所有子进程始终存活，也不修改支付宝的 `oom_score_adj`。

## 架构

```text
支付宝主进程
  └─ LSPosed 探针
       ├─ 主 Looper 心跳
       ├─ Sesame-GR 通知解析
       └─ Activity 前台/锁屏窗口 Hook

SystemUI 进程
  └─ 进程外看门狗
       ├─ 接收探针事件
       ├─ 维护诊断状态
       ├─ 锁屏定时拉起支付宝
       └─ 通过模块 ContentProvider 请求 root 执行器
```

LSPosed 作用域：

- `com.eg.android.AlipayGphone`
- `com.android.systemui`

## 构建

需要 Android SDK、Android SDK Build Tools、JDK 17。Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = "D:\Develop\Android\Android Studio\jbr"
.\gradlew.bat test assembleDebug
```

生成的 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装与配置

1. 安装 APK。
2. 在 LSPosed 中启用模块，并勾选支付宝和 SystemUI 作用域。
3. 在 root 管理器中允许模块 UID 执行 root 命令。
4. 重启 SystemUI 和支付宝，或重启设备。
5. 打开模块设置页，启用看门狗和锁屏前台拉起，设置拉起间隔。
6. 通过“打开日志”查看心跳、通知、拉起和恢复结果。

关闭“启用看门狗”会停止 SystemUI 侧的检查、诊断和自动拉起，但不会修改支付宝或 Sesame-GR 自身配置。

## 配置说明

- `启用看门狗`：总开关，同时控制诊断和锁屏前台拉起。
- `锁屏时拉起支付宝`：锁屏期间是否按间隔启动支付宝。
- `拉起间隔`：两次前台拉起之间的最短间隔。
- `诊断阈值`：用于记录心跳、通知和执行进度异常。

设置页中的说明图标可以查看每个诊断阈值的含义。日志页面默认显示最新 300 条记录。

## 测试

当前本地验证命令：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## 参考资料

`reference/` 保存过创建项目时的 Sesame-GR 源码快照和设备截图，仅用于理解外部行为，不参与编译，也不是本模块的依赖。当前公开仓库只保留目录说明；历史源码和设备截图默认由 `.gitignore` 排除在发布内容之外。

这部分资料不是构建、安装或运行本模块的必要文件。Apache License 2.0 只声明本项目适用范围内的项目文件；上游快照、截图和其他第三方材料不应默认视为由本项目重新授权，公开分发前需要确认其来源和再分发许可。

## License

本项目源代码按 Apache License 2.0 发布，详见 [LICENSE](LICENSE)。
