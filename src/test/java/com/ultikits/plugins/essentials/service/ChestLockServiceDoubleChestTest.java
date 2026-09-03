package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ChestLockService}'s double-chest propagation behaviour:
 * locking or unlocking one half of a double chest also locks/unlocks the
 * other half.
 * <p>
 * The existing {@code ChestLockServiceMockitoTest} deliberately configures every mock
 * block's {@code getState()} to return a non-{@code Chest} {@code BlockState}
 * ("prevents double-chest path", per that file's own {@code createMockBlock} comment),
 * so {@code lockDoubleChestOther}/{@code unlockDoubleChestOther}'s decision surface —
 * chest-type guard, chest-state guard, double-chest-holder guard, left/right side
 * selection, and the already-locked/already-unlocked guard on the other half — is
 * never exercised there. This class exercises exactly that surface.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("ChestLockService double-chest propagation Tests")
class ChestLockServiceDoubleChestTest {

    private ChestLockService service;
    private EssentialsConfig config;

    @SuppressWarnings("unchecked")
    private final DataOperator<ChestLockData> lockOperator = mock(DataOperator.class);

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();

        service = new ChestLockService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(service, "lockOperator", lockOperator);

        reset(lockOperator);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ChestLockData> cache() throws Exception {
        return (Map<String, ChestLockData>) EssentialsTestHelper.getField(service, "lockCache");
    }

    /** A single, non-double chest (or non-chest lockable) block. State is a plain BlockState. */
    private Block createNonDoubleChestBlock(Material material, World world, int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        Location loc = new Location(world, x, y, z);
        when(block.getLocation()).thenReturn(loc);
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        return block;
    }

    /** A chest block whose state IS a Chest, but whose inventory holder is a single (non-double) chest. */
    private Block createSingleChestBlockWithChestState(World world, int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        Location loc = new Location(world, x, y, z);
        when(block.getLocation()).thenReturn(loc);

        Chest chestState = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        when(chestState.getInventory()).thenReturn(inventory);
        InventoryHolder singleHolder = mock(InventoryHolder.class);
        when(inventory.getHolder()).thenReturn(singleHolder);
        when(block.getState()).thenReturn(chestState);
        return block;
    }

    /**
     * A chest block that is one half of a double chest. {@code isLeftSide} selects which of
     * {@code doubleChest.getLeftSide()}/{@code getRightSide()} resolves to this block's own
     * location (matching the ternary's true branch) vs. the other half's location.
     */
    private Block createDoubleChestBlock(World world, int x, int y, int z, boolean isLeftSide, Location otherLoc) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        Location loc = new Location(world, x, y, z);
        when(block.getLocation()).thenReturn(loc);

        Chest chestState = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        when(chestState.getInventory()).thenReturn(inventory);

        DoubleChest doubleChest = mock(DoubleChest.class);
        when(inventory.getHolder()).thenReturn(doubleChest);

        Chest leftChest = mock(Chest.class);
        Chest rightChest = mock(Chest.class);
        if (isLeftSide) {
            when(leftChest.getLocation()).thenReturn(loc);
            when(rightChest.getLocation()).thenReturn(otherLoc);
        } else {
            when(leftChest.getLocation()).thenReturn(otherLoc);
            when(rightChest.getLocation()).thenReturn(loc);
        }
        when(doubleChest.getLeftSide()).thenReturn(leftChest);
        when(doubleChest.getRightSide()).thenReturn(rightChest);

        when(block.getState()).thenReturn(chestState);
        return block;
    }

    @Nested
    @DisplayName("lockDoubleChestOther (reached via lockBlock)")
    class LockDoubleChestOtherTests {

        @Test
        @DisplayName("locking a barrel (lockable, but not CHEST/TRAPPED_CHEST) never attempts to lock a second block")
        void barrelDoesNotPropagateLock() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createNonDoubleChestBlock(Material.BARREL, world, 1, 64, 1);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.SUCCESS);
            verify(lockOperator, times(1)).insert(any(ChestLockData.class));
        }

        @Test
        @DisplayName("locking a chest that is not part of a double chest never attempts to lock a second block")
        void singleChestDoesNotPropagateLock() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createSingleChestBlockWithChestState(world, 1, 64, 1);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.SUCCESS);
            verify(lockOperator, times(1)).insert(any(ChestLockData.class));
        }

        @Test
        @DisplayName("locking the left half of a double chest also locks the right half")
        void locksRightHalfWhenLockingLeftHalf() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location rightLoc = new Location(world, 2, 64, 1);
            Block leftBlock = createDoubleChestBlock(world, 1, 64, 1, true, rightLoc);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(leftBlock, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.SUCCESS);
            verify(lockOperator, times(2)).insert(any(ChestLockData.class));
            assertThat(service.isLocked(rightLoc)).isTrue();
        }

        @Test
        @DisplayName("locking the right half of a double chest also locks the left half")
        void locksLeftHalfWhenLockingRightHalf() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location leftLoc = new Location(world, 1, 64, 1);
            Block rightBlock = createDoubleChestBlock(world, 2, 64, 1, false, leftLoc);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(rightBlock, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.SUCCESS);
            verify(lockOperator, times(2)).insert(any(ChestLockData.class));
            assertThat(service.isLocked(leftLoc)).isTrue();
        }

        @Test
        @DisplayName("locking a double chest whose other half is already locked does not create a duplicate lock")
        void doesNotDuplicateLockWhenOtherHalfAlreadyLocked() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location rightLoc = new Location(world, 2, 64, 1);
            Block leftBlock = createDoubleChestBlock(world, 1, 64, 1, true, rightLoc);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockData existingOtherLock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(2).y(64).z(1)
                    .ownerUuid(UUID.randomUUID().toString())
                    .ownerName("Other")
                    .build();
            cache().put(existingOtherLock.getLocationKey(), existingOtherLock);

            ChestLockService.LockResult result = service.lockBlock(leftBlock, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.SUCCESS);
            // Exactly one insert -- for the block actually locked. The already-locked other
            // half must not receive a second, duplicate insert.
            verify(lockOperator, times(1)).insert(any(ChestLockData.class));
        }
    }

    @Nested
    @DisplayName("unlockDoubleChestOther (reached via unlockBlock)")
    class UnlockDoubleChestOtherTests {

        private void seedLock(Location loc, UUID owner) throws Exception {
            ChestLockData lock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world(loc.getWorld().getName())
                    .x(loc.getBlockX()).y(loc.getBlockY()).z(loc.getBlockZ())
                    .ownerUuid(owner.toString())
                    .ownerName("Steve")
                    .build();
            cache().put(lock.getLocationKey(), lock);
        }

        @Test
        @DisplayName("unlocking a barrel never attempts to unlock a second block")
        void barrelDoesNotPropagateUnlock() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID playerUuid = UUID.randomUUID();
            Location loc = new Location(world, 1, 64, 1);
            seedLock(loc, playerUuid);
            Block block = createNonDoubleChestBlock(Material.BARREL, world, 1, 64, 1);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            ChestLockService.UnlockResult result = service.unlockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
            verify(lockOperator, times(1)).delById(any());
        }

        @Test
        @DisplayName("unlocking a chest that is not part of a double chest never attempts to unlock a second block")
        void singleChestDoesNotPropagateUnlock() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID playerUuid = UUID.randomUUID();
            Location loc = new Location(world, 1, 64, 1);
            seedLock(loc, playerUuid);
            Block block = createSingleChestBlockWithChestState(world, 1, 64, 1);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            ChestLockService.UnlockResult result = service.unlockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
            verify(lockOperator, times(1)).delById(any());
        }

        @Test
        @DisplayName("unlocking the left half of a double chest also unlocks the right half")
        void unlocksRightHalfWhenUnlockingLeftHalf() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID playerUuid = UUID.randomUUID();
            Location leftLoc = new Location(world, 1, 64, 1);
            Location rightLoc = new Location(world, 2, 64, 1);
            seedLock(leftLoc, playerUuid);
            seedLock(rightLoc, playerUuid);
            Block leftBlock = createDoubleChestBlock(world, 1, 64, 1, true, rightLoc);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            ChestLockService.UnlockResult result = service.unlockBlock(leftBlock, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
            verify(lockOperator, times(2)).delById(any());
            assertThat(service.isLocked(rightLoc)).isFalse();
        }

        @Test
        @DisplayName("unlocking the right half of a double chest also unlocks the left half")
        void unlocksLeftHalfWhenUnlockingRightHalf() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID playerUuid = UUID.randomUUID();
            Location leftLoc = new Location(world, 1, 64, 1);
            Location rightLoc = new Location(world, 2, 64, 1);
            seedLock(leftLoc, playerUuid);
            seedLock(rightLoc, playerUuid);
            Block rightBlock = createDoubleChestBlock(world, 2, 64, 1, false, leftLoc);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            ChestLockService.UnlockResult result = service.unlockBlock(rightBlock, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
            verify(lockOperator, times(2)).delById(any());
            assertThat(service.isLocked(leftLoc)).isFalse();
        }

        @Test
        @DisplayName("unlocking a double chest whose other half has no lock does not attempt a second delete")
        void doesNotDeleteWhenOtherHalfNotLocked() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID playerUuid = UUID.randomUUID();
            Location leftLoc = new Location(world, 1, 64, 1);
            Location rightLoc = new Location(world, 2, 64, 1);
            seedLock(leftLoc, playerUuid); // only this half is locked
            Block leftBlock = createDoubleChestBlock(world, 1, 64, 1, true, rightLoc);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            ChestLockService.UnlockResult result = service.unlockBlock(leftBlock, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
            // Exactly one delete -- for the block actually unlocked. The already-unlocked
            // other half must not trigger a second, spurious delete.
            verify(lockOperator, times(1)).delById(any());
        }
    }

    @Nested
    @DisplayName("canAccess admin bypass")
    class CanAccessAdminBypassTests {

        @Test
        @DisplayName("admin without bypass config enabled cannot access another player's locked chest, even with the admin permission node")
        void adminCannotAccessWhenBypassConfigDisabled() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            config.setChestLockAdminBypass(false);

            ChestLockData lock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(UUID.randomUUID().toString())
                    .build();
            cache().put(lock.getLocationKey(), lock);

            Location loc = new Location(world, 100, 64, 200);
            Player admin = EssentialsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(admin.hasPermission("ultiessentials.lock.admin")).thenReturn(true);

            assertThat(service.canAccess(loc, admin)).isFalse();
        }
    }
}
