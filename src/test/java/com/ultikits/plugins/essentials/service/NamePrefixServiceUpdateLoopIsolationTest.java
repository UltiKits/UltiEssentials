package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Same defect class as {@link ScoreboardServiceUpdateLoopIsolationTest}, for
 * {@link NamePrefixService}: its repeating task updates every online player, and its
 * {@code shutdown()} clears every recorded team. One player whose prefix cannot be applied, or one
 * team that can no longer be cleared (for example removed with the vanilla {@code team remove}
 * command), must not stop the others. A repeating failure is logged once until that player succeeds
 * again, and {@code removePlayer} (quit) forgets it.
 * <p>
 * The two players are created so that the first is also first in the service's team map order.
 * <p>
 * 验证头顶称号更新任务与关闭清理中单个玩家失败不会影响其他玩家，且失败日志不会刷屏。
 */
@DisplayName("NamePrefixService isolates each player in its update loop and shutdown")
class NamePrefixServiceUpdateLoopIsolationTest {

    private final IllegalStateException failure = new IllegalStateException("placeholder expansion failed");

    private NamePrefixService service;
    private org.slf4j.Logger failureLog;
    private Runnable updateLoop;
    private Player first;
    private Player second;
    private Team firstTeam;
    private Team secondTeam;
    private Map<String, Team> teams;
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        server = EssentialsTestHelper.getMockServer();
        when(server.getPluginManager().getPlugin("UltiTools")).thenReturn(mock(Plugin.class));

        UUID[] ids = idsInIterationOrder();
        first = EssentialsTestHelper.createMockPlayer("First", ids[0]);
        second = EssentialsTestHelper.createMockPlayer("Second", ids[1]);
        lenient().when(server.getPlayer(ids[0])).thenReturn(first);
        lenient().when(server.getPlayer(ids[1])).thenReturn(second);
        doReturn(Arrays.asList(first, second)).when(server).getOnlinePlayers();

        firstTeam = teamFor("First");
        secondTeam = teamFor("Second");
        teams = new HashMap<>();
        teams.put("up_" + ids[0].toString().substring(0, 8), firstTeam);
        teams.put("up_" + ids[1].toString().substring(0, 8), secondTeam);
        Scoreboard mainScoreboard = mock(Scoreboard.class);
        lenient().when(mainScoreboard.getTeam(anyString())).thenAnswer(inv -> teams.get(inv.<String>getArgument(0)));
        ScoreboardManager manager = mock(ScoreboardManager.class);
        when(manager.getMainScoreboard()).thenReturn(mainScoreboard);
        when(server.getScoreboardManager()).thenReturn(manager);

