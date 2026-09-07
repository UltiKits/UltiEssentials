package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.CommandContext;
import com.ultikits.ultitools.abstracts.command.validation.CommandValidator;
import com.ultikits.ultitools.annotations.command.RunAsync;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Falsification for FIX-01 (13-04): {@code WildCommand.wildTeleport} must run entirely on the
 * primary thread. The handler's whole body -- the highest-block lookup that loads a chunk, the
 * safety checks that read blocks, and the teleport itself -- must never leave it.
 *
 * <p><b>Dispatch mirrors the framework, it does not reinvent it.</b> {@code
 * BaseCommandExecutor.executeCommand} decides sync vs. async with exactly one line --
 * {@code isAsync = asyncCommand != null || method.isAnnotationPresent(RunAsync.class)} -- then
 * calls {@code runnable.runTask(...)} or {@code runnable.runTaskAsynchronously(...)}
 * accordingly (read from {@code BaseCommandExecutor.java} this session). This class reflects on
 * the identical annotation and issues the identical two {@link Bukkit#getScheduler()} calls, so
 * the SAME real {@code BukkitSchedulerMock} thread-pool-vs-tick-queue split the framework itself
 * relies on is what proves this test, not a hand-rolled thread check.</p>
 *
 * <p><b>Correction to this plan's own stated falsification mechanism (recorded verbatim in
 * {@code 13-LEDGER-UltiEssentials.md}).</b> The plan expected the pre-fix RED failure to be
 * {@code org.mockbukkit.mockbukkit.AsyncCatcher.catchOp}'s {@code IllegalStateException},
 * mirroring Paper's real asynchronous-operation guard. Measured directly this session (three
 * probes: a bare {@code player.teleport()} off-thread, the real unmodified {@code wildTeleport}
 * off-thread, and the same call on the tick thread) that mockbukkit-v1.21 4.101.0's
 * {@code PlayerMock.teleport(...)} and {@code WorldMock.getHighestBlockYAt(...)} /
 * {@code getBlockAt(...)} call zero {@code AsyncCatcher.catchOp} sites (a full disassembly of
 * every one of the jar's 28 {@code catchOp} call sites confirms none sit on this call chain --
 * they guard {@code kick}, {@code addPlayer}, {@code loadChunk}, {@code openInventory} and
 * similar, never {@code teleport} or a block/height lookup). Firing the same event off-thread
 * therefore does not throw in this test double, in either direction.
 * <p>
 * The substitute proof used here checks the exact primitive {@code AsyncCatcher.catchOp} itself
 * checks -- {@code Bukkit.getServer().isPrimaryThread()} -- captured from inside the dispatched
 * runnable, immediately before the handler body runs. This is the same fact the removed
 * annotation controls (which scheduler call the framework issues), verified against the real
 * {@code BukkitSchedulerMock} rather than asserted from a mock-setup shortcut: pre-fix, the
 * handler is scheduled via {@code runTaskAsynchronously} and executes on the scheduler's real
 * background thread pool ({@code isPrimaryThread() == false}); post-fix, it executes inside
 * {@code performOneTick()} on the calling (test) thread, which {@code ServerMock} treats as
 * primary ({@code isPrimaryThread() == true}). The test is still red before the fix and green
 * after it, for the real reason (the wrong thread), not for a setup error -- it does not
 * reproduce the specific exception text the plan predicted, because that exception is not part
 * of this dependency's simulated contract for this call chain.
 * </p>
 */
@DisplayName("WildCommand thread-safety falsification (FIX-01, 13-04)")
class WildCommandThreadSafetyTest {

    private ServerMock server;
    private Plugin mockPlugin;
    private WildCommand command;
    private World world;
    private Method handlerMethod;
    private boolean handlerIsAsync;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkitHelper.bootstrapLiveServer();
        // TestHelper.mockUltiToolsInstance() mocks the final UltiTools singleton so
        // UltiTools.getInstance() -- which CooldownValidator calls for its own i18n'd
        // failure message and cache registration -- resolves instead of NPEing. Mockito's
        // inline mock maker resolves UltiTools' entire method-signature closure to do this,
        // which is exactly why the pom now declares VaultAPI at test scope (Pitfall 5).
        TestHelper.mockUltiToolsInstance();
        mockPlugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");

        EssentialsConfig config = new EssentialsConfig();
        // Small, bounded range: the world is a uniform flat plane (grass at height 4 in every
        // direction, MockBukkit's own default), so any x/z lands on safe ground -- shrinking
        // the range only keeps coordinates small and the test fast, it does not change safety.
        config.setWildMinRange(1);
        config.setWildMaxRange(5);

