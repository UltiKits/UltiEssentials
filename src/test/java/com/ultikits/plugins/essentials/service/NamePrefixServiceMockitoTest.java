package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for NamePrefixService.
 * <p>
 * 头顶称号服务测试。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("NamePrefixService Tests (Mockito)")
class NamePrefixServiceMockitoTest {

    private NamePrefixService service;
    private EssentialsConfig config;
    private Scoreboard mainScoreboard;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();

        Plugin mockBukkitPlugin = mock(Plugin.class);

        // Mock ScoreboardManager
        org.bukkit.scoreboard.ScoreboardManager scoreboardManager = mock(org.bukkit.scoreboard.ScoreboardManager.class);
        mainScoreboard = mock(Scoreboard.class);
        when(scoreboardManager.getMainScoreboard()).thenReturn(mainScoreboard);
        when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(scoreboardManager);

        // Mock scheduler
        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        BukkitTask mockTask = mock(BukkitTask.class);
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        // Enable name prefix so updatePlayer doesn't short-circuit
        config.setNamePrefixEnabled(true);

        service = new NamePrefixService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "bukkitPlugin", mockBukkitPlugin);
        EssentialsTestHelper.setField(service, "scoreboard", mainScoreboard);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("updatePlayer")
    class UpdatePlayerTests {

        @Test
        @DisplayName("Should create team and update prefix/suffix")
        void shouldUpdatePlayer() {
            Team team = mock(Team.class);
            when(mainScoreboard.getTeam(anyString())).thenReturn(null);
            when(mainScoreboard.registerNewTeam(anyString())).thenReturn(team);
            when(team.hasEntry(anyString())).thenReturn(false);

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.updatePlayer(player);

            verify(team).addEntry("Steve");
            verify(team).setPrefix(anyString());
            verify(team).setSuffix(anyString());
        }

        @Test
        @DisplayName("Should reuse existing team")
        void shouldReuseExistingTeam() {
            Team team = mock(Team.class);
            when(mainScoreboard.getTeam(anyString())).thenReturn(team);
            when(team.hasEntry(anyString())).thenReturn(true);

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.updatePlayer(player);

            verify(mainScoreboard, never()).registerNewTeam(anyString());
            verify(team, never()).addEntry(anyString());
        }

        @Test
        @DisplayName("Should skip when disabled")
        void shouldSkipWhenDisabled() {
            config.setNamePrefixEnabled(false);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.updatePlayer(player);

            verify(mainScoreboard, never()).registerNewTeam(anyString());
        }

        @Test
        @DisplayName("Should truncate long prefix")
        void shouldTruncateLongPrefix() {
            Team team = mock(Team.class);
            when(mainScoreboard.getTeam(anyString())).thenReturn(null);
            when(mainScoreboard.registerNewTeam(anyString())).thenReturn(team);
            when(team.hasEntry(anyString())).thenReturn(false);

            // Set a very long prefix format
            config.setNamePrefixFormat("a".repeat(100));

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.updatePlayer(player);

            verify(team).setPrefix(argThat(prefix -> prefix.length() <= 64));
        }
    }

    @Nested
    @DisplayName("removePlayer")
    class RemovePlayerTests {

        @Test
        @DisplayName("Should remove player entry from team")
        void shouldRemovePlayer() {
            Team team = mock(Team.class);
            when(mainScoreboard.getTeam(anyString())).thenReturn(null);
            when(mainScoreboard.registerNewTeam(anyString())).thenReturn(team);
            when(team.hasEntry(anyString())).thenReturn(false);

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            // First add player
            service.updatePlayer(player);
            // Then remove
            service.removePlayer(player);

            verify(team).removeEntry("Steve");
        }

        @Test
        @DisplayName("Should not throw when player was never added")
        void shouldNotThrowWhenNeverAdded() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.removePlayer(player);
            // Should not throw
        }
    }

    @Nested
    @DisplayName("shutdown")
    class ShutdownTests {

        @Test
        @DisplayName("Should clean up teams on shutdown")
        void shouldCleanUpTeams() {
            Team team = mock(Team.class);
            when(mainScoreboard.getTeam(anyString())).thenReturn(null);
            when(mainScoreboard.registerNewTeam(anyString())).thenReturn(team);
            when(team.hasEntry(anyString())).thenReturn(false);
            when(team.getEntries()).thenReturn(new HashSet<>(Collections.singletonList("Steve")));

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            service.updatePlayer(player);

            service.shutdown();

            verify(team).removeEntry("Steve");
        }

        @Test
        @DisplayName("Should not throw when no players")
        void shouldNotThrowWhenEmpty() {
            service.shutdown();
            // Should not throw
        }
    }

    @Nested
    @DisplayName("reload")
    class ReloadTests {

        @Test
        @DisplayName("Should reload when enabled")
        void shouldReloadWhenEnabled() {
            config.setNamePrefixEnabled(true);

            service.reload();

            // Should not throw
        }

        @Test
        @DisplayName("Should only shutdown when disabled")
        void shouldOnlyShutdownWhenDisabled() {
            config.setNamePrefixEnabled(false);

            service.reload();

            // Should not re-init
        }
    }
}
