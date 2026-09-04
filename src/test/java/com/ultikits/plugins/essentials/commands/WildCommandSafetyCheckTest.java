package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WildCommand#wildTeleport}'s five safety-check rejection directions and its
 * retry-loop exhaustion path -- the existing {@code WildCommandTest} only ever exercises the
 * "everything is safe" success path and the disabled-feature path, leaving every rejection
 * reason (and the give-up-after-10-attempts path it always leads to here) untested.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("WildCommand safety-check rejection Tests")
class WildCommandSafetyCheckTest {

    private WildCommand command;
    private EssentialsConfig config;
    private Player player;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        command = new WildCommand(config);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
        world = player.getWorld();

        Location playerLoc = new Location(world, 0, 64, 0);
        when(player.getLocation()).thenReturn(playerLoc);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    /**
     * Every candidate location resolves to the same feet/head/ground block mocks regardless of
     * its (randomized) coordinates, so the outcome is deterministic across all 10 retry attempts.
     */
    private void stubUniformBlocks(Material feetType, Material headType, Material groundType) {
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block ground = mock(Block.class);
        when(feet.getType()).thenReturn(feetType);
        when(head.getType()).thenReturn(headType);
        when(ground.getType()).thenReturn(groundType);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(ground);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
    }

    private void assertNeverTeleportsAndReportsFailure() {
        verify(player, never()).teleport(any(Location.class));
        verify(player).sendMessage(contains("未能找到安全位置"));
    }

    @Test
    @DisplayName("A solid feet block is never a safe teleport target")
    void solidFeetBlockIsRejected() {
        stubUniformBlocks(Material.STONE, Material.AIR, Material.GRASS_BLOCK);

        command.wildTeleport(player);

        assertNeverTeleportsAndReportsFailure();
    }

    @Test
    @DisplayName("A solid head block (feet clear) is never a safe teleport target")
    void solidHeadBlockIsRejected() {
        stubUniformBlocks(Material.AIR, Material.STONE, Material.GRASS_BLOCK);

        command.wildTeleport(player);

        assertNeverTeleportsAndReportsFailure();
    }

    @Test
    @DisplayName("A non-solid ground block is never a safe teleport target")
    void nonSolidGroundIsRejected() {
        stubUniformBlocks(Material.AIR, Material.AIR, Material.AIR);

        command.wildTeleport(player);

        assertNeverTeleportsAndReportsFailure();
    }

    @Test
    @DisplayName("Lava as the ground block is never a safe teleport target")
    void lavaGroundIsRejected() {
        stubUniformBlocks(Material.AIR, Material.AIR, Material.LAVA);

        command.wildTeleport(player);

        assertNeverTeleportsAndReportsFailure();
    }

    @Test
    @DisplayName("Water as the ground block is never a safe teleport target")
    void waterGroundIsRejected() {
        stubUniformBlocks(Material.AIR, Material.AIR, Material.WATER);

        command.wildTeleport(player);

        assertNeverTeleportsAndReportsFailure();
    }

    @Test
    @DisplayName("After 10 failed attempts, the player is told no safe location was found and is never teleported")
    void exhaustingAllAttemptsNeverTeleports() {
        stubUniformBlocks(Material.STONE, Material.AIR, Material.GRASS_BLOCK);

        command.wildTeleport(player);

        // getBlockAt is called once per attempt inside the retry loop
        verify(world, times(10)).getBlockAt(any(Location.class));
        assertNeverTeleportsAndReportsFailure();
    }
}
