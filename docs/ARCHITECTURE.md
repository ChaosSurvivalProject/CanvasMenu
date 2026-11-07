# Canvas Menu · 架构与数据流

> 对应 Master Prompt 第 9 节第 1 项（架构与数据流图）与第 13 项（迭代路线图）。

## 1. 分层总览

```
┌───────────────────────────────────────────────────────────────┐
│  Minecraft Client (1.21.11) / Fabric Loader 0.16.x             │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  CanvasMenu (ClientModInitializer)                       │  │
│  │   · ConfigManager / ThemeLoader / Animator 初始化         │  │
│  │   · KeyBinding (F6 设置) + ClientTickEvents (E 接管)       │  │
│  │   · 启动时异步 UpdateChecker + shutdown hook               │  │
│  └───────────────┬─────────────────────────────────────────┘  │
│                  │ setScreen(...)                              │
│  ┌───────────────▼─────────────────────────────────────────┐  │
│  │  CanvasScreen (extends Screen)                           │  │
│  │   ┌───────────┬───────────┬───────────┬─────────────┐  │  │
│  │   │ 背景层 BG │ 内容层    │ 交互层    │ 特效层 FX   │  │  │
│  │   │ 渐变/模糊 │ 卡片/文本 │ hover/焦点│ 发光/动效   │  │  │
│  │   └───────────┴───────────┴───────────┴─────────────┘  │  │
│  │   每层独立 GuiGraphics.pose() 变换；统一 alpha/scale      │  │
│  └───────┬──────────────────┬──────────────────┬───────────┘  │
│          │                  │                  │               │
│   ┌──────▼──────┐    ┌──────▼──────┐   ┌──────▼───────────┐   │
│   │ layout/     │    │ widget/     │   │ render/           │   │
│   │ Anchor      │    │ Widget      │   │ Render2D          │   │
│   │ Absolute    │───▶│ ItemCard    │◀──│ BlurLayer         │   │
│   │ Flex        │    │ Button ...  │   │ Easing            │   │
│   │ Region      │    └──────┬──────┘   └───────────────────┘   │
│   └─────────────┘           │                                  │
│                    ┌────────▼────────┐                          │
│                    │ InventoryBridge │ ── 反射转发原版点击包 ──┼──▶ Server
│                    │ (Menu / Slot)   │    (不改协议)             │
│                    └─────────────────┘                          │
└───────────────────────────────────────────────────────────────┘
```

## 2. 数据流

### 2.1 渲染帧（每 tick / 每 frame）

```
Minecraft#tick ─▶ CanvasScreen#tick
                    ├─ Animator.tick(deltaMs) ─▶ 所有 Tween.update(now)
                    └─ widgets.forEach(Widget::tick)   (SearchBox 防抖计时等)

Minecraft#render ─▶ CanvasScreen#render(guiGraphics, mouseX, mouseY, delta)
                    ├─ pose.pushPose() → translate(center) → scale(0.98+0.02*t) → translate(-center)
                    ├─ renderBackgroundLayer()  自绘渐变 + BlurLayer.safeBlur（异常降级为半透明纯色）
                    ├─ renderContentLayer()     Region 布局 → widgets.render(g,mx,my,delta)
                    │                            └─ ItemCard: Render2D.roundRect / nineSlice / renderItem
                    ├─ renderInteractionLayer() hover 光晕、焦点描边、tooltip
                    ├─ renderEffectLayer()      glowRect、进入/退出淡入淡出
                    └─ pose.popPose()
```

> **关键**：全程不调用原版 `Screen#renderBackground` 的重复 blur 路径，避免 1.21.11 上的已知 blur 崩溃。

### 2.2 输入流

```
GLFW 输入 ─▶ Screen#mouseClicked / mouseDragged / mouseScrolled / keyPressed / charTyped
              ├─ CanvasScreen 焦点环：Tab 切焦点、Enter 激活、Esc → onBack()（逐层返回）
              ├─ 命中测试：倒序遍历 widgets，contains(x,y) 命中后消费事件
              ├─ ItemCard 拖拽 ─▶ InventoryBridge.vanillaClick(menuId, slotIndex, button, clickType, player)
              │                    └─ 反射调用 MultiPlayerGameMode，最终发送原版 ServerboundContainerClickPacket
              └─ SearchBox#charTyped ─▶ 120ms 防抖 ─▶ onQuery(String) ─▶ 过滤卡片 ─▶ layout()
```

### 2.3 主题流

```
assets/canvasmenu/themes/*.json
   └─ ThemeLoader.parse(json, Gson)
        ├─ 缺字段 → Theme.defaults() 对应值
        ├─ "#RRGGBB" / "#AARRGGBB" → int ARGB
        └─ Theme (record, 不可变) ─▶ Render2D / Layout / Widget 全部取值
Palette.byName(name) 提供三套内置预设，配置里的 theme 字段决定加载哪一个。
```

