package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for WarpService.
 * <p>
 * 地标点服务测试。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("WarpService Tests (Mockito)")
class WarpServiceMockitoTest {

    private WarpService warpService;
    private EssentialsConfig config;
    private TeleportService teleportService;

    @SuppressWarnings("unchecked")
    private DataOperator<WarpData> warpOperator = mock(DataOperator.class);

    @SuppressWarnings("unchecked")
    private Query<WarpData> query = mock(Query.class);

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();
        teleportService = mock(TeleportService.class);

        warpService = new WarpService();
        EssentialsTestHelper.setField(warpService, "config", config);
        EssentialsTestHelper.setField(warpService, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(warpService, "warpOperator", warpOperator);
        EssentialsTestHelper.setField(warpService, "teleportService", teleportService);

        reset(warpOperator, query);
        when(warpOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("getAllWarps")
    class GetAllWarpsTests {

        @Test
        @DisplayName("Should return all warps")
        void shouldReturnAllWarps() {
            List<WarpData> warps = Arrays.asList(
                    WarpData.builder().name("spawn").build(),
                    WarpData.builder().name("shop").build()
            );
            when(warpOperator.getAll()).thenReturn(warps);

            List<WarpData> result = warpService.getAllWarps();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getAccessibleWarps")
    class GetAccessibleWarpsTests {

        @Test
        @DisplayName("Should return warps without permission requirement")
        void shouldReturnPublicWarps() {
            WarpData publicWarp = WarpData.builder().name("spawn").build();
            WarpData privateWarp = WarpData.builder().name("vip").permission("vip.warp").build();
            when(warpOperator.getAll()).thenReturn(Arrays.asList(publicWarp, privateWarp));

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            List<WarpData> result = warpService.getAccessibleWarps(player);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("spawn");
        }

        @Test
        @DisplayName("Should return all warps for player with permission")
        void shouldReturnAllForPermittedPlayer() {
            WarpData publicWarp = WarpData.builder().name("spawn").build();
            WarpData privateWarp = WarpData.builder().name("vip").permission("vip.warp").build();
            when(warpOperator.getAll()).thenReturn(Arrays.asList(publicWarp, privateWarp));

            Player player = EssentialsTestHelper.createMockPlayer("VIP", UUID.randomUUID());
            when(player.hasPermission("vip.warp")).thenReturn(true);

            List<WarpData> result = warpService.getAccessibleWarps(player);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("canAccess")
    class CanAccessTests {

        @Test
        @DisplayName("Should return true for warp without permission")
        void shouldReturnTrueForPublic() {
            WarpData warp = WarpData.builder().name("spawn").build();
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            assertThat(warpService.canAccess(player, warp)).isTrue();
        }

        @Test
        @DisplayName("Should return true for warp with empty permission")
        void shouldReturnTrueForEmptyPermission() {
            WarpData warp = WarpData.builder().name("spawn").permission("").build();
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            assertThat(warpService.canAccess(player, warp)).isTrue();
        }

        @Test
        @DisplayName("Should return false when player lacks permission")
        void shouldReturnFalseWithoutPermission() {
            WarpData warp = WarpData.builder().name("vip").permission("vip.warp").build();
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            assertThat(warpService.canAccess(player, warp)).isFalse();
        }

        @Test
        @DisplayName("Should return true when player has permission")
        void shouldReturnTrueWithPermission() {
            WarpData warp = WarpData.builder().name("vip").permission("vip.warp").build();
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(player.hasPermission("vip.warp")).thenReturn(true);

            assertThat(warpService.canAccess(player, warp)).isTrue();
        }
    }

    @Nested
    @DisplayName("getWarp")
    class GetWarpTests {

        @Test
        @DisplayName("Should return warp by name")
        void shouldReturnWarp() {
            WarpData warp = WarpData.builder().name("spawn").build();
            when(query.first()).thenReturn(warp);

            WarpData result = warpService.getWarp("spawn");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return null when not found")
        void shouldReturnNull() {
            when(query.first()).thenReturn(null);

            WarpData result = warpService.getWarp("nonexistent");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("createWarp")
    class CreateWarpTests {

        @Test
        @DisplayName("Should create warp successfully")
        void shouldCreateWarp() {
            when(query.first()).thenReturn(null);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 0, 64, 0);

            WarpService.WarpResult result = warpService.createWarp("spawn", loc, UUID.randomUUID(), null);

            assertThat(result).isEqualTo(WarpService.WarpResult.CREATED);
            verify(warpOperator).insert(any(WarpData.class));
        }

        @Test
        @DisplayName("Should create warp with permission")
        void shouldCreateWarpWithPermission() {
            when(query.first()).thenReturn(null);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 0, 64, 0);

            WarpService.WarpResult result = warpService.createWarp("vip", loc, UUID.randomUUID(), "vip.warp");

            assertThat(result).isEqualTo(WarpService.WarpResult.CREATED);
        }

        @Test
        @DisplayName("Should return ALREADY_EXISTS when warp exists")
        void shouldReturnAlreadyExists() {
            when(query.first()).thenReturn(WarpData.builder().name("spawn").build());
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 0, 64, 0);

            WarpService.WarpResult result = warpService.createWarp("spawn", loc, UUID.randomUUID(), null);

            assertThat(result).isEqualTo(WarpService.WarpResult.ALREADY_EXISTS);
        }

        @Test
        @DisplayName("Should return INVALID_NAME for empty name")
        void shouldReturnInvalidNameEmpty() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 0, 64, 0);

            WarpService.WarpResult result = warpService.createWarp("   ", loc, UUID.randomUUID(), null);

            assertThat(result).isEqualTo(WarpService.WarpResult.INVALID_NAME);
        }

        @Test
        @DisplayName("Should return INVALID_NAME for name longer than 32")
        void shouldReturnInvalidNameTooLong() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 0, 64, 0);

            WarpService.WarpResult result = warpService.createWarp("a".repeat(33), loc, UUID.randomUUID(), null);

            assertThat(result).isEqualTo(WarpService.WarpResult.INVALID_NAME);
        }

        @Test
        @DisplayName("Should return DISABLED when warp is disabled")
        void shouldReturnDisabled() {
            config.setWarpEnabled(false);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 0, 64, 0);

            WarpService.WarpResult result = warpService.createWarp("spawn", loc, UUID.randomUUID(), null);

            assertThat(result).isEqualTo(WarpService.WarpResult.DISABLED);
        }
    }

    @Nested
    @DisplayName("deleteWarp")
    class DeleteWarpTests {

        @Test
        @DisplayName("Should delete warp successfully")
        void shouldDeleteWarp() {
            UUID warpId = UUID.randomUUID();
            WarpData warp = WarpData.builder().uuid(warpId).name("spawn").build();
            when(query.first()).thenReturn(warp);

            boolean result = warpService.deleteWarp("spawn");

            assertThat(result).isTrue();
            verify(warpOperator).delById(warpId);
        }

        @Test
        @DisplayName("Should return false when warp not found")
        void shouldReturnFalse() {
            when(query.first()).thenReturn(null);

            boolean result = warpService.deleteWarp("nonexistent");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("teleportToWarp")
    class TeleportToWarpTests {

        @Test
        @DisplayName("Should return DISABLED when disabled")
        void shouldReturnDisabled() {
            config.setWarpEnabled(false);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            TeleportResult result = warpService.teleportToWarp(player, "spawn");

            assertThat(result).isEqualTo(TeleportResult.DISABLED);
        }

        @Test
        @DisplayName("Should return ALREADY_TELEPORTING when in warmup")
        void shouldReturnAlreadyTeleporting() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(teleportService.isTeleporting(player.getUniqueId())).thenReturn(true);

            TeleportResult result = warpService.teleportToWarp(player, "spawn");

            assertThat(result).isEqualTo(TeleportResult.ALREADY_TELEPORTING);
        }

        @Test
        @DisplayName("Should return NOT_FOUND when warp does not exist")
        void shouldReturnNotFound() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(query.first()).thenReturn(null);

            TeleportResult result = warpService.teleportToWarp(player, "nonexistent");

            assertThat(result).isEqualTo(TeleportResult.NOT_FOUND);
        }

        @Test
        @DisplayName("Should return NO_PERMISSION when player lacks access")
        void shouldReturnNoPermission() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            WarpData warp = WarpData.builder().name("vip").permission("vip.warp").build();
            when(query.first()).thenReturn(warp);

            TeleportResult result = warpService.teleportToWarp(player, "vip");

            assertThat(result).isEqualTo(TeleportResult.NO_PERMISSION);
        }
    }

    @Nested
    @DisplayName("cancelTeleport and isTeleporting")
    class TeleportStateTests {

        @Test
        @DisplayName("Should delegate cancelTeleport to TeleportService")
        void shouldDelegateCancelTeleport() {
            UUID uuid = UUID.randomUUID();
            warpService.cancelTeleport(uuid);
            verify(teleportService).cancelTeleport(uuid);
        }

        @Test
        @DisplayName("Should delegate isTeleporting to TeleportService")
        void shouldDelegateIsTeleporting() {
            UUID uuid = UUID.randomUUID();
            when(teleportService.isTeleporting(uuid)).thenReturn(true);

            assertThat(warpService.isTeleporting(uuid)).isTrue();
        }
    }

    @Nested
    @DisplayName("WarpResult enum")
    class WarpResultTests {

        @Test
        @DisplayName("Should have all values")
        void shouldHaveAllValues() {
            assertThat(WarpService.WarpResult.values()).hasSize(4);
        }
    }
}
