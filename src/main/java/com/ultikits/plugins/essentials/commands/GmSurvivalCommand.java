package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shortcut command to switch to survival mode.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"gms"}, permission = "ultiessentials.gamemode.self", description = "essentials.command.gms.description")
public class GmSurvivalCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public GmSurvivalCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void survival(@CmdSender Player player) {
        if (!config.isGamemodeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage(i18n("essentials.gamemode.changed_survival"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.gms"));
    }
}
