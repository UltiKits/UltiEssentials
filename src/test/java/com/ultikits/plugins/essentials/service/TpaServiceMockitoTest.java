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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for TpaService.
 * <p>
 * TPA 传送请求服务测试。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("TpaService Tests (Mockito)")
class TpaServiceMockitoTest {

    private TpaService tpaService;
    private EssentialsConfig config;
    private Plugin mockBukkitPlugin;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();

        // Mock BukkitPlugin for scheduler
        mockBukkitPlugin = mock(Plugin.class);

        // Mock scheduler for timeout tasks
        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
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

    @Nested
    @DisplayName("sendTpaRequest")
    class SendTpaRequestTests {

        @Test
        @DisplayName("Should send TPA request successfully")
        void shouldSendTpaRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player sender = createPlayerInWorld("Sender", UUID.randomUUID(), world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            TpaService.TpaResult result = tpaService.sendTpaRequest(sender, target);

            assertThat(result).isEqualTo(TpaService.TpaResult.SENT);
        }

        @Test
        @DisplayName("Should reject self request")
        void shouldRejectSelfRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID uuid = UUID.randomUUID();
            Player player = createPlayerInWorld("Steve", uuid, world);

            // sender.equals(target) is true when same object reference
            TpaService.TpaResult result = tpaService.sendTpaRequest(player, player);

            assertThat(result).isEqualTo(TpaService.TpaResult.SELF_REQUEST);
        }

        @Test
        @DisplayName("Should reject when disabled")
        void shouldRejectWhenDisabled() {
            config.setTpaEnabled(false);
            Player sender = EssentialsTestHelper.createMockPlayer("Sender", UUID.randomUUID());
            Player target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());

            TpaService.TpaResult result = tpaService.sendTpaRequest(sender, target);

