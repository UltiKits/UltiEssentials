package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("HideCommand Tests")
class HideCommandTest {

    private HideCommand command;
    private EssentialsConfig config;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        command = new HideCommand(config);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());

        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);

        // Clear static set between tests
        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true); // NOPMD
        ((Set<UUID>) field.get(null)).clear();

        // Mock UltiTools plugin for getBukkitPlugin()
        PluginManager pm = Bukkit.getServer().getPluginManager();
        Plugin mockUltiTools = mock(Plugin.class);
        when(pm.getPlugin("UltiTools")).thenReturn(mockUltiTools);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("Should enable vanish mode")
    @SuppressWarnings("unchecked")
    void shouldEnableVanish() throws Exception {
        // Set up online players
        List<Player> onlinePlayers = new ArrayList<>();
        Player other = EssentialsTestHelper.createMockPlayer("Other", UUID.randomUUID());
        when(other.hasPermission("ultiessentials.hide.see")).thenReturn(false);
        onlinePlayers.add(other);

        Server server = Bukkit.getServer();
        doReturn(onlinePlayers).when(server).getOnlinePlayers();

        command.toggleHide(player);

        verify(player).sendMessage(anyString());

        // Verify player is in hidden set
        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true); // NOPMD
        Set<UUID> hidden = (Set<UUID>) field.get(null);
        assertThat(hidden).contains(playerUuid);
    }

    @Test
    @DisplayName("Should disable vanish mode")
    @SuppressWarnings("unchecked")
    void shouldDisableVanish() throws Exception {
        // Pre-add player to hidden set
        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true); // NOPMD
        Set<UUID> hidden = (Set<UUID>) field.get(null);
        hidden.add(playerUuid);

        // Set up online players
        List<Player> onlinePlayers = new ArrayList<>();
        Player other = EssentialsTestHelper.createMockPlayer("Other", UUID.randomUUID());
        onlinePlayers.add(other);

        Server server = Bukkit.getServer();
        doReturn(onlinePlayers).when(server).getOnlinePlayers();

        command.toggleHide(player);

        verify(player).sendMessage(anyString());
        assertThat(hidden).doesNotContain(playerUuid);
    }

    @Test
    @DisplayName("Should handle disabled feature")
    void shouldHandleDisabledFeature() {
        config.setHideEnabled(false);

        command.toggleHide(player);

        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("isHidden should return correct state")
    @SuppressWarnings("unchecked")
    void isHiddenShouldReturnCorrectState() throws Exception {
        assertThat(HideCommand.isHidden(player)).isFalse();

        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true); // NOPMD
        Set<UUID> hidden = (Set<UUID>) field.get(null);
        hidden.add(playerUuid);

        assertThat(HideCommand.isHidden(player)).isTrue();
    }

    @Test
    @DisplayName("removePlayer should remove from hidden set")
    @SuppressWarnings("unchecked")
    void removePlayerShouldRemove() throws Exception {
        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true); // NOPMD
        Set<UUID> hidden = (Set<UUID>) field.get(null);
        hidden.add(playerUuid);

        HideCommand.removePlayer(playerUuid);

        assertThat(hidden).doesNotContain(playerUuid);
    }

    @Test
    @DisplayName("handleHelp should send usage message")
    void handleHelpShouldSendUsage() {
        command.handleHelp(player);
        verify(player).sendMessage(anyString());
    }
}
