package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import javax.annotation.Nullable;
import com.ultikits.ultitools.annotations.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing chest locks.
 * <p>
 * 管理箱子锁的服务。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Slf4j
@Service
public class ChestLockService {
    
    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private EssentialsConfig config;

    private DataOperator<ChestLockData> lockOperator;

    // Cache for quick lookups
    private final Map<String, ChestLockData> lockCache = new ConcurrentHashMap<>();
    
    // Lockable block types
    private static final Set<Material> LOCKABLE_BLOCKS = new HashSet<>(Arrays.asList(
        Material.CHEST,
        Material.TRAPPED_CHEST,
        Material.BARREL,
        Material.SHULKER_BOX,
        Material.WHITE_SHULKER_BOX,
        Material.ORANGE_SHULKER_BOX,
        Material.MAGENTA_SHULKER_BOX,
        Material.LIGHT_BLUE_SHULKER_BOX,
        Material.YELLOW_SHULKER_BOX,
        Material.LIME_SHULKER_BOX,
        Material.PINK_SHULKER_BOX,
        Material.GRAY_SHULKER_BOX,
        Material.LIGHT_GRAY_SHULKER_BOX,
        Material.CYAN_SHULKER_BOX,
        Material.PURPLE_SHULKER_BOX,
        Material.BLUE_SHULKER_BOX,
        Material.BROWN_SHULKER_BOX,
        Material.GREEN_SHULKER_BOX,
        Material.RED_SHULKER_BOX,
        Material.BLACK_SHULKER_BOX,
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.HOPPER,
        Material.DROPPER,
        Material.DISPENSER,
        Material.BREWING_STAND
    ));
    
    /**
     * Initializes the service.
     * Automatically called by the IoC container after construction.
     */
    @PostConstruct
    public void init() {
        this.lockOperator = plugin.getDataOperator(ChestLockData.class);
        loadCache();
    }
    
    /**
     * Loads all locks into cache.
     */
    private void loadCache() {
        lockCache.clear();
        List<ChestLockData> allLocks = lockOperator.getAll();
        for (ChestLockData lock : allLocks) {
            lockCache.put(lock.getLocationKey(), lock);
        }
        log.info("Loaded {} chest locks into cache", lockCache.size());
    }
    
    /**
     * Checks if a block type is lockable.
     */
    public boolean isLockable(Material material) {
        return LOCKABLE_BLOCKS.contains(material);
    }
    
    /**
     * Locks a block.
     */
    public LockResult lockBlock(Block block, Player player) {
        if (!config.isChestLockEnabled()) {
            return LockResult.DISABLED;
        }
        
        if (!isLockable(block.getType())) {
            return LockResult.NOT_LOCKABLE;
        }
        
        // Check if already locked
        ChestLockData existing = getLock(block.getLocation());
        if (existing != null) {
            if (existing.getOwnerUuid().equals(player.getUniqueId().toString())) {
                return LockResult.ALREADY_LOCKED_BY_YOU;
            } else {
                return LockResult.ALREADY_LOCKED;
            }
        }
        
        // Create lock
        ChestLockData lock = ChestLockData.builder()
            .uuid(UUID.randomUUID())
            .world(block.getWorld().getName())
            .x(block.getX())
            .y(block.getY())
            .z(block.getZ())
            .ownerUuid(player.getUniqueId().toString())
            .ownerName(player.getName())
            .createdAt(System.currentTimeMillis())
            .build();
        
        lockOperator.insert(lock);
        lockCache.put(lock.getLocationKey(), lock);
        
        // If it's a double chest, lock the other half too
        lockDoubleChestOther(block, player);
        
        return LockResult.SUCCESS;
    }
    
    /**
     * Locks the other half of a double chest.
     */
    private void lockDoubleChestOther(Block block, Player player) {
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return;
        }
        
        if (!(block.getState() instanceof Chest)) {
            return;
        }
        
        Chest chest = (Chest) block.getState();
        InventoryHolder holder = chest.getInventory().getHolder();
        
        if (!(holder instanceof DoubleChest)) {
            return;
        }
        
        DoubleChest doubleChest = (DoubleChest) holder;
        Location left = ((Chest) doubleChest.getLeftSide()).getLocation();
        Location right = ((Chest) doubleChest.getRightSide()).getLocation();
        
        Location other = block.getLocation().equals(left) ? right : left;
        
