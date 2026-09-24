package com.ultikits.plugins.essentials.listener;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.TabBarConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener for customizing player tab list header and footer.
 */
@EventListener
public class TabBarListener implements Listener {

    @Autowired
    private EssentialsConfig config;
    
    @Autowired
    private TabBarConfig tabBarConfig;

    @Autowired
    private UltiToolsPlugin plugin;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isTabBarEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        updateTabBar(player);
    }

    /**
     * Updates the player's tab list header and footer.
     *
     * @param player the player to update
     */
    public void updateTabBar(Player player) {
        // A blank value shows the language file's text, resolved here when the tab list is drawn and
        // never while the configuration reloads (maintainer ruling 2026-09-24 (d)).
        String header = tabBarConfig.getHeader();
        if (header == null || header.trim().isEmpty()) {
            header = plugin.i18n("essentials.tabbar.default_header");
        }
        String footer = tabBarConfig.getFooter();
        if (footer == null || footer.trim().isEmpty()) {
            footer = plugin.i18n("essentials.tabbar.default_footer");
        }

        // Replace variables
        header = header
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));

        footer = footer
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));

        // Color codes
        header = ChatColor.translateAlternateColorCodes('&', header);
        footer = ChatColor.translateAlternateColorCodes('&', footer);

        player.setPlayerListHeaderFooter(header, footer);
    }
}
