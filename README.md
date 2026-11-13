# UltiEssentials

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.4.0%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)

Requires **UltiTools-API 6.4.0 or later**; an older framework refuses to load this module (it declares `api-version: 640`).

需要 **UltiTools-API 6.4.0 或更高版本**；更早的框架会拒绝加载本模块（本模块声明 `api-version: 640`）。

UltiEssentials 是基于 UltiTools-API 框架开发的服务器基础功能插件模块，提供了常用的传送、玩家状态、管理员工具和服务器自定义功能。

## ✨ 功能特性

### 🚀 传送功能

| 命令 | 描述 | 权限 |
|------|------|------|
| `/back` | 返回上一个传送位置 | `ultiessentials.back` |
| `/recall <player>` | Bring an online player to you / 召回在线玩家 | `ultiessentials.recall.other` |
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

### IP bans / IP 封禁

| Command | Description | Permission |
|---|---|---|
| `/banip <ip> [reason]` | Permanently ban a numeric IPv4/IPv6 address in this module and kick every online connection sharing it | `ultiessentials.banip` |
| `/unbanip <ip>` | Lift all active module ban records for that address, confirming the stored result before reporting success | `ultiessentials.unbanip` |

Both commands accept players and console. Use numeric addresses only, without hostnames, ports, CIDR, brackets or scope suffixes. IPv6 spellings are normalized; `/banlist` displays an IP-only record with its address in the name column. Because that record stores the address as its name, `/unban <address>` typed exactly as `/banlist` shows it also lifts the IP-only ban and reports it as a player unban; use `/unbanip`, which accepts any equivalent spelling and also reports a remaining vanilla IP ban. `/banip` does not create a player UUID ban. Shared addresses affect everyone using them (including a proxy's address); use the address the server actually sees and prefer `/ban` when only one account should be blocked. No IP-address tab completion is provided.

`features.ban.enabled` gates new IP bans and login enforcement; `/unbanip` still works while disabled. The existing `features.ban.broadcast-ban` and `features.ban.broadcast-unban` switches govern the announcements (which contain the IP address); the issuer always receives confirmation. Set them to `false` if addresses must not be shown to other players. `/unbanip` deactivates every matching record, including a legacy record that also carries a player UUID; independent player bans without that address remain. It reports a surviving module record as failure, not as success or not-banned.

These commands never change the separate vanilla IP ban list. A remaining vanilla IP ban is reported explicitly; lift it separately with `minecraft:pardon-ip`. Vanilla `/ban-ip` and `/pardon-ip` are not replaced. Successful lifting means this module has no active IP ban for the address, not that all other player/login restrictions are gone.

新增 `/banip <IP> [原因]` 与 `/unbanip <IP>`，玩家和控制台均可使用。只接受数值 IPv4/IPv6 地址；封禁共享地址会影响该地址的全部玩家。命令仅管理本模块记录，不改动原版 IP 名单。IP 封禁记录以地址作为名称保存，因此按 `/banlist` 显示的写法执行 `/unban <地址>` 也会解除该 IP 封禁，并按玩家解封报告；建议使用 `/unbanip`，它接受等价的地址写法，并会提示仍存在的原版 IP 封禁。解封会确认全部匹配记录已停用；独立玩家封禁仍可能生效。广播包含 IP，可通过现有广播开关关闭；执行者仍会收到结果。

### Staff recall / 管理员召回

`/recall Traveler` immediately brings the online player named `Traveler` to your current position and facing, even from another world; your own position does not change. Use the complete username (case-insensitive), not a prefix. This is a **player-only staff command**, gated by `ultiessentials.recall.other`; `/back` or `/tpahere` access alone does not grant it. It has no self or console variant, cooldown or warmup. For a consent-based request, use `/tpahere` instead. A vanished target looks offline unless you hold `ultiessentials.hide.see`; Tab completion also hides them. Another plugin may cancel the teleport: you then see `Recall failed; the player was not teleported`, not a success. On success you see `Recalled Traveler to your location`, and Traveler is told who recalled them. The recalled player can return using `/back` if that feature and its separate permission are enabled.

`features.recall.enabled` in `config/essentials.yml` defaults to `true` and now actually gates this command. Set it to `false` and run `/ul reload UltiEssentials` to refuse recalls. An existing `false` stays false; the module does not migrate or overwrite your choice. This key was removed in 6.3.0 when no command existed; with the implemented command it is live again and is no longer warned about as removed. A new JAR still requires a server restart.

`/recall Traveler` 会立即把完整用户名为 `Traveler` 的在线玩家传送到你当前的位置和朝向，允许跨世界，你自己不会移动。用户名不区分大小写，但不接受名字前缀。仅玩家可执行，需要独立的 `ultiessentials.recall.other` 权限；拥有 `/back` 或 `/tpahere` 权限不代表可以召回别人。不能召回自己，控制台不可用，无冷却、无预热。需要对方同意时用 `/tpahere`。没有 `ultiessentials.hide.see` 权限时，隐身目标按离线处理，也不会出现在补全中。其他插件取消传送时只提示失败；成功时双方收到通知。被召回者另有 `/back` 权限且该功能开启时，可以返回原位。

`config/essentials.yml` 中的 `features.recall.enabled` 默认 `true`，现在会真正控制召回命令。改为 `false` 后执行 `/ul reload UltiEssentials` 即可禁用；旧文件中已有的 `false` 不会被覆盖。该键在 6.3.0 因命令尚不存在而删除，现在恢复为有效设置，不再报告“已移除”。更换 JAR 仍需重启。

### Ban command completion / 封禁命令补全

`/ban` and `/tempban` suggest online names for players, console and RCON through the framework's sender-aware providers. Players without `ultiessentials.hide.see` do not see vanished targets; non-player senders see every online player. `/tempban` also suggests duration presets matching the typed prefix. `/unban` suggests distinct names from this module's active, unexpired ban records (including the address stored as an IP-only record's name), not the online-player list or vanilla bans. Candidates are prefix-matched without regard to case and go through framework permission/target filtering. If no candidates remain for a non-player sender, UltiTools-API 6.4.0 still permits Bukkit's default online-name fallback.

`/ban`、`/tempban` 通过框架的发送者感知提供器，为玩家、控制台和 RCON 补全在线名字；玩家没有 `ultiessentials.hide.see` 权限时看不到隐身目标，非玩家发送者可看到全部在线玩家。`/tempban` 还会按已输入前缀补全预设时长。`/unban` 补全本模块有效、未过期封禁记录中的去重名称（包括 IP 封禁记录保存为名称的地址），而不是在线玩家或原版封禁名单。候选按前缀匹配、不区分大小写，并经过框架的权限和发送者类型过滤。非玩家请求没有候选时，UltiTools-API 6.4.0 仍允许 Bukkit 回退到默认的在线名字补全。

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

1. 确保服务器已安装 [UltiTools-API](https://github.com/UltiKits/UltiTools-Reborn) 6.4.0 或更高版本。本模块声明 `api-version: 640`，更早的框架会拒绝加载它
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
  recall:
    enabled: true          # Enable staff /recall; 启用管理员召回
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

**`features.data-repair.enabled`** (default `true`): repairs records an earlier build saved without a primary key (homes, warps, bans, container locks) so they can be deleted and updated. It governs the **JSON** store only: in **SQLite** the framework repairs such records itself when it opens their tables at start-up, whatever this key says (UltiTools-API 6.3.0); MySQL never holds them. The shipped `features.scheduled-commands.commands` example uses `say` only; a file written by an earlier build keeps its `broadcast` entry, which a server without a plugin providing `/broadcast` cannot run (Essentials provides `/broadcast`), until you change it.

**`features.data-repair.enabled`**（默认 `true`）：修复旧版本保存时缺少主键的记录（家、地标点、封禁、容器锁），使其可以删除和更新。它只作用于 **JSON** 存储；使用 **SQLite** 时，框架会在启动打开数据表时自行修复这类记录，与此设置无关（UltiTools-API 6.3.0）；MySQL 不会存在这类记录。`features.scheduled-commands.commands` 的出厂示例只使用 `say`；旧版本写入的文件会保留其中的 `broadcast` 条目，未安装提供 `/broadcast` 的插件时服务器无法执行它（`/broadcast` 由 Essentials 提供），需要自行修改。

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
| `ultiessentials.recall.other` | Staff recall of another online player / 召回其他在线玩家 | Grant explicitly through your permissions plugin / 通过权限插件单独授予 |
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

UltiTools manages both official files. In-place edits are restored to the shipped text at every start for all shipped languages, and on `/ul reload` or `/ul reload UltiEssentials` for the official language in use (the official base when using a custom name). The previous file is kept beside it as `.bak` (or `.1.bak`, `.2.bak`, etc. if needed), and a WARNING names the file and backup. On the first start after upgrading from a framework version without language-file hash records (before UltiTools-API 6.3.0), each official file that differs from the new jar is backed up and restored even if you never edited it. Both `en.json` and `zh.json` have renamed keys in this module, so when the framework upgrade and this module's JAR swap happen in the same start, those older files produce two backups and two WARNINGs. If you follow the recommended order with a restart in between (framework first, while still running the earlier module version), that start records the unedited earlier files as untouched, and after the JAR swap they are refreshed with no backup and no WARNING. Either way an unedited installation needs no action.

To customise messages, copy an official file **inside this module's own** `plugins/UltiTools/pluginConfig/UltiEssentials/lang/` folder under a new name: for example, `zh.json` → `zh-myserver.json`. Start the name with the official language code and a hyphen; the name without `.json` must start with an ASCII letter or digit and contain only ASCII letters, digits, `_` and `-`. Edit the copy and set `language: zh-myserver` in `plugins/UltiTools/config.yml` (one setting shared by the framework and every module). Missing messages come from official `zh`; a module without that custom file uses its official `zh`. A safe name without an official-code prefix, such as `ja`, is still read from this module's `lang/ja.json`, but missing messages use English and a WARNING explains the fallback for each module when its language is loaded. An unsafe name is not read; this module uses English instead. A copy in another module's folder does not customise UltiEssentials.

这两个官方语言文件归 UltiTools 管理：每次启动会检查所有自带语言并恢复直接修改过的官方文件；执行 `/ul reload` 或 `/ul reload UltiEssentials` 时，会对当前使用的官方语言（使用自定义名称时则是其对应的官方语言）做相同处理。旧文件在原目录保留为 `.bak`（名称已被占用时使用 `.1.bak`、`.2.bak` 等），一条 WARNING 会指明原文件与备份。首次从没有语言文件哈希记录的框架版本（UltiTools-API 6.3.0 之前）升级后启动时，只要官方文件与新 jar 不同，即使从未手动改过，也会被备份并恢复。本模块的 `en.json` 和 `zh.json` 都改过键名，因此当框架升级与本模块 JAR 替换在同一次启动中完成时，这两个旧文件会产生两个备份与两条 WARNING。若按推荐顺序分两次启动（先升级框架、仍运行旧版本模块并重启），那次启动会把未改动的旧文件记录为未修改，替换 JAR 后它们会直接更新，不产生备份与 WARNING。无论哪种顺序，从未改过语言文件的服务器都无需任何操作。

如需自定义消息，请在**本模块自己的** `plugins/UltiTools/pluginConfig/UltiEssentials/lang/` 目录中复制官方文件并改名（例如 `zh.json` → `zh-myserver.json`）。新名称应以官方语言代码加连字符开头；不含 `.json` 的名称必须以 ASCII 字母或数字开头，且只含 ASCII 字母、数字、`_` 和 `-`。修改副本后，在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver`（框架和所有模块共用这一个设置）。副本中没有的消息取自官方 `zh`，没有该自定义文件的模块使用其官方 `zh`。`ja` 这类安全但没有官方语言前缀的名称仍会读取本模块的 `lang/ja.json`，但缺少的消息使用英文，各模块加载语言时会以一条 WARNING 解释回退。名称不安全时不会读取该文件，本模块改用英文。放在其他模块目录中的副本不会影响 UltiEssentials。

## Known limitations / 已知限制

### Legacy JSON data filenames / 旧 JSON 数据文件名

Do not rename or hand-edit the framework-owned JSON data files. The start-up primary-key repair expects a legacy record's filename to be `<uuid>.json`, where `uuid` is the identity stored in that record. If a file was manually renamed before the repair runs, the repair can leave the un-keyed original alongside a new keyed copy. It reports zero repaired for that identity; later starts warn that records share an identity, and homes or warps may be listed twice (UltiKits/UltiEssentials#71). This is not a supported way to rename a home or warp.

Take a backup before upgrading or repairing legacy data, and leave data filenames unchanged. If files have already been renamed, stop the server, back up the affected store, and restore a known-good backup with its original filenames before restarting. Setting `features.data-repair.enabled: false` in `config/essentials.yml` before the next start holds off the JSON repair while you investigate; it does not remove duplicates or make un-keyed records updateable or deletable.

不要重命名或手动编辑框架管理的 JSON 数据文件。启动主键修复要求旧记录的文件名为 `<uuid>.json`，其中 `uuid` 是记录内部保存的标识。若修复前手动改过文件名，修复可能留下原来的无主键记录，同时写入一份带主键的副本。该标识的修复计数为零；之后启动会警告多条记录共用标识，家或地标点可能重复列出（UltiKits/UltiEssentials#71）。这不是受支持的家或地标点改名方式。

升级或修复旧数据前先备份，不要改动数据文件名。若已经改名，先停服并备份受影响的数据存储，再从保留原始文件名的可靠备份恢复，然后重启。排查期间可在下次启动前将 `config/essentials.yml` 的 `features.data-repair.enabled` 设为 `false`，暂缓 JSON 修复；该设置不会删除重复记录，也不会让缺少主键的记录变得可以更新或删除。

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 支持

- GitHub Issues: [https://github.com/UltiKits/UltiTools-Reborn/issues](https://github.com/UltiKits/UltiTools-Reborn/issues)
- 文档: [https://doc.ultikits.com](https://doc.ultikits.com)
- QQ 群: 请查看官网获取最新群号
