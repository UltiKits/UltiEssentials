package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.commands.HideCommand;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.service.TeleportService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unloading the module (for example {@code /upm uninstall UltiEssentials}) shows every vanished
 * player to everyone again and forgets the vanish state, so nobody is left hidden by a module that is
 * no longer there to un-hide them (found reviewing the #32 change, the same defect class as
 * UltiKits/UltiEssentials#32: Bukkit's visibility and the module's vanish set stop agreeing).
 * <p>
 * Every hide is recorded against the {@code UltiTools} Bukkit plugin, which stays enabled when this
 * module is unloaded, so Bukkit keeps the hides; and after the unload the {@code /hide} command that
 * would lift them, and the join listener that keeps them consistent, are both gone. Before this, a
 * vanished player stayed hidden from the players online when they vanished, became visible to anyone
 * who joined afterwards, and could not un-vanish until they relogged.
 * <p>
 * The hook runs as {@code unregisterSelf()} runs it. Its four task-owning services are present as
 * no-op beans, so the only effects are the ones under test.
 * <p>
 * 卸载模块时让所有隐身玩家对所有人重新可见，并清空隐身状态。
 */
@DisplayName("Unloading the module lifts every vanish (UltiKits/UltiEssentials#32 class)")
class VanishOnUnloadTest {

    private UltiEssentials plugin;
    private Plugin ultiTools;
    private Set<UUID> hiddenPlayers;
    private ScoreboardService scoreboardService;

    @BeforeEach
    @SuppressWarnings({"unchecked", "PMD.AvoidAccessibilityAlteration"}) // HideCommand's vanish set is private
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        ultiTools = mock(Plugin.class);
        when(EssentialsTestHelper.getMockServer().getPluginManager().getPlugin("UltiTools")).thenReturn(ultiTools);

        plugin = mock(UltiEssentials.class, CALLS_REAL_METHODS);
        scoreboardService = mock(ScoreboardService.class);
        SimpleContainer container = new SimpleContainer();
        container.registerType(ScheduledCommandService.class, mock(ScheduledCommandService.class));
        container.registerType(ScoreboardService.class, scoreboardService);
        container.registerType(NamePrefixService.class, mock(NamePrefixService.class));
        container.registerType(TeleportService.class, mock(TeleportService.class));
        plugin.setContext(container);

        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true);
        hiddenPlayers = (Set<UUID>) field.get(null);
        hiddenPlayers.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        hiddenPlayers.clear();
        EssentialsTestHelper.tearDown();
    }

    private static Player player(String name) {
        return EssentialsTestHelper.createMockPlayer(name, UUID.randomUUID());
    }

    private static void online(Player... players) {
        doReturn(new ArrayList<>(Arrays.asList(players))).when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
    }

    @Test
    @DisplayName("Every vanished player is shown to every other online player, with the plugin /hide used, and the vanish set is emptied")
    void unloadShowsVanishedPlayersAndForgetsThem() {
        Player vanished = player("Vanished");
        Player viewer = player("Viewer");
        Player visible = player("Visible");
        hiddenPlayers.add(vanished.getUniqueId());
        online(vanished, viewer, visible);

        invokeOnUnregister(plugin);

        verify(viewer).showPlayer(same(ultiTools), same(vanished));
        verify(visible).showPlayer(same(ultiTools), same(vanished));
        verify(vanished, never()).showPlayer(any(Plugin.class), same(vanished));
        // Control: a player who was never vanished is not "shown" to anyone.
        verify(viewer, never()).showPlayer(any(Plugin.class), same(visible));
        assertThat(hiddenPlayers).isEmpty();
    }

    @Test
    @DisplayName("A vanished player who already left is forgotten too, so a reinstall does not start half-vanished")
    void unloadForgetsOfflineVanishedPlayers() {
        Player viewer = player("Viewer");
        hiddenPlayers.add(UUID.randomUUID());
        online(viewer);

        invokeOnUnregister(plugin);

        assertThat(hiddenPlayers).isEmpty();
    }

    @Test
    @DisplayName("Control: with nobody vanished, the unload shows nobody to anyone")
    void unloadWithNobodyVanishedShowsNobody() {
        Player first = player("First");
        Player second = player("Second");
        online(first, second);

        invokeOnUnregister(plugin);

        verify(first, never()).showPlayer(any(Plugin.class), any(Player.class));
        verify(second, never()).showPlayer(any(Plugin.class), any(Player.class));
    }

    @Test
    @DisplayName("A failure while revealing is reported, and the services are still shut down")
    void revealFailureStillShutsTheServicesDown() {
        Player vanished = player("Vanished");
        Player viewer = player("Viewer");
        hiddenPlayers.add(vanished.getUniqueId());
        online(vanished, viewer);
        IllegalStateException boom = new IllegalStateException("boom");
        doThrow(boom).when(viewer).showPlayer(any(Plugin.class), any(Player.class));

        assertThatThrownBy(() -> invokeOnUnregister(plugin)).isSameAs(boom);

        verify(scoreboardService).shutdown();
    }

    // onUnregister() is protected in the framework's package; unregisterSelf() calls it virtually,
    // and so does this reflective call. The hook's own failure is rethrown unwrapped, as
    // unregisterSelf() rethrows it.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // invokes the protected framework hook as unregisterSelf() does
    private static void invokeOnUnregister(UltiToolsPlugin target) {
        try {
            Method hook = UltiToolsPlugin.class.getDeclaredMethod("onUnregister");
            hook.setAccessible(true);
            hook.invoke(target);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
