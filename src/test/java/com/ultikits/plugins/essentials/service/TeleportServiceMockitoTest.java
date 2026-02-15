package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for TeleportService.
 * <p>
 * 传送服务测试。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("TeleportService Tests (Mockito)")
class TeleportServiceMockitoTest {

    private TeleportService teleportService;
    private Plugin mockBukkitPlugin;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        mockBukkitPlugin = mock(Plugin.class);

        // Mock scheduler
        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        BukkitTask mockTask = mock(BukkitTask.class);
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        teleportService = new TeleportService();
        EssentialsTestHelper.setField(teleportService, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(teleportService, "bukkitPlugin", mockBukkitPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("teleport - instant")
    class InstantTeleportTests {

        @Test
        @DisplayName("Should teleport instantly with 0 warmup")
        void shouldTeleportInstantly() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            Location target = new Location(world, 100, 64, 200);

            TeleportResult result = teleportService.teleport(player, target, 0, false);

            assertThat(result).isEqualTo(TeleportResult.SUCCESS);
            verify(player).teleport(target);
        }

        @Test
        @DisplayName("Should teleport instantly with negative warmup")
        void shouldTeleportInstantlyWithNegativeWarmup() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            Location target = new Location(world, 100, 64, 200);

            TeleportResult result = teleportService.teleport(player, target, -1, false);

            assertThat(result).isEqualTo(TeleportResult.SUCCESS);
            verify(player).teleport(target);
        }

        @Test
        @DisplayName("Should call onSuccess callback on instant teleport")
        void shouldCallOnSuccessCallback() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            Location target = new Location(world, 100, 64, 200);
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<Player> onSuccess = mock(java.util.function.Consumer.class);

            TeleportResult result = teleportService.teleport(player, target, 0, false, onSuccess, null);

            assertThat(result).isEqualTo(TeleportResult.SUCCESS);
            verify(onSuccess).accept(player);
        }
    }

    @Nested
    @DisplayName("teleport - warmup")
    class WarmupTeleportTests {

        @Test
        @DisplayName("Should start warmup with positive warmup time")
        void shouldStartWarmup() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            Location target = new Location(world, 100, 64, 200);

            TeleportResult result = teleportService.teleport(player, target, 5, true);

            assertThat(result).isEqualTo(TeleportResult.WARMUP_STARTED);
        }

        @Test
        @DisplayName("Should return ALREADY_TELEPORTING when already warming up")
        void shouldReturnAlreadyTeleporting() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            Location target = new Location(world, 100, 64, 200);

            teleportService.teleport(player, target, 5, true);
            TeleportResult result = teleportService.teleport(player, target, 5, true);

            assertThat(result).isEqualTo(TeleportResult.ALREADY_TELEPORTING);
        }
    }

    @Nested
    @DisplayName("cancelTeleport")
    class CancelTeleportTests {

        @Test
        @DisplayName("Should cancel pending teleport")
        void shouldCancelPending() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID uuid = UUID.randomUUID();
            Player player = EssentialsTestHelper.createMockPlayer("Steve", uuid);
            Location target = new Location(world, 100, 64, 200);

            teleportService.teleport(player, target, 5, true);
            teleportService.cancelTeleport(uuid);

            assertThat(teleportService.isTeleporting(uuid)).isFalse();
        }

        @Test
        @DisplayName("Should not throw when no pending teleport")
        void shouldNotThrowWhenNoPending() {
            teleportService.cancelTeleport(UUID.randomUUID());
            // Should not throw
        }
    }

    @Nested
    @DisplayName("isTeleporting")
    class IsTeleportingTests {

        @Test
        @DisplayName("Should return false initially")
        void shouldReturnFalseInitially() {
            assertThat(teleportService.isTeleporting(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("Should return true during warmup")
        void shouldReturnTrueDuringWarmup() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID uuid = UUID.randomUUID();
            Player player = EssentialsTestHelper.createMockPlayer("Steve", uuid);
            Location target = new Location(world, 100, 64, 200);

            teleportService.teleport(player, target, 5, true);

            assertThat(teleportService.isTeleporting(uuid)).isTrue();
        }

        @Test
        @DisplayName("Should return false after cancel")
        void shouldReturnFalseAfterCancel() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID uuid = UUID.randomUUID();
            Player player = EssentialsTestHelper.createMockPlayer("Steve", uuid);
            Location target = new Location(world, 100, 64, 200);

            teleportService.teleport(player, target, 5, true);
            teleportService.cancelTeleport(uuid);

            assertThat(teleportService.isTeleporting(uuid)).isFalse();
        }
    }
}
