package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.service.ChestLockService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChestLockListener Tests")
class ChestLockListenerTest {

    private ChestLockListener listener;
    private EssentialsConfig config;
    private ChestLockService chestLockService;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        chestLockService = mock(ChestLockService.class);

        listener = new ChestLockListener();
        EssentialsTestHelper.setField(listener, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(listener, "config", config);
        EssentialsTestHelper.setField(listener, "chestLockService", chestLockService);

        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("onPlayerInteract")
    class PlayerInteractTests {

        @Test
        @DisplayName("Should cancel interaction when player cannot access locked container")
        void shouldCancelWhenCantAccess() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getType()).thenReturn(Material.CHEST);
            when(block.getLocation()).thenReturn(loc);

            when(chestLockService.isLockable(Material.CHEST)).thenReturn(true);
            // Keyed on the container, not the clicked block: a double chest is one shared inventory
            // behind two blocks.
            when(chestLockService.canAccess(block, player)).thenReturn(false);

            ChestLockData lockData = ChestLockData.builder()
                    .ownerName("OtherPlayer")
                    .build();
            when(chestLockService.denyingLock(block, player)).thenReturn(lockData);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteract(event);

            verify(event).setCancelled(true);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should allow interaction when player can access")
        void shouldAllowWhenCanAccess() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getType()).thenReturn(Material.CHEST);
            when(block.getLocation()).thenReturn(loc);

            when(chestLockService.isLockable(Material.CHEST)).thenReturn(true);
            when(chestLockService.canAccess(block, player)).thenReturn(true);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should skip when block is null")
        void shouldSkipWhenBlockNull() {
            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(null);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should skip when block is not lockable")
        void shouldSkipWhenNotLockable() {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(Material.STONE);
            when(chestLockService.isLockable(Material.STONE)).thenReturn(false);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should skip when feature is disabled")
        void shouldSkipWhenDisabled() {
            config.setChestLockEnabled(false);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);

            listener.onPlayerInteract(event);

            // Typed explicitly: canAccess is overloaded on Block and Location since the interact
            // check moved to the container, and any() matches both.
            verify(chestLockService, never()).canAccess(any(org.bukkit.block.Block.class), any());
        }

        @Test
        @DisplayName("Should refuse a left click with the break wording, not the open wording")
        void shouldRefuseLeftClickWithBreakWording() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getType()).thenReturn(Material.CHEST);
            when(block.getLocation()).thenReturn(loc);

            when(chestLockService.isLockable(Material.CHEST)).thenReturn(true);
            when(chestLockService.canAccess(block, player)).thenReturn(false);