        command = new WildCommand(config);
        UltiToolsPlugin frameworkPlugin = mock(UltiToolsPlugin.class);
        lenient().when(frameworkPlugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        EssentialsTestHelper.setField(command, "plugin", frameworkPlugin);

        handlerMethod = WildCommand.class.getMethod("wildTeleport", Player.class);
        handlerIsAsync = handlerMethod.isAnnotationPresent(RunAsync.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    private Player spawnPlayer(String name) {
        Player player = server.addPlayer(name);
        player.teleport(new Location(world, 0, 65, 0));
        return player;
    }

    /**
     * Schedules {@code command.wildTeleport(player)} the way {@code BaseCommandExecutor} would
     * for a method carrying (or not carrying) {@code @RunAsync}, without ticking the scheduler --
     * callers that need several dispatches sharing one tick call {@link #tick()} themselves.
     */
    private PendingDispatch schedule(Player player) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean ranOnPrimaryThread = new AtomicBoolean();
        CountDownLatch latch = new CountDownLatch(1);
        Runnable body = () -> {
            try {
                ranOnPrimaryThread.set(Bukkit.getServer().isPrimaryThread());
                command.wildTeleport(player);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        };
        if (handlerIsAsync) {
            Bukkit.getScheduler().runTaskAsynchronously(mockPlugin, body);
        } else {
            Bukkit.getScheduler().runTask(mockPlugin, body);
        }
        return new PendingDispatch(latch, ranOnPrimaryThread, failure);
    }

    /** Runs the sync tick queue -- a no-op when the handler dispatches asynchronously, since
     * nothing was ever queued onto it. */
    private void tick() {
        if (!handlerIsAsync) {
            server.getScheduler().performOneTick();
        }
    }

    private DispatchOutcome awaitOne(PendingDispatch pending) throws InterruptedException {
        boolean completed = pending.latch.await(5, TimeUnit.SECONDS);
        return new DispatchOutcome(completed, pending.ranOnPrimaryThread.get(), pending.failure.get());
    }

    private DispatchOutcome dispatchAndAwait(Player player) throws InterruptedException {
        PendingDispatch pending = schedule(player);
        tick();
        return awaitOne(pending);
    }

    private CommandContext contextFor(Player player) {
        return CommandContext.builder()
                .sender(player)
                .matchedMethod(handlerMethod)
                .matchedFormat("")
                .executorClass(WildCommand.class)
                .build();
    }

    @Test
    @DisplayName("wildTeleport's whole body -- chunk lookup, safety checks, teleport -- runs on the primary thread")
    void wildTeleportRunsOnThePrimaryThread() throws Exception {
        Player player = spawnPlayer("Wild1");
        Location before = player.getLocation().clone();

        DispatchOutcome outcome = dispatchAndAwait(player);

        assertThat(outcome.completed).as("dispatched task completed within 5s").isTrue();
        assertThat(outcome.failure).as("no exception recorded during dispatch").isNull();
        assertThat(outcome.ranOnPrimaryThread)
                .as("handler body observed Bukkit.getServer().isPrimaryThread() -- the same "
                        + "primitive AsyncCatcher.catchOp checks -- as true")
                .isTrue();
        assertThat(player.getLocation()).as("player actually teleported").isNotEqualTo(before);
    }

    @Test
    @DisplayName("a second /wild inside the sixty-second cooldown is refused; neither attempt throws")
    void secondWildWithinCooldownIsRefused() throws Exception {
        Player player = spawnPlayer("Wild2");

        CommandContext context = contextFor(player);
        CommandValidator.ValidationResult firstCheck = command.getCooldownValidator().validate(context);
        assertThat(firstCheck.isValid()).as("no cooldown recorded yet").isTrue();

        DispatchOutcome first = dispatchAndAwait(player);
        assertThat(first.completed).isTrue();
        assertThat(first.failure).as("first attempt does not throw").isNull();
        command.getCooldownValidator().onComplete(context, first.failure == null);

        Location afterFirst = player.getLocation().clone();

        CommandValidator.ValidationResult secondCheck = command.getCooldownValidator().validate(context);
        assertThat(secondCheck.isValid())
                .as("a second attempt inside sixty seconds is refused by CooldownValidator")
                .isFalse();

        // Refused means the framework never invokes the mapped method a second time -- mirror
        // that here rather than calling wildTeleport again, exactly as onCommand's own
        // validate-before-invoke ordering does.
        assertThat(player.getLocation())
                .as("player is not moved a second time")
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("two players invoking /wild in the same tick are serialised on the primary thread")
    void twoPlayersAreSerialisedOnThePrimaryThread() throws Exception {
        Player alice = spawnPlayer("WildAlice");
        Player bob = spawnPlayer("WildBob");
        Location aliceBefore = alice.getLocation().clone();
        Location bobBefore = bob.getLocation().clone();

        PendingDispatch aliceDispatch = schedule(alice);
        PendingDispatch bobDispatch = schedule(bob);
        tick(); // one tick runs both queued bodies, back to back, on this thread

        DispatchOutcome aliceOutcome = awaitOne(aliceDispatch);
        DispatchOutcome bobOutcome = awaitOne(bobDispatch);

        assertThat(aliceOutcome.completed).isTrue();
        assertThat(bobOutcome.completed).isTrue();
        assertThat(aliceOutcome.failure).as("Alice's dispatch does not throw").isNull();
        assertThat(bobOutcome.failure).as("Bob's dispatch does not throw").isNull();
        assertThat(aliceOutcome.ranOnPrimaryThread).as("Alice's body ran on the primary thread").isTrue();
        assertThat(bobOutcome.ranOnPrimaryThread).as("Bob's body ran on the primary thread").isTrue();
        assertThat(alice.getLocation()).as("Alice ended up at a valid, moved location").isNotEqualTo(aliceBefore);
        assertThat(bob.getLocation()).as("Bob ended up at a valid, moved location").isNotEqualTo(bobBefore);
    }

    /** A scheduled-but-not-yet-awaited dispatch. */
    private static final class PendingDispatch {
        private final CountDownLatch latch;
        private final AtomicBoolean ranOnPrimaryThread;
        private final AtomicReference<Throwable> failure;

        private PendingDispatch(CountDownLatch latch, AtomicBoolean ranOnPrimaryThread,
                                 AtomicReference<Throwable> failure) {
            this.latch = latch;
            this.ranOnPrimaryThread = ranOnPrimaryThread;
            this.failure = failure;
        }
    }

    /** The observed result of one dispatch, once it has completed (or timed out). */
    private static final class DispatchOutcome {
        private final boolean completed;
        private final boolean ranOnPrimaryThread;
        private final Throwable failure;

        private DispatchOutcome(boolean completed, boolean ranOnPrimaryThread, Throwable failure) {
            this.completed = completed;
            this.ranOnPrimaryThread = ranOnPrimaryThread;
            this.failure = failure;
        }
    }
}
