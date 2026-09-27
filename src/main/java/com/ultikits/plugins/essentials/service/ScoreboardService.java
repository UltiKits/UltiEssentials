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
 * 管理玩家计分板的服务。
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

    // Each shown player's own sidebar board, reused across updates (UltiKits/UltiEssentials#40)
    private final Map<UUID, Scoreboard> playerBoards = new ConcurrentHashMap<>();

    // The line entries last written onto each player's board, so an unchanged sidebar is not rewritten
    private final Map<UUID, List<String>> shownLines = new ConcurrentHashMap<>();

    // Main update task
    private BukkitTask updateTask;

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
        if (!config.isScoreboardEnabled()) {
            return;
        }
        
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
        updateFailures.forget(player.getUniqueId());
        
        // Give back the server's main scoreboard, not a fresh empty one -- and only while this
        // module's own board is on screen (UltiKits/UltiEssentials#40).
        returnToMainScoreboard(player, own);
    }
    
    /**
     * Toggles scoreboard for a player.
     */
    public boolean toggleScoreboard(Player player) {
        if (isEnabled(player)) {
            disableScoreboard(player);
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
        if (scoreboard == null) {
            scoreboard = manager.getNewScoreboard();
            objective = scoreboard.registerNewObjective("ultiessentials", "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            playerBoards.put(player.getUniqueId(), scoreboard);
        } else {
            objective = scoreboard.getObjective("ultiessentials");
            if (objective == null) {
                objective = scoreboard.registerNewObjective("ultiessentials", "dummy", title);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else if (!title.equals(objective.getDisplayName())) {
                objective.setDisplayName(title);
            }
        }

        List<String> lines = config.getScoreboardLines();
        List<String> entries = new ArrayList<>();
        Set<String> usedEntries = new HashSet<>();
        for (String line : lines) {
            String parsedLine = parsePlaceholders(player, line);
            parsedLine = colorize(parsedLine);
            
            // Handle duplicate lines by adding invisible characters
            entries.add(ensureUnique(usedEntries, parsedLine));
        }
        if (!entries.equals(shownLines.get(player.getUniqueId()))) {
            for (String stale : new HashSet<>(scoreboard.getEntries())) {
                scoreboard.resetScores(stale);
            }
            int score = entries.size();
            for (String entry : entries) {
                Score scoreEntry = objective.getScore(entry);
                scoreEntry.setScore(score--);
            }
            shownLines.put(player.getUniqueId(), entries);
        }

        MainTeamMirror.mirror(manager.getMainScoreboard(), scoreboard);
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
     * Parses PlaceholderAPI placeholders.
     */
    private String parsePlaceholders(Player player, String text) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        
        // Fallback - basic placeholders
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_health%", String.valueOf((int) player.getHealth()));
        text = text.replace("%player_food%", String.valueOf(player.getFoodLevel()));
        text = text.replace("%player_level%", String.valueOf(player.getLevel()));
        text = text.replace("%player_world%", player.getWorld().getName());
        text = text.replace("%online_players%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()));
        
        return text;
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
