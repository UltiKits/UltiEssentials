package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.commands.BackCommand;
import com.ultikits.plugins.essentials.commands.HideCommand;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener for cleaning up player data when they quit.
 */
@EventListener
public class PlayerQuitListener implements Listener {

    @Autowired
    private TpaService tpaService;

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Clean up BackCommand temporary data
        BackCommand.removePlayer(uuid);

        // Clean up HideCommand vanish state
        HideCommand.removePlayer(uuid);

        // Clear every pending /tpa request the player sent or received, and cancel its timeout
        // task, instead of leaving the other party's request "busy" until it times out
        // (UltiKits/UltiEssentials#30).
        tpaService.onPlayerQuit(uuid);
    }
}
