package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.OwnTeleports;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.EventListener;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command to teleport player back to their previous location.
 * Also acts as an event listener to track teleport locations.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"back"}, permission = "ultiessentials.back", description = "essentials.command.back.description")
@I18n("back.description")
@EventListener
public class BackCommand extends BaseEssentialsCommand implements Listener {

    /**
     * Temporarily stores player's last teleport location.
     * Data is lost on player disconnect or server restart (by design).
     */
    private static final Map<UUID, Location> LAST_LOCATIONS = new ConcurrentHashMap<>();

    private final EssentialsConfig config;

    public BackCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void back(@CmdSender Player player) {
        if (!config.isBackEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        Location lastLocation = LAST_LOCATIONS.get(player.getUniqueId());
        if (lastLocation == null) {
            player.sendMessage(i18n("essentials.back.no_location"));
            return;
        }

        // /back is one of this module's own teleports: the next /back returns to where this one
        // started, as before (UltiKits/UltiEssentials#39).
        OwnTeleports.teleport(player, lastLocation);
        player.sendMessage(i18n("essentials.back.success"));
    }

    /**
     * Listens for teleport events to record the previous location.
     * <p>
     * Registered at {@code MONITOR} priority with {@code ignoreCancelled = true} so this only
     * observes the final outcome of the event, after every other listener has had a chance to
     * cancel it -- a teleport another listener (permissions, WorldGuard, another plugin) goes on
     * to cancel must not overwrite a previously recorded valid location with the player's
     * unchanged {@code from}.
     *
     * @param event the teleport event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!config.isBackEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        // Record only a teleport this module's own commands started (/home, /warp, /spawn,
        // /lobby, /wild, an accepted /tpa, /back itself), marked by OwnTeleports around the call.
        // Another plugin's teleport or a vanilla /tp has the same PLUGIN or COMMAND cause, so the
        // cause cannot tell them apart; recording them let a teleport after a rejoin re-arm /back
        // behind the quit cleanup (UltiKits/UltiEssentials#39).
        if (OwnTeleports.isInProgress(player)) {
            LAST_LOCATIONS.put(player.getUniqueId(), event.getFrom());
        }
    }

    /**
     * Cleans up temporary data when player quits to prevent memory leaks.
     *
     * @param event the quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        LAST_LOCATIONS.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Removes a player's stored location from the cache.
     *
     * @param uuid the player's UUID
     */
    public static void removePlayer(UUID uuid) {
        LAST_LOCATIONS.remove(uuid);
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.back"));
    }
}
