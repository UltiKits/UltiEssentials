package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("GameMode Shortcut Commands Tests")
class GmShortcutCommandsTest {

    private EssentialsConfig config;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("GmCreativeCommand")
    class GmCreativeTests {

        private GmCreativeCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new GmCreativeCommand(config);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should set creative mode")
        void shouldSetCreativeMode() {
            command.creative(player);
            verify(player).setGameMode(GameMode.CREATIVE);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setGamemodeEnabled(false);
            command.creative(player);
            verify(player, never()).setGameMode(any(GameMode.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("GmSurvivalCommand")
    class GmSurvivalTests {

        private GmSurvivalCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new GmSurvivalCommand(config);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should set survival mode")
        void shouldSetSurvivalMode() {
            command.survival(player);
            verify(player).setGameMode(GameMode.SURVIVAL);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setGamemodeEnabled(false);
            command.survival(player);
            verify(player, never()).setGameMode(any(GameMode.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("GmSpectatorCommand")
    class GmSpectatorTests {

        private GmSpectatorCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new GmSpectatorCommand(config);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should set spectator mode")
        void shouldSetSpectatorMode() {
            command.spectator(player);
            verify(player).setGameMode(GameMode.SPECTATOR);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setGamemodeEnabled(false);
            command.spectator(player);
            verify(player, never()).setGameMode(any(GameMode.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(player);
            verify(player).sendMessage(anyString());
        }
    }
}
