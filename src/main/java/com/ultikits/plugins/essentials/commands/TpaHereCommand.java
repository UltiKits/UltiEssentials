package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Command to send a TPA-here request (request another player to teleport to you).
 * <p>
 * 发送 TPA-here 请求的命令（请求另一个玩家传送到你身边）。
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"tpahere", "tphere"}, permission = "ultiessentials.tpahere", description = "essentials.command.tpahere.description")
@I18n("tpahere.description")
public class TpaHereCommand extends BaseEssentialsCommand {
    
    @Autowired
    private TpaService tpaService;
    
    @Autowired
    private EssentialsConfig config;
    
    /**
     * Send TPA-here request to a player.
     */
    @CmdMapping(format = "<player>")
    public void sendTpaHere(@CmdSender Player sender, @CmdParam("player") String playerName) {
        if (!config.isTpaEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        Player target = Bukkit.getPlayer(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(i18n("essentials.tpa.player_offline"));
            return;
        }
        
        TpaService.TpaResult result = tpaService.sendTpaHereRequest(sender, target);
        handleResult(sender, target, result);
    }
    
    private void handleResult(Player sender, Player target, TpaService.TpaResult result) {
        switch (result) {
            case SENT:
                sender.sendMessage(String.format(i18n("essentials.tpa.sent"), target.getName()));
                sender.sendMessage(i18n("essentials.tpa.waiting"));
                target.sendMessage(String.format(i18n("essentials.tpa.request_here"), sender.getName()));
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
                sender.sendMessage(String.format(i18n("essentials.tpa.cooldown"), remaining));
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
