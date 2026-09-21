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
            // Drop the cached team only if it is stale: once it was removed (for example with the
            // vanilla team command) it throws on every use, and the next update must look the team up
            // again or register a new one. A still-registered team is kept, because quit and shutdown
            // need it to remove the entry an earlier step of this update may already have added.
            Team cached = playerTeams.get(uuid);
            if (cached != null && isStale(uuid, cached)) {
                playerTeams.remove(uuid);
            }
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
            String teamName = teamNameFor(uuid);
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
    
    private static String teamNameFor(UUID uuid) {
        return "up_" + uuid.toString().substring(0, 8);
    }

    /**
     * Whether a cached team no longer is the team the scoreboard holds under its name. Compared by
     * the scoreboard's current team rather than by calling the cached object, which throws once its
     * team was removed; CraftBukkit's {@code CraftTeam#equals} compares the underlying team, so a team
     * re-registered under the same name is also detected.
     */
    private boolean isStale(UUID uuid, Team cached) {
        return !cached.equals(scoreboard.getTeam(teamNameFor(uuid)));
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
                // Name the player when they are online; only the UUID is known otherwise.
                Player player = Bukkit.getPlayer(recorded.getKey());
                Object who = player != null ? player.getName() : recorded.getKey();
                failureLog.error("Could not clear the name-prefix team of player {}; the other teams were still cleared",
                    who, e);
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
