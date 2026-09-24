package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.commands.HideCommand;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A player who is vanished with {@code /hide} stays hidden from players who join after they
 * vanished (UltiKits/UltiEssentials#32).
 * <p>
 * {@code /hide} hid the player from everyone online at that moment and from nobody after: nothing
 * re-applied the vanish to a later joiner, so the vanished player was visible to them.
 * {@code HideCommand#isHidden} existed for this and had no caller. The see-vanished permission,
 * {@code ultiessentials.hide.see}, which {@code /hide} already honours at toggle time, is honoured
 * here too: a joiner holding it keeps seeing the vanished player.
 * <p>
 * The join is dispatched the way Bukkit dispatches it -- to every {@code @EventHandler} on
 * {@link JoinQuitListener} that takes a {@link PlayerJoinEvent} -- so these cases pin the behaviour
 * of the join, not the name of whichever method implements it.
 * <p>
 * 隐身的玩家对之后加入的玩家同样保持隐身；持有 ultiessentials.hide.see 的玩家仍可看见。
 */
@DisplayName("A vanished player stays hidden from later joiners (UltiKits/UltiEssentials#32)")
class VanishOnJoinTest {

    private static final String SEE_VANISHED = "ultiessentials.hide.see";

    private JoinQuitListener listener;
    private Plugin ultiTools;
    private Set<UUID> hiddenPlayers;

    @BeforeEach
    @SuppressWarnings({"unchecked", "PMD.AvoidAccessibilityAlteration"}) // HideCommand's vanish set is private
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        listener = new JoinQuitListener();
        EssentialsTestHelper.setField(listener, "config", new EssentialsConfig());
        EssentialsTestHelper.setField(listener, "spawnConfig", new SpawnConfig());

        ultiTools = mock(Plugin.class);
        when(EssentialsTestHelper.getMockServer().getPluginManager().getPlugin("UltiTools")).thenReturn(ultiTools);

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

    private Player player(String name) {
        Player player = EssentialsTestHelper.createMockPlayer(name, UUID.randomUUID());
        when(player.hasPlayedBefore()).thenReturn(true); // not a first join: no spawn teleport
        return player;
    }

    private void online(Player... players) {
        doReturn(new ArrayList<>(Arrays.asList(players))).when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
    }

    /** Delivers the join to every PlayerJoinEvent handler on the listener, as Bukkit would. */
    private int join(Player joiner) throws Exception {
        PlayerJoinEvent event = new PlayerJoinEvent(joiner, "");
        int handlers = 0;
        for (Method method : JoinQuitListener.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == PlayerJoinEvent.class) {
                method.invoke(listener, event);
                handlers++;
            }
        }
        return handlers;
    }

    @Test
    @DisplayName("A later joiner without the see-vanished permission is not shown the vanished player")
    void laterJoinerCannotSeeVanishedPlayer() throws Exception {
        Player vanished = player("Vanished");
        Player visible = player("Visible");
        Player joiner = player("Joiner");
        hiddenPlayers.add(vanished.getUniqueId());
        online(vanished, visible, joiner);

        // Control: the join reached at least the existing handler, so a zero below is not a
        // join that was never delivered.
        assertThat(join(joiner)).isPositive();

        verify(joiner).hidePlayer(same(ultiTools), same(vanished));
        verify(joiner, never()).hidePlayer(any(Plugin.class), same(visible));
        verify(joiner, never()).hidePlayer(any(Plugin.class), same(joiner));
    }

    @Test
    @DisplayName("Every vanished player is hidden from the joiner, each once")
    void everyVanishedPlayerIsHidden() throws Exception {
        Player first = player("FirstVanished");
        Player second = player("SecondVanished");
        Player joiner = player("Joiner");
        hiddenPlayers.add(first.getUniqueId());
        hiddenPlayers.add(second.getUniqueId());
        online(first, second, joiner);

        join(joiner);

        verify(joiner, times(1)).hidePlayer(same(ultiTools), same(first));
        verify(joiner, times(1)).hidePlayer(same(ultiTools), same(second));
    }

    @Test
    @DisplayName("A later joiner holding ultiessentials.hide.see still sees the vanished player")
    void joinerWithSeePermissionStillSeesVanishedPlayer() throws Exception {
        Player vanished = player("Vanished");
        Player staff = player("Staff");
        when(staff.hasPermission(SEE_VANISHED)).thenReturn(true);
        hiddenPlayers.add(vanished.getUniqueId());
        online(vanished, staff);

        assertThat(join(staff)).isPositive();

        verify(staff, never()).hidePlayer(any(Plugin.class), any(Player.class));
    }

    @Test
    @DisplayName("Control: with nobody vanished, a joiner is hidden from no one")
    void nobodyVanishedHidesNothing() throws Exception {
        Player other = player("Other");
        Player joiner = player("Joiner");
        online(other, joiner);

        assertThat(join(joiner)).isPositive();

        verify(joiner, never()).hidePlayer(any(Plugin.class), any(Player.class));
    }

    @Test
    @DisplayName("A player who has left vanish is not hidden from a later joiner")
    void unvanishedPlayerIsNotHidden() throws Exception {
        Player formerlyVanished = player("Former");
        Player joiner = player("Joiner");
        hiddenPlayers.add(formerlyVanished.getUniqueId());
        HideCommand.removePlayer(formerlyVanished.getUniqueId());
        online(formerlyVanished, joiner);

        join(joiner);

        verify(joiner, never()).hidePlayer(any(Plugin.class), any(Player.class));
    }
}
