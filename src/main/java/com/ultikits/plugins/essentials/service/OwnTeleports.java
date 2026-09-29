package com.ultikits.plugins.essentials.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks the teleports this module's own commands start, so {@code /back} records those and no
 * other: a teleport by another plugin, a vanilla {@code /tp} or a teleport by this module that no
 * command asked for (the join-time spawn teleport) leaves {@code /back} where it was
 * (UltiKits/UltiEssentials#39).
 * <p>
 * The mark is set for one player around one {@link Player#teleport(Location)} call and removed in a
 * {@code finally}, so it covers exactly the {@code PlayerTeleportEvent} that call fires and cannot
 * outlive it or reach another player.
 */
public final class OwnTeleports {

    private static final Set<UUID> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private OwnTeleports() {
    }

    /**
     * Teleports the player for one of this module's commands.
     *
     * @param player the player to teleport
     * @param target where to
     * @return what {@link Player#teleport(Location)} returned
     */
    public static boolean teleport(Player player, Location target) {
        UUID id = player.getUniqueId();
        IN_PROGRESS.add(id);
        try {
            return player.teleport(target);
        } finally {
            IN_PROGRESS.remove(id);
        }
    }

    /**
     * Whether the teleport now in progress for this player was started by one of this module's
     * commands.
     *
     * @param player the player being teleported
     * @return {@code true} inside {@link #teleport(Player, Location)} for that player
     */
    public static boolean isInProgress(Player player) {
        return IN_PROGRESS.contains(player.getUniqueId());
    }
}
