package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
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
 * Tests the two {@code teleportToHome} directions {@code HomeServiceMockitoTest} never reaches:
 * a home that exists but whose world is no longer registered (WORLD_NOT_FOUND), and a home that
 * resolves successfully -- which is also the only place the warmup-skip permission decision is
 * exercised.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("HomeService teleportToHome world-resolution Tests")
class HomeServiceTeleportBehaviorTest {

    private HomeService homeService;
    private EssentialsConfig config;
    private TeleportService teleportService;

    @SuppressWarnings("unchecked")
    private final DataOperator<HomeData> homeOperator = mock(DataOperator.class);

    @SuppressWarnings("unchecked")
    private final Query<HomeData> query = mock(Query.class);

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();
        teleportService = mock(TeleportService.class);

        homeService = new HomeService();
        EssentialsTestHelper.setField(homeService, "config", config);
        EssentialsTestHelper.setField(homeService, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(homeService, "homeOperator", homeOperator);
        EssentialsTestHelper.setField(homeService, "teleportService", teleportService);

        reset(homeOperator, query);
        when(homeOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private HomeData homeIn(String worldName) {
        return HomeData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(UUID.randomUUID().toString())
                .name("home")
                .world(worldName)
                .x(1).y(64).z(1)
                .yaw(0).pitch(0)
                .build();
    }

    @Test
    @DisplayName("A home whose world is no longer registered on the server resolves to WORLD_NOT_FOUND")
    void unregisteredWorldResolvesToWorldNotFound() {
        when(query.first()).thenReturn(homeIn("deleted_world"));
        Server server = Bukkit.getServer();
        when(server.getWorld("deleted_world")).thenReturn(null);

        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

        TeleportResult result = homeService.teleportToHome(player, "home");

        assertThat(result).isEqualTo(TeleportResult.WORLD_NOT_FOUND);
        verify(teleportService, never()).teleport(any(), any(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("A home with a live world delegates to TeleportService with the full configured warmup")
    void resolvedHomeDelegatesWithFullWarmup() {
        when(query.first()).thenReturn(homeIn("world"));
        World world = EssentialsTestHelper.createMockWorld("world");
        Server server = Bukkit.getServer();
        when(server.getWorld("world")).thenReturn(world);
        config.setHomeTeleportWarmup(10);

        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.hasPermission("ultiessentials.home.nowarmup")).thenReturn(false);
        when(teleportService.teleport(eq(player), any(Location.class), eq(10), anyBoolean()))
                .thenReturn(TeleportResult.WARMUP_STARTED);

        TeleportResult result = homeService.teleportToHome(player, "home");

        assertThat(result).isEqualTo(TeleportResult.WARMUP_STARTED);
        verify(teleportService).teleport(eq(player), any(Location.class), eq(10), anyBoolean());
    }

    @Test
    @DisplayName("The no-warmup permission skips the configured warmup entirely")
    void noWarmupPermissionSkipsConfiguredWarmup() {
        when(query.first()).thenReturn(homeIn("world"));
        World world = EssentialsTestHelper.createMockWorld("world");
        Server server = Bukkit.getServer();
        when(server.getWorld("world")).thenReturn(world);
        config.setHomeTeleportWarmup(10);

        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.hasPermission("ultiessentials.home.nowarmup")).thenReturn(true);
        when(teleportService.teleport(eq(player), any(Location.class), eq(0), anyBoolean()))
                .thenReturn(TeleportResult.SUCCESS);

        TeleportResult result = homeService.teleportToHome(player, "home");

        assertThat(result).isEqualTo(TeleportResult.SUCCESS);
        verify(teleportService).teleport(eq(player), any(Location.class), eq(0), anyBoolean());
    }
}
