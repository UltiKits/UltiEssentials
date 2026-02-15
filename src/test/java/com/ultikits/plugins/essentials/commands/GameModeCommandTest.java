package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("GameModeCommand Tests")
class GameModeCommandTest {

    private GameModeCommand gmCommand;
    private EssentialsConfig config;
    private Player player;
    private Player target;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        gmCommand = new GameModeCommand(config);
        EssentialsTestHelper.setField(gmCommand, "plugin", EssentialsTestHelper.getMockPlugin());

        player = EssentialsTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("setGameMode - self")
    class SetGameModeTests {

        @Test
        @DisplayName("Should set survival mode with '0'")
        void shouldSetSurvivalWithZero() {
            gmCommand.setGameMode(player, "0");
            verify(player).setGameMode(GameMode.SURVIVAL);
        }

        @Test
        @DisplayName("Should set survival mode with 's'")
        void shouldSetSurvivalWithS() {
            gmCommand.setGameMode(player, "s");
            verify(player).setGameMode(GameMode.SURVIVAL);
        }

        @Test
        @DisplayName("Should set survival mode with 'survival'")
        void shouldSetSurvivalWithFullName() {
            gmCommand.setGameMode(player, "survival");
            verify(player).setGameMode(GameMode.SURVIVAL);
        }

        @Test
        @DisplayName("Should set creative mode with '1'")
        void shouldSetCreativeWithOne() {
            gmCommand.setGameMode(player, "1");
            verify(player).setGameMode(GameMode.CREATIVE);
        }

        @Test
        @DisplayName("Should set creative mode with 'c'")
        void shouldSetCreativeWithC() {
            gmCommand.setGameMode(player, "c");
            verify(player).setGameMode(GameMode.CREATIVE);
        }

        @Test
        @DisplayName("Should set creative mode with 'creative'")
        void shouldSetCreativeWithFullName() {
            gmCommand.setGameMode(player, "creative");
            verify(player).setGameMode(GameMode.CREATIVE);
        }

        @Test
        @DisplayName("Should set adventure mode with '2'")
        void shouldSetAdventureWithTwo() {
            gmCommand.setGameMode(player, "2");
            verify(player).setGameMode(GameMode.ADVENTURE);
        }

        @Test
        @DisplayName("Should set adventure mode with 'a'")
        void shouldSetAdventureWithA() {
            gmCommand.setGameMode(player, "a");
            verify(player).setGameMode(GameMode.ADVENTURE);
        }

        @Test
        @DisplayName("Should set spectator mode with '3'")
        void shouldSetSpectatorWithThree() {
            gmCommand.setGameMode(player, "3");
            verify(player).setGameMode(GameMode.SPECTATOR);
        }

        @Test
        @DisplayName("Should set spectator mode with 'sp'")
        void shouldSetSpectatorWithSp() {
            gmCommand.setGameMode(player, "sp");
            verify(player).setGameMode(GameMode.SPECTATOR);
        }

        @Test
        @DisplayName("Should set spectator mode with 'spectator'")
        void shouldSetSpectatorWithFullName() {
            gmCommand.setGameMode(player, "spectator");
            verify(player).setGameMode(GameMode.SPECTATOR);
        }

        @Test
        @DisplayName("Should reject invalid game mode")
        void shouldRejectInvalidMode() {
            gmCommand.setGameMode(player, "invalid");
            verify(player, never()).setGameMode(any(GameMode.class));
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setGamemodeEnabled(false);
            gmCommand.setGameMode(player, "0");
            verify(player, never()).setGameMode(any(GameMode.class));
        }
    }

    @Nested
    @DisplayName("setGameModeOther")
    class SetGameModeOtherTests {

        @Test
        @DisplayName("Should set game mode for another player")
        void shouldSetGameModeForOther() {
            gmCommand.setGameModeOther(player, "creative", target);

            verify(target).setGameMode(GameMode.CREATIVE);
            verify(player).sendMessage(anyString());
            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send message when target is null")
        void shouldSendMessageWhenTargetNull() {
            gmCommand.setGameModeOther(player, "creative", null);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should reject invalid game mode for other")
        void shouldRejectInvalidModeForOther() {
            gmCommand.setGameModeOther(player, "invalid", target);
            verify(target, never()).setGameMode(any(GameMode.class));
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setGamemodeEnabled(false);
            gmCommand.setGameModeOther(player, "creative", target);
            verify(target, never()).setGameMode(any(GameMode.class));
        }
    }

    @Test
    @DisplayName("handleHelp should send usage message")
    void handleHelpShouldSendUsage() {
        gmCommand.handleHelp(player);
        verify(player).sendMessage(anyString());
    }
}
