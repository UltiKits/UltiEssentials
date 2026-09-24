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
- `/ul reload UltiEssentials` now names, as a warning in the console, any of those services it could
  not reach — previously it reloaded the ones it found and reported success either way, so a feature
  left running on its old configuration looked identical to one that had been reloaded. The other
  services are still reloaded. On a stock install nothing can make this warning appear, and likewise
  nothing can make the uninstall above report a service it could not reach: both are guards against a
  future change to this module's own source, not settings you can reach from a configuration file
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
- `features.ban.broadcast-ban` and `features.ban.broadcast-unban` in `config/essentials.yml` now
  take effect. Previously `/ban`, `/tempban` and `/unban` announced every successful ban or unban to
  the whole server whatever these were set to. Both default to `true`, which is what the commands
  always did, so nothing changes unless one is `false` — and if your file already has one set to
  `false`, it takes effect from this version. With `features.ban.broadcast-ban: false`, a ban's
  notice and reason go to whoever issued it instead of to everyone — the notice was the issuer's
  only confirmation, so it is redirected rather than dropped — and the rest of the server no longer
  sees it. `/banlist` lists the ban either way. With `features.ban.broadcast-unban: false`, the
  issuer still gets the unban confirmation they always got, and nothing is broadcast
  (UltiKits/UltiEssentials#27).
- A pending `/tpa` or `/tpahere` request is now cleared as soon as its sender or its target leaves
  the server. Previously it stayed pending until `features.tpa.timeout` ran out (30 seconds by
  default), and for that whole time its target refused every other player's request as already
  having one. The player who stays online is not messaged when this happens: previously they were
  told the request had timed out once the timeout ran out, and a target who ran `/tpaccept` after the
  sender had left was told the sender was offline; now the request is simply gone, so `/tpaccept`
  and `/tpdeny` report that there is no pending request (UltiKits/UltiEssentials#30).
- A player vanished with `/hide` now stays hidden from players who join after they vanished.
  Previously `/hide` hid them only from the players online at that moment, so anyone who joined
  later could see them. A joiner holding `ultiessentials.hide.see` still sees vanished players, as
  players online at the time of the `/hide` already did (UltiKits/UltiEssentials#32).
- `/upm uninstall UltiEssentials` now shows every player vanished with `/hide` to everyone again and
  forgets the vanish state. Previously the vanish outlived the module — the hides belong to UltiTools
  itself, which stays enabled — so a vanished player stayed hidden from the players who were online
  when they vanished, was visible to anyone who joined afterwards, and could not un-vanish until they
  relogged, because `/hide` had been uninstalled with the module. The vanished player is not told
  (found by review of UltiKits/UltiEssentials#32).
- `features.wild.cooldown` in `config/essentials.yml` now sets `/wild`'s cooldown, in seconds.
  Previously the cooldown was a fixed 60 seconds and the key was never read. The default is still
  `60`, which is what every existing file already holds unless you edited it — an edited value takes
  effect from this version. `0` means no cooldown. `/ul reload UltiEssentials` applies a new value to
  the next `/wild`; a cooldown already running keeps the end time it started with, also when the new
  value is `0`. A negative value stops the module loading at start-up, with an error naming the key
  and the value. On `/ul reload UltiEssentials` a negative value is not applied: the console shows a
  warning naming the key, `/wild` keeps the cooldown it was using, and the rest of the reload —
  other settings in the file, the service restarts — completes as usual. The setting used to be
  declared with a 3600-second maximum; that limit is gone, and any value up to 2147483647 seconds is
  accepted (UltiKits/UltiEssentials#27, through UltiKits/UltiTools-Reborn#531).
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
- `/ul reload UltiEssentials` 现在会在控制台以警告形式指出重载时找不到的服务——此前它只重载能找到的服务
  并一律报告成功，因此仍按旧配置运行的功能与已重载的功能在日志上无法区分。其余服务仍会正常重载。在未修改
  源码的正式版本上，这条警告不可能出现，上面卸载时“找不到某个服务”的报错同样不可能出现：两者都是针对本模块
  源码未来改动的防护，而不是可以通过配置文件进入的状态（UltiKits/UltiEssentials#43）。
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
- `config/essentials.yml` 中的 `features.ban.broadcast-ban` 与 `features.ban.broadcast-unban` 现在会生效。此前
  `/ban`、`/tempban` 与 `/unban` 无论这两项如何设置，都会把每次成功的封禁或解禁广播给全服。两项默认均为 `true`，
  与这些命令一直以来的行为相同，因此除非其中一项为 `false`，否则没有任何变化——若你的文件中已将其中一项设为
  `false`，从本版本起即会生效。设置
  `features.ban.broadcast-ban: false` 后，封禁通知及原因改为只发送给执行者，而不再发给所有人——该通知是执行者
  唯一的确认信息，因此是改发而不是丢弃——服务器其他人不再看到它。无论如何设置，
  `/banlist` 都会列出该封禁。设置 `features.ban.broadcast-unban: false` 后，执行者仍会收到一直以来的解禁确认，
  且不再进行任何广播（UltiKits/UltiEssentials#27）。
- 待处理的 `/tpa` 或 `/tpahere` 请求现在会在其发送者或目标离开服务器时立即清除。此前该请求会一直保留到
  `features.tpa.timeout`（默认 30 秒）结束，在此期间其目标会以"已有待处理请求"为由拒绝其他所有玩家的请求。
  清除时不会向仍在线的一方发送消息：此前超时结束时他们会收到"请求已超时"的提示，而在发送者离开后执行
  `/tpaccept` 的目标会被告知发送者已离线；现在请求直接消失，因此 `/tpaccept` 与 `/tpdeny` 会报告没有待处理的
  请求（UltiKits/UltiEssentials#30）。
- 使用 `/hide` 隐身的玩家现在对其隐身之后才加入的玩家同样保持隐身。此前 `/hide` 只对当时在线的玩家生效，之后
  加入的任何人都能看见隐身者。持有 `ultiessentials.hide.see` 的加入者仍能看见隐身玩家，与 `/hide` 执行时已在线
  的玩家一致（UltiKits/UltiEssentials#32）。
- `/upm uninstall UltiEssentials` 现在会让所有使用 `/hide` 隐身的玩家对所有人重新可见，并清空隐身状态。此前隐身
  效果会在模块卸载后继续存在——这些隐藏记录属于仍处于启用状态的 UltiTools 本身——因此隐身玩家仍对其隐身时在线的
  玩家不可见，却对之后加入的玩家可见，并且由于 `/hide` 已随模块一起卸载，只能重新登录才能解除隐身。隐身玩家不会
  收到提示（在审查 UltiKits/UltiEssentials#32 时发现）。
- `config/essentials.yml` 中的 `features.wild.cooldown` 现在决定 `/wild` 的冷却时间（秒）。此前冷却时间固定为
  60 秒，该配置项从未被读取。默认值仍为 `60`，现有配置文件中除非你改过，本来就是这个值——改过的值从本版本起生效。
  `0` 表示不冷却。`/ul reload UltiEssentials` 会让新值作用于下一次 `/wild`；已经开始的冷却保持其原有的结束时间，
  新值为 `0` 时也是如此。负数会在启动时阻止模块加载，错误信息会指明该配置项和数值。在
  `/ul reload UltiEssentials` 时负数不会生效：控制台会输出一条指明该配置项的警告，`/wild` 继续使用原来的冷却时间，
  重载的其余部分——文件中的其他设置、各服务的重启——照常完成。该设置原先声明了 3600 秒的上限，该上限已取消，
  最大可接受 2147483647 秒（UltiKits/UltiEssentials#27，依赖 UltiKits/UltiTools-Reborn#531）。

### Changed

- Language keys were renamed from Chinese sentences to ASCII keys (for example `essentials.home.set`).
  An operator who edited this module's `lang/en.json` or `lang/zh.json` must re-apply those edits to
  the new keys; until then the renamed messages show the new built-in text. A server whose language
  files were never edited needs no action.
- 语言键已从中文句子改为 ASCII 键（例如 `essentials.home.set`）。改过本模块 `lang/en.json` 或
  `lang/zh.json` 的运维需要把改动重新套到新键上；在此之前，这些消息显示新的内置文本。从未改过语言文件的服务器无需任何操作。

- This module now declares `api-version: 630` in its `plugin.yml`, so it loads only on UltiTools
  6.3.0 or later. Its `/wild` cooldown uses a framework feature new in 6.3.0; on an older UltiTools
  it would load with no cooldown at all, silently, so it now refuses to load there instead
  (UltiKits/UltiEssentials#27, UltiKits/UltiTools-Reborn#531).
- 本模块的 `plugin.yml` 现在声明 `api-version: 630`，因此只能在 UltiTools 6.3.0 及以上版本加载。其 `/wild` 冷却使用了
  6.3.0 新增的框架功能；在旧版 UltiTools 上它会在没有任何冷却的情况下静默加载，因此现在改为直接拒绝加载
  （UltiKits/UltiEssentials#27、UltiKits/UltiTools-Reborn#531）。

### Removed

- The module's own "disabled" console line on unload (`UltiEssentials disabled!` under
  `language: en`) and its "configuration reloaded" console line on `/ul reload UltiEssentials`
  (`UltiEssentials config reloaded!`), together with the two language keys, present in both
  `lang/en.json` and `lang/zh.json`, that translated them. UltiTools 6.3.0 logs one reload line per
  module (`Module 'UltiEssentials' reloaded.`) (UltiKits/UltiEssentials#23).
- `features.recall.enabled` in `config/essentials.yml`. It never had any effect: this module has no
  `/recall` command. The command is recorded as a feature request, UltiKits/UltiEssentials#53 —
  removing the setting does not reject the feature (UltiKits/UltiEssentials#27).
- If the removed setting is still in your `config/essentials.yml`, which it will be on any server
  that has run an earlier version, start-up and every `/ul reload UltiEssentials` log one warning
  naming the module, the file and the key, and saying where the setting went. Deleting the key
  from the file silences the warning; leaving it there changes nothing else
  (UltiKits/UltiEssentials#27).
- 移除本模块卸载时输出的"UltiEssentials 已禁用！"控制台行、`/ul reload UltiEssentials` 时输出的
  "UltiEssentials 配置已重载！"控制台行，以及 `lang/en.json` 与 `lang/zh.json` 中对应的
  `UltiEssentials 已禁用！`、`UltiEssentials 配置已重载！` 两个语言键。UltiTools 6.3.0 会为每个模块输出
  一行重载日志（UltiKits/UltiEssentials#23）。
- 移除 `config/essentials.yml` 中的 `features.recall.enabled`。该设置从未生效：本模块没有 `/recall` 命令。
  该命令已作为功能请求记录在 UltiKits/UltiEssentials#53——删除设置并不代表否决该功能（UltiKits/UltiEssentials#27）。
- 若上述已删除的设置仍保留在你的 `config/essentials.yml` 中（运行过旧版本的服务器都会如此），启动时以及每次
  `/ul reload UltiEssentials` 都会输出一条警告，指明模块、文件和键，并说明该设置的去向。从文件中删除该键
  即可消除警告；保留它不会产生其他任何影响（UltiKits/UltiEssentials#27）。
