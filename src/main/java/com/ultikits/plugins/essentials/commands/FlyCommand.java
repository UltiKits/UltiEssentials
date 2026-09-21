package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to toggle flight mode for players.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"fly"}, permission = "ultiessentials.fly", description = "切换飞行模式")
public class FlyCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public FlyCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "")
    public void toggleFly(@CmdSender Player player) {
        if (!config.isFlyEnabled()) {
            player.sendMessage(i18n("该功能已禁用"));
            return;
        }

        boolean newState = !player.getAllowFlight();
        player.setAllowFlight(newState);

        if (newState) {
            player.sendMessage(i18n("飞行模式已开启"));
        } else {
            player.setFlying(false);
            player.sendMessage(i18n("飞行模式已关闭"));
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
            sender.sendMessage(i18n("该功能已禁用"));
            return;
        }

        if (target == null) {
            sender.sendMessage(i18n("玩家不存在或不在线"));
            return;
        }

        boolean newState = !target.getAllowFlight();
        target.setAllowFlight(newState);

        if (newState) {
            sender.sendMessage(String.format(i18n("已为 %s 开启飞行模式"), target.getName()));
            target.sendMessage(i18n("你的飞行模式已被开启"));
        } else {
            target.setFlying(false);
            sender.sendMessage(String.format(i18n("已为 %s 关闭飞行模式"), target.getName()));
            target.sendMessage(i18n("你的飞行模式已被关闭"));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("使用 /fly 切换飞行模式"));
    }
}
