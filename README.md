# UltiEssentials

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.3.0%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)

Requires **UltiTools-API 6.3.0 or later**; an older framework refuses to load this module (it declares `api-version: 630`).

需要 **UltiTools-API 6.3.0 或更高版本**；更早的框架会拒绝加载本模块（本模块声明 `api-version: 630`）。

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
| `/gma` | 快捷切换到冒险模式 | `ultiessentials.gamemode.self` |
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

### Command aliases and the `/gmc` shortcuts / 命令别名与 `/gmc` 等快捷命令

`features.commandalias.aliases` in `config/essentials.yml` rewrites a typed command before the server dispatches it. A new install's map is `day` and `night`. Earlier builds also shipped `gmc`, `gms`, `gma` and `gmsp`, which have the same names as this module's own shortcuts (`/gma` is a module command from this release on): while those entries are in the map, `/gmc`, `/gms`, `/gma` and `/gmsp` go to vanilla `/gamemode` and its permission, and `ultiessentials.gamemode.self` has no effect on them. **Upgrading an existing install:** the module never rewrites a configuration file an operator already has, so a server that ran an earlier build keeps those four entries, byte for byte. To get the module's own commands and have `ultiessentials.gamemode.self` apply, delete the `gmc`, `gms`, `gma` and `gmsp` lines from `features.commandalias.aliases` and run `/ul reload UltiEssentials`.

`config/essentials.yml` 中的 `features.commandalias.aliases` 会在服务器分发命令之前改写玩家输入的命令。新安装的默认表为 `day`、`night`。旧版本还预置了 `gmc`、`gms`、`gma`、`gmsp`，它们与本模块自带的快捷命令同名（`/gma` 自本版本起是本模块的命令）：这四条在表中时，`/gmc`、`/gms`、`/gma`、`/gmsp` 会被转给原版 `/gamemode` 及其权限，`ultiessentials.gamemode.self` 对它们不起作用。**升级已有服务器：**本模块从不改写服主已有的配置文件，所以运行过旧版本的服务器会原样保留这四条。若要使用本模块自己的命令并让 `ultiessentials.gamemode.self` 生效，请从 `features.commandalias.aliases` 中删除 `gmc`、`gms`、`gma`、`gmsp` 四行，然后执行 `/ul reload UltiEssentials`。

### The sidebar's scoreboard / 侧边栏计分板

The sidebar is drawn on a private scoreboard this module assigns to each player who has it on. That scoreboard belongs to this module alone: another plugin must not write onto it (register its own objective named `ultiessentials`, or put a player into its own team on it). Such writes are not supported and may be overwritten. The module copies the main scoreboard's teams onto it, so name prefixes stay visible, and restores its own objective, sidebar slot and lines if another plugin changes them. If another plugin's scoreboard is already on screen, this sidebar waits until the player is back on the main scoreboard.

侧边栏画在本模块为每位开启侧边栏的玩家换上的私有计分板上。这块计分板只归本模块所有：其他插件不应往上面写东西（注册名为 `ultiessentials` 的目标，或把玩家放进自己在这块计分板上的队伍），这类写入不受支持，可能被覆盖。本模块会把主计分板的队伍复制过去，让名字前缀保持可见；若其他插件改动了本模块的目标、侧边栏显示位置或内容行，会自动恢复。若玩家屏幕上已经是其他插件的计分板，本侧边栏会等到玩家回到主计分板后再显示。

### 🎨 服务器自定义

| 功能 | 描述 | 配置文件 |
|------|------|----------|
| MOTD 自定义 | 自定义服务器列表显示信息 | `config/motd.yml` |
| 入服欢迎 | 已移至 UltiChat 模块 | UltiChat 的 `config/chat.yml` |
| Tab 栏自定义 | 自定义 Tab 列表头尾 | `config/tabbar.yml` |
| 自动回复 | 本模块不提供，请使用 UltiChat 模块 | UltiChat 的 `config/autoreply.yml` |

## 📦 安装

