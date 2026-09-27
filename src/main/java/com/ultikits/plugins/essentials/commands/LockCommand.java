package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.ChestLockService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for locking containers.
 * <p>
 * Usage: /lock (while looking at a container)
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"lock", "l"},
    permission = "ultiessentials.lock",
    description = "essentials.command.lock.description"
)
public class LockCommand extends BaseEssentialsCommand {
    
    @Autowired
    private ChestLockService chestLockService;
    
    @CmdMapping(format = "")
    public void lock(@CmdSender Player player) {
        Block target = player.getTargetBlockExact(5);
        
        if (target == null) {
            player.sendMessage(i18n("essentials.lock.look_at_container"));
            return;
        }
        
        ChestLockService.LockResult result = chestLockService.lockBlock(target, player);
        
        switch (result) {
            case SUCCESS:
                player.sendMessage(i18n("essentials.lock.locked"));
                break;
            case NOT_LOCKABLE:
                player.sendMessage(i18n("essentials.lock.not_lockable"));
                break;
            case ALREADY_LOCKED:
                player.sendMessage(i18n("essentials.lock.locked_by_other"));
                break;
            case ALREADY_LOCKED_BY_YOU:
                player.sendMessage(i18n("essentials.lock.already_yours"));
                break;
            case DISABLED:
                player.sendMessage(i18n("essentials.lock.disabled"));
                break;
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.lock.usage"));
        sender.sendMessage(i18n("essentials.help.lock"));
    }
}
