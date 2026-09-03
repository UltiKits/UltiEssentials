package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link NamePrefixService} decisions the existing {@code NamePrefixServiceMockitoTest}
 * does not reach: {@code init()} is never called there (fields are injected directly via
 * reflection), {@code parsePlaceholders}'s PlaceholderAPI-present branch is never taken, and the
 * suffix-truncation path mirrors the existing prefix-truncation test but is itself untested.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("NamePrefixService init/parsePlaceholders/suffix-truncation Tests")
class NamePrefixServiceBehaviorTest {

    private NamePrefixService service;
    private EssentialsConfig config;
    private Scoreboard mainScoreboard;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();

        ScoreboardManager scoreboardManager = mock(ScoreboardManager.class);
        mainScoreboard = mock(Scoreboard.class);
        when(scoreboardManager.getMainScoreboard()).thenReturn(mainScoreboard);
        when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(scoreboardManager);

        scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        BukkitTask mockTask = mock(BukkitTask.class);
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        config.setNamePrefixEnabled(true);

        service = new NamePrefixService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "scoreboard", mainScoreboard);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("init")
    class InitTests {

        @Test
        @DisplayName("A disabled feature never starts the update task")
        void disabledFeatureNeverStartsUpdateTask() {
            config.setNamePrefixEnabled(false);

            service.init();

            verify(scheduler, never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        }

        @Test
        @DisplayName("An enabled feature starts the update task")
        void enabledFeatureStartsUpdateTask() {
            when(EssentialsTestHelper.getMockServer().getPluginManager().getPlugin("UltiTools"))
                    .thenReturn(mock(Plugin.class));
            config.setNamePrefixEnabled(true);

            service.init();

            verify(scheduler, times(1)).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("parsePlaceholders (via updatePlayer)")
    class ParsePlaceholdersTests {

        @Test
        @DisplayName("When PlaceholderAPI is installed, the prefix format is delegated to it")
        void delegatesToPlaceholderApiWhenInstalled() {
            Team team = mock(Team.class);
            when(mainScoreboard.getTeam(anyString())).thenReturn(null);
            when(mainScoreboard.registerNewTeam(anyString())).thenReturn(team);
            when(team.hasEntry(anyString())).thenReturn(false);
            config.setNamePrefixFormat("%player_name%'s prefix");

            PluginManager pluginManager = EssentialsTestHelper.getMockServer().getPluginManager();
            when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(mock(Plugin.class));

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            try (MockedStatic<PlaceholderAPI> papi = mockStatic(PlaceholderAPI.class)) {
                papi.when(() -> PlaceholderAPI.setPlaceholders(eq(player), anyString()))
                        .thenReturn("PAPI-RESOLVED");

                service.updatePlayer(player);

                papi.verify(() -> PlaceholderAPI.setPlaceholders(eq(player), anyString()), atLeastOnce());
                verify(team).setPrefix("PAPI-RESOLVED");
            }
        }
    }

    @Nested
    @DisplayName("Suffix truncation")
    class SuffixTruncationTests {

        @Test
        @DisplayName("A suffix longer than 64 characters is truncated to 64")
        void longSuffixIsTruncated() {
            Team team = mock(Team.class);
            when(mainScoreboard.getTeam(anyString())).thenReturn(null);
            when(mainScoreboard.registerNewTeam(anyString())).thenReturn(team);
            when(team.hasEntry(anyString())).thenReturn(false);
            config.setNameSuffixFormat("b".repeat(100));

            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            service.updatePlayer(player);

            verify(team).setSuffix(argThat(suffix -> suffix.length() <= 64));
        }
    }
}
