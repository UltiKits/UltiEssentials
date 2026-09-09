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
  the framework's `@Scheduled` annotation (0 sites) — see that row's own note.
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
  feature — for every Kind, `config` included: all 78 `config` rows below cite the reading member,
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
(classes), `@ConfigEntry` = 78, `@Table` = 4 (`HomeData`, `WarpData`, `BanData`, `ChestLockData`) —
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
`ultiessentials.lobby.set`), `WildCommand` (`ultiessentials.wild`, `@CmdCD(60)` — a 60-second
per-player cooldown is enforced by the framework's own cooldown validator, independently of the
declared-but-dead `features.wild.cooldown` config key — see `## Configuration`), and
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
| ultiessentials.home.set-named | Create or update a specific named home at the sender's current position, refusing if the sender is at their per-permission home limit and the name does not already exist | command | `/sethome <name>` | ultiessentials.sethome | player | player | brief | SetHomeCommand#setHome |
| ultiessentials.home.delete | Delete a home by name, reporting whether one existed | command | `/delhome <name>` (aliases `/deletehome`, `/rmhome`) | ultiessentials.delhome | player | player | brief | DelHomeCommand#deleteHome |
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
| ultiessentials.warp.delete | Delete a warp by name (server-wide — any holder of `warp.delete` may delete any warp, not only ones they created), reporting whether one existed | command | `/delwarp <name>` (aliases `/deletewarp`, `/rmwarp`, `/removewarp`) | ultiessentials.warp.delete | player | admin | brief | DelWarpCommand#delWarp |
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

