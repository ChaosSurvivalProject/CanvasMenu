# Canvas Menu · 画布式菜单模组 + 指令中心

> 我们做的不是「更好看的背包」，而是 **Minecraft 的画布操作系统** —— 把翻箱子变成逛展，把格子变成卡片，把界面变成一整块会呼吸的画布。

一个面向 **Minecraft 1.21.11 / Fabric / Java 21** 的**纯客户端**模组：把传统格子菜单改造为一体化画布 UI，并内置一个 **指令中心（Command Center）** —— 9 大分类、31 条内置指令、41 张统一风格贴图卡片，点击即可把指令填入聊天栏。另含 GitHub Release 自动更新全链路。

设计信条：**留白优先、层级分明、单一强调色、动效克制、信息如藏品般被陈列。**

---

## 0. 截图

> 本仓库不提交二进制截图（保持仓库轻量）。请在这里放入你的实机截图：

| 画面 | 截图占位 |
|---|---|
| 画布式背包 | `docs/screenshots/inventory.png` *(待补)* |
| 指令中心 · 全部 | `docs/screenshots/command-center.png` *(待补)* |
| 指令中心 · 天气分类 + Tooltip | `docs/screenshots/command-tooltip.png` *(待补)* |
| 设置页 · 发现新版本 | `docs/screenshots/update.png` *(待补)* |

贴图本身的预览见 [`docs/TEXTURES.md`](docs/TEXTURES.md)。

---

## 1. 特性

### 画布底座（`com.chaossurvival.canvasmenu.*`）

| 模块 | 内容 |
|---|---|
| 画布 Screen | 四层渲染（背景 / 内容 / 交互 / 特效）、淡入淡出 + 0.98→1.0 缩放、Tab 焦点漫游、Esc 逐层返回、窗口缩放自动重排 |
| 渲染图元 | 圆角、四角渐变、描边、柔和阴影、九宫格、强调色发光、**安全模糊（异常自动降级）**，全部自绘 |
| 兼容层 | `GuiCompat`：1.21.8+ 把 `pose()` 换成 `Matrix3x2fStack`、`drawString` 改 6 参 ARGB，本层反射探测 + 安全降级，跨版本不崩溃 |
| 布局 | 锚点 + 绝对定位 + Flex 流式；Hero / Rail / Cards / Dock / Context 五大区域 |
| 组件 | Button / Slider / Toggle / SearchBox（120ms 防抖）/ TabBar / Rail / ItemCard |

### 指令中心（`com.canvasmenu.*`，本次新增）

| 模块 | 内容 |
|---|---|
| 入口 | 画布主菜单新增「指令中心」按钮；或按 `F7` 直接打开全屏画布面板 |
| 布局 | 左侧分类 Rail（含「全部 / 收藏」虚拟分类）+ 右侧贴图卡片**流式网格**，支持滚轮翻页与视口裁剪 |
| 指令数据 | **`assets/canvasmenu/commands.json` 驱动**：31 条指令 / 9 个真实分类，玩家可在 `config/canvasmenu-commands.json` 覆盖增删改 |
| 贴图 | **41 张真实 64×64 RGBA PNG**，扁平化 + 微渐变 + 细描边，按分类分子目录；`IconTextures` 经 ResourceManager **虚拟线程异步加载** |
| 占位符兜底 | 贴图缺失 / 解码失败 / 未加载完 → 自动降级为「主色圆角方牌 + 图标文字」，界面永不空白；替换真实 PNG 后代码零改动 |
| 卡片 | 九宫格圆角面板、四角微渐变、悬停连续抬升（≤3px）+ 强调色发光描边、收藏金色角标 |
| 交互 | **左键**打开聊天栏并填入指令（**不自动发送**，回车确认）· **右键**加入/移出收藏 · **悬停**显示指令 + 说明 Tooltip · **搜索框**实时过滤 · Enter/Space 键盘激活 |
| 主题 | 三套预设：**暗夜靛蓝 / 雾灰极简 / 霓虹赛博**，设置页与面板内均可轮换 |

**只改表现层**：底层仍走原版 `Menu / Slot` 与原版网络包，不破坏生存 / 多人逻辑，不触发反作弊误判。

---

## 2. 技术栈

