package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TPA Commands Tests")
class TpaCommandsTest {

    private EssentialsConfig config;
    private TpaService tpaService;
    private Player sender;
    private Player target;
    private UUID senderUuid;
    private UUID targetUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        tpaService = mock(TpaService.class);
        senderUuid = UUID.randomUUID();
        targetUuid = UUID.randomUUID();
        sender = EssentialsTestHelper.createMockPlayer("Sender", senderUuid);
        target = EssentialsTestHelper.createMockPlayer("Target", targetUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("TpaCommand")
    class TpaCommandTests {

        private TpaCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new TpaCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "tpaService", tpaService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should send TPA request - sent")
        void shouldSendTpaRequestSent() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Target"))).thenReturn(target);
            when(target.isOnline()).thenReturn(true);

            when(tpaService.sendTpaRequest(sender, target)).thenReturn(TpaService.TpaResult.SENT);

            command.sendTpa(sender, "Target");

            verify(tpaService).sendTpaRequest(sender, target);
            verify(sender, atLeast(1)).sendMessage(anyString());
            verify(target, atLeast(1)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle self request")
        void shouldHandleSelfRequest() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Target"))).thenReturn(target);
            when(target.isOnline()).thenReturn(true);

            when(tpaService.sendTpaRequest(sender, target)).thenReturn(TpaService.TpaResult.SELF_REQUEST);

            command.sendTpa(sender, "Target");

            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle target busy")
        void shouldHandleTargetBusy() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Target"))).thenReturn(target);
            when(target.isOnline()).thenReturn(true);

            when(tpaService.sendTpaRequest(sender, target)).thenReturn(TpaService.TpaResult.TARGET_BUSY);

            command.sendTpa(sender, "Target");

            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle on cooldown")
        void shouldHandleOnCooldown() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Target"))).thenReturn(target);
            when(target.isOnline()).thenReturn(true);

            when(tpaService.sendTpaRequest(sender, target)).thenReturn(TpaService.TpaResult.ON_COOLDOWN);
            when(tpaService.getRemainingCooldown(senderUuid)).thenReturn(30);

            command.sendTpa(sender, "Target");

            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle cross world disabled")
        void shouldHandleCrossWorldDisabled() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Target"))).thenReturn(target);
            when(target.isOnline()).thenReturn(true);

            when(tpaService.sendTpaRequest(sender, target)).thenReturn(TpaService.TpaResult.CROSS_WORLD_DISABLED);

            command.sendTpa(sender, "Target");

            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle player offline")
        void shouldHandlePlayerOffline() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("OfflinePlayer"))).thenReturn(null);

            command.sendTpa(sender, "OfflinePlayer");

            verify(sender).sendMessage(anyString());
            verify(tpaService, never()).sendTpaRequest(any(), any());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabledFeature() {
            config.setTpaEnabled(false);

            command.sendTpa(sender, "Target");

            verify(tpaService, never()).sendTpaRequest(any(), any());
            verify(sender).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("TpaHereCommand")
    class TpaHereCommandTests {

        private TpaHereCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new TpaHereCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "tpaService", tpaService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should send TPA-here request - sent")
        void shouldSendTpaHereRequestSent() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Target"))).thenReturn(target);
            when(target.isOnline()).thenReturn(true);

            when(tpaService.sendTpaHereRequest(sender, target)).thenReturn(TpaService.TpaResult.SENT);

            command.sendTpaHere(sender, "Target");

            verify(tpaService).sendTpaHereRequest(sender, target);
            verify(sender, atLeast(1)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle player offline")
        void shouldHandlePlayerOffline() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Offline"))).thenReturn(null);

            command.sendTpaHere(sender, "Offline");

            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabledFeature() {
            config.setTpaEnabled(false);

            command.sendTpaHere(sender, "Target");

            verify(tpaService, never()).sendTpaHereRequest(any(), any());
        }

        @Test
        @DisplayName("Should handle self request")
        void shouldHandleSelfRequest() {
            Server server = Bukkit.getServer();
            when(server.getPlayer(eq("Target"))).thenReturn(target);
            when(target.isOnline()).thenReturn(true);

            when(tpaService.sendTpaHereRequest(sender, target)).thenReturn(TpaService.TpaResult.SELF_REQUEST);

            command.sendTpaHere(sender, "Target");

            verify(sender).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("TpAcceptCommand")
    class TpAcceptCommandTests {

        private TpAcceptCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new TpAcceptCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "tpaService", tpaService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should accept TPA request - accepted")
        void shouldAcceptTpaRequest() {
            TpaService.TpaRequest request = mock(TpaService.TpaRequest.class);
            when(request.getSenderUuid()).thenReturn(senderUuid);
            when(tpaService.getRequest(targetUuid)).thenReturn(request);
            when(tpaService.acceptRequest(target)).thenReturn(TpaService.TpaResult.ACCEPTED);

            Server server = Bukkit.getServer();
            when(server.getPlayer(eq(senderUuid))).thenReturn(sender);
            when(sender.isOnline()).thenReturn(true);

            command.acceptTpa(target);

            verify(target).sendMessage(anyString());
            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle no request")
        void shouldHandleNoRequest() {
            when(tpaService.getRequest(targetUuid)).thenReturn(null);

            command.acceptTpa(target);

            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle sender offline after accept")
        void shouldHandleSenderOffline() {
            TpaService.TpaRequest request = mock(TpaService.TpaRequest.class);
            when(request.getSenderUuid()).thenReturn(senderUuid);
            when(tpaService.getRequest(targetUuid)).thenReturn(request);
            when(tpaService.acceptRequest(target)).thenReturn(TpaService.TpaResult.SENDER_OFFLINE);

            command.acceptTpa(target);

            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle no request result")
        void shouldHandleNoRequestResult() {
            TpaService.TpaRequest request = mock(TpaService.TpaRequest.class);
            when(tpaService.getRequest(targetUuid)).thenReturn(request);
            when(tpaService.acceptRequest(target)).thenReturn(TpaService.TpaResult.NO_REQUEST);

            command.acceptTpa(target);

            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabledFeature() {
            config.setTpaEnabled(false);

            command.acceptTpa(target);

            verify(tpaService, never()).acceptRequest(any());
        }
    }

    @Nested
    @DisplayName("TpDenyCommand")
    class TpDenyCommandTests {

        private TpDenyCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new TpDenyCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "tpaService", tpaService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should deny TPA request")
        void shouldDenyTpaRequest() {
            TpaService.TpaRequest request = mock(TpaService.TpaRequest.class);
            when(tpaService.getRequest(targetUuid)).thenReturn(request);
            when(tpaService.denyRequest(target)).thenReturn(TpaService.TpaResult.DENIED);

            command.denyTpa(target);

            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle no request")
        void shouldHandleNoRequest() {
            when(tpaService.getRequest(targetUuid)).thenReturn(null);

            command.denyTpa(target);

            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabledFeature() {
            config.setTpaEnabled(false);

            command.denyTpa(target);

            verify(tpaService, never()).denyRequest(any());
        }
    }
}