### 2.4 自动更新流（虚拟线程）

```
CanvasMenu#onInitializeClient
   └─ Async.run(() -> UpdateChecker.check(config))
        ├─ HttpClient (connect 5s / read 10s, User-Agent: CanvasMenu-Updater)
        ├─ GET /repos/{owner}/{repo}/releases/latest   ← owner/repo 由配置固定
        ├─ 解析 tag_name / assets[] / body / published_at → ReleaseInfo
        └─ SemVer.compare(local, remote) ─▶ Optional<ReleaseInfo>
              └─ SettingsScreen 显示提示 + changelog 折叠 + 「下载并重启」
                    └─ Downloader.download(url, dest, progress, sha256?)
                          ├─ 校验 Content-Length，可选 SHA-256
                          └─ Updater.scheduleSwap(newJar)
                                ├─ 写 mods/update.lock（防重复）
                                ├─ ClientLifecycleEvent.CLIENT_STOPPING + JVM shutdown hook
                                └─ 退出后：移动新 jar 到 mods/、删除旧 jar 与 lock
                                   （绝不热替换运行中 jar）
```

失败路径：任何网络/解析异常 → 记 debug 日志并返回 `Optional.empty()`，**不抛异常、不弹窗、不阻塞渲染线程**；设置页保留手动「检查更新」。

### 2.5 发版流（开发者本地，非游戏运行时）

```
./gradlew publishRelease  (GH_TOKEN / -PgithubToken)
   └─ build ─▶ remapJar ─▶ build/libs/canvas-menu-{version}.jar
        └─ ReleaseUploader.main(owner, repo, token, tag, changelog, jarPath)
             ├─ POST   /repos/{o}/{r}/releases            {draft:true}
             ├─ POST   {upload_url}?name=...jar           application/java-archive
             ├─ POST   {upload_url}?name=...jar.sha256    text/plain
             └─ PATCH  /repos/{o}/{r}/releases/{id}       {draft:false}
```

令牌只经由参数/环境变量注入，**代码中无默认值，仓库中无凭证**。

## 3. 关键设计决策

| 决策 | 理由 |
|---|---|
| 核心 UI 全部自研，不引入 UI 框架 | 避免重型依赖拖累启动与版本兼容；提示词明确要求 |
| 所有图元基于 `GuiGraphics.fill` 与扫描线，不依赖 `blit` / `RenderPipeline` 具体签名 | 1.21.x 渲染 API 变动频繁，纯填充实现跨小版本最稳 |
| 模糊走反射 + 异常降级 | 「安全调用，避免 blur 崩溃」；任何异常都退化为半透明纯色 |
| 原版点击走反射隔离层 `InventoryBridge` | 隔离易变的 `gameMode` 方法签名，同时保证发送的是原版网络包 |
| 更新源固定在配置，运行时不可改 | 防投毒 |
| 不热替换 jar，用 lock + 退出时替换 | 避免类加载器冲突与存档损坏 |
| 动效统一由 `Animator` 虚拟时钟推进 | 可在 reduce-motion 下整体降为 0ms，也便于暂停时冻结 |
| Token 只在构建期注入 | 满足「不进仓库、不进日志、不硬编码」 |

## 4. 迭代路线图

| 阶段 | 范围 | 交付 |
|---|---|---|
| **MVP（0.1.0，本仓库）** | 先跑起来 | CanvasScreen + Render2D + ItemCard + 接管背包 E + 三套主题 + 更新检查 |
| **V1** | 完整交互 | 搜索 / 分类 Rail、拖拽完整对接、创造模式适配、设置页、自动更新下载替换 |
| **V1.5** | 换肤与无障碍 | 主题 JSON 热加载、社区主题导入、reduce-motion / 高对比 / 键盘导航完善 |
| **V2** | 创作工具 | 布局编辑器（玩家自定义区域）、`publishRelease` 一键发版、changelog 从 commit 生成 |
| **V3（可选）** | 表现力增强 | 网页式增强层，「游戏内设计工具」级表现（重，谨慎评估） |

### 参考资料（学思路，不照抄）

| 类别 | 项目 | 学什么 |
|---|---|---|
| 轻量 UI | LowFrame Screen API | Screen / Widget 精简封装 |
| 样式库 | faleUI (1.21.11) | 自绘纹理、样式系统 |
| 组件集 | MenuKit | 面板 / 标签 / 滚动容器组织 |
| Builder UI | Super GUI Lib (SGL) | 声明式组件构建 |
| 网页式 UI | Graphene UI | 后期若要网页级表现力（重，谨慎） |
| 渲染层 | Lumaris | 图层 / canvas 渲染思路 |
| 官方 | Fabric Custom Screens 文档 | GuiGraphics / RenderState 正确用法 |
