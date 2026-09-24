package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.WarpService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for creating warps.
 * <p>
 * Usage: /setwarp <name> [permission]
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"setwarp", "swarp", "addwarp"},
    permission = "ultiessentials.warp.set",
    description = "essentials.command.setwarp.description"
)
public class SetWarpCommand extends BaseEssentialsCommand {
    
    @Autowired
    private WarpService warpService;
    
    @CmdMapping(format = "<name>")
    public void setWarp(@CmdSender Player player, @CmdParam("name") String name) {
        setWarpWithPermission(player, name, null);
    }
    
    @CmdMapping(format = "<name> <permission>")
    public void setWarpWithPermission(
        @CmdSender Player player,
        @CmdParam("name") String name,
        @CmdParam("permission") String permission
    ) {
        WarpService.WarpResult result = warpService.createWarp(
            name,
            player.getLocation(),
            player.getUniqueId(),
            permission
        );
        
        switch (result) {
            case CREATED:
                if (permission != null && !permission.isEmpty()) {
                    player.sendMessage(String.format(i18n("essentials.warp.created_with_permission"), name, permission));
                } else {
                    player.sendMessage(i18n("essentials.warp.created") + name);
                }
                break;
            case ALREADY_EXISTS:
                player.sendMessage(i18n("essentials.warp.exists") + name);
                break;
            case INVALID_NAME:
                player.sendMessage(i18n("essentials.warp.invalid_name"));
                break;
            case DISABLED:
                player.sendMessage(i18n("essentials.warp.disabled"));
                break;
            default:
                // Handle any unexpected result types
                break;
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.setwarp.usage"));
        sender.sendMessage(i18n("essentials.help.setwarp"));
        sender.sendMessage(i18n("essentials.help.setwarp.permission"));
    }
}