            assertThat(result).isEqualTo(TpaService.TpaResult.DISABLED);
        }

        @Test
        @DisplayName("Should reject cross-world when disabled")
        void shouldRejectCrossWorld() {
            config.setTpaAllowCrossWorld(false);
            World world1 = EssentialsTestHelper.createMockWorld("world");
            World world2 = EssentialsTestHelper.createMockWorld("nether");
            Player sender = createPlayerInWorld("Sender", UUID.randomUUID(), world1);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world2);

            TpaService.TpaResult result = tpaService.sendTpaRequest(sender, target);

            assertThat(result).isEqualTo(TpaService.TpaResult.CROSS_WORLD_DISABLED);
        }

        @Test
        @DisplayName("Should reject when target is busy")
        void shouldRejectWhenTargetBusy() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player sender1 = createPlayerInWorld("Sender1", UUID.randomUUID(), world);
            Player sender2 = createPlayerInWorld("Sender2", UUID.randomUUID(), world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            tpaService.sendTpaRequest(sender1, target);
            TpaService.TpaResult result = tpaService.sendTpaRequest(sender2, target);

            assertThat(result).isEqualTo(TpaService.TpaResult.TARGET_BUSY);
        }

        @Test
        @DisplayName("Should reject when on cooldown")
        void shouldRejectWhenOnCooldown() throws Exception {
            config.setTpaCooldown(60); // 60 second cooldown
            World world = EssentialsTestHelper.createMockWorld("world");
            Player sender = createPlayerInWorld("Sender", UUID.randomUUID(), world);
            Player target1 = createPlayerInWorld("Target1", UUID.randomUUID(), world);
            Player target2 = createPlayerInWorld("Target2", UUID.randomUUID(), world);

            tpaService.sendTpaRequest(sender, target1);
            TpaService.TpaResult result = tpaService.sendTpaRequest(sender, target2);

            assertThat(result).isEqualTo(TpaService.TpaResult.ON_COOLDOWN);
        }
    }

    @Nested
    @DisplayName("sendTpaHereRequest")
    class SendTpaHereRequestTests {

        @Test
        @DisplayName("Should send TPA-here request successfully")
        void shouldSendTpaHereRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Player sender = createPlayerInWorld("Sender", UUID.randomUUID(), world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            TpaService.TpaResult result = tpaService.sendTpaHereRequest(sender, target);

            assertThat(result).isEqualTo(TpaService.TpaResult.SENT);
        }
    }

    @Nested
    @DisplayName("acceptRequest")
    class AcceptRequestTests {

        @Test
        @DisplayName("Should accept TPA request and teleport sender to target")
        void shouldAcceptTpaRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            UUID targetUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", targetUuid, world);

            // Register sender with Bukkit
            when(Bukkit.getPlayer(senderUuid)).thenReturn(sender);

            tpaService.sendTpaRequest(sender, target);
            TpaService.TpaResult result = tpaService.acceptRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.ACCEPTED);
            verify(sender).teleport(target.getLocation());
        }

        @Test
        @DisplayName("Should accept TPA-here request and teleport target to sender")
        void shouldAcceptTpaHereRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            UUID targetUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", targetUuid, world);

            when(Bukkit.getPlayer(senderUuid)).thenReturn(sender);

            tpaService.sendTpaHereRequest(sender, target);
            TpaService.TpaResult result = tpaService.acceptRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.ACCEPTED);
            verify(target).teleport(sender.getLocation());
        }

        @Test
        @DisplayName("Should return NO_REQUEST when no pending request")
        void shouldReturnNoRequest() {
            Player target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());

            TpaService.TpaResult result = tpaService.acceptRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.NO_REQUEST);
        }

        @Test
        @DisplayName("Should return SENDER_OFFLINE when sender went offline")
        void shouldReturnSenderOffline() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            tpaService.sendTpaRequest(sender, target);

            // Sender goes offline
            when(Bukkit.getPlayer(senderUuid)).thenReturn(null);

            TpaService.TpaResult result = tpaService.acceptRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.SENDER_OFFLINE);
        }

        @Test
        @DisplayName("Should return DISABLED when tpa is disabled")
        void shouldReturnDisabled() {
            config.setTpaEnabled(false);
            Player target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());

            TpaService.TpaResult result = tpaService.acceptRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.DISABLED);
        }
    }

    @Nested
    @DisplayName("denyRequest")
    class DenyRequestTests {

        @Test
        @DisplayName("Should deny request successfully")
        void shouldDenyRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            when(Bukkit.getPlayer(senderUuid)).thenReturn(sender);

            tpaService.sendTpaRequest(sender, target);
            TpaService.TpaResult result = tpaService.denyRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.DENIED);
            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should return NO_REQUEST when no pending request")
        void shouldReturnNoRequest() {
            Player target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());

            TpaService.TpaResult result = tpaService.denyRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.NO_REQUEST);
        }

        @Test
        @DisplayName("Should handle sender offline during deny")
        void shouldHandleSenderOffline() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            tpaService.sendTpaRequest(sender, target);
            when(Bukkit.getPlayer(senderUuid)).thenReturn(null);

            TpaService.TpaResult result = tpaService.denyRequest(target);

            assertThat(result).isEqualTo(TpaService.TpaResult.DENIED);
        }
    }

    @Nested
    @DisplayName("cancelRequest")
    class CancelRequestTests {

        @Test
        @DisplayName("Should cancel existing request")
        void shouldCancelExistingRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID targetUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", UUID.randomUUID(), world);
            Player target = createPlayerInWorld("Target", targetUuid, world);

            tpaService.sendTpaRequest(sender, target);
            tpaService.cancelRequest(targetUuid);

            assertThat(tpaService.getRequest(targetUuid)).isNull();
        }

        @Test
        @DisplayName("Should not throw when cancelling non-existing request")
        void shouldNotThrowForNonExisting() {
            tpaService.cancelRequest(UUID.randomUUID());
            // Should not throw
        }
    }

    @Nested
    @DisplayName("getRequest")
    class GetRequestTests {

        @Test
        @DisplayName("Should return request when exists")
        void shouldReturnRequest() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID targetUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", UUID.randomUUID(), world);
            Player target = createPlayerInWorld("Target", targetUuid, world);

            tpaService.sendTpaRequest(sender, target);
            TpaService.TpaRequest request = tpaService.getRequest(targetUuid);

            assertThat(request).isNotNull();
            assertThat(request.getSenderUuid()).isEqualTo(sender.getUniqueId());
            assertThat(request.getTargetUuid()).isEqualTo(targetUuid);
            assertThat(request.getType()).isEqualTo(TpaService.TpaType.TPA);
            assertThat(request.getTimestamp()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should return null when no request")
        void shouldReturnNull() {
            assertThat(tpaService.getRequest(UUID.randomUUID())).isNull();
        }
    }

    @Nested
    @DisplayName("isOnCooldown")
    class CooldownTests {

        @Test
        @DisplayName("Should not be on cooldown initially")
        void shouldNotBeOnCooldownInitially() {
            assertThat(tpaService.isOnCooldown(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("Should be on cooldown after sending request")
        void shouldBeOnCooldownAfterRequest() {
            config.setTpaCooldown(60);
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            tpaService.sendTpaRequest(sender, target);

            assertThat(tpaService.isOnCooldown(senderUuid)).isTrue();
        }
    }

    @Nested
    @DisplayName("getRemainingCooldown")
    class GetRemainingCooldownTests {

        @Test
        @DisplayName("Should return 0 when no cooldown")
        void shouldReturnZeroWhenNoCooldown() {
            assertThat(tpaService.getRemainingCooldown(UUID.randomUUID())).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return positive value when on cooldown")
        void shouldReturnPositiveWhenOnCooldown() {
            config.setTpaCooldown(60);
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", UUID.randomUUID(), world);

            tpaService.sendTpaRequest(sender, target);

            assertThat(tpaService.getRemainingCooldown(senderUuid)).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("onPlayerQuit")
    class OnPlayerQuitTests {

        @Test
        @DisplayName("Should cancel request when target quits")
        void shouldCancelWhenTargetQuits() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID targetUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", UUID.randomUUID(), world);
            Player target = createPlayerInWorld("Target", targetUuid, world);

            tpaService.sendTpaRequest(sender, target);
            tpaService.onPlayerQuit(targetUuid);

            assertThat(tpaService.getRequest(targetUuid)).isNull();
        }

        @Test
        @DisplayName("Should cancel request when sender quits")
        void shouldCancelWhenSenderQuits() {
            World world = EssentialsTestHelper.createMockWorld("world");
            UUID senderUuid = UUID.randomUUID();
            UUID targetUuid = UUID.randomUUID();
            Player sender = createPlayerInWorld("Sender", senderUuid, world);
            Player target = createPlayerInWorld("Target", targetUuid, world);

            tpaService.sendTpaRequest(sender, target);
            tpaService.onPlayerQuit(senderUuid);

            assertThat(tpaService.getRequest(targetUuid)).isNull();
        }
    }

    @Nested
    @DisplayName("TpaRequest")
    class TpaRequestTests {

        @Test
        @DisplayName("Should create request with all fields")
        void shouldCreateRequest() {
            UUID senderUuid = UUID.randomUUID();
            UUID targetUuid = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();

            TpaService.TpaRequest request = new TpaService.TpaRequest(
                    senderUuid, targetUuid, TpaService.TpaType.TPA, timestamp);

            assertThat(request.getSenderUuid()).isEqualTo(senderUuid);
            assertThat(request.getTargetUuid()).isEqualTo(targetUuid);
            assertThat(request.getType()).isEqualTo(TpaService.TpaType.TPA);
            assertThat(request.getTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("Should create TPA_HERE request")
        void shouldCreateTpaHereRequest() {
            TpaService.TpaRequest request = new TpaService.TpaRequest(
                    UUID.randomUUID(), UUID.randomUUID(), TpaService.TpaType.TPA_HERE, 0);

            assertThat(request.getType()).isEqualTo(TpaService.TpaType.TPA_HERE);
        }
    }

    @Nested
    @DisplayName("TpaType enum")
    class TpaTypeTests {

        @Test
        @DisplayName("Should have two values")
        void shouldHaveTwoValues() {
            assertThat(TpaService.TpaType.values()).hasSize(2);
        }

        @Test
        @DisplayName("Should have TPA and TPA_HERE")
        void shouldHaveTpaAndTpaHere() {
            assertThat(TpaService.TpaType.valueOf("TPA")).isEqualTo(TpaService.TpaType.TPA);
            assertThat(TpaService.TpaType.valueOf("TPA_HERE")).isEqualTo(TpaService.TpaType.TPA_HERE);
        }
    }

    @Nested
    @DisplayName("TpaResult enum")
    class TpaResultTests {

        @Test
        @DisplayName("Should have all result values")
        void shouldHaveAllValues() {
            assertThat(TpaService.TpaResult.values()).hasSize(10);
        }
    }
}
