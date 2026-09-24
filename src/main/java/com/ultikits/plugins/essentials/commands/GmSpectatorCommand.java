package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shortcut command to switch to spectator mode.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"gmsp"}, permission = "ultiessentials.gamemode.self", description = "essentials.command.gmsp.description")
public class GmSpectatorCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public GmSpectatorCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void spectator(@CmdSender Player player) {
        if (!config.isGamemodeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(i18n("essentials.gamemode.changed_spectator"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.gmsp"));
    }
}
