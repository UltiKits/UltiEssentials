package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
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
            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer("NewPlayer")).thenReturn(target);

            command.add(player, "NewPlayer");

            verify(target).setWhitelisted(true);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle unresolved player")
        void shouldHandleUnresolvedPlayer() {
            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer("Unknown")).thenReturn(null);

            command.add(player, "Unknown");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setWhitelistEnabled(false);

            OfflinePlayer target = mock(OfflinePlayer.class);
            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer("SomePlayer")).thenReturn(target);

            command.add(player, "SomePlayer");

            verify(target, never()).setWhitelisted(anyBoolean());
        }

        @Test
        @DisplayName("aSixteenCharacterNameIsAccepted: the boundary is the platform's own username limit, measured on the argument string's own length")
        void aSixteenCharacterNameIsAccepted() {
            String name = "A234567890123456"; // exactly 16 chars, plain ASCII
            assertThat(name).hasSize(16);
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getName()).thenReturn(name);
            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(name)).thenReturn(target);

            command.add(player, name);

            verify(target).setWhitelisted(true);
        }

        @Test
        @DisplayName("aSeventeenCharacterNameIsRefusedWithAMessage: one character over the platform's limit produces a message and no exception, and never reaches offline-player resolution")
        void aSeventeenCharacterNameIsRefusedWithAMessage() {
            String name = "A2345678901234567"; // 17 chars, plain ASCII -- length, not encoding
            assertThat(name).hasSize(17);

            command.add(player, name);

            verify(player).sendMessage(anyString());
            Server server = Bukkit.getServer();
            verify(server, never()).getOfflinePlayer(anyString());
        }

        @Test
        @DisplayName("anEmptyOrBlankNameIsRefusedTheSameWay: an empty or whitespace-only name takes the same refusal path as an over-long one")
        void anEmptyOrBlankNameIsRefusedTheSameWay() {
            command.add(player, "   ");

            verify(player).sendMessage(anyString());
            Server server = Bukkit.getServer();
            verify(server, never()).getOfflinePlayer(anyString());
        }

        @Test
        @DisplayName("aNameWithIncidentalWhitespaceIsTrimmedBeforeResolution: leading/trailing whitespace is trimmed before both the length check and Bukkit.getOfflinePlayer are applied")
        void aNameWithIncidentalWhitespaceIsTrimmedBeforeResolution() {
            String padded = "  ABC  ";
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getName()).thenReturn("ABC");
            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer("ABC")).thenReturn(target);

            command.add(player, padded);

            verify(server).getOfflinePlayer("ABC");
            verify(server, never()).getOfflinePlayer(padded);
            verify(target).setWhitelisted(true);
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
            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(Collections.singleton(target));

            command.remove(player, "OldPlayer");

            verify(target).setWhitelisted(false);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle unresolved player")
        void shouldHandleUnresolvedPlayer() {
            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(Collections.emptySet());

            command.remove(player, "Unknown");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setWhitelistEnabled(false);

            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getName()).thenReturn("SomePlayer");
            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(Collections.singleton(target));

            command.remove(player, "SomePlayer");

            verify(target, never()).setWhitelisted(anyBoolean());
        }

        /**
         * Proves the review round-3 Codex finding on PR#22 (comment 3944574356): the round-2 fix
         * (commit 7698424) removed remove()'s length/blank guard on the premise that "remove has
         * no resolution step to protect" -- but the code it left behind still called
         * {@code Bukkit.getOfflinePlayer(playerName)} unconditionally, the exact platform resolver
         * UltiEssentials#17 measured crashing for a name over 16 characters on a real Paper server.
         * A name that is not already on the whitelist must never reach that resolver at all.
         */
        @Test
        @DisplayName("removeNeverAsksThePlatformResolverForANameNotAlreadyWhitelisted: an over-length name that is not already on the whitelist is reported as not found without ever calling Bukkit.getOfflinePlayer, the resolver #17 measured crashing on for names like this")
        void removeNeverAsksThePlatformResolverForANameNotAlreadyWhitelisted() {
            String tooLong = "A2345678901234567"; // 17 chars -- the exact shape #17 measured crashing
            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(Collections.emptySet());

            command.remove(player, tooLong);

            verify(server, never()).getOfflinePlayer(anyString());
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("removeStillRemovesAMalformedLegacyEntryAlreadyOnTheWhitelist: an over-length name that IS already on the whitelist (a manual whitelist.json edit, a historical offline-mode account, file corruption) is still removable, matched against the whitelist's own existing entries rather than resolved fresh")
        void removeStillRemovesAMalformedLegacyEntryAlreadyOnTheWhitelist() {
            String tooLong = "A2345678901234567"; // 17 chars, already present as a legacy entry
            OfflinePlayer legacyEntry = mock(OfflinePlayer.class);
            when(legacyEntry.getName()).thenReturn(tooLong);
            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(Collections.singleton(legacyEntry));

            command.remove(player, tooLong);

            verify(server, never()).getOfflinePlayer(anyString());
            verify(legacyEntry).setWhitelisted(false);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("removeStillRemovesABlankLegacyEntryAlreadyOnTheWhitelist: a blank name that IS already on the whitelist is still removable the same way")
        void removeStillRemovesABlankLegacyEntryAlreadyOnTheWhitelist() {
            String blank = "   ";
            OfflinePlayer legacyEntry = mock(OfflinePlayer.class);
            when(legacyEntry.getName()).thenReturn(blank);
            Server server = Bukkit.getServer();
            when(server.getWhitelistedPlayers()).thenReturn(Collections.singleton(legacyEntry));

            command.remove(player, blank);

            verify(server, never()).getOfflinePlayer(anyString());
            verify(legacyEntry).setWhitelisted(false);
            verify(player).sendMessage(anyString());
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
