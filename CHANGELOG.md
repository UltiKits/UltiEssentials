# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Reloading this module (`/ul reload UltiEssentials`) now re-reads its configuration files and
  refreshes its language files, so an edited value such as `features.speed.max-speed` applies
  without a restart. Previously this module's reload method replaced the framework's and only
  logged a line, so neither step ran. UltiTools 6.3.0 also reports `@ConditionalOnConfig` drift and
  logs its own per-module reload line at this point (UltiKits/UltiEssentials#23).
- `/ul reload UltiEssentials` (or `/ul reload`) now also restarts the scheduled-command, scoreboard
  and name-prefix services against the re-read configuration, so changes to
  `features.scheduled-commands.enabled`, `features.scheduled-commands.commands`,
  `features.scoreboard.enabled`, `features.scoreboard.update-interval`,
  `features.nameprefix.enabled` and `features.nameprefix.update-interval` apply without a restart;
  turning name prefixes on through `/ul reload UltiEssentials` now applies them to online players
  and later joins (UltiKits/UltiEssentials#28). As on a restart, a reload starts each scheduled
  command's interval again. While the scoreboard stays enabled across a reload, each online
  player's sidebar stays shown or hidden as it was, including a `/scoreboard` choice; a reload that
  turns the scoreboard off removes every sidebar, and a reload that turns it on applies
  `features.scoreboard.auto-enable` to players already online. If one of the three services fails
  to reload, the failure is logged and the other two are still reloaded.
- Turning the sidebar off with `/scoreboard off` or `/scoreboard` now returns the player to the
  server's main scoreboard, so name prefixes and other teams on it become visible to that player.
  Previously the player was given an empty scoreboard and saw none of them until they rejoined
  (UltiKits/UltiEssentials#44).
- One player whose sidebar or name prefix cannot be refreshed (for example because a PlaceholderAPI
  expansion throws for that player) no longer stops the scoreboard and name-prefix refreshes for
  every other player. That player is retried on every update, and the failure is logged once and
  again only after a refresh for that player has succeeded in between. A name-prefix team removed
  with the vanilla `team remove` command is re-created on the next update (UltiKits/UltiEssentials#44).
- Unloading this module (`/upm uninstall UltiEssentials`) now runs the framework's command
  unregistration and then its listener unregistration, so the module's commands are really removed
  and its listeners stop firing. Previously this module's unload method replaced the framework's, so
  after `/upm uninstall UltiEssentials` both its commands and its listeners stayed active until the
  server restarted (UltiKits/UltiEssentials#23). The scheduled-command, scoreboard and name-prefix background tasks
  are still not cancelled when the module is uninstalled without a server restart: configured
  scheduled commands keep running (UltiKits/UltiEssentials#43).
- `/delhome <name>`, `/delwarp <name>`, `/unban <player>`, `/unlock`, re-running
  `/sethome <name>` on an existing home, and breaking your own locked container now actually change
  what is stored. Every record this module writes — homes, warps, bans and container locks — was
  previously saved with an empty primary key, so the later delete or update matched no row at all
  and the stored record survived untouched: a deleted home reappeared in `/homes`, an unbanned
  player was still rejected at login with this module's own ban message, a broken container's lock
  came back after a restart, and moving an existing home kept the old coordinates. Records written
  from this version on carry that key, and records written by earlier versions are repaired once at
  start-up (UltiKits/UltiEssentials#34, UltiKits/UltiEssentials#35, UltiKits/UltiEssentials#37).
- `/fly <player>` now requires `ultiessentials.fly.other`, a separate node from the
  `ultiessentials.fly` that lets a player toggle their own flight. Previously the two shared one
  node, so any server granting ordinary players self-flight was also letting them force flight on or
  off for anyone online, including cutting another player's flight mid-air. Grant
  `ultiessentials.fly.other` to whoever should keep that ability — this matches how `/heal <player>`
  and `/gm <mode> <player>` already work (UltiKits/UltiEssentials#25).
- 重载本模块（`/ul reload UltiEssentials`）现在会重新读取其配置文件并刷新语言文件，修改后的
  `features.speed.max-speed` 等配置无需重启即可生效。此前本模块的重载方法替换了框架的重载方法且只输出
  一行日志，这两步都不会执行。UltiTools 6.3.0 还会在此时报告 `@ConditionalOnConfig` 漂移并输出框架自身的
  模块重载日志（UltiKits/UltiEssentials#23）。
- `/ul reload UltiEssentials`（或 `/ul reload`）现在还会按重新读取的配置重启定时命令、计分板和头顶称号服务，
  因此修改 `features.scheduled-commands.enabled`、`features.scheduled-commands.commands`、
  `features.scoreboard.enabled`、`features.scoreboard.update-interval`、
  `features.nameprefix.enabled` 与 `features.nameprefix.update-interval` 后无需重启即可生效；通过
  `/ul reload UltiEssentials` 开启头顶称号后，称号现在会应用到在线玩家和之后加入的玩家（UltiKits/UltiEssentials#28）。
  与重启一样，重载会让每条定时命令的间隔重新开始计时。计分板在重载前后都保持开启时，每位在线玩家侧边栏的
  显示或隐藏状态保持不变，包括其通过 `/scoreboard` 做出的选择；关闭计分板的重载会移除所有侧边栏，开启计分板的
  重载会对已在线的玩家应用 `features.scoreboard.auto-enable`。三个服务中任一服务重载失败时会记录日志，另外两个
  服务仍会重载。
- 通过 `/scoreboard off` 或 `/scoreboard` 关闭侧边栏后，玩家现在会回到服务器的主计分板，头顶称号及主计分板上的
  其他队伍对该玩家变为可见。此前玩家会被分配一个空计分板，重新进入服务器前看不到这些内容
  （UltiKits/UltiEssentials#44）。
- 某位玩家的侧边栏或头顶称号无法刷新时（例如某个 PlaceholderAPI 扩展对该玩家抛出异常），不再会导致其他所有
  玩家的计分板和头顶称号停止刷新。该玩家会在每次更新时重试，失败只记录一次日志，直到该玩家刷新成功后再次失败
  才会重新记录。通过原版 `team remove` 命令删除的头顶称号队伍会在下次更新时重新创建（UltiKits/UltiEssentials#44）。
- 卸载本模块（`/upm uninstall UltiEssentials`）现在会由框架先注销命令、再注销监听器，本模块的命令会被
  真正移除，其监听器也不再触发。此前本模块的卸载方法替换了框架的卸载方法，因此执行
  `/upm uninstall UltiEssentials` 后，其命令和监听器都会保持生效，直到服务器重启（UltiKits/UltiEssentials#23）。在不重启服务器的情况下卸载本模块时，定时命令、计分板和头顶称号的后台
  任务仍不会被取消，已配置的定时命令会继续执行（UltiKits/UltiEssentials#43）。
- `/delhome <名称>`、`/delwarp <名称>`、`/unban <玩家>`、`/unlock`、对已存在的家再次执行
  `/sethome <名称>`，以及破坏自己上锁的容器，现在都会真正改变已保存的数据。本模块写入的每条记录——家、地标点、
  封禁和容器锁——此前保存时主键为空，之后的删除或更新语句匹配不到任何行，已保存的记录原样保留：被删除的家仍会
  出现在 `/homes` 中，被解禁的玩家登录时仍会被本模块自己的封禁提示拒绝，被破坏容器的锁会在重启后回来，移动已存在
  的家仍保留旧坐标。从本版本起写入的记录都带有该主键，更早版本写入的记录会在启动时修复一次
  （UltiKits/UltiEssentials#34、UltiKits/UltiEssentials#35、UltiKits/UltiEssentials#37）。
- `/fly <玩家>` 现在需要 `ultiessentials.fly.other` 权限，与玩家切换自己飞行所用的 `ultiessentials.fly`
  分开。此前两者共用同一个权限节点，因此只要服务器给普通玩家开放了自己飞行的权限，他们同时也能为任何在线玩家
  开启或关闭飞行，包括在其飞行途中将其关闭。请为应保留该能力的人授予 `ultiessentials.fly.other`——这与
  `/heal <玩家>`、`/gm <模式> <玩家>` 现有的做法一致（UltiKits/UltiEssentials#25）。

### Removed

- The module's own "disabled" console line on unload (`UltiEssentials disabled!` under
  `language: en`) and its "configuration reloaded" console line on `/ul reload UltiEssentials`
  (`UltiEssentials config reloaded!`), together with the two language keys, present in both
  `lang/en.json` and `lang/zh.json`, that translated them. UltiTools 6.3.0 logs one reload line per
  module (`Module 'UltiEssentials' reloaded.`) (UltiKits/UltiEssentials#23).
- 移除本模块卸载时输出的"UltiEssentials 已禁用！"控制台行、`/ul reload UltiEssentials` 时输出的
  "UltiEssentials 配置已重载！"控制台行，以及 `lang/en.json` 与 `lang/zh.json` 中对应的
  `UltiEssentials 已禁用！`、`UltiEssentials 配置已重载！` 两个语言键。UltiTools 6.3.0 会为每个模块输出
  一行重载日志（UltiKits/UltiEssentials#23）。
