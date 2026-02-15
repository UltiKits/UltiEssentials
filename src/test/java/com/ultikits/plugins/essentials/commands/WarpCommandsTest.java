package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.service.WarpService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Warp Commands Tests")
class WarpCommandsTest {

    private WarpService warpService;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        warpService = mock(WarpService.class);
        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("WarpCommand")
    class WarpCommandTests {

        private WarpCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new WarpCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "warpService", warpService);
        }

        @Test
        @DisplayName("Should teleport to warp - success")
        void shouldTeleportToWarpSuccess() {
            when(warpService.teleportToWarp(player, "spawn")).thenReturn(TeleportResult.SUCCESS);

            command.warp(player, "spawn");

            verify(warpService).teleportToWarp(player, "spawn");
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle warmup started")
        void shouldHandleWarmupStarted() {
            when(warpService.teleportToWarp(player, "spawn")).thenReturn(TeleportResult.WARMUP_STARTED);

            command.warp(player, "spawn");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle warp not found")
        void shouldHandleNotFound() {
            when(warpService.teleportToWarp(player, "missing")).thenReturn(TeleportResult.NOT_FOUND);

            command.warp(player, "missing");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle world not found")
        void shouldHandleWorldNotFound() {
            when(warpService.teleportToWarp(player, "deleted")).thenReturn(TeleportResult.WORLD_NOT_FOUND);

            command.warp(player, "deleted");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle no permission")
        void shouldHandleNoPermission() {
            when(warpService.teleportToWarp(player, "vip")).thenReturn(TeleportResult.NO_PERMISSION);

            command.warp(player, "vip");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle already teleporting")
        void shouldHandleAlreadyTeleporting() {
            when(warpService.teleportToWarp(player, "spawn")).thenReturn(TeleportResult.ALREADY_TELEPORTING);

            command.warp(player, "spawn");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled")
        void shouldHandleDisabled() {
            when(warpService.teleportToWarp(player, "spawn")).thenReturn(TeleportResult.DISABLED);

            command.warp(player, "spawn");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(1)).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("SetWarpCommand")
    class SetWarpCommandTests {

        private SetWarpCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new SetWarpCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "warpService", warpService);
        }

        @Test
        @DisplayName("Should create warp without permission")
        void shouldCreateWarpWithoutPermission() {
            when(warpService.createWarp(eq("spawn"), any(), eq(playerUuid), isNull()))
                    .thenReturn(WarpService.WarpResult.CREATED);

            command.setWarp(player, "spawn");

            verify(warpService).createWarp(eq("spawn"), any(), eq(playerUuid), isNull());
        }

        @Test
        @DisplayName("Should create warp with permission - created")
        void shouldCreateWarpWithPermissionCreated() {
            when(warpService.createWarp(eq("vip"), any(), eq(playerUuid), eq("vip.warp")))
                    .thenReturn(WarpService.WarpResult.CREATED);

            command.setWarpWithPermission(player, "vip", "vip.warp");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should create warp without permission - created message")
        void shouldCreateWarpCreatedMessage() {
            when(warpService.createWarp(eq("spawn"), any(), eq(playerUuid), isNull()))
                    .thenReturn(WarpService.WarpResult.CREATED);

            command.setWarpWithPermission(player, "spawn", null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle already exists")
        void shouldHandleAlreadyExists() {
            when(warpService.createWarp(eq("spawn"), any(), eq(playerUuid), isNull()))
                    .thenReturn(WarpService.WarpResult.ALREADY_EXISTS);

            command.setWarpWithPermission(player, "spawn", null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle invalid name")
        void shouldHandleInvalidName() {
            when(warpService.createWarp(eq(""), any(), eq(playerUuid), isNull()))
                    .thenReturn(WarpService.WarpResult.INVALID_NAME);

            command.setWarpWithPermission(player, "", null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled")
        void shouldHandleDisabled() {
            when(warpService.createWarp(eq("spawn"), any(), eq(playerUuid), isNull()))
                    .thenReturn(WarpService.WarpResult.DISABLED);

            command.setWarpWithPermission(player, "spawn", null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(1)).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("DelWarpCommand")
    class DelWarpCommandTests {

        private DelWarpCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new DelWarpCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "warpService", warpService);
        }

        @Test
        @DisplayName("Should delete warp successfully")
        void shouldDeleteWarpSuccess() {
            when(warpService.deleteWarp("spawn")).thenReturn(true);

            command.delWarp(player, "spawn");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send message when warp not found")
        void shouldSendMessageWhenNotFound() {
            when(warpService.deleteWarp("missing")).thenReturn(false);

            command.delWarp(player, "missing");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(1)).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("WarpsCommand")
    class WarpsCommandTests {

        private WarpsCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new WarpsCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "warpService", warpService);
        }

        @Test
        @DisplayName("Should list accessible warps")
        void shouldListAccessibleWarps() {
            List<WarpData> warps = Arrays.asList(
                WarpData.builder().uuid(UUID.randomUUID()).name("spawn")
                    .world("world").x(0).y(64).z(0).build(),
                WarpData.builder().uuid(UUID.randomUUID()).name("shop")
                    .world("world").x(100).y(64).z(200).permission("shop.warp").build()
            );
            when(warpService.getAccessibleWarps(player)).thenReturn(warps);

            command.listWarps(player);

            // header + 2 warps + footer
            verify(player, atLeast(3)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show message when no warps")
        void shouldShowMessageWhenNoWarps() {
            when(warpService.getAccessibleWarps(player)).thenReturn(Collections.emptyList());

            command.listWarps(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show warp with permission indicator")
        void shouldShowWarpWithPermissionIndicator() {
            List<WarpData> warps = Collections.singletonList(
                WarpData.builder().uuid(UUID.randomUUID()).name("vip")
                    .world("world").x(0).y(64).z(0).permission("vip.warp").build()
            );
            when(warpService.getAccessibleWarps(player)).thenReturn(warps);

            command.listWarps(player);

            verify(player, atLeast(2)).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(1)).sendMessage(anyString());
        }
    }
}
