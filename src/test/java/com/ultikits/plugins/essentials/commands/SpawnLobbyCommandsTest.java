package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.LobbyConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Spawn & Lobby Command Tests")
class SpawnLobbyCommandsTest {

    private EssentialsConfig config;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("SpawnCommand")
    class SpawnCommandTests {

        private SpawnCommand command;
        private SpawnConfig spawnConfig;

        @BeforeEach
        void setUp() throws Exception {
            spawnConfig = mock(SpawnConfig.class);
            command = new SpawnCommand(config, spawnConfig);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should teleport to spawn")
        void shouldTeleportToSpawn() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location spawnLoc = new Location(world, 0, 64, 0);
            when(spawnConfig.getSpawnLocation()).thenReturn(spawnLoc);

            command.teleportToSpawn(player);

            verify(player).teleport(spawnLoc);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send error when world is null")
        void shouldSendErrorWhenWorldNull() {
            Location noWorldLoc = new Location(null, 0, 64, 0);
            when(spawnConfig.getSpawnLocation()).thenReturn(noWorldLoc);

            command.teleportToSpawn(player);

            verify(player, never()).teleport(any(Location.class));
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setSpawnEnabled(false);

            command.teleportToSpawn(player);

            verify(player, never()).teleport(any(Location.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("SetSpawnCommand")
    class SetSpawnCommandTests {

        private SetSpawnCommand command;
        private SpawnConfig spawnConfig;

        @BeforeEach
        void setUp() throws Exception {
            spawnConfig = mock(SpawnConfig.class);
            command = new SetSpawnCommand(config, spawnConfig);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should set spawn at player location")
        void shouldSetSpawn() throws IOException {
            command.setSpawn(player);

            verify(spawnConfig).setSpawnLocation(player.getLocation());
            verify(spawnConfig).save();
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send error when save fails")
        void shouldSendErrorWhenSaveFails() throws IOException {
            doThrow(new IOException("write error")).when(spawnConfig).save();

            command.setSpawn(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setSpawnEnabled(false);

            command.setSpawn(player);

            verify(spawnConfig, never()).setSpawnLocation(any(Location.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("LobbyCommand")
    class LobbyCommandTests {

        private LobbyCommand command;
        private LobbyConfig lobbyConfig;

        @BeforeEach
        void setUp() throws Exception {
            lobbyConfig = mock(LobbyConfig.class);
            command = new LobbyCommand(config, lobbyConfig);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should teleport to lobby")
        void shouldTeleportToLobby() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location lobbyLoc = new Location(world, 0, 64, 0);
            when(lobbyConfig.getLobbyLocation()).thenReturn(lobbyLoc);

            command.teleportToLobby(player);

            verify(player).teleport(lobbyLoc);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send error when world is null")
        void shouldSendErrorWhenWorldNull() {
            Location noWorldLoc = new Location(null, 0, 64, 0);
            when(lobbyConfig.getLobbyLocation()).thenReturn(noWorldLoc);

            command.teleportToLobby(player);

            verify(player, never()).teleport(any(Location.class));
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setLobbyEnabled(false);

            command.teleportToLobby(player);

            verify(player, never()).teleport(any(Location.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("SetLobbyCommand")
    class SetLobbyCommandTests {

        private SetLobbyCommand command;
        private LobbyConfig lobbyConfig;

        @BeforeEach
        void setUp() throws Exception {
            lobbyConfig = mock(LobbyConfig.class);
            command = new SetLobbyCommand(config, lobbyConfig);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should set lobby at player location")
        void shouldSetLobby() throws IOException {
            command.setLobby(player);

            verify(lobbyConfig).setLobbyLocation(player.getLocation());
            verify(lobbyConfig).save();
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send error when save fails")
        void shouldSendErrorWhenSaveFails() throws IOException {
            doThrow(new IOException("write error")).when(lobbyConfig).save();

            command.setLobby(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setLobbyEnabled(false);

            command.setLobby(player);

            verify(lobbyConfig, never()).setLobbyLocation(any(Location.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player).sendMessage(anyString());
        }
    }
}
