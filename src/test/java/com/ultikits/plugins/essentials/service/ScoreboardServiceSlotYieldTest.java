package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.FakeScoreboards;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.manager.PluginManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sidebar slot goes to whichever sidebar a player sees first: this module's sidebar is shown
 * only while the player views the main scoreboard or this module's own board, is removed only while
 * it is on screen, and comes back on the next update once the slot is free (maintainer decision
 * 2026-09-27, UltiKits/UltiEssentials#40 with UltiKits/UltiSideBar#26).
 */
@DisplayName("ScoreboardService yields the sidebar slot to another scoreboard (#40)")
class ScoreboardServiceSlotYieldTest {

    private ScoreboardService service;
    private EssentialsConfig config;
    private Scoreboard mainBoard;
    private Scoreboard newBoard;
    private Scoreboard foreignBoard;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        mainBoard = FakeScoreboards.board();
        newBoard = FakeScoreboards.board();
        foreignBoard = FakeScoreboards.board();
        Objective objective = mock(Objective.class);
        lenient().when(newBoard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(objective);
        lenient().when(newBoard.getObjective("ultiessentials")).thenReturn(objective);
        lenient().when(newBoard.getEntries()).thenReturn(Collections.<String>emptySet());
        lenient().when(objective.getScore(anyString())).thenReturn(mock(Score.class));

        ScoreboardManager manager = mock(ScoreboardManager.class);
        lenient().when(manager.getMainScoreboard()).thenReturn(mainBoard);
        lenient().when(manager.getNewScoreboard()).thenReturn(newBoard);
        when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(manager);

        config = new EssentialsConfig();
        config.setScoreboardEnabled(true);
        config.setScoreboardTitle("Title");
        config.setScoreboardLines(Collections.singletonList("Line"));

        service = new ScoreboardService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "plugin", CatalogueText.plugin("en"));
        EssentialsTestHelper.setField(service, "manager", manager);
        EssentialsTestHelper.setField(service, "bukkitPlugin", mock(Plugin.class));

        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        lenient().when(player.isOnline()).thenReturn(true);
        lenient().when(EssentialsTestHelper.getMockServer().getPlayer(player.getUniqueId())).thenReturn(player);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("Enabling while another scoreboard holds the slot leaves that scoreboard on screen and reports the slot as taken")
    void enableYieldsToAForeignBoard() {
        player.setScoreboard(foreignBoard);

        service.enableScoreboard(player);

        assertThat(player.getScoreboard()).isSameAs(foreignBoard);
        assertThat(service.isEnabled(player)).isTrue();
        assertThat(service.isSlotTakenByAnother(player)).isTrue();
    }

    @Test
    @DisplayName("Enabling while the player is on the main scoreboard shows this sidebar")
    void enableTakesAFreeSlot() {
        player.setScoreboard(mainBoard);

        service.enableScoreboard(player);

        assertThat(player.getScoreboard()).isSameAs(newBoard);
        assertThat(service.isSlotTakenByAnother(player)).isFalse();
    }

    @Test
    @DisplayName("An update does not take the slot back while another scoreboard holds it, and shows this sidebar again once the slot is free")
    void updateWaitsForTheSlotAndThenComesBack() {
        player.setScoreboard(foreignBoard);
        service.enableScoreboard(player);

        service.updateScoreboard(player);
        assertThat(player.getScoreboard()).isSameAs(foreignBoard);

        player.setScoreboard(mainBoard);
        service.updateScoreboard(player);
        assertThat(player.getScoreboard()).isSameAs(newBoard);
    }

    @Test
    @DisplayName("/scoreboard off while another scoreboard is showing leaves that scoreboard on screen")
    void disableLeavesAForeignBoardAlone() {
        player.setScoreboard(mainBoard);
        service.enableScoreboard(player);
        player.setScoreboard(foreignBoard);

        service.disableScoreboard(player);

        assertThat(player.getScoreboard()).isSameAs(foreignBoard);
    }

    @Test
    @DisplayName("/scoreboard off while this sidebar is showing returns the player to the main scoreboard")
    void disableReturnsItsOwnBoardToMain() {
        player.setScoreboard(mainBoard);
        service.enableScoreboard(player);

        service.disableScoreboard(player);

        assertThat(player.getScoreboard()).isSameAs(mainBoard);
    }

    @Test
    @DisplayName("Shutdown leaves a player who is viewing another scoreboard on it")
    void shutdownLeavesAForeignBoardAlone() {
        player.setScoreboard(mainBoard);
        service.enableScoreboard(player);
        player.setScoreboard(foreignBoard);

        service.shutdown();

        assertThat(player.getScoreboard()).isSameAs(foreignBoard);
    }

    @Nested
    @DisplayName("Start-up notice when UltiSideBar's sidebar is also enabled")
    class OtherSidebarNotice {

        @TempDir
        Path moduleFolder;

        private UltiToolsPlugin sideBar(String enabledLine) throws Exception {
            Path configFolder = moduleFolder.resolve("config");
            Files.createDirectories(configFolder);
            Files.write(configFolder.resolve("sidebar.yml"), enabledLine.getBytes(StandardCharsets.UTF_8));
            UltiToolsPlugin module = mock(UltiToolsPlugin.class);
            lenient().when(module.getPluginName()).thenReturn("UltiSideBar");
            lenient().when(module.getResourceFolderPath()).thenReturn(moduleFolder.toString());
            return module;
        }

        private org.slf4j.Logger runStartUp(List<UltiToolsPlugin> modules) throws Exception {
            org.slf4j.Logger log = mock(org.slf4j.Logger.class);
            BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
            lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                    .thenReturn(mock(BukkitTask.class));
            when(EssentialsTestHelper.getMockServer().getPluginManager().getPlugin("UltiTools"))
                    .thenReturn(mock(Plugin.class));
            try (MockedStatic<UltiToolsPlugin> framework = mockStatic(UltiToolsPlugin.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                framework.when(UltiToolsPlugin::getPluginManager).thenReturn(pluginManager);
                lenient().when(pluginManager.getPluginList()).thenReturn(modules);

                service.init();
                EssentialsTestHelper.setField(service, "failureLog", log);

                ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
                verify(scheduler).runTask(any(Plugin.class), task.capture());
                task.getValue().run();
            }
            return log;
        }

        @Test
        @DisplayName("Logs one line after start-up when UltiSideBar is loaded with its sidebar enabled")
        void logsWhenBothSidebarsAreEnabled() throws Exception {
            org.slf4j.Logger log = runStartUp(Collections.singletonList(sideBar("enabled: true\n")));

            verify(log, times(1)).info(CatalogueText.text("en", "essentials.log.scoreboard_other_sidebar"));
        }

        @Test
        @DisplayName("Logs nothing when UltiSideBar's sidebar is disabled")
        void silentWhenTheOtherSidebarIsDisabled() throws Exception {
            org.slf4j.Logger log = runStartUp(Collections.singletonList(sideBar("enabled: false\n")));

            verify(log, never()).info(anyString());
        }

        @Test
        @DisplayName("Logs nothing when UltiSideBar is not loaded")
        void silentWithoutTheOtherModule() throws Exception {
            org.slf4j.Logger log = runStartUp(Collections.<UltiToolsPlugin>emptyList());

            verify(log, never()).info(anyString());
        }
    }
}
