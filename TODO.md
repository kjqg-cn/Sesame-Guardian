# Sesame Guardian 迭代规划与 TODO 清单

本项目旨在为支付宝内运行的 LSPosed 模块（如 Sesame-GR）提供可靠、低功耗、可观测的进程外守护机制。
本文档用于跟踪后续版本的优化项与功能迭代规划。

---

## 阶段规划概览

| 阶段 | 核心主题 | 目标 | 优先级 |
| :--- | :--- | :--- | :--- |
| **第一期** | **生效准入保障与功耗优化** | 前置拦截未激活误配置，并彻底解决锁屏频繁盲目亮屏与无效耗电问题 | **P0（最高）** |
| **第二期** | **可观测性与 Dashboard** | 解决“当前守护到底有没有生效”的用户体感问题，提供实时状态面板 | **P1（高）** |
| **第三期** | **底层性能与通用扩展** | 降低 Root 进程创建开销，抽象多模块 Profile，支持非亮屏解冻探索 | **P2（中）** |

---

## 第一期：生效准入保障与功耗优化（P0）

直击模块使用的最前置门禁以及熄屏锁屏场景下的体验痛点，防止未激活误配置，并将“定时盲目拉起”演进为“智能按需唤醒”。

- [x] **1.1 LSPosed 模块启用与作用域检测（未启用时只读防误操）**
  - **现状分析**：用户在安装模块后，常出现忘记在 LSPosed 中启用、漏选作用域（需包含 SystemUI 与支付宝）、或启用后未重启进程的情况。这是使用模块的最前置条件，如果前置未就绪，配置任何参数都是徒劳且容易造成排查困惑。
  - **实现方案**：
    - **双层检测机制**：
      1. *框架激活*：通过模块内部桩方法 Hook（`isModuleActive()`），判断模块是否被 Xposed/LSPosed 框架整体加载；
      2. *作用域连通*：通过与 SystemUI 守护服务的 IPC 握手，确认核心宿主是否真正完成注入并在运行。
    - **UI 交互（禁用操作 / 只读模式）**：
      - 检测到未激活或核心作用域未就绪时，顶部显示醒目的警示横幅（若已安装 LSPosed 可提供快捷跳转），将开关、输入框和“保存配置”按钮全部置灰（`setEnabled(false)`），进入只读浏览模式。
    - **严格的“防回滚 / 防覆盖”保障**：
      - 未启用或停用时**仅限制保存（Save），绝不重置或回滚现有配置**。
      - 本地 `WatchdogConfigStore` 正常加载已有配置用于界面回显；用户后续重新启用模块时，原配置完好保留，无需重复配置。
    - **系统兼容与开机时效保障**：
      - 声明 `<queries>` 支持 Android 11+ 包可见性，保障 LSPosed 快捷跳转入口正常识别；
      - 运行状态时效采用单调时间（`elapsedRealtime`）结合开机基准时间，防止校时跳变与跨重启误判。
  - **涉及组件**：`MainActivity.java`, `WatchdogHook.java`, `WatchdogConfigStore.java`, `ModuleActivationStatus.java`, `AndroidManifest.xml`

- [x] **1.2 基于“下次执行时间”的智能按需唤醒**
  - **现状分析**：当前 SystemUI 守护以固定间隔（默认 5 分钟）盲目拉起前台，即使通知中明确标明 30 分钟后才需要执行，中间的拉起操作完全属于无用功。
  - **实现方案**：
    - 在 `SystemUiWatchdogState` 中利用已解析的目标执行时间（`nextExecutionElapsedRealtime`），实现按需唤醒判断 `checkForegroundRecoveryTrigger`。
    - 优先以计划执行时间前 90 秒作为提前唤醒窗口（`SCHEDULED_DUE`），支持同一执行周期去重防抖与 60 秒冷却防抖；
    - 在计划时间缺失、计划时间逾期、心跳超时或正在执行时，无缝回退至配置的保底拉起间隔（`FALLBACK_INTERVAL`）；
    - 设置页文案优化，明确区分智能按需唤醒与保底拉起间隔，并在日志中明确区分触发源。
  - **涉及组件**：`SystemUiWatchdog.java`, `SystemUiWatchdogState.java`, `MainActivity.java`, `SystemUiWatchdogStateTest.java`
  - **预期效果**：锁屏期间的亮屏次数预期降低 70%~80% 以上。

