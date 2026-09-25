package com.ultikits.plugins.essentials.service;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import lombok.extern.slf4j.Slf4j;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import com.ultikits.ultitools.annotations.PostConstruct;
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

    // Player UUIDs with active scoreboards
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

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
        }
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
        updateFailures.forget(player.getUniqueId());
        
        // Give back the server's main scoreboard, not a fresh empty one: name-prefix teams and any
        // other main-scoreboard content are only visible on the main scoreboard.
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
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
     */
    public void updateScoreboard(Player player) {
        if (manager == null || !enabledPlayers.contains(player.getUniqueId())) {
            return;
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        String title = parsePlaceholders(player, config.getScoreboardTitle());

        Objective objective = scoreboard.registerNewObjective(
            "ultiessentials",
            "dummy",
            colorize(title)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = config.getScoreboardLines();
        int score = lines.size();
        
        for (String line : lines) {
            String parsedLine = parsePlaceholders(player, line);
            parsedLine = colorize(parsedLine);
            
            // Handle duplicate lines by adding invisible characters
            parsedLine = ensureUnique(scoreboard, parsedLine);
            
            Score scoreEntry = objective.getScore(parsedLine);
            scoreEntry.setScore(score--);
        }
        
        player.setScoreboard(scoreboard);
    }
    
    /**
     * Ensures a line is unique by adding invisible characters if necessary.
     */
    private String ensureUnique(Scoreboard scoreboard, String line) {
        String original = line;
        String result = line;
        int attempt = 0;

        while (scoreboard.getEntries().contains(result) && attempt < 16) {
            result = original + ChatColor.values()[attempt].toString();
            attempt++;
        }

        // Truncate if too long (scoreboard limit is 40 characters in modern MC)
        if (result.length() > 40) {
            result = result.substring(0, 40);
        }

        return result;
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
        
        // Return every player who had a sidebar to the main scoreboard
        for (UUID uuid : enabledPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && manager != null) {
                try {
                    player.setScoreboard(manager.getMainScoreboard());
                } catch (RuntimeException e) {
                    failureLog.error(plugin.i18n("essentials.log.scoreboard_reset_failed"), player.getName(), e);
                }
            }
        }
        
        enabledPlayers.clear();
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
