package com.ultikits.plugins.essentials.config;

import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Location Config Tests")
class LocationConfigsTest {

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("SpawnConfig")
    class SpawnConfigTests {

        @Test
        @DisplayName("Should have default values")
        void shouldHaveDefaultValues() {
            SpawnConfig config = new SpawnConfig();

            assertThat(config.getWorld()).isEqualTo("world");
            assertThat(config.getX()).isEqualTo(0.0);
            assertThat(config.getY()).isEqualTo(64.0);
            assertThat(config.getZ()).isEqualTo(0.0);
            assertThat(config.getYaw()).isEqualTo(0.0);
            assertThat(config.getPitch()).isEqualTo(0.0);
            assertThat(config.isTeleportOnFirstJoin()).isTrue();
            assertThat(config.isTeleportOnRespawn()).isTrue();
        }

        @Test
        @DisplayName("Should get spawn location")
        void shouldGetSpawnLocation() {
            SpawnConfig config = new SpawnConfig();
            World world = EssentialsTestHelper.createMockWorld("world");

            Server server = Bukkit.getServer();
            when(server.getWorld("world")).thenReturn(world);

            Location loc = config.getSpawnLocation();

            assertThat(loc).isNotNull();
            assertThat(loc.getWorld()).isEqualTo(world);
            assertThat(loc.getX()).isEqualTo(0.0);
            assertThat(loc.getY()).isEqualTo(64.0);
            assertThat(loc.getZ()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should set spawn location from Bukkit Location")
        void shouldSetSpawnLocation() {
            SpawnConfig config = new SpawnConfig();
            World world = EssentialsTestHelper.createMockWorld("custom_world");
            Location loc = new Location(world, 100.5, 70.0, -200.5, 45.0f, 30.0f);

            config.setSpawnLocation(loc);

            assertThat(config.getWorld()).isEqualTo("custom_world");
            assertThat(config.getX()).isEqualTo(100.5);
            assertThat(config.getY()).isEqualTo(70.0);
            assertThat(config.getZ()).isEqualTo(-200.5);
            assertThat(config.getYaw()).isEqualTo(45.0);
            assertThat(config.getPitch()).isEqualTo(30.0);
        }

        @Test
        @DisplayName("Should handle null world in setSpawnLocation")
        void shouldHandleNullWorldInSet() {
            SpawnConfig config = new SpawnConfig();
            Location loc = new Location(null, 100.5, 70.0, -200.5);

            config.setSpawnLocation(loc);

            // World should remain default since the new location has null world
            assertThat(config.getWorld()).isEqualTo("world");
            assertThat(config.getX()).isEqualTo(100.5);
        }

        @Test
        @DisplayName("Should set and get config options")
        void shouldSetAndGetConfigOptions() {
            SpawnConfig config = new SpawnConfig();

            config.setTeleportOnFirstJoin(false);
            config.setTeleportOnRespawn(false);

            assertThat(config.isTeleportOnFirstJoin()).isFalse();
            assertThat(config.isTeleportOnRespawn()).isFalse();
        }
    }

    @Nested
    @DisplayName("LobbyConfig")
    class LobbyConfigTests {

        @Test
        @DisplayName("Should have default values")
        void shouldHaveDefaultValues() {
            LobbyConfig config = new LobbyConfig();

            assertThat(config.getWorld()).isEqualTo("world");
            assertThat(config.getX()).isEqualTo(0.0);
            assertThat(config.getY()).isEqualTo(64.0);
            assertThat(config.getZ()).isEqualTo(0.0);
            assertThat(config.getYaw()).isEqualTo(0.0);
            assertThat(config.getPitch()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should get lobby location")
        void shouldGetLobbyLocation() {
            LobbyConfig config = new LobbyConfig();
            World world = EssentialsTestHelper.createMockWorld("world");

            Server server = Bukkit.getServer();
            when(server.getWorld("world")).thenReturn(world);

            Location loc = config.getLobbyLocation();

            assertThat(loc).isNotNull();
            assertThat(loc.getWorld()).isEqualTo(world);
        }

        @Test
        @DisplayName("Should set lobby location from Bukkit Location")
        void shouldSetLobbyLocation() {
            LobbyConfig config = new LobbyConfig();
            World world = EssentialsTestHelper.createMockWorld("hub_world");
            Location loc = new Location(world, 50.0, 80.0, -100.0, 90.0f, -10.0f);

            config.setLobbyLocation(loc);

            assertThat(config.getWorld()).isEqualTo("hub_world");
            assertThat(config.getX()).isEqualTo(50.0);
            assertThat(config.getY()).isEqualTo(80.0);
            assertThat(config.getZ()).isEqualTo(-100.0);
            assertThat(config.getYaw()).isEqualTo(90.0);
            assertThat(config.getPitch()).isEqualTo(-10.0);
        }

        @Test
        @DisplayName("Should handle null world in setLobbyLocation")
        void shouldHandleNullWorldInSet() {
            LobbyConfig config = new LobbyConfig();
            Location loc = new Location(null, 50.0, 80.0, -100.0);

            config.setLobbyLocation(loc);

            assertThat(config.getWorld()).isEqualTo("world");
            assertThat(config.getX()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("TabBarConfig")
    class TabBarConfigTests {

        @Test
        @DisplayName("Should have default values")
        void shouldHaveDefaultValues() {
            TabBarConfig config = new TabBarConfig();

            assertThat(config.getHeader()).isEqualTo("&6=== 服务器名称 ===");
            assertThat(config.getFooter()).isEqualTo("&7在线: &e%online%&7/&e%max%");
        }

        @Test
        @DisplayName("Should set and get header")
        void shouldSetAndGetHeader() {
            TabBarConfig config = new TabBarConfig();

            config.setHeader("&a=== My Server ===");

            assertThat(config.getHeader()).isEqualTo("&a=== My Server ===");
        }

        @Test
        @DisplayName("Should set and get footer")
        void shouldSetAndGetFooter() {
            TabBarConfig config = new TabBarConfig();

            config.setFooter("&7Players: %online%");

            assertThat(config.getFooter()).isEqualTo("&7Players: %online%");
        }
    }

    @Nested
    @DisplayName("MotdConfig")
    class MotdConfigTests {

        @Test
        @DisplayName("Should have default values")
        void shouldHaveDefaultValues() {
            MotdConfig config = new MotdConfig();

            assertThat(config.getLine1()).isEqualTo("&6Welcome to our server!");
            assertThat(config.getLine2()).isEqualTo("&7Powered by UltiTools");
            assertThat(config.getMaxPlayers()).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should set and get line1")
        void shouldSetAndGetLine1() {
            MotdConfig config = new MotdConfig();

            config.setLine1("Custom MOTD Line 1");

            assertThat(config.getLine1()).isEqualTo("Custom MOTD Line 1");
        }

        @Test
        @DisplayName("Should set and get line2")
        void shouldSetAndGetLine2() {
            MotdConfig config = new MotdConfig();

            config.setLine2("Custom MOTD Line 2");

            assertThat(config.getLine2()).isEqualTo("Custom MOTD Line 2");
        }

        @Test
        @DisplayName("Should set and get maxPlayers")
        void shouldSetAndGetMaxPlayers() {
            MotdConfig config = new MotdConfig();

            config.setMaxPlayers(100);

            assertThat(config.getMaxPlayers()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should accept -1 as use server default")
        void shouldAcceptNegativeOne() {
            MotdConfig config = new MotdConfig();

            config.setMaxPlayers(-1);

            assertThat(config.getMaxPlayers()).isEqualTo(-1);
        }
    }
}
