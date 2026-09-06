package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.EventListener;
import com.ultikits.ultitools.context.MergedAnnotationResolver;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

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

        /**
         * Proves the review round-2 Codex finding on PR#22 (comment 3944429766): every one of
         * this plugin's own teleport call sites -- {@code TeleportService} (backing /home and
         * /warp), {@code SpawnCommand}, {@code LobbyCommand} -- calls
         * {@code Player#teleport(Location)} with no explicit cause, which Bukkit/Paper's
         * {@code Entity#teleport(Location)} javadoc and source both default to
         * {@code TeleportCause.PLUGIN}, not {@code COMMAND}. Before this fix, /back after any of
         * those commands recorded nothing.
         */
        @Test
        @DisplayName("Should record location on plugin-triggered teleport (13-11, review round 2)")
        @SuppressWarnings("unchecked")
        void shouldRecordLocationOnPluginTeleport() throws Exception {
            World world = EssentialsTestHelper.createMockWorld("world");
            Location from = new Location(world, 100, 64, 200);
            Location to = new Location(world, 500, 64, 500);

            PlayerTeleportEvent event = new PlayerTeleportEvent(
                    player, from, to, PlayerTeleportEvent.TeleportCause.PLUGIN);

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

    /**
     * Proves UltiEssentials#14 (13-CONTEXT.md, 13-RECONFIRMATION.md): {@code BackCommand}
     * declares {@code implements Listener} but never carried the framework's own
     * {@code @EventListener} registration annotation, so {@code ListenerManager#registerAll}
     * -- which skips any {@code Listener}-typed bean for which
     * {@code MergedAnnotationResolver.find(listener.getClass(), EventListener.class)} returns
     * {@code null} -- never calls {@code Bukkit.getPluginManager().registerEvents(...)} for it.
     * {@code onPlayerTeleport} therefore never received a single real event; the pre-existing
     * tests above only ever call it directly, which is exactly the "annotation present but never
     * exercised" shape this task's own read_first warns about.
     */
    @Nested
    @DisplayName("event listener registration (13-11, UltiEssentials#14)")
    class RegistrationTests {

        private ServerMock mockBukkitServer;
        private PluginMock mockBukkitPlugin;
        private BackCommand liveBackCommand;
        private PlayerMock mockBukkitPlayer;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUpMockBukkit() throws Exception {
            MockBukkitHelper.clearForeignServer();
            mockBukkitServer = MockBukkit.mock();
            mockBukkitPlugin = MockBukkit.createMockPlugin();
            liveBackCommand = new BackCommand(new EssentialsConfig());

            UltiToolsPlugin i18nPlugin = mock(UltiToolsPlugin.class);
            lenient().when(i18nPlugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            Field pluginField = com.ultikits.plugins.essentials.commands.BaseEssentialsCommand.class
                    .getDeclaredField("plugin");
            pluginField.setAccessible(true); // NOPMD
            pluginField.set(liveBackCommand, i18nPlugin);

            mockBukkitPlayer = mockBukkitServer.addPlayer("RegistrationPlayer");

            Field field = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
            field.setAccessible(true); // NOPMD
            ((Map<UUID, Location>) field.get(null)).clear();
        }

        @AfterEach
        void tearDownMockBukkit() {
            MockBukkitHelper.safeUnmock();
        }

        @Test
        @DisplayName("theReturnCommandIsRegisteredAsAListener: BackCommand carries the framework's own listener-registration annotation, checked through the exact mechanism ListenerManager#registerAll uses (MergedAnnotationResolver), not a bare isAnnotationPresent check")
        void theReturnCommandIsRegisteredAsAListener() {
            EventListener annotation = MergedAnnotationResolver.find(BackCommand.class, EventListener.class);
            assertThat(annotation).isNotNull();
        }

        @Test
        @DisplayName("aTeleportIsRecordedAndReturnedTo: a teleport event dispatched through Bukkit's real event bus (not a direct method call) is recorded, and /back returns the player there")
        void aTeleportIsRecordedAndReturnedTo() {
            // Registers through the real Bukkit event system -- the same call
            // ListenerManager#registerAll makes once the annotation lets it reach this class --
            // proving the wiring actually works, not merely that the annotation is present.
            mockBukkitServer.getPluginManager().registerEvents(liveBackCommand, mockBukkitPlugin);

            World world = mockBukkitServer.addSimpleWorld("registration-world");
            Location from = new Location(world, 100, 64, 200);
            Location to = new Location(world, 500, 64, 500);
            mockBukkitPlayer.setLocation(to);

            PlayerTeleportEvent event = new PlayerTeleportEvent(
                    mockBukkitPlayer, from, to, PlayerTeleportEvent.TeleportCause.COMMAND);
            mockBukkitServer.getPluginManager().callEvent(event);

            liveBackCommand.back(mockBukkitPlayer);

            assertThat(mockBukkitPlayer.getLocation()).isEqualTo(from);
        }
    }
}