- [x] **1.3 夜间免打扰 / 节能时段**
  - **现状分析**：夜间（如 01:00 - 06:30）几乎没有蚂蚁森林能量生成，锁屏亮屏容易打扰睡眠并造成待机掉电。
  - **实现方案**：
    - 在 `WatchdogConfig` 中增加免打扰配置项（默认预设为 `01:00 - 06:30`，默认关闭 `quietHoursEnabled = false`），支持小时与分钟的合法范围校验；
    - 在 `WatchdogConfig.isInQuietHours(wallClockMillis)` 中提供日内与跨午夜（如 `23:00 - 06:30`）时段判断；
    - 在 `SystemUiWatchdogState.checkForegroundRecoveryTrigger()` 与 `SystemUiWatchdog` 恢复链路中增加全局免打扰拦截，免打扰期间完全抑制智能按需亮屏、保底拉起以及软/硬故障恢复流程（不发广播、不调用 Root 重启、不亮屏），仅保留被动心跳与通知观察；离开免打扰时段后自动恢复正常拉起与故障自愈；
    - 在 `MainActivity` 中提供独立的夜间免打扰卡片面板、列表行点击切换与 `TimePickerDialog` 原生时间选择器，未激活时置灰保护；日志格式化同步展示免打扰状态与区间。
  - **涉及组件**：`WatchdogConfig.java`, `WatchdogConfigStore.java`, `WatchdogContract.java`, `SystemUiWatchdog.java`, `SystemUiWatchdogState.java`, `WatchdogLogFormatter.java`, `MainActivity.java`, `WatchdogConfigTest.java`, `SystemUiWatchdogStateTest.java`, `WatchdogLogFormatterTest.java`
  - **预期效果**：夜间免打扰时段内锁屏零唤醒、零亮屏、零主动恢复打扰，彻底杜绝夜间无效拉起与睡眠打扰。

- [x] **1.4 唤醒后快速主动熄屏与退回后台**
  - **现状痛点**：拉起支付宝后，持有 `SCREEN_BRIGHT_WAKE_LOCK` 达 30 秒，屏幕需等待系统锁屏超时才会熄灭，且支付宝长期驻留前台顶层增加耗电与误触风险。
  - **实现方案与落地成果**：
    - **就绪闭环回调**：支付宝侧 `AlipayForegroundHook` 在生命周期恢复就绪后（适配安全锁屏下焦点缺失回退识别），主动向 SystemUI 发送恢复成功确认；
    - **缓冲退回后台**：就绪后给予 3 秒安全缓冲（确保 Sesame-GR 调度器与任务已开始运行），在确认锁屏未解锁前提下，调用 `activity.moveTaskToBack(true)` 将支付宝安全退回后台任务栈，顶层切回 Launcher；
    - **双通道快速熄屏（SystemUI 优先 + Root 幂等降级）**：SystemUI 守护服务收到恢复确认后启动 3 秒延时，对称释放 `SCREEN_BRIGHT_WAKE_LOCK` 并优先反射系统接口灭屏；若因 ROM 限制反射失败，自动无缝降级通过 Root 发送专用幂等灭屏指令 `input keyevent 223`（`KEYCODE_SLEEP`），已熄屏时绝不反向唤醒；
    - **全链路参数透传与硬恢复一致性**：在“前台保活”面板新增“唤醒后快速熄屏”开关项（默认开启），不仅支持普通锁屏前台拉起，亦全链路透传至 Root 硬恢复（`am force-stop + start`）通道，彻底消除开关状态漂移；
    - **防误熄安全防护**：用户在亮屏期间解锁屏幕（`ACTION_USER_PRESENT`）时，立即取消待执行的快速熄屏与退后台逻辑，避免干扰用户正常使用；
    - **模块私有日志闭环**：支付宝探针初始化私有日志通道，将 Activity 退后台结果与生命周期就绪状态直接写入 `watchdog.log`，前端日志界面可直接查阅。
  - **涉及组件**：`AlipayForegroundHook.java`, `AlipayProbe.java`, `SystemUiWatchdog.java`, `WatchdogConfig.java`, `WatchdogConfigStore.java`, `WatchdogContract.java`, `WatchdogCommandProvider.java`, `RootCommandRunner.java`, `RootRecoveryExecutor.java`, `MainActivity.java`, `WatchdogConfigTest.java`, `RootCommandRunnerTest.java`
  - **预期效果**：锁屏拉起后亮屏停留大幅缩短至 3 秒，支付宝自动退回后台并主动快速灭屏，彻底杜绝长时间无效亮屏与前台误触。

