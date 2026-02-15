package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for ChestLockService.
 * <p>
 * 箱子锁服务测试。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("ChestLockService Tests (Mockito)")
class ChestLockServiceMockitoTest {

    private ChestLockService service;
    private EssentialsConfig config;

    @SuppressWarnings("unchecked")
    private DataOperator<ChestLockData> lockOperator = mock(DataOperator.class);

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();

        service = new ChestLockService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(service, "lockOperator", lockOperator);

        reset(lockOperator);
        when(lockOperator.getAll()).thenReturn(new ArrayList<>());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private Block createMockBlock(Material material, World world, int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        Location loc = new Location(world, x, y, z);
        when(block.getLocation()).thenReturn(loc);
        // Default: not a Chest state (prevents double-chest path)
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        return block;
    }

    @Nested
    @DisplayName("isLockable")
    class IsLockableTests {

        @Test
        @DisplayName("Should return true for chest")
        void shouldReturnTrueForChest() {
            assertThat(service.isLockable(Material.CHEST)).isTrue();
        }

        @Test
        @DisplayName("Should return true for trapped chest")
        void shouldReturnTrueForTrappedChest() {
            assertThat(service.isLockable(Material.TRAPPED_CHEST)).isTrue();
        }

        @Test
        @DisplayName("Should return true for barrel")
        void shouldReturnTrueForBarrel() {
            assertThat(service.isLockable(Material.BARREL)).isTrue();
        }

        @Test
        @DisplayName("Should return true for furnace")
        void shouldReturnTrueForFurnace() {
            assertThat(service.isLockable(Material.FURNACE)).isTrue();
        }

        @Test
        @DisplayName("Should return true for hopper")
        void shouldReturnTrueForHopper() {
            assertThat(service.isLockable(Material.HOPPER)).isTrue();
        }

        @Test
        @DisplayName("Should return true for shulker box")
        void shouldReturnTrueForShulkerBox() {
            assertThat(service.isLockable(Material.SHULKER_BOX)).isTrue();
        }

        @Test
        @DisplayName("Should return false for stone")
        void shouldReturnFalseForStone() {
            assertThat(service.isLockable(Material.STONE)).isFalse();
        }

        @Test
        @DisplayName("Should return false for dirt")
        void shouldReturnFalseForDirt() {
            assertThat(service.isLockable(Material.DIRT)).isFalse();
        }
    }

    @Nested
    @DisplayName("lockBlock")
    class LockBlockTests {

