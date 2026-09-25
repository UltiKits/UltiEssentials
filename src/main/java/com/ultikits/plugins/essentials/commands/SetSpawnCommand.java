package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

/**
 * Command to set the server spawn point at player's current location.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"setspawn"}, permission = "ultiessentials.spawn.set", description = "essentials.command.setspawn.description")
public class SetSpawnCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;
    private final SpawnConfig spawnConfig;

    public SetSpawnCommand(EssentialsConfig config, SpawnConfig spawnConfig) {
        this.config = config;
        this.spawnConfig = spawnConfig;
    }

    @CmdMapping(format = "")
    public void setSpawn(@CmdSender Player player) {
        if (!config.isSpawnEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        spawnConfig.setSpawnLocation(player.getLocation());
        try {
            spawnConfig.save();
            player.sendMessage(i18n("essentials.spawn.set"));
        } catch (IOException e) {
            player.sendMessage(i18n("essentials.spawn.save_failed"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.setspawn"));
    }
}
