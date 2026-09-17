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
  `features.nameprefix.enabled` and `features.nameprefix.update-interval` apply without a restart,
  and turning name prefixes on by reload applies them without an error on player join
  (UltiKits/UltiEssentials#28). As on a restart, a reload starts each scheduled command's interval
  again, and it resets every player's `/scoreboard` choice: with `features.scoreboard.enabled` and
  `features.scoreboard.auto-enable` both true every online player gets the sidebar, otherwise every
  sidebar is removed.
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
  模块重载日志（UltiKits/UltiEssentials#23）。
- `/ul reload UltiEssentials`（或 `/ul reload`）现在还会按重新读取的配置重启定时命令、计分板和头顶称号服务，
  因此修改 `features.scheduled-commands.enabled`、`features.scheduled-commands.commands`、
  `features.scoreboard.enabled`、`features.scoreboard.update-interval`、
  `features.nameprefix.enabled` 与 `features.nameprefix.update-interval` 后无需重启即可生效，通过重载开启
  头顶称号也会正常应用称号，玩家加入时不会报错（UltiKits/UltiEssentials#28）。与重启一样，重载会让每条
  定时命令的间隔重新开始计时，并重置每位玩家通过 `/scoreboard` 做出的选择：`features.scoreboard.enabled` 与
  `features.scoreboard.auto-enable` 均为 true 时所有在线玩家都会显示侧边栏，否则所有侧边栏都会被移除。
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
