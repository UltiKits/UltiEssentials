package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEssentials#28: {@link ScoreboardListener#onPlayerJoin} checks
 * {@code features.scoreboard.auto-enable} at join time but enables the sidebar 20 ticks later. A
 * {@code /ul reload} inside that second that turns {@code auto-enable} off must still win, because the
 * documentation says players who join after a reload follow the reloaded value.
 * <p>
 * 验证玩家加入后延迟启用侧边栏前，若重载关闭了 auto-enable，则不再启用。
 */
@DisplayName("ScoreboardListener re-checks auto-enable when its delayed enable runs (UltiKits/UltiEssentials#28)")
class ScoreboardListenerDelayedEnableTest {

    private EssentialsConfig config;
    private ScoreboardService scoreboardService;
    private ScoreboardListener listener;
    private Player player;
    private PlayerJoinEvent event;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        config.setScoreboardEnabled(true);
        config.setScoreboardAutoEnable(true);
        scoreboardService = mock(ScoreboardService.class);

        listener = new ScoreboardListener();
        EssentialsTestHelper.setField(listener, "config", config);
        EssentialsTestHelper.setField(listener, "scoreboardService", scoreboardService);
        EssentialsTestHelper.setField(listener, "bukkitPlugin", mock(Plugin.class));

        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("the delayed enable still runs when auto-enable is unchanged")
    void delayedEnableRunsWhenAutoEnableUnchanged() {
        listener.onPlayerJoin(event);

        delayedTask().run();

        verify(scoreboardService).enableScoreboard(player);
    }

    @Test
    @DisplayName("a reload that turns auto-enable off before the delayed enable runs leaves the player without a sidebar")
    void reloadTurningAutoEnableOffCancelsDelayedEnable() {
        listener.onPlayerJoin(event);

        config.setScoreboardAutoEnable(false); // /ul reload re-reads the same config instance in place
        delayedTask().run();

        verify(scoreboardService, never()).enableScoreboard(any(Player.class));
    }

    private Runnable delayedTask() {
        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(any(Plugin.class), task.capture(), eq(20L));
        return task.getValue();
    }
}
