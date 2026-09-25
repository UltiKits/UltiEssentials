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
    @ConfigEntry(path = "features.back.enabled", comment = "启用 /back 返回命令")
    private boolean backEnabled = true;

    @ConfigEntry(path = "features.spawn.enabled", comment = "启用 /spawn 出生点命令")
    private boolean spawnEnabled = true;

    @ConfigEntry(path = "features.lobby.enabled", comment = "启用 /lobby 主城命令")
    private boolean lobbyEnabled = true;

    @ConfigEntry(path = "features.wild.enabled", comment = "启用 /wild 随机传送")
    private boolean wildEnabled = true;

    @Range(min = 100, max = 100000)
    @ConfigEntry(path = "features.wild.max-range", comment = "随机传送最大范围")
    private int wildMaxRange = 10000;

    @Range(min = 10, max = 10000)
    @ConfigEntry(path = "features.wild.min-range", comment = "随机传送最小范围")
    private int wildMinRange = 100;

    // Seconds between two /wild uses by one player; 0 means no cooldown. Bound to /wild through the
    // framework's config-bound @CmdCD on WildCommand#wildTeleport, so this field is the only place
    // the default lives, and /ul reload applies a new value (UltiKits/UltiEssentials#27,
    // UltiKits/UltiTools-Reborn#531). Must stay an int: the binding accepts only integral fields.
    // Deliberately no @Range: the binding owns the range (0 to Integer.MAX_VALUE), refusing the
    // module at load and keeping the running value with a WARNING on reload. A module @Range would
    // pre-empt that on reload by aborting the whole reload part-way.
    @ConfigEntry(path = "features.wild.cooldown", comment = "随机传送冷却时间(秒)，0 为不冷却")
    private int wildCooldown = 60;

    // features.recall.enabled was removed in 6.3.0: there is no /recall command. A copy left in an
    // operator's file is reported by RemovedConfigKeys (UltiKits/UltiEssentials#27).

    // ============ 玩家状态功能 ============
    @ConfigEntry(path = "features.fly.enabled", comment = "启用 /fly 飞行命令")
    private boolean flyEnabled = true;

    @ConfigEntry(path = "features.heal.enabled", comment = "启用 /heal 治疗命令")
    private boolean healEnabled = true;

    @ConfigEntry(path = "features.speed.enabled", comment = "启用 /speed 速度命令")
    private boolean speedEnabled = true;

    @Range(min = 1, max = 10)
    @ConfigEntry(path = "features.speed.max-speed", comment = "最大速度倍数")
    private int speedMaxSpeed = 10;

    @ConfigEntry(path = "features.gamemode.enabled", comment = "启用 /gm 游戏模式命令")
    private boolean gamemodeEnabled = true;

    @ConfigEntry(path = "features.hide.enabled", comment = "启用 /hide 隐身命令")
    private boolean hideEnabled = true;

    // ============ 管理类功能 ============
    @ConfigEntry(path = "features.invsee.enabled", comment = "启用 /invsee 查看背包命令")
    private boolean invseeEnabled = true;

    @ConfigEntry(path = "features.whitelist.enabled", comment = "启用 /wl 白名单命令")
    private boolean whitelistEnabled = true;

    // ============ 监听器功能 ============
    @ConfigEntry(path = "features.motd.enabled", comment = "启用 MOTD 自定义")
    private boolean motdEnabled = true;

    @ConfigEntry(path = "features.tab-bar.enabled", comment = "启用 Tab 栏自定义")
    private boolean tabBarEnabled = true;

    // ============ Home 系统功能 ============
    @ConfigEntry(path = "features.home.enabled", comment = "启用 /home 家系统")
    private boolean homeEnabled = true;

    @Range(min = 1, max = 100)
    @ConfigEntry(path = "features.home.default-max-homes", comment = "默认最大家数量")
    private int homeDefaultMaxHomes = 3;

    @Range(min = 0, max = 60)
    @ConfigEntry(path = "features.home.teleport-warmup", comment = "传送预热时间(秒)，0为立即传送")
    private int homeTeleportWarmup = 3;

    @ConfigEntry(path = "features.home.cancel-on-move", comment = "移动时取消传送")
    private boolean homeCancelOnMove = true;

    // ============ TPA 传送功能 ============
    @ConfigEntry(path = "features.tpa.enabled", comment = "启用 /tpa 传送请求")
    private boolean tpaEnabled = true;

    @Range(min = 5, max = 300)
    @ConfigEntry(path = "features.tpa.timeout", comment = "传送请求超时时间(秒)")
    private int tpaTimeout = 30;

    @Range(min = 0, max = 600)
    @ConfigEntry(path = "features.tpa.cooldown", comment = "发送请求冷却时间(秒)")
    private int tpaCooldown = 10;

    @ConfigEntry(path = "features.tpa.allow-cross-world", comment = "允许跨世界传送")
    private boolean tpaAllowCrossWorld = true;

    // ============ Warp 地标功能 ============
    @ConfigEntry(path = "features.warp.enabled", comment = "启用 /warp 地标系统")
    private boolean warpEnabled = true;

    @Range(min = 0, max = 60)
    @ConfigEntry(path = "features.warp.teleport-warmup", comment = "地标传送预热时间(秒)")
    private int warpTeleportWarmup = 3;

    // ============ Ban 封禁系统 ============
    @ConfigEntry(path = "features.ban.enabled", comment = "启用封禁系统")
    private boolean banEnabled = true;

    @ConfigEntry(path = "features.ban.broadcast-ban", comment = "广播封禁消息")
    private boolean banBroadcast = true;

    @ConfigEntry(path = "features.ban.broadcast-unban", comment = "广播解禁消息")
    private boolean unbanBroadcast = true;

    // ============ Scoreboard 计分板 ============
    @ConfigEntry(path = "features.scoreboard.enabled", comment = "启用计分板功能")
    private boolean scoreboardEnabled = true;

    @ConfigEntry(path = "features.scoreboard.auto-enable", comment = "玩家进入时自动启用计分板")
    private boolean scoreboardAutoEnable = true;

    @Range(min = 1, max = 60)
    @ConfigEntry(path = "features.scoreboard.update-interval", comment = "计分板更新间隔(秒)")
    private int scoreboardUpdateInterval = 1;

    // The Java default is the title every earlier version shipped: the framework writes it for a
    // missing key, and materializeText() then rewrites it in the server's language (maintainer
    // decision 2026-09-25, UltiKits/UltiEssentials#26).
    @NotEmpty
    @ConfigEntry(path = "features.scoreboard.title", comment = "计分板标题 (支持PlaceholderAPI)")
    private String scoreboardTitle = SHIPPED_SCOREBOARD_TITLE;

    @ConfigEntry(path = "features.scoreboard.lines", comment = "计分板内容行 (支持PlaceholderAPI)")
    private java.util.List<String> scoreboardLines = SHIPPED_SCOREBOARD_LINES;

    // ============ Scheduled Commands ============
    @ConfigEntry(path = "features.scheduled-commands.enabled", comment = "Enable scheduled command execution / 启用定时命令执行")
    private boolean scheduledCommandsEnabled = false;

    @ConfigEntry(path = "features.scheduled-commands.commands",
        comment = "Scheduled commands, format: interval_seconds:command / 定时命令列表，格式: 间隔秒数:命令")
    private java.util.List<String> scheduledCommands = SHIPPED_SCHEDULED_COMMANDS;

    // ============ ChestLock 箱子锁 ============
    @ConfigEntry(path = "features.chestlock.enabled", comment = "启用箱子锁功能")
    private boolean chestLockEnabled = true;

    @ConfigEntry(path = "features.chestlock.admin-bypass", comment = "管理员可以绕过锁定")
    private boolean chestLockAdminBypass = true;

    // ============ DeathPunish 死亡惩罚 ============
    @ConfigEntry(path = "features.deathpunish.enabled", comment = "启用死亡惩罚")
    private boolean deathPunishEnabled = false;

    @ConfigEntry(path = "features.deathpunish.money.enabled", comment = "启用金币惩罚")
    private boolean deathPunishMoneyEnabled = false;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "features.deathpunish.money.percent", comment = "掉落金币百分比")
    private double deathPunishMoneyPercent = 10.0;

    @Range(min = 0, max = 1000000)
    @ConfigEntry(path = "features.deathpunish.money.max", comment = "最大掉落金币 (0为无限制)")
    private double deathPunishMoneyMax = 1000.0;

    @ConfigEntry(path = "features.deathpunish.item.enabled", comment = "启用物品掉落惩罚")
    private boolean deathPunishItemDropEnabled = false;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "features.deathpunish.item.drop-chance", comment = "物品掉落概率(%)")
    private double deathPunishItemDropChance = 50.0;

    @ConfigEntry(path = "features.deathpunish.item.keep-other", comment = "保留未掉落的物品")
    private boolean deathPunishKeepOtherItems = true;

    @ConfigEntry(path = "features.deathpunish.item.whitelist", comment = "物品白名单(不会掉落)")
    private java.util.List<String> deathPunishItemWhitelist = java.util.Arrays.asList(
        "DIAMOND_SWORD",
        "DIAMOND_PICKAXE"
    );

    @ConfigEntry(path = "features.deathpunish.exp.enabled", comment = "启用经验惩罚")
    private boolean deathPunishExpEnabled = false;

    @Range(min = 0, max = 100)
    @ConfigEntry(path = "features.deathpunish.exp.percent", comment = "额外经验损失百分比")
    private double deathPunishExpPercent = 20.0;

    @ConfigEntry(path = "features.deathpunish.command.enabled", comment = "启用命令惩罚")
    private boolean deathPunishCommandEnabled = false;

    @ConfigEntry(path = "features.deathpunish.command.commands", comment = "死亡执行的命令 ({PLAYER}为玩家名)")
    private java.util.List<String> deathPunishCommands = SHIPPED_DEATHPUNISH_COMMANDS;

    @ConfigEntry(path = "features.deathpunish.world-whitelist", comment = "不进行惩罚的世界")
    private java.util.List<String> deathPunishWorldWhitelist = java.util.Arrays.asList(
        "world_creative"
    );

    // ============ NamePrefix 头顶称号 ============
    @ConfigEntry(path = "features.nameprefix.enabled", comment = "启用头顶称号")
    private boolean namePrefixEnabled = false;

    @ConfigEntry(path = "features.nameprefix.prefix-format", comment = "前缀格式 (支持PlaceholderAPI)")
    private String namePrefixFormat = "&7[&e%vault_prefix%&7] ";

    @ConfigEntry(path = "features.nameprefix.suffix-format", comment = "后缀格式 (支持PlaceholderAPI)")
    private String nameSuffixFormat = "";

    @Range(min = 1, max = 60)
    @ConfigEntry(path = "features.nameprefix.update-interval", comment = "更新间隔(秒)")
    private int namePrefixUpdateInterval = 5;

    // ============ CommandAlias 命令别名 ============
    @ConfigEntry(path = "features.commandalias.enabled", comment = "启用命令别名")
    private boolean commandAliasEnabled = true;

    @ConfigEntry(path = "features.commandalias.aliases", comment = "命令别名映射 (别名: 原命令)")
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
            comment = "启动时修复缺少主键的旧记录（家、地标点、封禁、容器锁）；关闭后这些记录无法删除或更新")
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
     * module's language is loaded ({@code registerSelf()} and {@code onReload()}), never from a change
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
