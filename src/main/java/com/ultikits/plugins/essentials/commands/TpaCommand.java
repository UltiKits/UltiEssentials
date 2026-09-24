package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Command to send a TPA request (teleport to another player).
 * <p>
 * 发送 TPA 请求的命令（传送到另一个玩家）。
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"tpa"}, permission = "ultiessentials.tpa", description = "essentials.command.tpa.description")
@I18n("tpa.description")
public class TpaCommand extends BaseEssentialsCommand {
    
    @Autowired
    private TpaService tpaService;
    
    @Autowired
    private EssentialsConfig config;
    
    /**
     * Send TPA request to a player.
     */
    @CmdMapping(format = "<player>")
    public void sendTpa(@CmdSender Player sender, @CmdParam("player") String playerName) {
        if (!config.isTpaEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        Player target = Bukkit.getPlayer(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(i18n("essentials.tpa.player_offline"));
            return;
        }
        
        TpaService.TpaResult result = tpaService.sendTpaRequest(sender, target);
        handleResult(sender, target, result);
    }
    
    private void handleResult(Player sender, Player target, TpaService.TpaResult result) {
        switch (result) {
            case SENT:
                sender.sendMessage(i18n("essentials.tpa.sent") + " " + target.getName());
                sender.sendMessage(i18n("essentials.tpa.waiting"));
                target.sendMessage(sender.getName() + " " + i18n("essentials.tpa.request_to_you"));
                target.sendMessage(i18n("essentials.tpa.hint"));
                break;
            case SELF_REQUEST:
                sender.sendMessage(i18n("essentials.tpa.self"));
                break;
            case TARGET_BUSY:
                sender.sendMessage(i18n("essentials.tpa.target_has_pending"));
                break;
            case ON_COOLDOWN:
                int remaining = tpaService.getRemainingCooldown(sender.getUniqueId());
                sender.sendMessage(i18n("essentials.tpa.cooldown") + " (" + remaining + "s)");
                break;
            case CROSS_WORLD_DISABLED:
                sender.sendMessage(i18n("essentials.tpa.cross_world"));
                break;
            case DISABLED:
                sender.sendMessage(i18n("essentials.error.feature_disabled"));
                break;
            default:
                break;
        }
    }
}
