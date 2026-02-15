package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.service.ChestLockService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Lock Commands Tests")
class LockCommandsTest {

    private ChestLockService chestLockService;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        chestLockService = mock(ChestLockService.class);
        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("LockCommand")
    class LockCommandTests {

        private LockCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new LockCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "chestLockService", chestLockService);
        }

        @Test
        @DisplayName("Should lock block - success")
        void shouldLockBlockSuccess() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.lockBlock(block, player)).thenReturn(ChestLockService.LockResult.SUCCESS);

            command.lock(player);

            verify(chestLockService).lockBlock(block, player);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle no target block")
        void shouldHandleNoTargetBlock() {
            when(player.getTargetBlockExact(5)).thenReturn(null);

            command.lock(player);

            verify(chestLockService, never()).lockBlock(any(), any());
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle not lockable")
        void shouldHandleNotLockable() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.lockBlock(block, player)).thenReturn(ChestLockService.LockResult.NOT_LOCKABLE);

            command.lock(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle already locked")
        void shouldHandleAlreadyLocked() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.lockBlock(block, player)).thenReturn(ChestLockService.LockResult.ALREADY_LOCKED);

            command.lock(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle already locked by you")
        void shouldHandleAlreadyLockedByYou() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.lockBlock(block, player)).thenReturn(ChestLockService.LockResult.ALREADY_LOCKED_BY_YOU);

            command.lock(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.lockBlock(block, player)).thenReturn(ChestLockService.LockResult.DISABLED);

            command.lock(player);

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
    @DisplayName("UnlockCommand")
    class UnlockCommandTests {

        private UnlockCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new UnlockCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "chestLockService", chestLockService);
        }

        @Test
        @DisplayName("Should unlock block - success")
        void shouldUnlockBlockSuccess() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.unlockBlock(block, player)).thenReturn(ChestLockService.UnlockResult.SUCCESS);

            command.unlock(player);

            verify(chestLockService).unlockBlock(block, player);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle no target block")
        void shouldHandleNoTargetBlock() {
            when(player.getTargetBlockExact(5)).thenReturn(null);

            command.unlock(player);

            verify(chestLockService, never()).unlockBlock(any(), any());
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle not locked")
        void shouldHandleNotLocked() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.unlockBlock(block, player)).thenReturn(ChestLockService.UnlockResult.NOT_LOCKED);

            command.unlock(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle not owner")
        void shouldHandleNotOwner() {
            Block block = mock(Block.class);
            when(player.getTargetBlockExact(5)).thenReturn(block);
            when(chestLockService.unlockBlock(block, player)).thenReturn(ChestLockService.UnlockResult.NOT_OWNER);

            command.unlock(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show lock info")
        void shouldShowLockInfo() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 100, 64, 200);
            when(block.getLocation()).thenReturn(loc);
            when(player.getTargetBlockExact(5)).thenReturn(block);

            ChestLockData lockData = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .ownerName("Owner")
                    .world("world")
                    .x(100).y(64).z(200)
                    .build();
            when(chestLockService.getLock(loc)).thenReturn(lockData);

            command.info(player);

            verify(player, atLeast(2)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show not locked for info")
        void shouldShowNotLockedForInfo() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 100, 64, 200);
            when(block.getLocation()).thenReturn(loc);
            when(player.getTargetBlockExact(5)).thenReturn(block);

            when(chestLockService.getLock(loc)).thenReturn(null);

            command.info(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle no target for info")
        void shouldHandleNoTargetForInfo() {
            when(player.getTargetBlockExact(5)).thenReturn(null);

            command.info(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("handleHelp should send usage messages")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player, atLeast(2)).sendMessage(anyString());
        }
    }
}
