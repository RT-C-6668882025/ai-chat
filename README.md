# AI 伴侣 · AI Companion

**本地优先的 AI 角色陪伴应用** —— 自定义角色 + 记忆系统 + 变量系统 + 消息树 + 剧情图 + 提示词市场。

数据全本地存储（文件 JSON），模型自备（用户填 API Key），支持 Anthropic 与 OpenAI 兼容双协议。

---

## 一、功能总览

### 核心三机制

| 机制 | 说明 |
|---|---|
| **记忆系统** | 对话自动提炼成事件，带权重衰减；按 Top-K 注入长期上下文；可置顶/恢复/改写联动 |
| **消息树** | 每条消息带 `parentId`，重新生成不销毁历史，分支可回溯、可切换、可重生成 |
| **剧情图** | 剧情是"节点 + 边"组成的图，变量驱动跳转；5 种触发边（选项/条件/关键词/语义/自动） |

### 三种内容形态

| 形态 | 结构 |
|---|---|
| 角色 | 只有人设，无剧情（日常陪伴、纯聊天） |
| 轻剧情 | 人设 + 一段情境描述 |
| 完整故事 | 人设 + 世界观 + 剧情图 + 变量 |

### 功能清单（对照规格书 §0–§9）

**P0 — 最小可用**
- [x] 存储层封装（文件 JSON，原子写入）
- [x] 完整数据模型（Character / Story / Session / Message / MemoryEvent / ChatCommand / Feedback）
- [x] 首次引导（API 配置）
- [x] 角色列表 + 手动创建角色
- [x] 对话页基础：发送、流式接收、括号动作解析、气泡渲染
- [x] API 双模式 + SSE 流式（Anthropic + OpenAI 兼容）
- [x] 设置页（API 部分）

**P1 — 核心体感**
- [x] 记忆系统全流程（生成、衰减、注入、面板、编辑、置顶、恢复）
- [x] 变量系统 + 好感度 + 心声解析展示
- [x] 消息树 + 带方向的重新生成（7 种方向）+ 分支切换 `‹2/3›` + 回溯
- [x] 改写 AI 消息 + 记忆联动
- [x] 多气泡拆分与延迟渲染（`|||` 分隔）
- [x] 提示词模板可编辑（设置页 + 恢复默认）
- [x] 危机干预关键词（不可关闭提示条）

**P2 — 玩法**
- [x] 视角指令（内置 20 条：窥探 8 / 情境 7 / 产出 5）+ 指令编辑器 + 入戏记录
- [x] 回复灵感（3 条建议 chip 点击填入）
- [x] 沉浸模式（单击空白隐藏/显示 UI）
- [x] 轻剧情类型 + 完整剧情图（序章 / 节点 / 边 / 结局图鉴 / canEnd / 漂移检测）
- [x] 反馈累积（长按消息 → 不喜欢 → 原因多选 → 注入 prompt「需要避免」段）

**P3 — 完善**
- [x] 剧情图条件边 + 循环合流 + JSON 高级编辑
- [x] 指令编辑器（自定义指令增删改）
- [x] 三问引导生成角色（AI 生成角色卡 + AI 优化人设）
- [x] 主动消息（进入会话时检测时间间隔触发）
- [x] 成长统计卡片（「我们的旅程」：相识天数/轮数/记忆数/结局数/最高好感度）
- [x] 数据导入导出（JSON 合并导入）
- [x] 多角色场景（故事内多 speaker + 旁白）
- [x] **预设提示词市场**（4 个 GitHub 来源统一解析为 Character + 自定义 URL 导入）

**系统能力**
- [x] 屏幕安全区适配（WindowInsetsCompat + 24dp 兜底 + IME 键盘处理）
- [x] 返回键三重拦截（onBackPressed / dispatchKeyEvent / OnBackInvokedCallback 反射）+ 300ms 防抖
- [x] 消息分页加载（loadChunk=50 + 「加载更早」）、智能滚动、右滑返回
- [x] 错误友好化（401/403/404/429/5xx/超时/断网 → 中文提示 + 重试）
- [x] 崩溃日志兜底（`/sdcard/crash_log.txt` + 应用外部目录双写）
- [x] Agent 工具调用模式（Api.callAgent + 5 工具，与标签后处理并存）

