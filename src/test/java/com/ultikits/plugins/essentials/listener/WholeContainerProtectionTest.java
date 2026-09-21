package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.service.ChestLockService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Every protection path in this module, asked about the half of a double chest that holds
 * <strong>no</strong> lock record of its own while the other half does.
 * <p>
 * That state is the one {@link ChestLockService#canAccess(org.bukkit.block.Block, Player)} was added
 * to cover -- legacy data written before double-chest propagation existed, and a lock whose second
 * insert never landed. The interact path was keyed on the container when it was added; every other
 * path was left keyed on the single block, and each of those is a separate way to reach the same
 * shared inventory. Breaking is the worst of them: the interact bypass only opened the items,
 * breaking drops them on the floor for whoever swung the tool (UltiKits/UltiEssentials#50, the
 * unreplied P1 on {@code ChestLockListener.java:60}).
 * <p>
 * These tests wire a <strong>real</strong> {@link ChestLockService} to a <strong>real</strong>
 * {@link ChestLockListener}, because the defect lives in which lock lookup the listener chooses --
 * {@code ChestLockListenerTest} mocks the whole service, so it cannot see the choice at all.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Whole-container protection: the unrecorded half of a double chest")
class WholeContainerProtectionTest {

    /** The half that holds the lock record. */
    private static final int RECORDED_X = 10;
    /** The half that holds no record of its own, and shares RECORDED_X's inventory. */
    private static final int UNRECORDED_X = 11;
    private static final int Y = 64;
    private static final int Z = 20;

    private ChestLockService service;
    private ChestLockListener listener;
    private EssentialsConfig config;
    private World world;

    private Player owner;
    private Player stranger;

    private Block recordedHalf;
    private Block unrecordedHalf;

    /** The one inventory both halves open, and whose items a break of either half would drop. */
    private Inventory sharedInventory;
    private List<ItemStack> sharedContents;

    @SuppressWarnings("unchecked")
    private final DataOperator<ChestLockData> lockOperator = mock(DataOperator.class);

    @SuppressWarnings("unchecked")
    private final Query<ChestLockData> storedLockQuery = mock(Query.class);

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();

        service = new ChestLockService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(service, "lockOperator", lockOperator);

        listener = new ChestLockListener();
        EssentialsTestHelper.setField(listener, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(listener, "config", config);
        EssentialsTestHelper.setField(listener, "chestLockService", service);

        reset(lockOperator, storedLockQuery);
        // A store that reports nothing left after a delete, and a transaction that actually runs its
        // action -- DataOperator#transaction is a default interface method, so an unstubbed mock
        // returns null and silently never runs it. Mirrors ChestLockServiceDoubleChestTest.
        lenient().when(lockOperator.transaction(
                        org.mockito.ArgumentMatchers.<java.util.concurrent.Callable<Object>>any()))
                .thenAnswer(inv -> ((java.util.concurrent.Callable<?>) inv.getArgument(0)).call());
        lenient().when(lockOperator.query()).thenReturn(storedLockQuery);
        lenient().when(storedLockQuery.where(anyString())).thenReturn(storedLockQuery);
        lenient().when(storedLockQuery.and(anyString())).thenReturn(storedLockQuery);
        lenient().when(storedLockQuery.eq(any())).thenReturn(storedLockQuery);
        lenient().when(storedLockQuery.list()).thenReturn(new ArrayList<>());

        world = EssentialsTestHelper.createMockWorld("world");
        owner = EssentialsTestHelper.createMockPlayer("Alice", UUID.randomUUID());
        stranger = EssentialsTestHelper.createMockPlayer("Bob", UUID.randomUUID());

        sharedContents = Arrays.asList(mock(ItemStack.class), mock(ItemStack.class));
        sharedInventory = mock(Inventory.class);
        lenient().when(sharedInventory.getContents())
                .thenReturn(sharedContents.toArray(new ItemStack[0]));

        buildDoubleChest();
        seedLockOnRecordedHalfOnly();
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    /**
     * Two chest blocks sharing one {@link DoubleChest} holder, exactly as Bukkit reports a placed
     * double chest: each block's state is a {@link Chest}, and both chests' inventories are held by
     * the same {@code DoubleChest} whose left/right sides are the two blocks.
     */
    private void buildDoubleChest() {
        Chest recordedState = mock(Chest.class);
        Chest unrecordedState = mock(Chest.class);

        Location recordedLoc = new Location(world, RECORDED_X, Y, Z);
        Location unrecordedLoc = new Location(world, UNRECORDED_X, Y, Z);
        when(recordedState.getLocation()).thenReturn(recordedLoc);
        when(unrecordedState.getLocation()).thenReturn(unrecordedLoc);

        DoubleChest doubleChest = mock(DoubleChest.class);
        when(doubleChest.getLeftSide()).thenReturn(recordedState);
        when(doubleChest.getRightSide()).thenReturn(unrecordedState);
        when(sharedInventory.getHolder()).thenReturn(doubleChest);

        when(recordedState.getInventory()).thenReturn(sharedInventory);
        when(unrecordedState.getInventory()).thenReturn(sharedInventory);

        recordedHalf = chestBlock(recordedLoc, recordedState);
        unrecordedHalf = chestBlock(unrecordedLoc, unrecordedState);

        // getBlock() closes the loop the hopper path walks: holder -> block -> container lock lookup.
        lenient().when(recordedState.getBlock()).thenReturn(recordedHalf);
        lenient().when(unrecordedState.getBlock()).thenReturn(unrecordedHalf);
    }

    private Block chestBlock(Location loc, Chest state) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        when(block.getLocation()).thenReturn(loc);
        when(block.getState()).thenReturn(state);
        lenient().when(block.getWorld()).thenReturn(world);
        lenient().when(block.getX()).thenReturn(loc.getBlockX());
        lenient().when(block.getY()).thenReturn(loc.getBlockY());
        lenient().when(block.getZ()).thenReturn(loc.getBlockZ());
        return block;
    }

    /**
     * One record, on one half. The other half is exactly as a legacy row or an interrupted second
     * insert leaves it: a block of a locked container with nothing stored against its own location.
     */
    private void seedLockOnRecordedHalfOnly() throws Exception {
        ChestLockData lock = ChestLockData.builder()
                .uuid(UUID.randomUUID())
                .world("world")
                .x(RECORDED_X)
                .y(Y)
                .z(Z)
                .ownerUuid(owner.getUniqueId().toString())
                .ownerName(owner.getName())
                .createdAt(System.currentTimeMillis())
                .build();
        cache().put(lock.getLocationKey(), lock);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ChestLockData> cache() throws Exception {
        return (Map<String, ChestLockData>) EssentialsTestHelper.getField(service, "lockCache");
    }

    /**
     * What a player standing in front of the container actually gets: whether the block is still
     * there afterwards, and what landed on the floor.
     * <p>
     * The listener's only lever over a break is the event, so this applies Bukkit's own documented
     * consequence to it -- an uncancelled {@link BlockBreakEvent} with {@code isDropItems()} true is
     * the server removing the block and dropping the container's contents. Asserting on the
     * consequence rather than on {@code event.isCancelled()} keeps the test pinned to the thing an
     * exploiter is after (the items) rather than to the flag that happens to prevent it today.
     */
    private BreakOutcome attemptBreak(Block target, Player breaker) {
        BlockBreakEvent event = new BlockBreakEvent(target, breaker);
        listener.onBlockBreak(event);
        if (event.isCancelled()) {
            return new BreakOutcome(true, Collections.<ItemStack>emptyList());
        }
        List<ItemStack> dropped = event.isDropItems()
                ? Arrays.asList(target.getState() instanceof Chest
                        ? ((Chest) target.getState()).getInventory().getContents()
                        : new ItemStack[0])
                : Collections.<ItemStack>emptyList();
        return new BreakOutcome(false, dropped);
    }

    /** The outcome of swinging a tool at a block: did it survive, and what dropped. */
    private static final class BreakOutcome {
        private final boolean blockSurvived;
        private final List<ItemStack> droppedItems;

        BreakOutcome(boolean blockSurvived, List<ItemStack> droppedItems) {
            this.blockSurvived = blockSurvived;
            this.droppedItems = droppedItems;
        }
    }

    @Nested
    @DisplayName("breaking")
    class BreakingTests {

        @Test
        @DisplayName("a stranger breaking the unrecorded half neither destroys it nor spills the shared inventory")
        void strangerCannotBreakUnrecordedHalf() {
            BreakOutcome outcome = attemptBreak(unrecordedHalf, stranger);

            // The items first: they are what the exploit is for, and asserting them first means a
            // regression reports "the chest spilled its contents" rather than "a flag was false".
            assertThat(outcome.droppedItems)
                    .as("nothing the locked container held may reach the floor, where anyone can "
                            + "pick it up")
                    .isEmpty();
            assertThat(outcome.blockSurvived)
                    .as("the unrecorded half of a locked double chest must still be standing "
                            + "after a stranger swings at it")
                    .isTrue();
        }

        @Test
        @DisplayName("the stranger is told which player's lock refused them, naming the other half's owner")
        void strangerIsToldWhoOwnsIt() {
            attemptBreak(unrecordedHalf, stranger);

            verify(stranger, atLeastOnce()).sendMessage(contains("Alice"));
        }

        @Test
        @DisplayName("a stranger breaking the recorded half is refused too -- the half that always was")
        void strangerCannotBreakRecordedHalf() {
            BreakOutcome outcome = attemptBreak(recordedHalf, stranger);

            assertThat(outcome.droppedItems).isEmpty();
            assertThat(outcome.blockSurvived).isTrue();
        }

        @Test
        @DisplayName("the owner may still break the unrecorded half of their own container")
        void ownerMayStillBreakUnrecordedHalf() {
            BreakOutcome outcome = attemptBreak(unrecordedHalf, owner);

            assertThat(outcome.blockSurvived)
                    .as("widening the lookup must not lock an owner out of their own container")
                    .isFalse();
            assertThat(outcome.droppedItems).isEqualTo(sharedContents);
        }

        @Test
        @DisplayName("an admin may still break another player's container, ungated by chestlock.admin-bypass")
        void adminMayStillBreakUnrecordedHalf() {
            when(stranger.hasPermission("ultiessentials.lock.admin")).thenReturn(true);
            // Breaking deliberately does not consult this toggle; opening does. Turning it off here
            // pins that the two checks stayed independent through this fix, as FEATURES.md records.
            config.setChestLockAdminBypass(false);

            BreakOutcome outcome = attemptBreak(unrecordedHalf, stranger);

            assertThat(outcome.blockSurvived).isFalse();
        }

        @Test
        @DisplayName("breaking is not policed at all while the feature is disabled")
        void disabledFeatureDoesNotPolice() {
            config.setChestLockEnabled(false);

            BreakOutcome outcome = attemptBreak(unrecordedHalf, stranger);

            assertThat(outcome.blockSurvived).isFalse();
        }
    }

    @Nested
    @DisplayName("explosions")
    class ExplosionTests {

        @Test
        @DisplayName("an explosion cannot destroy the unrecorded half either")
        void explosionSparesUnrecordedHalf() {
            List<Block> destroyed = new ArrayList<>(Collections.singletonList(unrecordedHalf));
            EntityExplodeEvent event = mock(EntityExplodeEvent.class);
            when(event.blockList()).thenReturn(destroyed);

            listener.onEntityExplode(event);

            assertThat(destroyed)
                    .as("a TNT charge next to the unrecorded half must not do what a pickaxe may not")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("hoppers")
    class HopperTests {

        @Test
        @DisplayName("a hopper cannot siphon a double chest -- its holder is a DoubleChest, not a Container")
        void hopperCannotDrainDoubleChest() {
            InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
            when(event.getSource()).thenReturn(sharedInventory);

            listener.onInventoryMove(event);

            // An `instanceof org.bukkit.block.Container` test on the holder skipped every double
            // chest, locked or not: DoubleChest is an InventoryHolder but not a block state.
            verify(event).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("the lock and unlock commands read")
    class CommandReadTests {

        @Test
        @DisplayName("a stranger cannot put their own lock on the unrecorded half")
        void strangerCannotLockUnrecordedHalf() {
            ChestLockService.LockResult result = service.lockBlock(unrecordedHalf, stranger);

            // Letting this through created one container protected by two records with two owners --
            // and unlockBlock then removed both on the say-so of whichever one the caller owned.
            assertThat(result).isEqualTo(ChestLockService.LockResult.ALREADY_LOCKED);
            verify(lockOperator, never()).insert(any(ChestLockData.class));
        }

        @Test
        @DisplayName("a player owning only one half cannot unlock the container out from under the other owner")
        void oneHalfOwnerCannotUnlockTheWholeContainer() throws Exception {
            ChestLockData strangersHalf = ChestLockData.builder()
                    .uuid(UUID.randomUUID())
                    .world("world").x(UNRECORDED_X).y(Y).z(Z)
                    .ownerUuid(stranger.getUniqueId().toString())
                    .ownerName(stranger.getName())
                    .createdAt(System.currentTimeMillis())
                    .build();
            cache().put(strangersHalf.getLocationKey(), strangersHalf);

            ChestLockService.UnlockResult result = service.unlockBlock(unrecordedHalf, stranger);

            assertThat(result).isEqualTo(ChestLockService.UnlockResult.NOT_OWNER);
            assertThat(cache()).containsKey(ChestLockData.createLocationKey("world", RECORDED_X, Y, Z));
        }

        @Test
        @DisplayName("the owner unlocking from the unrecorded half still works, and takes the record with it")
        void ownerCanUnlockFromUnrecordedHalf() throws Exception {
            ChestLockService.UnlockResult result = service.unlockBlock(unrecordedHalf, owner);

            // Keyed on the block, this answered NOT_LOCKED -- contradicting the interact check that
            // refuses to open that very block.
            assertThat(result).isEqualTo(ChestLockService.UnlockResult.SUCCESS);
            assertThat(cache()).isEmpty();
        }

        @Test
        @DisplayName("/unlock info reports the container as locked when asked about the unrecorded half")
        void infoReportsTheContainerLockFromEitherHalf() {
            assertThat(service.locksProtecting(unrecordedHalf))
                    .as("the read /unlock info performs")
                    .extracting(ChestLockData::getOwnerName)
                    .containsExactly("Alice");
        }
    }
}
