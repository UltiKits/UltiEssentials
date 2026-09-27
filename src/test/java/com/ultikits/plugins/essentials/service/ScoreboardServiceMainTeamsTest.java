package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.FakeScoreboards;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sidebar's private scoreboard carries the server's main-scoreboard teams, so name prefixes
 * (this module's own {@code features.nameprefix}, vanilla {@code /team}, other plugins) stay
 * visible while the sidebar is on, and the sidebar keeps one board per player instead of building a
 * new one every update (maintainer decision 2026-09-27, UltiKits/UltiEssentials#40).
 */
@DisplayName("ScoreboardService carries the main scoreboard's teams onto its own board (#40)")
class ScoreboardServiceMainTeamsTest {

    private ScoreboardService service;
    private EssentialsConfig config;
    private ScoreboardManager manager;
    private Scoreboard mainBoard;
    private Scoreboard privateBoard;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        mainBoard = FakeScoreboards.board();
        privateBoard = FakeScoreboards.board();
        Objective objective = mock(Objective.class);
        lenient().when(privateBoard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(objective);
        lenient().when(privateBoard.getObjective("ultiessentials")).thenReturn(objective);
        lenient().when(privateBoard.getEntries()).thenReturn(Collections.<String>emptySet());
        lenient().when(objective.getScore(anyString())).thenReturn(mock(Score.class));

        manager = mock(ScoreboardManager.class);
        lenient().when(manager.getMainScoreboard()).thenReturn(mainBoard);
        lenient().when(manager.getNewScoreboard()).thenReturn(privateBoard);
        when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(manager);

        config = new EssentialsConfig();
        config.setScoreboardEnabled(true);
        config.setScoreboardTitle("Title");
        config.setScoreboardLines(Collections.singletonList("Line"));

        service = new ScoreboardService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "plugin", CatalogueText.plugin("en"));
        EssentialsTestHelper.setField(service, "manager", manager);
        EssentialsTestHelper.setField(service, "bukkitPlugin", mock(Plugin.class));

        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        lenient().when(player.isOnline()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("Enabling the sidebar puts the main board's team, prefix and members on the sidebar's board")
    void enableCopiesMainBoardTeams() {
        FakeScoreboards.addTeam(mainBoard, "up_x", "[VIP] ", "Alice");

        service.enableScoreboard(player);

        verify(player).setScoreboard(privateBoard);
        Team copy = privateBoard.getTeam("up_x");
        assertThat(copy).isNotNull();
        assertThat(copy.prefix()).isEqualTo(Component.text("[VIP] "));
        assertThat(copy.getEntries()).containsExactly("Alice");
    }

    @Test
    @DisplayName("One update follows a team added, changed and removed on the main board")
    void updateFollowsTheMainBoard() {
        service.enableScoreboard(player);
        Team source = FakeScoreboards.addTeam(mainBoard, "up_x", "[VIP] ", "Alice", "Bob");

        service.updateScoreboard(player);
        assertThat(privateBoard.getTeam("up_x").prefix()).isEqualTo(Component.text("[VIP] "));

        source.prefix(Component.text("[MVP] "));
        source.suffix(Component.text(" *"));
        source.setColor(ChatColor.GOLD);
        source.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        source.setAllowFriendlyFire(false);
        source.setCanSeeFriendlyInvisibles(false);
        source.displayName(Component.text("VIPs"));
        source.removeEntry("Bob");
        source.addEntry("Carol");
        service.updateScoreboard(player);

        Team copy = privateBoard.getTeam("up_x");
        assertThat(copy.prefix()).isEqualTo(Component.text("[MVP] "));
        assertThat(copy.suffix()).isEqualTo(Component.text(" *"));
        assertThat(copy.getColor()).isEqualTo(ChatColor.GOLD);
        assertThat(copy.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.NEVER);
        assertThat(copy.allowFriendlyFire()).isFalse();
        assertThat(copy.canSeeFriendlyInvisibles()).isFalse();
        assertThat(copy.displayName()).isEqualTo(Component.text("VIPs"));
        assertThat(copy.getEntries()).containsExactlyInAnyOrder("Alice", "Carol");

        source.unregister();
        service.updateScoreboard(player);
        assertThat(privateBoard.getTeam("up_x")).isNull();
    }

    @Test
    @DisplayName("An unchanged team costs no writes on the next update")
    void unchangedTeamCostsNoWrites() {
        FakeScoreboards.addTeam(mainBoard, "up_x", "[VIP] ", "Alice");
        service.enableScoreboard(player);
        Team copy = privateBoard.getTeam("up_x");
        clearInvocations(copy, privateBoard);

        service.updateScoreboard(player);

        verify(copy, never()).prefix(any());
        verify(copy, never()).suffix(any());
        verify(copy, never()).displayName(any());
        verify(copy, never()).setColor(any());
        verify(copy, never()).setOption(any(), any());
        verify(copy, never()).setAllowFriendlyFire(anyBoolean());
        verify(copy, never()).setCanSeeFriendlyInvisibles(anyBoolean());
        verify(copy, never()).addEntry(anyString());
        verify(copy, never()).removeEntry(anyString());
        verify(privateBoard, never()).registerNewTeam(anyString());
        assertThat(copy.getEntries()).containsExactly("Alice");
    }

    @Test
    @DisplayName("The sidebar keeps one board per player: repeated updates neither build nor assign a new board")
    void keepsOneBoardPerPlayer() {
        service.enableScoreboard(player);
        when(player.getScoreboard()).thenReturn(privateBoard);

        service.updateScoreboard(player);
        service.updateScoreboard(player);
        service.updateScoreboard(player);

        verify(manager, times(1)).getNewScoreboard();
        verify(player, times(1)).setScoreboard(any(Scoreboard.class));
    }
}
