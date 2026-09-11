# Sesame Guardian 项目说明

## 语言与工作方式

- 默认使用简体中文沟通，代码、命令、日志和 Android 接口名保留原文。
- 修改前先阅读本文件、相关源码和调用链；先定位证据，再修改。
- 优先小步实现和设备验证，不做无关重构，不修改或依赖 Sesame-GR 的内部类。
- 搜索优先使用 `rg`，手工编辑优先使用 `apply_patch`。
- 删除已有文件或目录前必须获得用户确认。

## 项目目标

本项目是一个独立的 LSPosed 模块，用于监控支付宝内 Sesame-GR 的运行状态，并在其因长时间锁屏、进程冻结、主线程停滞或任务线程卡死而停止调度时进行恢复。

本项目必须：

- 独立构建、安装和升级，不修改 Sesame-GR 源码。
- 可以与 Sesame-GR 同时作用于支付宝。
- 不直接引用、反射或 Hook `io.github.lazyimmortal.sesame.*` 类，避免 LSPosed 模块 ClassLoader 隔离及上游升级造成耦合。
- 将真正的守护服务放在支付宝进程之外。只在支付宝进程内增加检测逻辑无法处理整个进程被冻结或被杀的情况。
- 面向已 root、已安装 LSPosed 的设备设计，可以使用 root 完成最终恢复，但不能要求用户手工部署 shell 脚本。

## 已确认的问题

测试设备上，Sesame-GR 设置为 5 分钟执行一次，并开启“保持唤醒”和“超时重启”，但长时间锁屏后可能出现：

- 通知中的“上次执行”停留在数小时前。
- 解锁后“下次执行”被重新计算为大约 5 分钟后。
- 打开支付宝时首页重新加载，Sesame-GR 随即开始执行。
- 支付宝主进程可能仍在，但部分子进程已被 HyperOS 以 `kill_bg_proc` 回收。
- 目标进程曾出现约 750 MB RSS、约 916 MB Swap 和 400 多个线程。
- Sesame-GR 持有 `PARTIAL_WAKE_LOCK`，但 WakeLock 只能阻止 CPU 完全休眠，不能保证主 Looper、RPC 环境、WebView/小程序子进程和应用进程始终健康。

Sesame-GR 当前的 5 分钟调度使用支付宝主线程的 `Handler.postDelayed()`。当支付宝进程或主 Looper 被系统冻结时，调度器和模块内部的超时检测会一起停止。“超时重启”主要处理 RPC 登录超时，不等于进程级守护服务，也不会真正 `force-stop` 支付宝。

## 独立模块架构

### LSPosed 作用域

初始设计包含两个作用域：

- `com.eg.android.AlipayGphone`：运行轻量探针。
- `com.android.systemui`：运行进程外守护服务。

必须严格判断进程名：

- 支付宝探针只在 `processName == com.eg.android.AlipayGphone` 的主进程运行。
- SystemUI 守护服务只在 `processName == com.android.systemui` 时运行。

SystemUI 属于关键系统进程。注入代码必须小、无阻塞，并在所有 Hook 和广播入口捕获 `Throwable`。不要把完整业务、网络访问、磁盘扫描或 root 命令直接放在 SystemUI 主线程。

### 支付宝探针

支付宝侧只收集状态，不负责自救：

1. Hook `Application.attach(Context)`，完成一次性初始化。
2. 使用 `Handler(Looper.getMainLooper())` 每分钟发送主 Looper 心跳。
3. Hook `NotificationManager.notify()` 的常用重载，观察支付宝发出的 Sesame-GR 通知。
4. 从通知中识别以下状态：
   - `执行中`
   - `下次执行 HH:mm:ss`
   - `上次执行 HH:mm:ss ...`
5. 将心跳和通知状态发送给进程外守护服务。
6. 在收到带恢复标记的支付宝启动请求时，尽力设置 `showWhenLocked`、`turnScreenOn`，并仅在 Activity 窗口实际获得焦点后回报前台恢复结果。

跨进程时间判断使用 `SystemClock.elapsedRealtime()`，不能只使用墙上时间，避免用户校时、网络校时或时区变化造成误判。

### Sesame-GR 通知特征