            ChestLockData lockData = ChestLockData.builder()
                    .ownerName("OtherPlayer")
                    .build();
            when(chestLockService.denyingLock(block, player)).thenReturn(lockData);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);
            when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);

            listener.onPlayerInteract(event);

            // The refusal itself is the assertion that matters, and it is asserted first: a left
            // click on someone else's locked container must still be cancelled here. The wording
            // is the declared-but-undelivered half, and it is checked second so that a change
            // which bought the wording by letting the click through fails on this line rather
            // than passing on the next.
            verify(event).setCancelled(true);
            // Exact equality, not contains(): the open wording is a strict prefix of the break
            // wording, so contains() would pass on the very message this row exists to reject.
            verify(player).sendMessage("§c该容器被 §fOtherPlayer §c锁定，无法破坏");
        }

        @Test
        @DisplayName("Should keep the open wording for a right click")
        void shouldKeepOpenWordingOnRightClick() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getType()).thenReturn(Material.CHEST);
            when(block.getLocation()).thenReturn(loc);

            when(chestLockService.isLockable(Material.CHEST)).thenReturn(true);
            when(chestLockService.canAccess(block, player)).thenReturn(false);

            ChestLockData lockData = ChestLockData.builder()
                    .ownerName("OtherPlayer")
                    .build();
            when(chestLockService.denyingLock(block, player)).thenReturn(lockData);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);
            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);

            listener.onPlayerInteract(event);

            verify(event).setCancelled(true);
            verify(player).sendMessage("§c该容器被 §fOtherPlayer §c锁定");
        }
    }

    @Nested
    @DisplayName("onBlockBreak")
    class BlockBreakTests {

        @Test
        @DisplayName("Should cancel break when not owner or admin")
        void shouldCancelWhenNotOwner() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getLocation()).thenReturn(loc);

            ChestLockData lockData = ChestLockData.builder()
                    .ownerUuid("other-uuid")
                    .ownerName("OtherPlayer")
                    .build();
            // Container-scoped: breaking asks for every record protecting the container this block
            // belongs to, not just this block's own. Keyed on the block, a stranger could break the
            // unrecorded half of a locked double chest and collect the drops.
            when(chestLockService.locksProtecting(block))
                    .thenReturn(Collections.singletonList(lockData));
            when(player.hasPermission("ultiessentials.lock.admin")).thenReturn(false);

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("Should allow break and remove lock when owner")
        void shouldAllowWhenOwner() {
            UUID ownerUuid = UUID.randomUUID();
            Player owner = EssentialsTestHelper.createMockPlayer("Owner", ownerUuid);

            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getLocation()).thenReturn(loc);

            ChestLockData lockData = ChestLockData.builder()
                    .ownerUuid(ownerUuid.toString())
                    .ownerName("Owner")
                    .build();
            when(chestLockService.locksProtecting(block))
                    .thenReturn(Collections.singletonList(lockData));

            BlockBreakEvent event = new BlockBreakEvent(block, owner);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
            verify(chestLockService).onBlockBreak(loc);
        }

        @Test
        @DisplayName("Should allow break when admin")
        void shouldAllowWhenAdmin() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getLocation()).thenReturn(loc);

            ChestLockData lockData = ChestLockData.builder()
                    .ownerUuid("other-uuid")
                    .ownerName("OtherPlayer")
                    .build();
            when(chestLockService.locksProtecting(block))
                    .thenReturn(Collections.singletonList(lockData));
            when(player.hasPermission("ultiessentials.lock.admin")).thenReturn(true);

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
            verify(chestLockService).onBlockBreak(loc);
        }

        @Test
        @DisplayName("Should skip when block is not locked")
        void shouldSkipWhenNotLocked() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getLocation()).thenReturn(loc);

            when(chestLockService.locksProtecting(block)).thenReturn(Collections.emptyList());

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Should skip when feature is disabled")
        void shouldSkipWhenDisabled() {
            config.setChestLockEnabled(false);

            Block block = mock(Block.class);
            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            verify(chestLockService, never()).locksProtecting(any());
        }

        @Test
        @DisplayName("Should skip when lock is null")
        void shouldSkipWhenLockNull() {
            Block block = mock(Block.class);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 10, 64, 20);
            when(block.getLocation()).thenReturn(loc);

            when(chestLockService.isLocked(loc)).thenReturn(true);
            when(chestLockService.getLock(loc)).thenReturn(null);

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
        }
    }

    @Nested
    @DisplayName("onEntityExplode")
    class EntityExplodeTests {

        @Test
        @DisplayName("Should remove locked blocks from explosion")
        void shouldRemoveLockedBlocks() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc1 = new Location(world, 1, 64, 1);
            Location loc2 = new Location(world, 2, 64, 2);

            Block lockedBlock = mock(Block.class);
            when(lockedBlock.getLocation()).thenReturn(loc1);
            Block normalBlock = mock(Block.class);
            when(normalBlock.getLocation()).thenReturn(loc2);

            when(chestLockService.isContainerLocked(lockedBlock)).thenReturn(true);
            when(chestLockService.isContainerLocked(normalBlock)).thenReturn(false);

            List<Block> blockList = new ArrayList<>(Arrays.asList(lockedBlock, normalBlock));
            EntityExplodeEvent event = mock(EntityExplodeEvent.class);
            when(event.blockList()).thenReturn(blockList);

            listener.onEntityExplode(event);

            assertThat(blockList).doesNotContain(lockedBlock);
            assertThat(blockList).contains(normalBlock);
        }

        @Test
        @DisplayName("Should skip when feature is disabled")
        void shouldSkipWhenDisabled() {
            config.setChestLockEnabled(false);

            EntityExplodeEvent event = mock(EntityExplodeEvent.class);

            listener.onEntityExplode(event);

            verify(event, never()).blockList();
        }
    }

    @Nested
    @DisplayName("onBlockExplode")
    class BlockExplodeTests {

        @Test
        @DisplayName("Should remove locked blocks from explosion")
        void shouldRemoveLockedBlocks() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 1, 64, 1);

            Block lockedBlock = mock(Block.class);
            when(lockedBlock.getLocation()).thenReturn(loc);
            when(chestLockService.isContainerLocked(lockedBlock)).thenReturn(true);

            List<Block> blockList = new ArrayList<>(Collections.singletonList(lockedBlock));
            BlockExplodeEvent event = mock(BlockExplodeEvent.class);
            when(event.blockList()).thenReturn(blockList);

            listener.onBlockExplode(event);

            assertThat(blockList).isEmpty();
        }

        @Test
        @DisplayName("Should skip when feature is disabled")
        void shouldSkipWhenDisabled() {
            config.setChestLockEnabled(false);

            BlockExplodeEvent event = mock(BlockExplodeEvent.class);

            listener.onBlockExplode(event);

            verify(event, never()).blockList();
        }
    }

    @Nested
    @DisplayName("onPistonExtend")
    class PistonExtendTests {

        @Test
        @DisplayName("Should cancel when pushing locked block")
        void shouldCancelWhenPushingLockedBlock() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 1, 64, 1);

            Block lockedBlock = mock(Block.class);
            when(lockedBlock.getLocation()).thenReturn(loc);
            when(chestLockService.isContainerLocked(lockedBlock)).thenReturn(true);

            BlockPistonExtendEvent event = mock(BlockPistonExtendEvent.class);
            when(event.getBlocks()).thenReturn(Collections.singletonList(lockedBlock));

            listener.onPistonExtend(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel when no locked blocks")
        void shouldNotCancelWhenNoLockedBlocks() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 1, 64, 1);

            Block normalBlock = mock(Block.class);
            when(normalBlock.getLocation()).thenReturn(loc);
            when(chestLockService.isLocked(loc)).thenReturn(false);

            BlockPistonExtendEvent event = mock(BlockPistonExtendEvent.class);
            when(event.getBlocks()).thenReturn(Collections.singletonList(normalBlock));

            listener.onPistonExtend(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should skip when feature is disabled")
        void shouldSkipWhenDisabled() {
            config.setChestLockEnabled(false);

            BlockPistonExtendEvent event = mock(BlockPistonExtendEvent.class);

            listener.onPistonExtend(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("onPistonRetract")
    class PistonRetractTests {

        @Test
        @DisplayName("Should cancel when pulling locked block")
        void shouldCancelWhenPullingLockedBlock() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 1, 64, 1);

            Block lockedBlock = mock(Block.class);
            when(lockedBlock.getLocation()).thenReturn(loc);
            when(chestLockService.isContainerLocked(lockedBlock)).thenReturn(true);

            BlockPistonRetractEvent event = mock(BlockPistonRetractEvent.class);
            when(event.getBlocks()).thenReturn(Collections.singletonList(lockedBlock));

            listener.onPistonRetract(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel when no locked blocks")
        void shouldNotCancelWhenNoLockedBlocks() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 1, 64, 1);

            Block normalBlock = mock(Block.class);
            when(normalBlock.getLocation()).thenReturn(loc);
            when(chestLockService.isLocked(loc)).thenReturn(false);

            BlockPistonRetractEvent event = mock(BlockPistonRetractEvent.class);
            when(event.getBlocks()).thenReturn(Collections.singletonList(normalBlock));

            listener.onPistonRetract(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should skip when feature is disabled")
        void shouldSkipWhenDisabled() {
            config.setChestLockEnabled(false);

            BlockPistonRetractEvent event = mock(BlockPistonRetractEvent.class);

            listener.onPistonRetract(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("onInventoryMove")
    class InventoryMoveTests {

        @Test
        @DisplayName("Should cancel hopper item move from locked container")
        void shouldCancelHopperFromLockedContainer() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 1, 64, 1);

            // Create a mock Container that is also an InventoryHolder
            Container container = mock(Container.class);
            when(container.getLocation()).thenReturn(loc);
            // Resolved from the holder rather than from a Location: a double chest's holder is a
            // DoubleChest, which is not a Container, so the old instanceof test skipped it entirely.
            when(chestLockService.isContainerLocked(container)).thenReturn(true);

            Inventory sourceInv = mock(Inventory.class);
            when(sourceInv.getHolder()).thenReturn(container);

            Inventory destInv = mock(Inventory.class);

            InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
            when(event.getSource()).thenReturn(sourceInv);
            when(event.getDestination()).thenReturn(destInv);
            when(event.getItem()).thenReturn(mock(ItemStack.class));

            listener.onInventoryMove(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should allow hopper item move from unlocked container")
        void shouldAllowHopperFromUnlockedContainer() {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location loc = new Location(world, 1, 64, 1);

            Container container = mock(Container.class);
            when(container.getLocation()).thenReturn(loc);
            when(chestLockService.isLocked(loc)).thenReturn(false);

            Inventory sourceInv = mock(Inventory.class);
            when(sourceInv.getHolder()).thenReturn(container);

            InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
            when(event.getSource()).thenReturn(sourceInv);

            listener.onInventoryMove(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should skip when feature is disabled")
        void shouldSkipWhenDisabled() {
            config.setChestLockEnabled(false);

            InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);

            listener.onInventoryMove(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }
}
