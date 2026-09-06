package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
     * RED-phase stub (13-11 TDD cycle) -- resolves under the (wrong) artifact name and
     * performs no null check yet. See the GREEN commit for the real implementation.
     */
    @com.ultikits.ultitools.annotations.PostConstruct
    public void init() {
        this.bukkitPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools-API");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isNamePrefixEnabled()) {
            return;
        }
        
        // Delay a bit to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("UltiTools-API"),
            () -> namePrefixService.updatePlayer(event.getPlayer()),
            10L
        );
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        namePrefixService.removePlayer(event.getPlayer());
    }
}
