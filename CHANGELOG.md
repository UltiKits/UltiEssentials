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
  **Restart the server, do not reload, after changing `features.nameprefix.enabled`,
  `features.scoreboard.enabled`, `features.scoreboard.update-interval`,
  `features.nameprefix.update-interval`, `features.scheduled-commands.enabled` or
  `features.scheduled-commands.commands`.** A reload does not start, stop or reschedule the
  scheduled-command, scoreboard and name-prefix background tasks (UltiKits/UltiEssentials#28). In
  particular, a reload that turns `features.nameprefix.enabled` from `false` to `true` leaves the
  name-prefix service without a scoreboard, so every player join afterwards logs a
  `NullPointerException` from `NamePrefixService.updatePlayer` and no prefix is applied until a
  restart. Turning name prefixes off by reload freezes the existing prefixes; turning the scoreboard
  on by reload shows a sidebar that never refreshes; scheduled commands do not start, stop or
  change.
- Unloading this module (`/upm uninstall UltiEssentials`) now runs the framework's command
  unregistration and then its listener unregistration, so the module's commands are really removed
  and its listeners stop firing. Previously this module's unload method replaced the framework's, so
  after `/upm uninstall UltiEssentials` both its commands and its listeners stayed active until the
  server restarted (UltiKits/UltiEssentials#23). The scheduled-command, scoreboard and name-prefix background tasks
  are still not cancelled when the module is uninstalled without a server restart: configured
  scheduled commands keep running (UltiKits/UltiEssentials#43).
- 重载本模块（`/ul reload UltiEssentials`）现在会重新读取其配置文件并刷新语言文件，修改后的
  `features.speed.max-speed` 等配置无需重启即可生效。此前本模块的重载方法替换了框架的重载方法且只输出
  一行日志，这两步都不会执行。UltiTools 6.3.0 还会在此时报告 `@ConditionalOnConfig` 漂移并输出框架自身的
  模块重载日志（UltiKits/UltiEssentials#23）。**修改 `features.nameprefix.enabled`、
  `features.scoreboard.enabled`、`features.scoreboard.update-interval`、
  `features.nameprefix.update-interval`、`features.scheduled-commands.enabled` 或
  `features.scheduled-commands.commands` 后请重启服务器，不要使用重载。** 重载不会启动、停止或重新调度
  定时命令、计分板和头顶称号的后台任务（UltiKits/UltiEssentials#28）。尤其是通过重载把
  `features.nameprefix.enabled` 从 `false` 改为 `true` 时，头顶称号服务没有计分板对象，之后每位玩家加入都会
  在 `NamePrefixService.updatePlayer` 抛出 `NullPointerException`，重启前不会应用任何称号。通过重载关闭
  头顶称号会使现有称号冻结；通过重载开启计分板会显示一个不再刷新的侧边栏；定时命令不会启动、停止或改变。
- 卸载本模块（`/upm uninstall UltiEssentials`）现在会由框架先注销命令、再注销监听器，本模块的命令会被
  真正移除，其监听器也不再触发。此前本模块的卸载方法替换了框架的卸载方法，因此执行
  `/upm uninstall UltiEssentials` 后，其命令和监听器都会保持生效，直到服务器重启（UltiKits/UltiEssentials#23）。在不重启服务器的情况下卸载本模块时，定时命令、计分板和头顶称号的后台
  任务仍不会被取消，已配置的定时命令会继续执行（UltiKits/UltiEssentials#43）。

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
