package com.ultikits.plugins.essentials.service;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import lombok.extern.slf4j.Slf4j;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import com.ultikits.ultitools.annotations.PostConstruct;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing player scoreboards.
 * <p>
 * The private scoreboard this service assigns to a player belongs to this service alone (maintainer
 * decision 2026-09-27, UltiKits/UltiEssentials#65): another plugin writing onto it - an objective of its
 * own named {@code ultiessentials}, or a player placed in its own team on that board - is outside the
 * contract and may be overwritten. The service restores its own objective, sidebar slot and lines, and
 * changes only what it created there: its objective and the teams it copied from the main scoreboard.
 * <p>
 * 管理玩家计分板的服务。本服务换上的私有计分板只归本服务所有；其他插件不应往上面写东西。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Slf4j
@Service
public class ScoreboardService {
    
    @Autowired
    private EssentialsConfig config;

    @Autowired
    private UltiToolsPlugin plugin;

    private Plugin bukkitPlugin;

    /** The module whose sidebar shares the player's sidebar slot with this one. */
    private static final String OTHER_SIDEBAR_MODULE = "UltiSideBar";

    /** That module's configuration file, relative to its module folder. */
    private static final String OTHER_SIDEBAR_CONFIG = "config/sidebar.yml";

    /** That module's switch for its sidebar. */
    private static final String OTHER_SIDEBAR_KEY = "enabled";

    /** Longest entry text a scoreboard line keeps (the limit older clients enforce). */
    private static final int MAX_ENTRY_LENGTH = 40;

    // Player UUIDs with active scoreboards
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    // Players who turned the sidebar off in this session, so the delayed auto-enable on join leaves
    // them alone (UltiKits/UltiEssentials#45); forgotten when the player quits or turns it on
    private final Set<UUID> declinedPlayers = ConcurrentHashMap.newKeySet();

    // Each shown player's own sidebar board, reused across updates (UltiKits/UltiEssentials#40)
    private final Map<UUID, Scoreboard> playerBoards = new ConcurrentHashMap<>();

    // The line entries last written onto each player's board, so an unchanged sidebar is not rewritten
    private final Map<UUID, List<String>> shownLines = new ConcurrentHashMap<>();

    // The main-scoreboard teams copied onto each player's board, so only those are ever changed or removed
    private final Map<UUID, Map<String, Team>> copiedTeams = new ConcurrentHashMap<>();

    // Main update task
    private BukkitTask updateTask;

    // Set by shutdown() and cleared again only by reload(): once the module is unloaded, a delayed
    // join callback that still fires is a no-op (UltiKits/UltiEssentials#51)
    private volatile boolean shutDown;

    // Scoreboard manager
    private ScoreboardManager manager;

    // Instance reference to the class logger, so a test can observe the per-player failure reports
    // (the module's test classpath has no slf4j binding to capture them otherwise).
    private org.slf4j.Logger failureLog = log;

    // Players whose sidebar update is currently failing, so the update task logs each failure once.
    private final RepeatedFailureFilter<UUID> updateFailures = new RepeatedFailureFilter<>();
    
    /**
     * Initializes the scoreboard service.
     * Automatically called by the IoC container after construction.
     */
    @PostConstruct
    public void init() {
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        this.manager = Bukkit.getScoreboardManager();

        if (manager == null) {
            log.warn(plugin.i18n("essentials.log.scoreboard_manager_missing"));
            return;
        }
        
        // Start update task if enabled
        if (config.isScoreboardEnabled()) {
            startUpdateTask();
            Bukkit.getScheduler().runTask(bukkitPlugin, this::reportOtherSidebar);
        }
    }

    /**
     * Logs one line, on the first server tick after start-up (when every module has been loaded,
     * whatever their order), when UltiSideBar's sidebar is also enabled: each player keeps whichever
     * sidebar is shown first, and the other one waits (UltiKits/UltiEssentials#40).
     */
    private void reportOtherSidebar() {
        for (UltiToolsPlugin module : UltiToolsPlugin.getPluginManager().getPluginList()) {
            if (OTHER_SIDEBAR_MODULE.equals(module.getPluginName())
                    && isOtherSidebarEnabled(module.getResourceFolderPath())) {
                failureLog.info(plugin.i18n("essentials.log.scoreboard_other_sidebar"));
                return;
            }
        }
    }

    /**
     * Reads UltiSideBar's own switch for its sidebar from that module's configuration file; the
     * switch defaults to on, as it ships.
     */
    private static boolean isOtherSidebarEnabled(String moduleFolder) {
        File file = new File(moduleFolder, OTHER_SIDEBAR_CONFIG);
        if (!file.isFile()) {
            return true;
        }
        return YamlConfiguration.loadConfiguration(file).getBoolean(OTHER_SIDEBAR_KEY, true);
    }
    
    /**
     * Starts the scoreboard update task.
     */
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        int updateInterval = config.getScoreboardUpdateInterval();
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : enabledPlayers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        refreshIsolated(uuid, player);
                    } else {
                        enabledPlayers.remove(uuid);
                        playerBoards.remove(uuid);
                        shownLines.remove(uuid);
                        copiedTeams.remove(uuid);
                        updateFailures.forget(uuid);
                    }
                }
            }
        }.runTaskTimer(bukkitPlugin, 20L, updateInterval * 20L);
    }
    
    /**
     * Refreshes one player's sidebar for the update task. A failure stays with that player: it is
     * logged at error level the first time, suppressed while it repeats, and the player stays shown
     * and is retried on the next update; the other players are refreshed regardless.
     */
    private void refreshIsolated(UUID uuid, Player player) {
        try {
            updateScoreboard(player);
        } catch (RuntimeException e) {
            if (updateFailures.firstFailure(uuid)) {
                failureLog.error(plugin.i18n("essentials.log.sidebar_failed"), player.getName(), e);
            }
            return;
        }
        if (updateFailures.recovered(uuid)) {
            failureLog.info(plugin.i18n("essentials.log.sidebar_recovered"), player.getName());
        }
    }

    /**
     * Enables scoreboard for a player.
     */
    public void enableScoreboard(Player player) {
        if (shutDown || !config.isScoreboardEnabled()) {
            // After an unload, the delayed join enable must not put a player on a sidebar that
            // nothing updates or takes down any more (UltiKits/UltiEssentials#51).
            return;
        }
        
        declinedPlayers.remove(player.getUniqueId());
        enabledPlayers.add(player.getUniqueId());
        updateScoreboard(player);
    }
    
    /**
     * Disables scoreboard for a player.
     */
    public void disableScoreboard(Player player) {
        enabledPlayers.remove(player.getUniqueId());
        Scoreboard own = playerBoards.remove(player.getUniqueId());
        shownLines.remove(player.getUniqueId());
        copiedTeams.remove(player.getUniqueId());
        updateFailures.forget(player.getUniqueId());
        
        // Give back the server's main scoreboard, not a fresh empty one -- and only while this
        // module's own board is on screen (UltiKits/UltiEssentials#40).
        returnToMainScoreboard(player, own);
    }
    
    /**
     * Turns the sidebar off because the player asked to, whether or not it is on yet: the choice is
     * remembered for the rest of the session, so the delayed auto-enable on join does not turn it
     * back on a moment later (UltiKits/UltiEssentials#45).
     */
    public void declineScoreboard(Player player) {
        declinedPlayers.add(player.getUniqueId());
        disableScoreboard(player);
    }

    /**
     * Whether the player turned the sidebar off in this session.
     */
    public boolean hasDeclined(Player player) {
        return declinedPlayers.contains(player.getUniqueId());
    }

    /**
     * Forgets the player's choice for the session; called when the player quits.
     */
    public void forgetChoice(Player player) {
        declinedPlayers.remove(player.getUniqueId());
    }

    /**
     * Toggles scoreboard for a player.
     */
    public boolean toggleScoreboard(Player player) {
        if (isEnabled(player)) {
            declineScoreboard(player);
            return false;
        } else {
            enableScoreboard(player);
            return true;
        }
    }
    
    /**
     * Checks if scoreboard is enabled for a player.
     */
    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }
    
    /**
     * Updates the scoreboard for a player.
     * <p>
     * Each player keeps one sidebar board, built on the first update and reused afterwards: the
     * title and lines are rewritten in place (the lines only when they changed), and the board is
     * assigned to the player only when it is not already the one on screen. The main scoreboard's
     * teams are copied onto it on every update, so name prefixes and every other main-board team
     * stay visible while the sidebar is on (maintainer decision 2026-09-27,
     * UltiKits/UltiEssentials#40).
     */
    public void updateScoreboard(Player player) {
        if (manager == null || !enabledPlayers.contains(player.getUniqueId())) {
            return;
        }
        if (isSlotTakenByAnother(player)) {
            // Another scoreboard holds the sidebar slot; it stays until it is put away, and a later
            // update shows this sidebar then (UltiKits/UltiEssentials#40, UltiKits/UltiSideBar#26).
            return;
        }

        String title = colorize(parsePlaceholders(player, config.getScoreboardTitle()));
        Scoreboard scoreboard = playerBoards.get(player.getUniqueId());
        Objective objective;
        // Set when the board no longer shows the remembered lines, so they are drawn again in full.
        boolean redraw = false;
        // Set when the objective was created in this update and so holds no score yet.
        boolean freshObjective = false;
        if (scoreboard == null) {
            scoreboard = manager.getNewScoreboard();
            objective = scoreboard.registerNewObjective("ultiessentials", "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            playerBoards.put(player.getUniqueId(), scoreboard);
            copiedTeams.remove(player.getUniqueId());
            freshObjective = true;
        } else {
            objective = scoreboard.getObjective("ultiessentials");
            if (objective == null) {
                // Another plugin removed the objective from this board. The new one starts empty, so
                // the remembered lines no longer describe what is shown: forget them and draw every line.
                objective = scoreboard.registerNewObjective("ultiessentials", "dummy", title);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                redraw = true;
                freshObjective = true;
            } else if (!title.equals(objective.getDisplayName())) {
                objective.setDisplayName(title);
            }
            // The rest of what another plugin can change on this board while the player views it: the
            // sidebar slot (cleared, or given to another objective) and the scores (reset). The copied
            // teams and the title are rewritten on every update anyway.
            if (!objective.equals(scoreboard.getObjective(DisplaySlot.SIDEBAR))) {
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
            List<String> shown = shownLines.get(player.getUniqueId());
            if (shown != null && !redraw) {
                for (String entry : shown) {
                    if (!objective.getScore(entry).isScoreSet()) {
                        redraw = true;
                        break;
                    }
                }
            }
        }

        // The main board's teams go on first, and every entry of a team on this board counts as taken:
        // a line equal to one (a %player_name% line for a player with a name prefix) would otherwise be
        // that team member's own entry, and the copied team's prefix, suffix and colour would format the
        // sidebar row. Such a line gets a distinct entry that shows the same text.
        MainTeamMirror.mirror(manager.getMainScoreboard(), scoreboard,
                copiedTeams.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>()));
        List<String> lines = config.getScoreboardLines();
        List<String> entries = new ArrayList<>();
        Set<String> usedEntries = new HashSet<>();
        for (Team team : scoreboard.getTeams()) {
            usedEntries.addAll(team.getEntries());
        }
        for (String line : lines) {
            String parsedLine = parsePlaceholders(player, line);
            parsedLine = colorize(parsedLine);
            
            // Handle duplicate lines by adding invisible characters
            entries.add(ensureUnique(usedEntries, parsedLine));
        }
        List<String> previous = shownLines.get(player.getUniqueId());
        if (redraw || !entries.equals(previous)) {
            // The previous lines go with this module's own objective, replaced by an empty one. Resetting
            // them with Scoreboard#resetScores would clear each entry on every objective of the board,
            // another plugin's included, whenever that plugin scores the same name.
            if (previous != null && !freshObjective) {
                objective.unregister();
                objective = scoreboard.registerNewObjective("ultiessentials", "dummy", title);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
            int score = entries.size();
            for (String entry : entries) {
                Score scoreEntry = objective.getScore(entry);
                scoreEntry.setScore(score--);
            }
            shownLines.put(player.getUniqueId(), entries);
        }

        if (!scoreboard.equals(player.getScoreboard())) {
            player.setScoreboard(scoreboard);
        }
    }
    
    /**
     * Whether another plugin's scoreboard holds the player's sidebar slot: the player views neither
     * the server's main scoreboard nor this module's own board. The first sidebar shown keeps the
     * slot; this module's sidebar waits (maintainer decision 2026-09-27, UltiKits/UltiEssentials#40).
     *
     * @param player the player
     * @return {@code true} if this module's sidebar yields to the scoreboard on screen
     */
    public boolean isSlotTakenByAnother(Player player) {
        Scoreboard current = player.getScoreboard();
        if (current == null || manager == null || current.equals(playerBoards.get(player.getUniqueId()))) {
            return false;
        }
        return !current.equals(manager.getMainScoreboard());
    }

    /**
     * Returns the player to the main scoreboard, but only while this module's own board is the one
     * on screen: another plugin's scoreboard is left where it is.
     */
    private void returnToMainScoreboard(Player player, Scoreboard own) {
        if (manager != null && own != null && own.equals(player.getScoreboard())) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    /**
     * Makes a line unique among the lines of this update: it is truncated to the entry length limit
     * first and only then compared, so two lines that differ only after the limit still become two
     * entries (UltiKits/UltiEssentials#41). A duplicate gets an invisible colour code appended, and
     * enough of its text is cut to keep the whole entry within the limit.
     */
    private String ensureUnique(Set<String> usedEntries, String line) {
        String base = truncate(line, MAX_ENTRY_LENGTH);
        String result = base;
        int attempt = 0;

        while (usedEntries.contains(result) && attempt < 16) {
            String code = ChatColor.values()[attempt].toString();
            result = truncate(base, MAX_ENTRY_LENGTH - code.length()) + code;
            attempt++;
        }

        usedEntries.add(result);
        return result;
    }

    /**
     * Cuts text to at most {@code max} characters, dropping a trailing colour-code character that
     * the cut would leave without its code.
     */
    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        if (cut.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }
    
    /**
     * Fills this module's own placeholders, then PlaceholderAPI's when it is installed, so the shipped
     * default lines show values whether or not PlaceholderAPI is there (UltiKits/UltiEssentials#59).
     */
    private String parsePlaceholders(Player player, String text) {
        String filled = BuiltInPlaceholders.forScoreboard(player, text);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, filled);
        }
        return filled;
    }
    
    /**
     * Colorizes a string with color codes.
     */
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Stops the update task and cleans up.
     */
    public void shutdown() {
        shutDown = true;
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        
        // Return every player who is viewing this module's sidebar to the main scoreboard
        for (UUID uuid : enabledPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && manager != null) {
                try {
                    returnToMainScoreboard(player, playerBoards.get(uuid));
                } catch (RuntimeException e) {
                    failureLog.error(plugin.i18n("essentials.log.scoreboard_reset_failed"), player.getName(), e);
                }
            }
        }
        
        enabledPlayers.clear();
        playerBoards.clear();
        shownLines.clear();
        copiedTeams.clear();
        updateFailures.clear();
    }
    
    /**
     * Reloads the scoreboard configuration.
     * <p>
     * While the scoreboard stays enabled, each online player keeps the sidebar shown or hidden as it
     * was before the reload, whatever the reloaded {@code auto-enable} says: the only per-player state
     * this service holds is {@link #enabledPlayers}, in memory, and a {@code /scoreboard} choice is
     * recorded there in the same way as an automatic enable on join. When the reload turns the
     * scoreboard on, no player had a sidebar to keep, so online players follow the reloaded
     * {@code auto-enable}; players joining after any reload follow it through
     * {@code ScoreboardListener} (UltiKits/UltiEssentials#28). A player whose sidebar cannot be
     * rebuilt is logged and stays marked as shown, so the update task retries it every interval,
     * and the players after them are still restored.
     * <p>
     * 重载时保留每位在线玩家的侧边栏显示/隐藏状态；重载开启计分板时在线玩家按 auto-enable 处理。
     */
    public void reload() {
        boolean wasRunning = updateTask != null;
        Set<UUID> shownBeforeReload = new HashSet<>(enabledPlayers);
        shutdown();
        shutDown = false;
        
        if (config.isScoreboardEnabled()) {
            startUpdateTask();
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean show = wasRunning
                    ? shownBeforeReload.contains(player.getUniqueId())
                    : config.isScoreboardAutoEnable();
                if (show) {
                    try {
                        enableScoreboard(player);
                    } catch (RuntimeException e) {
                        // enableScoreboard has already marked the player as shown, so the update
                        // task retries the sidebar every interval; the other players are unaffected.
                        failureLog.error(plugin.i18n("essentials.log.sidebar_reload_failed"), player.getName(), e);
                    }
                }
            }
        }
    }
}
