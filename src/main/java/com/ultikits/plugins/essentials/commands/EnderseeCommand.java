package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to view another player's ender chest.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"endersee", "echest"}, permission = "ultiessentials.endersee", description = "essentials.command.endersee.description")
public class EnderseeCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public EnderseeCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "<player>")
    public void endersee(@CmdSender Player sender, @CmdParam("player") Player target) {
        if (!config.isInvseeEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        if (target == null) {
            sender.sendMessage(i18n("essentials.error.player_not_found_or_offline"));
            return;
        }

        sender.openInventory(target.getEnderChest());
        sender.sendMessage(String.format(i18n("essentials.endersee.viewing"), target.getName()));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.endersee"));
    }
}
