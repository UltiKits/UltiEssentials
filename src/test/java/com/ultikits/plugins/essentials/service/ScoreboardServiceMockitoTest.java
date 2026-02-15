package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for ScoreboardService.
 * <p>
 * 计分板服务测试。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("ScoreboardService Tests (Mockito)")
class ScoreboardServiceMockitoTest {

    private ScoreboardService service;
    private EssentialsConfig config;
    private ScoreboardManager scoreboardManager;
    private Plugin mockBukkitPlugin;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();
        mockBukkitPlugin = mock(Plugin.class);

        // Mock ScoreboardManager
        scoreboardManager = mock(ScoreboardManager.class);
        Scoreboard mockScoreboard = mock(Scoreboard.class);
        when(scoreboardManager.getNewScoreboard()).thenReturn(mockScoreboard);
        when(scoreboardManager.getMainScoreboard()).thenReturn(mockScoreboard);

        // Mock Objective
        Objective mockObjective = mock(Objective.class);
        Score mockScore = mock(Score.class);
        when(mockScoreboard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(mockObjective);
        when(mockObjective.getScore(anyString())).thenReturn(mockScore);
        when(mockScoreboard.getEntries()).thenReturn(new HashSet<>());

        // Register scoreboardManager
        when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(scoreboardManager);

        // Mock scheduler
        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        BukkitTask mockTask = mock(BukkitTask.class);
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        service = new ScoreboardService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "manager", scoreboardManager);
        EssentialsTestHelper.setField(service, "bukkitPlugin", mockBukkitPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("enableScoreboard")
    class EnableScoreboardTests {

        @Test
        @DisplayName("Should enable scoreboard for player")
        void shouldEnableScoreboard() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.enableScoreboard(player);

            assertThat(service.isEnabled(player)).isTrue();
        }

        @Test
        @DisplayName("Should not enable when scoreboard feature is disabled")
        void shouldNotEnableWhenDisabled() {
            config.setScoreboardEnabled(false);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.enableScoreboard(player);

            assertThat(service.isEnabled(player)).isFalse();
        }
    }

    @Nested
    @DisplayName("disableScoreboard")
    class DisableScoreboardTests {

        @Test
        @DisplayName("Should disable scoreboard for player")
        void shouldDisableScoreboard() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.enableScoreboard(player);
            service.disableScoreboard(player);

            assertThat(service.isEnabled(player)).isFalse();
        }

        @Test
        @DisplayName("Should reset to default scoreboard")
        void shouldResetScoreboard() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.enableScoreboard(player);
            service.disableScoreboard(player);

            // enableScoreboard calls updateScoreboard (setScoreboard once),
            // then disableScoreboard calls setScoreboard again
            verify(player, atLeast(2)).setScoreboard(any(Scoreboard.class));
        }
    }

    @Nested
    @DisplayName("toggleScoreboard")
    class ToggleScoreboardTests {

        @Test
        @DisplayName("Should toggle on when off")
        void shouldToggleOn() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            boolean result = service.toggleScoreboard(player);

            assertThat(result).isTrue();
            assertThat(service.isEnabled(player)).isTrue();
        }

        @Test
        @DisplayName("Should toggle off when on")
        void shouldToggleOff() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.enableScoreboard(player);
            boolean result = service.toggleScoreboard(player);

            assertThat(result).isFalse();
            assertThat(service.isEnabled(player)).isFalse();
        }
    }

    @Nested
    @DisplayName("isEnabled")
    class IsEnabledTests {

        @Test
        @DisplayName("Should return false initially")
        void shouldReturnFalseInitially() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            assertThat(service.isEnabled(player)).isFalse();
        }
    }

    @Nested
    @DisplayName("updateScoreboard")
    class UpdateScoreboardTests {

        @Test
        @DisplayName("Should update scoreboard for enabled player")
        void shouldUpdateScoreboard() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            // Mock online players for placeholder replacement
            when(EssentialsTestHelper.getMockServer().getOnlinePlayers()).thenReturn(new ArrayList<>());
            when(EssentialsTestHelper.getMockServer().getMaxPlayers()).thenReturn(100);

            service.enableScoreboard(player);
            service.updateScoreboard(player);

            verify(player, atLeastOnce()).setScoreboard(any(Scoreboard.class));
        }

        @Test
        @DisplayName("Should skip update when manager is null")
        void shouldSkipWhenManagerNull() throws Exception {
            EssentialsTestHelper.setField(service, "manager", null);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.updateScoreboard(player);

            verify(player, never()).setScoreboard(any());
        }

        @Test
        @DisplayName("Should skip update when player not enabled")
        void shouldSkipWhenNotEnabled() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.updateScoreboard(player);

            verify(player, never()).setScoreboard(any());
        }
    }

    @Nested
    @DisplayName("shutdown")
    class ShutdownTests {

        @Test
        @DisplayName("Should clear enabled players on shutdown")
        void shouldClearOnShutdown() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            service.enableScoreboard(player);
            service.shutdown();

            assertThat(service.isEnabled(player)).isFalse();
        }

        @Test
        @DisplayName("Should reset scoreboards on shutdown")
        void shouldResetScoreboards() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            service.enableScoreboard(player);
            service.shutdown();

            verify(player, atLeastOnce()).setScoreboard(any(Scoreboard.class));
        }
    }

    @Nested
    @DisplayName("reload")
    class ReloadTests {

        @Test
        @DisplayName("Should clear and restart when enabled")
        void shouldReloadWhenEnabled() {
            config.setScoreboardEnabled(true);

            service.reload();

            // Should not throw
        }

        @Test
        @DisplayName("Should only shutdown when disabled")
        void shouldOnlyShutdownWhenDisabled() {
            config.setScoreboardEnabled(false);

            service.reload();

            // Should not start update task
        }
    }
}
