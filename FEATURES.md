# UltiEssentials — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultiessentials` here, `ultichat`, `ultitools`, and `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug,
  so lowercasing it would make it un-greppable against its own source line. An ID changes only
  when the feature's identity changes, never on rewording. IDs are unique within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `gui` rows (no GUI page class — confirmed absent from Phase 9's
  `gui-exclusions/` register, see the `## Configuration` section note below) and no `placeholder`
  rows (`ScoreboardService`/`NamePrefixService` consume PlaceholderAPI variables via
  `PlaceholderAPI#setPlaceholders`, they do not register their own expansion) and no `gate` rows
  (0 `@ConditionalOnConfig` sites) — all three Kinds stay in the vocabulary for cross-repository
  consistency even though none appears below. This module's one `scheduled` row is NOT backed by
  the framework's `@Scheduled` annotation (0 sites) — see that row's own note. The five rows under
  `## Lifecycle` are `event` rows with no `@EventHandler` site behind them, and
  `ultiessentials.lock.protect-whole-container` is an `event` row whose behaviour runs inside every
  `ChestLockListener` handler rather than in one of its own, so the 21 `@EventHandler` sites in the
  positive control below match the other 21 `event` rows, not all 27: `/ul reload`, an unload and
  start-up are framework-invoked lifecycle steps, not commands this repository maps or config reads,
  so `event` is the closest-fitting Kind.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for, not
  from whether it carries a permission string — most command executors in this repository carry
  one, so judging by the string alone would make nearly everything `admin`. This module's core
  identity is player self-service (home/warp/back/spawn/tpa/heal/feed/fly/speed and their kin), so
  those stay `player` even though every one of them is permission-gated; inspection tools that
  read another player's private inventory contents (`/invsee`, `/endersee`, `/armorsee`) and
  moderation tools (`/ban`, `/unban`, `/banlist`, `/tempban`, `/wl`) are `admin` because the
  feature itself is staff oversight, not self-service, regardless of how permissively an operator
  chooses to grant the node.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row; it is a property, not a tier. `n/a` is for every other
  Kind (`config`, `event`, `gate`, `gui`, `persistence`, `scheduled`, `placeholder`) — the concept
  of "who this targets" does not apply to a config key or a background task the way it applies to
  a command. No class in this repository declares `@CmdTarget(CONSOLE)`; every command row below
  is `player` (33 classes) or `both` (5 classes: `BanCommand`, `UnbanCommand`, `BanListCommand`,
  `TempBanCommand`, `WhitelistCommand` — all moderation commands, deliberately console-runnable).
- **Permission:** the literal node string, `none`, or `n/a`, each optionally suffixed with the
  literal text `(requireOp=true)` (preceded by one space) when the row's class-level
  `@CmdExecutor` carries that flag — the suffix augments whichever of the three base values
  applies; it is not a fourth value. `requireOp` is never set anywhere in this repository's
  `commands/` package (0 occurrences), so no row below carries the suffix. `n/a` is for every Kind
  that is not `command`.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included: all 77 `config` rows below cite the reading member,
  not merely the field declaration on the `@ConfigEntity` class (which only binds the key).
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text. A
  hazard noticed while reading becomes a negative checklist row, not a note here. Where a feature's
  actual runtime behaviour genuinely diverges from what its own comment, its `@Range`/`@NotEmpty`
  validation, or the public doc page describes it as doing (a dead key, an unreachable code path, a
  permission gap), that fact is itself part of "what the feature does" and is stated here as a
  plain, sourced observation, with the filed issue number, never as advice on how to fix it.

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This form defeats three measured traps, each of which produces a wrong-but-plausible number
rather than an error:

1. **Multi-root repositories** — UltiBot's sources live under `ultibot-api/`, `ultibot-core/` and
   `ultibot-v1_21_R1/`, so a naive `<repo>/src/main/java` glob returns 0 for it, silently. This
   module is a single-root Maven project (`src/main/java` only), so this trap does not apply to
   it, but the robust `find` form is used regardless — the same command must work unmodified
   across all 18 repositories.
2. **Git worktrees and build output** — UltiEconomy carries `.worktrees/economy-v2/src/main/java`,
   so a `find` without the `-not -path` exclusions above reports 48 `@CmdMapping` sites where the
   real number is 24. This module carries no worktree directory.
3. **Javadoc and string literals** — requiring the annotation to start its own line (the
   `^[[:space:]]*@` anchor) is what defeats a javadoc mention or a warning-message string literal
   that merely contains the annotation's name as text. This module's naive (unanchored) and
   line-start counts are identical for every annotation kind measured below.

**Positive control:** the line-start form returns `@CmdExecutor` = 38 (classes), `@CmdMapping` =
57 (formats), `@EventListener` = 12 (classes — 11 in `listener/`, 1 in `commands/BackCommand.java`
which is simultaneously a command executor and an event listener), `@EventHandler` = 21 (handler
methods across those 12 classes), `@Scheduled` = 0, `@ConditionalOnConfig` = 0, `@ConfigEntity` = 5
(classes), `@ConfigEntry` = 77, `@Table` = 4 (`HomeData`, `WarpData`, `BanData`, `ChestLockData`) —
confirmed by reading `WhitelistCommand.java` directly (6 `@CmdMapping` sites: `add <player>`,
`remove <player>`, `list`, `on`, `off`, `status` — the largest single-class mapping count in this
module) and `ChestLockListener.java` (7 `@EventHandler` sites: `onPlayerInteract`, `onBlockBreak`,
`onEntityExplode`, `onBlockExplode`, `onPistonExtend`, `onPistonRetract`, `onInventoryMove` — the
largest single-class handler count). `ultiessentials.whitelist.add` (`WhitelistCommand#add`) is
this module's standing positive control for the command-row count: 57 `@CmdMapping` sites and 57
`command`-Kind rows below, checked by identity, not merely by count — see the acceptance-criteria
verify in plan 10-08 Task 1, which fails a table that reports 38 (classes) instead of 57 (formats).

## Teleportation & World Presence

