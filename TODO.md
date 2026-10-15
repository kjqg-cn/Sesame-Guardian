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

- [ ] **1.1 LSPosed 模块启用与作用域检测（未启用时只读防误操）**
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
  - **涉及组件**：`MainActivity.java`, `WatchdogHook.java`, `WatchdogConfigStore.java`

- [ ] **1.2 基于“下次执行时间”的智能按需唤醒**
  - **现状分析**：当前 SystemUI 守护以固定间隔（默认 5 分钟）盲目拉起前台，即使通知中明确标明 30 分钟后才需要执行，中间的拉起操作完全属于无用功。
  - **实现方案**：
    - 在 `SystemUiWatchdogState` 中记录由 `AlipayNotificationProbe` 解析出的目标执行时间（`nextExecutionElapsedRealtime`）。
    - 改造 `handleForegroundRecovery`：优先以“下次执行时间 - 1~2 分钟宽限期”作为唤醒触发点；仅在通知缺失、通知失效或心跳超期停滞时，才退回到配置的保底拉起间隔。
  - **涉及组件**：`SystemUiWatchdog.java`, `SystemUiWatchdogState.java`, `AlipayNotificationProbe.java`
  - **预期效果**：锁屏期间的亮屏次数预期降低 70%~80% 以上。

- [ ] **1.3 夜间免打扰 / 节能时段**
  - **现状分析**：夜间（如 01:00 - 06:30）几乎没有蚂蚁森林能量生成，锁屏亮屏容易打扰睡眠并造成待机掉电。
  - **实现方案**：
    - 在配置中增加“免打扰时段”（默认可预设为 `01:00 - 06:30`，支持在设置页开关与自定义时间范围）。
    - 在免打扰期间：可选择“完全不点亮屏幕”（仅被动记录）或“将拉起保底间隔自动延长至 30~60 分钟”。
  - **涉及组件**：`WatchdogConfig.java`, `WatchdogConfigStore.java`, `MainActivity.java`, `SystemUiWatchdog.java`

- [ ] **1.4 唤醒后快速主动熄屏与退回后台**
  - **现状分析**：拉起支付宝后，持有 `SCREEN_BRIGHT_WAKE_LOCK` 达 30 秒，屏幕需等待系统锁屏超时才会熄灭。
  - **实现方案**：
    - 在支付宝侧确认收到 `onResume` 且窗口实际可见/获得焦点后，向守护服务回报就绪事件。
    - 守护服务在确认拉起成功并维持短暂宽限（如 3~5 秒）后，主动释放 WakeLock，并可通过 root 调用 `input keyevent 26` 或系统熄屏接口及时灭屏。
    - （可选）调用 `activity.moveTaskToBack(true)` 促使支付宝退回后台，降低前台 CPU 占用。
  - **涉及组件**：`AlipayForegroundHook.java`, `SystemUiWatchdog.java`, `RootCommandRunner.java`

- [ ] **1.5 距离传感器避让（防兜里/面朝下亮屏）**
  - **实现方案**：在 SystemUI 触发屏幕唤醒前，读取系统距离传感器（`Sensor.TYPE_PROXIMITY`）。若处于遮挡状态（手机装在口袋或正面朝下静置），则暂缓亮屏拉起，待传感器恢复正常后再触发。
  - **涉及组件**：`SystemUiWatchdog.java`

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

## 迭代建议与版本排期

- **v0.2.0**：重点交付 **第一期 1.1（模块激活与作用域准入检测）**、**1.2（按需唤醒）** 与 **1.3（免打扰时段）**，保障前置门禁并解决锁屏功耗体验。
- **v0.3.0**：交付 **第二期 2.1（首页 Dashboard 状态卡片）** 与 **第一期 1.4（主动熄屏）**。
- **v0.4.0+**：视社区与真机反馈，推进 **Root Shell 复用** 与 **多模块 Profile**。
