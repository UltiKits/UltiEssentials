package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("WhitelistCommand Tests")
class WhitelistCommandTest {

    private WhitelistCommand command;
    private EssentialsConfig config;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        command = new WhitelistCommand(config);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("add subcommand")
    class AddTests {

        @Test
        @DisplayName("Should add player to whitelist")
        void shouldAddPlayer() {
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getName()).thenReturn("NewPlayer");

            command.add(player, target);

            verify(target).setWhitelisted(true);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle null player")
        void shouldHandleNullPlayer() {
            command.add(player, null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setWhitelistEnabled(false);

            OfflinePlayer target = mock(OfflinePlayer.class);
            command.add(player, target);

            verify(target, never()).setWhitelisted(anyBoolean());
        }
    }

    @Nested
    @DisplayName("remove subcommand")
    class RemoveTests {

        @Test
        @DisplayName("Should remove player from whitelist")
        void shouldRemovePlayer() {
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getName()).thenReturn("OldPlayer");

            command.remove(player, target);

            verify(target).setWhitelisted(false);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle null player")
        void shouldHandleNullPlayer() {
            command.remove(player, null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setWhitelistEnabled(false);

            OfflinePlayer target = mock(OfflinePlayer.class);
            command.remove(player, target);

            verify(target, never()).setWhitelisted(anyBoolean());
        }
    }

    @Nested
    @DisplayName("list subcommand")
    class ListTests {

        @Test
        @DisplayName("Should list whitelisted players")
        @SuppressWarnings("unchecked")
        void shouldListWhitelistedPlayers() {
            OfflinePlayer p1 = mock(OfflinePlayer.class);
            when(p1.getName()).thenReturn("Player1");
            OfflinePlayer p2 = mock(OfflinePlayer.class);
            when(p2.getName()).thenReturn("Player2");

            Set<OfflinePlayer> whitelisted = new HashSet<>(Arrays.asList(p1, p2));

            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(whitelisted);

            command.list(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show empty whitelist")
        void shouldShowEmptyWhitelist() {
            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(Collections.emptySet());

            command.list(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setWhitelistEnabled(false);

            command.list(player);

            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("on/off subcommands")
    class EnableDisableTests {

        @Test
        @DisplayName("Should enable whitelist")
        void shouldEnableWhitelist() {
            Server server = Bukkit.getServer();

            command.enable(player);

            verify(server).setWhitelist(true);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should disable whitelist")
        void shouldDisableWhitelist() {
            Server server = Bukkit.getServer();

            command.disable(player);

            verify(server).setWhitelist(false);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature for enable")
        void shouldHandleDisabledForEnable() {
            config.setWhitelistEnabled(false);
            Server server = Bukkit.getServer();

            command.enable(player);

            verify(server, never()).setWhitelist(anyBoolean());
        }

        @Test
        @DisplayName("Should handle disabled feature for disable")
        void shouldHandleDisabledForDisable() {
            config.setWhitelistEnabled(false);
            Server server = Bukkit.getServer();

            command.disable(player);

            verify(server, never()).setWhitelist(anyBoolean());
        }
    }

    @Nested
    @DisplayName("status subcommand")
    class StatusTests {

        @Test
        @DisplayName("Should show whitelist status")
        void shouldShowStatus() {
            Server server = Bukkit.getServer();
            when(server.hasWhitelist()).thenReturn(true);
            when(server.getWhitelistedPlayers()).thenReturn(Collections.emptySet());

            command.status(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setWhitelistEnabled(false);

            command.status(player);

            verify(player).sendMessage(anyString());
        }
    }

    @Test
    @DisplayName("handleHelp should send usage messages")
    void handleHelpShouldSendUsage() {
        command.handleHelp(player);
        verify(player, atLeast(4)).sendMessage(anyString());
    }
}