        BukkitScheduler scheduler = server.getScheduler();
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    updateLoop = inv.getArgument(1);
                    return mock(BukkitTask.class);
                });

        EssentialsConfig config = new EssentialsConfig();
        config.setNamePrefixEnabled(true);
        config.setNamePrefixFormat("[P] ");
        config.setNameSuffixFormat("");

        service = new NamePrefixService();
        EssentialsTestHelper.setField(service, "config", config);
        service.init();
        failureLog = mock(org.slf4j.Logger.class);
        EssentialsTestHelper.setField(service, "failureLog", failureLog);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("a player whose prefix cannot be applied does not stop the next player's update")
    void failingPlayerDoesNotStopLaterPlayers() {
        doThrow(failure).when(firstTeam).setPrefix(anyString());

        runUpdate();

        verify(secondTeam).setPrefix("[P] ");
    }

    @Test
    @DisplayName("a repeating failure is logged once, and again only after the player recovered and failed anew")
    void repeatedFailureIsLoggedOnceUntilRecovery() {
        doThrow(failure).when(firstTeam).setPrefix(anyString());
        runUpdate();
        runUpdate();
        runUpdate();
        verify(failureLog, times(1)).error(anyString(), eq("First"), same(failure));

        doNothing().when(firstTeam).setPrefix(anyString());
        runUpdate();
        doThrow(failure).when(firstTeam).setPrefix(anyString());
        runUpdate();
        runUpdate();

        verify(failureLog, times(2)).error(anyString(), eq("First"), same(failure));
        verify(failureLog, times(1)).info(anyString(), eq("First"));
    }

    @Test
    @DisplayName("a team removed with the Bukkit API between updates is re-resolved, so the prefix comes back")
    void removedTeamIsResolvedAgain() {
        runUpdate();
        verify(firstTeam).setPrefix("[P] ");
        UUID firstId = first.getUniqueId();
        String teamName = "up_" + firstId.toString().substring(0, 8);
        IllegalStateException unregistered = new IllegalStateException("Unregistered scoreboard component");
        // Team#unregister(): the old object now throws, and the scoreboard no longer knows the name,
        // so a new team with that name is what getTeam/registerNewTeam hand out.
        doThrow(unregistered).when(firstTeam).hasEntry(anyString());
        Team recreated = teamFor("First");
        teams.put(teamName, recreated);

        runUpdate();
        runUpdate();

        verify(failureLog, times(1)).error(anyString(), eq("First"), same(unregistered));
        verify(recreated).setPrefix("[P] ");
        verify(failureLog).info(anyString(), eq("First"));
    }

    @Test
    @DisplayName("reload (shutdown) forgets a suppressed failure, so a failure that persists is reported again")
    void shutdownForgetsSuppressedFailures() {
        doThrow(failure).when(firstTeam).setPrefix(anyString());
        runUpdate();

        service.reload();
        runUpdate();

        verify(failureLog, times(2)).error(anyString(), eq("First"), same(failure));
    }

    @Test
    @DisplayName("removePlayer (quit) forgets a suppressed failure")
    void quitForgetsSuppressedFailure() {
        doThrow(failure).when(firstTeam).setPrefix(anyString());
        runUpdate();

        service.removePlayer(first); // NamePrefixListener#onPlayerQuit
        runUpdate();

        verify(failureLog, times(2)).error(anyString(), eq("First"), same(failure));
    }

    @Test
    @DisplayName("shutdown still clears the next team when one team can no longer be cleared")
    void shutdownIsolatesEachTeam() {
        runUpdate();
        IllegalStateException unregistered = new IllegalStateException("Unregistered scoreboard component");
        doThrow(unregistered).when(firstTeam).getEntries();

        assertThatCode(service::shutdown).doesNotThrowAnyException();

        verify(secondTeam).removeEntry("Second");
        verify(failureLog).error(anyString(), eq("First"), same(unregistered));
    }

    @Test
    @DisplayName("shutdown names a player who is offline by UUID, since no name is at hand")
    void shutdownNamesOfflinePlayerByUuid() {
        runUpdate();
        IllegalStateException unregistered = new IllegalStateException("Unregistered scoreboard component");
        doThrow(unregistered).when(firstTeam).getEntries();
        UUID firstId = first.getUniqueId(); // resolved before verify: a mock call inside verify breaks the matchers
        when(server.getPlayer(firstId)).thenReturn(null);

        assertThatCode(service::shutdown).doesNotThrowAnyException();

        verify(failureLog).error(anyString(), eq(firstId), same(unregistered));
    }

    private void runUpdate() {
        assertThatCode(updateLoop::run).doesNotThrowAnyException();
    }

    private static Team teamFor(String entry) {
        Team team = mock(Team.class);
        lenient().when(team.hasEntry(anyString())).thenReturn(false);
        lenient().when(team.getEntries()).thenReturn(Collections.singleton(entry));
        return team;
    }

    // Two ids whose iteration order in a HashMap (the type of NamePrefixService#playerTeams) is the
    // order they are inserted in.
    private static UUID[] idsInIterationOrder() {
        while (true) {
            Map<UUID, Boolean> probe = new HashMap<>();
            probe.put(UUID.randomUUID(), Boolean.TRUE);
            probe.put(UUID.randomUUID(), Boolean.TRUE);
            Iterator<UUID> iterator = probe.keySet().iterator();
            UUID[] ids = {iterator.next(), iterator.next()};
            Map<UUID, Boolean> check = new HashMap<>();
            check.put(ids[0], Boolean.TRUE);
            check.put(ids[1], Boolean.TRUE);
            if (check.keySet().iterator().next().equals(ids[0])) {
                return ids;
            }
        }
    }
}
