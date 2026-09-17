package com.ultikits.plugins.essentials.service;

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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.ultikits.ultitools.annotations.PostConstruct;
import java.util.*;

/**
 * Service for managing player name prefixes/suffixes.
 * <p>
 * 管理玩家头顶称号的服务。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Slf4j
@Service
public class NamePrefixService {
    
    @Autowired
    private EssentialsConfig config;

    private Plugin bukkitPlugin;
    private BukkitTask updateTask;
    private Scoreboard scoreboard;

    // Instance reference to the class logger, so a test can observe the per-player failure reports
    // (the module's test classpath has no slf4j binding to capture them otherwise).
    private org.slf4j.Logger failureLog = log;

    // Players whose prefix update is currently failing, so the update task logs each failure once.
    private final RepeatedFailureFilter<UUID> updateFailures = new RepeatedFailureFilter<>();
    
    // Player teams
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    
    /**
     * Initializes the name prefix service.
     * Automatically called by the IoC container after construction.
     */
    @PostConstruct
    public void init() {
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");

        if (!config.isNamePrefixEnabled()) {
            return;
        }

        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        startUpdateTask();
    }
    
    /**
     * Starts the update task.
     */
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayers();
            }
        }.runTaskTimer(bukkitPlugin, 20L, config.getNamePrefixUpdateInterval() * 20L);
    }
    
    /**
     * Updates name prefix/suffix for all online players.
     */
    private void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateIsolated(player);
        }
    }

    /**
     * Updates one player's prefix for the update task. A failure stays with that player: it is logged
     * at error level the first time, suppressed while it repeats, and retried on the next update; the
     * other players are updated regardless.
     */
    private void updateIsolated(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            updatePlayer(player);
        } catch (RuntimeException e) {
            if (updateFailures.firstFailure(uuid)) {
                failureLog.error("Could not update the name prefix for {}; it will be retried on every update, "
                    + "and this failure is not logged again until an update for that player succeeds",
                    player.getName(), e);
            }
            return;
        }
        if (updateFailures.recovered(uuid)) {
            failureLog.info("The name prefix for {} updates again", player.getName());
        }
    }
    
    /**
     * Updates name prefix/suffix for a player.
     */
    public void updatePlayer(Player player) {
        if (!config.isNamePrefixEnabled()) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        
        // Get or create team
        Team team = playerTeams.get(uuid);
        if (team == null) {
            String teamName = "up_" + uuid.toString().substring(0, 8);
            team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            playerTeams.put(uuid, team);
        }
        
        // Add player to team
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
        
        // Set prefix
        String prefix = config.getNamePrefixFormat();
        prefix = parsePlaceholders(player, prefix);
        prefix = colorize(prefix);
        
        // Truncate if too long (16 chars limit for older versions, 64 for newer)
        if (prefix.length() > 64) {
            prefix = prefix.substring(0, 64);
        }
        team.setPrefix(prefix);
        
        // Set suffix
        String suffix = config.getNameSuffixFormat();
        suffix = parsePlaceholders(player, suffix);
        suffix = colorize(suffix);
        
        if (suffix.length() > 64) {
            suffix = suffix.substring(0, 64);
        }
        team.setSuffix(suffix);
    }
    
    /**
     * Removes a player from the system.
     */
    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        updateFailures.forget(uuid);
        Team team = playerTeams.remove(uuid);
        
        if (team != null) {
            team.removeEntry(player.getName());
            // Don't unregister team, just remove entry
        }
    }
    
    /**
     * Parses PlaceholderAPI placeholders.
     */
    private String parsePlaceholders(Player player, String text) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text.replace("%player_name%", player.getName());
    }
    
    /**
     * Colorizes a string with color codes.
     */
    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Shuts down the service.
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        
        // Clean up teams, one at a time: a team that can no longer be cleared (for example removed with
        // the vanilla team command) must not keep the others populated.
        for (Map.Entry<UUID, Team> recorded : playerTeams.entrySet()) {
            try {
                Team team = recorded.getValue();
                for (String entry : team.getEntries()) {
                    team.removeEntry(entry);
                }
            } catch (RuntimeException e) {
                failureLog.error("Could not clear the name-prefix team of player {}; the other teams were still cleared",
                    recorded.getKey(), e);
            }
        }
        playerTeams.clear();
        updateFailures.clear();
    }
    
    /**
     * Reloads the service.
     */
    public void reload() {
        shutdown();
        if (config.isNamePrefixEnabled()) {
            init();
        }
    }
}
