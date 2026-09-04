package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TempBanCommand}, which has no prior test class at all: player-existence
 * validation, duration-format validation, the {@code BanResult} switch driving the broadcast,
 * and the tab-completion arg-count routing.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("TempBanCommand Tests")
class TempBanCommandTest {

    private TempBanCommand command;
    private BanService banService;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        command = new TempBanCommand();
        banService = mock(BanService.class);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(command, "banService", banService);

        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private OfflinePlayer stubExistingTarget(String name, UUID uuid) {
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(uuid);
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getName()).thenReturn(name);
        Server server = Bukkit.getServer();
        when(server.getOfflinePlayer(eq(name))).thenReturn(target);
        return target;
    }

    @Nested
    @DisplayName("Player-existence validation")
    class PlayerExistenceTests {

        @Test
        @DisplayName("A name with no UUID and no play history is rejected without calling the ban service")
        void nonexistentPlayerIsRejected() {
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(null);
            when(target.hasPlayedBefore()).thenReturn(false);
            Server server = Bukkit.getServer();
            when(server.getOfflinePlayer(eq("Nobody"))).thenReturn(target);

            command.tempbanWithReason(player, "Nobody", "1d", "reason");

            verify(player).sendMessage(contains("Nobody"));
            verifyNoInteractions(banService);
        }
    }

    @Nested
    @DisplayName("Duration-format validation")
    class DurationFormatTests {

        @Test
        @DisplayName("An invalid duration format is rejected with two usage messages, and the ban service is never called")
        void invalidDurationIsRejected() {
            stubExistingTarget("BadPlayer", UUID.randomUUID());

            command.tempbanWithReason(player, "BadPlayer", "not-a-duration", "reason");

            // Exact content, not just count -- a regression that sent two different (but still
            // wrong) messages would still satisfy times(2).sendMessage(anyString()).
            verify(player).sendMessage(eq("§c无效的时长格式"));
            verify(player).sendMessage(eq("§7示例: 1d, 2h, 30m, 1w, 1d12h30m"));
            verifyNoInteractions(banService);
        }

        @Test
        @DisplayName("A valid duration format proceeds to call the ban service")
        void validDurationProceeds() {
            UUID targetUuid = UUID.randomUUID();
            stubExistingTarget("BadPlayer", targetUuid);
            when(banService.banPlayer(eq(targetUuid), eq("BadPlayer"), anyString(), any(), anyString(),
                    anyLong(), isNull())).thenReturn(BanService.BanResult.SUCCESS);

            command.tempbanWithReason(player, "BadPlayer", "1d", "reason");

            verify(banService).banPlayer(eq(targetUuid), eq("BadPlayer"), eq("reason"),
                    eq(playerUuid), eq("TestPlayer"), anyLong(), isNull());
        }
    }

    @Nested
    @DisplayName("BanResult routing")
    class BanResultRoutingTests {

        @Test
        @DisplayName("SUCCESS broadcasts the ban announcement and the reason")
        void successBroadcasts() {
            UUID targetUuid = UUID.randomUUID();
            stubExistingTarget("BadPlayer", targetUuid);
            when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString(), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.SUCCESS);

            try (org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                command.tempbanWithReason(player, "BadPlayer", "1d", "hacking");

                bukkit.verify(() -> Bukkit.broadcastMessage(contains("BadPlayer")));
                bukkit.verify(() -> Bukkit.broadcastMessage(contains("hacking")));
            }
        }

        @Test
        @DisplayName("ALREADY_BANNED sends the sender a rejection message, no broadcast")
        void alreadyBannedNotifiesSenderOnly() {
            UUID targetUuid = UUID.randomUUID();
            stubExistingTarget("BadPlayer", targetUuid);
            when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString(), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.ALREADY_BANNED);

            // Wrap Bukkit statics so an unwanted Bukkit.broadcastMessage(...) added to this
            // branch is actually observed, instead of hitting the real static -> mocked-server
            // path unnoticed. Exact message content, not just anyString(), so the
            // ALREADY_BANNED and DISABLED branches can't pass with their messages swapped.
            try (org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                command.tempbanWithReason(player, "BadPlayer", "1d", "hacking");

                verify(player).sendMessage(eq("§c该玩家已被封禁"));
                bukkit.verify(() -> Bukkit.broadcastMessage(anyString()), never());
            }
        }

        @Test
        @DisplayName("DISABLED sends the sender a feature-disabled message, no broadcast")
        void disabledNotifiesSenderOnly() {
            UUID targetUuid = UUID.randomUUID();
            stubExistingTarget("BadPlayer", targetUuid);
            when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString(), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.DISABLED);

            try (org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                command.tempbanWithReason(player, "BadPlayer", "1d", "hacking");

                verify(player).sendMessage(eq("§c封禁功能已禁用"));
                bukkit.verify(() -> Bukkit.broadcastMessage(anyString()), never());
            }
        }

        @Test
        @DisplayName("A console sender is recorded as operator name \"Console\" with a null operator UUID")
        void consoleSenderRecordsConsoleOperator() {
            UUID targetUuid = UUID.randomUUID();
            stubExistingTarget("BadPlayer", targetUuid);
            CommandSender console = mock(CommandSender.class);
            when(console.getName()).thenReturn("CONSOLE");
            when(banService.banPlayer(eq(targetUuid), anyString(), anyString(), isNull(), eq("Console"),
                    anyLong(), isNull())).thenReturn(BanService.BanResult.SUCCESS);

            try (org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                command.tempbanWithReason(console, "BadPlayer", "1d", "hacking");
            }

            verify(banService).banPlayer(eq(targetUuid), anyString(), anyString(), isNull(), eq("Console"),
                    anyLong(), isNull());
        }
    }

    @Nested
    @DisplayName("tempban (default reason)")
    class DefaultReasonTests {

        @Test
        @DisplayName("The 2-arg form delegates with the default reason")
        void delegatesWithDefaultReason() {
            UUID targetUuid = UUID.randomUUID();
            stubExistingTarget("BadPlayer", targetUuid);
            when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString(), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.SUCCESS);

            try (org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                command.tempban(player, "BadPlayer", "1d");
            }

            verify(banService).banPlayer(eq(targetUuid), anyString(), eq("无理由"),
                    eq(playerUuid), eq("TestPlayer"), anyLong(), isNull());
        }
    }

    @Nested
    @DisplayName("suggest")
    class SuggestTests {

        @Test
        @DisplayName("One argument suggests online player names")
        void oneArgSuggestsOnlinePlayers() {
            Player online = EssentialsTestHelper.createMockPlayer("Alice", UUID.randomUUID());
            doReturn(java.util.Collections.singletonList(online))
                    .when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();

            List<String> result = command.suggest(player, mock(org.bukkit.command.Command.class), new String[]{"A"});

            assertThat(result).contains("Alice");
        }

        @Test
        @DisplayName("Two arguments suggest the fixed set of duration presets")
        void twoArgsSuggestDurationPresets() {
            List<String> result = command.suggest(player, mock(org.bukkit.command.Command.class), new String[]{"BadPlayer", ""});

            assertThat(result).contains("1h", "1d", "1w");
        }
    }
}
