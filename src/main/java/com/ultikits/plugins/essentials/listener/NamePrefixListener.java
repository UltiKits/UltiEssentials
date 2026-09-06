package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import com.ultikits.ultitools.annotations.PostConstruct;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Listener for name prefix events.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@EventListener
public class NamePrefixListener implements Listener {
    
    @Autowired
    private EssentialsConfig config;
    
    @Autowired
    private NamePrefixService namePrefixService;

    private Plugin bukkitPlugin;

    /**
     * Resolves the framework's plugin handle once, under its actual registered name (the
     * artifact name, {@code "UltiTools-API"}, is not the registered plugin name -- see
     * {@code ScoreboardService}, {@code NamePrefixService}, {@code TeleportService},
     * {@code TpaService} and {@code HideCommand} for the same, already-correct lookup). Failing
     * here, at construction, means a broken lookup stops the server starting instead of throwing
     * once per player join indefinitely.
     *
     * @throws IllegalStateException if the framework plugin cannot be resolved by name
     */
    @PostConstruct
    public void init() {
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        if (this.bukkitPlugin == null) {
            throw new IllegalStateException(
                "Could not resolve the UltiTools framework plugin by its registered name "
                    + "\"UltiTools\" -- name-prefix-on-join scheduling cannot work.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isNamePrefixEnabled()) {
            return;
        }
        
        // Delay a bit to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(
            bukkitPlugin,
            () -> namePrefixService.updatePlayer(event.getPlayer()),
            10L
        );
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        namePrefixService.removePlayer(event.getPlayer());
    }
}
