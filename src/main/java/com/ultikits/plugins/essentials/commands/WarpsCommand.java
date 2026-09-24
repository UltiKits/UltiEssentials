package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.service.WarpService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command for listing all warps.
 * <p>
 * Usage: /warps
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"warps", "warplist", "listwarp"},
    permission = "ultiessentials.warp.list",
    description = "essentials.command.warps.description"
)
public class WarpsCommand extends BaseEssentialsCommand {
    
    @Autowired
    private WarpService warpService;
    
    @CmdMapping(format = "")
    public void listWarps(@CmdSender Player player) {
        List<WarpData> accessibleWarps = warpService.getAccessibleWarps(player);
        
        if (accessibleWarps.isEmpty()) {
            player.sendMessage(i18n("essentials.warps.empty"));
            return;
        }
        
        player.sendMessage(i18n("essentials.warps.header"));
        
        for (WarpData warp : accessibleWarps) {
            StringBuilder info = new StringBuilder();
            info.append("§e").append(warp.getName());
            info.append(" §7- ").append(warp.getWorld());
            info.append(" (").append(String.format("%.1f", warp.getX()));
            info.append(", ").append(String.format("%.1f", warp.getY()));
            info.append(", ").append(String.format("%.1f", warp.getZ())).append(")");
            
            if (warp.getPermission() != null && !warp.getPermission().isEmpty()) {
                info.append(" §c[需要权限]");
            }
            
            player.sendMessage(info.toString());
        }
        
        player.sendMessage(i18n("essentials.list.total_prefix") + accessibleWarps.size() + 
            i18n("essentials.warps.total_suffix"));
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.warps.usage"));
        sender.sendMessage(i18n("essentials.help.warps"));
    }
}
