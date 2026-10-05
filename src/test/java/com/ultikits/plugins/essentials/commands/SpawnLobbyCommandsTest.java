package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.LobbyConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.config.ConfigWriteRefusedException;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

        /**
         * The operator's command names exactly the six location settings, so only they are written
         * (maintainer decision 2026-10-04, "what code may write, by file type"; UltiKits/UltiEssentials#72).
         * Before, the command saved the whole entity.
         */
        @Test
        @DisplayName("Should set spawn at player location, writing exactly the six location settings")
        void shouldSetSpawn() throws IOException {
            command.setSpawn(player);

            verify(spawnConfig).setSpawnLocation(player.getLocation());
            verify(spawnConfig).saveOperatorChange("spawn.location.world", "spawn.location.x", "spawn.location.y",
                    "spawn.location.z", "spawn.location.yaw", "spawn.location.pitch");
            verify(spawnConfig, never()).save();
            verify(player).sendMessage(CatalogueText.text("zh", "essentials.spawn.set"));
        }

        @Test
        @DisplayName("Should send error when save fails")
        void shouldSendErrorWhenSaveFails() throws IOException {
            doThrow(new IOException("write error")).when(spawnConfig).saveOperatorChange(any(String[].class));

            command.setSpawn(player);

            verify(player).sendMessage(CatalogueText.text("zh", "essentials.spawn.save_failed"));
        }

        /** Maintainer decision 2026-10-05: a refused operator change says "not saved" and why, and is rolled back. */
        /**
         * A write that fails for any other reason also puts back the six settings the configuration held before
         * the command, and logs the reason once, so the reply's pointer to the server log holds (gate-1 top-up
         * IN-01/IN-02 of plan 17-72, UltiKits/UltiEssentials#72).
         */
        @Test
        @DisplayName("A failed write restores the six settings held before the command and logs the reason")
        void failedWriteRestoresThePreviousSettingsAndLogsTheReason() throws IOException {
            when(spawnConfig.getWorld()).thenReturn("world_before");
            when(spawnConfig.getX()).thenReturn(1.5);
            when(spawnConfig.getY()).thenReturn(70.0);
            when(spawnConfig.getZ()).thenReturn(-2.5);
            when(spawnConfig.getYaw()).thenReturn(90.0);
            when(spawnConfig.getPitch()).thenReturn(10.0);
            doThrow(new IOException("write error")).when(spawnConfig).saveOperatorChange(any(String[].class));

            command.setSpawn(player);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(spawnConfig);
            order.verify(spawnConfig).setSpawnLocation(player.getLocation());
            order.verify(spawnConfig).setWorld("world_before");
            verify(spawnConfig).setX(1.5);
            verify(spawnConfig).setY(70.0);
            verify(spawnConfig).setZ(-2.5);
            verify(spawnConfig).setYaw(90.0);
            verify(spawnConfig).setPitch(10.0);
            verify(EssentialsTestHelper.getMockLogger()).warn(contains("write error"));
        }

        @Test
        @DisplayName("A write the framework refuses tells the player the spawn was not saved, and why")
        void refusedWriteSaysNotSavedAndWhy() throws IOException {
            doThrow(new ConfigWriteRefusedException("config/spawn.yml", "the file uses YAML anchors, aliases or merge keys"))
                    .when(spawnConfig).saveOperatorChange(any(String[].class));

            command.setSpawn(player);

            org.mockito.ArgumentCaptor<String> reply = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(reply.capture());
            assertThat(reply.getValue()).isEqualTo(String.format(CatalogueText.text("zh", "essentials.spawn.not_saved"),
                    "the file uses YAML anchors, aliases or merge keys"));
            verify(EssentialsTestHelper.getMockLogger()).warn(contains("the file uses YAML anchors, aliases or merge keys"));
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

        /** As /setspawn: exactly the six location settings the command names (UltiKits/UltiEssentials#72). */
        @Test
        @DisplayName("Should set lobby at player location, writing exactly the six location settings")
        void shouldSetLobby() throws IOException {
            command.setLobby(player);

            verify(lobbyConfig).setLobbyLocation(player.getLocation());
            verify(lobbyConfig).saveOperatorChange("lobby.location.world", "lobby.location.x", "lobby.location.y",
                    "lobby.location.z", "lobby.location.yaw", "lobby.location.pitch");
            verify(lobbyConfig, never()).save();
            verify(player).sendMessage(CatalogueText.text("zh", "essentials.lobby.set"));
        }

        @Test
        @DisplayName("Should send error when save fails")
        void shouldSendErrorWhenSaveFails() throws IOException {
            doThrow(new IOException("write error")).when(lobbyConfig).saveOperatorChange(any(String[].class));

            command.setLobby(player);

            verify(player).sendMessage(CatalogueText.text("zh", "essentials.lobby.save_failed"));
        }

        /**
         * A write that fails for any other reason also puts back the six settings the configuration held before
         * the command, and logs the reason once, so the reply's pointer to the server log holds (gate-1 top-up
         * IN-01/IN-02 of plan 17-72, UltiKits/UltiEssentials#72).
         */
        @Test
        @DisplayName("A failed write restores the six settings held before the command and logs the reason")
        void failedWriteRestoresThePreviousSettingsAndLogsTheReason() throws IOException {
            when(lobbyConfig.getWorld()).thenReturn("world_before");
            when(lobbyConfig.getX()).thenReturn(1.5);
            when(lobbyConfig.getY()).thenReturn(70.0);
            when(lobbyConfig.getZ()).thenReturn(-2.5);
            when(lobbyConfig.getYaw()).thenReturn(90.0);
            when(lobbyConfig.getPitch()).thenReturn(10.0);
            doThrow(new IOException("write error")).when(lobbyConfig).saveOperatorChange(any(String[].class));

            command.setLobby(player);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(lobbyConfig);
            order.verify(lobbyConfig).setLobbyLocation(player.getLocation());
            order.verify(lobbyConfig).setWorld("world_before");
            verify(lobbyConfig).setX(1.5);
            verify(lobbyConfig).setY(70.0);
            verify(lobbyConfig).setZ(-2.5);
            verify(lobbyConfig).setYaw(90.0);
            verify(lobbyConfig).setPitch(10.0);
            verify(EssentialsTestHelper.getMockLogger()).warn(contains("write error"));
        }

        @Test
        @DisplayName("A write the framework refuses tells the player the lobby was not saved, and why")
        void refusedWriteSaysNotSavedAndWhy() throws IOException {
            doThrow(new ConfigWriteRefusedException("config/lobby.yml", "the file cannot be read or parsed"))
                    .when(lobbyConfig).saveOperatorChange(any(String[].class));

            command.setLobby(player);

            org.mockito.ArgumentCaptor<String> reply = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(reply.capture());
            assertThat(reply.getValue()).isEqualTo(String.format(CatalogueText.text("zh", "essentials.lobby.not_saved"),
                    "the file cannot be read or parsed"));
            verify(EssentialsTestHelper.getMockLogger()).warn(contains("the file cannot be read or parsed"));
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
