package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to teleport player to the server spawn point.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"spawn"}, permission = "ultiessentials.spawn.teleport", description = "essentials.command.spawn.description")
public class SpawnCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;
    private final SpawnConfig spawnConfig;

    public SpawnCommand(EssentialsConfig config, SpawnConfig spawnConfig) {
        this.config = config;
        this.spawnConfig = spawnConfig;
    }

    @CmdMapping(format = "")
    public void teleportToSpawn(@CmdSender Player player) {
        if (!config.isSpawnEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        Location spawn = spawnConfig.getSpawnLocation();
        if (spawn.getWorld() == null) {
            player.sendMessage(i18n("essentials.spawn.world_not_found"));
            return;
        }

        player.teleport(spawn);
        player.sendMessage(i18n("essentials.spawn.success"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.spawn"));
    }
}
