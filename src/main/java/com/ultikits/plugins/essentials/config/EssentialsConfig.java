package com.ultikits.plugins.essentials.config;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;
import lombok.Getter;
import lombok.Setter;

/**
 * Main configuration class for UltiEssentials.
 * Controls feature toggles and common parameters.
 */
@Getter
@Setter
@ConfigEntity("config/essentials.yml")
public class EssentialsConfig extends AbstractConfigEntity {

    // ============ 传送类功能 ============
    @ConfigEntry(path = "features.back.enabled", comment = "{essentials.config.essentials.features.back.enabled}")
    private boolean backEnabled = true;

    @ConfigEntry(path = "features.spawn.enabled", comment = "{essentials.config.essentials.features.spawn.enabled}")
    private boolean spawnEnabled = true;

    @ConfigEntry(path = "features.lobby.enabled", comment = "{essentials.config.essentials.features.lobby.enabled}")
    private boolean lobbyEnabled = true;

    @ConfigEntry(path = "features.wild.enabled", comment = "{essentials.config.essentials.features.wild.enabled}")
    private boolean wildEnabled = true;

    @Range(min = 100, max = 100000)
    @ConfigEntry(path = "features.wild.max-range", comment = "{essentials.config.essentials.features.wild.max-range}")
    private int wildMaxRange = 10000;

    @Range(min = 10, max = 10000)
    @ConfigEntry(path = "features.wild.min-range", comment = "{essentials.config.essentials.features.wild.min-range}")
    private int wildMinRange = 100;

    // Seconds between two /wild uses by one player; 0 means no cooldown. Bound to /wild through the
    // framework's config-bound @CmdCD on WildCommand#wildTeleport, so this field is the only place
    // the default lives, and /ul reload applies a new value (UltiKits/UltiEssentials#27,
    // UltiKits/UltiTools-Reborn#531). Must stay an int: the binding accepts only integral fields.
    // Deliberately no @Range: the binding owns the range (0 to Integer.MAX_VALUE), refusing the
    // module at load and keeping the running value with a WARNING on reload. A module @Range would
    // pre-empt that on reload by aborting the whole reload part-way.
    @ConfigEntry(path = "features.wild.cooldown", comment = "{essentials.config.essentials.features.wild.cooldown}")
    private int wildCooldown = 60;

    // features.recall.enabled was removed in 6.3.0: there is no /recall command. A copy left in an
    // operator's file is reported by RemovedConfigKeys (UltiKits/UltiEssentials#27).

    // ============ 玩家状态功能 ============
    @ConfigEntry(path = "features.fly.enabled", comment = "{essentials.config.essentials.features.fly.enabled}")
    private boolean flyEnabled = true;

    @ConfigEntry(path = "features.heal.enabled", comment = "{essentials.config.essentials.features.heal.enabled}")
    private boolean healEnabled = true;

    @ConfigEntry(path = "features.speed.enabled", comment = "{essentials.config.essentials.features.speed.enabled}")
    private boolean speedEnabled = true;

    @Range(min = 1, max = 10)
    @ConfigEntry(path = "features.speed.max-speed", comment = "{essentials.config.essentials.features.speed.max-speed}")
    private int speedMaxSpeed = 10;

    @ConfigEntry(path = "features.gamemode.enabled", comment = "{essentials.config.essentials.features.gamemode.enabled}")
    private boolean gamemodeEnabled = true;

    @ConfigEntry(path = "features.hide.enabled", comment = "{essentials.config.essentials.features.hide.enabled}")
    private boolean hideEnabled = true;

    // ============ 管理类功能 ============
    @ConfigEntry(path = "features.invsee.enabled", comment = "{essentials.config.essentials.features.invsee.enabled}")
    private boolean invseeEnabled = true;

    @ConfigEntry(path = "features.whitelist.enabled", comment = "{essentials.config.essentials.features.whitelist.enabled}")
    private boolean whitelistEnabled = true;

    // ============ 监听器功能 ============
    @ConfigEntry(path = "features.motd.enabled", comment = "{essentials.config.essentials.features.motd.enabled}")
    private boolean motdEnabled = true;

