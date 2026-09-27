package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /wild} loads each candidate chunk asynchronously and checks it back on the main thread
 * (UltiKits/UltiEssentials#24), and its success message names the position the player lands on
 * (UltiKits/UltiEssentials#33).
 */
@DisplayName("/wild loads chunks asynchronously and reports where the player lands (#24, #33)")
class WildCommandAsyncChunkTest {

    private WildCommand command;
    private Player player;
    private World world;
    private final List<CompletableFuture<Chunk>> loads = new ArrayList<>();
    private final List<Runnable> mainThreadTasks = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        EssentialsConfig config = new EssentialsConfig();
        config.setWildMinRange(100);
        config.setWildMaxRange(200);
        command = new WildCommand(config);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        world = player.getWorld();
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        lenient().when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(70);
        lenient().when(world.getChunkAtAsync(anyInt(), anyInt())).thenAnswer(inv -> {
            CompletableFuture<Chunk> load = new CompletableFuture<>();
            loads.add(load);
            return load;
        });
        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        lenient().when(scheduler.runTask(org.mockito.ArgumentMatchers.<Plugin>any(), any(Runnable.class)))
                .thenAnswer(inv -> {
                    mainThreadTasks.add(inv.getArgument(1));
                    return mock(org.bukkit.scheduler.BukkitTask.class);
                });
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block ground = mock(Block.class);
        lenient().when(feet.getType()).thenReturn(Material.AIR);
        lenient().when(head.getType()).thenReturn(Material.AIR);
        lenient().when(ground.getType()).thenReturn(Material.GRASS_BLOCK);
        lenient().when(feet.getRelative(0, 1, 0)).thenReturn(head);
        lenient().when(feet.getRelative(0, -1, 0)).thenReturn(ground);
        lenient().when(world.getBlockAt(any(Location.class))).thenReturn(feet);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private void completeLoadAndRunMainThreadTask() {
        assertThat(loads).isNotEmpty();
        loads.get(loads.size() - 1).complete(mock(Chunk.class));
        assertThat(mainThreadTasks).as("the check is handed to the main thread").isNotEmpty();
        mainThreadTasks.remove(0).run();
    }

    @Test
    @DisplayName("The command itself touches no chunk, height or block: it only asks for the chunk asynchronously")
    void noSynchronousChunkAccess() {
        command.wildTeleport(player);

        assertThat(loads).hasSize(1);
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt());
        verify(world, never()).getBlockAt(any(Location.class));
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    @DisplayName("Once the chunk has loaded, the safety check and the teleport run in a main-thread task, not in the load's callback")
    void teleportRunsInAMainThreadTask() {
        command.wildTeleport(player);
        loads.get(0).complete(mock(Chunk.class));

        verify(player, never()).teleport(any(Location.class));
        assertThat(mainThreadTasks).hasSize(1);

        mainThreadTasks.remove(0).run();

        verify(player).teleport(any(Location.class));
    }

    @Test
    @DisplayName("The success message reports the landing position: the destination's own coordinates (#33)")
    void messageReportsTheLandingPosition() {
        command.wildTeleport(player);
        completeLoadAndRunMainThreadTask();

        ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(destination.capture());
        Location landing = destination.getValue();
        assertThat(landing.getBlockY()).isEqualTo(71);
        verify(player).sendMessage(String.format(CatalogueText.text("zh", "essentials.wild.success"),
                landing.getBlockX(), landing.getBlockY(), landing.getBlockZ()));
    }

    @Test
    @DisplayName("An unsafe candidate loads the next chunk, up to ten attempts, then reports failure")
    void attemptsAreChainedUpToTen() {
        Block solid = mock(Block.class);
        when(solid.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(any(Location.class))).thenReturn(solid);

        command.wildTeleport(player);
        for (int attempt = 0; attempt < 10; attempt++) {
            completeLoadAndRunMainThreadTask();
        }

        assertThat(loads).hasSize(10);
        verify(player, never()).teleport(any(Location.class));
        verify(player).sendMessage(CatalogueText.text("zh", "essentials.wild.no_safe_location"));
    }

    @Test
    @DisplayName("A player who logged out while the chunk loaded is not teleported, and no further chunk is loaded")
    void aPlayerWhoLeftIsNotTeleported() {
        command.wildTeleport(player);
        when(player.isOnline()).thenReturn(false);

        completeLoadAndRunMainThreadTask();

        verify(player, never()).teleport(any(Location.class));
        assertThat(loads).hasSize(1);
    }

    /**
     * The continuation of a search runs under the {@code UltiTools} Bukkit plugin, which outlives this
     * module, so unloading the module cannot cancel it by plugin. A search pending at the unload must
     * therefore do nothing when its chunk arrives: no teleport, no message, no further chunk.
     */
    private void unloadTheModule() throws Exception {
        com.ultikits.plugins.essentials.UltiEssentials module =
                mock(com.ultikits.plugins.essentials.UltiEssentials.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        com.ultikits.ultitools.context.SimpleContainer container = new com.ultikits.ultitools.context.SimpleContainer();
        container.registerType(com.ultikits.plugins.essentials.service.ScheduledCommandService.class,
                mock(com.ultikits.plugins.essentials.service.ScheduledCommandService.class));
        container.registerType(com.ultikits.plugins.essentials.service.ScoreboardService.class,
                mock(com.ultikits.plugins.essentials.service.ScoreboardService.class));
        container.registerType(com.ultikits.plugins.essentials.service.NamePrefixService.class,
                mock(com.ultikits.plugins.essentials.service.NamePrefixService.class));
        container.registerType(com.ultikits.plugins.essentials.service.TeleportService.class,
                mock(com.ultikits.plugins.essentials.service.TeleportService.class));
        container.registerType(com.ultikits.plugins.essentials.service.TpaService.class,
                mock(com.ultikits.plugins.essentials.service.TpaService.class));
        module.setContext(container);
        java.lang.reflect.Method hook = com.ultikits.ultitools.abstracts.UltiToolsPlugin.class.getDeclaredMethod("onUnregister");
        hook.setAccessible(true); // NOPMD - the protected framework hook, invoked as unregisterSelf() does
        hook.invoke(module);
    }

    @Test
    @DisplayName("A search whose chunk is still loading when the module unloads does nothing once it loads")
    void aSearchPendingAtUnloadDoesNothing() throws Exception {
        command.wildTeleport(player);
        org.mockito.Mockito.clearInvocations(player);

        unloadTheModule();
        loads.get(0).complete(mock(Chunk.class));
        while (!mainThreadTasks.isEmpty()) {
            mainThreadTasks.remove(0).run();
        }

        verify(player, never()).teleport(any(Location.class));
        verify(player, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        assertThat(loads).hasSize(1);
    }

    @Test
    @DisplayName("A search whose chunk has loaded but whose main-thread check has not run yet does nothing after the unload")
    void aCheckQueuedAtUnloadDoesNothing() throws Exception {
        command.wildTeleport(player);
        org.mockito.Mockito.clearInvocations(player);
        loads.get(0).complete(mock(Chunk.class));
        assertThat(mainThreadTasks).hasSize(1);

        unloadTheModule();
        mainThreadTasks.remove(0).run();

        verify(player, never()).teleport(any(Location.class));
        verify(player, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        assertThat(loads).hasSize(1);
    }

    @Test
    @DisplayName("Control: a search started after an unload and reload of the module still teleports")
    void aSearchStartedAfterTheUnloadStillRuns() throws Exception {
        unloadTheModule();

        command.wildTeleport(player);
        completeLoadAndRunMainThreadTask();

        verify(player).teleport(any(Location.class));
    }
}
