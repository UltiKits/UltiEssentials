package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shortcut command to switch to adventure mode.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"gma"}, permission = "ultiessentials.gamemode.self", description = "essentials.command.gma.description")
public class GmAdventureCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public GmAdventureCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void adventure(@CmdSender Player player) {
        if (!config.isGamemodeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.sendMessage(i18n("essentials.gamemode.changed_adventure"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.gma"));
    }
}
