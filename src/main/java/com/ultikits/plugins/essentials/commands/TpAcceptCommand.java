package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Command to accept a TPA request.
 * <p>
 * 接受 TPA 请求的命令。
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"tpaccept", "tpyes", "tpok"}, permission = "ultiessentials.tpaccept", description = "essentials.command.tpaccept.description")
@I18n("tpaccept.description")
public class TpAcceptCommand extends BaseEssentialsCommand {
    
    @Autowired
    private TpaService tpaService;
    
    @Autowired
    private EssentialsConfig config;
    
    /**
     * Accept a pending TPA request.
     */
    @CmdMapping(format = "")
    public void acceptTpa(@CmdSender Player player) {
        if (!config.isTpaEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        TpaService.TpaRequest request = tpaService.getRequest(player.getUniqueId());
        if (request == null) {
            player.sendMessage(i18n("essentials.tpa.no_pending"));
            return;
        }
        
        TpaService.TpaResult result = tpaService.acceptRequest(player);
        
        switch (result) {
            case ACCEPTED:
                player.sendMessage(i18n("essentials.tpa.accepted"));
                // Notify sender
                Player sender = Bukkit.getPlayer(request.getSenderUuid());
                if (sender != null && sender.isOnline()) {
                    sender.sendMessage(player.getName() + " " + i18n("essentials.tpa.accepted_by"));
                }
                break;
            case SENDER_OFFLINE:
                player.sendMessage(i18n("essentials.tpa.sender_offline"));
                break;
            case NO_REQUEST:
                player.sendMessage(i18n("essentials.tpa.no_pending"));
                break;
            default:
                break;
        }
    }
}
