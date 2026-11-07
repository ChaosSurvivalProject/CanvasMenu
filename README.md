# Canvas Menu · 画布式菜单模组

> 我们做的不是「更好看的背包」，而是 **Minecraft 的画布操作系统** —— 把翻箱子变成逛展，把格子变成卡片，把界面变成一整块会呼吸的画布。

一个面向 **Minecraft 1.21.11 / Fabric / Java 21** 的**纯客户端**模组：把传统格子菜单改造为一体化画布 UI。物品不再是格子里的方块，而是被策展的卡片；界面不是控件拼贴，而是一整块可呼吸的画布。内置 GitHub Release 联网更新检查，以及开发者侧的一键发版工具。

设计信条：**留白优先、层级分明、单一强调色、动效克制、信息如藏品般被陈列。**

---

## 1. 特性

| 模块 | 内容 |
|---|---|
| 画布 Screen | 四层渲染（背景 / 内容 / 交互 / 特效）、淡入淡出 + 0.98→1.0 缩放、Tab 焦点漫游、Esc 逐层返回、窗口缩放自动重排 |
| 渲染图元 | 圆角、四角渐变、描边、柔和阴影、九宫格、强调色发光、**安全模糊（异常自动降级）**，全部自绘，不引第三方 UI 框架 |
| 布局 | 锚点 + 绝对定位 + Flex 流式；Hero / Rail / Cards / Dock / Context 五大区域；间距全部取自 `Theme.gap` |
| 组件 | Button / Slider / Toggle / SearchBox（120ms 防抖）/ TabBar / Rail / **ItemCard** |
| ItemCard | 无格子边框、hover 抬升展开、稀有度靠描边与卡面微染、三类轮廓、右键详情浮层、拖拽对接原版 Slot |
| 主题 | JSON 驱动换肤，缺字段回退默认；内置 Neon Cyber / Genshin Archive / Mono Minimal 三套 |
| 动效 | 虚拟时钟全局 tick 驱动，4 种缓动，单次 ≤ 320ms，尊重 reduce-motion |
| 自动更新 | 虚拟线程异步查 GitHub Release → SemVer 比对 → 下载校验 → **重启替换 + update.lock**，绝不热替换运行中 jar |
| 一键发版 | Gradle `publishRelease`：草稿 Release → 上传 jar + sha256 → 校验 → 发布 |

**只改表现层**：底层仍走原版 `Menu / Slot` 与原版网络包，不破坏生存 / 多人逻辑，不触发反作弊误判。

---

## 2. 技术栈

| 项 | 值 |
|---|---|
| 语言 | Java 21（record / sealed / 虚拟线程） |
| Minecraft | 1.21.11 |
| Loader | Fabric Loader 0.16.14，Fabric API `0.141.6+1.21.11` |
| 构建 | Gradle 8.10.2 + fabric-loom 1.7.4，Java toolchain 21 |
| 运行侧 | `CLIENT` only（`fabric.mod.json` → `environment: "client"`） |
| 依赖 | 仅 Fabric + MC 自带 Gson + JDK 内置 `java.net.http.HttpClient`；**无 OkHttp / Jackson / 重型 UI 库** |

---

## 3. 快速开始

```bash
git clone https://github.com/ChaosSurvivalProject/CanvasMenu.git
cd CanvasMenu
./gradlew build
```

产物位于 `build/libs/canvas-menu-0.1.0.jar`，把它放进 `.minecraft/mods/` 即可（需同时安装 Fabric Loader、Fabric API）。

> 本机只有 JDK 20 也没关系：`settings.gradle` 里的 foojay toolchain resolver 会自动下载 JDK 21。

### 按键与行为

- `E`：默认接管背包，打开画布式背包（可在设置中关闭接管，回退原版）。
- `F6`（默认）：打开模组设置页。
- `Tab` / `Enter` / `Esc`：焦点漫游 / 激活 / 逐层返回。

---

## 4. 目录结构（对应提示词第 9 节 13 步交付）

```
src/main/java/com/chaossurvival/canvasmenu/
├── CanvasMenu.java                 客户端入口 ClientModInitializer
├── config/   ConfigManager · ModConfig                      (步骤 2 / 配置)
├── theme/    Theme · ThemeLoader · Palette                  (步骤 5)
├── gui/
│   ├── screen/   CanvasScreen · CanvasInventoryScreen · SettingsScreen   (步骤 3 / 12)
│   ├── widget/   Widget · AbstractWidget · Button · Slider · Toggle ·
│   │             SearchBox · TabBar · Rail · ItemCard · InventoryBridge  (步骤 6 / 7)
│   ├── layout/   Layout · Anchor · Flex · Absolute · Region              (步骤 8)
│   └── render/   Render2D · BlurLayer · Easing                            (步骤 4)
├── anim/     Animator · Tween · transitions/Transition · TransitionRunner (步骤 9)
├── update/   UpdateChecker · SemVer · Downloader · Updater                (步骤 10)
├── publish/  ReleaseUploader                                            (步骤 11)
└── util/     Async · Log
src/main/resources/
├── fabric.mod.json
└── assets/canvasmenu/
    ├── themes/  default / neon_cyber / genshin_archive / mono_minimal .json
    └── lang/    zh_cn.json · en_us.json
docs/ARCHITECTURE.md   文字版架构与数据流图 + 迭代路线图（步骤 1 / 13）
```

