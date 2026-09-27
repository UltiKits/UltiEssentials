package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to view another player's inventory.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"invsee"}, permission = "ultiessentials.invsee", description = "essentials.command.invsee.description")
public class InvseeCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public InvseeCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "<player>")
    public void invsee(@CmdSender Player sender, @CmdParam("player") Player target) {
        if (!config.isInvseeEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        if (target == null) {
            sender.sendMessage(i18n("essentials.error.player_not_found_or_offline"));
            return;
        }

        if (target.equals(sender)) {
            sender.sendMessage(i18n("essentials.invsee.self"));
            return;
        }

        sender.openInventory(target.getInventory());
        sender.sendMessage(String.format(i18n("essentials.invsee.viewing"), target.getName()));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.invsee"));
    }
}
