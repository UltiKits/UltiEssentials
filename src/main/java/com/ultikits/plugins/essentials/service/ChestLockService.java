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
        
        // Every record protecting this container comes out together or none does. Removing them one
        // at a time left the first half dropped from the cache while the second half's record
        // survived, and since the interact check keys on the clicked block, clicking that now-uncached
        // half opened the shared inventory the surviving record was still protecting -- a bypass
        // introduced by returning FAILED after the first removal had already taken effect (gate 2 P1).
        return removeAllStoredLocks(locksProtectingSameContainerAs(block))
                ? UnlockResult.SUCCESS : UnlockResult.FAILED;
    }

    /**
     * Removes a set of lock records all together, or leaves every one of them exactly as it was.
     * <p>
     * The confirmation runs <strong>inside</strong> the transaction and aborts it, so a record that
     * the store will not give up rolls the whole removal back rather than leaving some records gone
     * and some present. The cache is mutated only afterwards, and only if the store agreed on all of
     * them, so there is no point at which the cache and the store disagree about part of a container.
     * <p>
     * Removing them one at a time is what produced gate 2's bypass: the first half was dropped from
     * the cache, the second half's record survived, and the interact check -- which keys on the
     * clicked block -- then allowed clicks on the uncached half into the shared inventory the
     * surviving record was still protecting. Restoring the first record on failure would be a
     * compensating write that can itself fail, and would leave the same hazard for any future
     * multi-record lock; this makes the intermediate state unable to exist instead.
     * <p>
     * On a store with no transaction manager bound the framework runs the action with no transaction
     * at all, so the deletes that succeeded stand. The cache is still not touched, which errs towards
     * reporting the container as locked -- protective, and the opposite of a bypass.
     *
     * @param locks every record protecting the container, possibly one
     * @return true if all of them are gone; false if any survived, with none of them dropped
     */
    private boolean removeAllStoredLocks(List<ChestLockData> locks) {
        if (locks.isEmpty()) {
            return true;
        }
        try {
            lockOperator.transaction(() -> {
                for (ChestLockData lock : locks) {
                    lockOperator.delById(lock.getId());
                }
                for (ChestLockData lock : locks) {
                    if (isStored(lock.getWorld(), lock.getX(), lock.getY(), lock.getZ())) {
                        // Aborts the transaction, which is the point: every delete in it is undone.
                        throw new IllegalStateException("Lock record " + lock.getId() + " at "
                                + lock.getLocationKey() + " is still stored after a delete");
                    }
                }
                return null;
            });
        } catch (Exception e) {
            StringBuilder kept = new StringBuilder();
            for (ChestLockData lock : locks) {
                kept.append(' ').append(lock.getLocationKey());
            }
            log.error("Could not remove the {} lock record(s) protecting this container, so none of "
                    + "them was removed and all of them stay in the cache -- the container keeps "
                    + "reporting as locked rather than half-unlocked. Locations:{}",
                    locks.size(), kept, e);
            return false;
        }
        for (ChestLockData lock : locks) {
            lockCache.remove(lock.getLocationKey());
        }
        return true;
    }

    /**
     * Every cached lock record protecting the same container as {@code block} -- its own, plus the
     * other half's when the block is part of a double chest, because the two halves share one
     * inventory.
     *
     * @param block a container block
     * @return the records protecting it, in no particular order, possibly empty
     */
    private List<ChestLockData> locksProtectingSameContainerAs(Block block) {
        List<ChestLockData> locks = new ArrayList<>(2);
        ChestLockData own = getLock(block.getLocation());
        if (own != null) {
            locks.add(own);
        }
        Location other = doubleChestOtherHalf(block);
        if (other != null) {
            ChestLockData otherLock = getLock(other);
            if (otherLock != null) {
                locks.add(otherLock);
            }
        }
        return locks;
    }

    /**
     * The location of the other half of {@code block}'s double chest, or null when it is not one.
     */
    @Nullable
    private Location doubleChestOtherHalf(Block block) {
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return null;
        }
        if (!(block.getState() instanceof Chest)) {
            return null;
        }
        Chest chest = (Chest) block.getState();
        InventoryHolder holder = chest.getInventory().getHolder();
        if (!(holder instanceof DoubleChest)) {
            return null;
        }
        DoubleChest doubleChest = (DoubleChest) holder;
        Location left = ((Chest) doubleChest.getLeftSide()).getLocation();
        Location right = ((Chest) doubleChest.getRightSide()).getLocation();
        return block.getLocation().equals(left) ? right : left;
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
     * Checks whether a player may open the container this block belongs to, keyed on the
     * <strong>container</strong> rather than on the block they clicked.
     * <p>
     * A double chest is one shared inventory behind two blocks, so opening either block opens the same
     * items. Keying the check on the clicked block alone means a lock record covering one half does not
     * protect clicks on the other -- measured: every protection check in this module took a single
     * {@code Location}, and {@code DoubleChest} was consulted only in the lock and unlock paths. Gate 2
     * found one route to that divergence (a partly-completed unlock, now impossible); this closes the
     * rest, including a lock whose second half was never written and legacy data holding only one half.
     * <p>
     * A player who may access every record protecting the container may open it; one denied by any of
     * them may not.
     *
     * @param block  the container block the player is acting on
     * @param player the player
     * @return true if no record protecting this container denies them
     */
    public boolean canAccess(Block block, Player player) {
        for (ChestLockData lock : locksProtectingSameContainerAs(block)) {
            if (!canAccess(lock, player)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The record protecting this container that denies {@code player}, or null if none does. Used to
     * name an owner in a refusal message.
     *
     * @param block  the container block
     * @param player the player
     * @return the denying record, or null
     */
    @Nullable
    public ChestLockData denyingLock(Block block, Player player) {
        for (ChestLockData lock : locksProtectingSameContainerAs(block)) {
            if (!canAccess(lock, player)) {
                return lock;
            }
        }
        return null;
    }

    private boolean canAccess(ChestLockData lock, Player player) {
        if (lock.getOwnerUuid().equals(player.getUniqueId().toString())) {
            return true;
        }
        return config.isChestLockAdminBypass() && player.hasPermission("ultiessentials.lock.admin");
    }

    /**
     * Checks if a player can access a locked block.
     * <p>
     * Keyed on one location, so for a double chest it answers for that half only. Prefer
     * {@link #canAccess(Block, Player)}, which answers for the whole container.
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
        // Exactly this location's record: the other half of a double chest is still standing, so its
        // own lock must survive. Routed through the same all-or-nothing path so there is one place
        // where the cache is mutated after the store agrees.
        return removeAllStoredLocks(Collections.singletonList(lock));
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
