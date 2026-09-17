package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Server;
import org.bukkit.World;
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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Codex review on UltiKits/UltiEssentials PR #44 (thread "Remove failed players from the enabled
 * scoreboard set"): {@link ScoreboardService}'s repeating update task and its {@code shutdown()}
 * walk every player with a sidebar, and one player whose sidebar cannot be built or reset must not
 * stop the players after them. A player who keeps failing stays shown and is retried every update,
 * but the failure is logged once until that player succeeds again, so the console is not flooded
 * every interval; leaving the sidebar (quit or {@code /scoreboard off}) or going offline forgets it.
 * <p>
 * The two players are created so that the first is also first in the service's iteration order.
 * <p>
 * 验证计分板更新任务中单个玩家失败不会影响其他玩家，且失败日志不会每次刷屏。
 */
@DisplayName("ScoreboardService isolates each player in its update loop and shutdown")
class ScoreboardServiceUpdateLoopIsolationTest {

    private final IllegalStateException failure = new IllegalStateException("placeholder expansion failed");

    private ScoreboardService service;
    private org.slf4j.Logger failureLog;
    private Runnable updateLoop;
    private Scoreboard mainScoreboard;
    private Player first;
    private Player second;
    private World firstWorld;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        Server server = EssentialsTestHelper.getMockServer();
        when(server.getPluginManager().getPlugin("UltiTools")).thenReturn(mock(Plugin.class));

        ScoreboardManager manager = mock(ScoreboardManager.class);
        mainScoreboard = mock(Scoreboard.class);
        lenient().when(manager.getMainScoreboard()).thenReturn(mainScoreboard);
        lenient().when(manager.getNewScoreboard()).thenAnswer(inv -> sidebarScoreboard());
        when(server.getScoreboardManager()).thenReturn(manager);

        BukkitScheduler scheduler = server.getScheduler();
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    updateLoop = inv.getArgument(1);
                    return mock(BukkitTask.class);
                });

        EssentialsConfig config = new EssentialsConfig();
        config.setScoreboardEnabled(true);
        config.setScoreboardTitle("Title");
        config.setScoreboardLines(Collections.singletonList("%player_world%"));

        service = new ScoreboardService();
        EssentialsTestHelper.setField(service, "config", config);
        service.init();
        failureLog = mock(org.slf4j.Logger.class);
        EssentialsTestHelper.setField(service, "failureLog", failureLog);

        UUID[] ids = idsInIterationOrder();
        first = onlinePlayer(server, "First", ids[0]);
        second = onlinePlayer(server, "Second", ids[1]);
        firstWorld = first.getWorld();
        service.enableScoreboard(first);
        service.enableScoreboard(second);
        clearInvocations(first, second);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("a player whose sidebar cannot be built does not stop the next player's refresh, and stays shown")
    void failingPlayerDoesNotStopLaterPlayers() {
        doThrow(failure).when(first).getWorld();

        runUpdate();

        verify(second).setScoreboard(any(Scoreboard.class));
        assertThat(service.isEnabled(first)).isTrue();
        assertThat(service.isEnabled(second)).isTrue();
    }

    @Test
    @DisplayName("a repeating failure is logged once, and again only after the player recovered and failed anew")
    void repeatedFailureIsLoggedOnceUntilRecovery() {
        doThrow(failure).when(first).getWorld();
        runUpdate();
        runUpdate();
        runUpdate();
        verify(failureLog, times(1)).error(anyString(), eq("First"), same(failure));

        doReturn(firstWorld).when(first).getWorld();
        runUpdate();
        doThrow(failure).when(first).getWorld();
        runUpdate();
        runUpdate();

        verify(failureLog, times(2)).error(anyString(), eq("First"), same(failure));
    }

    @Test
    @DisplayName("leaving the sidebar (quit or /scoreboard off) forgets a suppressed failure")
    void disablingForgetsSuppressedFailure() {
        doThrow(failure).when(first).getWorld();
        runUpdate();

        service.disableScoreboard(first); // ScoreboardListener#onPlayerQuit and /scoreboard off
        doReturn(firstWorld).when(first).getWorld();
        service.enableScoreboard(first);
        doThrow(failure).when(first).getWorld();
        runUpdate();

        verify(failureLog, times(2)).error(anyString(), eq("First"), same(failure));
    }

    @Test
    @DisplayName("a player the update loop drops because they went offline has a suppressed failure forgotten")
    void offlinePlayerForgetsSuppressedFailure() {
        doThrow(failure).when(first).getWorld();
        runUpdate();

        when(first.isOnline()).thenReturn(false);
        runUpdate();
        assertThat(service.isEnabled(first)).isFalse();

        when(first.isOnline()).thenReturn(true);
        doReturn(firstWorld).when(first).getWorld();
        service.enableScoreboard(first);
        doThrow(failure).when(first).getWorld();
        runUpdate();

        verify(failureLog, times(2)).error(anyString(), eq("First"), same(failure));
    }

    @Test
    @DisplayName("shutdown still returns the next player to the main scoreboard when one player cannot be reset")
    void shutdownIsolatesEachPlayer() {
        IllegalStateException resetFailure = new IllegalStateException("cannot set scoreboard");
        doThrow(resetFailure).when(first).setScoreboard(mainScoreboard);

        assertThatCode(service::shutdown).doesNotThrowAnyException();

        verify(second).setScoreboard(mainScoreboard);
        assertThat(service.isEnabled(second)).isFalse();
        verify(failureLog).error(anyString(), eq("First"), same(resetFailure));
    }

    private void runUpdate() {
        assertThatCode(updateLoop::run).doesNotThrowAnyException();
    }

    private static Player onlinePlayer(Server server, String name, UUID id) {
        Player player = EssentialsTestHelper.createMockPlayer(name, id);
        lenient().when(player.isOnline()).thenReturn(true);
        lenient().when(server.getPlayer(id)).thenReturn(player);
        return player;
    }

    // Two ids whose iteration order in a ConcurrentHashMap key set (the type of
    // ScoreboardService#enabledPlayers) is the order they are inserted in.
    private static UUID[] idsInIterationOrder() {
        while (true) {
            Set<UUID> probe = ConcurrentHashMap.newKeySet();
            probe.add(UUID.randomUUID());
            probe.add(UUID.randomUUID());
            Iterator<UUID> iterator = probe.iterator();
            UUID[] ids = {iterator.next(), iterator.next()};
            Set<UUID> check = ConcurrentHashMap.newKeySet();
            check.add(ids[0]);
            check.add(ids[1]);
            if (check.iterator().next().equals(ids[0])) {
                return ids;
            }
        }
    }

    private static Scoreboard sidebarScoreboard() {
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        lenient().when(scoreboard.getEntries()).thenReturn(Collections.<String>emptySet());
        lenient().when(scoreboard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(objective);
        lenient().when(objective.getScore(anyString())).thenReturn(mock(Score.class));
        return scoreboard;
    }
}