**`/fly <player>` carries no elevated permission of its own** — its `@CmdMapping` declares no
`permission` override, so it inherits the class-level `ultiessentials.fly`, the SAME node that
gates toggling one's own flight. Contrast `HealCommand`/`FeedCommand` (explicit
`ultiessentials.heal.other` override) and `GameModeCommand` (explicit
`ultiessentials.gamemode.other` override), both of which correctly require a distinct, more
privileged node for the other-player variant. Any player holding ordinary self-fly access can
toggle flight for any other online player. Filed as UltiKits/UltiEssentials#25.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.heal.self | Restore the sender's health to their current max-health attribute value | command | `/heal` | ultiessentials.heal.self | player | player | brief | HealCommand#healSelf |
| ultiessentials.heal.other | Restore a named online target's health to their max-health attribute value | command | `/heal <player>` | ultiessentials.heal.other | player | admin | brief | HealCommand#healOther |
| ultiessentials.feed.self | Restore the sender's food level to 20 and saturation to 20.0 | command | `/feed` | ultiessentials.heal.self | player | player | brief | FeedCommand#feedSelf |
| ultiessentials.feed.other | Restore a named online target's food level to 20 and saturation to 20.0 | command | `/feed <player>` | ultiessentials.heal.other | player | admin | brief | FeedCommand#feedOther |
| ultiessentials.speed.set | Set the sender's walk speed (`0.2 * value`, capped at 1.0) and fly speed (`0.1 * value`, capped at 1.0); `0` resets to the platform default instead of setting a zero speed; refuses any value outside `0..speed.max-speed` | command | `/speed <value>` | ultiessentials.speed | player | player | brief | SpeedCommand#setSpeed |
| ultiessentials.speed.reset | Reset the sender's walk and fly speed to the platform default (0.2 / 0.1) | command | `/speed reset` | ultiessentials.speed | player | player | brief | SpeedCommand#resetSpeed |
| ultiessentials.fly.toggle-self | Toggle the sender's own flight allowance; disabling also forces `setFlying(false)` so the sender does not remain airborne with flight revoked | command | `/fly` | ultiessentials.fly | player | player | brief | FlyCommand#toggleFly |
| ultiessentials.fly.toggle-other | Toggle a named online target's flight allowance — gated by the SAME permission node as toggling one's own flight, not a separate elevated node (see section note; UltiKits/UltiEssentials#25) | command | `/fly <player>` | ultiessentials.fly | player | admin | brief | FlyCommand#toggleFlyOther |
| ultiessentials.hide.toggle | Toggle the sender's own vanish: on enable, hides the sender from every online player lacking `ultiessentials.hide.see`; on disable, re-shows the sender to everyone. State is a static in-memory set, not persisted (see `## Data Persistence`) | command | `/hide` (alias `/vanish`) | ultiessentials.hide | player | admin | brief | HideCommand#toggleHide |
| ultiessentials.gamemode.set-self | Set the sender's own game mode by numeric (`0-3`), single-letter, or full-name token | command | `/gm <mode>` | ultiessentials.gamemode.self | player | player | brief | GameModeCommand#setGameMode |
| ultiessentials.gamemode.set-other | Set a named online target's game mode, notifying both sender and target | command | `/gm <mode> <player>` | ultiessentials.gamemode.other | player | admin | brief | GameModeCommand#setGameModeOther |
| ultiessentials.gamemode.shortcut-creative | Shortcut to set the sender's own game mode to CREATIVE | command | `/gmc` | ultiessentials.gamemode.self | player | player | none | GmCreativeCommand#creative |
| ultiessentials.gamemode.shortcut-spectator | Shortcut to set the sender's own game mode to SPECTATOR | command | `/gmsp` | ultiessentials.gamemode.self | player | player | none | GmSpectatorCommand#spectator |
| ultiessentials.gamemode.shortcut-survival | Shortcut to set the sender's own game mode to SURVIVAL | command | `/gms` | ultiessentials.gamemode.self | player | player | none | GmSurvivalCommand#survival |
| ultiessentials.scoreboard.toggle | Toggle the sender's own sidebar scoreboard on or off | command | `/scoreboard` (alias `/sb`) | ultiessentials.scoreboard | player | player | brief | ScoreboardCommand#toggle |
| ultiessentials.scoreboard.enable | Explicitly enable the sender's sidebar scoreboard (no-ops with a distinct message if already on) | command | `/scoreboard on` | ultiessentials.scoreboard | player | player | none | ScoreboardCommand#enable |
| ultiessentials.scoreboard.disable | Explicitly disable the sender's sidebar scoreboard (no-ops with a distinct message if already off) | command | `/scoreboard off` | ultiessentials.scoreboard | player | player | none | ScoreboardCommand#disable |
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
| ultiessentials.ban.ban | Permanently ban a player (online or previously-seen offline) with a fixed, hardcoded-Chinese default reason (no i18n call — read `BanCommand.java:38` for the exact characters; it reads roughly "no reason given"), kicking them immediately if online, and broadcasting the ban | command | `/ban <player>` (alias `/eban`) | ultiessentials.ban | both | admin | brief | BanCommand#ban |
| ultiessentials.ban.ban-with-reason | Permanently ban a player with a given reason, kicking them immediately if online, and broadcasting the ban and reason | command | `/ban <player> <reason>` | ultiessentials.ban | both | admin | brief | BanCommand#banWithReason |
| ultiessentials.ban.tempban | Temporarily ban a player for a parsed duration (`1d`/`2h`/`30m`/`1w`, combinable e.g. `1d12h30m`) with the same fixed, hardcoded-Chinese default reason as `ultiessentials.ban.ban` (see that row), refusing on an unparseable duration | command | `/tempban <player> <duration>` | ultiessentials.ban.temp | both | admin | brief | TempBanCommand#tempban |
| ultiessentials.ban.tempban-with-reason | Temporarily ban a player for a parsed duration with a given reason | command | `/tempban <player> <duration> <reason>` | ultiessentials.ban.temp | both | admin | brief | TempBanCommand#tempbanWithReason |
| ultiessentials.ban.unban | Unban a player by name (this plugin's own record only), reporting one of four distinct outcomes depending on whether the plugin's own record and/or the server's own vanilla ban list currently ban the name (see section note) | command | `/unban <player>` (alias `/pardon`) | ultiessentials.unban | both | admin | brief | UnbanCommand#unban |
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
its other half too. **`UnlockCommand`'s two mappings never check `features.chestlock.enabled` at
all** (`ChestLockService#unlockBlock`/`#getLock` have no such check, unlike `#lockBlock`) — a
container can be unlocked, and its lock info inspected, even while the whole feature is configured
disabled; only locking a NEW container is actually gated.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.lock.lock | Lock the container the sender is looking at (within 5 blocks), refusing on a non-lockable block type, an already-locked container (by anyone), or the feature being disabled | command | `/lock` (alias `/l`) | ultiessentials.lock | player | player | brief | LockCommand#lock |
| ultiessentials.lock.unlock | Unlock the container the sender is looking at, refusing if not locked or if locked by someone else and the sender lacks `ultiessentials.lock.admin` — NOT gated by `features.chestlock.enabled` (see section note) | command | `/unlock` (alias `/ul` — distinct from the framework's own `/ul` core command; Bukkit's own command-conflict resolution decides which registration wins if both are present) | ultiessentials.lock | player | player | brief | UnlockCommand#unlock |
| ultiessentials.lock.info | Report whether the looked-at container is locked and, if so, by whom and at what coordinates — NOT gated by `features.chestlock.enabled` (see section note) | command | `/unlock info` | ultiessentials.lock | player | player | none | UnlockCommand#info |
| ultiessentials.chestlock.protect-interact | Cancel a right-click interaction with a locked container by anyone who is neither its owner nor (when `chestlock.admin-bypass` is true) an `ultiessentials.lock.admin` holder, and message the interacting player naming the owner | event | right-click a locked container you do not own and cannot bypass | n/a | n/a | internal | none | ChestLockListener#onPlayerInteract |
| ultiessentials.chestlock.protect-break | Cancel breaking a locked container by anyone who is neither its owner nor an `ultiessentials.lock.admin` holder (this check does NOT consult `chestlock.admin-bypass` — admin bypass for interaction and admin bypass for breaking are two independently-coded checks that happen to require the same permission node); removes the lock record when a permitted break succeeds | event | attempt to break a locked container | n/a | n/a | internal | none | ChestLockListener#onBlockBreak |
| ultiessentials.chestlock.protect-explode-entity | Remove any locked block from an entity explosion's (TNT, creeper, etc.) block-destruction list before it is applied | event | detonate an entity explosion near a locked container | n/a | n/a | internal | none | ChestLockListener#onEntityExplode |
| ultiessentials.chestlock.protect-explode-block | Remove any locked block from a block explosion's (bed/respawn-anchor misuse, etc.) block-destruction list before it is applied | event | trigger a block explosion near a locked container | n/a | n/a | internal | none | ChestLockListener#onBlockExplode |
| ultiessentials.chestlock.protect-piston-extend | Cancel a piston-extend event outright if any block it would move is locked | event | extend a piston toward a locked container | n/a | n/a | internal | none | ChestLockListener#onPistonExtend |
| ultiessentials.chestlock.protect-piston-retract | Cancel a piston-retract event outright if any block it would move is locked | event | retract a sticky piston pulling a locked container | n/a | n/a | internal | none | ChestLockListener#onPistonRetract |
| ultiessentials.chestlock.protect-hopper | Cancel an `InventoryMoveItemEvent` whose source container is locked, preventing a hopper from siphoning items out of a locked container | event | place a hopper feeding from a locked container | n/a | n/a | internal | none | ChestLockListener#onInventoryMove |

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
| ultiessentials.scheduledcommands.run | Run each configured console command on its own fixed interval, indefinitely, for as long as the server is up | scheduled | runs automatically, every `interval_seconds` per configured entry, while `features.scheduled-commands.enabled` is true | n/a | n/a | admin | brief | ScheduledCommandService#startTasks |

## Data Persistence

Homes, warps, bans, and chest locks are stored via `DataOperator<T>` against `@Table`-annotated
entities (`HomeData`, `WarpData`, `BanData`, `ChestLockData` respectively — MySQL/SQLite/JSON per
`config.yml`, the framework's own choice, not this module's); `/back` and `/hide` state are
deliberately in-memory only. **This module is not in Phase 9's GUI-exclusion register** — confirmed
by reading `.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/`
directly: no `UltiEssentials.md` file exists there and no file in that directory names
UltiEssentials — so every row's `Covers` column throughout this document is left blank.

**Module-wide i18n coverage gap, `ultiessentials.i18n-coverage`:** the overwhelming majority of
this module's player-facing text — every `handleHelp` line in all 38 command classes, most
non-default-path status messages in `BanCommand`/`TempBanCommand`/`UnbanCommand`/`LockCommand`/
`UnlockCommand`/`ScoreboardCommand`/`SetHomeCommand`/`SetWarpCommand`/`TpaCommand`/
`TpaHereCommand`/`TpAcceptCommand`/`TpDenyCommand`/`HomeCommand`/`HomesCommand`/`WarpCommand`/
`WarpsCommand`/`WildCommand` and others, plus `BanService#formatKickMessage`,
`BanService#formatDuration`, `ChestLockListener`'s two lock-refusal lines, and
`DeathPunishListener`'s punishment summary — passes a Chinese literal string as the i18n lookup
key, but that exact key exists in NEITHER `lang/en.json` NOR `lang/zh.json` (both files hold only
90 keys total, an identical key set, confirmed by a full diff). `Language#getLocalizedText`'s only
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
| ultiessentials.back.not-persisted | The `/back` location map (`BackCommand.LAST_LOCATIONS`) is a plain static `ConcurrentHashMap`, deliberately never written to any `DataOperator`/file — a player's back-location is lost on quit (explicit cleanup) and on server restart (the map is reconstructed empty on JVM start) | persistence | run a teleport, then restart the server without quitting first, then run `/back` | n/a | n/a | internal | brief | BackCommand#LAST_LOCATIONS |
| ultiessentials.hide.not-persisted | The `/hide` vanish-state set (`HideCommand.HIDDEN_PLAYERS`) is a plain static `HashSet`, deliberately never written to any `DataOperator`/file — vanish state is lost on quit (explicit cleanup in `PlayerQuitListener`) and on server restart | persistence | toggle `/hide` on, then restart the server without quitting first, then check the player's visibility to others on rejoin | n/a | n/a | internal | brief | HideCommand#HIDDEN_PLAYERS |

## Configuration

Every `@ConfigEntry`-annotated field across this module's five `@ConfigEntity` classes (78 keys
total: `EssentialsConfig` 59, `SpawnConfig` 8, `LobbyConfig` 6, `MotdConfig` 3, `TabBarConfig` 2 —
matching the reconciliation table's own `@ConfigEntry` count of 78 exactly). Several of these keys
already have a behavioural row above (the various `features.*.enabled` toggles, home/warp/tpa
warmups, deathpunish sub-toggles) — that row documents the *feature* the key drives, this row
documents the *key* itself, at file-and-key granularity, so the reconciliation table can prove
every key is accounted for without also making every behavioural row carry a `config` Kind.

**Four keys are declared, `@Range`/`@NotEmpty`-validated where applicable, and shipped with a
comment describing their effect, but are never read by any production code outside
`EssentialsConfig` itself** — confirmed by a repository-wide grep for each key's generated
getter (`isX()`/`getX()`) finding zero call sites beyond the config class's own declaration.
Each is called out in its own row below with the filed issue number
(`UltiKits/UltiEssentials#27`) rather than a claim that flipping it changes anything:
`features.recall.enabled` (there is no `/recall` command anywhere in this module's source at
all — the key describes a feature that does not exist), `features.wild.cooldown` (`/wild`'s real
cooldown is the hardcoded `@CmdCD(60)` on `WildCommand#wildTeleport`, entirely independent of this
key's value), `features.ban.broadcast-ban`, and `features.ban.broadcast-unban` (every ban/tempban/
unban command calls `Bukkit.broadcastMessage(...)` unconditionally; these two keys' values are
never consulted).

**`/ul reload` does not actually refresh this module's scheduled-command, scoreboard, or
name-prefix background tasks.** `UltiEssentials#reloadSelf()` logs a "config reloaded" message but
calls none of `ScheduledCommandService#reload()`, `ScoreboardService#reload()`, or
`NamePrefixService#reload()` — all three exist specifically to restart their respective background
tasks against fresh config values, and none is ever invoked from anywhere in this module. This
compounds the separately-filed UltiKits/UltiEssentials#23 (`reloadSelf()` not calling
`super.reloadSelf()`, so even the underlying `@ConfigEntry` values are not re-read from disk):
fixing #23 alone would still leave these three services running against their boot-time state.
Filed as UltiKits/UltiEssentials#28.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiessentials.config.essentials.features.back.enabled | Enable `/back` | config | `config/essentials.yml: features.back.enabled (default: true)` | n/a | n/a | admin | brief | BackCommand#back |
| ultiessentials.config.essentials.features.spawn.enabled | Enable `/spawn`, `/setspawn`, and the respawn/first-join auto-teleport listeners | config | `config/essentials.yml: features.spawn.enabled (default: true)` | n/a | n/a | admin | brief | SpawnCommand#teleportToSpawn |
| ultiessentials.config.essentials.features.lobby.enabled | Enable `/lobby` and `/setlobby` | config | `config/essentials.yml: features.lobby.enabled (default: true)` | n/a | n/a | admin | brief | LobbyCommand#teleportToLobby |
| ultiessentials.config.essentials.features.wild.enabled | Enable `/wild` | config | `config/essentials.yml: features.wild.enabled (default: true)` | n/a | n/a | admin | brief | WildCommand#wildTeleport |
| ultiessentials.config.essentials.features.wild.max-range | The outer radius, in blocks, of `/wild`'s random-point search (validated `@Range(100, 100000)`) | config | `config/essentials.yml: features.wild.max-range (default: 10000)` | n/a | n/a | admin | brief | WildCommand#wildTeleport |
| ultiessentials.config.essentials.features.wild.min-range | The inner radius, in blocks, of `/wild`'s random-point search (validated `@Range(10, 10000)`); `/wild` refuses outright if this is >= `max-range` | config | `config/essentials.yml: features.wild.min-range (default: 100)` | n/a | n/a | admin | brief | WildCommand#wildTeleport |
| ultiessentials.config.essentials.features.wild.cooldown | Declared as `/wild`'s per-player cooldown in seconds (validated `@Range(0, 3600)`); never read — the real cooldown is the hardcoded `@CmdCD(60)` on the command method, unaffected by this key's value. Known product defect, UltiKits/UltiEssentials#27 | config | `config/essentials.yml: features.wild.cooldown (default: 60, has no effect, see UltiKits/UltiEssentials#27)` | n/a | n/a | admin | brief | EssentialsConfig#wildCooldown (declared, never read outside this class) |
| ultiessentials.config.essentials.features.recall.enabled | Declared as a toggle for a "`/recall`" command; no `/recall` command, or any other reader of this key, exists anywhere in this module's source. Known product defect, UltiKits/UltiEssentials#27 | config | `config/essentials.yml: features.recall.enabled (default: true, has no effect, see UltiKits/UltiEssentials#27)` | n/a | n/a | admin | brief | EssentialsConfig#recallEnabled (declared, never read outside this class) |
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
| ultiessentials.config.essentials.features.ban.broadcast-ban | Declared as a toggle for whether a new ban is broadcast server-wide; never read — `/ban` and `/tempban` always call `Bukkit.broadcastMessage(...)` unconditionally on success. Known product defect, UltiKits/UltiEssentials#27 | config | `config/essentials.yml: features.ban.broadcast-ban (default: true, has no effect, see UltiKits/UltiEssentials#27)` | n/a | n/a | admin | brief | EssentialsConfig#banBroadcast (declared, never read outside this class) |
| ultiessentials.config.essentials.features.ban.broadcast-unban | Declared as a toggle for whether a successful unban is broadcast server-wide; never read — `/unban` always calls `Bukkit.broadcastMessage(...)` unconditionally on the plain-success outcome. Known product defect, UltiKits/UltiEssentials#27 | config | `config/essentials.yml: features.ban.broadcast-unban (default: true, has no effect, see UltiKits/UltiEssentials#27)` | n/a | n/a | admin | brief | EssentialsConfig#unbanBroadcast (declared, never read outside this class) |
| ultiessentials.config.essentials.features.scoreboard.enabled | Enable `/scoreboard`/`/sb` and the sidebar-scoreboard update loop | config | `config/essentials.yml: features.scoreboard.enabled (default: true)` | n/a | n/a | admin | brief | ScoreboardService#enableScoreboard |
| ultiessentials.config.essentials.features.scoreboard.auto-enable | Automatically enable the sidebar scoreboard 1 second after a player joins | config | `config/essentials.yml: features.scoreboard.auto-enable (default: true)` | n/a | n/a | admin | brief | ScoreboardListener#onPlayerJoin |
| ultiessentials.config.essentials.features.scoreboard.update-interval | Seconds between sidebar-scoreboard content refreshes for every player with it enabled (validated `@Range(1, 60)`) | config | `config/essentials.yml: features.scoreboard.update-interval (default: 1)` | n/a | n/a | admin | brief | ScoreboardService#startUpdateTask |
| ultiessentials.config.essentials.features.scoreboard.title | The sidebar scoreboard's title line, PlaceholderAPI-or-fallback-substituted and color-coded (validated `@NotEmpty`) | config | `config/essentials.yml: features.scoreboard.title (default: "&6&l" + a Chinese literal — read `EssentialsConfig.java:143` for the exact characters)` | n/a | n/a | admin | none | ScoreboardService#updateScoreboard |
| ultiessentials.config.essentials.features.scoreboard.lines | The sidebar scoreboard's body lines, in display order, each PlaceholderAPI-or-fallback-substituted and color-coded; duplicate rendered lines are disambiguated with an appended invisible `ChatColor` and truncated to 40 characters | config | `config/essentials.yml: features.scoreboard.lines (default: 10 lines)` | n/a | n/a | admin | none | ScoreboardService#updateScoreboard |
| ultiessentials.config.essentials.features.scheduled-commands.enabled | Enable the scheduled-console-command feature entirely | config | `config/essentials.yml: features.scheduled-commands.enabled (default: false)` | n/a | n/a | admin | brief | ScheduledCommandService#startTasks |
| ultiessentials.config.essentials.features.scheduled-commands.commands | The scheduled command list, each entry `interval_seconds:command`; a malformed entry (missing colon, non-numeric or non-positive interval, empty command) is logged and skipped, not rejected as a whole-file validation failure | config | `config/essentials.yml: features.scheduled-commands.commands (default: 2 entries)` | n/a | n/a | admin | detailed | ScheduledCommandService#startTasks |
| ultiessentials.config.essentials.features.chestlock.enabled | Enable NEW container locking via `/lock`; does not gate `/unlock`'s two mappings (see `## Container Locking` section note) | config | `config/essentials.yml: features.chestlock.enabled (default: true)` | n/a | n/a | admin | brief | ChestLockListener#onPlayerInteract |
| ultiessentials.config.essentials.features.chestlock.admin-bypass | Whether an `ultiessentials.lock.admin` holder may open (not break — see `## Container Locking` section note) another player's locked container | config | `config/essentials.yml: features.chestlock.admin-bypass (default: true)` | n/a | n/a | admin | brief | ChestLockService#canAccess |
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
| ultiessentials.config.tabbar.tabbar.footer | Tab-list footer template, `&`-color-coded, with `%online%`/`%max%` substitution | config | `config/tabbar.yml: tabbar.footer (default: "&7" + a Chinese literal + ": &e%online%&7/&e%max%" — read `TabBarConfig.java:21` for the exact characters)` | n/a | n/a | admin | brief | TabBarListener#updateTabBar |