1. 确保服务器已安装 [UltiTools-API](https://github.com/UltiKits/UltiTools-Reborn) 6.3.0 或更高版本。本模块声明 `api-version: 630`，更早的框架会拒绝加载它
2. 将 `UltiEssentials-1.0.0.jar` 放入 `plugins/UltiTools/plugins/` 目录
3. 重启服务器（`/ul reload` 只重载配置，不会加载新的模块 jar）

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
  tab-bar:
    enabled: true          # 启用 Tab 栏自定义
```

**`features.data-repair.enabled`** (default `true`): repairs records an earlier build saved without a primary key (homes, warps, bans, container locks) so they can be deleted and updated. It governs the **JSON** store only: in **SQLite** the framework repairs such records itself when it opens their tables at start-up, whatever this key says (UltiTools-API 6.3.0); MySQL never holds them. The shipped `features.scheduled-commands.commands` example uses `say` only; a file written by an earlier build keeps its `broadcast` entry, which plain Paper cannot run (Essentials provides `/broadcast`), until you change it.

**`features.data-repair.enabled`**（默认 `true`）：修复旧版本保存时缺少主键的记录（家、地标点、封禁、容器锁），使其可以删除和更新。它只作用于 **JSON** 存储；使用 **SQLite** 时，框架会在启动打开数据表时自行修复这类记录，与此设置无关（UltiTools-API 6.3.0）；MySQL 不会存在这类记录。`features.scheduled-commands.commands` 的出厂示例只使用 `say`；旧版本写入的文件会保留其中的 `broadcast` 条目，原版 Paper 无法执行它（`/broadcast` 由 Essentials 提供），需要自行修改。

### 出生点配置 (`config/spawn.yml`)

```yaml
spawn:
  location:
    world: world           # 出生点世界名
    x: 0.0
    y: 64.0
    z: 0.0
    yaw: 0.0
    pitch: 0.0
  teleport-on-first-join: true   # 玩家首次加入时传送到出生点
  teleport-on-respawn: true      # 玩家重生时传送到出生点
```

### 主城配置 (`config/lobby.yml`)

```yaml
lobby:
  location:
    world: world           # 主城世界名
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
  line2: "&7由 UltiTools 驱动"
  max-players: -1          # 服务器列表中显示的最大人数，-1 表示不改动
  # 只支持 & 颜色代码，不替换 %online%、%max% 等变量
```

### 欢迎消息

UltiEssentials 不再读取 `config/welcome.yml`。入服欢迎、进出服消息和首次加入广播已移至 UltiChat 模块，配置在 UltiChat 的 `config/chat.yml` 的 `join-quit.*` 下。

### Tab 栏配置 (`config/tabbar.yml`)

```yaml
tabbar:
  header: "&6=== 欢迎来到服务器 ==="
  footer: "&7在线: &e%online%&7/&e%max%"
  # 玩家加入时设置；只替换 %online% 和 %max%，不支持其他变量
```

### 自动回复

UltiEssentials 不提供自动回复，也不读取 `config/autoreply.yml`。关键词自动回复由 UltiChat 模块提供，配置在 UltiChat 的 `config/autoreply.yml`。

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

这两个官方语言文件归 UltiTools 管理：直接修改会在下次启动时被恢复（修改过的文件会留下备份）。如需自定义消息，请把官方文件复制为以语言代码加连字符开头、只含 ASCII 字母、数字、`_` 和 `-` 的新名称（例如 `zh.json` → `zh-myserver.json`），修改这个副本，然后在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver`（框架和所有模块共用这一个设置）。副本中没有的消息取自名称开头对应的官方文件（此处为 `zh`）；没有该名称文件的模块使用其官方 `zh`。

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 支持

- GitHub Issues: [https://github.com/UltiKits/UltiTools-Reborn/issues](https://github.com/UltiKits/UltiTools-Reborn/issues)
- 文档: [https://doc.ultikits.com](https://doc.ultikits.com)
- QQ 群: 请查看官网获取最新群号
