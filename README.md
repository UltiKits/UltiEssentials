# UltiEssentials

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.2.1-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![Paper](https://img.shields.io/badge/Paper-1.19--1.21-green)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-8+-orange)](https://www.java.com/)

Runs on **Paper 1.19 or newer** only, like the UltiTools framework it is built on (the framework declares Bukkit `api-version: 1.19` and uses Paper's Adventure API throughout). Spigot and servers older than 1.19 are not supported.

仅支持 **Paper 1.19 及以上**，与其所依赖的 UltiTools 框架一致（框架声明 Bukkit `api-version: 1.19`，并全面使用 Paper 的 Adventure API）。不支持 Spigot，也不支持 1.19 以前的服务端。

UltiEssentials 是基于 UltiTools-API 框架开发的服务器基础功能插件模块，提供了常用的传送、玩家状态、管理员工具和服务器自定义功能。

## ✨ 功能特性

### 🚀 传送功能

| 命令 | 描述 | 权限 |
|------|------|------|
| `/back` | 返回上一个传送位置 | `ultiessentials.back` |
| `/spawn` | 传送到服务器出生点 | `ultiessentials.spawn.teleport` |
| `/setspawn` | 设置服务器出生点 | `ultiessentials.spawn.set` |
| `/lobby` `/hub` | 传送到主城 | `ultiessentials.lobby.teleport` |
| `/setlobby` `/sethub` | 设置主城位置 | `ultiessentials.lobby.set` |
| `/wild` `/rtp` | 随机传送到野外 | `ultiessentials.wild` |

### 💪 玩家状态
| 命令 | 描述 | 权限 |
|------|------|------|
| `/fly [玩家]` | 切换飞行模式 | `ultiessentials.fly` (自己) / `ultiessentials.fly.other` (其他玩家) |
| `/heal [玩家]` | 恢复生命值 | `ultiessentials.heal.self` / `ultiessentials.heal.other` |
| `/feed [玩家]` | 恢复饱食度 | `ultiessentials.heal.self` / `ultiessentials.heal.other` |
| `/speed <速度>` | 调整移动速度 (0-10) | `ultiessentials.speed` |
| `/speed reset` | 重置移动速度 | `ultiessentials.speed` |
| `/gm <模式> [玩家]` | 切换游戏模式 | `ultiessentials.gamemode.self` / `ultiessentials.gamemode.other` |
| `/gms` | 快捷切换到生存模式 | `ultiessentials.gamemode.self` |
| `/gmc` | 快捷切换到创造模式 | `ultiessentials.gamemode.self` |
| `/gmsp` | 快捷切换到旁观模式 | `ultiessentials.gamemode.self` |
| `/hide` `/vanish` | 切换隐身模式 | `ultiessentials.hide` |

**游戏模式参数:**
- `0` / `s` / `survival` - 生存模式
- `1` / `c` / `creative` - 创造模式
- `2` / `a` / `adventure` - 冒险模式
- `3` / `sp` / `spectator` - 旁观模式

### 🔧 管理员工具

| 命令 | 描述 | 权限 |
|------|------|------|
| `/invsee <玩家>` | 查看玩家背包 | `ultiessentials.invsee` |
| `/endersee <玩家>` `/echest <玩家>` | 查看玩家末影箱 | `ultiessentials.endersee` |
| `/armorsee <玩家>` | 查看玩家装备 | `ultiessentials.armorsee` |
| `/wl add <玩家>` | 添加玩家到白名单 | `ultiessentials.whitelist.manage` |
| `/wl remove <玩家>` | 从白名单移除玩家 | `ultiessentials.whitelist.manage` |
| `/wl list` | 查看白名单列表 | `ultiessentials.whitelist.manage` |
| `/wl on` | 启用白名单 | `ultiessentials.whitelist.manage` |
| `/wl off` | 禁用白名单 | `ultiessentials.whitelist.manage` |
| `/wl status` | 查看白名单状态 | `ultiessentials.whitelist.manage` |

### Commands that take over vanilla commands / 接管原版命令的命令

`/ban`, `/banlist` and `/scoreboard` deliberately take over the vanilla commands of the same name: `/ban` and `/banlist` work on this module's own ban records, not on the server's ban list, and `/scoreboard` toggles this module's sidebar. The vanilla commands stay reachable as `minecraft:ban`, `minecraft:banlist` and `minecraft:scoreboard`.

`/ban`、`/banlist`、`/scoreboard` 有意接管了同名的原版命令：`/ban` 与 `/banlist` 操作本模块自己的封禁记录，而不是服务器的封禁名单；`/scoreboard` 用于开关本模块的侧边栏。原版命令仍可通过 `minecraft:ban`、`minecraft:banlist`、`minecraft:scoreboard` 调用。

### The sidebar's scoreboard / 侧边栏计分板

The sidebar is drawn on a private scoreboard this module assigns to each player who has it on. That scoreboard belongs to this module alone: another plugin must not write onto it (register its own objective named `ultiessentials`, or put a player into its own team on it). Such writes are not supported and may be overwritten. The module copies the main scoreboard's teams onto it, so name prefixes stay visible, and restores its own objective, sidebar slot and lines if another plugin changes them. If another plugin's scoreboard is already on screen, this sidebar waits until the player is back on the main scoreboard.

侧边栏画在本模块为每位开启侧边栏的玩家换上的私有计分板上。这块计分板只归本模块所有：其他插件不应往上面写东西（注册名为 `ultiessentials` 的目标，或把玩家放进自己在这块计分板上的队伍），这类写入不受支持，可能被覆盖。本模块会把主计分板的队伍复制过去，让名字前缀保持可见；若其他插件改动了本模块的目标、侧边栏显示位置或内容行，会自动恢复。若玩家屏幕上已经是其他插件的计分板，本侧边栏会等到玩家回到主计分板后再显示。

### 🎨 服务器自定义

| 功能 | 描述 | 配置文件 |
|------|------|----------|
| MOTD 自定义 | 自定义服务器列表显示信息 | `config/motd.yml` |
| 入服欢迎 | 玩家加入时显示欢迎消息 | `config/welcome.yml` |
| Tab 栏自定义 | 自定义 Tab 列表头尾 | `config/tabbar.yml` |
| 自动回复 | 关键词触发自动回复 | `config/autoreply.yml` |

## 📦 安装

1. 确保服务器已安装 [UltiTools-API](https://github.com/UltiKits/UltiTools-Reborn) 6.2.1+
2. 将 `UltiEssentials-1.0.0.jar` 放入 `plugins/UltiTools/plugins/` 目录
3. 重启服务器或使用 `/ul reload` 重载插件

**或通过 UPM 安装:**
```
/upm install UltiEssentials
```

## ⚙️ 配置文件

### 主配置 (`config/essentials.yml`)

```yaml
features:
  # ===== 传送功能 =====
  back:
    enabled: true          # 启用 /back 返回命令
  spawn:
    enabled: true          # 启用 /spawn 出生点命令
  lobby:
    enabled: true          # 启用 /lobby 主城命令
  wild:
    enabled: true          # 启用 /wild 随机传送
    max-range: 10000       # 随机传送最大范围
    min-range: 100         # 随机传送最小范围
    cooldown: 60           # 冷却时间 (秒)，0 为不冷却

  # ===== 玩家状态 =====
  fly:
    enabled: true          # 启用 /fly 飞行命令
  heal:
    enabled: true          # 启用 /heal 和 /feed 命令
  speed:
    enabled: true          # 启用 /speed 速度命令
    max-speed: 10          # 最大速度倍数
  gamemode:
    enabled: true          # 启用 /gm 游戏模式命令
  hide:
    enabled: true          # 启用 /hide 隐身命令

  # ===== 管理工具 =====
  invsee:
    enabled: true          # 启用 /invsee 查看背包命令
  whitelist:
    enabled: true          # 启用 /wl 白名单命令

  # ===== 服务器自定义 =====
  motd:
    enabled: true          # 启用 MOTD 自定义
  join-welcome:
    enabled: true          # 启用入服欢迎消息
  tab-bar:
    enabled: true          # 启用 Tab 栏自定义
  auto-reply:
    enabled: true          # 启用自动回复
```

### 出生点配置 (`config/spawn.yml`)

```yaml
spawn:
  world: world             # 出生点世界名
  x: 0.0
  y: 64.0
  z: 0.0
  yaw: 0.0
  pitch: 0.0
```

### 主城配置 (`config/lobby.yml`)

```yaml
lobby:
  world: world             # 主城世界名
  x: 0.0
  y: 64.0
  z: 0.0
  yaw: 0.0
  pitch: 0.0
```

### MOTD 配置 (`config/motd.yml`)

```yaml
motd:
  line1: "&6欢迎来到服务器"
  line2: "&7在线人数: &a%online%&7/&c%max%"
  # 支持 %online% 和 %max% 变量
```

### 欢迎消息配置 (`config/welcome.yml`)

```yaml
welcome:
  enabled: true
  first-join-message: "&6欢迎 &e%player% &6第一次加入服务器！"
  join-message: "&a%player% 加入了服务器"
  quit-message: "&c%player% 离开了服务器"
  # 支持 %player% 变量
```

### Tab 栏配置 (`config/tabbar.yml`)

```yaml
tabbar:
  header: "&6=== 欢迎来到服务器 ==="
  footer: "&7在线: &a%online%&7 | &7TPS: &a%tps%"
  update-interval: 20      # 更新间隔 (tick)
  # 支持 PlaceholderAPI 变量
```

### 自动回复配置 (`config/autoreply.yml`)

```yaml
auto-reply:
  enabled: true
  replies:
    - trigger: "怎么赚钱"
      response: "&a可以通过挖矿、钓鱼、种地等方式赚取金币"
    - trigger: "怎么回城"
      response: "&a使用 /spawn 回到出生点，或 /lobby 回到主城"
```

## 🔐 权限节点

### 传送权限
| 权限 | 描述 | 默认 |
|------|------|------|
| `ultiessentials.back` | 使用 /back 命令 | 玩家 |
| `ultiessentials.spawn.teleport` | 传送到出生点 | 玩家 |
| `ultiessentials.spawn.set` | 设置出生点 | OP |
| `ultiessentials.lobby.teleport` | 传送到主城 | 玩家 |
| `ultiessentials.lobby.set` | 设置主城 | OP |
| `ultiessentials.wild` | 使用随机传送 | 玩家 |

### 玩家状态权限
| 权限 | 描述 | 默认 |
|------|------|------|
| `ultiessentials.fly` | 切换自己的飞行模式 | OP |
| `ultiessentials.fly.other` | 切换其他玩家的飞行模式（`ultiessentials.fly` 不授予此权限） | OP |
| `ultiessentials.heal.self` | 治疗自己 | OP |
| `ultiessentials.heal.other` | 治疗其他玩家 | OP |
| `ultiessentials.speed` | 调整速度 | OP |
| `ultiessentials.gamemode.self` | 切换自己的游戏模式 | OP |
| `ultiessentials.gamemode.other` | 切换他人游戏模式 | OP |
| `ultiessentials.hide` | 使用隐身模式 | OP |
| `ultiessentials.hide.see` | 看见隐身玩家 | OP |

### 管理权限
| 权限 | 描述 | 默认 |
|------|------|------|
| `ultiessentials.invsee` | 查看玩家背包 | OP |
| `ultiessentials.endersee` | 查看玩家末影箱 | OP |
| `ultiessentials.armorsee` | 查看玩家装备 | OP |
| `ultiessentials.whitelist.manage` | 管理白名单 | OP |

## 🌍 多语言支持

UltiEssentials 支持多语言，语言文件位于 `lang/` 目录：
- `lang/zh.json` - 简体中文
- `lang/en.json` - English

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 支持

- GitHub Issues: [https://github.com/UltiKits/UltiTools-Reborn/issues](https://github.com/UltiKits/UltiTools-Reborn/issues)
- 文档: [https://doc.ultikits.com](https://doc.ultikits.com)
- QQ 群: 请查看官网获取最新群号