    @ConfigEntry(path = "features.tab-bar.enabled", comment = "{essentials.config.essentials.features.tab-bar.enabled}")
    private boolean tabBarEnabled = true;

    // ============ Home 系统功能 ============
    @ConfigEntry(path = "features.home.enabled", comment = "{essentials.config.essentials.features.home.enabled}")
    private boolean homeEnabled = true;

    @Range(min = 1, max = 100)
    @ConfigEntry(path = "features.home.default-max-homes", comment = "{essentials.config.essentials.features.home.default-max-homes}")
    private int homeDefaultMaxHomes = 3;

    @Range(min = 0, max = 60)
    @ConfigEntry(path = "features.home.teleport-warmup", comment = "{essentials.config.essentials.features.home.teleport-warmup}")
    private int homeTeleportWarmup = 3;

    @ConfigEntry(path = "features.home.cancel-on-move", comment = "{essentials.config.essentials.features.home.cancel-on-move}")
    private boolean homeCancelOnMove = true;

    // ============ TPA 传送功能 ============
    @ConfigEntry(path = "features.tpa.enabled", comment = "{essentials.config.essentials.features.tpa.enabled}")
    private boolean tpaEnabled = true;

    @Range(min = 5, max = 300)
    @ConfigEntry(path = "features.tpa.timeout", comment = "{essentials.config.essentials.features.tpa.timeout}")
    private int tpaTimeout = 30;

    @Range(min = 0, max = 600)
    @ConfigEntry(path = "features.tpa.cooldown", comment = "{essentials.config.essentials.features.tpa.cooldown}")
    private int tpaCooldown = 10;

    @ConfigEntry(path = "features.tpa.allow-cross-world", comment = "{essentials.config.essentials.features.tpa.allow-cross-world}")
    private boolean tpaAllowCrossWorld = true;

    // ============ Warp 地标功能 ============
    @ConfigEntry(path = "features.warp.enabled", comment = "{essentials.config.essentials.features.warp.enabled}")
    private boolean warpEnabled = true;

    @Range(min = 0, max = 60)
    @ConfigEntry(path = "features.warp.teleport-warmup", comment = "{essentials.config.essentials.features.warp.teleport-warmup}")
    private int warpTeleportWarmup = 3;

    // ============ Ban 封禁系统 ============
    @ConfigEntry(path = "features.ban.enabled", comment = "{essentials.config.essentials.features.ban.enabled}")
    private boolean banEnabled = true;

    @ConfigEntry(path = "features.ban.broadcast-ban", comment = "{essentials.config.essentials.features.ban.broadcast-ban}")
    private boolean banBroadcast = true;

    @ConfigEntry(path = "features.ban.broadcast-unban", comment = "{essentials.config.essentials.features.ban.broadcast-unban}")
    private boolean unbanBroadcast = true;

    // ============ Scoreboard 计分板 ============
    @ConfigEntry(path = "features.scoreboard.enabled", comment = "{essentials.config.essentials.features.scoreboard.enabled}")
    private boolean scoreboardEnabled = true;

    @ConfigEntry(path = "features.scoreboard.auto-enable", comment = "{essentials.config.essentials.features.scoreboard.auto-enable}")
    private boolean scoreboardAutoEnable = true;

    @Range(min = 1, max = 60)
    @ConfigEntry(path = "features.scoreboard.update-interval", comment = "{essentials.config.essentials.features.scoreboard.update-interval}")
    private int scoreboardUpdateInterval = 1;

    // The Java default is the title every earlier version shipped: the framework writes it for a
    // missing key, and materializeText() then rewrites it in the server's language (maintainer
    // decision 2026-09-25, UltiKits/UltiEssentials#26).
    @NotEmpty
    @ConfigEntry(path = "features.scoreboard.title", comment = "{essentials.config.essentials.features.scoreboard.title}")
    private String scoreboardTitle = SHIPPED_SCOREBOARD_TITLE;

    @ConfigEntry(path = "features.scoreboard.lines", comment = "{essentials.config.essentials.features.scoreboard.lines}")
    private java.util.List<String> scoreboardLines = SHIPPED_SCOREBOARD_LINES;