---

## 二、APP 架构

### 总体形态

**本地优先的单 Activity Android 应用**（原生 APK），包名 `com.vibe.generated.p20260823`。
单 Activity + ViewFlipper 页面切换，无 Fragment，无第三方框架，Java 7 语法约束（无 lambda / 方法引用 / try-with-resources）。

```
┌────────────────────────────────────────────────┐
│ MainActivity (extends ShadowActivity)  ~4700行  │ ← 唯一 Activity
│  ├─ 9 屏视图切换 + 4 个 RecyclerView.Adapter    │
│  ├─ 三重返回键拦截 + 手势 + 沉浸模式 + 对话框    │
│  └─ 标签后处理对话管线（requestReply）          │
├────────────────────────────────────────────────┤
│ ChatEngine   —— 双模式：标签解析 + Agent 工具入口 │
│ StoryEngine  —— 剧情图（5 种边 + 赋值 + 结局）   │
│ Api          —— 双协议 SSE + Agent loop         │
│ Store        —— 文件 JSON 持久化                 │
│ MarketClient —— GitHub 提示词市场拉取解析        │
│ Template / Insets / AppLogger / 崩溃兜底        │
└────────────────────────────────────────────────┘
```

### 分层职责

| 层 | 类 | 职责 |
|---|---|---|
| **表现层** | `MainActivity` | 全部 UI：9 屏 inflate/切换/刷新；消息树渲染（回溯 pathMessages）、分支 `‹2/3›`、7 方向重生成、改写、记忆/变量面板、指令库（20 条）、剧情编辑器、结局面板、错误重试、返回键三级拦截、右滑手势、沉浸模式 |
| **业务层** | `ChatEngine` | 双通道：① `buildSystemPrompt` + `parseResponse`（标签后处理：`<var>/<inner>/（）/‖‖‖`）；② `buildAgentSystemPrompt` + `buildTools` + `sendAgentMessage`（Agent 工具模式）；记忆衰减/总结/Top-K 注入、危机关键词、阶段映射 |
| **业务层** | `StoryEngine` | 剧情图：节点/边查询、5 种触发边（选项/条件/关键词/语义/自动）、变量赋值求值、结局解锁、旁白解析、漂移检测提示 |
| **数据层** | `Store` | `getFilesDir()/aic_config/` 下 config/characters/sessions/stories/records/commands 各自独立 JSON，原子写入；4 预设角色、示例剧情、导入导出、预设角色工厂 |
| **网络层** | `Api` | **双通道**：`callModel`（SSE 流式，记忆/灵感/judge 仍用）；`callAgent`（Agent 循环，非流式 + `tool_result` 回传，上限 10 次）；Anthropic + OpenAI 兼容双协议 |
| **网络层** | `MarketClient` | 4 个 GitHub 来源统一解析（Awesome-Prompts 递归 / CSV / YAML / SillyTavern JSON）+ 自定义 URL 判断（blob/tree/raw） |
| **工具层** | `Template` | mustache 风格模板渲染（变量/条件/循环） |
| **工具层** | `Insets` | `WindowInsetsCompat` 安全区适配：动态 padding + 24dp 兜底 + IME 键盘高度 |
| **工具层** | `AppLogger` / `CrashHandlerApp` / `SimpleImageLoader` | 日志 / 崩溃兜底 / 图片加载 |

### 页面导航模型

- `activity_main.xml` 仅一个空 `FrameLayout`（`root_container`）
- `showScreen(name)` 懒加载 + 缓存：首次 inflate 对应 `screen_*.xml` 并 addView，之后 `setVisibility(GONE/VISIBLE)` 切换 —— 9 屏常驻内存，**切换即刷新数据**
- `Insets.applySystemBars` 挂在根容器，**单点覆盖全部页面**（动态 inset + 24dp 兜底 + IME）

