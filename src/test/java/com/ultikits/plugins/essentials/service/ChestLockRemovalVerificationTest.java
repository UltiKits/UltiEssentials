package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.service.ChestLockService.UnlockResult;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.SilentlyFailingStore;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * A container lock may be treated as removed only once the stored record is confirmed gone
 * (UltiKits/UltiEssentials#37).
 * <p>
 * Both removal paths dropped the in-memory cache entry unconditionally after calling
 * {@code delById}, which addressed its row by a primary key that was never written. The cache then
 * said "not locked" while the row was still on disk — so {@code /unlock info} reported the container
 * unlocked, a chest replaced at the same coordinates was unlocked, and the next restart reloaded the
 * surviving row and locked it again.
 * <p>
 * The divergence is the defect, not merely the surviving row: as long as the cache and the store
 * disagree, the answer a player gets depends on how recently the server booted. So the assertions
 * here check the store <em>and</em> the cache after each attempt.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Verified chest-lock removal (UltiKits/UltiEssentials#37)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ChestLockRemovalVerificationTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private World world;
    private PlayerMock owner;
    private SilentlyFailingStore<ChestLockData> store;
    private ChestLockService lockService;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();

        world = server.addSimpleWorld("world");
        owner = server.addPlayer("LockOwner");

        EssentialsConfig config = mock(EssentialsConfig.class);
        lenient().when(config.isChestLockEnabled()).thenReturn(true);
        lenient().when(config.isChestLockAdminBypass()).thenReturn(true);

        store = new SilentlyFailingStore<>(tempDir.toFile().getAbsolutePath(), ChestLockData.class);

        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(plugin.getDataOperator(ChestLockData.class)).thenReturn(store);

        lockService = new ChestLockService();
        setField(lockService, "config", config);
        setField(lockService, "plugin", plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Nested
    @DisplayName("/unlock")
    class UnlockCommandPathTests {

        @Test
        @DisplayName("a lock that really is removed reports success, and leaves store and cache agreeing")
        void aRemovedLockReportsSuccess() {
            Location location = new Location(world, 100, 64, 100);
            storeLockAt(location);
            lockService.init();

            UnlockResult result = lockService.unlockBlock(new BlockMock(Material.CHEST, location), owner);

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(result).isEqualTo(UnlockResult.SUCCESS);
            assertThat(persistedLocksAt(location)).as("stored records for that location").isZero();
            assertThat(lockService.isLocked(location)).as("cached state").isFalse();
        }

        @Test
        @DisplayName("a lock the store silently kept does NOT report success, and store and cache still agree")
        void aKeptLockDoesNotReportSuccess() {
            Location location = new Location(world, 100, 64, 100);
            storeLockAt(location);
            lockService.init();
            store.ignoreDeletes();

            UnlockResult result = lockService.unlockBlock(new BlockMock(Material.CHEST, location), owner);

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(result).as("reported outcome while the record survives").isEqualTo(UnlockResult.FAILED);
            assertThat(persistedLocksAt(location)).as("stored records for that location").isEqualTo(1);
            assertThat(lockService.isLocked(location))
                .as("cached state must still agree with the store, or a restart changes the answer")
                .isTrue();
        }

        @Test
        @DisplayName("an unlocked container is still reported as not locked, without touching the store")
        void anUnlockedContainerIsReportedNotLocked() {
            lockService.init();

            UnlockResult result = lockService.unlockBlock(
                new BlockMock(Material.CHEST, new Location(world, 5, 64, 5)), owner);

            assertThat(result).isEqualTo(UnlockResult.NOT_LOCKED);
            assertThat(store.deleteAttempts()).isZero();
        }

        @Test
        @DisplayName("a lock owned by someone else is refused without touching the store")
        void someoneElsesLockIsRefused() {
            Location location = new Location(world, 100, 64, 100);
            store.insert(lockAt(location, UUID.randomUUID(), "SomebodyElse"));
            lockService.init();
            PlayerMock stranger = server.addPlayer("Stranger");

            UnlockResult result = lockService.unlockBlock(new BlockMock(Material.CHEST, location), stranger);

            assertThat(result).isEqualTo(UnlockResult.NOT_OWNER);
            assertThat(store.deleteAttempts()).isZero();
            assertThat(persistedLocksAt(location)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Owner breaks their own locked container")
    class BlockBreakPathTests {

        @Test
        @DisplayName("a lock that really is removed leaves nothing stored and nothing cached")
        void aRemovedLockLeavesNothing() {
            Location location = new Location(world, 100, 64, 100);
            storeLockAt(location);
            lockService.init();

            boolean removed = lockService.onBlockBreak(location);

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(removed).isTrue();
            assertThat(persistedLocksAt(location)).isZero();
            assertThat(lockService.isLocked(location)).isFalse();
        }

        @Test
        @DisplayName("a lock the store silently kept stays cached, so the next restart does not change the answer")
        void aKeptLockStaysCached() {
            Location location = new Location(world, 100, 64, 100);
            storeLockAt(location);
            lockService.init();
            store.ignoreDeletes();

            boolean removed = lockService.onBlockBreak(location);

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(removed).as("reported outcome while the record survives").isFalse();
            assertThat(persistedLocksAt(location)).isEqualTo(1);
            assertThat(lockService.isLocked(location))
                .as("this is the divergence #37 reported: cache said unlocked, disk said locked")
                .isTrue();
        }

        @Test
        @DisplayName("breaking an unlocked container reports nothing removed and touches no store")
        void breakingAnUnlockedContainerRemovesNothing() {
            lockService.init();

            boolean removed = lockService.onBlockBreak(new Location(world, 5, 64, 5));

            assertThat(removed).isFalse();
            assertThat(store.deleteAttempts()).isZero();
        }
    }

    @Nested
    @DisplayName("Reload after a failed removal")
    class ReloadTests {

        @Test
        @DisplayName("a surviving record is still there after a cache reload, which is what a restart does")
        void aSurvivingRecordComesBackOnReload() throws Exception {
            Location location = new Location(world, 100, 64, 100);
            storeLockAt(location);
            lockService.init();
            store.ignoreDeletes();

            lockService.onBlockBreak(location);
            // What ChestLockService#init does on boot: rebuild the cache from the store.
            ChestLockService rebooted = new ChestLockService();
            setField(rebooted, "config", getField(lockService, "config"));
            setField(rebooted, "plugin", getField(lockService, "plugin"));
            rebooted.init();

            assertThat(rebooted.isLocked(location))
                .as("the record survived, so a reboot still reports the container locked")
                .isTrue();
        }
    }

    // === fixtures ===

    private void storeLockAt(Location location) {
        store.insert(lockAt(location, owner.getUniqueId(), owner.getName()));
    }

    private static ChestLockData lockAt(Location location, UUID ownerUuid, String ownerName) {
        return ChestLockData.builder()
            .uuid(UUID.randomUUID())
            .world(location.getWorld().getName())
            .x(location.getBlockX())
            .y(location.getBlockY())
            .z(location.getBlockZ())
            .ownerUuid(ownerUuid.toString())
            .ownerName(ownerName)
            .createdAt(System.currentTimeMillis())
            .build();
    }

    /**
     * Counts what is really stored for a location, bypassing the service's cache entirely.
     */
    private int persistedLocksAt(Location location) {
        int count = 0;
        for (ChestLockData lock : store.getAll()) {
            if (lock.getLocationKey().equals(ChestLockData.createLocationKey(
                    location.getWorld().getName(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ()))) {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = ChestLockService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static Object getField(Object target, String name) throws Exception {
        Field field = ChestLockService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
