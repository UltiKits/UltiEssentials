package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SpeedCommand Tests")
class SpeedCommandTest {

    private SpeedCommand speedCommand;
    private EssentialsConfig config;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        speedCommand = new SpeedCommand(config);
        EssentialsTestHelper.setField(speedCommand, "plugin", EssentialsTestHelper.getMockPlugin());

        player = EssentialsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("setSpeed")
    class SetSpeedTests {

        @Test
        @DisplayName("Should set speed to specified value")
        void shouldSetSpeed() {
            speedCommand.setSpeed(player, 5);

            verify(player).setWalkSpeed(anyFloat());
            verify(player).setFlySpeed(anyFloat());
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should reset speed when speed is 0")
        void shouldResetSpeedWhenZero() {
            speedCommand.setSpeed(player, 0);

            verify(player).setWalkSpeed(0.2f);
            verify(player).setFlySpeed(0.1f);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should reject speed above max")
        void shouldRejectSpeedAboveMax() {
            speedCommand.setSpeed(player, 11);

            verify(player, never()).setWalkSpeed(anyFloat());
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should reject negative speed")
        void shouldRejectNegativeSpeed() {
            speedCommand.setSpeed(player, -1);

            verify(player, never()).setWalkSpeed(anyFloat());
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should cap walk speed at 1.0")
        void shouldCapWalkSpeedAtOne() {
            speedCommand.setSpeed(player, 10);

            verify(player).setWalkSpeed(1.0f);
            verify(player).setFlySpeed(1.0f);
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setSpeedEnabled(false);

            speedCommand.setSpeed(player, 5);

            verify(player, never()).setWalkSpeed(anyFloat());
        }
    }

    @Nested
    @DisplayName("resetSpeed")
    class ResetSpeedTests {

        @Test
        @DisplayName("Should reset to default speeds")
        void shouldResetToDefaults() {
            speedCommand.resetSpeed(player);

            verify(player).setWalkSpeed(0.2f);
            verify(player).setFlySpeed(0.1f);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setSpeedEnabled(false);

            speedCommand.resetSpeed(player);

            verify(player, never()).setWalkSpeed(anyFloat());
        }
    }

    @Test
    @DisplayName("handleHelp should send usage message")
    void handleHelpShouldSendUsage() {
        speedCommand.handleHelp(player);
        verify(player).sendMessage(anyString());
    }
}
