package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to toggle flight mode for players.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"fly"}, permission = "ultiessentials.fly", description = "essentials.command.fly.description")
public class FlyCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public FlyCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void toggleFly(@CmdSender Player player) {
        if (!config.isFlyEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        boolean newState = !player.getAllowFlight();
        player.setAllowFlight(newState);

        if (newState) {
            player.sendMessage(i18n("essentials.fly.enabled"));
        } else {
            player.setFlying(false);
            player.sendMessage(i18n("essentials.fly.disabled"));
        }
    }

    // Acting on another player needs a node the self variant does not grant. Without this
    // override the mapping inherited the class-level ultiessentials.fly, so every server granting
    // ordinary players self-flight also let them force flight on or off for anyone online
    // (UltiKits/UltiEssentials#25). Matches the ultiessentials.gamemode.other /
    // ultiessentials.heal.other pattern the module's other self/other pairs already use.
    @CmdMapping(format = "<player>", permission = "ultiessentials.fly.other")
    public void toggleFlyOther(
            @CmdSender Player sender,
            @CmdParam("player") Player target) {

        if (!config.isFlyEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        if (target == null) {
            sender.sendMessage(i18n("essentials.error.player_not_found_or_offline"));
            return;
        }

        boolean newState = !target.getAllowFlight();
        target.setAllowFlight(newState);

        if (newState) {
            sender.sendMessage(String.format(i18n("essentials.fly.enabled_other"), target.getName()));
            target.sendMessage(i18n("essentials.fly.enabled_target"));
        } else {
            target.setFlying(false);
            sender.sendMessage(String.format(i18n("essentials.fly.disabled_other"), target.getName()));
            target.sendMessage(i18n("essentials.fly.disabled_target"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.fly"));
    }
}
