package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.FakeScoreboards;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
    @DisplayName("A sidebar line equal to a team member's name gets its own entry, so the team's prefix does not format the row")
    void aLineEqualToATeamEntryIsNotFormattedByTheTeam() {
        FakeScoreboards.addTeam(mainBoard, "up_x", "[VIP] ", "Steve");
        config.setScoreboardLines(java.util.Arrays.asList("Steve", "Line"));
        Objective objective = privateBoard.getObjective("ultiessentials");

        service.enableScoreboard(player);

        org.mockito.ArgumentCaptor<String> entries = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(objective, org.mockito.Mockito.atLeastOnce()).getScore(entries.capture());
        assertThat(entries.getAllValues()).as("the row is not the team member's own entry").doesNotContain("Steve");
        assertThat(entries.getAllValues()).anySatisfy(e -> assertThat(ChatColor.stripColor(e)).isEqualTo("Steve"));
        assertThat(privateBoard.getTeam("up_x").getEntries()).containsExactly("Steve");
    }

    @Test
    @DisplayName("When another plugin removes the sidebar objective from the board, the next update redraws every line on the new one")
    void aRecreatedObjectiveGetsEveryLineAgain() {
        service.enableScoreboard(player);
        Objective replacement = mock(Objective.class);
        when(replacement.getScore(anyString())).thenReturn(mock(Score.class));
        when(privateBoard.getObjective("ultiessentials")).thenReturn(null);
        when(privateBoard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(replacement);

        service.updateScoreboard(player);

        verify(replacement).getScore("Line");
    }

    /** Scores on {@code objective} read as set, so an update sees the lines intact and tests one thing. */
    private static void keepScoresSet(Objective objective) {
        Score score = mock(Score.class);
        when(score.isScoreSet()).thenReturn(true);
        when(objective.getScore(anyString())).thenReturn(score);
    }

    // Another plugin can change this module's board while the player views it. Every piece of that
    // board the update does not rebuild on its own is checked: the objective (recreated above), the
    // sidebar slot, and the scores; the title and the copied teams are rewritten on every update.

    @Test
    @DisplayName("When another plugin clears the sidebar slot on the board, the next update puts the objective back in it")
    void aClearedSidebarSlotIsRestored() {
        Objective objective = privateBoard.getObjective("ultiessentials");
        keepScoresSet(objective);
        service.enableScoreboard(player);
        org.mockito.Mockito.clearInvocations(objective);
        when(privateBoard.getObjective(DisplaySlot.SIDEBAR)).thenReturn(null);

        service.updateScoreboard(player);

        verify(objective).setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    @Test
    @DisplayName("When another plugin shows its own objective in the sidebar slot of the board, the next update puts this one back")
    void aForeignObjectiveInTheSlotIsReplaced() {
        Objective objective = privateBoard.getObjective("ultiessentials");
        keepScoresSet(objective);
        service.enableScoreboard(player);
        org.mockito.Mockito.clearInvocations(objective);
        when(privateBoard.getObjective(DisplaySlot.SIDEBAR)).thenReturn(mock(Objective.class));

        service.updateScoreboard(player);

        verify(objective).setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    @Test
    @DisplayName("While this objective holds the slot, an update does not set the slot again")
    void theSlotIsNotRewrittenWhileHeld() {
        Objective objective = privateBoard.getObjective("ultiessentials");
        keepScoresSet(objective);
        service.enableScoreboard(player);
        org.mockito.Mockito.clearInvocations(objective);
        when(privateBoard.getObjective(DisplaySlot.SIDEBAR)).thenReturn(objective);

        service.updateScoreboard(player);

        verify(objective, never()).setDisplaySlot(any());
    }

    @Test
    @DisplayName("When another plugin resets the scores on the board, the next update draws every line again")
    void resetScoresAreRedrawn() {
        Objective objective = privateBoard.getObjective("ultiessentials");
        Score score = mock(Score.class);
        when(objective.getScore(anyString())).thenReturn(score);
        when(score.isScoreSet()).thenReturn(true);
        service.enableScoreboard(player);
        org.mockito.Mockito.clearInvocations(score);
        when(score.isScoreSet()).thenReturn(false);

        service.updateScoreboard(player);

        verify(score).setScore(anyInt());
    }

    @Test
    @DisplayName("While every shown line keeps its score, an update writes no score")
    void intactScoresAreNotRewritten() {
        Objective objective = privateBoard.getObjective("ultiessentials");
        Score score = mock(Score.class);
        when(objective.getScore(anyString())).thenReturn(score);
        when(score.isScoreSet()).thenReturn(true);
        service.enableScoreboard(player);
        org.mockito.Mockito.clearInvocations(score);

        service.updateScoreboard(player);

        verify(score, never()).setScore(anyInt());
    }

    // Only what this module put on the board is ever removed from it: the scores of its own previous
    // lines, and the teams it copied from the main scoreboard. Anything another plugin put there stays.

    @Test
    @DisplayName("When the lines change, no entry is reset board-wide: this module's objective is replaced, so another objective's scores stay")
    void aLineChangeReplacesOnlyThisModulesObjective() {
        Objective objective = privateBoard.getObjective("ultiessentials");
        service.enableScoreboard(player);
        Objective replacement = mock(Objective.class);
        when(replacement.getScore(anyString())).thenReturn(mock(Score.class));
        when(privateBoard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(replacement);
        config.setScoreboardLines(Collections.singletonList("Other"));

        service.updateScoreboard(player);

        verify(privateBoard, never()).resetScores(anyString());
        verify(objective).unregister();
        verify(replacement).setDisplaySlot(DisplaySlot.SIDEBAR);
        verify(replacement).getScore("Other");
    }

    @Test
    @DisplayName("A team another plugin put on the board stays; only teams copied from the main scoreboard are removed")
    void aForeignTeamOnTheBoardStays() {
        Team copied = FakeScoreboards.addTeam(mainBoard, "up_x", "[VIP] ", "Alice");
        FakeScoreboards.addTeam(privateBoard, "tab_sort", "", "Bob");
        service.enableScoreboard(player);
        assertThat(privateBoard.getTeam("up_x")).isNotNull();

        copied.unregister();
        service.updateScoreboard(player);

        assertThat(privateBoard.getTeam("up_x")).as("the copy of a team gone from the main board").isNull();
        assertThat(privateBoard.getTeam("tab_sort")).as("another plugin's team").isNotNull();
    }

    @Test
    @DisplayName("A team another plugin put on the board under a copied team's name is left as that plugin set it")
    void aForeignTeamReusingACopiedNameIsLeftAlone() {
        Team source = FakeScoreboards.addTeam(mainBoard, "up_x", "[VIP] ", "Alice");
        service.enableScoreboard(player);
        privateBoard.getTeam("up_x").unregister();
        Team foreign = FakeScoreboards.addTeam(privateBoard, "up_x", "[TAB] ", "Bob");

        service.updateScoreboard(player);
        source.unregister();
        service.updateScoreboard(player);

        assertThat(privateBoard.getTeam("up_x")).isSameAs(foreign);
        assertThat(foreign.prefix()).isEqualTo(Component.text("[TAB] "));
        assertThat(foreign.getEntries()).containsExactly("Bob");
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