| 项 | 值 |
|---|---|
| 语言 | Java 21（record / 虚拟线程 / `java.net.http.HttpClient`） |
| Minecraft | 1.21.11（Mojang 官方映射 `loom.officialMojangMappings()`） |
| Loader | Fabric Loader 0.16.14，Fabric API `0.141.6+1.21.11` |
| 构建 | Gradle **9.5.0** + **fabric-loom 1.17.21**，Java toolchain 21 |
| 运行侧 | `CLIENT` only（`fabric.mod.json` → `environment: "client"`） |
| 依赖 | 仅 Fabric + MC 自带 Gson + JDK 内置 HttpClient；**无 OkHttp / Jackson / 重型 UI 库** |

> 版本组合已对上游仓库核实：1.21.11 需要 Java 21 且带 `client_mappings`；
> `fabric-loom 1.17.21` 由 Gradle 9.5.0 构建、字节码目标 Java 21，是本组合中兼容 JDK 21 的最新稳定版
> （1.18.x / 1.19.x 已改为 Java 25 字节码，无法在 JDK 21 上运行）。Gradle Wrapper 同步为 9.5.0。

---

## 3. 快速开始

```bash
git clone https://github.com/ChaosSurvivalProject/CanvasMenu.git
cd CanvasMenu
./gradlew build
```

产物位于 `build/libs/canvas-menu-0.2.0.jar`，把它放进 `.minecraft/mods/` 即可（需同时安装 Fabric Loader 与 Fabric API）。

> 本机只有 JDK 20 也没关系：`settings.gradle` 的 foojay toolchain resolver 会自动下载 JDK 21。

### 按键与行为

| 按键 | 行为 |
|---|---|
| `E` | 默认接管背包，打开画布式背包（可在设置中关闭接管） |
| `F6` | 打开模组设置页 |
| `F7` | **打开指令中心** |
| `Tab` / `Enter` / `Esc` | 焦点漫游 / 激活 / 逐层返回 |
| 指令中心内 左键 / 右键 / 滚轮 | 填入聊天栏 / 收藏 / 翻页 |

### 指令中心使用教程

1. 进入世界后按 `F7`，或打开背包点击底部「**指令中心**」按钮。
2. 左侧 Rail 选择分类（天气 / 时间 / 游戏模式 / 传送 / 物品 / 生物 / 世界 / 玩家 / 杂项），或选择「全部」「收藏」。
3. 右侧网格浏览贴图卡片；鼠标悬停可看到**指令原文 + 说明 + 操作提示**浮层。
4. **左键点击**卡片 → 自动打开聊天栏并填入指令，**光标停在末尾，不会自动发送**；确认或修改后按回车执行，按 Esc 取消。
5. **右键点击**卡片 → 加入 / 移出「收藏」，收藏持久化在 `config/canvasmenu-favorites.json`。
6. 顶部搜索框输入中文或英文关键词（例如「天气」「钻石」「give」「time」）实时过滤。
7. 底部「重载配置」可在不重启游戏的情况下重新读取 `commands.json` 与贴图。

---

## 4. 目录结构

```
src/main/java/
├── com/chaossurvival/canvasmenu/          画布底座（0.1.0 起）
│   ├── CanvasMenu.java                    入口：配置 / F6 / E 接管
│   ├── config/  theme/  anim/  util/
│   └── gui/
│       ├── screen/   CanvasScreen · CanvasInventoryScreen · SettingsScreen
│       ├── widget/   Widget · AbstractWidget · Button · ... · ItemCard
│       ├── layout/   Region · Anchor · Flex · Absolute
│       └── render/   Render2D · BlurLayer · Easing · GuiCompat ★
├── com/canvasmenu/                        指令中心模块（0.2.0 新增）
│   ├── CommandCenter.java                 第二个 client entrypoint
│   ├── command/   CommandEntry · CommandCategory · CommandRegistry
│   │              · Favorites · ChatBridge
│   ├── render/    IconTextures · IconBlitter · CardPaint
│   ├── widget/    CommandCardWidget · CommandRail · CommandTooltip
│   │              · CommandGridLayout
│   ├── screen/    CommandPanelScreen
│   ├── theme/     ThemePreset（三套预设）
│   └── update/    AutoUpdater（完整更新链路）
src/main/resources/
├── fabric.mod.json
└── assets/canvasmenu/
    ├── commands.json                      ★ 指令注册表（31 条）
    ├── textures/commands/                 ★ 41 张 64×64 PNG
    │   ├── categories/  weather/  time/  gamemode/  teleport/  item/
    │   └── mob/  world/  player/  misc/
    ├── themes/    night_indigo / mist_gray / neon_cyber / ... .json
    └── lang/      zh_cn.json · en_us.json
.github/workflows/  build.yml（CI 构建）· release.yml（发布 Release）
docs/  ARCHITECTURE.md · TEXTURES.md
```

