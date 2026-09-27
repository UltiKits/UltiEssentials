package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.i18n.CatalogueText;
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
        EssentialsTestHelper.setField(service, "plugin", CatalogueText.plugin("zh"));
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
    @DisplayName("A blank title and empty lines are shown exactly as configured, as at origin/master "
            + "(maintainer decision 2026-09-25, UltiKits/UltiEssentials#26: built-in text is written into "
            + "the file at start-up, not resolved at read time)")
    class BlankValueTests {

        private String titleShown() throws Exception {
            EssentialsTestHelper.setField(service, "manager", scoreboardManager);
            service.enableScoreboard(EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID()));
            ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
            verify(mockScoreboard).registerNewObjective(anyString(), anyString(), title.capture());
            return title.getValue();
        }

        @Test
        @DisplayName("a blank title is shown as blank")
        void blankTitleIsShownAsBlank() throws Exception {
            config.setScoreboardTitle("");
            assertThat(titleShown()).isEmpty();
        }

        @Test
        @DisplayName("a whitespace-only title is shown unchanged")
        void whitespaceTitleIsShownUnchanged() throws Exception {
            config.setScoreboardTitle("  ");
            assertThat(titleShown()).isEqualTo("  ");
        }

        @Test
        @DisplayName("a customised title is shown unchanged")
        void customisedTitleIsKept() throws Exception {
            config.setScoreboardTitle("&bMy Server");
            assertThat(titleShown()).isEqualTo("\u00a7bMy Server");
        }

        @Test
        @DisplayName("empty lines draw no scoreboard entries")
        void emptyLinesDrawNothing() throws Exception {
            config.setScoreboardTitle("x");
            config.setScoreboardLines(new java.util.ArrayList<String>());
            titleShown();
            verify(mockObjective, never()).getScore(anyString());
        }
    }

    @Nested
    @DisplayName("ensureUnique (via updateScoreboard): lines are truncated to 40 characters first, then made unique (#41)")
    class EnsureUniqueTests {

        private List<String> entriesShown(List<String> lines) throws Exception {
            EssentialsTestHelper.setField(service, "manager", scoreboardManager);
            config.setScoreboardLines(lines);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            service.enableScoreboard(player);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(mockObjective, atLeastOnce()).getScore(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("A line equal to an earlier line has a ChatColor code appended instead of overwriting it")
        void collidingLineGetsColorCodeAppended() throws Exception {
            List<String> entries = entriesShown(Arrays.asList("Same Line", "Same Line"));

            assertThat(entries).hasSize(2);
            assertThat(entries.get(0)).isEqualTo("Same Line");
            assertThat(entries.get(1)).isNotEqualTo("Same Line").startsWith("Same Line");
        }

        @Test
        @DisplayName("A line longer than 40 characters is truncated to exactly its first 40")
        void longLineIsTruncatedAt40Characters() throws Exception {
            String longLine = "This line is exactly long enough to exceed forty characters";

            List<String> entries = entriesShown(Collections.singletonList(longLine));

            assertThat(entries).containsExactly(longLine.substring(0, 40));
        }

        @Test
        @DisplayName("Two lines that differ only after character 40 both show as distinct entries of at most 40 characters (#41)")
        void linesSharingAFortyCharacterPrefixStayDistinct() throws Exception {
            String sharedPrefix = "0123456789012345678901234567890123456789012345";

            List<String> entries = entriesShown(Arrays.asList(sharedPrefix + "A", sharedPrefix + "B"));

            assertThat(entries).hasSize(2);
            assertThat(new HashSet<>(entries)).hasSize(2);
            assertThat(entries).allSatisfy(entry -> assertThat(entry.length()).isLessThanOrEqualTo(40));
            assertThat(entries.get(0)).isEqualTo(sharedPrefix.substring(0, 40));
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

                // The empty lines now show the language file's default lines, which go through
                // PlaceholderAPI too, so the title is not the only call.
                papi.verify(() -> PlaceholderAPI.setPlaceholders(eq(player), anyString()), atLeastOnce());
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
