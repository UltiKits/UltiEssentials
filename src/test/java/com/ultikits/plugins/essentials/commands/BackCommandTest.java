package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("BackCommand Tests")
class BackCommandTest {

    private BackCommand backCommand;
    private EssentialsConfig config;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        backCommand = new BackCommand(config);
        EssentialsTestHelper.setField(backCommand, "plugin", EssentialsTestHelper.getMockPlugin());

        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);

        // Clear static map between tests
        Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
        field.setAccessible(true); // NOPMD
        ((Map<UUID, Location>) field.get(null)).clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("back command")
    class BackTests {

        @Test
        @DisplayName("Should teleport to last location")
        void shouldTeleportToLastLocation() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location lastLoc = new Location(world, 10, 64, 20);

            // Simulate storing a last location via teleport event
            Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
            field.setAccessible(true); // NOPMD
            @SuppressWarnings("unchecked")
            Map<UUID, Location> map = (Map<UUID, Location>) field.get(null);
            map.put(playerUuid, lastLoc);

            backCommand.back(player);

            verify(player).teleport(lastLoc);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send message when no last location")
        void shouldSendMessageWhenNoLastLocation() {
            backCommand.back(player);

            verify(player, never()).teleport(any(Location.class));
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setBackEnabled(false);

            backCommand.back(player);

            verify(player, never()).teleport(any(Location.class));
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("onPlayerTeleport listener")
    class TeleportListenerTests {

        @Test
        @DisplayName("Should record location on command teleport")
        @SuppressWarnings("unchecked")
        void shouldRecordLocationOnCommandTeleport() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location from = new Location(world, 100, 64, 200);
            Location to = new Location(world, 500, 64, 500);

            PlayerTeleportEvent event = new PlayerTeleportEvent(
                    player, from, to, PlayerTeleportEvent.TeleportCause.COMMAND);

            backCommand.onPlayerTeleport(event);

            Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
            field.setAccessible(true); // NOPMD
            Map<UUID, Location> map = (Map<UUID, Location>) field.get(null);
            assertThat(map).containsKey(playerUuid);
            assertThat(map.get(playerUuid)).isEqualTo(from);
        }

        @Test
        @DisplayName("Should not record location for non-command teleport")
        @SuppressWarnings("unchecked")
        void shouldNotRecordNonCommandTeleport() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location from = new Location(world, 100, 64, 200);
            Location to = new Location(world, 500, 64, 500);

            PlayerTeleportEvent event = new PlayerTeleportEvent(
                    player, from, to, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);

            backCommand.onPlayerTeleport(event);

            Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
            field.setAccessible(true); // NOPMD
            Map<UUID, Location> map = (Map<UUID, Location>) field.get(null);
            assertThat(map).doesNotContainKey(playerUuid);
        }

        @Test
        @DisplayName("Should not record when feature is disabled")
        @SuppressWarnings("unchecked")
        void shouldNotRecordWhenDisabled() throws Exception {
            config.setBackEnabled(false);
            World world = EssentialsTestHelper.createMockWorld("world");
            Location from = new Location(world, 100, 64, 200);
            Location to = new Location(world, 500, 64, 500);

            PlayerTeleportEvent event = new PlayerTeleportEvent(
                    player, from, to, PlayerTeleportEvent.TeleportCause.COMMAND);

            backCommand.onPlayerTeleport(event);

            Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
            field.setAccessible(true); // NOPMD
            Map<UUID, Location> map = (Map<UUID, Location>) field.get(null);
            assertThat(map).doesNotContainKey(playerUuid);
        }
    }

    @Nested
    @DisplayName("onPlayerQuit listener")
    class QuitListenerTests {

        @Test
        @DisplayName("Should remove player data on quit")
        @SuppressWarnings("unchecked")
        void shouldRemovePlayerDataOnQuit() throws Exception {
            // First store a location
            Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
            field.setAccessible(true); // NOPMD
            Map<UUID, Location> map = (Map<UUID, Location>) field.get(null);
            map.put(playerUuid, new Location(null, 0, 0, 0));

            PlayerQuitEvent event = new PlayerQuitEvent(player, "left");

            backCommand.onPlayerQuit(event);

            assertThat(map).doesNotContainKey(playerUuid);
        }
    }

    @Test
    @DisplayName("removePlayer static method should remove data")
    @SuppressWarnings("unchecked")
    void removePlayerShouldRemoveData() throws Exception {
        Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
        field.setAccessible(true); // NOPMD
        Map<UUID, Location> map = (Map<UUID, Location>) field.get(null);
        map.put(playerUuid, new Location(null, 0, 0, 0));

        BackCommand.removePlayer(playerUuid);

        assertThat(map).doesNotContainKey(playerUuid);
    }

    @Test
    @DisplayName("handleHelp should send usage message")
    void handleHelpShouldSendUsage() {
        backCommand.handleHelp(player);
        verify(player).sendMessage(anyString());
    }
}
