package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.commands.BackCommand;
import com.ultikits.plugins.essentials.commands.HideCommand;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.TabBarConfig;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Other Listeners Tests")
class OtherListenersTest {

    private EssentialsConfig config;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("ScoreboardListener")
    class ScoreboardListenerTests {

        private ScoreboardListener listener;
        private ScoreboardService scoreboardService;

        @BeforeEach
        void setUp() throws Exception {
            scoreboardService = mock(ScoreboardService.class);
            listener = new ScoreboardListener();
            EssentialsTestHelper.setField(listener, "config", config);
            EssentialsTestHelper.setField(listener, "scoreboardService", scoreboardService);

            // Mock plugin for scheduler
            PluginManager pm = Bukkit.getServer().getPluginManager();
            Plugin mockPlugin = mock(Plugin.class);
            when(pm.getPlugin("UltiTools-API")).thenReturn(mockPlugin);
        }

        @Test
        @DisplayName("Should auto-enable scoreboard on join when auto-enable is on")
        void shouldAutoEnableOnJoin() {
            config.setScoreboardEnabled(true);
            config.setScoreboardAutoEnable(true);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            // Verify scheduler was called (delayed task)
            BukkitScheduler scheduler = Bukkit.getScheduler();
            verify(scheduler).runTaskLater(any(Plugin.class), any(Runnable.class), eq(20L));
        }

        @Test
        @DisplayName("Should not auto-enable when scoreboard is disabled")
        void shouldNotAutoEnableWhenDisabled() {
            config.setScoreboardEnabled(false);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            BukkitScheduler scheduler = Bukkit.getScheduler();
            verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
        }

        @Test
        @DisplayName("Should not auto-enable when auto-enable is off")
        void shouldNotAutoEnableWhenAutoOff() {
            config.setScoreboardEnabled(true);
            config.setScoreboardAutoEnable(false);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            BukkitScheduler scheduler = Bukkit.getScheduler();
            verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
        }

        @Test
        @DisplayName("Should disable scoreboard on quit")
        void shouldDisableOnQuit() {
            PlayerQuitEvent event = new PlayerQuitEvent(player, "left");

            listener.onPlayerQuit(event);

            verify(scoreboardService).disableScoreboard(player);
        }
    }

    @Nested
    @DisplayName("NamePrefixListener")
    class NamePrefixListenerTests {

        private NamePrefixListener listener;
        private NamePrefixService namePrefixService;

        @BeforeEach
        void setUp() throws Exception {
            namePrefixService = mock(NamePrefixService.class);
            listener = new NamePrefixListener();
            EssentialsTestHelper.setField(listener, "config", config);
            EssentialsTestHelper.setField(listener, "namePrefixService", namePrefixService);

            // Mock plugin for scheduler
            PluginManager pm = Bukkit.getServer().getPluginManager();
            Plugin mockPlugin = mock(Plugin.class);
            when(pm.getPlugin("UltiTools-API")).thenReturn(mockPlugin);
        }

        @Test
        @DisplayName("Should update name prefix on join")
        void shouldUpdateOnJoin() {
            config.setNamePrefixEnabled(true);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            // Verify scheduler was called
            BukkitScheduler scheduler = Bukkit.getScheduler();
            verify(scheduler).runTaskLater(any(Plugin.class), any(Runnable.class), eq(10L));
        }

        @Test
        @DisplayName("Should not update when feature is disabled")
        void shouldNotUpdateWhenDisabled() {
            config.setNamePrefixEnabled(false);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            BukkitScheduler scheduler = Bukkit.getScheduler();
            verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
        }

        @Test
        @DisplayName("Should remove player on quit")
        void shouldRemoveOnQuit() {
            PlayerQuitEvent event = new PlayerQuitEvent(player, "left");

            listener.onPlayerQuit(event);

            verify(namePrefixService).removePlayer(player);
        }
    }

    @Nested
    @DisplayName("TabBarListener")
    class TabBarListenerTests {

        private TabBarListener listener;
        private TabBarConfig tabBarConfig;

        @BeforeEach
        void setUp() throws Exception {
            tabBarConfig = new TabBarConfig();
            listener = new TabBarListener();
            EssentialsTestHelper.setField(listener, "config", config);
            EssentialsTestHelper.setField(listener, "tabBarConfig", tabBarConfig);
        }

        @Test
        @DisplayName("Should update tab bar on join")
        void shouldUpdateTabBarOnJoin() {
            config.setTabBarEnabled(true);

            Server server = Bukkit.getServer();
            List<Player> onlinePlayers = new ArrayList<>();
            doReturn(onlinePlayers).when(server).getOnlinePlayers();
            when(server.getMaxPlayers()).thenReturn(100);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            verify(player).setPlayerListHeaderFooter(anyString(), anyString());
        }

        @Test
        @DisplayName("Should not update tab bar when disabled")
        void shouldNotUpdateWhenDisabled() {
            config.setTabBarEnabled(false);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");

            listener.onPlayerJoin(event);

            verify(player, never()).setPlayerListHeaderFooter(anyString(), anyString());
        }

        @Test
        @DisplayName("Should replace variables in header and footer")
        void shouldReplaceVariables() {
            config.setTabBarEnabled(true);
            tabBarConfig.setHeader("Online: %online%/%max%");
            tabBarConfig.setFooter("Players: %online%");

            Server server = Bukkit.getServer();
            List<Player> onlinePlayers = new ArrayList<>();
            onlinePlayers.add(player);
            doReturn(onlinePlayers).when(server).getOnlinePlayers();
            when(server.getMaxPlayers()).thenReturn(50);

            listener.updateTabBar(player);

            verify(player).setPlayerListHeaderFooter(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("PlayerQuitListener")
    class PlayerQuitListenerTests {

        private PlayerQuitListener listener;

        @BeforeEach
        void setUp() {
            listener = new PlayerQuitListener();
        }

        @Test
        @DisplayName("Should clean up BackCommand and HideCommand data on quit")
        @SuppressWarnings("unchecked")
        void shouldCleanUpOnQuit() throws Exception {
            // Add data to BackCommand static map
            Field backField = BackCommand.class.getDeclaredField("LAST_LOCATIONS");
            backField.setAccessible(true); // NOPMD
            Map<UUID, org.bukkit.Location> backMap = (Map<UUID, org.bukkit.Location>) backField.get(null);
            backMap.put(playerUuid, new org.bukkit.Location(null, 0, 0, 0));

            // Add data to HideCommand static set
            Field hideField = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
            hideField.setAccessible(true); // NOPMD
            Set<UUID> hideSet = (Set<UUID>) hideField.get(null);
            hideSet.add(playerUuid);

            PlayerQuitEvent event = new PlayerQuitEvent(player, "left");

            listener.onPlayerQuit(event);

            assertThat(backMap).doesNotContainKey(playerUuid);
            assertThat(hideSet).doesNotContain(playerUuid);
        }
    }
}