        if (getLock(other) == null) {
            ChestLockData otherLock = ChestLockData.builder()
                .uuid(UUID.randomUUID())
                .world(other.getWorld().getName())
                .x(other.getBlockX())
                .y(other.getBlockY())
                .z(other.getBlockZ())
                .ownerUuid(player.getUniqueId().toString())
                .ownerName(player.getName())
                .createdAt(System.currentTimeMillis())
                .build();
            
            lockOperator.insert(otherLock);
            lockCache.put(otherLock.getLocationKey(), otherLock);
        }
    }
    
    /**
     * Unlocks a block, reporting success only once the stored record is confirmed gone.
     * <p>
     * The confirmation is a re-query of the store, not the delete call returning: the framework's
     * {@code delById} returns {@code void} and discards the affected-row count. Dropping the cache
     * entry regardless left the cache saying "not locked" while the row was still on disk, so
     * {@code /unlock info} reported the container unlocked and the next restart reloaded the
     * surviving row and locked it again (UltiKits/UltiEssentials#37). The cache entry is therefore
     * kept when the record survives: a cache that disagrees with the store makes the answer a player
     * gets depend on how recently the server booted.
     *
     * @param block  the block to unlock
     * @param player the player asking
     * @return the outcome, including {@link UnlockResult#FAILED} when the record could not be removed
     */
    public UnlockResult unlockBlock(Block block, Player player) {
        ChestLockData lock = getLock(block.getLocation());
        
        if (lock == null) {
            return UnlockResult.NOT_LOCKED;
        }
        
        // Check permission
        if (!lock.getOwnerUuid().equals(player.getUniqueId().toString())
                && !player.hasPermission("ultiessentials.lock.admin")) {
            return UnlockResult.NOT_OWNER;
        }
        
        if (!removeStoredLock(lock)) {
            return UnlockResult.FAILED;
        }
        
        // If it's a double chest, unlock the other half too
        unlockDoubleChestOther(block);
        
        return UnlockResult.SUCCESS;
    }

    /**
     * Deletes one lock record and confirms by re-query that no record remains for its location,
     * dropping the cache entry only then.
     *
     * @param lock the lock record to remove
     * @return true if nothing is stored for that location any more
     */
    private boolean removeStoredLock(ChestLockData lock) {
        lockOperator.delById(lock.getId());
        if (isStored(lock.getWorld(), lock.getX(), lock.getY(), lock.getZ())) {
            log.error("Lock record {} at {} (owner {}) is still stored after a delete; keeping it in "
                    + "the cache so the container does not appear unlocked until the next restart",
                    lock.getId(), lock.getLocationKey(), lock.getOwnerName());
            return false;
        }
        lockCache.remove(lock.getLocationKey());
        return true;
    }

    /**
     * Asks the store — never the cache — whether any lock record exists for a location.
     */
    private boolean isStored(String world, int x, int y, int z) {
        return !lockOperator.query()
            .where("world").eq(world)
            .where("x").eq(x)
            .where("y").eq(y)
            .where("z").eq(z)
            .list()
            .isEmpty();
    }
    
    /**
     * Unlocks the other half of a double chest.
     */
    private void unlockDoubleChestOther(Block block) {
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return;
        }
        
        if (!(block.getState() instanceof Chest)) {
            return;
        }
        
        Chest chest = (Chest) block.getState();
        InventoryHolder holder = chest.getInventory().getHolder();
        
        if (!(holder instanceof DoubleChest)) {
            return;
        }
        
        DoubleChest doubleChest = (DoubleChest) holder;
        Location left = ((Chest) doubleChest.getLeftSide()).getLocation();
        Location right = ((Chest) doubleChest.getRightSide()).getLocation();
        
        Location other = block.getLocation().equals(left) ? right : left;
        
        ChestLockData otherLock = getLock(other);
        if (otherLock != null) {
            removeStoredLock(otherLock);
        }
    }
    
    /**
     * Gets the lock for a location.
     */
    @Nullable
    public ChestLockData getLock(Location location) {
        String key = ChestLockData.createLocationKey(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
        return lockCache.get(key);
    }
    
    /**
     * Checks if a player can access a locked block.
     */
    public boolean canAccess(Location location, Player player) {
        ChestLockData lock = getLock(location);
        
        if (lock == null) {
            return true;
        }
        
        // Owner can always access
        if (lock.getOwnerUuid().equals(player.getUniqueId().toString())) {
            return true;
        }
        
        // Admin can access if config allows
        return config.isChestLockAdminBypass() && player.hasPermission("ultiessentials.lock.admin");
    }
    
    /**
     * Checks if a block is locked.
     */
    public boolean isLocked(Location location) {
        return getLock(location) != null;
    }
    
    /**
     * Removes a lock when its block is broken, reporting whether the stored record is really gone.
     * <p>
     * Previously the cache entry was dropped whatever the store did, which is what
     * UltiKits/UltiEssentials#37 observed: {@code /unlock info} on a chest replaced at the same
     * coordinates reported "not locked" while the row was still on disk, and the next restart
     * reloaded it and locked the container again. The cache is now kept in step with the store in
     * both directions.
     *
     * @param location the broken block's location
     * @return true if a lock existed and is now gone, false if there was none or it survived
     */
    public boolean onBlockBreak(Location location) {
        ChestLockData lock = getLock(location);
        if (lock == null) {
            return false;
        }
        return removeStoredLock(lock);
    }
    
    public enum LockResult {
        SUCCESS,
        NOT_LOCKABLE,
        ALREADY_LOCKED,
        ALREADY_LOCKED_BY_YOU,
        DISABLED
    }
    
    public enum UnlockResult {
        SUCCESS,
        NOT_LOCKED,
        NOT_OWNER,
        /**
         * The record could not be removed from the store, so the container is still locked. Kept
         * distinct from {@link #SUCCESS} because telling a player their container is unlocked when
         * the lock survives is the defect UltiKits/UltiEssentials#37 reports.
         */
        FAILED
    }
}
