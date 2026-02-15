package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScoreboardCommand Tests")
class ScoreboardCommandTest {

    private ScoreboardService scoreboardService;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        scoreboardService = mock(ScoreboardService.class);
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private ScoreboardCommand createCommand() throws Exception {
        ScoreboardCommand command = new ScoreboardCommand();
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(command, "scoreboardService", scoreboardService);
        return command;
    }

    @Test
    @DisplayName("Should toggle scoreboard on")
    void shouldToggleScoreboardOn() throws Exception {
        ScoreboardCommand command = createCommand();
        when(scoreboardService.toggleScoreboard(player)).thenReturn(true);

        command.toggle(player);

        verify(scoreboardService).toggleScoreboard(player);
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should toggle scoreboard off")
    void shouldToggleScoreboardOff() throws Exception {
        ScoreboardCommand command = createCommand();
        when(scoreboardService.toggleScoreboard(player)).thenReturn(false);

        command.toggle(player);

        verify(scoreboardService).toggleScoreboard(player);
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should enable scoreboard")
    void shouldEnableScoreboard() throws Exception {
        ScoreboardCommand command = createCommand();
        when(scoreboardService.isEnabled(player)).thenReturn(false);

        command.enable(player);

        verify(scoreboardService).enableScoreboard(player);
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should send message when already enabled")
    void shouldSendMessageWhenAlreadyEnabled() throws Exception {
        ScoreboardCommand command = createCommand();
        when(scoreboardService.isEnabled(player)).thenReturn(true);

        command.enable(player);

        verify(scoreboardService, never()).enableScoreboard(player);
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should disable scoreboard")
    void shouldDisableScoreboard() throws Exception {
        ScoreboardCommand command = createCommand();
        when(scoreboardService.isEnabled(player)).thenReturn(true);

        command.disable(player);

        verify(scoreboardService).disableScoreboard(player);
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should send message when already disabled")
    void shouldSendMessageWhenAlreadyDisabled() throws Exception {
        ScoreboardCommand command = createCommand();
        when(scoreboardService.isEnabled(player)).thenReturn(false);

        command.disable(player);

        verify(scoreboardService, never()).disableScoreboard(player);
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("handleHelp should send usage messages")
    void handleHelpShouldSendUsage() throws Exception {
        ScoreboardCommand command = createCommand();

        command.handleHelp(player);

        verify(player, atLeast(1)).sendMessage(anyString());
    }
}
