package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.service.ChestLockService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command for unlocking containers.
 * <p>
 * Usage: /unlock (while looking at a container)
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"unlock", "ul"},
    permission = "ultiessentials.lock",
    description = "essentials.command.unlock.description"
)
public class UnlockCommand extends BaseEssentialsCommand {
    
    @Autowired
    private ChestLockService chestLockService;
    
    @CmdMapping(format = "")
    public void unlock(@CmdSender Player player) {
        Block target = player.getTargetBlockExact(5);
        
        if (target == null) {
            player.sendMessage(i18n("essentials.lock.look_at_container"));
            return;
        }
        
        ChestLockService.UnlockResult result = chestLockService.unlockBlock(target, player);
        
        switch (result) {
            case SUCCESS:
                player.sendMessage(i18n("essentials.unlock.unlocked"));
                break;
            case NOT_LOCKED:
                player.sendMessage(i18n("essentials.unlock.not_locked"));
                break;
            case NOT_OWNER:
                player.sendMessage(i18n("essentials.unlock.not_owner"));
                break;
            case FAILED:
                // The lock record could not be removed from storage, so the container is still
                // locked. Reporting success here is the defect UltiKits/UltiEssentials#37 reports.
                player.sendMessage(i18n("essentials.unlock.failed"));
                break;
        }
    }
    
    @CmdMapping(format = "info")
    public void info(@CmdSender Player player) {
        Block target = player.getTargetBlockExact(5);
        
        if (target == null) {
            player.sendMessage(i18n("essentials.lock.look_at_container"));
            return;
        }
        
        // Container-scoped, like every other lock question in the module. Asking about the looked-at
        // block alone reported "not locked" on the unrecorded half of a partly-recorded double chest
        // -- the same block the interact check refuses to open, so the command contradicted the
        // protection a player was standing in front of. Every protecting record is printed rather
        // than the first: legacy data can hold one half per owner (gate 2 round 3).
        List<ChestLockData> locks = chestLockService.locksProtecting(target);

        if (locks.isEmpty()) {
            player.sendMessage(i18n("essentials.unlock.info_not_locked"));
        } else {
            player.sendMessage(i18n("essentials.unlock.info_header"));
            for (ChestLockData lock : locks) {
                player.sendMessage(i18n("essentials.unlock.info_owner") + lock.getOwnerName());
                player.sendMessage(i18n("essentials.unlock.info_location") +
                    lock.getWorld() + " (" + lock.getX() + ", " + lock.getY() + ", " + lock.getZ() + ")");
            }
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.unlock.usage"));
        sender.sendMessage(i18n("essentials.help.unlock"));
        sender.sendMessage(i18n("essentials.help.unlock.info"));
    }
}
