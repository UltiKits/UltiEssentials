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
    description = "解锁容器"
)
public class UnlockCommand extends BaseEssentialsCommand {
    
    @Autowired
    private ChestLockService chestLockService;
    
    @CmdMapping(format = "")
    public void unlock(@CmdSender Player player) {
        Block target = player.getTargetBlockExact(5);
        
        if (target == null) {
            player.sendMessage(i18n("§c请看向一个容器"));
            return;
        }
        
        ChestLockService.UnlockResult result = chestLockService.unlockBlock(target, player);
        
        switch (result) {
            case SUCCESS:
                player.sendMessage(i18n("§a已解锁该容器"));
                break;
            case NOT_LOCKED:
                player.sendMessage(i18n("§c该容器未被锁定"));
                break;
            case NOT_OWNER:
                player.sendMessage(i18n("§c你不是该容器的主人"));
                break;
            case FAILED:
                // The lock record could not be removed from storage, so the container is still
                // locked. Reporting success here is the defect UltiKits/UltiEssentials#37 reports.
                player.sendMessage(i18n("§c解锁失败，该容器的锁定记录无法移除，请联系管理员"));
                break;
        }
    }
    
    @CmdMapping(format = "info")
    public void info(@CmdSender Player player) {
        Block target = player.getTargetBlockExact(5);
        
        if (target == null) {
            player.sendMessage(i18n("§c请看向一个容器"));
            return;
        }
        
        // Container-scoped, like every other lock question in the module. Asking about the looked-at
        // block alone reported "not locked" on the unrecorded half of a partly-recorded double chest
        // -- the same block the interact check refuses to open, so the command contradicted the
        // protection a player was standing in front of. Every protecting record is printed rather
        // than the first: legacy data can hold one half per owner (gate 2 round 3).
        List<ChestLockData> locks = chestLockService.locksProtecting(target);

        if (locks.isEmpty()) {
            player.sendMessage(i18n("§7该容器未被锁定"));
        } else {
            player.sendMessage(i18n("§6=== 容器锁定信息 ==="));
            for (ChestLockData lock : locks) {
                player.sendMessage(i18n("§7主人: §f") + lock.getOwnerName());
                player.sendMessage(i18n("§7位置: §f") +
                    lock.getWorld() + " (" + lock.getX() + ", " + lock.getY() + ", " + lock.getZ() + ")");
            }
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("用法: /unlock"));
        sender.sendMessage(i18n("看向一个容器并使用此命令来解锁它"));
        sender.sendMessage(i18n("/unlock info - 查看锁定信息"));
    }
}