**9 个屏幕**：onboard（首次引导）/ home（角色列表）/ char_edit（角色编辑）/ chat（对话）/ memory（记忆面板）/ vars（变量面板）/ story（剧情编辑）/ commands（指令库）/ settings（设置）＋ 提示词市场（market，从主页 📦 进入）

### 数据流（标签后处理主链路）

```
用户输入 → Message 树落盘(Store)
        → ChatEngine.buildSystemPrompt(角色+记忆TopK+变量阶段+剧情节点+反馈)
        → Api.callModel(SSE流式) → onChunk 增量渲染
        → parseResponse → <var>更新变量 / <inner>心声 / （动作） / ‖‖‖多气泡
        → StoryEngine.resolveEdge 检查剧情边（关键词→条件→语义judge）
        → 记忆衰减 + 每15轮异步总结
        → JSON 落盘
```

### Agent 双通道生成管线

```
现有链路（MainActivity.requestReply）：
  用户输入 → buildSystemPrompt(含标签格式指令) → Api.callModel(SSE流式)
         → parseResponse 剥离 <var>/<inner>/（动作）/‖‖‖ → 渲染

新增链路（ChatEngine.sendAgentMessage，供后续接入）：
  用户输入 → buildAgentSystemPrompt(无格式指令 + 工具说明 + 调用顺序约束)
         → Api.callAgent(Agent loop, 上限10次)：
             模型输出 tool_use
               → 主线程执行工具 → tool_result 回传 → 继续请求
             直到模型输出纯文本 → onDone
         5 工具：send_bubble(多气泡) / update_variable(变量)
                / set_inner_voice(心声) / check_condition(语义边内嵌)
                / store_memory(主动记忆, 开关控制)
```

**关键设计：两条通道并存。** 记忆总结、灵感生成、指令生成、judge 判定仍走 `callModel` + `parseResponse`；`check_condition` 工具在**故事模式且有语义边**时才加入工具集；`store_memory` 由 `enableProactiveMemory` 配置控制。

### 返回键优先级（从高到低）

```
1. 对话框/底部弹窗 —— 系统原生优先消费
2. 沉浸模式 —— 先退出沉浸，恢复 UI
3. 子页面按层级返回父级（记忆/变量/指令 → 对话；剧情编辑 → 对话；角色编辑/设置 → 主页）
4. 对话页 → 主页
5. 主页 → 弹「退出 AI 伴侣？」确认框（禁止直接退出）
6. 首次引导 → 提示「请先配置 API 或创建角色」
7. 兜底 —— 未知状态回到主页
```

三重拦截：`onBackPressed` + `dispatchKeyEvent`（软键盘可见先收起键盘）+ `OnBackInvokedCallback`（API 33+ 反射注册），300ms 防抖防重复触发。

---

## 三、数据模型

```
Character（角色）─── 跨故事复用，改动同步
    │
    ├─→ Session（会话）─── 一次持续对话
    │       ├── Message[]      消息树（parentId）
    │       ├── MemoryEvent[]  记忆事件（weight 衰减）
    │       └── variables{}    变量当前值
    │
    └─→ Story（故事，可选）
            ├── World          世界观（不含情节）
            ├── PlotGraph      剧情图（PlotNode[] / PlotEdge[] / Variable[]）
            └── UserPersona    用户扮演设定
```

**消息树**：每条 Message 有 `parentId`，`session.currentLeafId` 指向活跃分支末端；渲染从 currentLeafId 沿 parentId 回溯到根反转。

**记忆不删除**：权重衰减低于阈值只是停止注入，数据保留，可恢复/置顶。

**内置变量**：`affection`（好感度）自动注入每个会话，范围 -100~200，映射为阶段描述（初识 <20 / 熟悉 20-59 / 亲近 60-119 / 深交 ≥120）注入 prompt。

---

## 四、目录结构

