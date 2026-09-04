package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ScoreboardService}'s decisions not reached by the existing
 * {@code ScoreboardServiceMockitoTest}: {@code init()} is never called there (the manager field
 * is injected directly via reflection instead), {@code ensureUnique}'s collision-resolution loop
 * is never exercised (no test seeds a colliding scoreboard entry), and {@code parsePlaceholders}'
 * PlaceholderAPI-present branch is never taken (the plugin lookup always returns null there).
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("ScoreboardService init/ensureUnique/parsePlaceholders Tests")
class ScoreboardServiceBehaviorTest {

    private ScoreboardService service;
    private EssentialsConfig config;
    private ScoreboardManager scoreboardManager;
    private Scoreboard mockScoreboard;
    private Objective mockObjective;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();

        scoreboardManager = mock(ScoreboardManager.class);
        mockScoreboard = mock(Scoreboard.class);
        when(scoreboardManager.getNewScoreboard()).thenReturn(mockScoreboard);

        mockObjective = mock(Objective.class);
        Score mockScore = mock(Score.class);
        when(mockScoreboard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(mockObjective);
        when(mockObjective.getScore(anyString())).thenReturn(mockScore);
        when(mockScoreboard.getEntries()).thenReturn(new HashSet<>());

        when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(scoreboardManager);

        scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        BukkitTask mockTask = mock(BukkitTask.class);
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        service = new ScoreboardService();
        EssentialsTestHelper.setField(service, "config", config);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("init")
    class InitTests {

        @Test
        @DisplayName("A null scoreboard manager disables the feature: no update task is ever started")
        void nullManagerNeverStartsUpdateTask() {
            when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(null);
            config.setScoreboardEnabled(true);

            service.init();

            verify(scheduler, never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        }

        @Test
        @DisplayName("A present manager with the feature enabled starts the update task")
        void presentManagerAndEnabledStartsUpdateTask() {
            when(EssentialsTestHelper.getMockServer().getPluginManager().getPlugin("UltiTools"))
                    .thenReturn(mock(Plugin.class));
            config.setScoreboardEnabled(true);

            service.init();

            verify(scheduler, times(1)).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        }

        @Test
        @DisplayName("A present manager with the feature disabled never starts the update task")
        void presentManagerAndDisabledNeverStartsUpdateTask() {
            config.setScoreboardEnabled(false);

            service.init();

            verify(scheduler, never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("ensureUnique (via updateScoreboard)")
    class EnsureUniqueTests {

        @Test
        @DisplayName("A colliding line has a ChatColor code appended instead of being overwritten")
        void collidingLineGetsColorCodeAppended() throws Exception {
            EssentialsTestHelper.setField(service, "manager", scoreboardManager);
            config.setScoreboardLines(Collections.singletonList("Same Line"));
            // The scoreboard already contains the exact line text this update would produce,
            // forcing the collision-resolution loop to run at least once.
            when(mockScoreboard.getEntries()).thenReturn(new HashSet<>(Collections.singletonList("Same Line")));

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            service.enableScoreboard(player);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(mockObjective).getScore(captor.capture());
            assertThat(captor.getValue()).isNotEqualTo("Same Line");
            assertThat(captor.getValue()).startsWith("Same Line");
        }

        @Test
        @DisplayName("A resolved line longer than 40 characters is truncated to 40")
        void resolvedLineIsTruncatedAt40Characters() throws Exception {
            EssentialsTestHelper.setField(service, "manager", scoreboardManager);
            String longLine = "This line is exactly long enough to exceed forty characters";
            config.setScoreboardLines(Collections.singletonList(longLine));
            // Force at least one collision-resolution pass, appending a color code that pushes
            // the already-long line over the 40-character limit.
            when(mockScoreboard.getEntries()).thenReturn(new HashSet<>(Collections.singletonList(longLine)));

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            service.enableScoreboard(player);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(mockObjective).getScore(captor.capture());
            // Exact length (and exact value), not "at most 40" -- ScoreboardService.java does an
            // exact substring(0, 40), so any truncation short of 40 would still satisfy a "<= 40"
            // check while visibly breaking the feature. The appended ChatColor code lands past
            // position 40, so the expected value is simply the original line's first 40 chars.
            assertThat(captor.getValue()).hasSize(40).isEqualTo(longLine.substring(0, 40));
        }
    }

    @Nested
    @DisplayName("parsePlaceholders")
    class ParsePlaceholdersTests {

        @Test
        @DisplayName("When PlaceholderAPI is installed, placeholders are delegated to it instead of the fallback replacer")
        void delegatesToPlaceholderApiWhenInstalled() throws Exception {
            EssentialsTestHelper.setField(service, "manager", scoreboardManager);
            PluginManager pluginManager = EssentialsTestHelper.getMockServer().getPluginManager();
            when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(mock(Plugin.class));
            config.setScoreboardTitle("%player_name%'s board");
            config.setScoreboardLines(Collections.emptyList());

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            try (MockedStatic<PlaceholderAPI> papi = mockStatic(PlaceholderAPI.class)) {
                papi.when(() -> PlaceholderAPI.setPlaceholders(eq(player), anyString()))
                        .thenReturn("PAPI-RESOLVED");

                service.enableScoreboard(player);

                papi.verify(() -> PlaceholderAPI.setPlaceholders(eq(player), anyString()));
                verify(mockScoreboard).registerNewObjective(eq("ultiessentials"), eq("dummy"), eq("PAPI-RESOLVED"));
            }
        }
    }

    @Nested
    @DisplayName("reload")
    class ReloadTests {

        @Test
        @DisplayName("autoEnable=true re-enables the scoreboard for every currently online player")
        void autoEnableTrueReEnablesOnlinePlayers() throws Exception {
            EssentialsTestHelper.setField(service, "manager", scoreboardManager);
            EssentialsTestHelper.setField(service, "bukkitPlugin", mock(Plugin.class));
            Player online = EssentialsTestHelper.createMockPlayer("Alice", UUID.randomUUID());
            doReturn(Collections.singletonList(online))
                    .when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
            config.setScoreboardEnabled(true);
            config.setScoreboardAutoEnable(true);

            service.reload();

            assertThat(service.isEnabled(online)).isTrue();
        }

        @Test
        @DisplayName("autoEnable=false leaves online players without a scoreboard after reload")
        void autoEnableFalseLeavesOnlinePlayersDisabled() throws Exception {
            EssentialsTestHelper.setField(service, "manager", scoreboardManager);
            EssentialsTestHelper.setField(service, "bukkitPlugin", mock(Plugin.class));
            Player online = EssentialsTestHelper.createMockPlayer("Alice", UUID.randomUUID());
            doReturn(Collections.singletonList(online))
                    .when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
            config.setScoreboardEnabled(true);
            config.setScoreboardAutoEnable(false);

            service.reload();

            assertThat(service.isEnabled(online)).isFalse();
        }
    }
}