当前上游版本的通知特征为：

- 支付宝包名：`com.eg.android.AlipayGphone`
- 通知 ID：`99`
- Channel ID：`io.github.lazyimmortal.sesame.ANTFOREST_NOTIFY_CHANNEL`
- SubText：`芝麻粒`
- 标题常见前缀：`启动中`、`执行中`、`下次执行`
- 内容常见前缀：`上次执行`

这些属于观察到的外部特征，不是稳定 API。当前实现将其作为 Sesame-GR 专用解析规则写在代码中，设置页不覆盖通知 ID 或 Channel ID。支持其他模块需要增加独立的配置 profile。

### SystemUI 守护服务

SystemUI 侧维护状态机：

- 最近一次主 Looper 心跳。
- 最近一次通知更新。
- 最近一次“执行中”。
- 最近一次“下次执行”及其目标时间。
- 最近一次恢复动作和当前冷却时间。
- 当前支付宝 PID/用户 ID（如可获得）。
- 锁屏时的前台拉起请求、屏幕唤醒锁和可见性确认。

检查触发点：

- 定时检查。
- `ACTION_SCREEN_ON`。
- `ACTION_USER_PRESENT`。
- SystemUI 进程重新启动后进行带宽限的冷启动检查。

当前默认阈值和策略：

- 主 Looper 心跳超过 10 分钟未更新：疑似进程冻结。
- “下次执行”逾期超过 10 分钟：疑似调度失效。
- 停留在“执行中”超过 45 分钟：疑似任务线程卡死。
- 执行进度停滞阈值为 60 分钟；它只在自动软恢复打开时进入旧的软恢复链路。
- 锁屏前台拉起默认开启，间隔为 5 分钟；它不依赖通知状态判断。
- 自动软恢复和 root 硬恢复默认关闭。
- 每次硬恢复后冷却 20 分钟。
- 每小时最多硬恢复 3 次，超过后停止自动重启并记录明确原因。

阈值必须可配置，默认值要保守，避免正常的长任务或支付宝升级过程触发重启风暴。

### 分级恢复

当前主要恢复路径是锁屏前台拉起：

1. 锁屏时按配置间隔请求唤醒屏幕。
2. 通过固定参数的 root 命令启动 `com.eg.android.AlipayGphone/.AlipayLogin`。
3. 支付宝侧尽力设置 `showWhenLocked`、`turnScreenOn`，并等待窗口实际获得焦点。
4. 当前设备上安全锁屏仍可能使窗口不可见，因此 root 硬回退默认关闭。

旧的 Sesame-GR 软恢复链路仍保留在代码中用于兼容和诊断，但 `autoSoftRecovery` 默认关闭：

1. 可选发送 `com.eg.android.AlipayGphone.sesame.execute`。
2. 观察 90 秒后可发送 `com.eg.android.AlipayGphone.sesame.restart`。
3. 只有显式开启 root 硬恢复时才会执行 `force-stop + start`。

当前没有实现 SystemUI system UID 的 `forceStopPackage` / `startActivityAsUser` 路径，硬恢复只通过独立 root 执行组件完成：

- root 权限只授予本模块 APK 的 UID，不要要求用户给 SystemUI 授予 `su`。
- SystemUI 只向本模块的显式组件发送恢复请求。
- 本模块进程通过 `su` 执行固定、参数受控的 `am force-stop --user ...` 和 `am start --user ...`。
- 禁止拼接未经验证的包名、组件名或任意用户输入到 root shell。

## 设置页关于入口

- 设置页右上角应提供一个可点击的更多/关于图标。
- 点击图标后应弹出“关于”入口；进入关于页后至少显示当前模块版本、源代码入口和反馈入口。
- “源代码”指向项目主页：`https://github.com/kjqg-cn/Sesame-Guardian`。
- “反馈”指向 GitHub Issues：`https://github.com/kjqg-cn/Sesame-Guardian/issues`。
- 关于页可继续扩展许可证、构建信息、免责声明等内容；新增外链必须使用明确的可访问性描述，并通过系统浏览器打开。

### 安全规则