```
src/main/
├── AndroidManifest.xml
├── java/com/vibe/generated/p20260823/
│   ├── MainActivity.java       唯一 Activity（UI + 页面切换 + 返回键 + 手势）
│   ├── ChatEngine.java         业务层（prompt 组装 / 标签解析 / Agent 工具入口 / 记忆）
│   ├── StoryEngine.java        剧情图引擎（节点/边/赋值/结局/旁白）
│   ├── Api.java                网络层（Anthropic + OpenAI 兼容 / SSE / Agent loop）
│   ├── MarketClient.java       提示词市场（4 来源解析 + 自定义 URL）
│   ├── Store.java              数据层（文件 JSON 持久化 / 预设内容 / 导入导出）
│   ├── Template.java           模板渲染（mustache 风格）
│   ├── Insets.java             安全区适配工具
│   ├── AppLogger.java          日志工具
│   ├── CrashHandlerApp.java    Application + 崩溃兜底
│   └── SimpleImageLoader.java  图片加载
└── res/
    ├── drawable/        bg_chip / 启动图标前景背景
    ├── layout/          activity_main + 9 屏 + 4 列表项
    ├── mipmap-*/        启动图标（自适应 + 各密度 PNG）
    └── values/          字符串 / 颜色 / 主题（values-night 深色）
```

---

## 五、构建方式

### 方式一：设备端构建管线（当前开发环境）

AAPT2 + Javac + D8 三步管线（等效标准 Android 构建核心），无需 Gradle，依赖（AndroidX / Material / RecyclerView / CardView）已内置。构建产物直接注入 VibeApp 宿主运行。

### 方式二：GitHub Actions 云端构建独立 APK

工程已附带标准 Gradle 工程文件（`build.gradle` / `app/build.gradle` / `settings.gradle` / `gradle.properties` / `.github/workflows/build.yml`）：

```
触发：push 到 main 或手动 workflow_dispatch
环境：ubuntu-latest + JDK 17 + Gradle 8.13
命令：gradle assembleDebug
产物：上传 artifact「app-debug」（app/build/outputs/apk/debug/*.apk）
```

**依赖版本**（app/build.gradle）：

```gradle
implementation 'androidx.core:core:1.13.1'
implementation 'androidx.appcompat:appcompat:1.7.0'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.cardview:cardview:1.0.0'
```

minSdk 29 / targetSdk 36 / compileSdk 36。

> ⚠️ 独立构建注意事项：
> 1. 上传前删除 `AndroidManifest.xml` 里的 `package=` 属性（AGP 8.x 已由 `namespace` 承担）
> 2. 独立 APK 的 `MainActivity` 基类应为 `android.app.Activity`（ShadowActivity 由 VibeApp 宿主注入，独立安装时不存在）

---

## 六、使用说明

1. 首次启动进入引导页：选择 API 模式（Anthropic / OpenAI 兼容）→ 填 API Key → 地址 → 模型
   - 默认 Anthropic：`https://api.anthropic.com` + `claude-sonnet-4-6`
   - 默认 OpenAI 兼容：`https://api.deepseek.com` + `deepseek-chat`（支持 DeepSeek / Qwen / Kimi / 硅基流动 / Ollama）
2. 主页创建角色（或从 4 个预设角色 / 提示词市场导入）
3. 进入对话：发送 → 流式接收 → 多气泡 / 动作 / 心声 / 变量自动解析
4. 长按消息：复制 / 改写 / 重新生成（7 方向）/ 不喜欢 / 从这里开始新分支 / 删除
5. 剧情模式：在故事编辑中建剧情图（节点 + 边 + 变量赋值），对话中自动推进
6. 提示词市场：主页 📦 进入，6 个 Tab（Awesome-Prompts / ChatGPT Prompts / DSH 角色扮演 / SillyTavern / 粘贴导入 / 自定义 URL），预览 → 导入 → 自动扫描 `{{变量}}` 播种变量

---

## 七、已知限制

- 无端到端 API 联调（需自备真实 Key 验证）
- Agent 模式已实现于 Api/ChatEngine 层，未接入 MainActivity 对话链路（标签后处理为主链路）
- 多角色场景仅支持故事内多 speaker + 旁白
- 主动消息仅进入会话时触发，无后台推送
- GitHub API 未认证限流 60 次/小时（已用目录串行 + 正文懒加载策略规避）
- 独立 APK 与 VibeApp 宿主插件模式互斥（ShadowActivity 基类差异）
