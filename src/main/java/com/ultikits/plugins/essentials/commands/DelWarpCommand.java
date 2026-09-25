package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.WarpService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for deleting warps.
 * <p>
 * Usage: /delwarp <name>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"delwarp", "deletewarp", "rmwarp", "removewarp"},
    permission = "ultiessentials.warp.delete",
    description = "essentials.command.delwarp.description"
)
public class DelWarpCommand extends BaseEssentialsCommand {
    
    @Autowired
    private WarpService warpService;
    
    @CmdMapping(format = "<name>")
    public void delWarp(@CmdSender Player player, @CmdParam("name") String name) {
        switch (warpService.deleteWarp(name)) {
            case REMOVED:
                player.sendMessage(i18n("essentials.warp.deleted") + name);
                break;
            case NOT_FOUND:
                player.sendMessage(i18n("essentials.warp.not_found") + name);
                break;
            case FAILED:
                // Distinct from NOT_FOUND: the warp is still there and still usable by everyone.
                player.sendMessage(i18n("essentials.warp.delete_failed") + " (" + name + ")");
                break;
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.delwarp.usage"));
        sender.sendMessage(i18n("essentials.help.delwarp"));
    }
    
    @Override
    protected List<String> suggest(Player player, Command command, String[] args) {
        if (args.length == 1) {
            return warpService.getAllWarps().stream()
                .map(w -> w.getName())
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return super.suggest(player, command, args);
    }
}
