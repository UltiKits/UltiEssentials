package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.commands.HideCommand;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener for first-join spawn teleport, and for keeping vanished players hidden from players who
 * join after they vanished.
 * <p>
 * Chat-related join/quit messages, welcome messages, and titles have been
 * moved to the UltiChat module.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@EventListener
public class JoinQuitListener implements Listener {

    @Autowired
    private EssentialsConfig config;

    @Autowired
    private SpawnConfig spawnConfig;

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // First join teleport to spawn
        if (config.isSpawnEnabled() && spawnConfig.isTeleportOnFirstJoin() && !player.hasPlayedBefore()) {
            if (spawnConfig.getSpawnLocation() != null && spawnConfig.getSpawnLocation().getWorld() != null) {
                player.teleport(spawnConfig.getSpawnLocation());
            }
        }
    }

    /**
     * Hides every player vanished with {@code /hide} from the player who has just joined, unless
     * the joiner may see vanished players (UltiKits/UltiEssentials#32).
     *
     * @param event the join
     */
    @EventHandler
    public void hideVanishedPlayers(PlayerJoinEvent event) {
        HideCommand.hideVanishedPlayersFrom(event.getPlayer());
    }
}