        @Test
        @DisplayName("Should lock block successfully")
        void shouldLockSuccessfully() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.SUCCESS);
            verify(lockOperator).insert(any(ChestLockData.class));
        }

        @Test
        @DisplayName("Should return NOT_LOCKABLE for non-lockable block")
        void shouldReturnNotLockable() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.STONE, world, 100, 64, 200);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.NOT_LOCKABLE);
        }

        @Test
        @DisplayName("Should return DISABLED when chest lock is disabled")
        void shouldReturnDisabled() {
            config.setChestLockEnabled(false);
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.DISABLED);
        }

        @Test
        @DisplayName("Should return ALREADY_LOCKED when locked by another player")
        void shouldReturnAlreadyLocked() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);
            UUID otherUuid = UUID.randomUUID();

            // Pre-populate cache with existing lock
            ChestLockData existing = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(otherUuid.toString())
                    .ownerName("Other")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(existing.getLocationKey(), existing);

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.LockResult result = service.lockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.ALREADY_LOCKED);
        }

        @Test
        @DisplayName("Should return ALREADY_LOCKED_BY_YOU when already own the lock")
        void shouldReturnAlreadyLockedByYou() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);
            UUID playerUuid = UUID.randomUUID();

            ChestLockData existing = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(playerUuid.toString())
                    .ownerName("Steve")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(existing.getLocationKey(), existing);

            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            ChestLockService.LockResult result = service.lockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.LockResult.ALREADY_LOCKED_BY_YOU);
        }
    }

    @Nested
    @DisplayName("unlockBlock")
    class UnlockBlockTests {

        @Test
        @DisplayName("Should unlock block successfully")
        void shouldUnlockSuccessfully() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID playerUuid = UUID.randomUUID();
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);

            UUID lockId = UUID.randomUUID();
            ChestLockData lock = ChestLockData.builder()
                    .uuid(lockId)
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(playerUuid.toString())
                    .ownerName("Steve")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            ChestLockService.UnlockResult result = service.unlockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
            verify(lockOperator).delById(lockId);
        }

        @Test
        @DisplayName("Should return NOT_LOCKED when block is not locked")
        void shouldReturnNotLocked() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.UnlockResult result = service.unlockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.NOT_LOCKED);
        }

        @Test
        @DisplayName("Should return NOT_OWNER when player does not own lock")
        void shouldReturnNotOwner() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);

            ChestLockData lock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(UUID.randomUUID().toString())
                    .ownerName("Other")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            ChestLockService.UnlockResult result = service.unlockBlock(block, player);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.NOT_OWNER);
        }

        @Test
        @DisplayName("Should allow admin to unlock")
        void shouldAllowAdminUnlock() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Block block = createMockBlock(Material.CHEST, world, 100, 64, 200);

            UUID lockId = UUID.randomUUID();
            ChestLockData lock = ChestLockData.builder()
                    .uuid(lockId)
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(UUID.randomUUID().toString())
                    .ownerName("Other")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Player admin = EssentialsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(admin.hasPermission("ultiessentials.lock.admin")).thenReturn(true);

            ChestLockService.UnlockResult result = service.unlockBlock(block, admin);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
        }
    }

    @Nested
    @DisplayName("canAccess")
    class CanAccessTests {

        @Test
        @DisplayName("Should return true when no lock")
        void shouldReturnTrueWhenNoLock() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 100, 64, 200);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            assertThat(service.canAccess(loc, player)).isTrue();
        }

        @Test
        @DisplayName("Should return true when owner")
        void shouldReturnTrueWhenOwner() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID playerUuid = UUID.randomUUID();

            ChestLockData lock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(playerUuid.toString())
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Location loc = new Location(world, 100, 64, 200);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", playerUuid);

            assertThat(service.canAccess(loc, player)).isTrue();
        }

        @Test
        @DisplayName("Should return false when not owner and not admin")
        void shouldReturnFalseWhenNotOwner() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");

            ChestLockData lock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(UUID.randomUUID().toString())
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Location loc = new Location(world, 100, 64, 200);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            assertThat(service.canAccess(loc, player)).isFalse();
        }

        @Test
        @DisplayName("Should return true for admin with bypass")
        void shouldReturnTrueForAdmin() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            config.setChestLockAdminBypass(true);

            ChestLockData lock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(UUID.randomUUID().toString())
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Location loc = new Location(world, 100, 64, 200);
            Player admin = EssentialsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(admin.hasPermission("ultiessentials.lock.admin")).thenReturn(true);

            assertThat(service.canAccess(loc, admin)).isTrue();
        }
    }

    @Nested
    @DisplayName("isLocked")
    class IsLockedTests {

        @Test
        @DisplayName("Should return false when not locked")
        void shouldReturnFalseWhenNotLocked() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 100, 64, 200);

            assertThat(service.isLocked(loc)).isFalse();
        }

        @Test
        @DisplayName("Should return true when locked")
        void shouldReturnTrueWhenLocked() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");

            ChestLockData lock = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(UUID.randomUUID().toString())
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Location loc = new Location(world, 100, 64, 200);

            assertThat(service.isLocked(loc)).isTrue();
        }
    }

    @Nested
    @DisplayName("onBlockBreak")
    class OnBlockBreakTests {

        @Test
        @DisplayName("Should remove lock on block break")
        void shouldRemoveLockOnBreak() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID lockId = UUID.randomUUID();

            ChestLockData lock = ChestLockData.builder()
                    .uuid(lockId)
                    .world("world").x(100).y(64).z(200)
                    .ownerUuid(UUID.randomUUID().toString())
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, ChestLockData> cache = (Map<String, ChestLockData>)
                    EssentialsTestHelper.getField(service, "lockCache");
            cache.put(lock.getLocationKey(), lock);

            Location loc = new Location(world, 100, 64, 200);
            service.onBlockBreak(loc);

            verify(lockOperator).delById(lockId);
            assertThat(service.isLocked(loc)).isFalse();
        }

        @Test
        @DisplayName("Should do nothing when not locked")
        void shouldDoNothingWhenNotLocked() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 100, 64, 200);

            service.onBlockBreak(loc);

            verify(lockOperator, never()).delById(any());
        }
    }

    @Nested
    @DisplayName("loadCache")
    class LoadCacheTests {

        @Test
        @DisplayName("Should load locks from data operator during init")
        void shouldLoadLocksDuringInit() throws Exception {
            ChestLockData lock1 = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(10).y(64).z(20)
                    .ownerUuid("uuid1")
                    .build();
            ChestLockData lock2 = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(30).y(64).z(40)
                    .ownerUuid("uuid2")
                    .build();

            when(lockOperator.getAll()).thenReturn(Arrays.asList(lock1, lock2));

            // Manually call init to trigger loadCache
            when(EssentialsTestHelper.getMockPlugin().getDataOperator(ChestLockData.class))
                    .thenReturn(lockOperator);
            service.init();

            World world = EssentialsTestHelper.createMockWorld("world");
            assertThat(service.isLocked(new Location(world, 10, 64, 20))).isTrue();
            assertThat(service.isLocked(new Location(world, 30, 64, 40))).isTrue();
        }
    }

    @Nested
    @DisplayName("Enum coverage")
    class EnumTests {

        @Test
        @DisplayName("LockResult should have all values")
        void lockResultValues() {
            assertThat(ChestLockService.LockResult.values()).hasSize(5);
            assertThat(ChestLockService.LockResult.valueOf("SUCCESS"))
                    .isEqualTo(ChestLockService.LockResult.SUCCESS);
        }

        @Test
        @DisplayName("UnlockResult should have all values")
        void unlockResultValues() {
            assertThat(ChestLockService.UnlockResult.values()).hasSize(3);
            assertThat(ChestLockService.UnlockResult.valueOf("SUCCESS"))
                    .isEqualTo(ChestLockService.UnlockResult.SUCCESS);
        }
    }
}
