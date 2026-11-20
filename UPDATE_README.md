# CanvasMenu 内置自动更新模块 · 改动说明

> 依据 `update-gate/skills/mc-autoupdate` 的 Fabric 客户端方案实现。**本次按决策覆盖替换原有 AutoUpdater 更新链路。**

## 一、做了什么

用 mc-autoupdate 方案替换 CanvasMenu 原有的 `com.canvasmenu.update.AutoUpdater` 链路：启动自检 + 每周四 12:00 检查，固定 manifest 直连、sha256 校验，下载到 `mods/.update/` 后提示玩家重启生效，跨版本只提示不自动下载。

## 二、改动文件清单

| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `src/main/java/com/chaossurvival/canvasmenu/autoupdate/ClientUpdateChecker.java` | 新增 | 新客户端检查器（替换旧 AutoUpdater） |
| `src/main/java/com/chaossurvival/canvasmenu/autoupdate/Json.java` | 新增 | 零依赖 JSON 解析器 |
| `src/main/java/com/canvasmenu/CommandCenter.java` | 修改 | 移除旧 `AutoUpdater.checkOnStartup()` 与退出 swap hook，改启动新检查器（try-catch 隔离） |
| `updates/latest.json` | 新增 | 更新清单（stable，指向当前 v0.2.0 Release 的 fabric jar，真实 sha256） |
| `gradle.properties` | 修改 | mod_version 0.2.0 → 0.2.1（patch +1） |

> 旧 `update/` 包文件予以保留（避免其它潜在引用编译失败），但其自动检查入口已在 CommandCenter 停用，更新链路实际由新模块接管。若需彻底删除旧包，可在本机确认无引用后移除。

## 三、安全红线核对

- [x] 启动自检 + 每周四 12:00（Asia/Shanghai）
- [x] 跨版本只提示、不自动下载；sha256 校验失败删除
- [x] 下载到 `.part` 后原子移动；不静默替换运行中的 jar
- [x] 无 Token、只走 HTTPS、文案中英双语、注释简体中文
- [x] 新类用 javac + `-Xlint:all` 编译零警告
- [ ] **构建验证**：需你本机 `./gradlew build` 确认（覆盖替换涉及入口改动，尤其建议验证）

## 四、版本号说明

本地 modVersion patch +1 到 0.2.1；manifest target 指向已发布的最新包 0.2.0。当前本地 ≥ 目标不会触发更新（正确）。未来发版时同步升级即可生效。
