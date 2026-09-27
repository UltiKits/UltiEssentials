package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.OwnTeleports;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * Command to randomly teleport player within a configured range.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"wild", "rtp"}, permission = "ultiessentials.wild", description = "essentials.command.wild.description")
public class WildCommand extends BaseEssentialsCommand {

    private static final Random RANDOM = new Random();

    /** Candidate locations tried before /wild gives up. */
    private static final int MAX_ATTEMPTS = 10;

    private final EssentialsConfig config;

    public WildCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    // The cooldown is features.wild.cooldown in config/essentials.yml (default 60 seconds, 0 for none),
    // resolved by the framework at load and again at /ul reload (UltiKits/UltiEssentials#27,
    // UltiKits/UltiTools-Reborn#531). No literal here: the default lives only in EssentialsConfig,
    // and a literal beside the binding is refused at load.
    @CmdCD(config = EssentialsConfig.class, key = "features.wild.cooldown")
    public void wildTeleport(@CmdSender Player player) {
        if (!config.isWildEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        World world = player.getWorld();
        int maxRange = config.getWildMaxRange();
        int minRange = config.getWildMinRange();

        // EssentialsConfig validates each of wildMinRange/wildMaxRange individually (@Range) but
        // not their relationship, so a misconfigured server could otherwise reach
        // RANDOM.nextInt(maxRange - minRange) with a non-positive bound and throw.
        if (minRange >= maxRange) {
            player.sendMessage(i18n("essentials.wild.misconfigured"));
            return;
        }

        player.sendMessage(i18n("essentials.wild.searching"));

        Location origin = player.getLocation();
        tryLocation(player, world, origin.getX(), origin.getZ(), minRange, maxRange, 0);
    }

    /**
     * Tries one random candidate location. Its chunk is loaded asynchronously, so an unexplored
     * chunk is generated without stalling the server tick; the height lookup, the safety check and
     * the teleport then run in a task on the main thread, where world and player state may be read
     * and changed. An unsafe candidate tries the next one, up to {@link #MAX_ATTEMPTS} in all
     * (UltiKits/UltiEssentials#24). A player who logged out while a chunk loaded is left alone.
     */
    private void tryLocation(Player player, World world, double originX, double originZ,
                             int minRange, int maxRange, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            player.sendMessage(i18n("essentials.wild.no_safe_location"));
            return;
        }
        int range = minRange + RANDOM.nextInt(maxRange - minRange);
        double angle = RANDOM.nextDouble() * 2 * Math.PI;
        int x = (int) (originX + range * Math.cos(angle));
        int z = (int) (originZ + range * Math.sin(angle));

        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, loadFailure) ->
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("UltiTools"), () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (loadFailure == null) {
                    int y = world.getHighestBlockYAt(x, z);
                    Location target = new Location(world, x + 0.5, y + 1, z + 0.5);
                    if (isSafeLocation(target)) {
                        OwnTeleports.teleport(player, target);
                        // Report where the player lands -- the destination's own block position,
                        // feet included -- not the ground block below it (UltiKits/UltiEssentials#33).
                        player.sendMessage(String.format(i18n("essentials.wild.success"),
                                target.getBlockX(), target.getBlockY(), target.getBlockZ()));
                        return;
                    }
                }
                tryLocation(player, world, originX, originZ, minRange, maxRange, attempt + 1);
            }));
    }

    /**
     * Checks if a location is safe for teleportation.
     *
     * @param location the location to check
     * @return true if safe, false otherwise
     */
    private boolean isSafeLocation(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        // Feet and head position must be air or passable
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }

        // Ground must be solid
        if (!ground.getType().isSolid()) {
            return false;
        }

        // Cannot teleport onto lava or water
        if (ground.getType() == Material.LAVA || ground.getType() == Material.WATER) {
            return false;
        }

        return true;
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.wild"));
    }
}
