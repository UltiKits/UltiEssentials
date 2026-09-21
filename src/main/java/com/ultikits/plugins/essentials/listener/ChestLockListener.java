package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.service.ChestLockService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Listener for chest lock protection.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@EventListener
public class ChestLockListener implements Listener {
    
    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private EssentialsConfig config;

    @Autowired
    private ChestLockService chestLockService;
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!config.isChestLockEnabled()) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        
        if (!chestLockService.isLockable(block.getType())) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Keyed on the container, not on the clicked block: a double chest is one shared inventory
        // behind two blocks, so a record covering either half protects the items behind both.
        if (!chestLockService.canAccess(block, player)) {
            event.setCancelled(true);
            
            ChestLockData lock = chestLockService.denyingLock(block, player);
            if (lock != null) {
                player.sendMessage(plugin.i18n("§c该容器被 §f") + 
                    lock.getOwnerName() + plugin.i18n(" §c锁定"));
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isChestLockEnabled()) {
            return;
        }
        
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Keyed on the container, for the same reason onPlayerInteract is: a double chest is one
        // shared inventory behind two blocks. Asking isLocked about the clicked block alone left the
        // unrecorded half of a partly-recorded double chest breakable by anyone, and breaking it drops
        // its contents on the floor -- strictly worse than the interact bypass, which only opened them
        // (UltiKits/UltiEssentials#50 gate 2 round 3).
        //
        // Deliberately the raw permission node rather than ChestLockService#canAccess(Block, Player):
        // admin bypass for opening a container is gated by chestlock.admin-bypass, admin bypass for
        // breaking one is not. FEATURES.md records that these are two separate checks that happen to
        // require the same node, and this fix widens which records are consulted without changing
        // which players are let through.
        boolean isAdmin = player.hasPermission("ultiessentials.lock.admin");
        ChestLockData denying = null;
        for (ChestLockData lock : chestLockService.locksProtecting(block)) {
            if (!lock.getOwnerUuid().equals(player.getUniqueId().toString()) && !isAdmin) {
                denying = lock;
                break;
            }
        }

        if (denying != null) {
            event.setCancelled(true);
            player.sendMessage(plugin.i18n("§c该容器被 §f") +
                denying.getOwnerName() + plugin.i18n(" §c锁定，无法破坏"));
            return;
        }

        // Remove lock when broken. The boolean is deliberately not acted on here: the break has
        // already been allowed at this point, and ChestLockService logs the record and its location
        // at ERROR when the store would not give it up, keeping the lock cached so the container
        // does not appear unlocked until the next restart (UltiKits/UltiEssentials#37). Cancelling
        // the break instead would stop an owner from ever breaking their own container while the
        // store is failing, which is a policy choice this fix does not make.
        chestLockService.onBlockBreak(block.getLocation());
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!config.isChestLockEnabled()) {
            return;
        }
        
        // Container-scoped: an explosion that destroys the unrecorded half of a partly-recorded double
        // chest drops that half's contents just as a break does, so the same whole-container lookup
        // applies. This is the site whose earlier "an explosion destroys a specific block, not a
        // container" reasoning was the same argument that left breaking open (gate 2 round 3).
        event.blockList().removeIf(block -> chestLockService.isContainerLocked(block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!config.isChestLockEnabled()) {
            return;
        }

        // Container-scoped, for the reason given on onEntityExplode.
        event.blockList().removeIf(block -> chestLockService.isContainerLocked(block));
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!config.isChestLockEnabled()) {
            return;
        }
        
        // Container-scoped for consistency with every other protection site. Vanilla will not let a
        // piston move a block entity at all, so no exploitable route through this handler is known --
        // but "no route is known" was also true of breaking until it was looked for, and the cost of
        // widening the lookup is one map probe.
        for (Block block : event.getBlocks()) {
            if (chestLockService.isContainerLocked(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!config.isChestLockEnabled()) {
            return;
        }

        // Container-scoped, for the reason given on onPistonExtend.
        for (Block block : event.getBlocks()) {
            if (chestLockService.isContainerLocked(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!config.isChestLockEnabled()) {
            return;
        }
        
        // Prevent a hopper from moving items out of a locked container.
        //
        // Resolved through the service rather than by an `instanceof Container` test here: a double
        // chest's inventory is held by a DoubleChest, which is not a Container and not a block state,
        // so the old test skipped every double chest -- a hopper could drain one even when BOTH halves
        // held records. That is a wider hole than the unrecorded-half case the break fix closes, and
        // it is fixed here rather than reported (gate 2 round 3).
        //
        // getDestination() is deliberately not checked: inserting items into a locked container does
        // not expose its contents, and cancelling insertion would stop an owner's own hopper feeding
        // their own locked chest.
        if (chestLockService.isContainerLocked(event.getSource().getHolder())) {
            event.setCancelled(true);
        }
    }
}
