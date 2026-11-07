# 指令贴图资源 · 目录结构与命名规范

所有贴图均为 **64×64 PNG / RGBA / 透明背景**，风格统一为「扁平化 + 微渐变 + 细描边 + 顶部高光」：
圆角方形徽章（圆角 14px）承载白色图形，四角完全透明。生成脚本与 `commands.json` 共用同一份
指令清单，因此文件名与配置永远不会漂移。

## 目录结构

```
src/main/resources/assets/canvasmenu/
├── commands.json                     # 指令注册表（玩家可自行增删改）
└── textures/commands/
    ├── categories/                   # 10 个分类图标（含虚拟「收藏」）
    │   ├── weather.png  time.png  gamemode.png  teleport.png  item.png
    │   ├── mob.png  world.png  player.png  misc.png  favorites.png
    ├── weather/     weather_sun.png  weather_rain.png  weather_thunder.png
    ├── time/        time_day.png  time_sunset.png  time_night.png  time_midnight.png
    ├── gamemode/    gamemode_survival.png ... gamemode_spectator.png
    ├── teleport/    teleport_spawn.png  teleport_sethome.png  teleport_back.png
    ├── item/        item_diamond.png  item_iron_sword.png  item_golden_apple.png  item_ender_pearl.png
    ├── mob/         mob_creeper.png  mob_zombie.png  mob_skeleton.png  mob_ender_dragon.png
    ├── world/       world_no_daylight_cycle.png  world_keep_inventory.png  world_peaceful.png
    ├── player/      player_regeneration.png  player_speed.png  player_levitation.png
    └── misc/        misc_locate_village.png  misc_seed.png  misc_say.png
```

## 命名规范

| 规则 | 说明 |
|---|---|
| 文件名 | `<分类>_<语义>.png`，全小写 + 下划线，例如 `weather_sun.png`、`gamemode_survival.png` |
| 子目录 | 与 `commands.json` 的 `category` 字段一一对应 |
| `icon` 字段 | 相对 `textures/commands/` 的路径，例如 `weather/weather_sun.png` |
| 尺寸 | 必须 64×64；代码按 `textureWidth=64` 绘制，替换贴图时保持该尺寸即可零改代码 |
| 格式 | PNG + Alpha 通道；卡片的圆角、渐变、描边、发光全部由代码绘制，不 baked 进贴图 |

## 占位符兜底

`IconTextures` 通过 `ResourceManager` 异步加载贴图并注册为 `DynamicTexture`；
若贴图缺失、解码失败或目标版本的贴图绘制接口不可用，`IconBlitter` 自动降级为
「主题色圆角底 + `glyph` 图标文字 + 细描边」，**界面永不空白、永不崩溃**。
因此可以直接把任意一张自制 64×64 PNG 覆盖到对应路径完成替换。

## 指令与贴图对照表

| 分类 | 贴图 | 指令 | 说明 |
|---|---|---|---|
| weather | `weather/weather_sun.png` | `/weather clear` | 变成晴天 |
| weather | `weather/weather_rain.png` | `/weather rain` | 变成雨天 |
| weather | `weather/weather_thunder.png` | `/weather thunder` | 变成雷暴天 |
| time | `time/time_day.png` | `/time set day` | 设为白天 |
| time | `time/time_sunset.png` | `/time set sunset` | 设为黄昏 |
| time | `time/time_night.png` | `/time set night` | 设为夜晚 |
| time | `time/time_midnight.png` | `/time set midnight` | 设为午夜 |
| gamemode | `gamemode/gamemode_survival.png` | `/gamemode survival` | 切换生存模式 |
| gamemode | `gamemode/gamemode_creative.png` | `/gamemode creative` | 切换创造模式 |
| gamemode | `gamemode/gamemode_adventure.png` | `/gamemode adventure` | 切换冒险模式 |
| gamemode | `gamemode/gamemode_spectator.png` | `/gamemode spectator` | 切换旁观模式 |
| teleport | `teleport/teleport_spawn.png` | `/spawn` | 传送到出生点 |
| teleport | `teleport/teleport_sethome.png` | `/sethome` | 设置家的位置 |
| teleport | `teleport/teleport_back.png` | `/back` | 返回死亡点 |
| item | `item/item_diamond.png` | `/give @p diamond 64` | 获得 64 个钻石 |
| item | `item/item_iron_sword.png` | `/give @p iron_sword 1` | 获得一把铁剑 |
| item | `item/item_golden_apple.png` | `/give @p golden_apple 16` | 获得 16 个金苹果 |
| item | `item/item_ender_pearl.png` | `/give @p ender_pearl 16` | 获得 16 个末影珍珠 |
| mob | `mob/mob_creeper.png` | `/summon creeper` | 召唤苦力怕 |
| mob | `mob/mob_zombie.png` | `/summon zombie` | 召唤僵尸 |
| mob | `mob/mob_skeleton.png` | `/summon skeleton` | 召唤骷髅 |
| mob | `mob/mob_ender_dragon.png` | `/summon ender_dragon` | 召唤末影龙 |
| world | `world/world_no_daylight_cycle.png` | `/gamerule doDaylightCycle false` | 停止昼夜循环 |
| world | `world/world_keep_inventory.png` | `/gamerule keepInventory true` | 死亡不掉落物品 |
| world | `world/world_peaceful.png` | `/difficulty peaceful` | 切换和平难度 |
| player | `player/player_regeneration.png` | `/effect give @p regeneration 30 2` | 生命恢复 30 秒 |
| player | `player/player_speed.png` | `/effect give @p speed 60 1` | 速度提升 60 秒 |
| player | `player/player_levitation.png` | `/effect give @p levitation 10 1` | 漂浮 10 秒 |
| misc | `misc/misc_locate_village.png` | `/locate structure village` | 定位村庄 |
| misc | `misc/misc_seed.png` | `/seed` | 查看世界种子 |
| misc | `misc/misc_say.png` | `/say 你好，世界！` | 发送一条聊天消息 |

共 31 条指令、41 张贴图（含分类图标）。
