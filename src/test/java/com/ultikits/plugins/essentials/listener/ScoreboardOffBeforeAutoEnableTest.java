package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.commands.ScoreboardCommand;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEssentials#45: a {@code /scoreboard off} (or a {@code /scoreboard} that turns it off)
 * run in the second between joining and the delayed auto-enable is the player's choice for the
 * session; the delayed auto-enable must not override it.
 */
@DisplayName("/scoreboard off right after joining wins over the delayed auto-enable (#45)")
class ScoreboardOffBeforeAutoEnableTest {

    private ScoreboardService service;
    private ScoreboardCommand command;
    private ScoreboardListener listener;
    private Player player;
    private Scoreboard privateBoard;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        EssentialsConfig config = new EssentialsConfig();
        config.setScoreboardEnabled(true);
        config.setScoreboardAutoEnable(true);
        config.setScoreboardTitle("Title");
        config.setScoreboardLines(Collections.singletonList("Line"));

        ScoreboardManager manager = mock(ScoreboardManager.class);
        privateBoard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        lenient().when(manager.getNewScoreboard()).thenReturn(privateBoard);
        lenient().when(manager.getMainScoreboard()).thenReturn(mock(Scoreboard.class));
        lenient().when(privateBoard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(objective);
        lenient().when(privateBoard.getEntries()).thenReturn(Collections.<String>emptySet());
        lenient().when(objective.getScore(anyString())).thenReturn(mock(Score.class));

        service = new ScoreboardService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "plugin", CatalogueText.plugin("en"));
        EssentialsTestHelper.setField(service, "manager", manager);
        EssentialsTestHelper.setField(service, "bukkitPlugin", mock(Plugin.class));

        command = new ScoreboardCommand();
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(command, "scoreboardService", service);

        listener = new ScoreboardListener();
        EssentialsTestHelper.setField(listener, "config", config);
        EssentialsTestHelper.setField(listener, "scoreboardService", service);
        EssentialsTestHelper.setField(listener, "bukkitPlugin", mock(Plugin.class));

        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private PlayerJoinEvent join() {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private Runnable lastDelayedEnable() {
        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).runTaskLater(any(Plugin.class), task.capture(), eq(20L));
        List<Runnable> tasks = task.getAllValues();
        return tasks.get(tasks.size() - 1);
    }

    @Test
    @DisplayName("join, /scoreboard off within the second, then the delayed enable: no sidebar")
    void offBeforeTheDelayedEnableSticks() {
        listener.onPlayerJoin(join());
        command.disable(player);

        lastDelayedEnable().run();

        assertThat(service.isEnabled(player)).isFalse();
        verify(player, never()).setScoreboard(privateBoard);
    }

    @Test
    @DisplayName("join, /scoreboard (toggle) twice within the second, then the delayed enable: no sidebar")
    void toggleOffBeforeTheDelayedEnableSticks() {
        listener.onPlayerJoin(join());
        command.toggle(player);
        command.toggle(player);

        lastDelayedEnable().run();

        assertThat(service.isEnabled(player)).isFalse();
    }

    @Test
    @DisplayName("the choice lasts for the session only: after a quit and a new join the sidebar is enabled again")
    void theChoiceEndsWithTheSession() {
        listener.onPlayerJoin(join());
        command.disable(player);
        lastDelayedEnable().run();
        listener.onPlayerQuit(mock(PlayerQuitEvent.class, inv -> inv.getMethod().getName().equals("getPlayer") ? player : null));
        clearInvocations(player);

        listener.onPlayerJoin(join());
        lastDelayedEnable().run();

        assertThat(service.isEnabled(player)).isTrue();
    }

    @Test
    @DisplayName("/scoreboard on after /scoreboard off turns the sidebar on again")
    void onAfterOffEnablesAgain() {
        listener.onPlayerJoin(join());
        command.disable(player);
        command.enable(player);

        lastDelayedEnable().run();

        assertThat(service.isEnabled(player)).isTrue();
    }

    // Every automatic enable honours the choice, not only the delayed join enable: a reload that turns
    // the scoreboard feature on shows it to online players by auto-enable, and that must skip a player
    // who turned it off this session (found by review on the #45 fix: the reload path was the other
    // automatic caller of the same enable).

    @Test
    @DisplayName("/scoreboard off, then the feature is switched off and on by reloads: the sidebar stays off")
    void aReloadThatTurnsTheFeatureOnKeepsTheChoice() throws Exception {
        doReturn(Collections.singletonList(player)).when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
        listener.onPlayerJoin(join());
        lastDelayedEnable().run();
        command.disable(player);
        EssentialsConfig config = (EssentialsConfig) EssentialsTestHelper.getField(service, "config");
        config.setScoreboardEnabled(false);
        service.reload();

        config.setScoreboardEnabled(true);
        service.reload();

        assertThat(service.isEnabled(player)).isFalse();
    }

    @Test
    @DisplayName("Control: without a /scoreboard off, a reload that turns the feature on shows the sidebar by auto-enable")
    void aReloadThatTurnsTheFeatureOnShowsItOtherwise() throws Exception {
        doReturn(Collections.singletonList(player)).when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
        EssentialsConfig config = (EssentialsConfig) EssentialsTestHelper.getField(service, "config");
        config.setScoreboardEnabled(false);
        service.reload();

        config.setScoreboardEnabled(true);
        service.reload();

        assertThat(service.isEnabled(player)).isTrue();
    }
}