> 两个包的关系：`com.canvasmenu.*` 是新增功能模块，**复用** `com.chaossurvival.canvasmenu.*` 的
> `CanvasScreen` / `Render2D` / `Theme` / `Async` 等基础设施，不重复造轮子。
> 直接把整个仓库导入 IDE 即可编译；完整代码均在上述文件中。

---

## 5. 指令配置（JSON 驱动）

`assets/canvasmenu/commands.json` 片段：

```json
{
  "schemaVersion": 1,
  "categories": [
    { "id": "weather", "name": "天气", "glyph": "云", "tint": "#4A90D9",
      "icon": "categories/weather.png", "virtual": false }
  ],
  "commands": [
    {
      "id": "weather_clear",
      "category": "weather",
      "icon": "weather/weather_sun.png",
      "label": "晴天",
      "description": "变成晴天",
      "command": "/weather clear",
      "keywords": ["晴", "太阳", "sun", "clear", "天气"],
      "tint": "#F2B33D",
      "glyph": "晴"
    }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `id` | 唯一标识；收藏以此为准 |
| `category` | 分类 id，也是贴图子目录名 |
| `icon` | 相对 `textures/commands/` 的贴图路径 |
| `label` / `description` | 卡片标题 / Tooltip 说明 |
| `command` | 点击后填入聊天栏的完整指令 |
| `keywords` | 搜索关键词（中文 + 英文） |
| `tint` / `glyph` | 占位符主色 / 图标文字 |

**玩家自定义**：把 `commands.json` 复制到 `.minecraft/config/canvasmenu-commands.json` 并编辑即可覆盖出厂配置；缺字段自动回退、坏条目跳过、重复 id 只保留第一条，改坏了也不会让游戏崩溃。面板底部「重载配置」即时生效。

贴图目录结构与命名规范详见 [`docs/TEXTURES.md`](docs/TEXTURES.md)。

---

## 6. 主题

| id | 名称 | 气质 |
|---|---|---|
| `night_indigo` | 暗夜靛蓝 | 深靛底 + 冷蓝强调 |
| `mist_gray` | 雾灰极简 | 中性灰 + 大留白 + 小圆角 |
| `neon_cyber` | 霓虹赛博 | 墨黑底 + 青霓虹发光 |

主题 JSON 位于 `assets/canvasmenu/themes/*.json`，支持 `#RRGGBB` 与 `#AARRGGBB`，缺字段自动回退。设置页「主题」按钮或指令中心底部「主题」按钮轮换。

---

## 7. GitHub 自动更新

```
fabric.mod.json version
   └─> AutoUpdater.checkOnStartup()            [虚拟线程]
         GET /repos/ChaosSurvivalProject/CanvasMenu/releases/latest
         └─> SemVer 比对（支持 v 前缀，忽略预发布）
               └─> 设置页提示 + changelog 折叠 + 「下载并重启」
                     └─> downloadAndSchedule()  [虚拟线程]
                           可选 SHA-256 校验 → .part → 原子 move
                           → mods/.canvasmenu-update/canvas-menu-x.y.z.jar
                           → 写 mods/update.lock（CREATE_NEW 原子，防重复）
                                 └─> CLIENT_STOPPING / JVM shutdown
                                       → 移动新 jar 到 mods/、删旧 jar、删 lock
                                       → 下次启动加载新版本
```

| 约束 | 实现 |
|---|---|
| 不阻塞主线程 | 检查与下载全部经 `Async`（`newVirtualThreadPerTaskExecutor`），结果只写 `volatile` 字段，渲染线程只读 |
| 失败静默降级 | 网络错误 / HTTP 非 200 / JSON 坏数据 / 校验失败一律返回空或把状态置 `FAILED` 且**清空提示文案**，不弹错误；设置页保留重试入口 |
| 防重复下载 | `update.lock` 用 `CREATE_NEW` 原子创建，已存在直接跳过 |
| **绝不热替换运行中的 jar** | 更新器**不触碰 ClassLoader、不重新加载类、不在游戏运行期间改写当前被加载的 jar**；只在退出阶段做纯文件层「移动 + 删除」，新 jar 下次启动才被读取 |
| 防投毒 | 更新源固定为出厂常量 `ChaosSurvivalProject/CanvasMenu`，运行时不提供改源入口；检查与下载均为匿名只读请求，**不需要任何令牌** |

完整实现见 [`src/main/java/com/canvasmenu/update/AutoUpdater.java`](src/main/java/com/canvasmenu/update/AutoUpdater.java)。

---

## 8. 构建产物与发布 Release

### 8.1 本地生成 jar

```bash
./gradlew clean build
ls -lh build/libs/
```

| 文件 | 用途 |
|---|---|
| `canvas-menu-0.2.0.jar` | **可安装**的 Fabric 模组（经 `remapJar` 处理） |
| `canvas-menu-0.2.0-sources.jar` | 源码 jar（`withSourcesJar()`），不放入 mods |

把主 jar 放进 `.minecraft/mods/` 即可运行。

### 8.2 方式 A：CI 自动发布（推荐）

推送 `v*` tag 即触发 [`.github/workflows/release.yml`](.github/workflows/release.yml)：

```bash
git tag v0.2.0
git push origin v0.2.0
```

工作流会：使用 Temurin JDK 21 真实执行 `./gradlew build` → 挑出可安装 jar → 生成同名 `.sha256` → 用 `gh release create` 创建 Release 并上传**两个资产** + 自动生成 release notes。也可在 Actions 页面手动 `workflow_dispatch` 并填写 tag。

每次 push / PR 另有 [`build.yml`](.github/workflows/build.yml) 做编译校验并上传 jar artifact。

### 8.3 方式 B：本地一键发版

```bash
# 令牌只在运行期注入，绝不写进仓库
GH_TOKEN=<TOKEN> ./gradlew publishRelease
# 或
./gradlew publishRelease -PgithubToken=<TOKEN> -Pchangelog="新增指令中心"
```

`publishRelease` 依赖 `build`：以 `draft=true` 创建 Release → 上传 jar 与 `.sha256` → 校验通过后 `PATCH` 为正式发布。

### 8.4 方式 C：完全手动

```bash
sha256sum build/libs/canvas-menu-0.2.0.jar > build/libs/canvas-menu-0.2.0.jar.sha256
gh release create v0.2.0 \
  build/libs/canvas-menu-0.2.0.jar \
  build/libs/canvas-menu-0.2.0.jar.sha256 \
  --title "Canvas Menu v0.2.0" --generate-notes
```

> **自动更新器如何消费**：它读取 `/releases/latest`，挑出 `.jar`（排除 `sources` / `javadoc` / `-dev`）与可选 `.sha256`，因此 Release **必须带一个可安装 jar**；带 `.sha256` 时会自动做完整性校验。

> **令牌安全**：令牌只从 `-PgithubToken` / `GH_TOKEN` / 用户级 `~/.gradle/gradle.properties` 读取。
> 仓库内**不含任何令牌、不含默认令牌值**；`.gitignore` 已排除 `.env`、`*.token`、`*.key`、`*.pem` 等。
> 只需 `repo` / `contents:write` 权限，建议 Fine-grained PAT 且仅授权本仓库。
> ⚠️ 若曾在对话 / 命令行中暴露过 PAT，请立即在 GitHub Settings → Developer settings 中吊销并重新签发。

---

## 9. 兼容与稳定性检查清单

- [x] 不重复调用原版 `renderBackground`，背景层完全自绘 → 规避 1.21.11 blur 崩溃
- [x] `GuiCompat` 适配 1.21.8+ `Matrix3x2fStack` 与 6 参 ARGB `drawString`，探测失败安全降级
- [x] 窗口缩放触发 `layout()` 重排；卡片区 scissor 裁剪，滚动不越界
- [x] 过滤重排放在 `tick()` 中父级遍历之后，避免并发修改异常
- [x] 贴图虚拟线程异步加载，主线程不等待；缺图自动占位符兜底
- [x] 纯客户端，不改服务端协议；指令填入聊天栏由玩家确认，不自动发送
- [x] 更新下载失败静默降级，不阻塞渲染线程
- [x] 不热替换运行中 jar，采用「下载 + 退出替换 + lock」
- [x] Token 不进仓库、不进日志、不硬编码
- [x] reduce-motion / 高对比 / 纯键盘导航可用

---

## 10. 许可

本模组为**客户端表现层**改造，不修改服务端逻辑；与 Mojang、Microsoft 无官方关联。

**License: MIT** — 见 [`LICENSE`](LICENSE)。
