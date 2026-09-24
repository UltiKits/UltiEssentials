package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shortcut command to switch to creative mode.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"gmc"}, permission = "ultiessentials.gamemode.self", description = "essentials.command.gmc.description")
public class GmCreativeCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public GmCreativeCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void creative(@CmdSender Player player) {
        if (!config.isGamemodeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        player.setGameMode(GameMode.CREATIVE);
        player.sendMessage(i18n("essentials.gamemode.changed_creative"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.gmc"));
    }
}