`SpawnCommand`/`SetSpawnCommand` (class permission `ultiessentials.spawn.teleport` /
`ultiessentials.spawn.set`), `LobbyCommand`/`SetLobbyCommand` (`ultiessentials.lobby.teleport` /
`ultiessentials.lobby.set`), `WildCommand` (`ultiessentials.wild`, `@CmdCD(60)` — a fixed
60-second per-player cooldown enforced by the framework's own cooldown validator, with no setting:
the never-read `features.wild.cooldown` key was removed in 6.3.0 (UltiKits/UltiEssentials#27), and
a configurable cooldown needs UltiKits/UltiTools-Reborn#531), and
`BackCommand` (`ultiessentials.back`, simultaneously a command executor and, via `@EventListener`,
an event listener). `RespawnListener` and `JoinQuitListener` back the automatic spawn-teleport
behaviours.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.spawn.teleport | Teleport the sender to the configured spawn location, refusing if the spawn world is not loaded | command | `/spawn` | ultiessentials.spawn.teleport | player | player | brief | SpawnCommand#teleportToSpawn |
| ultiessentials.spawn.set | Set the server spawn location to the sender's current position and persist it to `config/spawn.yml` | command | `/setspawn` | ultiessentials.spawn.set | player | admin | brief | SetSpawnCommand#setSpawn |
| ultiessentials.spawn.on-respawn | On player respawn, override the vanilla respawn location with the configured spawn point (gated by both `features.spawn.enabled` and `spawn.teleport-on-respawn`) | event | die and respawn as any player | n/a | n/a | player | brief | RespawnListener#onPlayerRespawn |
| ultiessentials.spawn.on-first-join | On a player's genuinely first join (`Player#hasPlayedBefore()` false), teleport them to the configured spawn point (gated by both `features.spawn.enabled` and `spawn.teleport-on-first-join`) | event | join the server for the first time ever | n/a | n/a | player | brief | JoinQuitListener#onPlayerJoin |
| ultiessentials.lobby.teleport | Teleport the sender to the configured lobby/hub location, refusing if the lobby world is not loaded | command | `/lobby` (alias `/hub`) | ultiessentials.lobby.teleport | player | player | brief | LobbyCommand#teleportToLobby |
| ultiessentials.lobby.set | Set the server lobby/hub location to the sender's current position and persist it to `config/lobby.yml` | command | `/setlobby` (alias `/sethub`) | ultiessentials.lobby.set | player | admin | brief | SetLobbyCommand#setLobby |
| ultiessentials.wild.teleport | Randomly teleport the sender within the configured min/max range of their current position, trying up to 10 candidate points and rejecting any that land on a solid ceiling/floor mismatch or on lava/water; refuses outright if `wild.min-range` >= `wild.max-range` | command | `/wild` (alias `/rtp`) | ultiessentials.wild | player | player | brief | WildCommand#wildTeleport |
| ultiessentials.back.teleport | Teleport the sender to the location recorded before their most recent COMMAND- or PLUGIN-caused teleport; refuses if no location is recorded for them yet | command | `/back` | ultiessentials.back | player | player | brief | BackCommand#back |
| ultiessentials.back.track | Record the sender's pre-teleport location on every `PlayerTeleportEvent` whose cause is `COMMAND` or `PLUGIN` (MONITOR priority, `ignoreCancelled = true`), so a later `/back` returns to it; a teleport of any other cause (a portal, an ender pearl, a plugin using a different cause) is not tracked | event | run any command-triggered teleport, or trigger one of this plugin's own plugin-caused teleports (`/home`, `/warp`, `/spawn`, `/lobby`, `/wild`, an accepted `/tpa`) | n/a | n/a | player | brief | BackCommand#onPlayerTeleport |
| ultiessentials.back.cleanup-on-quit | Remove the quitting player's recorded back-location from the in-memory map on quit; `PlayerQuitListener#onPlayerQuit` (see `## Player Status & Utility`) performs the identical removal via `BackCommand.removePlayer(uuid)` on the SAME event, so this specific cleanup runs twice per quit — redundant but harmless, since removing an already-absent map key is a no-op | event | quit the server as any player who has a recorded back-location | n/a | n/a | internal | none | BackCommand#onPlayerQuit |

## Homes

`HomeCommand` (`ultiessentials.home`), `SetHomeCommand` (`ultiessentials.sethome`),
`DelHomeCommand` (`ultiessentials.delhome`), `HomesCommand` (`ultiessentials.homes`) — all four
gated by `features.home.enabled`, backed by `HomeService` and the persisted `HomeData` entity (see
`## Data Persistence`). A home name is lowercased and trimmed before storage; a name normalizing
to empty or over 32 characters is rejected. The per-player home limit is resolved by walking
`ultiessentials.home.max.<100..1>` permission nodes downward, then `ultiessentials.home.unlimited`,
then falling back to `home.default-max-homes` — the highest-numbered `max.<n>` node a player holds
wins, not the sum of any nodes they hold.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.home.teleport-default | Teleport to the home named `home`, falling back to the player's first home (by database order) if no home is named `home`; refuses if the player has no homes at all | command | `/home` (alias `/h`) | ultiessentials.home | player | player | brief | HomeCommand#teleportToDefaultHome |
| ultiessentials.home.teleport-named | Teleport to a specific named home, with warmup (`home.teleport-warmup`, skippable via `ultiessentials.home.nowarmup`) and cancel-on-move (`home.cancel-on-move`) support inherited from `TeleportService` | command | `/home <name>` | ultiessentials.home | player | player | brief | HomeCommand#teleportToHome |
| ultiessentials.home.set-default | Create or update the home named `home` at the sender's current position | command | `/sethome` (alias `/sh`) | ultiessentials.sethome | player | player | brief | SetHomeCommand#setDefaultHome |
| ultiessentials.home.set-named.outcome-verified | Re-running `/sethome <name>` on an existing home re-queries the stored record afterwards and compares the location it would teleport to with the sender's position — the constructed location, so facing direction counts too, reporting a distinct failure when the move did not reach the store instead of confirming a move that would still teleport the player to the old location | command | `/sethome <existing name>` | ultiessentials.sethome | player | player | brief | HomeService#setHome |
| ultiessentials.home.set-named | Create or update a specific named home at the sender's current position, refusing if the sender is at their per-permission home limit and the name does not already exist | command | `/sethome <name>` | ultiessentials.sethome | player | player | brief | SetHomeCommand#setHome |
| ultiessentials.home.delete | Delete a home by name, reporting success only once the record is confirmed gone from the store (re-queried after the delete, because the framework's `delById` returns no affected-row count) and the not-found message otherwise. Three outcomes, not two: a record that is still there after the attempt is reported differently from one that was never there, because "no such record" tells the operator to stop looking while the record is still in force. | command | `/delhome <name>` (aliases `/deletehome`, `/rmhome`) | ultiessentials.delhome | player | player | brief | DelHomeCommand#deleteHome |
| ultiessentials.home.list | List every home the sender owns, with world and rounded (int-truncated) coordinates, header showing the current count against the resolved limit | command | `/homes` (alias `/homelist`) | ultiessentials.homes | player | player | brief | HomesCommand#listHomes |

## Warps

`WarpCommand` (`ultiessentials.warp.use`), `SetWarpCommand` (`ultiessentials.warp.set`),
`DelWarpCommand` (`ultiessentials.warp.delete`), `WarpsCommand` (`ultiessentials.warp.list`) —
gated by `features.warp.enabled`, backed by `WarpService` and the persisted `WarpData` entity (see
`## Data Persistence`). A warp is server-wide (not per-player) and may carry an optional per-warp
access permission node, checked separately from the `warp.use` command permission itself. Warp
teleport's cancel-on-move behaviour deliberately reuses `home.cancel-on-move` — `WarpService`
carries no `warp.cancel-on-move` key of its own (`WarpService#teleportToWarp`'s own inline comment
states this is intentional reuse, not an oversight).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.warp.teleport | Teleport to a named warp, subject to the warp's own optional access permission, with warmup (`warp.teleport-warmup`, skippable via `ultiessentials.warp.nowarmup`) and cancel-on-move (reusing `home.cancel-on-move`, see section note) | command | `/warp <name>` (alias `/w`) | ultiessentials.warp.use | player | player | brief | WarpCommand#warp |
| ultiessentials.warp.create | Create a warp at the sender's position with no access restriction, refusing if the (lowercased, trimmed) name is empty/over 32 characters or already exists | command | `/setwarp <name>` (aliases `/swarp`, `/addwarp`) | ultiessentials.warp.set | player | admin | brief | SetWarpCommand#setWarp |
| ultiessentials.warp.create-with-permission | Create a warp at the sender's position gated behind the given permission node string, which is stored verbatim and checked later by `WarpService#canAccess` — no validation that the string is a syntactically well-formed or already-granted permission node | command | `/setwarp <name> <permission>` | ultiessentials.warp.set | player | admin | brief | SetWarpCommand#setWarpWithPermission |
| ultiessentials.warp.delete | Delete a warp by name (server-wide — any holder of `warp.delete` may delete any warp, not only ones they created), reporting success only once the record is confirmed gone from the store (re-queried after the delete, because the framework's `delById` returns no affected-row count) and the not-found message otherwise. Three outcomes, not two: a record that is still there after the attempt is reported differently from one that was never there, because "no such record" tells the operator to stop looking while the record is still in force. | command | `/delwarp <name>` (aliases `/deletewarp`, `/rmwarp`, `/removewarp`) | ultiessentials.warp.delete | player | admin | brief | DelWarpCommand#delWarp |
| ultiessentials.warp.list | List every warp the sender can access (owning no access permission or holding the warp's own), with world, one-decimal coordinates, and a hardcoded-Chinese, no-i18n-call marker on permission-gated entries (read `WarpsCommand.java:51` for the exact characters) that renders unchanged regardless of `language: en` (see `ultiessentials.i18n-coverage` under `## Data Persistence` for the module-wide pattern this instance belongs to) | command | `/warps` (aliases `/warplist`, `/listwarp`) | ultiessentials.warp.list | player | player | brief | WarpsCommand#listWarps |

## TPA (Teleport Ask)

`TpaCommand` (`ultiessentials.tpa`), `TpaHereCommand` (`ultiessentials.tpahere`),
`TpAcceptCommand` (`ultiessentials.tpaccept`), `TpDenyCommand` (`ultiessentials.tpdeny`) — gated
by `features.tpa.enabled`, backed by `TpaService`. Requests are held in memory only, one pending
request per target at a time, auto-expiring after `tpa.timeout` seconds via a `BukkitRunnable`
(not the framework's `@Scheduled`). `TpaService#onPlayerQuit(UUID)` exists to cancel a quitting
player's requests (as sender or target) immediately, but is never called from any listener in this
repository — grep confirms zero call sites — so a request involving a player who quits is instead
cleaned up only when the existing timeout task fires, up to `tpa.timeout` seconds later, not
instantly on quit.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.tpa.send | Send a request asking to teleport TO the named online target; refuses on self-target, cross-world (unless `tpa.allow-cross-world`), the sender's own cooldown, or the target already holding a pending request from anyone | command | `/tpa <player>` | ultiessentials.tpa | player | player | brief | TpaCommand#sendTpa |
| ultiessentials.tpa.send-here | Send a request asking the named online target to teleport TO the sender; same refusal conditions as `ultiessentials.tpa.send` | command | `/tpahere <player>` (alias `/tphere`) | ultiessentials.tpahere | player | player | brief | TpaHereCommand#sendTpaHere |
| ultiessentials.tpa.accept | Accept the sender's own pending incoming request, teleporting the original requester (for `/tpa`) or the accepter (for `/tpahere`) accordingly, then clearing the request | command | `/tpaccept` (aliases `/tpyes`, `/tpok`) | ultiessentials.tpaccept | player | player | brief | TpAcceptCommand#acceptTpa |
| ultiessentials.tpa.deny | Deny the sender's own pending incoming request and notify the original requester if still online | command | `/tpdeny` (aliases `/tpno`, `/tpcancel`) | ultiessentials.tpdeny | player | player | brief | TpDenyCommand#denyTpa |

## Player Status & Utility

`HealCommand`/`FeedCommand` (both class-permission `ultiessentials.heal.self`, gated by
`features.heal.enabled`), `SpeedCommand` (`ultiessentials.speed`, `features.speed.enabled`),
`FlyCommand` (`ultiessentials.fly`, `features.fly.enabled`), `HideCommand`
(`ultiessentials.hide`, `features.hide.enabled`), `GameModeCommand` + its three shortcuts
(`ultiessentials.gamemode.self`, `features.gamemode.enabled`), `ScoreboardCommand`
(`ultiessentials.scoreboard`) plus `ScoreboardListener`, `CommandAliasListener`
(`features.commandalias.enabled`), and the two quit-cleanup listeners.

**`FeedCommand` and `HealCommand` share the identical permission pair** — both class-level
`ultiessentials.heal.self` and both other-player mappings `ultiessentials.heal.other` — there is
no `ultiessentials.feed.*` node anywhere in the source. Granting `ultiessentials.heal.other`
grants feed-others too, and there is no way to separate the two.

**`/fly <player>` requires `ultiessentials.fly.other`** — its `@CmdMapping` declares that
override, so a player holding only the class-level `ultiessentials.fly` that gates toggling their
own flight is refused. This matches `HealCommand`/`FeedCommand` (`ultiessentials.heal.other`) and
`GameModeCommand` (`ultiessentials.gamemode.other`), the module's other self/other pairs. Until
UltiKits/UltiEssentials#25 was fixed the mapping declared no override and inherited the self node,
so ordinary self-fly access carried flight control over every online player; `OtherPlayerPermissionTest`
now holds all four pairs to the rule.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.heal.self | Restore the sender's health to their current max-health attribute value | command | `/heal` | ultiessentials.heal.self | player | player | brief | HealCommand#healSelf |
| ultiessentials.heal.other | Restore a named online target's health to their max-health attribute value | command | `/heal <player>` | ultiessentials.heal.other | player | admin | brief | HealCommand#healOther |
| ultiessentials.feed.self | Restore the sender's food level to 20 and saturation to 20.0 | command | `/feed` | ultiessentials.heal.self | player | player | brief | FeedCommand#feedSelf |
| ultiessentials.feed.other | Restore a named online target's food level to 20 and saturation to 20.0 | command | `/feed <player>` | ultiessentials.heal.other | player | admin | brief | FeedCommand#feedOther |
| ultiessentials.speed.set | Set the sender's walk speed (`0.2 * value`, capped at 1.0) and fly speed (`0.1 * value`, capped at 1.0); `0` resets to the platform default instead of setting a zero speed; refuses any value outside `0..speed.max-speed` | command | `/speed <value>` | ultiessentials.speed | player | player | brief | SpeedCommand#setSpeed |
| ultiessentials.speed.reset | Reset the sender's walk and fly speed to the platform default (0.2 / 0.1) | command | `/speed reset` | ultiessentials.speed | player | player | brief | SpeedCommand#resetSpeed |
| ultiessentials.fly.toggle-self | Toggle the sender's own flight allowance; disabling also forces `setFlying(false)` so the sender does not remain airborne with flight revoked | command | `/fly` | ultiessentials.fly | player | player | brief | FlyCommand#toggleFly |
| ultiessentials.fly.toggle-other | Toggle a named online target's flight allowance — gated by its own elevated node, which a holder of the self-flight node alone does not have (fixed in UltiKits/UltiEssentials#25) | command | `/fly <player>` | ultiessentials.fly.other | player | admin | brief | FlyCommand#toggleFlyOther |
| ultiessentials.hide.toggle | Toggle the sender's own vanish: on enable, hides the sender from every online player lacking `ultiessentials.hide.see`; on disable, re-shows the sender to everyone. State is a static in-memory set, not persisted (see `## Data Persistence`) | command | `/hide` (alias `/vanish`) | ultiessentials.hide | player | admin | brief | HideCommand#toggleHide |
| ultiessentials.gamemode.set-self | Set the sender's own game mode by numeric (`0-3`), single-letter, or full-name token | command | `/gm <mode>` | ultiessentials.gamemode.self | player | player | brief | GameModeCommand#setGameMode |
| ultiessentials.gamemode.set-other | Set a named online target's game mode, notifying both sender and target | command | `/gm <mode> <player>` | ultiessentials.gamemode.other | player | admin | brief | GameModeCommand#setGameModeOther |
| ultiessentials.gamemode.shortcut-creative | Shortcut to set the sender's own game mode to CREATIVE | command | `/gmc` | ultiessentials.gamemode.self | player | player | none | GmCreativeCommand#creative |
| ultiessentials.gamemode.shortcut-spectator | Shortcut to set the sender's own game mode to SPECTATOR | command | `/gmsp` | ultiessentials.gamemode.self | player | player | none | GmSpectatorCommand#spectator |
| ultiessentials.gamemode.shortcut-survival | Shortcut to set the sender's own game mode to SURVIVAL | command | `/gms` | ultiessentials.gamemode.self | player | player | none | GmSurvivalCommand#survival |
| ultiessentials.scoreboard.toggle | Toggle the sender's own sidebar scoreboard on or off; turning it off returns the sender to the server's main scoreboard | command | `/scoreboard` (alias `/sb`) | ultiessentials.scoreboard | player | player | brief | ScoreboardCommand#toggle |
| ultiessentials.scoreboard.enable | Explicitly enable the sender's sidebar scoreboard (no-ops with a distinct message if already on) | command | `/scoreboard on` | ultiessentials.scoreboard | player | player | none | ScoreboardCommand#enable |
| ultiessentials.scoreboard.disable | Explicitly disable the sender's sidebar scoreboard and return the sender to the server's main scoreboard, where name-prefix teams are (no-ops with a distinct message if already off) | command | `/scoreboard off` | ultiessentials.scoreboard | player | player | none | ScoreboardCommand#disable |
| ultiessentials.scoreboard.auto-enable-on-join | 1 second after join (`runTaskLater(20L)`, re-checking the player is still online), auto-enable the sidebar scoreboard when `scoreboard.auto-enable` is true | event | join the server with `features.scoreboard.enabled` and `scoreboard.auto-enable` both true | n/a | n/a | player | brief | ScoreboardListener#onPlayerJoin |
| ultiessentials.scoreboard.disable-on-quit | Remove the quitting player from the enabled-scoreboard set unconditionally (independent of whether `scoreboard.enabled` is currently true) | event | quit the server with an active scoreboard | n/a | n/a | internal | none | ScoreboardListener#onPlayerQuit |
| ultiessentials.commandalias.rewrite | Rewrite the leading command token of a chat-typed command per the `commandalias.aliases` map (e.g. `/gmc` -> `/gamemode creative`) before Bukkit dispatches it, evaluated at `LOWEST` priority | event | type a command whose first token matches a configured alias key | n/a | n/a | player | brief | CommandAliasListener#onPlayerCommand |
| ultiessentials.cleanup.on-quit | On quit, clear the quitting player's `/back` location and vanish (`/hide`) state from their respective static in-memory maps | event | quit the server as any player holding either state | n/a | n/a | internal | none | PlayerQuitListener#onPlayerQuit |

## Player Inspection

`ArmorseeCommand` (`ultiessentials.armorsee`), `EnderseeCommand` (`ultiessentials.endersee`),
`InvseeCommand` (`ultiessentials.invsee`) — all three gated by `features.invsee.enabled` (a single
shared toggle covers all three inspection commands, not one key each), staff tools for viewing
another player's inventory contents live.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.invsee.view | Open a live, editable view of a named online target's main inventory in the sender's own inventory screen; refuses on a missing/offline target | command | `/invsee <player>` | ultiessentials.invsee | player | admin | brief | InvseeCommand#invsee |
| ultiessentials.endersee.view | Open a live, editable view of a named online target's ender chest in the sender's own inventory screen; refuses on a missing/offline target | command | `/endersee <player>` (alias `/echest`) | ultiessentials.endersee | player | admin | brief | EnderseeCommand#endersee |
| ultiessentials.armorsee.view | Open a read-and-drop 9-slot temporary inventory showing a named online target's four armor slots (reversed to helmet-first display order) and off-hand item; refuses on a missing/offline target ONLY -- UNLIKE `InvseeCommand`, this class has no self-target check at all, so targeting oneself opens the same temporary view of one's own gear rather than being refused | command | `/armorsee <player>` | ultiessentials.armorsee | player | admin | brief | ArmorseeCommand#armorsee |

## Moderation

`BanCommand`/`UnbanCommand`/`BanListCommand`/`TempBanCommand` (gated by `features.ban.enabled`,
backed by `BanService` and the persisted `BanData` entity — see `## Data Persistence`) and
`WhitelistCommand` (gated by `features.whitelist.enabled`, backed directly by Bukkit's own
`OfflinePlayer#setWhitelisted`/`Bukkit#setWhitelist`, no plugin-owned persistence of its own).
`BanListener` enforces bans at login. `banPlayer`/`unbanPlayerByName` never touch the server's own
vanilla ban list — a name unbanned by this plugin's commands may still be banned by a separate
vanilla `/ban`, and `UnbanCommand`/`BanService#isBannedInServerBanList` exist specifically to
report that distinction rather than a false "not banned anywhere".

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.ban.ban | Permanently ban a player (online or previously-seen offline) with a fixed, hardcoded-Chinese default reason (no i18n call — read `BanCommand.java:38` for the exact characters; it reads roughly "no reason given"), kicking them immediately if online, and announcing the ban — to the whole server, or to the issuer alone when `features.ban.broadcast-ban` is `false` | command | `/ban <player>` (alias `/eban`) | ultiessentials.ban | both | admin | brief | BanCommand#ban |
| ultiessentials.ban.ban-with-reason | Permanently ban a player with a given reason, kicking them immediately if online, and announcing the ban and reason — to the whole server, or to the issuer alone when `features.ban.broadcast-ban` is `false` | command | `/ban <player> <reason>` | ultiessentials.ban | both | admin | brief | BanCommand#banWithReason |
| ultiessentials.ban.tempban | Temporarily ban a player for a parsed duration (`1d`/`2h`/`30m`/`1w`, combinable e.g. `1d12h30m`) with the same fixed, hardcoded-Chinese default reason as `ultiessentials.ban.ban` (see that row), refusing on an unparseable duration, and announcing the ban as `ultiessentials.ban.ban` does | command | `/tempban <player> <duration>` | ultiessentials.ban.temp | both | admin | brief | TempBanCommand#tempban |
| ultiessentials.ban.tempban-with-reason | Temporarily ban a player for a parsed duration with a given reason, announcing the ban and reason as `ultiessentials.ban.ban-with-reason` does | command | `/tempban <player> <duration> <reason>` | ultiessentials.ban.temp | both | admin | brief | TempBanCommand#tempbanWithReason |
| ultiessentials.ban.unban | Unban a player by name (this plugin's own record only), reporting one of four distinct outcomes depending on whether the plugin's own record and/or the server's own vanilla ban list currently ban the name (see section note); the two success outcomes are reported only once no active record remains for the name (re-queried after the deactivation, because the framework's `update(T)` returns no affected-row count), so the unban broadcast (sent on the plain-success outcome only, and only while `features.ban.broadcast-unban` is `true`) cannot fire for a ban that is still in force; a ban record that survives the attempt is reported as a failure to lift it, never as a name that was never banned | command | `/unban <player>` (alias `/pardon`) | ultiessentials.unban | both | admin | brief | UnbanCommand#unban |
| ultiessentials.banlist.list | List active bans, page 1, 10 per page, each entry showing name, permanent-or-remaining-time, reason, and operator | command | `/banlist` (alias `/bans`) | ultiessentials.banlist | both | admin | brief | BanListCommand#banlist |
| ultiessentials.banlist.list-page | List active bans on a specific page (clamped to `1..totalPages`) | command | `/banlist <page>` | ultiessentials.banlist | both | admin | none | BanListCommand#banlistPage |
| ultiessentials.ban.login-check | Reject a joining player's login (`KICK_BANNED`) if either their UUID or their connecting IP address currently has an active, unexpired ban record | event | attempt to join while an active UUID or IP ban exists | n/a | n/a | admin | detailed | BanListener#onPlayerLogin |
| ultiessentials.whitelist.add | Add a named player (does not need to be online) to the whitelist, rejecting an empty or over-16-character name before ever calling the platform's offline-player resolver | command | `/wl add <player>` | ultiessentials.whitelist.manage | both | admin | brief | WhitelistCommand#add |
| ultiessentials.whitelist.remove | Remove a named player from the whitelist by matching case-insensitively against the CURRENTLY whitelisted set (not by resolving through the platform's offline-player lookup), so a malformed or historical over-length entry can still be removed | command | `/wl remove <player>` | ultiessentials.whitelist.manage | both | admin | brief | WhitelistCommand#remove |
| ultiessentials.whitelist.list | List every currently whitelisted player name, comma-separated | command | `/wl list` | ultiessentials.whitelist.manage | both | admin | none | WhitelistCommand#list |
| ultiessentials.whitelist.enable | Turn on server whitelist enforcement | command | `/wl on` | ultiessentials.whitelist.manage | both | admin | brief | WhitelistCommand#enable |
| ultiessentials.whitelist.disable | Turn off server whitelist enforcement | command | `/wl off` | ultiessentials.whitelist.manage | both | admin | brief | WhitelistCommand#disable |
| ultiessentials.whitelist.status | Report whether whitelist enforcement is on/off and the current whitelisted-player count | command | `/wl status` | ultiessentials.whitelist.manage | both | admin | none | WhitelistCommand#status |

## Container Locking

`LockCommand`/`UnlockCommand` (both `ultiessentials.lock` — the SAME node locks and unlocks one's
own container; `ultiessentials.lock.admin` separately grants breaking/unlocking others' locks),
`ChestLockListener` (gated by `features.chestlock.enabled`), backed by `ChestLockService` and the
persisted `ChestLockData` entity (see `## Data Persistence`). Lockable block types: chests, trapped
chests, barrels, all 17 shulker box colors, furnaces, blast furnaces, smokers, hoppers, droppers,
dispensers, and brewing stands. Locking or unlocking a double chest automatically locks/unlocks
its other half too. **Every lock question this module asks is keyed on the container, not on the
single block** (`ChestLockService#locksProtecting(Block)` and `#isContainerLocked`): a double chest
is one shared inventory behind two blocks, so a record on either half protects both, and a half
holding no record of its own — legacy data written before double-chest propagation existed, or a
lock whose second insert never landed — is protected by the other half's record. The one deliberate
exception is removing the record of a block that has just been destroyed, which really is about that
one block (`ChestLockService#onBlockBreak(Location)`): the surviving half keeps its own record. **`UnlockCommand`'s two mappings never check `features.chestlock.enabled` at
all** (`ChestLockService#unlockBlock`/`#getLock` have no such check, unlike `#lockBlock`) — a
container can be unlocked, and its lock info inspected, even while the whole feature is configured
disabled; only locking a NEW container is actually gated.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.lock.lock | Lock the container the sender is looking at (within 5 blocks), refusing on a non-lockable block type, a container ANY record already protects on behalf of another player, or the feature being disabled. The already-locked test is container-scoped, so a player cannot put their own record on the unrecorded half of a container someone else holds; when every protecting record is already the sender's own, `/lock` on the half that has none writes the missing record and reports success, which is the repair route for a lock whose second insert never landed | command | `/lock` (alias `/l`) | ultiessentials.lock | player | player | brief | LockCommand#lock, ChestLockService#lockBlock |
| ultiessentials.lock.protect-whole-container | A lock record on either half of a double chest protects the shared inventory behind both, on EVERY path that can reach its contents — opening, breaking, entity and block explosions, piston movement, and hopper extraction — because each is keyed on the container rather than on the block acted upon. A stranger is refused on both halves even when only one half holds a record. Breaking was the last path still keyed on the single block: it let a stranger destroy the unrecorded half and collect the drops, which is strictly worse than the interact bypass because the items reach the floor where anyone may take them | event | act on either half of a double chest only one half of which is recorded as locked | n/a | n/a | internal | none | ChestLockService#locksProtecting, #isContainerLocked, #canAccess(Block, Player), every @EventHandler in ChestLockListener |
| ultiessentials.lock.unlock | Unlock the container the sender is looking at, refusing if no record protects the container, or if ANY record protecting it belongs to someone else and the sender lacks `ultiessentials.lock.admin` — the permission test covers every record that is about to be removed, so owning one half is not authority to remove the other half's record, and `/unlock` on the unrecorded half of a partly-recorded container works rather than reporting "not locked" — NOT gated by `features.chestlock.enabled` (see section note); reports success only once no lock record remains stored for that location (re-queried after the delete, because the framework's `delById` returns no affected-row count), and keeps the container listed as locked otherwise so the cached state cannot disagree with the store. For a double chest every record protecting the container is removed inside one transaction and the re-query runs inside it too, so either both halves are unlocked or neither is and nothing is dropped from the cache | command | `/unlock` (alias `/ul` — distinct from the framework's own `/ul` core command; Bukkit's own command-conflict resolution decides which registration wins if both are present) | ultiessentials.lock | player | player | brief | UnlockCommand#unlock |
| ultiessentials.lock.info | Report whether the looked-at CONTAINER is locked and, if so, by whom and at what coordinates — every record protecting it is printed, so a partly-recorded double chest reports as locked from either half, and legacy data holding one half per owner names both — NOT gated by `features.chestlock.enabled` (see section note) | command | `/unlock info` | ultiessentials.lock | player | player | none | UnlockCommand#info |
| ultiessentials.chestlock.protect-interact | Cancel a right-click interaction with a locked container by anyone who is neither its owner nor (when `chestlock.admin-bypass` is true) an `ultiessentials.lock.admin` holder, and message the interacting player naming the owner | event | right-click a locked container you do not own and cannot bypass | n/a | n/a | internal | none | ChestLockListener#onPlayerInteract |
| ultiessentials.chestlock.protect-break | Refuse a break with a wording distinct from the open-refusal one, naming the owner and saying the container cannot be broken. TWO handlers can produce that refusal and both do: an ordinary mining attempt never reaches `onBlockBreak` at all — a left click IS the start of digging, so `onPlayerInteract` cancels it first and speaks the break wording from there, while `onBlockBreak` refuses a break driven through the API, where no `PlayerInteractEvent` is raised. Both call one shared builder, so the two routes cannot drift. Gate 3 round 4 measured the split: a real client mining a locked chest produced one `LEFT_CLICK_BLOCK cancelled=true` and no `BlockBreakEvent`, and got the open wording; the same break through `Player#breakBlock` got the break wording. The interact handler's cancellation is unconditional and unchanged by this — the wording is chosen after it, never instead of it. Cancel breaking a container protected by ANY lock record — including the half of a double chest that holds no record of its own — by anyone who is neither that record's owner nor an `ultiessentials.lock.admin` holder (this check does NOT consult `chestlock.admin-bypass` — admin bypass for interaction and admin bypass for breaking are two independently-coded checks that happen to require the same permission node); removes ONLY the broken block's own record when a permitted break succeeds, since the other half is still standing and still needs its own — confirmed against the store before the cached entry is dropped, so a record that could not be removed leaves the location still reported as locked rather than appearing unlocked until the next restart | event | attempt to break a locked container | n/a | n/a | internal | none | ChestLockListener#onBlockBreak |
| ultiessentials.chestlock.protect-explode-entity | Remove from an entity explosion's (TNT, creeper, etc.) block-destruction list any block belonging to a locked container, including the unrecorded half of a double chest — an explosion drops that half's contents exactly as a break does | event | detonate an entity explosion near a locked container | n/a | n/a | internal | none | ChestLockListener#onEntityExplode |
| ultiessentials.chestlock.protect-explode-block | Remove from a block explosion's (bed/respawn-anchor misuse, etc.) block-destruction list any block belonging to a locked container, container-scoped as for entity explosions | event | trigger a block explosion near a locked container | n/a | n/a | internal | none | ChestLockListener#onBlockExplode |
| ultiessentials.chestlock.protect-piston-extend | Cancel a piston-extend event outright if any block it would move belongs to a locked container (container-scoped for consistency). **No real-machine row exercises this guard, because vanilla forecloses the path** — measured in `paper-1.21.11-132.jar`: `PistonBaseBlock.isPushable` ends `return !state.hasBlockEntity()`, `hasBlockEntity()` is `getBlock() instanceof EntityBlock`, and every block class behind `ChestLockService.LOCKABLE_BLOCKS` is an `EntityBlock`, so no lockable container can ever be a member of `getBlocks()`. The guard is kept anyway: "no known route" was equally true of breaking until gate 2 round 3 looked for one, and the cost of the lookup is one map probe. Its behaviour is covered by `ChestLockListenerTest$PistonExtendTests`; the checklist row of the same ID verifies only the platform rule this unreachability rests on, so that the rule changing is noticed | event | extend a piston toward a locked container | n/a | n/a | internal | none | ChestLockListener#onPistonExtend, ChestLockListenerTest$PistonExtendTests |
| ultiessentials.chestlock.protect-piston-retract | Cancel a piston-retract event outright if any block it would move belongs to a locked container, container-scoped as for piston-extend. Unreachable in vanilla for the same measured reason — `isPushable` gates retraction through the same `PistonStructureResolver` — kept for the same reason, and covered by `ChestLockListenerTest$PistonRetractTests` rather than by a real-machine row | event | retract a sticky piston pulling a locked container | n/a | n/a | internal | none | ChestLockListener#onPistonRetract, ChestLockListenerTest$PistonRetractTests |
| ultiessentials.chestlock.protect-hopper | Cancel an `InventoryMoveItemEvent` whose source inventory belongs to a locked container, preventing a hopper from siphoning items out of it. The source is resolved from the inventory's HOLDER, which for a double chest is a `DoubleChest` — not a `org.bukkit.block.Container` and not a block state at all, so a holder type-test against `Container` skipped every double chest and left even a fully-recorded one drainable. `getDestination()` is deliberately not checked: inserting items does not expose a container's contents, and cancelling insertion would stop an owner's own hopper feeding their own locked chest | event | place a hopper feeding from a locked container, single or double | n/a | n/a | internal | none | ChestLockListener#onInventoryMove, ChestLockService#isContainerLocked(InventoryHolder) |

## Server Presentation

`MotdListener` (`features.motd.enabled`, `MotdConfig`), `TabBarListener` (`features.tab-bar.enabled`,
`TabBarConfig`), `NamePrefixListener`+`NamePrefixService` (`features.nameprefix.enabled`, default
OFF), and `DeathPunishListener` (`features.deathpunish.enabled`, default OFF).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.motd.customize | Override the server list ping's MOTD with two configured, `&`-color-coded lines, and optionally override the displayed max-player count (a value <= 0 leaves the platform default) | event | ping the server's status (a client's server list, or any status-query tool) | n/a | n/a | admin | brief | MotdListener#onServerListPing |
| ultiessentials.tabbar.on-join | On join, set the joining player's tab-list header/footer from configured, `&`-color-coded templates with `%online%`/`%max%` substitution | event | join the server with `features.tab-bar.enabled` true | n/a | n/a | player | brief | TabBarListener#onPlayerJoin |
| ultiessentials.nameprefix.on-join | 0.5 seconds after join (`runTaskLater(10L)`, re-checking the player is still online), register the player into a per-player scoreboard team carrying a configured, PlaceholderAPI-or-fallback-substituted prefix/suffix (each truncated to 64 characters) | event | join the server with `features.nameprefix.enabled` true (default false) | n/a | n/a | player | brief | NamePrefixListener#onPlayerJoin |
| ultiessentials.nameprefix.on-quit | Remove the quitting player's name from their scoreboard team's entry list (the team object itself is kept registered, not unregistered) | event | quit the server with an active name-prefix team | n/a | n/a | internal | none | NamePrefixListener#onPlayerQuit |
| ultiessentials.deathpunish.on-death | On a non-cancelled player death, outside any world on the configured world whitelist and for a player lacking `ultiessentials.deathpunish.bypass`, independently apply up to four configured punishments — a percentage-of-balance money loss via Vault (capped, silently skipped if no economy plugin is present), a percentage chance to drop each non-whitelisted item (optionally keeping the rest), a percentage experience-orb reduction, and a list of console commands with `{PLAYER}`/`%player%` substitution — then report a single hardcoded-Chinese summary line naming only the sub-punishments that actually applied (no i18n call anywhere in this method) | event | die as a player, with `features.deathpunish.enabled` true (default false) and no bypass permission | n/a | n/a | player | detailed | DeathPunishListener#onPlayerDeath |

## Scheduled Commands

`ScheduledCommandService` (`features.scheduled-commands.enabled`, default OFF) parses each entry
of `scheduled-commands.commands` (format `interval_seconds:command`, malformed entries logged and
skipped) and schedules it as an independent, indefinitely-repeating `BukkitRunnable` via
`runTaskTimer` at server-console privilege. **This is NOT backed by the framework's own
`@Scheduled` annotation** — the reconciliation table's `@Scheduled` line reads 0 against this one
`scheduled`-Kind row, and that is the correct, intentional divergence: the feature exists and
functions, it is simply implemented with Bukkit's own scheduler API directly rather than the
framework's declarative annotation.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.scheduledcommands.run | Run each configured console command on its own fixed interval, indefinitely, for as long as this module stays loaded (stopped by `/upm uninstall UltiEssentials`, UltiKits/UltiEssentials#43) | scheduled | runs automatically, every `interval_seconds` per configured entry, while `features.scheduled-commands.enabled` is true | n/a | n/a | admin | brief | ScheduledCommandService#startTasks |

## Lifecycle

UltiTools 6.3.0 makes `UltiToolsPlugin#reloadSelf()` and `#unregisterSelf()` `final` template
methods. Before UltiKits/UltiEssentials#23's lifecycle migration this module overrode both with a
body that only logged a line, replacing the framework's own steps; both overrides were deleted
rather than renamed, and it prints no reload or unload line of its own. It declares two hooks,
`onReload()` (UltiKits/UltiEssentials#28) and `onUnregister()` (UltiKits/UltiEssentials#43).
`/ul reload UltiEssentials` runs the framework's reload steps (configuration reload, language
refresh, `@ConditionalOnConfig` drift report — this module has 0 sites — and the framework's
per-module `Module 'UltiEssentials' reloaded.` INFO line), then `onReload()`, which calls
`reload()` on `ScheduledCommandService`, `ScoreboardService` and `NamePrefixService` in that order
(see `## Configuration` for what each restart does) after first repeating the start-up check for
removed settings left in `config/essentials.yml` (`ultiessentials.lifecycle.removed-key-warning`),
warning by name for any it cannot resolve
rather than skipping it silently. That warning, and the unload's matching failure, cannot be
produced on a stock install and are not checklist material: all four services are unconditional
`@Service` beans and this module declares no `@ConditionalOnConfig`, so `getBean` returns null
only after a source change — a renamed service, one moved out of `scanBasePackages`, or one
registered under an interface type. They are guards against that, not states an operator can
configure into, and what holds them instead is `UltiEssentialsServiceUnloadTest` (three tests)
and `UltiEssentialsServiceReloadTest#unresolvableServiceIsReportedOnReload`, each pinned by its
own mutation pair. Note also that `/ul reload <name>` replies success unconditionally, so the
reload warning reaches the console and never the sender (UltiKits/UltiTools-Reborn#529).
`/upm uninstall UltiEssentials` runs `onUnregister()` first — it calls `shutdown()` on
`ScheduledCommandService`, `ScoreboardService`, `NamePrefixService` and `TeleportService`, each on
its own and each even when an earlier one fails, then reports the first failure — and only then the
framework's command and listener unregistration. This matters because these four services start
repeating tasks through `BukkitRunnable#runTaskTimer` owned by the `UltiTools` Bukkit plugin, not by
this module: Bukkit does not cancel them when this module is unloaded, and the framework's unload
path cancels only the tasks it created itself from `@Scheduled` methods (this module declares none).
The *defect* never affected server shutdown — Bukkit cancels every task of the `UltiTools` plugin when
that plugin is disabled — but the *hook* runs there too: `UltiTools#onDisable` calls
`PluginManager#close()`, which calls `unregister()` and so `unregisterSelf()` on every loaded module
(`close()` isolates a module whose hook throws, so a failure here cannot skip another module's
unregistration or the framework's own config save). The visible consequence of that is one file:
`NamePrefixService#shutdown` empties teams on the server's main scoreboard, which Bukkit persists to
`world/data/scoreboard.dat`, so a clean stop now writes entries out of that file that it previously
left in. Players get their prefix back about a second after rejoining, from the same update task.
`shutdown()` empties each `up_` team but does not unregister it, on unload as on reload. That is a
deliberate carry-over, not an oversight: the method is shared with `/ul reload`, where the team is
needed again moments later, and a re-installed module finds and reuses the existing team rather than
registering a new one. The cost is that after an uninstall the server keeps one registered, empty
`up_<first 8 characters of the player UUID>` team per player who was online while the module ran,
until someone runs the vanilla `team remove` on it. The changelog says so in both languages.
`ConfigManager#reloadConfigs` re-initialises, in place, the same `EssentialsConfig` instance the
container injected into `SpeedCommand`, which reads `features.speed.max-speed` at call time — the
observable the first row below uses. The second row turns name prefixes on through a reload, the
third turns the scoreboard off through a reload, the fourth reads the warnings a removed setting left
in the file produces, and the fifth unloads the module and reads whether
its repeating tasks stopped.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.lifecycle.reload | `/ul reload UltiEssentials` re-reads this module's configuration files into the running module, so an edited value such as `features.speed.max-speed` applies to the next `/speed` without a restart; the module prints no reload line of its own. On UltiTools 6.2.5 the module's reload override replaced the framework's reload and only logged, so an edit took effect only after a restart | event | `/ul reload UltiEssentials` (framework calls `reloadSelf()`, which reloads configuration, refreshes language, reports `@ConditionalOnConfig` drift and logs its own per-module line) | n/a | n/a | admin | brief | SpeedCommand#setSpeed |
| ultiessentials.lifecycle.reload-nameprefix | After `features.nameprefix.enabled` is changed from `false` to `true`, `/ul reload UltiEssentials` restarts `NamePrefixService` with the main scoreboard, so the restarted update task applies the configured prefix to every online player about 1 second after the reload and a player who joins afterwards gets it 0.5 seconds after joining, with no exception from `NamePrefixService#updatePlayer` (UltiKits/UltiEssentials#28) | event | `/ul reload UltiEssentials` after editing `features.nameprefix.enabled` to `true` in `config/essentials.yml` | n/a | n/a | admin | brief | UltiEssentials#onReload, NamePrefixService#reload |
| ultiessentials.lifecycle.reload-scoreboard-off | After `features.scoreboard.enabled` is changed from `true` to `false`, `/ul reload UltiEssentials` cancels the sidebar update task and returns every player who had a sidebar to the server's main scoreboard, so the sidebar disappears and name prefixes (and any other team on the main scoreboard) become visible to those players without rejoining (UltiKits/UltiEssentials#28) | event | `/ul reload UltiEssentials` after editing `features.scoreboard.enabled` to `false` in `config/essentials.yml` | n/a | n/a | admin | brief | ScoreboardService#reload, ScoreboardService#shutdown |
| ultiessentials.lifecycle.removed-key-warning | At start-up and on every `/ul reload UltiEssentials`, log one WARN line for each setting this module has removed that is still in the operator's `config/essentials.yml` — `features.wild.cooldown` and `features.recall.enabled` (UltiKits/UltiEssentials#27) — naming the module, the file and the key and saying where the setting's job went (`UltiKits/UltiTools-Reborn#531` for the cooldown, `UltiKits/UltiEssentials#53` for `/recall`); a file holding neither key produces no such line, and a configuration that cannot be read produces one line saying the file was not checked rather than none | event | start the server, or run `/ul reload UltiEssentials`, with a removed key left in `plugins/UltiTools/pluginConfig/UltiEssentials/config/essentials.yml` | n/a | n/a | admin | brief | UltiEssentials#warnAboutRemovedSettings, RemovedConfigKeys#warningsFor |
| ultiessentials.lifecycle.unload-tasks | `/upm uninstall UltiEssentials` stops every repeating task this module started: configured entries of `features.scheduled-commands.commands` stop being dispatched to the console, the sidebar and name-prefix update tasks stop, and a teleport warmup still counting down is cancelled rather than completed. Players who had a sidebar are returned to the server's main scoreboard and this module's name-prefix team entries are removed, although the now-empty teams stay registered. Before UltiKits/UltiEssentials#43 the module declared no `onUnregister()` hook, so all of these kept running against the uninstalled module until the server was restarted while the uninstall reported success. One-shot delayed tasks are out of scope and are tracked in UltiKits/UltiEssentials#51 | event | `/upm uninstall UltiEssentials` from the server console (framework calls `unregisterSelf()`, which runs `onUnregister()` and then unregisters this module's commands and listeners) | n/a | n/a | admin | brief | UltiEssentials#onUnregister, ScheduledCommandService#shutdown, ScoreboardService#shutdown, NamePrefixService#shutdown, TeleportService#shutdown |

## Data Persistence

Homes, warps, bans, and chest locks are stored via `DataOperator<T>` against `@Table`-annotated
entities (`HomeData`, `WarpData`, `BanData`, `ChestLockData` respectively — MySQL/SQLite/JSON per
`config.yml`, the framework's own choice, not this module's); `/back` and `/hide` state are
deliberately in-memory only. **This module is not in Phase 9's GUI-exclusion register** — confirmed
by reading `.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/`
directly: no `UltiEssentials.md` file exists there and no file in that directory names
UltiEssentials — so every row's `Covers` column throughout this document is left blank.

**Start-up repair of records written before UltiKits/UltiEssentials#34, `ultiessentials.storedkey.repair`:** records this module wrote before #34 was fixed were saved with the framework's `@Column("id")` primary key left empty, so every later delete or update addressed by `WHERE id = ?` matched nothing. `EntityIdBackfillService`, invoked from `UltiEssentials#registerSelf()` once per start-up, gives those records their key through the public `DataOperator` API: nothing in that API can set a column on a row it cannot address (measured — all three update entry points key on `id`, `Query` has no update terminal, and no `UPDATE` builder in the framework takes a `WhereCondition`), so a row-backed store is deleted and re-inserted, while a cache-backed store needs only the re-insert because the key is written onto the stored record itself. One transaction covers a whole entity type rather than one per record, and the key is confirmed present afterwards rather than assumed. Repairing 8,000 records takes about a second. It is idempotent — a repaired record has a key, so the next start-up sees no candidate and writes nothing — and it leaves a record untouched, counted and logged at WARNING, rather than guessing, when the record has no identity of its own, shares its identity with another stored record, or already carries a key that disagrees with its identity. It logs one INFO line per entity type whose records it wrote, plus a summary, so an INFO line always means records were written; a record it left alone is reported at WARNING instead, one line each with the reason, so a run that only skipped is not silent and is also not claiming a write. `features.data-repair.enabled` (default `true`) can hold it off entirely, in which case it logs one line naming the key and writes nothing. The default is `true` because an operator who never learns this defect exists would never switch a repair on, and records that stay silently un-keyed are worse than a repair that runs; the key exists because running without anyone deciding is what makes this repair a data change rather than a code change, and an operator mid-migration, or one who has not taken a backup yet, has a real reason to wait. There is deliberately no dry-run mode: it would double the paths and the interesting one would be the one nobody runs.

**Module-wide i18n coverage gap, `ultiessentials.i18n-coverage`:** the overwhelming majority of
this module's player-facing text — every `handleHelp` line in all 38 command classes, most
non-default-path status messages in `BanCommand`/`TempBanCommand`/`UnbanCommand`/`LockCommand`/
`UnlockCommand`/`ScoreboardCommand`/`SetHomeCommand`/`SetWarpCommand`/`TpaCommand`/
`TpaHereCommand`/`TpAcceptCommand`/`TpDenyCommand`/`HomeCommand`/`HomesCommand`/`WarpCommand`/
`WarpsCommand`/`WildCommand` and others, plus `BanService#formatKickMessage`,
`BanService#formatDuration`, `ChestLockListener`'s two lock-refusal lines, and
`DeathPunishListener`'s punishment summary — passes a Chinese literal string as the i18n lookup
key, but that exact key exists in NEITHER `lang/en.json` NOR `lang/zh.json` (both files hold only
88 keys total, an identical key set, confirmed by a full diff; measured after
UltiKits/UltiEssentials#23's lifecycle migration removed the two keys of the module's own unload
and reload console lines). `Language#getLocalizedText`'s only
fallback for an unmatched key is to return the key itself unchanged — so `language: en` has
literally no effect on any of this text; it renders in Chinese on an English-configured server
exactly as it would on a Chinese-configured one. A minority of messages (all of
`BanListCommand`, most of `UnbanCommand`, the base disabled-feature / player-not-found i18n key pair used
throughout (both DO have real English translations, keyed `feature_disabled` and a Chinese-literal key respectively — read `lang/en.json` for the exact strings), and the handful of `teleport_*`-style keys) DO have real English translations and
behave correctly. `BanService#formatKickMessage`/`#formatDuration` do not call `i18n(...)` at all
— their Chinese text is a compile-time literal with no lookup step, not merely an unmatched key.
Filed as UltiKits/UltiEssentials#26 (scope: catalogue the affected call sites; do not translate,
per this plan's zero-code rule).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.home.persistence | A home created or updated via `/sethome` survives a server restart — backed by `HomeData` (`@Table("essentials_homes")`) through the framework's own `DataOperator`, written synchronously on the calling thread at the moment of the command, not deferred to shutdown | persistence | run `/sethome`, restart the server, run `/home <name>` | n/a | n/a | player | brief | HomeService#setHome, HomeData |
| ultiessentials.warp.persistence | A warp created via `/setwarp` survives a server restart, backed by `WarpData` (`@Table("essentials_warps")`) the same way as homes | persistence | run `/setwarp`, restart the server, run `/warp <name>` | n/a | n/a | admin | brief | WarpService#createWarp, WarpData |
| ultiessentials.ban.persistence | A ban created via `/ban`/`/tempban` survives a server restart, backed by `BanData` (`@Table("essentials_bans")`); an expired temporary ban is filtered out by `hasExpired()` at read time rather than being actively deleted from storage on expiry | persistence | run `/tempban <player> 1h`, restart the server, run `/banlist` | n/a | n/a | admin | brief | BanService#banPlayer, BanData |
| ultiessentials.chestlock.persistence | A lock created via `/lock` survives a server restart — backed by `ChestLockData` (`@Table("essentials_chest_locks")`); `ChestLockService#init()` reloads every stored lock into an in-memory `lockCache` map at boot (`@PostConstruct`, before any player can interact with a container) | persistence | run `/lock` on a container, restart the server, `/unlock info` on the same container | n/a | n/a | player | brief | ChestLockService#init, ChestLockData |
| ultiessentials.storedkey.repair | Records written before UltiKits/UltiEssentials#34 was fixed are given their missing primary key once, at start-up, so deleting or updating them works; the repair reports per entity type what it repaired and what it deliberately left alone, a second start-up repairs nothing, and `features.data-repair.enabled` can hold it off (see `ultiessentials.config.essentials.features.data-repair.enabled`) | persistence | start the server on a data store written by a build earlier than this one and read the start-up log, then delete one of the pre-existing records | n/a | n/a | admin | brief | EntityIdBackfillService#run, UltiEssentials#registerSelf |
| ultiessentials.chestlock.break.persistence | A lock removed by its owner breaking the container stays removed across a server restart — the record is deleted from the store, not only dropped from the in-memory cache `ChestLockService#init` rebuilds at boot | persistence | lock a container, break it as its owner, restart the server, place a container at the same coordinates and run `/unlock info` | n/a | n/a | player | brief | ChestLockService#onBlockBreak, ChestLockData |
| ultiessentials.ban.unban.persistence | A ban lifted via `/unban` stays lifted across a server restart — the record's active flag is cleared in the store, not only in the response | persistence | run `/ban <target>`, `/unban <target>`, restart the server, run `/banlist` and have the target connect | n/a | n/a | admin | brief | BanService#unbanPlayerByName, BanData |
| ultiessentials.home.delete.persistence | A home deleted via `/delhome` stays deleted across a server restart — the record is removed from the store, not only from the response | persistence | run `/sethome farm`, `/delhome farm`, restart the server, run `/homes` | n/a | n/a | player | brief | HomeService#deleteHome, HomeData |
| ultiessentials.warp.delete.persistence | A warp deleted via `/delwarp` stays deleted across a server restart — the record is removed from the store, not only from the response | persistence | run `/setwarp shop`, `/delwarp shop`, restart the server, run `/warps` | n/a | n/a | admin | brief | WarpService#deleteWarp, WarpData |
| ultiessentials.back.not-persisted | The `/back` location map (`BackCommand.LAST_LOCATIONS`) is a plain static `ConcurrentHashMap`, deliberately never written to any `DataOperator`/file — a player's back-location is lost on quit (explicit cleanup) and on server restart (the map is reconstructed empty on JVM start) | persistence | run a teleport, then restart the server without quitting first, then run `/back` | n/a | n/a | internal | brief | BackCommand#LAST_LOCATIONS |
| ultiessentials.hide.not-persisted | The `/hide` vanish-state set (`HideCommand.HIDDEN_PLAYERS`) is a plain static `HashSet`, deliberately never written to any `DataOperator`/file — vanish state is lost on quit (explicit cleanup in `PlayerQuitListener`) and on server restart | persistence | toggle `/hide` on, then restart the server without quitting first, then check the player's visibility to others on rejoin | n/a | n/a | internal | brief | HideCommand#HIDDEN_PLAYERS |

## Configuration

Every `@ConfigEntry`-annotated field across this module's five `@ConfigEntity` classes (77 keys
total: `EssentialsConfig` 58, `SpawnConfig` 8, `LobbyConfig` 6, `MotdConfig` 3, `TabBarConfig` 2 —
matching the reconciliation table's own `@ConfigEntry` count of 77 exactly; `EssentialsConfig` gained
`features.data-repair.enabled` with UltiKits/UltiEssentials#34 and lost `features.wild.cooldown` and
`features.recall.enabled` with UltiKits/UltiEssentials#27). Several of these keys
already have a behavioural row above (the various `features.*.enabled` toggles, home/warp/tpa
warmups, deathpunish sub-toggles) — that row documents the *feature* the key drives, this row
documents the *key* itself, at file-and-key granularity, so the reconciliation table can prove
every key is accounted for without also making every behavioural row carry a `config` Kind.

**Four keys were declared and shipped with a comment describing their effect, but were never read
by any production code outside `EssentialsConfig` itself** (UltiKits/UltiEssentials#27). In 6.3.0
two are wired and two are removed. `features.ban.broadcast-ban` and `features.ban.broadcast-unban`
now decide whether a ban and an unban are announced to the whole server; both default to `true`,
which is what the commands always did. `features.wild.cooldown` (`/wild`'s cooldown is the fixed
`@CmdCD(60)` on `WildCommand#wildTeleport`) and `features.recall.enabled` (there is no `/recall`
command) were **removed**: a copy left in an operator's file is reported by
`ultiessentials.lifecycle.removed-key-warning`, a configurable `/wild` cooldown is requested from the
framework as UltiKits/UltiTools-Reborn#531, and `/recall` is recorded as a feature request,
UltiKits/UltiEssentials#53.

**`/ul reload UltiEssentials` re-reads configuration values and restarts this module's
scheduled-command, scoreboard and name-prefix background tasks.** Since
UltiKits/UltiEssentials#23's lifecycle migration this module no longer overrides `reloadSelf()`, so
UltiTools 6.3.0's `final` `reloadSelf()` re-initialises every configuration bean in place and code
that reads a getter at call time sees the edited value (`ultiessentials.lifecycle.reload`). The
module's `onReload()` hook then calls `ScheduledCommandService#reload()`,
`ScoreboardService#reload()` and `NamePrefixService#reload()` (UltiKits/UltiEssentials#28). Each
cancels the repeating tasks it owns and, only if its feature is still enabled, starts them again
against the re-read values, so an edit to `features.scheduled-commands.enabled`,
`features.scheduled-commands.commands`, `features.scoreboard.enabled`,
`features.scoreboard.update-interval`, `features.nameprefix.enabled`, or
`features.nameprefix.update-interval` takes effect on reload. Each service is reloaded on its own:
if one service's reload throws, the failure is logged at SEVERE with the service name and the other
two are still reloaded. That failure is reported only on the server console; the reply to whoever
ran `/ul reload` does not reflect it (UltiKits/UltiTools-Reborn#509). Turning name prefixes on by reload gives `NamePrefixService` the main
scoreboard before its update task first runs, 1 second after the reload
(`ultiessentials.lifecycle.reload-nameprefix`); turning them off cancels that task and removes from
their prefix team every player this service has assigned since the server started (an entry left
on the saved main scoreboard by an earlier session is not removed). Two effects of a reload match a
restart rather than preserving running state: every scheduled command's interval starts counting
again from the reload, so reloading more often than a command's interval keeps postponing that
command; and `NamePrefixService#reload()` removes those players from their prefix team and, if
name prefixes stay enabled, the restarted task adds online players back 1 second later. While the
scoreboard stays enabled across a reload, each online player's sidebar stays shown or hidden as it
was, including a `/scoreboard` choice (the only per-player scoreboard state is `ScoreboardService`'s
in-memory `enabledPlayers` set, which `/scoreboard` and the automatic enable on join both write). A
reload that turns the scoreboard off removes every sidebar and returns those players to the
server's main scoreboard, so name prefixes become visible to them without rejoining
(`ultiessentials.lifecycle.reload-scoreboard-off`); a reload that turns it on applies
`features.scoreboard.auto-enable` to players already online. Players who join after a reload follow
the reloaded `auto-enable` through `ScoreboardListener#onPlayerJoin`, which re-checks it when its
delayed enable runs.

**One failing player does not stop a refresh.** The scoreboard and name-prefix update tasks refresh
each player on their own: a player whose sidebar or prefix cannot be refreshed (for example because
a PlaceholderAPI expansion throws for that player) is logged once at error level and retried on every
update, while the other players are refreshed as usual; the failure is logged again only after a
refresh for that player has succeeded in between, and is forgotten when the player quits or turns
the sidebar off. A player's name-prefix team that was removed (for example with the vanilla
`team remove` command) is looked up again or re-created on the next update, so the prefix comes back. Each scheduled command already runs as its own task, so a command that throws
affects only itself.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.config.essentials.features.back.enabled | Enable `/back` | config | `config/essentials.yml: features.back.enabled (default: true)` | n/a | n/a | admin | brief | BackCommand#back |
| ultiessentials.config.essentials.features.spawn.enabled | Enable `/spawn`, `/setspawn`, and the respawn/first-join auto-teleport listeners | config | `config/essentials.yml: features.spawn.enabled (default: true)` | n/a | n/a | admin | brief | SpawnCommand#teleportToSpawn |
| ultiessentials.config.essentials.features.lobby.enabled | Enable `/lobby` and `/setlobby` | config | `config/essentials.yml: features.lobby.enabled (default: true)` | n/a | n/a | admin | brief | LobbyCommand#teleportToLobby |
| ultiessentials.config.essentials.features.wild.enabled | Enable `/wild` | config | `config/essentials.yml: features.wild.enabled (default: true)` | n/a | n/a | admin | brief | WildCommand#wildTeleport |
| ultiessentials.config.essentials.features.wild.max-range | The outer radius, in blocks, of `/wild`'s random-point search (validated `@Range(100, 100000)`) | config | `config/essentials.yml: features.wild.max-range (default: 10000)` | n/a | n/a | admin | brief | WildCommand#wildTeleport |
| ultiessentials.config.essentials.features.wild.min-range | The inner radius, in blocks, of `/wild`'s random-point search (validated `@Range(10, 10000)`); `/wild` refuses outright if this is >= `max-range` | config | `config/essentials.yml: features.wild.min-range (default: 100)` | n/a | n/a | admin | brief | WildCommand#wildTeleport |
| ultiessentials.config.essentials.features.fly.enabled | Enable `/fly` (both self and other-player mappings) | config | `config/essentials.yml: features.fly.enabled (default: true)` | n/a | n/a | admin | brief | FlyCommand#toggleFly |
| ultiessentials.config.essentials.features.heal.enabled | Enable `/heal` (both self and other-player mappings; also gates `/feed`, which shares this class's config check) | config | `config/essentials.yml: features.heal.enabled (default: true)` | n/a | n/a | admin | brief | HealCommand#healSelf, FeedCommand#feedSelf |
| ultiessentials.config.essentials.features.speed.enabled | Enable `/speed` (both mappings) | config | `config/essentials.yml: features.speed.enabled (default: true)` | n/a | n/a | admin | brief | SpeedCommand#setSpeed |
| ultiessentials.config.essentials.features.speed.max-speed | The maximum speed multiplier `/speed <value>` accepts (validated `@Range(1, 10)`) | config | `config/essentials.yml: features.speed.max-speed (default: 10)` | n/a | n/a | admin | brief | SpeedCommand#setSpeed |
| ultiessentials.config.essentials.features.gamemode.enabled | Enable `/gm` and its three `/gmc`/`/gms`/`/gmsp` shortcuts | config | `config/essentials.yml: features.gamemode.enabled (default: true)` | n/a | n/a | admin | brief | GameModeCommand#setGameMode |
| ultiessentials.config.essentials.features.hide.enabled | Enable `/hide` | config | `config/essentials.yml: features.hide.enabled (default: true)` | n/a | n/a | admin | brief | HideCommand#toggleHide |
| ultiessentials.config.essentials.features.invsee.enabled | Enable `/invsee`, `/endersee`, and `/armorsee` (one shared toggle for all three inspection commands) | config | `config/essentials.yml: features.invsee.enabled (default: true)` | n/a | n/a | admin | brief | InvseeCommand#invsee |
| ultiessentials.config.essentials.features.whitelist.enabled | Enable all six `/wl` sub-commands | config | `config/essentials.yml: features.whitelist.enabled (default: true)` | n/a | n/a | admin | brief | WhitelistCommand#add |
| ultiessentials.config.essentials.features.motd.enabled | Enable the custom-MOTD server-list-ping listener | config | `config/essentials.yml: features.motd.enabled (default: true)` | n/a | n/a | admin | brief | MotdListener#onServerListPing |
| ultiessentials.config.essentials.features.tab-bar.enabled | Enable the tab-list header/footer listener | config | `config/essentials.yml: features.tab-bar.enabled (default: true)` | n/a | n/a | admin | brief | TabBarListener#onPlayerJoin |
| ultiessentials.config.essentials.features.home.enabled | Enable all four `/home`, `/sethome`, `/delhome`, `/homes` commands | config | `config/essentials.yml: features.home.enabled (default: true)` | n/a | n/a | admin | brief | HomeCommand#teleportToDefaultHome |
| ultiessentials.config.essentials.features.home.default-max-homes | The per-player home limit used when no `ultiessentials.home.max.<n>`/`.unlimited` permission node is held (validated `@Range(1, 100)`) | config | `config/essentials.yml: features.home.default-max-homes (default: 3)` | n/a | n/a | admin | brief | HomeService#getMaxHomes |
| ultiessentials.config.essentials.features.home.teleport-warmup | Seconds of warmup before a `/home <name>` teleport completes, skippable via `ultiessentials.home.nowarmup` (validated `@Range(0, 60)`; `0` teleports instantly) | config | `config/essentials.yml: features.home.teleport-warmup (default: 3)` | n/a | n/a | admin | brief | HomeService#teleportToHome |
| ultiessentials.config.essentials.features.home.cancel-on-move | Cancel a warming-up `/home` (and, by reuse, `/warp`) teleport if the player moves more than 1 block (squared distance) from their start position during warmup | config | `config/essentials.yml: features.home.cancel-on-move (default: true)` | n/a | n/a | admin | brief | HomeService#teleportToHome, WarpService#teleportToWarp |
| ultiessentials.config.essentials.features.tpa.enabled | Enable `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` | config | `config/essentials.yml: features.tpa.enabled (default: true)` | n/a | n/a | admin | brief | TpaCommand#sendTpa |
| ultiessentials.config.essentials.features.tpa.timeout | Seconds before an unanswered TPA request auto-expires and notifies both parties (validated `@Range(5, 300)`) | config | `config/essentials.yml: features.tpa.timeout (default: 30)` | n/a | n/a | admin | brief | TpaService#startTimeoutTask |
| ultiessentials.config.essentials.features.tpa.cooldown | Seconds a sender must wait between successive `/tpa`/`/tpahere` requests (validated `@Range(0, 600)`) | config | `config/essentials.yml: features.tpa.cooldown (default: 10)` | n/a | n/a | admin | brief | TpaService#isOnCooldown |
| ultiessentials.config.essentials.features.tpa.allow-cross-world | Allow a `/tpa`/`/tpahere` request between players in different worlds | config | `config/essentials.yml: features.tpa.allow-cross-world (default: true)` | n/a | n/a | admin | brief | TpaService#sendRequest |
| ultiessentials.config.essentials.features.warp.enabled | Enable `/warp`, `/setwarp`, `/delwarp`, `/warps` | config | `config/essentials.yml: features.warp.enabled (default: true)` | n/a | n/a | admin | brief | WarpCommand#warp |
| ultiessentials.config.essentials.features.warp.teleport-warmup | Seconds of warmup before a `/warp <name>` teleport completes, skippable via `ultiessentials.warp.nowarmup` (validated `@Range(0, 60)`) | config | `config/essentials.yml: features.warp.teleport-warmup (default: 3)` | n/a | n/a | admin | brief | WarpService#teleportToWarp |
| ultiessentials.config.essentials.features.ban.enabled | Gates creating a NEW ban (`/ban`, `/tempban`, via `BanService#banPlayer`) and the login kick check (`BanListener#onPlayerLogin`) -- does NOT gate `/unban` (`BanService#unbanPlayerByName` has no enabled check) or `/banlist` (`BanService#getActiveBans` has no enabled check either), so disabling this key stops new bans from being created or enforced at login but leaves existing bans fully manageable and visible | config | `config/essentials.yml: features.ban.enabled (default: true)` | n/a | n/a | admin | brief | BanService#banPlayer |
| ultiessentials.config.essentials.features.ban.broadcast-ban | Whether a successful `/ban` or `/tempban` announces the ban and its reason to the whole server (`true`) or to the issuer alone (`false`) — never to nobody, because the announcement is the issuer's only confirmation; read on every ban. Before UltiKits/UltiEssentials#27 this key was never read and every ban was broadcast | config | `config/essentials.yml: features.ban.broadcast-ban (default: true)` | n/a | n/a | admin | brief | BanCommand#announce, TempBanCommand#announce |
| ultiessentials.config.essentials.features.ban.broadcast-unban | Whether a plain-success `/unban` is also announced to the whole server; the issuer's own confirmation is sent either way; read on every unban. Before UltiKits/UltiEssentials#27 this key was never read and every plain-success unban was broadcast | config | `config/essentials.yml: features.ban.broadcast-unban (default: true)` | n/a | n/a | admin | brief | UnbanCommand#unban |
| ultiessentials.config.essentials.features.scoreboard.enabled | Enable `/scoreboard`/`/sb` and the sidebar-scoreboard update loop | config | `config/essentials.yml: features.scoreboard.enabled (default: true)` | n/a | n/a | admin | brief | ScoreboardService#enableScoreboard |
| ultiessentials.config.essentials.features.scoreboard.auto-enable | Automatically enable the sidebar scoreboard 1 second after a player joins | config | `config/essentials.yml: features.scoreboard.auto-enable (default: true)` | n/a | n/a | admin | brief | ScoreboardListener#onPlayerJoin |
| ultiessentials.config.essentials.features.scoreboard.update-interval | Seconds between sidebar-scoreboard content refreshes for every player with it enabled (validated `@Range(1, 60)`) | config | `config/essentials.yml: features.scoreboard.update-interval (default: 1)` | n/a | n/a | admin | brief | ScoreboardService#startUpdateTask |
| ultiessentials.config.essentials.features.scoreboard.title | The sidebar scoreboard's title line, PlaceholderAPI-or-fallback-substituted and color-coded (validated `@NotEmpty`) | config | `config/essentials.yml: features.scoreboard.title (default: "&6&l" + a Chinese literal — read `EssentialsConfig.java:143` for the exact characters)` | n/a | n/a | admin | none | ScoreboardService#updateScoreboard |
| ultiessentials.config.essentials.features.scoreboard.lines | The sidebar scoreboard's body lines, in display order, each PlaceholderAPI-or-fallback-substituted and color-coded; a duplicate rendered line under 40 characters is disambiguated with an appended invisible `ChatColor`, but `ScoreboardService#ensureUnique` checks the untruncated candidate against already-added (and therefore already-truncated) entries before truncating its own result at the end -- for two lines that are identical only in their first 40+ characters, the collision check never fires, so the second line is truncated to the SAME 40-character string as the first and overwrites it as one `Score` entry rather than appearing as a second line | config | `config/essentials.yml: features.scoreboard.lines (default: 10 lines)` | n/a | n/a | admin | detailed | ScoreboardService#updateScoreboard, ScoreboardService#ensureUnique |
| ultiessentials.config.essentials.features.scheduled-commands.enabled | Enable the scheduled-console-command feature entirely | config | `config/essentials.yml: features.scheduled-commands.enabled (default: false)` | n/a | n/a | admin | brief | ScheduledCommandService#startTasks |
| ultiessentials.config.essentials.features.scheduled-commands.commands | The scheduled command list, each entry `interval_seconds:command`; a malformed entry (missing colon, non-numeric or non-positive interval, empty command) is logged and skipped, not rejected as a whole-file validation failure | config | `config/essentials.yml: features.scheduled-commands.commands (default: 2 entries)` | n/a | n/a | admin | detailed | ScheduledCommandService#startTasks |
| ultiessentials.config.essentials.features.chestlock.enabled | Enable NEW container locking via `/lock`; does not gate `/unlock`'s two mappings (see `## Container Locking` section note) | config | `config/essentials.yml: features.chestlock.enabled (default: true)` | n/a | n/a | admin | brief | ChestLockListener#onPlayerInteract |
| ultiessentials.config.essentials.features.chestlock.admin-bypass | Whether an `ultiessentials.lock.admin` holder may open another player's locked container. It gates opening ONLY: breaking a locked container and removing its record via `/unlock` both test the `ultiessentials.lock.admin` node directly and ignore this toggle, so turning it off does not stop an admin breaking or unlocking | config | `config/essentials.yml: features.chestlock.admin-bypass (default: true)` | n/a | n/a | admin | brief | ChestLockService#canAccess(ChestLockData, Player) |
| ultiessentials.config.essentials.features.deathpunish.enabled | Enable the on-death punishment listener entirely | config | `config/essentials.yml: features.deathpunish.enabled (default: false)` | n/a | n/a | admin | brief | DeathPunishListener#onPlayerDeath |
| ultiessentials.config.essentials.features.deathpunish.money.enabled | Enable the money-loss-on-death punishment (requires a Vault-compatible economy plugin; silently skipped without one) | config | `config/essentials.yml: features.deathpunish.money.enabled (default: false)` | n/a | n/a | admin | brief | DeathPunishListener#onPlayerDeath |
| ultiessentials.config.essentials.features.deathpunish.money.percent | Percentage of the dying player's current balance lost on death (validated `@Range(0, 100)`) | config | `config/essentials.yml: features.deathpunish.money.percent (default: 10.0)` | n/a | n/a | admin | brief | DeathPunishListener#onPlayerDeath |
| ultiessentials.config.essentials.features.deathpunish.money.max | Hard cap on money lost per death regardless of the percentage; `0` means no cap (validated `@Range(0, 1000000)`) | config | `config/essentials.yml: features.deathpunish.money.max (default: 1000.0)` | n/a | n/a | admin | none | DeathPunishListener#onPlayerDeath |
| ultiessentials.config.essentials.features.deathpunish.item.enabled | Enable the item-drop-modification punishment on death | config | `config/essentials.yml: features.deathpunish.item.enabled (default: false)` | n/a | n/a | admin | brief | DeathPunishListener#processItemDrop |
| ultiessentials.config.essentials.features.deathpunish.item.drop-chance | Per-non-whitelisted-item percentage chance it is (further) affected by the drop-keep logic below (validated `@Range(0, 100)`) | config | `config/essentials.yml: features.deathpunish.item.drop-chance (default: 50.0)` | n/a | n/a | admin | detailed | DeathPunishListener#processItemDrop |
| ultiessentials.config.essentials.features.deathpunish.item.keep-other | When true, the vanilla drop list is reduced to ONLY the items that rolled inside `drop-chance` (i.e. this key inverts the naming: `true` keeps FEWER items, not "keeps the others") | config | `config/essentials.yml: features.deathpunish.item.keep-other (default: true)` | n/a | n/a | admin | detailed | DeathPunishListener#processItemDrop |
| ultiessentials.config.essentials.features.deathpunish.item.whitelist | Item type names skipped by the random drop-chance roll -- but NOT thereby guaranteed to drop: `#processItemDrop` never adds a whitelisted item to `toDrop`, so when `deathpunish.item.keep-other` is also `true` (the shipped default for THAT key), the later `drops.retainAll(toDrop)` call removes whitelisted items from the death drop list along with every item that lost its roll, the opposite of an exemption. Only with `keep-other: false` do whitelisted items reliably survive (nothing is removed from `drops` in that branch at all) | config | `config/essentials.yml: features.deathpunish.item.whitelist (default: 2 entries)` | n/a | n/a | admin | detailed | DeathPunishListener#processItemDrop |
| ultiessentials.config.essentials.features.deathpunish.exp.enabled | Enable the experience-loss punishment on death | config | `config/essentials.yml: features.deathpunish.exp.enabled (default: false)` | n/a | n/a | admin | brief | DeathPunishListener#onPlayerDeath |
| ultiessentials.config.essentials.features.deathpunish.exp.percent | Additional percentage of the dying player's total experience subtracted from the already-computed dropped-experience amount (validated `@Range(0, 100)`) | config | `config/essentials.yml: features.deathpunish.exp.percent (default: 20.0)` | n/a | n/a | admin | brief | DeathPunishListener#onPlayerDeath |
| ultiessentials.config.essentials.features.deathpunish.command.enabled | Enable running configured console commands on death | config | `config/essentials.yml: features.deathpunish.command.enabled (default: false)` | n/a | n/a | admin | brief | DeathPunishListener#executeCommands |
| ultiessentials.config.essentials.features.deathpunish.command.commands | Console commands run on a punished death, with `{PLAYER}`/`%player%` substitution | config | `config/essentials.yml: features.deathpunish.command.commands (default: 1 entry)` | n/a | n/a | admin | none | DeathPunishListener#executeCommands |
| ultiessentials.config.essentials.features.deathpunish.world-whitelist | World names in which death punishment never applies at all | config | `config/essentials.yml: features.deathpunish.world-whitelist (default: 1 entry)` | n/a | n/a | admin | brief | DeathPunishListener#onPlayerDeath |
| ultiessentials.config.essentials.features.nameprefix.enabled | Enable the scoreboard-team-based name prefix/suffix feature entirely | config | `config/essentials.yml: features.nameprefix.enabled (default: false)` | n/a | n/a | admin | brief | NamePrefixService#init |
| ultiessentials.config.essentials.features.nameprefix.prefix-format | The prefix template applied to every player's scoreboard-team entry, PlaceholderAPI-or-fallback-substituted, color-coded, truncated to 64 characters | config | `config/essentials.yml: features.nameprefix.prefix-format (default: "&7[&e%vault_prefix%&7] ")` | n/a | n/a | admin | brief | NamePrefixService#updatePlayer |
| ultiessentials.config.essentials.features.nameprefix.suffix-format | The suffix template applied to every player's scoreboard-team entry (empty by default — no suffix shown) | config | `config/essentials.yml: features.nameprefix.suffix-format (default: "")` | n/a | n/a | admin | none | NamePrefixService#updatePlayer |
| ultiessentials.config.essentials.features.nameprefix.update-interval | Seconds between the periodic re-application of prefix/suffix to every online player (validated `@Range(1, 60)`) | config | `config/essentials.yml: features.nameprefix.update-interval (default: 5)` | n/a | n/a | admin | none | NamePrefixService#updateAllPlayers |
| ultiessentials.config.essentials.features.commandalias.enabled | Enable the command-alias-rewrite listener entirely | config | `config/essentials.yml: features.commandalias.enabled (default: true)` | n/a | n/a | admin | brief | CommandAliasListener#onPlayerCommand |
| ultiessentials.config.essentials.features.commandalias.aliases | The alias-to-real-command map (default 6 entries: `gmc`/`gms`/`gma`/`gmsp`/`day`/`night`) | config | `config/essentials.yml: features.commandalias.aliases (default: 6 entries)` | n/a | n/a | admin | brief | CommandAliasListener#onPlayerCommand |
| ultiessentials.config.spawn.spawn.location.world | The spawn point's world name; a world that fails to resolve at teleport time produces a null-world `Location` that `/spawn`/`/setspawn` and the two auto-teleport listeners each check for and refuse on | config | `config/spawn.yml: spawn.location.world (default: "world")` | n/a | n/a | admin | brief | SpawnConfig#getSpawnLocation |
| ultiessentials.config.spawn.spawn.location.x | The spawn point's X coordinate | config | `config/spawn.yml: spawn.location.x (default: 0.0)` | n/a | n/a | admin | none | SpawnConfig#getSpawnLocation |
| ultiessentials.config.spawn.spawn.location.y | The spawn point's Y coordinate | config | `config/spawn.yml: spawn.location.y (default: 64.0)` | n/a | n/a | admin | none | SpawnConfig#getSpawnLocation |
| ultiessentials.config.spawn.spawn.location.z | The spawn point's Z coordinate | config | `config/spawn.yml: spawn.location.z (default: 0.0)` | n/a | n/a | admin | none | SpawnConfig#getSpawnLocation |
| ultiessentials.config.spawn.spawn.location.yaw | The spawn point's horizontal facing angle | config | `config/spawn.yml: spawn.location.yaw (default: 0.0)` | n/a | n/a | admin | none | SpawnConfig#getSpawnLocation |
| ultiessentials.config.spawn.spawn.location.pitch | The spawn point's vertical facing angle | config | `config/spawn.yml: spawn.location.pitch (default: 0.0)` | n/a | n/a | admin | none | SpawnConfig#getSpawnLocation |
| ultiessentials.config.spawn.spawn.teleport-on-first-join | Teleport a genuinely-first-time joiner to spawn (see `ultiessentials.spawn.on-first-join`) | config | `config/spawn.yml: spawn.teleport-on-first-join (default: true)` | n/a | n/a | admin | brief | JoinQuitListener#onPlayerJoin |
| ultiessentials.config.spawn.spawn.teleport-on-respawn | Override the vanilla respawn location with spawn (see `ultiessentials.spawn.on-respawn`) | config | `config/spawn.yml: spawn.teleport-on-respawn (default: true)` | n/a | n/a | admin | brief | RespawnListener#onPlayerRespawn |
| ultiessentials.config.lobby.lobby.location.world | The lobby point's world name; a null-resolving world produces a null-world `Location`, checked and refused on by `/lobby`/`/setlobby` | config | `config/lobby.yml: lobby.location.world (default: "world")` | n/a | n/a | admin | brief | LobbyConfig#getLobbyLocation |
| ultiessentials.config.lobby.lobby.location.x | The lobby point's X coordinate | config | `config/lobby.yml: lobby.location.x (default: 0.0)` | n/a | n/a | admin | none | LobbyConfig#getLobbyLocation |
| ultiessentials.config.lobby.lobby.location.y | The lobby point's Y coordinate | config | `config/lobby.yml: lobby.location.y (default: 64.0)` | n/a | n/a | admin | none | LobbyConfig#getLobbyLocation |
| ultiessentials.config.lobby.lobby.location.z | The lobby point's Z coordinate | config | `config/lobby.yml: lobby.location.z (default: 0.0)` | n/a | n/a | admin | none | LobbyConfig#getLobbyLocation |
| ultiessentials.config.lobby.lobby.location.yaw | The lobby point's horizontal facing angle | config | `config/lobby.yml: lobby.location.yaw (default: 0.0)` | n/a | n/a | admin | none | LobbyConfig#getLobbyLocation |
| ultiessentials.config.lobby.lobby.location.pitch | The lobby point's vertical facing angle | config | `config/lobby.yml: lobby.location.pitch (default: 0.0)` | n/a | n/a | admin | none | LobbyConfig#getLobbyLocation |
| ultiessentials.config.motd.motd.line1 | MOTD first line, `&`-color-coded | config | `config/motd.yml: motd.line1 (default: "&6Welcome to our server!")` | n/a | n/a | admin | brief | MotdListener#onServerListPing |
| ultiessentials.config.motd.motd.line2 | MOTD second line, `&`-color-coded | config | `config/motd.yml: motd.line2 (default: "&7Powered by UltiTools")` | n/a | n/a | admin | brief | MotdListener#onServerListPing |
| ultiessentials.config.motd.motd.max-players | Overrides the displayed max-player count in the server-list ping; `-1` leaves the platform's real value | config | `config/motd.yml: motd.max-players (default: -1)` | n/a | n/a | admin | none | MotdListener#onServerListPing |
| ultiessentials.config.tabbar.tabbar.header | Tab-list header template, `&`-color-coded, with `%online%`/`%max%` substitution | config | `config/tabbar.yml: tabbar.header (default: "&6=== " + a Chinese literal + " ===" — read `TabBarConfig.java:18` for the exact characters)` | n/a | n/a | admin | brief | TabBarListener#updateTabBar |
| ultiessentials.config.essentials.features.data-repair.enabled | Run the start-up repair that gives records written before UltiKits/UltiEssentials#34 was fixed the primary key they were saved without; set to `false` to leave those records exactly as they are, in which case deleting or updating them keeps failing and one log line names this key | config | `config/essentials.yml: features.data-repair.enabled (default: true)` | n/a | n/a | admin | brief | EntityIdBackfillService#run |
| ultiessentials.config.tabbar.tabbar.footer | Tab-list footer template, `&`-color-coded, with `%online%`/`%max%` substitution | config | `config/tabbar.yml: tabbar.footer (default: "&7" + a Chinese literal + ": &e%online%&7/&e%max%" — read `TabBarConfig.java:21` for the exact characters)` | n/a | n/a | admin | brief | TabBarListener#updateTabBar |