- [x] **1.5 距离传感器避让（防兜里/面朝下亮屏）**
  - **现状痛点**：当手机装在裤兜、背包或正面扣在桌面上时，锁屏前台拉起盲目点亮屏幕，不仅在封闭空间内造成无效发热和待机掉电，还极易引发屏幕误触甚至意外报警。
  - **实现方案与落地成果**：
    - **按需单次极速采样（Zero Idle Power）**：坚决不采用后台常驻监听（避免传感器硬件持续供电耗电），仅在即将唤醒屏幕前向 `SensorManager` 注册单次异步监听，限制 400ms 超时窗口获取读数后即刻注销；
    - **物理测距与多传感器姿态融合判定**：物理红外距离传感器近距离（`distance < 5.0cm`）直接判定遮挡；针对无顶置红外硬件机型的虚拟超声波距离传感器盲区，融合环境光照（`lux <= 1.0`）与机身重力姿态（正面朝下倒扣 `Z轴 < -6.0 m/s²` 或头朝下入兜 `Y轴 < -5.0 m/s²`）判定，兼顾传统机型与超声波旗舰，且夜间正面平放关灯正常放行不误判；
    - **避让与容错保护（Fail-safe）**：检测到遮挡时立即跳过本次屏幕唤醒与前台拉起，并在诊断日志中明确记录原因与传感器特征，刷新防抖冷却；若设备无传感器、注册失败或采样超时则自动保底放行，避免保活永久卡死；用户解锁屏幕或流程取消时即时注销清理；
    - **设置开关与配置下发**：在“前台保活”面板新增“距离传感器避让”独立开关（默认开启），支持动态下发与即时生效，日志格式化同步展示配置项。
  - **涉及组件**：`SystemUiWatchdog.java`, `WatchdogConfig.java`, `WatchdogConfigStore.java`, `WatchdogContract.java`, `WatchdogLogFormatter.java`, `MainActivity.java`, `WatchdogConfigTest.java`, `WatchdogLogFormatterTest.java`
  - **预期效果**：在手机处于口袋、背包或正面扣放时完全抑制无效亮屏，彻底杜绝封闭空间发热与误触，进一步降低待机功耗。

- [x] **1.6 界面布局交互优化与二级页面视觉统一**
  - **现状痛点**：设置页保存与 Root 检测按钮位于长列表底部，每次修改需频繁下拉到底；日志页缺少统一顶栏与返回键，状态栏未适配浅色沉浸，且操作按钮随长日志滚出屏幕。
  - **落地成果**：
    - 主配置页操作栏吸底常驻（Sticky Bottom Bar），配置项与底部操作解耦；配置页设置 `adjustResize` 软键盘避让；
    - “查看日志”收纳至右上角操作菜单，保持主界面清爽；
    - 日志页对齐“关于”页标准 64dp AppBar、返回箭头与状态栏浅色沉浸；
    - 日志页概览与操作按钮固定置顶，日志正文独立滚动，长日志浏览更便捷。
  - **涉及组件**：`MainActivity.java`, `LogActivity.java`, `AndroidManifest.xml`

---

## 第二期：用户交互与可观测性（P1）

提升直观使用体验，无需在翻找文本日志的情况下掌握模块当前的健康状态。

