package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /back} returns only from a teleport this module's own commands started
 * (UltiKits/UltiEssentials#39). Teleports are dispatched through MockBukkit's real event bus, so the
 * listener sees exactly the events a server would fire.
 */
@DisplayName("/back records only this module's own teleports (#39)")
class BackOwnTeleportsTest {

    private ServerMock server;
    private PluginMock bukkitPlugin;
    private BackCommand back;
    private SpawnCommand spawn;
    private PlayerMock player;
    private World world;
    private Location spawnPoint;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
        bukkitPlugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        spawnPoint = new Location(world, 0.5, 70, 0.5);

        UltiToolsPlugin i18nPlugin = mock(UltiToolsPlugin.class);
        lenient().when(i18nPlugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        EssentialsConfig config = new EssentialsConfig();
        back = new BackCommand(config);
        SpawnConfig spawnConfig = mock(SpawnConfig.class);
        when(spawnConfig.getSpawnLocation()).thenReturn(spawnPoint);
        spawn = new SpawnCommand(config, spawnConfig);
        Field pluginField = BaseEssentialsCommand.class.getDeclaredField("plugin");
        pluginField.setAccessible(true); // NOPMD
        pluginField.set(back, i18nPlugin);
        pluginField.set(spawn, i18nPlugin);

        Field locations = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
        locations.setAccessible(true); // NOPMD
        ((Map<UUID, Location>) locations.get(null)).clear();

        server.getPluginManager().registerEvents(back, bukkitPlugin);
        player = server.addPlayer("Wanderer");
        player.teleport(new Location(world, 100.5, 64, 200.5));
        player.nextMessage();
        clearRecorded();
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @SuppressWarnings("unchecked")
    private void clearRecorded() throws Exception {
        Field locations = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
        locations.setAccessible(true); // NOPMD
        ((Map<UUID, Location>) locations.get(null)).clear();
        while (player.nextMessage() != null) {
            // drain
        }
    }

    private String lastMessage() {
        String last = null;
        String next;
        while ((next = player.nextMessage()) != null) {
            last = next;
        }
        return last;
    }

    @Test
    @DisplayName("/spawn then /back returns the player to where /spawn took them from")
    void spawnThenBackReturns() {
        Location before = player.getLocation().clone();

        spawn.teleportToSpawn(player);
        back.back(player);

        assertThat(player.getLocation()).isEqualTo(before);
    }

    @Test
    @DisplayName("/back twice returns the player to where the first /back took them from")
    void backTwiceToggles() {
        Location before = player.getLocation().clone();
        spawn.teleportToSpawn(player);

        back.back(player);
        back.back(player);

        assertThat(player.getLocation()).isEqualTo(spawnPoint);
        assertThat(before).isNotEqualTo(spawnPoint);
    }

    @Test
    @DisplayName("Another plugin's teleport is not recorded: /back then has no location")
    void aForeignPluginTeleportIsNotRecorded() {
        player.teleport(new Location(world, 900.5, 64, 900.5), PlayerTeleportEvent.TeleportCause.PLUGIN);

        back.back(player);

        assertThat(lastMessage()).isEqualTo(CatalogueText.text("en", "essentials.back.no_location"));
        assertThat(player.getLocation().getX()).isEqualTo(900.5);
    }

    @Test
    @DisplayName("A vanilla /tp (a COMMAND teleport this module did not start) is not recorded")
    void aVanillaCommandTeleportIsNotRecorded() {
        player.teleport(new Location(world, 900.5, 64, 900.5), PlayerTeleportEvent.TeleportCause.COMMAND);

        back.back(player);

        assertThat(lastMessage()).isEqualTo(CatalogueText.text("en", "essentials.back.no_location"));
    }

    @Test
    @DisplayName("After a quit and a rejoin, a teleport by something else leaves /back with no location")
    void rejoinThenForeignTeleportLeavesNothingToReturnTo() {
        spawn.teleportToSpawn(player);
        back.onPlayerQuit(new PlayerQuitEvent(player, "bye"));

        player.teleport(new Location(world, 5.5, 64, 5.5), PlayerTeleportEvent.TeleportCause.PLUGIN);
        back.back(player);

        assertThat(lastMessage()).isEqualTo(CatalogueText.text("en", "essentials.back.no_location"));
    }
}