- 所有恢复操作必须有冷却、频率限制和日志。
- 第一次收到心跳前不应立即判定故障，应有启动宽限期。
- 系统启动、支付宝升级、模块升级和用户主动强停期间应避免自动拉起循环。
- 用户在设置中关闭守护服务后，必须停止诊断、锁屏前台拉起和恢复动作。
- 自动软恢复和 root 硬恢复默认关闭；诊断阈值默认只用于记录。
- 不 Hook `system_server`，除非 SystemUI 方案已被证明无法满足需求且用户明确接受更高风险。
- 不在 SystemUI 中强行设置 Keyguard 遮挡；当前设备上该路径可能造成锁屏黑屏，相关桥接代码保持停用。
- 不设置所有支付宝进程的 `oom_score_adj=-1000`。支付宝内存和线程压力较大，强行保护全部子进程可能挤压 SystemUI、桌面或系统服务。

## 当前实施状态与后续

已完成：

- 支付宝主进程识别和主 Looper 心跳。
- Sesame-GR 通知 Hook、状态解析和跨进程诊断事件。
- SystemUI 进程外状态机、启动宽限、冷却和频率限制。
- 锁屏前台拉起、屏幕唤醒和 root 执行组件。
- Activity 锁屏窗口 Hook、实际可见性确认和日志记录。
- 设置页、倒序日志页、配置迁移和单元测试。

后续工作应优先围绕真机证据进行：

1. 继续观察停用 Keyguard 遮挡桥接后是否还会出现锁屏黑屏。
2. 判断 5 分钟前台拉起间隔对功耗和 Sesame-GR 稳定性的实际影响。
3. 若需要支持其他支付宝内 LSPosed 模块，再将通知、广播和目标 Activity 抽象成 profile。
4. 提交公开仓库前检查 `reference/` 中的历史快照和敏感字符串，并补充许可证。

## 验收标准

- Sesame-GR 无需重新编译或修改。
- 新模块禁用后不改变支付宝和 Sesame-GR 的行为。
- 支付宝主进程被杀后，守护服务能识别心跳中断。
- 支付宝主进程仍在但主 Looper 被阻塞时，能在阈值后识别。
- Sesame-GR 通知超过预期时间未更新时，能识别调度失效。
- 锁屏前台拉起会按冷却和间隔执行，不因一次可见性失败立即形成重启循环。
- 自动软恢复和 root 硬恢复默认关闭；显式开启时必须遵守冷却和每小时上限。
- root 硬恢复命令成功后，支付宝和 Sesame-GR 能重新初始化；锁屏 Activity 是否真正可见取决于 ROM。
- SystemUI 重启后可以恢复监控，模块异常不能形成 SystemUI 崩溃循环。
- 所有检测和恢复动作都能在模块设置页或日志中看到原因、时间和结果。

## 参考文件

`reference/` 保存了创建本项目时 Sesame-GR 的相关源码快照和用户截图，仅用于理解现有外部行为：

- `reference/sesame-gr/util/NotificationUtil.java`
- `reference/sesame-gr/hook/ApplicationHook.java`
- `reference/sesame-gr/model/BaseModel.java`
- `reference/sesame-gr/util/ClassUtil.java`
- `reference/sesame-gr/AndroidManifest.xml`
- `reference/sesame-gr/res/arrays.xml`
- `reference/device-pic/`

参考快照可能落后于上游。实现不能把这些文件作为编译依赖；需要确认上游当前行为时，应重新查看 Sesame-GR 的最新版本。

## 当前工程状态

项目已经是可独立构建、安装和升级的 LSPosed 模块，核心代码位于 `app/src/main/java`。当前默认策略是：

- 使用支付宝主进程心跳和 Sesame-GR 通知做诊断；
- 锁屏期间定时拉起支付宝作为主要保活动作；
- 自动软恢复和 root 硬恢复关闭；
- 不输入锁屏密码，不保证安全锁屏上显示支付宝窗口；
- 诊断日志默认在设置页按最新记录倒序显示。

当前设备验证环境为 Xiaomi/HyperOS、Android 16。锁屏前台 Activity 在该 ROM 上仍可能被 Keyguard 覆盖，因此不能把“收到 Activity 生命周期事件”当作真正可见的保证。
