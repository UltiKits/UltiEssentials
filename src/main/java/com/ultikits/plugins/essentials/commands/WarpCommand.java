package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.service.WarpService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for teleporting to warps.
 * <p>
 * Usage: /warp <name>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"warp", "w"},
    permission = "ultiessentials.warp.use",
    description = "essentials.command.warp.description"
)
public class WarpCommand extends BaseEssentialsCommand {
    
    @Autowired
    private WarpService warpService;
    
    @CmdMapping(format = "<name>")
    public void warp(@CmdSender Player player, @CmdParam("name") String name) {
        TeleportResult result = warpService.teleportToWarp(player, name);
        
        switch (result) {
            case SUCCESS:
                player.sendMessage(i18n("essentials.warp.teleported") + name);
                break;
            case WARMUP_STARTED:
                player.sendMessage(i18n("essentials.warp.warmup"));
                break;
            case NOT_FOUND:
                player.sendMessage(i18n("essentials.warp.not_found") + name);
                break;
            case WORLD_NOT_FOUND:
                player.sendMessage(i18n("essentials.warp.world_not_found"));
                break;
            case NO_PERMISSION:
                player.sendMessage(i18n("essentials.warp.no_permission"));
                break;
            case ALREADY_TELEPORTING:
                player.sendMessage(i18n("essentials.warp.in_progress"));
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
        sender.sendMessage(i18n("essentials.help.warp.usage"));
        sender.sendMessage(i18n("essentials.help.warp"));
    }
    
    @Override
    protected List<String> suggest(Player player, Command command, String[] args) {
        if (args.length == 1) {
            return warpService.getAccessibleWarps(player).stream()
                .map(w -> w.getName())
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return super.suggest(player, command, args);
    }
}
