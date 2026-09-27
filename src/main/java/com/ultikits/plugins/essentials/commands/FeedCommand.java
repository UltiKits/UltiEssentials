package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to restore player hunger.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"feed"}, permission = "ultiessentials.heal.self", description = "essentials.command.feed.description")
public class FeedCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public FeedCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void feedSelf(@CmdSender Player player) {
        if (!config.isHealEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.sendMessage(i18n("essentials.feed.self"));
    }

    @CmdMapping(format = "<player>", permission = "ultiessentials.heal.other")
    public void feedOther(
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

        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        sender.sendMessage(String.format(i18n("essentials.feed.other"), target.getName()));
        target.sendMessage(i18n("essentials.feed.target"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.feed"));
    }
}