---

## 5. 配置

运行时配置写在 `.minecraft/config/canvasmenu.json`（首次启动自动生成）：

| 字段 | 默认 | 说明 |
|---|---|---|
| `theme` | `neon_cyber` | 主题名，对应 `assets/canvasmenu/themes/*.json` |
| `takeoverInventory` | `true` | 是否接管 E 背包 |
| `takeoverCreative` | `false` | 是否接管创造物品栏 |
| `updateOwner` / `updateRepo` | `ChaosSurvivalProject` / `CanvasMenu` | **只信任该源**，运行时不可改，防投毒 |
| `checkUpdatesOnStartup` | `true` | 启动时异步检查更新 |
| `ignorePrerelease` | `true` | 忽略预发布版本 |
| `highContrast` / `reduceMotion` | `false` | 无障碍 |
| `tooltipTicks` | `40` | tooltip 可读时长 |
| `verifySha256` | `true` | Release 附带 `.sha256` 时强制校验 |

主题 JSON 支持 `#RRGGBB` 与 `#AARRGGBB` 两种色值；缺字段自动回退默认主题。

---

## 6. 自动更新链路

```
fabric.mod.json version
   └─> UpdateChecker (虚拟线程, connect 5s / read 10s)
         GET /repos/{owner}/{repo}/releases/latest
         └─> SemVer 比对 (支持 v 前缀 / pre-release 忽略规则)
               └─> SettingsScreen 顶部提示 + changelog 折叠 + 「下载并重启」
                     └─> Downloader → mods/.canvasmenu-update/xxx.jar
                           (Content-Length 校验 + 可选 sha256)
                           └─> Updater: 写 update.lock → shutdown hook
                                 → 退出时移动新 jar 到 mods/ 删除旧 jar
```

- **绝不热替换运行中的 jar**；网络失败静默降级，只在设置页保留「检查更新」重试，不弹错误打断游戏。
- 更新源由配置固定，运行时不提供改源入口。

---

## 7. 开发者一键发版（令牌安全）

```bash
# 方式 A：环境变量（推荐，令牌不落在命令行历史里）
GH_TOKEN=<TOKEN> ./gradlew publishRelease

# 方式 B：Gradle 属性
./gradlew publishRelease -PgithubToken=<TOKEN> -Pchangelog="修复了卡片布局"
```

`publishRelease` 依赖 `build`，会：

1. 以 `draft=true` 创建 Release（`tag_name=v{version}`，`body`=changelog）；
2. 上传 `canvas-menu-{version}.jar` 与同名 `.sha256`；
3. 全部校验通过后 `PATCH` 将 `draft` 改为 `false` 正式发布。

> **安全约定**：令牌只从 `-PgithubToken` / `GH_TOKEN` / 用户级 `~/.gradle/gradle.properties` 读取。
> 本仓库内 **不含任何令牌、不含默认令牌值**；`.gitignore` 已排除 `.env`、`*.token`、`*.key`、`*.pem`、本地 properties 等。
> 只需 `repo` / `contents:write` 权限，建议使用 Fine-grained PAT 且仅授权本仓库。
> ⚠️ 若曾在对话/命令行中暴露过 PAT，请在 GitHub Settings → Developer settings 中立即吊销并重新签发。

---

## 8. 兼容与稳定性检查清单

- [x] 不重复调用原版 `renderBackground`，背景层完全自绘 → 规避 1.21.11 blur 崩溃
- [x] 窗口缩放触发 `layout()` 重排，不错位
- [x] 纯客户端，不改服务端协议
- [x] 更新下载失败静默降级，不阻塞渲染线程
- [x] 不热替换运行中 jar，采用「下载 + 重启替换 + lock」
- [x] Token 不进仓库、不进日志、不硬编码
- [x] reduce-motion / 高对比 / 纯键盘导航可用
- [x] 物品拖拽仍发送原版点击包（左键拾取 / 拆分、右键单放、Shift 快速移动）

---

## 9. 迭代路线图

| 阶段 | 目标 |
|---|---|
| **MVP（本仓库 0.1.0）** | CanvasScreen + Render2D + ItemCard + 接管背包 E + 三套主题 + 更新检查 |
| V1 | 搜索 / 分类 Rail、拖拽完整对接、创造模式适配、设置页、自动更新下载替换 |
| V1.5 | 主题 JSON 热加载、社区主题导入、reduce-motion / 无障碍完善 |
| V2 | 布局编辑器、`publishRelease` 一键发版、changelog 从 commit 自动生成 |
| V3 | 可选网页式增强层，「游戏内设计工具」级表现 |

详见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

---

## 10. 免责与许可

本模组为**客户端表现层**改造，不修改服务端逻辑；与 Mojang、Microsoft 无官方关联。参考项目仅借鉴组织思路，未复制其代码。

**License: MIT** — 见 [`LICENSE`](LICENSE)。

> 借鉴气质而非抄袭：《原神 / 星铁》的档案感、《死亡搁浅》的留白、《赛博朋克 2077》HUD 的模块化浮层，以及 Paper Panel / Epic / Ubisoft Connect 的单线边框与横向 rail。
