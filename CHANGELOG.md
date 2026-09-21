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
  server restarted (UltiKits/UltiEssentials#23).
- `/upm uninstall UltiEssentials` now stops every repeating background task this module started:
  configured entries of `features.scheduled-commands.commands` stop being dispatched to the console,
  the sidebar update timer and the name-prefix update timer stop, and a teleport warmup still
  counting down is cancelled rather than completed. Players who had a sidebar are returned to the
  server's main scoreboard and this module's name-prefix teams are emptied of their members, so no
  leftover sidebar or prefix survives the uninstall; the now-empty `up_` teams themselves stay
  registered on the main scoreboard and can be removed with the vanilla `team remove` command.
  Previously all of these kept running against the uninstalled module until the server was
  restarted, while the uninstall reported success. A player whose warmup is cancelled stays where
  they are and is not told: telling them would need a new language key, and this module's keys are
  being reworked under UltiKits/UltiEssentials#26. This covers the tasks that run on a timer, and
  not tasks scheduled to run once — a pending `/tpa` request still expires and still messages both
  players up to `features.tpa.timeout` seconds (30 by default) after the uninstall, and a player who
  joins in the second before it can still be given a sidebar or a name prefix; that residue is
  UltiKits/UltiEssentials#51. If one of these services fails to stop, or cannot be reached at all,
  the others are still stopped and the uninstall reports that failure rather than a clean removal
  (UltiKits/UltiEssentials#43).
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
- At start-up, this module now repairs records it wrote before the fix above, giving each the primary
  key it was saved without. It reports one line per kind of record it wrote and a summary, so such a
  line always means records were written; a record it leaves alone is reported as a warning instead,
  one line each with the reason. It runs once: a repaired record is not visited again on later start-ups. A record it
  cannot repair safely is left exactly as it is and reported as a warning — this happens when the
  record carries no identity of its own, when two stored records share one identity, or when a record
  already carries a key that disagrees with its identity. Such a record still reads normally, but
  deleting or updating it keeps failing, and the warning names it so it can be re-created by hand
  (UltiKits/UltiEssentials#34).
- New configuration key `features.data-repair.enabled` in `config/essentials.yml`, default `true`:
  whether the start-up repair described above runs. Set it to `false` to leave records written before
  the fix exactly as they are — useful if you are part-way through a migration or have not taken a
  backup yet. One log line then names the key, no records are written, and deleting or updating those
  records keeps failing until you turn it back on. There is no dry-run setting
  (UltiKits/UltiEssentials#34).
- A deletion, unban or unlock that could not change what is stored now says so, instead of saying the
  record does not exist. `/delhome`, `/delwarp` and `/unban` previously reported a surviving record
  the same way they report one that was never there, which told an operator to stop looking while
  `/homes` or `/banlist` still listed it. Each now has its own message for that case, as `/unlock`
  already did. Re-running `/sethome <name>` on an existing home checks the stored coordinates
  afterwards and reports a failure rather than confirming a move that would still teleport you to the
  old location. Unlocking a double chest reports success only when BOTH halves' records are gone; a
  surviving half is reported as a failure and stays locked (UltiKits/UltiEssentials#34,
  UltiKits/UltiEssentials#35, UltiKits/UltiEssentials#37).
- A lock on a double chest is now enforced from either side, and unlocking one is all or nothing.
  Because the two halves are one shared inventory, a lock record on either half now refuses strangers
  clicking either block — previously the check looked only at the block clicked, so a container whose
  halves disagreed could be opened from the unrecorded side. Unlocking removes every record protecting
  the container together: if any of them cannot be removed, none is, the container keeps reporting as
  locked, and the failure is reported rather than a half-unlocked container being left behind
  (UltiKits/UltiEssentials#37).
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
  `/upm uninstall UltiEssentials` 后，其命令和监听器都会保持生效，直到服务器重启（UltiKits/UltiEssentials#23）。
- `/upm uninstall UltiEssentials` 现在会停止本模块启动的全部重复后台任务：
  `features.scheduled-commands.commands` 中配置的条目不再向控制台派发命令，侧边栏刷新任务与头顶称号刷新
  任务都会停止，正在倒计时的预热传送会被取消而不是继续执行。拥有侧边栏的玩家会回到服务器的主计分板，本模块
  的头顶称号队伍会被清空成员，卸载后不会残留侧边栏或称号；已清空的 `up_` 队伍本身仍注册在主计分板上，可用
  原版 `team remove` 命令删除。此前这些任务都会继续对已卸载的模块运行，直到服务器重启，而卸载却报告成功。
  预热传送被取消的玩家会留在原地且不会收到提示：发送提示需要新增语言键，而本模块的语言键正在
  UltiKits/UltiEssentials#26 中统一重命名。本次改动覆盖的是按固定间隔重复的任务，不包括只执行一次的延时
  任务——待处理的 `/tpa` 请求仍会在卸载后最多 `features.tpa.timeout` 秒（默认 30 秒）超时并向双方发送消息，
  卸载前一秒内加入的玩家仍可能被加上侧边栏或头顶称号，这部分残留记录在 UltiKits/UltiEssentials#51。若其中
  某个服务停止失败或根本无法找到，其余服务仍会停止，并且卸载会如实报告该失败，而不是报告卸载干净
  （UltiKits/UltiEssentials#43）。
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
- 启动时，本模块现在会修复上一条所述在修复之前写入的记录，为每条记录补上保存时缺失的主键。它会为写入过记录的每类记录各输出一行日志并给出汇总，因此出现该行就说明确实写入了数据；
  被跳过的记录改为以警告形式逐条报告并说明原因。
  修复只进行一次：已修复的记录在之后的启动中不会再被处理。无法安全修复的记录会原样保留，并以警告形式报告——
  出现在记录自身没有标识、两条记录共用同一标识，或记录已带有与其标识不一致的主键时。此类记录仍可正常读取，
  但删除或更新仍会失败，警告中会指出该记录，便于手动重建（UltiKits/UltiEssentials#34）。
- `config/essentials.yml` 新增配置项 `features.data-repair.enabled`，默认 `true`：控制上述启动修复是否执行。
  设为 `false` 会让修复前写入的记录保持原样——在数据迁移进行中或尚未备份时很有用。此时日志会输出一行指明该配置项，
  不写入任何记录，这些记录的删除与更新在重新开启前仍会失败。本项没有“试运行”模式（UltiKits/UltiEssentials#34）。
- 删除、解禁或解锁未能改变已保存的数据时，现在会明确说明，而不再报告该记录不存在。`/delhome`、`/delwarp`、
  `/unban` 此前会把"记录仍然存在"与"记录从未存在"报告为同一种结果，导致管理员在 `/homes` 或 `/banlist`
  仍列出该记录时就不再排查。现在三者各有专门的提示，与 `/unlock` 原有的做法一致。对已存在的家再次执行
  `/sethome <名称>` 会在之后核对已保存的坐标，若新位置未写入存储则报告失败，而不再确认一次仍会把玩家传送到
  旧位置的"移动"。解锁大箱子时，只有两半的记录都被移除才报告成功；任一半的记录仍然存在即报告失败并保持上锁
  （UltiKits/UltiEssentials#34、UltiKits/UltiEssentials#35、UltiKits/UltiEssentials#37）。
- 大箱子的锁现在从任意一侧都会生效，解锁则是要么两边都解、要么都不解。由于大箱子的两半共用一个容器，任一半上的
  锁定记录现在都会拒绝他人点击任意一块——此前检查只看被点击的那一块，因此两半记录不一致的容器可以从没有记录的
  一侧打开。解锁时会把保护该容器的所有记录放在同一个事务中一起移除：只要有一条无法移除，就一条都不移除，容器
  继续显示为已锁定，并报告失败，而不会留下解锁了一半的容器（UltiKits/UltiEssentials#37）。

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
