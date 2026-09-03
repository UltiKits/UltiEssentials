package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the {@code teleportToWarp} directions {@code WarpServiceMockitoTest} never reaches: an
 * accessible warp (no permission node) whose world is no longer registered, and a fully-resolved
 * warp -- the only place the warmup-skip permission decision is exercised.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("WarpService teleportToWarp world-resolution Tests")
class WarpServiceTeleportBehaviorTest {

    private WarpService warpService;
    private EssentialsConfig config;
    private TeleportService teleportService;

    @SuppressWarnings("unchecked")
    private final DataOperator<WarpData> warpOperator = mock(DataOperator.class);

    @SuppressWarnings("unchecked")
    private final Query<WarpData> query = mock(Query.class);

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

    private WarpData openWarpIn(String worldName) {
        return WarpData.builder()
                .name("spawn")
                .world(worldName)
                .x(1).y(64).z(1)
                .yaw(0).pitch(0)
                .build();
    }

    @Test
    @DisplayName("An accessible warp whose world is no longer registered resolves to WORLD_NOT_FOUND")
    void unregisteredWorldResolvesToWorldNotFound() {
        when(query.first()).thenReturn(openWarpIn("deleted_world"));
        Server server = Bukkit.getServer();
        when(server.getWorld("deleted_world")).thenReturn(null);

        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

        TeleportResult result = warpService.teleportToWarp(player, "spawn");

        assertThat(result).isEqualTo(TeleportResult.WORLD_NOT_FOUND);
        verify(teleportService, never()).teleport(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("A resolved warp delegates to TeleportService with the configured warmup")
    void resolvedWarpDelegatesWithFullWarmup() {
        when(query.first()).thenReturn(openWarpIn("world"));
        World world = EssentialsTestHelper.createMockWorld("world");
        Server server = Bukkit.getServer();
        when(server.getWorld("world")).thenReturn(world);
        config.setWarpTeleportWarmup(5);

        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.hasPermission("ultiessentials.warp.nowarmup")).thenReturn(false);
        when(teleportService.teleport(eq(player), any(Location.class), eq(5), anyBoolean()))
                .thenReturn(TeleportResult.WARMUP_STARTED);

        TeleportResult result = warpService.teleportToWarp(player, "spawn");

        assertThat(result).isEqualTo(TeleportResult.WARMUP_STARTED);
        verify(teleportService).teleport(eq(player), any(Location.class), eq(5), anyBoolean());
    }

    @Test
    @DisplayName("The no-warmup permission skips the configured warmup entirely")
    void noWarmupPermissionSkipsConfiguredWarmup() {
        when(query.first()).thenReturn(openWarpIn("world"));
        World world = EssentialsTestHelper.createMockWorld("world");
        Server server = Bukkit.getServer();
        when(server.getWorld("world")).thenReturn(world);
        config.setWarpTeleportWarmup(5);

        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.hasPermission("ultiessentials.warp.nowarmup")).thenReturn(true);
        when(teleportService.teleport(eq(player), any(Location.class), eq(0), anyBoolean()))
                .thenReturn(TeleportResult.SUCCESS);

        TeleportResult result = warpService.teleportToWarp(player, "spawn");

        assertThat(result).isEqualTo(TeleportResult.SUCCESS);
        verify(teleportService).teleport(eq(player), any(Location.class), eq(0), anyBoolean());
    }
}
