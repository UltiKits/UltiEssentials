package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the two decisions {@code BanCommandsTest} does not reach for {@link BanCommand}: the
 * player-existence rejection (every existing test stubs a target that exists), and the
 * {@code suggest} arg-count routing.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("BanCommand behaviour Tests")
class BanCommandBehaviorTest {

    private BanCommand command;
    private BanService banService;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        banService = mock(BanService.class);
        command = new BanCommand();
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(command, "banService", banService);
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("A name with no UUID and no play history is rejected without calling the ban service")
    void nonexistentPlayerIsRejected() {
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(null);
        when(target.hasPlayedBefore()).thenReturn(false);
        Server server = Bukkit.getServer();
        when(server.getOfflinePlayer(eq("Nobody"))).thenReturn(target);

        command.banWithReason(player, "Nobody", "reason");

        verify(player).sendMessage(contains("Nobody"));
        verifyNoInteractions(banService);
    }

    @Test
    @DisplayName("A UUID-less name that HAS played before is not rejected by the existence check")
    void uuidLessButPreviouslyPlayedNameProceeds() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        // getUniqueId() is null on first read (the existence check) -- a real quirk this test
        // pins as its own decision, distinct from "never played" -- then non-null when banPlayer
        // itself is invoked, matching what banWithReason actually passes downstream.
        when(target.getUniqueId()).thenReturn(null, targetUuid);
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getName()).thenReturn("ReturningPlayer");
        Server server = Bukkit.getServer();
        when(server.getOfflinePlayer(eq("ReturningPlayer"))).thenReturn(target);
        when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(BanService.BanResult.SUCCESS);

        command.banWithReason(player, "ReturningPlayer", "reason");

        verify(banService).banPlayer(eq(targetUuid), eq("ReturningPlayer"), eq("reason"),
                any(), anyString());
    }

    @Test
    @DisplayName("A target with no display name falls back to the typed player name in the ban call")
    void nullTargetNameFallsBackToTypedName() {
        UUID targetUuid = UUID.randomUUID();
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getName()).thenReturn(null);
        Server server = Bukkit.getServer();
        when(server.getOfflinePlayer(eq("TypedName"))).thenReturn(target);
        when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(BanService.BanResult.SUCCESS);

        command.banWithReason(player, "TypedName", "reason");

        verify(banService).banPlayer(eq(targetUuid), eq("TypedName"), eq("reason"), any(), anyString());
    }

    @Test
    @DisplayName("suggest with one argument suggests online player names")
    void suggestWithOneArgSuggestsOnlinePlayers() {
        Player online = EssentialsTestHelper.createMockPlayer("Alice", UUID.randomUUID());
        doReturn(java.util.Collections.singletonList(online))
                .when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();

        List<String> result = command.suggest(player, mock(org.bukkit.command.Command.class), new String[]{"A"});

        assertThat(result).contains("Alice");
    }

    @Test
    @DisplayName("suggest with a non-1 argument count defers to the framework's own mapping-based completion")
    void suggestWithOtherArgCountDefersToSuper() {
        List<String> result = command.suggest(player, mock(org.bukkit.command.Command.class), new String[]{});

        assertThat(result).isNotNull();
    }
}
