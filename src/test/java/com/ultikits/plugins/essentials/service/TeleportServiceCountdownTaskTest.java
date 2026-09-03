package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link TeleportService}'s warmup-countdown callback -- the anonymous
 * {@code BukkitRunnable} {@code startWarmupTeleport} schedules via {@code runTaskTimer}. The
 * existing {@code TeleportServiceMockitoTest} stubs {@code runTaskTimer} to return a mock task
 * WITHOUT ever invoking the scheduled callback, so this callback's body -- the movement-detection
 * cancel, the countdown-reaches-zero teleport, and the success/cancel callback dispatch -- has
 * never run under test. This class captures the scheduled runnable and invokes it directly
 * (the UltiMail {@code MailServiceTest} idiom for scheduler callbacks) to exercise it.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("TeleportService warmup-countdown callback Tests")
class TeleportServiceCountdownTaskTest {

    private TeleportService teleportService;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        Plugin mockBukkitPlugin = mock(Plugin.class);

        scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        BukkitTask mockTask = mock(BukkitTask.class);
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        teleportService = new TeleportService();
        EssentialsTestHelper.setField(teleportService, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(teleportService, "bukkitPlugin", mockBukkitPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private Runnable captureScheduledCallback() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(any(Plugin.class), captor.capture(), anyLong(), anyLong());
        return captor.getValue();
    }

    @Test
    @DisplayName("The countdown reaches zero after its ticks elapse, teleporting the player and invoking onSuccess")
    void countdownReachingZeroTeleportsAndInvokesOnSuccess() {
        World world = EssentialsTestHelper.createMockWorld("world");
        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.isOnline()).thenReturn(true);
        Location target = new Location(world, 10, 64, 10);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        // warmupSeconds=1 schedules the countdown task (0 or below teleports instantly without
        // ever scheduling anything, so 1 is the minimum that reaches this callback at all).
        teleportService.teleport(player, target, 1, false, p -> successCalled.set(true), null);
        Runnable callback = captureScheduledCallback();

        callback.run(); // countdown=1 -> not yet zero: sends the warmup message, decrements
        verify(player, never()).teleport(any(Location.class));
        assertThat(successCalled).isFalse();

        callback.run(); // countdown=0 -> teleports and fires onSuccess
        verify(player).teleport(target);
        assertThat(successCalled).isTrue();
        assertThat(teleportService.isTeleporting(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("A player who moved beyond the threshold with cancelOnMove has the teleport cancelled and onCancel invoked")
    void movingTooFarCancelsWarmupAndInvokesOnCancel() {
        World world = EssentialsTestHelper.createMockWorld("world");
        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        Location start = new Location(world, 0, 64, 0);
        when(player.getLocation()).thenReturn(start);
        when(player.isOnline()).thenReturn(true);
        Location target = new Location(world, 10, 64, 10);
        AtomicBoolean cancelCalled = new AtomicBoolean(false);

        teleportService.teleport(player, target, 5, true, null, p -> cancelCalled.set(true));
        Runnable callback = captureScheduledCallback();

        // Player moves far away before the next tick
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 100));
        callback.run();

        verify(player, never()).teleport(any(Location.class));
        assertThat(cancelCalled).isTrue();
        assertThat(teleportService.isTeleporting(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("A player who stayed within the threshold with cancelOnMove is not cancelled and the countdown continues")
    void stayingWithinThresholdDoesNotCancel() {
        World world = EssentialsTestHelper.createMockWorld("world");
        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        Location start = new Location(world, 0, 64, 0);
        when(player.getLocation()).thenReturn(start);
        when(player.isOnline()).thenReturn(true);
        Location target = new Location(world, 10, 64, 10);

        teleportService.teleport(player, target, 5, true, null, null);
        Runnable callback = captureScheduledCallback();

        // Player barely moved (well within the 1-block-squared threshold)
        when(player.getLocation()).thenReturn(new Location(world, 0.2, 64, 0));
        callback.run();

        verify(player, never()).teleport(any(Location.class));
        assertThat(teleportService.isTeleporting(player.getUniqueId())).isTrue();
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("cancelOnMove=false never checks movement, even for a player who moved far away")
    void cancelOnMoveFalseIgnoresMovement() {
        World world = EssentialsTestHelper.createMockWorld("world");
        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.isOnline()).thenReturn(true);
        Location target = new Location(world, 10, 64, 10);
        AtomicBoolean cancelCalled = new AtomicBoolean(false);

        teleportService.teleport(player, target, 5, false, null, p -> cancelCalled.set(true));
        Runnable callback = captureScheduledCallback();

        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 100));
        callback.run();

        assertThat(cancelCalled).isFalse();
        assertThat(teleportService.isTeleporting(player.getUniqueId())).isTrue();
    }

    @Test
    @DisplayName("A player who went offline mid-warmup has the pending teleport cleaned up without teleporting")
    void offlinePlayerCleansUpWithoutTeleporting() {
        World world = EssentialsTestHelper.createMockWorld("world");
        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.isOnline()).thenReturn(true);
        Location target = new Location(world, 10, 64, 10);

        teleportService.teleport(player, target, 5, false, null, null);
        Runnable callback = captureScheduledCallback();

        when(player.isOnline()).thenReturn(false);
        callback.run();

        verify(player, never()).teleport(any(Location.class));
        assertThat(teleportService.isTeleporting(player.getUniqueId())).isFalse();
    }
}
