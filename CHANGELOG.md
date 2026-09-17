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
  logs its own per-module reload line at this point. A reload still does not start, stop or
  reschedule the scheduled-command, scoreboard and name-prefix background tasks, so edits to their
  on/off switches, command list or update intervals still need a restart to change those tasks
  (UltiKits/UltiEssentials#28) (UltiKits/UltiEssentials#23).
- Unloading this module (`/upm uninstall UltiEssentials`, or server shutdown) now runs the
  framework's command unregistration and then its listener unregistration; this module has no
  unload work of its own. Previously this module's unload method replaced the framework's, so its
  commands were never unregistered on any unload path, and its listeners were not unregistered on
  `/upm uninstall` (UltiKits/UltiEssentials#23).
- 重载本模块（`/ul reload UltiEssentials`）现在会重新读取其配置文件并刷新语言文件，修改后的
  `features.speed.max-speed` 等配置无需重启即可生效。此前本模块的重载方法替换了框架的重载方法且只输出
  一行日志，这两步都不会执行。UltiTools 6.3.0 还会在此时报告 `@ConditionalOnConfig` 漂移并输出框架自身的
  模块重载日志。重载仍不会启动、停止或重新调度定时命令、计分板和头顶称号的后台任务，修改它们的开关、
  命令列表或更新间隔仍需重启服务器才能作用于这些任务（UltiKits/UltiEssentials#28）（UltiKits/UltiEssentials#23）。
- 卸载本模块（`/upm uninstall UltiEssentials` 或关闭服务器）现在会由框架先注销命令、再注销监听器；
  本模块自身没有卸载工作。此前本模块的卸载方法替换了框架的卸载方法，因此任何卸载途径都不会注销其命令，
  `/upm uninstall` 也不会注销其监听器（UltiKits/UltiEssentials#23）。

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
