package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link TpaService}'s timeout-expiration callback -- the anonymous {@code BukkitRunnable}
 * {@code startTimeoutTask} schedules via {@code runTaskLater}. The existing
 * {@code TpaServiceMockitoTest} stubs {@code runTaskLater} to return a mock task WITHOUT ever
 * invoking the scheduled {@code Runnable}, so this callback's body has never run under test. This
 * class captures the scheduled runnable and invokes it directly (the {@code MailServiceTest}
 * idiom from UltiMail, the worked example this ecosystem's scheduler-callback convention points
 * to), which is what lets both directions of the callback's still-pending-request check -- the
 * plan's one classified-behavioural decision here -- actually run.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("TpaService timeout-task callback Tests")
class TpaServiceTimeoutTaskTest {

    private TpaService tpaService;
    private EssentialsConfig config;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();
        Plugin mockBukkitPlugin = mock(Plugin.class);

        scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        BukkitTask mockTask = mock(BukkitTask.class);
        lenient().when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong()))
                .thenReturn(mockTask);

        tpaService = new TpaService();
        EssentialsTestHelper.setField(tpaService, "config", config);
        EssentialsTestHelper.setField(tpaService, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(tpaService, "bukkitPlugin", mockBukkitPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private Player createPlayerInWorld(String name, UUID uuid, World world) {
        Player player = EssentialsTestHelper.createMockPlayer(name, uuid);
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.isOnline()).thenReturn(true);
        return player;
    }

    private Runnable captureScheduledCallback() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(any(Plugin.class), captor.capture(), anyLong());
        return captor.getValue();
    }

    @Test
    @DisplayName("A still-pending request is auto-cancelled and both players are notified on timeout")
    void stillPendingRequestIsCancelledAndBothNotified() {
        World world = EssentialsTestHelper.createMockWorld("world");
        UUID senderUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        Player sender = createPlayerInWorld("Sender", senderUuid, world);
        Player target = createPlayerInWorld("Target", targetUuid, world);
        when(Bukkit.getPlayer(senderUuid)).thenReturn(sender);
        when(Bukkit.getPlayer(targetUuid)).thenReturn(target);

        tpaService.sendTpaRequest(sender, target);
        Runnable callback = captureScheduledCallback();

        // The request is still active -- nothing accepted/denied it -- so this pins the
        // "request != null" branch's true direction.
        callback.run();

        verify(sender).sendMessage(anyString());
        verify(target).sendMessage(anyString());
        assertThat(tpaService.getRequest(targetUuid)).isNull();
    }

    @Test
    @DisplayName("A request already resolved before the timeout fires produces no notification")
    void alreadyResolvedRequestProducesNoNotification() {
        World world = EssentialsTestHelper.createMockWorld("world");
        UUID senderUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        Player sender = createPlayerInWorld("Sender", senderUuid, world);
        Player target = createPlayerInWorld("Target", targetUuid, world);
        when(Bukkit.getPlayer(senderUuid)).thenReturn(sender);
        when(Bukkit.getPlayer(targetUuid)).thenReturn(target);

        tpaService.sendTpaRequest(sender, target);
        Runnable callback = captureScheduledCallback();

        // Resolve the request (e.g. the target accepted/denied it) BEFORE the timeout callback
        // ever runs -- pinning the "request == null" branch's false-outcome direction.
        tpaService.cancelRequest(targetUuid);

        callback.run();

        verify(sender, never()).sendMessage(anyString());
        verify(target, never()).sendMessage(anyString());
    }
}
