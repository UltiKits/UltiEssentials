package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Ban Commands Tests")
class BanCommandsTest {

    private BanService banService;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        banService = mock(BanService.class);
        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("BanCommand")
    class BanCommandTests {

        private BanCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new BanCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "banService", banService);
        }

        @Test
        @DisplayName("Should ban player with default reason")
        void shouldBanWithDefaultReason() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    eq(playerUuid), eq("TestPlayer"))).thenReturn(BanService.BanResult.SUCCESS);

            command.ban(player, "BadPlayer");

            verify(banService).banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    eq(playerUuid), eq("TestPlayer"));
        }

        @Test
        @DisplayName("Should ban player with reason - success")
        void shouldBanWithReasonSuccess() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), eq("hacking"),
                    eq(playerUuid), eq("TestPlayer"))).thenReturn(BanService.BanResult.SUCCESS);

            command.banWithReason(player, "BadPlayer", "hacking");

            verify(banService).banPlayer(eq(targetUuid), eq("BadPlayer"), eq("hacking"),
                    eq(playerUuid), eq("TestPlayer"));
        }

        @Test
        @DisplayName("Should handle already banned")
        void shouldHandleAlreadyBanned() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    eq(playerUuid), eq("TestPlayer"))).thenReturn(BanService.BanResult.ALREADY_BANNED);

            command.banWithReason(player, "BadPlayer", "reason");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled ban feature")
        void shouldHandleDisabledBan() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    eq(playerUuid), eq("TestPlayer"))).thenReturn(BanService.BanResult.DISABLED);

            command.banWithReason(player, "BadPlayer", "reason");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should ban from console sender")
        void shouldBanFromConsole() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            CommandSender consoleSender = mock(CommandSender.class);
            when(consoleSender.getName()).thenReturn("CONSOLE");

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    isNull(), eq("Console"))).thenReturn(BanService.BanResult.SUCCESS);

            command.banWithReason(consoleSender, "BadPlayer", "hacking");

            verify(banService).banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    isNull(), eq("Console"));
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(1)).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("BanListCommand")
    class BanListCommandTests {

        private BanListCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new BanListCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "banService", banService);
        }

        @Test
        @DisplayName("Should show empty ban list")
        void shouldShowEmptyBanList() {
            when(banService.getActiveBans()).thenReturn(Collections.emptyList());

            command.banlist(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show ban list with entries")
        void shouldShowBanListWithEntries() {
            List<BanData> bans = Arrays.asList(
                BanData.builder()
                    .uuid(UUID.randomUUID())
                    .playerName("Player1")
                    .reason("cheating")
                    .bannedByName("Admin")
                    .banTime(System.currentTimeMillis())
                    .expireTime(-1) // permanent
                    .active(true)
                    .build(),
                BanData.builder()
                    .uuid(UUID.randomUUID())
                    .playerName("Player2")
                    .reason("griefing")
                    .bannedByName("Mod")
                    .banTime(System.currentTimeMillis())
                    .expireTime(System.currentTimeMillis() + 3600000) // 1 hour
                    .active(true)
                    .build()
            );
            when(banService.getActiveBans()).thenReturn(bans);

            command.banlist(player);

            // header + 2 players (each has 3 lines) + footer
            verify(player, atLeast(4)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show specific page")
        void shouldShowSpecificPage() {
            when(banService.getActiveBans()).thenReturn(Collections.emptyList());

            command.banlistPage(player, 2);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should clamp page to minimum 1")
        void shouldClampPageToMin() {
            when(banService.getActiveBans()).thenReturn(Collections.emptyList());

            command.banlistPage(player, -5);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(1)).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("TempBanCommand")
    class TempBanCommandTests {

        private TempBanCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new TempBanCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "banService", banService);
        }

        @Test
        @DisplayName("Should temp ban player with default reason")
        void shouldTempBanWithDefaultReason() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    eq(playerUuid), eq("TestPlayer"), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.SUCCESS);

            command.tempban(player, "BadPlayer", "1d");

            verify(banService).banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    eq(playerUuid), eq("TestPlayer"), anyLong(), isNull());
        }

        @Test
        @DisplayName("Should temp ban with reason - success")
        void shouldTempBanWithReasonSuccess() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), eq("hacking"),
                    eq(playerUuid), eq("TestPlayer"), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.SUCCESS);

            command.tempbanWithReason(player, "BadPlayer", "1h", "hacking");

            verify(banService).banPlayer(eq(targetUuid), eq("BadPlayer"), eq("hacking"),
                    eq(playerUuid), eq("TestPlayer"), anyLong(), isNull());
        }

        @Test
        @DisplayName("Should reject invalid duration")
        void shouldRejectInvalidDuration() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            command.tempbanWithReason(player, "BadPlayer", "invalid", "reason");

            verify(banService, never()).banPlayer(any(), anyString(), anyString(),
                    any(), anyString(), anyLong(), any());
            verify(player, atLeast(1)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle already banned")
        void shouldHandleAlreadyBanned() {
            UUID targetUuid = UUID.randomUUID();
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(targetUuid);
            when(target.hasPlayedBefore()).thenReturn(true);
            when(target.getName()).thenReturn("BadPlayer");

            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("BadPlayer"))).thenReturn(target);

            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(),
                    eq(playerUuid), eq("TestPlayer"), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.ALREADY_BANNED);

            command.tempbanWithReason(player, "BadPlayer", "1d", "reason");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(2)).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("UnbanCommand")
    class UnbanCommandTests {

        private UnbanCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new UnbanCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "banService", banService);
        }

        @Test
        @DisplayName("Should unban player successfully")
        void shouldUnbanSuccess() {
            when(banService.unbanPlayerByName("BadPlayer")).thenReturn(true);

            command.unban(player, "BadPlayer");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send message when player not banned")
        void shouldSendMessageWhenNotBanned() {
            when(banService.unbanPlayerByName("GoodPlayer")).thenReturn(false);

            command.unban(player, "GoodPlayer");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(1)).sendMessage(anyString());
        }
    }
}