    // ============ Scheduled Commands ============
    @ConfigEntry(path = "features.scheduled-commands.enabled", comment = "{essentials.config.essentials.features.scheduled-commands.enabled}")
    private boolean scheduledCommandsEnabled = false;

    @ConfigEntry(path = "features.scheduled-commands.commands",
        comment = "{essentials.config.essentials.features.scheduled-commands.commands}")
    private java.util.List<String> scheduledCommands = SHIPPED_SCHEDULED_COMMANDS;

    // ============ ChestLock 箱子锁 ============
    @ConfigEntry(path = "features.chestlock.enabled", comment = "{essentials.config.essentials.features.chestlock.enabled}")
    private boolean chestLockEnabled = true;

    @ConfigEntry(path = "features.chestlock.admin-bypass", comment = "{essentials.config.essentials.features.chestlock.admin-bypass}")
    private boolean chestLockAdminBypass = true;

    // ============ DeathPunish 死亡惩罚 ============
    @ConfigEntry(path = "features.deathpunish.enabled", comment = "{essentials.config.essentials.features.deathpunish.enabled}")
    private boolean deathPunishEnabled = false;

    @ConfigEntry(path = "features.deathpunish.money.enabled", comment = "{essentials.config.essentials.features.deathpunish.money.enabled}")
    private boolean deathPunishMoneyEnabled = false;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "features.deathpunish.money.percent", comment = "{essentials.config.essentials.features.deathpunish.money.percent}")
    private double deathPunishMoneyPercent = 10.0;

    @Range(min = 0, max = 1000000)
    @ConfigEntry(path = "features.deathpunish.money.max", comment = "{essentials.config.essentials.features.deathpunish.money.max}")
    private double deathPunishMoneyMax = 1000.0;

    @ConfigEntry(path = "features.deathpunish.item.enabled", comment = "{essentials.config.essentials.features.deathpunish.item.enabled}")
    private boolean deathPunishItemDropEnabled = false;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "features.deathpunish.item.drop-chance", comment = "{essentials.config.essentials.features.deathpunish.item.drop-chance}")
    private double deathPunishItemDropChance = 50.0;

    @ConfigEntry(path = "features.deathpunish.item.keep-other", comment = "{essentials.config.essentials.features.deathpunish.item.keep-other}")
    private boolean deathPunishKeepOtherItems = true;

    @ConfigEntry(path = "features.deathpunish.item.whitelist", comment = "{essentials.config.essentials.features.deathpunish.item.whitelist}")
    private java.util.List<String> deathPunishItemWhitelist = java.util.Arrays.asList(
        "DIAMOND_SWORD",
        "DIAMOND_PICKAXE"
    );

    @ConfigEntry(path = "features.deathpunish.exp.enabled", comment = "{essentials.config.essentials.features.deathpunish.exp.enabled}")
    private boolean deathPunishExpEnabled = false;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "features.deathpunish.exp.percent", comment = "{essentials.config.essentials.features.deathpunish.exp.percent}")
    private double deathPunishExpPercent = 20.0;

    @ConfigEntry(path = "features.deathpunish.command.enabled", comment = "{essentials.config.essentials.features.deathpunish.command.enabled}")
    private boolean deathPunishCommandEnabled = false;

    @ConfigEntry(path = "features.deathpunish.command.commands", comment = "{essentials.config.essentials.features.deathpunish.command.commands}")
    private java.util.List<String> deathPunishCommands = SHIPPED_DEATHPUNISH_COMMANDS;

    @ConfigEntry(path = "features.deathpunish.world-whitelist", comment = "{essentials.config.essentials.features.deathpunish.world-whitelist}")
    private java.util.List<String> deathPunishWorldWhitelist = java.util.Arrays.asList(
        "world_creative"
    );

    // ============ NamePrefix 头顶称号 ============
    @ConfigEntry(path = "features.nameprefix.enabled", comment = "{essentials.config.essentials.features.nameprefix.enabled}")
    private boolean namePrefixEnabled = false;

    @ConfigEntry(path = "features.nameprefix.prefix-format", comment = "{essentials.config.essentials.features.nameprefix.prefix-format}")
    private String namePrefixFormat = "&7[&e%vault_prefix%&7] ";

    @ConfigEntry(path = "features.nameprefix.suffix-format", comment = "{essentials.config.essentials.features.nameprefix.suffix-format}")
    private String nameSuffixFormat = "";

    @Range(min = 1, max = 60)
    @ConfigEntry(path = "features.nameprefix.update-interval", comment = "{essentials.config.essentials.features.nameprefix.update-interval}")
    private int namePrefixUpdateInterval = 5;

    // ============ CommandAlias 命令别名 ============
    @ConfigEntry(path = "features.commandalias.enabled", comment = "{essentials.config.essentials.features.commandalias.enabled}")
    private boolean commandAliasEnabled = true;

    @ConfigEntry(path = "features.commandalias.aliases", comment = "{essentials.config.essentials.features.commandalias.aliases}")
    private java.util.Map<String, String> commandAliases = new java.util.HashMap<String, String>() {{
        put("gmc", "gamemode creative");
        put("gms", "gamemode survival");
        put("gma", "gamemode adventure");
        put("gmsp", "gamemode spectator");
        put("day", "time set day");
        put("night", "time set night");
    }};

    // ============ 启动数据修复 Start-up data repair ============
    // Kept in the `features.` namespace because it is the only namespace this file uses; a lone
    // top-level key would be a convention decision of its own. What it controls is a repair, not a
    // feature -- see EntityIdBackfillService and FEATURES.md's `ultiessentials.storedkey.repair`.
    @ConfigEntry(path = "features.data-repair.enabled",
            comment = "{essentials.config.essentials.features.data-repair.enabled}")
    private boolean dataRepairEnabled = true;

    // ============ Config text materializer (maintainer decision 2026-09-25, UltiKits/UltiEssentials#26) ============

    /** The catalogue key of the scoreboard title's text in the server's language. */
    static final String SCOREBOARD_TITLE_KEY = "essentials.scoreboard.default_title";

    /** The catalogue key of the scoreboard lines' text, one entry with the lines separated by "\n". */
    static final String SCOREBOARD_LINES_KEY = "essentials.scoreboard.default_lines";

    /** The catalogue key of the scheduled commands' text, one entry with the entries separated by "\n". */
    static final String SCHEDULED_COMMANDS_KEY = "essentials.scheduled-commands.default_commands";

    /** The catalogue key of the death-punishment commands' text, one entry with the entries separated by "\n". */
    static final String DEATHPUNISH_COMMANDS_KEY = "essentials.deathpunish.default_commands";

    /**
     * The scoreboard title every earlier version shipped; the Java default of {@link #scoreboardTitle},
     * and one of the values {@link #materializeText} recognises as built-in text, compared byte for byte.
     */
    private static final String SHIPPED_SCOREBOARD_TITLE = "&6&l服务器信息";

    /**
     * The scoreboard lines every earlier version shipped; the Java default of {@link #scoreboardLines},
     * and one of the values {@link #materializeText} recognises as built-in text, compared byte for byte.
     */
    private static final java.util.List<String> SHIPPED_SCOREBOARD_LINES = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
        "&7欢迎, &e%player_name%",
        "&7",
        "&6在线玩家: &f%online_players%/%max_players%",
        "&6当前世界: &f%player_world%",
        "&7",
        "&6生命值: &c%player_health%",
        "&6饥饿值: &a%player_food%",
        "&6等级: &e%player_level%",
        "&7",
        "&ewww.example.com"
    ));

    /**
     * The scheduled commands every earlier version shipped; the Java default of {@link #scheduledCommands},
     * and one of the values {@link #materializeText} recognises as built-in text, compared byte for byte.
     * Only the words after {@code say}/{@code broadcast} are translated: the interval prefix, the command
     * verb and the colour code are the same in every language (maintainer decision 2026-09-25, "纳入，和其他
     * 文字同样处理").
     */
    private static final java.util.List<String> SHIPPED_SCHEDULED_COMMANDS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
        "300:say Server is online!",
        "600:broadcast &cReminder: follow server rules!"
    ));

    /**
     * The death-punishment command every earlier version shipped; the Java default of
     * {@link #deathPunishCommands}, and one of the values {@link #materializeText} recognises as built-in
     * text, compared byte for byte. Only the word after {@code say} is translated: {@code say} and
     * {@code {PLAYER}} are the same in every language (maintainer decision 2026-09-25, "纳入，和其他文字同样处理").
     */
    private static final java.util.List<String> SHIPPED_DEATHPUNISH_COMMANDS = java.util.Collections.singletonList(
        "say {PLAYER} 死亡了!"
    );

    /**
     * Writes the scoreboard title and lines, the scheduled-command text and the death-punishment command
     * text in the server's language (maintainer decision 2026-09-25, UltiKits/UltiEssentials#26): each is
     * replaced with {@code text}'s current text when it is still built-in text -- a default an earlier
     * version shipped, or this jar's text for it in any language -- and differs from the current text, and
     * (for the title) fits the field's own constraints. Any other value is the operator's and is kept, and
     * a blank {@code scoreboardTitle} or empty {@code scoreboardLines} is never materialized -- both are
     * the operator's way to show nothing, exactly as at {@code origin/master}, and {@code @NotEmpty}
     * already refuses a blank {@code scoreboardTitle} before this runs. Idempotent. Must run after the
     * module's language is loaded ({@code registerSelf()} and {@code onReload(ReloadReport)}), never from a change
     * listener; the caller saves the file when this returns {@code true}.
     *
     * @param text catalogue key to text in the server's language, from this jar's own catalogue
     *             ({@code ConfigTextDefaults#jarLanguage}), so every value written is in the tracked set
     * @return whether any value was rewritten
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(EssentialsConfig.class);
        boolean changed = false;

        String newTitle = ConfigTextDefaults.materialize(EssentialsConfig.class, "scoreboardTitle", scoreboardTitle,
                ConfigTextDefaults.currentText(text, "", SCOREBOARD_TITLE_KEY),
                ConfigTextDefaults.tracked(jar, "", SCOREBOARD_TITLE_KEY, SHIPPED_SCOREBOARD_TITLE));
        if (!Objects.equals(newTitle, scoreboardTitle)) {
            scoreboardTitle = newTitle;
            changed = true;
        }

        java.util.List<String> newLines = ConfigTextDefaults.materializeLines(EssentialsConfig.class, "scoreboardLines", scoreboardLines,
                ConfigTextDefaults.currentLines(text, SCOREBOARD_LINES_KEY),
                ConfigTextDefaults.trackedLines(jar, SCOREBOARD_LINES_KEY, SHIPPED_SCOREBOARD_LINES));
        if (!Objects.equals(newLines, scoreboardLines)) {
            scoreboardLines = newLines;
            changed = true;
        }

        java.util.List<String> newScheduled = ConfigTextDefaults.materializeLines(EssentialsConfig.class, "scheduledCommands", scheduledCommands,
                ConfigTextDefaults.currentLines(text, SCHEDULED_COMMANDS_KEY),
                ConfigTextDefaults.trackedLines(jar, SCHEDULED_COMMANDS_KEY, SHIPPED_SCHEDULED_COMMANDS));
        if (!Objects.equals(newScheduled, scheduledCommands)) {
            scheduledCommands = newScheduled;
            changed = true;
        }

        java.util.List<String> newDeathpunish = ConfigTextDefaults.materializeLines(EssentialsConfig.class, "deathPunishCommands", deathPunishCommands,
                ConfigTextDefaults.currentLines(text, DEATHPUNISH_COMMANDS_KEY),
                ConfigTextDefaults.trackedLines(jar, DEATHPUNISH_COMMANDS_KEY, SHIPPED_DEATHPUNISH_COMMANDS));
        if (!Objects.equals(newDeathpunish, deathPunishCommands)) {
            deathPunishCommands = newDeathpunish;
            changed = true;
        }

        return changed;
    }

    public EssentialsConfig() {
        super("config/essentials.yml");
    }
}
