# AI Companion

本地优先的 AI 角色扮演陪伴应用。角色、剧情、记忆、存档全部留在你自己的设备上，模型自备（填自己的 API Key），支持 OpenAI 兼容协议与 Anthropic 协议。

原生 Android，纯 Java + AndroidX，不用任何第三方网络库或 DI 框架。

[![Build APK](https://github.com/RT-C-6668882025/ai-chat/actions/workflows/build.yml/badge.svg)](https://github.com/RT-C-6668882025/ai-chat/actions/workflows/build.yml)

---

## 快速开始

### 装一个来用

到 [Releases](https://github.com/RT-C-6668882025/ai-chat/releases/latest) 下载最新的 `app-debug.apk` 直接安装。装好后进设置填 API Key 即可。

之后不用再来这里：应用内 **设置 → 关于 → 检查更新** 会读取最新 Release，有新版就下载安装。

### 自己构建

Android Studio 打开仓库根目录，Sync 后 Build → Build APK(s)。或者命令行：

```bash
./gradlew :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。需要本机装好 Android SDK（compileSdk 36）。

---

## 功能

### 对话

- **消息树**：每条消息记 `parentId`，重新生成不覆盖旧回复，而是长出一条兄弟分支。气泡右下角的 `‹2/3›` 可以来回切换，随时比较不同走向。
- **时光倒流**：选中历史里任意一句，从那里重新开始。之后的对话不会删除，保留成另一条分支，随时切回。
- **多气泡**：模型用 `|||` 分隔可以连发几条短消息，比一大段独白更像真人聊天。
- **心声**：`<inner>` 标签里的内容作为角色没说出口的想法单独展示。
- **括号指令**：见下方专门一节。
- **沉浸模式**：全屏、隐藏所有杂项、文字逐字浮现，点击跳过。适合坐下来读剧情。

### 记忆

对话会被自动提炼成记忆事件，带权重衰减。注入 prompt 时按 Top-K 挑选，衰减到阈值以下只是停止注入，**数据不删除**，可以在记忆面板里恢复或置顶。改写一条消息会连带把相关记忆标记为待更新。

衰减速率、注入条数、总结间隔都在设置里可调。

### 剧情

剧情是一张**图**，不是一条线：

- 节点带台词与剧情指引，边分选项边、条件边、关键词边、自动边
- 变量赋值挂在节点上（`affection+5` 这类）
- 条件边按变量值决定是否可走，因此同一个节点在不同状态下会通向不同地方
- 多结局，解锁后进结局图鉴

剧情可以手写，也可以让 AI 生成——填个题材和基调，直接产出整张分支图。生成结果会先做结构校验（节点 id 唯一、起始节点存在、所有边指向真实节点），不合法就报错丢弃，不会写坏存档。

### 养成

| 系统 | 说明 |
|---|---|
| **好感度** | 范围 -100~200，分初识 / 熟悉 / 亲近 / 深交四个阶段，阶段描述会注入 prompt 影响语气 |
| **里程碑** | 8 个好感度阈值，跨过时记下角色当时说的那句话，做成一张纪念卡 |
| **每日心情** | 每个角色每天有个心情（由日期+角色+好感度档位哈希得出，当天稳定），影响说话语气 |
| **连续陪伴** | 进对话自动签到，记连续天数与累计天数 |
| **成就** | 18 条，纯函数判定，老存档继续用会自动补齐已达成的 |
| **纪念册** | 时间轴聚合里程碑 / 结局 / 成就 / 置顶记忆，未解锁成就显示灰色剪影 |

### 括号指令

在输入框里打一对括号，内容不算「你的角色说的话」，而是当场生效的设定。两种写法自动识别：

| 你输入 | 效果 |
|---|---|
| `（好感度+10）` `（信任-5）` | 直接改变量，全局立即生效，**不消耗 API 调用** |
| `（天气=雨）` `（阵营=反抗军）` | 赋值，可以存文本 |
| `（新变量=3）` | 变量不存在就新建，会自动出现在变量面板 |
| `（下起了雨，气氛忽然安静）` | 场景指令：插一条旁白，并随下一句注入 prompt |

中英文括号都认，允许空格。长按工具栏的「动作」会直接插入一对括号。

### 提示词市场

内置四个 GitHub 来源（Awesome-Prompts / ChatGPT Prompts / DSH 角色扮演 / SillyTavern）加粘贴导入与自定义 URL。

列表**抓一次就缓存在本地**，之后进 Tab 直接读缓存不发请求——GitHub 未认证 API 只有 60 次/小时，而 Awesome 一次递归就要几十次请求。想要最新的点顶栏刷新。刷新失败时保留旧缓存而不是清空。

### 提示词随处可改

点角色头像（主页卡片或聊天顶栏）直接开编辑器，角色设定 / 补充设定 / 开场白 就地改。每一处都有「AI 帮我改」：说一句「更毒舌一点」，模型重写，**结果先预览再决定采用**。

全局提示词模板在设置里同样能让 AI 改，并且会检查占位符没丢——改坏了拒绝保存。

### 外观

暖阳（亮）与星夜（暗）两套完整配色，设置里可选「跟随系统 / 暖阳 / 星夜」。

---

## 配置

首次启动会引导填 API 配置，之后在设置里改。

| 项 | 说明 |
|---|---|
| API 模式 | `openai`（OpenAI 兼容）或 `anthropic` |
| Base URL | 默认 `https://api.deepseek.com`。DeepSeek / Qwen / Kimi / 硅基流动 / Ollama 等兼容端点都能填 |
| 模型分工 | `main` 对话、`memory` 记忆总结、`judge` 语义判定、`command` 指令生成，可以分别指定，留空则回落到 main |

其余可调项：流式开关、历史窗口长度、记忆参数（总结间隔 / 注入条数 / 衰减速率 / 淡出阈值）、多气泡上限、心声与变量开关、主动消息间隔。

---

## 提示词模板

设置里的模板用 mustache 风格，支持 `{{变量}}`、`{{#if 路径}}…{{/if}}`、`{{#each 数组}}…{{/each}}`。可用的占位符：

| 占位符 | 内容 |
|---|---|
| `{{char.name}}` `{{char.persona}}` `{{char.privateNote}}` | 角色本身 |
| `{{user.callMe}}` `{{user.setting}}` | 用户扮演设定 |
| `{{story.globalBackground}}` `{{story.situation}}` | 世界观与情境 |
| `{{#each memories}}{{this}}{{/each}}` | Top-K 记忆 |
| `{{#each visibleVariables}}{{this.name}}：{{this.value}}{{/each}}` | 全部变量 |
| `{{affectionStage}}` | 好感度阶段描述 |
| `{{sceneNote}}` | 括号写入的当前场景 |
| `{{daily.mood}}` `{{daily.moodHint}}` `{{daily.streak}}` | 今日心情与连续天数 |
| `{{#each feedback.reasons}}` | 用户标记过的「不喜欢」原因 |
| `{{config.*}}` | 各项开关，用于条件输出格式要求 |

新增的段落（今日状态、当前场景）都包在 `{{#if}}` 里，所以自定义过模板的人不受影响。

---

## 架构

单 Activity，手写页面栈，无 Fragment。Java 8 字节码但源码只用 Java 7 语法（无 lambda）。

```
MainActivity          唯一 Activity：12 个页面的 inflate/切换/刷新、
                      全部 Adapter、对话管线、返回栈与转场动画
├─ ChatEngine         prompt 组装、回复解析、记忆衰减与 Top-K、
│                     好感度阶段、危机关键词、消息树遍历
├─ StoryEngine        剧情图：节点/边查询、条件求值、变量赋值、结局
├─ Api                网络：OpenAI 兼容 + Anthropic 双协议、SSE 流式
├─ Store              持久化：filesDir 下各自独立的 JSON，预设内容，导入导出
├─ MarketClient       提示词市场四来源解析
├─ Daily              每日心情、签到、当日话题
├─ Milestones         好感度里程碑
├─ Achievements       成就判定
├─ Directive          括号指令解析
├─ Updater            检查更新、下载
├─ Json               从模型返回的文本里抠 JSON + 剧情结构校验
├─ Template           mustache 风格模板渲染
└─ Insets / AppLogger / CrashHandlerApp / SimpleImageLoader
```

**页面**（12）：onboard / home / char_edit / chat / memory / vars / story / commands / market / album / rewind / settings

`showScreen(name, forward)` 懒加载并缓存视图，前进时把当前页压入返回栈并从下方淡入，后退弹栈并从上方淡入。所有页内返回按钮都走 `goBack()`。

### 数据模型

```
Character ──┬─→ Session ──┬── Message[]      消息树（parentId 串联）
（跨会话复用）│             ├── MemoryEvent[]  记忆（weight 衰减）
            │             ├── variables{}    变量（含 affection）
            │             ├── milestones[]   里程碑
            │             └── achievements[] 成就
            └─→ Story ────┬── globalBackground / situation
                          ├── nodes[]        节点（choices / edges / assignments）
                          └── endings[]      结局
```

养成相关的字段全部挂在 session 里，读取一律用 `opt*` 带默认值，所以**老存档不需要迁移**，导入导出也不用改。

### 设计体系

`values/` 下 44 个语义色 token，`values-night/` 定义同名的一套，CI 前有脚本校验两边名字完全对应。另有 `dimens.xml`（间距/圆角/尺寸阶梯）、`type.xml`（字体阶梯）、`styles.xml`（形状与组件样式）。布局里不写裸 dp 和十六进制色。

---

## 构建与发版

每次 push 到 `main`，GitHub Actions 会：

1. 用 `1.0.<run_number>` 作为 versionName、`run_number` 作为 versionCode 构建
2. 上传 artifact
3. 建一个 tag `v1.0.<run_number>` 的 Release 并附上 APK

版本号从 Gradle 属性注入（本地不传参时是 `1.0.0-dev`），这样 **APK 里的 versionName 与 Release tag 一致**——应用内检查更新正是拿这两者比对。

### 关于签名

`app/debug.keystore` 是提交进仓库的固定 debug 签名，口令用的是安卓公开约定值（`android` / `androiddebugkey`）。

这么做的原因：CI 每次运行本来会新生成一个 debug 密钥，而签名不同的 APK 无法互相覆盖安装，自动更新会卡在安装那一步。固定下来才能让历次构建互相升级。

> **这不提供任何安全保证**，口令是公开的。仅用于让自己构建的版本可以互相覆盖安装，**不可用于发布到应用商店**。要正式分发请自行生成 release keystore 并改用 GitHub Secrets 注入。

### 更新装不上怎么排查

| 现象 | 原因 |
|---|---|
| 点了「下载并安装」没反应 | 系统要求先允许「安装未知应用」，应用会弹窗引导去设置页开启 |
| 提示签名不一致 / 应用未安装 | 旧版是用别的密钥签的（比如手动构建的），先卸载旧版再装 |
| 一直提示有新版 | 装的是本地构建版（版本号 `1.0.0-dev`），装一次 Release 版即可 |

---

## 数据与隐私

- 角色、对话、记忆、剧情、存档全部存在应用私有目录的 JSON 文件里，不上传任何服务器
- 对话内容直接发往**你自己配置的 API 端点**，本项目不经手、不中转
- 设置里可以整包导出/导入（按 id 合并，保留本地新增）
- 应用带崩溃日志兜底，日志同样只写本地

---

## 已知限制

- 没有端到端 API 联调，需自备真实 Key 验证
- 主动消息只在进入会话时按时间间隔触发，没有后台推送
- 多角色只支持故事内多 speaker + 旁白
- GitHub 未认证 API 限流 60 次/小时（已用本地缓存规避，但首次抓取或频繁刷新仍可能撞上）
- 危机关键词提示条不可关闭，这是有意的
