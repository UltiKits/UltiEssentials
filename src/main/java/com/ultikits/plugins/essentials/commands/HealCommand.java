package com.ultikits.plugins.essentials.commands;

import com.cryptomorin.xseries.XAttribute;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to restore player health.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"heal"}, permission = "ultiessentials.heal.self", description = "essentials.command.heal.description")
public class HealCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public HealCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void healSelf(@CmdSender Player player) {
        if (!config.isHealEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        double maxHealth = player.getAttribute(XAttribute.MAX_HEALTH.get()).getValue();
        player.setHealth(maxHealth);
        player.sendMessage(i18n("essentials.heal.self"));
    }

    @CmdMapping(format = "<player>", permission = "ultiessentials.heal.other")
    public void healOther(
            @CmdSender Player sender,
            @CmdParam("player") Player target) {

        if (!config.isHealEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        if (target == null) {
            sender.sendMessage(i18n("essentials.error.player_not_found_or_offline"));
            return;
        }

        double maxHealth = target.getAttribute(XAttribute.MAX_HEALTH.get()).getValue();
        target.setHealth(maxHealth);
        sender.sendMessage(String.format(i18n("essentials.heal.other"), target.getName()));
        target.sendMessage(i18n("essentials.heal.target"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.heal"));
    }
}
