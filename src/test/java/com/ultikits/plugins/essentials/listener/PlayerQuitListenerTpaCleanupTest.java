package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A player who quits with a teleport request pending -- as its sender or as its target -- has that
 * request cleared at once, through the module's quit listener (UltiKits/UltiEssentials#30).
 * <p>
 * {@code TpaService#onPlayerQuit} always existed for this and nothing called it, so the request
 * stayed pending until its timeout task fired, up to {@code features.tpa.timeout} seconds later
 * (30 by default), and until then the target refused every new request as busy.
 * <p>
 * Each case drives the real {@link TpaService} through the real {@link PlayerQuitListener}, and each
 * keeps a second, unrelated request between two players who stay online as a control: the cleanup
 * must remove only the quitter's requests.
 * <p>
 * 玩家退出时立即清除其作为发送者或目标的待处理传送请求。
 */
@DisplayName("Quitting clears the quitter's pending /tpa requests (UltiKits/UltiEssentials#30)")
class PlayerQuitListenerTpaCleanupTest {

    private final List<BukkitTask> timeoutTasks = new ArrayList<>();
    private TpaService tpaService;
    private PlayerQuitListener listener;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        lenient().when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong()))
                .thenAnswer(inv -> {
                    BukkitTask task = mock(BukkitTask.class);
                    timeoutTasks.add(task);
                    return task;
                });

        tpaService = new TpaService();
        EssentialsTestHelper.setField(tpaService, "config", new EssentialsConfig());
        EssentialsTestHelper.setField(tpaService, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(tpaService, "bukkitPlugin", mock(Plugin.class));

        listener = new PlayerQuitListener();
        EssentialsTestHelper.setField(listener, "tpaService", tpaService);

        world = EssentialsTestHelper.createMockWorld("world");
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private Player online(String name) {
        Player player = EssentialsTestHelper.createMockPlayer(name, UUID.randomUUID());
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.isOnline()).thenReturn(true);
        return player;
    }

    @Test
    @DisplayName("The target quits: the request to them is cleared and its timeout task cancelled; an unrelated request stays")
    void targetQuittingClearsTheRequestToThem() {
        Player sender = online("Sender");
        Player target = online("Target");
        Player otherSender = online("OtherSender");
        Player otherTarget = online("OtherTarget");
        assertThat(tpaService.sendTpaRequest(sender, target)).isEqualTo(TpaService.TpaResult.SENT);
        assertThat(tpaService.sendTpaRequest(otherSender, otherTarget)).isEqualTo(TpaService.TpaResult.SENT);

        listener.onPlayerQuit(new PlayerQuitEvent(target, "left"));

        assertThat(tpaService.getRequest(target.getUniqueId())).isNull();
        verify(timeoutTasks.get(0)).cancel();
        assertThat(tpaService.getRequest(otherTarget.getUniqueId())).isNotNull();
        verify(timeoutTasks.get(1), never()).cancel();
    }

    @Test
    @DisplayName("The sender quits: their request is cleared, so its target can take a new one at once; an unrelated request stays")
    void senderQuittingFreesTheTarget() {
        Player sender = online("Sender");
        Player target = online("Target");
        Player otherSender = online("OtherSender");
        Player otherTarget = online("OtherTarget");
        Player latecomer = online("Latecomer");
        assertThat(tpaService.sendTpaRequest(sender, target)).isEqualTo(TpaService.TpaResult.SENT);
        assertThat(tpaService.sendTpaRequest(otherSender, otherTarget)).isEqualTo(TpaService.TpaResult.SENT);

        listener.onPlayerQuit(new PlayerQuitEvent(sender, "left"));

        assertThat(tpaService.getRequest(target.getUniqueId())).isNull();
        verify(timeoutTasks.get(0)).cancel();
        // The observable consequence the issue describes: before the fix the target stayed
        // "busy" until the timeout, refusing this request with TARGET_BUSY.
        assertThat(tpaService.sendTpaRequest(latecomer, target)).isEqualTo(TpaService.TpaResult.SENT);
        assertThat(tpaService.getRequest(otherTarget.getUniqueId())).isNotNull();
        verify(timeoutTasks.get(1), never()).cancel();
    }

    @Test
    @DisplayName("Control: a player with no pending request quitting leaves every request in place")
    void bystanderQuittingLeavesRequestsAlone() {
        Player sender = online("Sender");
        Player target = online("Target");
        Player bystander = online("Bystander");
        assertThat(tpaService.sendTpaRequest(sender, target)).isEqualTo(TpaService.TpaResult.SENT);

        listener.onPlayerQuit(new PlayerQuitEvent(bystander, "left"));

        assertThat(tpaService.getRequest(target.getUniqueId())).isNotNull();
        verify(timeoutTasks.get(0), never()).cancel();
    }
}