- [ ] **2.1 首页实时状态看板（Dashboard）**
  - **现状分析**：目前设置页只有表单配置，用户无法直观确认 SystemUI 守护是否注入成功、支付宝是否存活、上次执行情况等。
  - **实现方案**：
    - 在 `MainActivity` 顶部增加卡片式看板组件。
    - 通过 ContentProvider 或广播从 SystemUI 查询最新健康快照，并定时刷新：
      - **守护状态**：运行中 (SystemUI PID: xxx) / 未注入或未连接
      - **支付宝状态**：存活 (PID: xxx) / 已退出 / 疑似冻结
      - **最近心跳**：X 秒前（正常 / 超时警告）
      - **Sesame-GR 状态**：下次执行 07:45:00 / 正在执行 / 状态未知
      - **上次前台拉起**：X 分钟前（成功 / 失败 / 未触发）
  - **涉及组件**：`MainActivity.java`, `WatchdogQueryProvider.java` / `WatchdogContract.java`, `SystemUiWatchdog.java`

- [ ] **2.2 手动即时测试 / 一键诊断**
  - **实现方案**：在设置页提供“立即测试前台拉起”按钮，方便用户在配置完成后一键验证 root 授权、屏幕点亮与拉起链路是否通畅，并在界面上实时回显执行结果。
  - **涉及组件**：`MainActivity.java`, `WatchdogCommandProvider.java`

---

## 第三期：底层性能与通用扩展（P2）

面向长远演进，提高系统资源利用效率并拓展适用场景。

- [ ] **3.1 持久化 Root Shell 会话（复用 Session）**
  - **现状分析**：`RootCommandRunner` 每次执行命令都会新建 `ProcessBuilder("/system/bin/su", ...)`，反复 fork 进程开销大，且容易导致 root 管理器（Magisk/KernelSU/APatch）频繁记录授权日志。
  - **实现方案**：引入持久化交互式 Shell 会话机制（或长连接守护子进程），保持单个 `su` session，通过管道标准输入输出发送命令并解析退出状态。
  - **涉及组件**：`RootCommandRunner.java`, `RootRecoveryExecutor.java`

- [ ] **3.2 多模块/多插件适配 Profile 化**
  - **现状分析**：当前通知 ID `99`、Channel ID、SubText `芝麻粒` 均为 Sesame-GR 硬编码。
  - **实现方案**：
    - 将通知监听特征、恢复广播、拉起目标 Activity 抽象为 `ModuleProfile` 模型。
    - 内置常用 Profile（如 Sesame-GR、其他自动化脚本），并支持用户自定义正则或通知过滤条件。
  - **涉及组件**：`AlipayNotificationProbe.java`, `ModuleProfile.java`, `WatchdogConfig.java`

- [ ] **3.3 息屏/免亮屏唤醒与解冻路径探索**
  - **研究方向**：针对特定 ROM（如 HyperOS / AOSP），探索在不点亮物理显示屏（Display STATE_OFF）的前提下，通过 VirtualDisplay、专用保活广播或修改 cgroups/freezer 节点直接解冻支付宝主线程 Looper 的可行性。
  - **涉及组件**：`SystemUiWatchdog.java`, `RootCommandRunner.java`

---

## 版本交付与迭代排期

- **v0.1.0**：项目初始发布，搭建独立 LSPosed 框架、SystemUI 进程外守护服务、支付宝主 Looper 心跳与 Root 恢复链路。
- **v0.1.1**：优化关于页与日志页，补全文档与项目规范。
- **v0.1.2**：交付 **第一期 1.1（模块激活与作用域准入检测）**、**1.2（智能按需唤醒）**、**1.3（夜间免打扰）** 与 **1.6（界面布局与二级页统一）**。
- **v0.1.3（当前）**：交付 **第一期 1.4（唤醒后快速熄屏与退回后台）** 与 **1.5（距离传感器姿态融合避让）**，第一期生效准入保障与功耗优化圆满收官。
- **v0.2.0**：规划交付 **第二期 2.1（首页 Dashboard 状态卡片）** 与 **2.2（一键即时诊断）**。
- **v0.3.0+**：视社区与真机反馈，推进 **2.2（一键即时诊断）**、**3.1（Root Shell 复用）** 与 **3.2（多模块 Profile）**。
